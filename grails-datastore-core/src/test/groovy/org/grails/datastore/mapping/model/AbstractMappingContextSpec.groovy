/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.model

import org.springframework.core.convert.converter.Converter
import org.springframework.core.env.StandardEnvironment
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import spock.lang.Specification

import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.engine.BeanEntityAccess
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.proxy.JavassistProxyFactory
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.grails.datastore.mapping.reflect.FieldEntityAccess
import org.grails.datastore.mapping.validation.ValidatorRegistry

class AbstractMappingContextSpec extends Specification {

    KeyValueMappingContext mappingContext = new KeyValueMappingContext('test')

    void "entities are registered once and can be overridden or added as external"() {
        when:
        PersistentEntity first = mappingContext.addPersistentEntity(AMCRoot)
        PersistentEntity again = mappingContext.addPersistentEntity(AMCRoot)
        PersistentEntity notOverridden = mappingContext.addPersistentEntity(AMCRoot, false)
        PersistentEntity overridden = mappingContext.addPersistentEntity(AMCRoot, true)

        then:
        again.is(first)
        notOverridden.is(first)
        !overridden.is(first)
        mappingContext.getPersistentEntity(AMCRoot.name).is(overridden)
        mappingContext.persistentEntities.size() == 1

        when:
        PersistentEntity external = mappingContext.addExternalPersistentEntity(AMCExternal)

        then:
        external.external
        mappingContext.addExternalPersistentEntity(AMCExternal).is(external)
    }

    void "adding several entities initialises them and notifies listeners"() {
        given:
        List<String> added = []
        mappingContext.addMappingContextListener({ PersistentEntity e -> added << e.name } as MappingContext.Listener)
        mappingContext.addMappingContextListener(null)

        when:
        Collection<PersistentEntity> entities = mappingContext.addPersistentEntities(AMCRoot, AMCChild)

        then:
        entities*.name == [AMCRoot.name, AMCChild.name]
        entities.every { it.initialized }
        added == [AMCRoot.name, AMCChild.name]

        when:
        mappingContext.addPersistentEntity(AMCExternal)

        then:
        added.last() == AMCExternal.name
    }

    void "entities can be looked up by class, instance and proxied name"() {
        given:
        mappingContext.addPersistentEntity(AMCRoot)

        expect:
        mappingContext.isPersistentEntity(AMCRoot)
        mappingContext.isPersistentEntity(new AMCRoot())
        !mappingContext.isPersistentEntity(String)
        !mappingContext.isPersistentEntity((Class) null)
        !mappingContext.isPersistentEntity((Object) null)
        mappingContext.getPersistentEntity(AMCRoot.name + '_$$_javassist_0').name == AMCRoot.name
        mappingContext.getPersistentEntity('nope') == null
    }

    void "the inheritance hierarchy is indexed by discriminator and parent"() {
        given:
        PersistentEntity root = mappingContext.addPersistentEntity(AMCRoot)
        PersistentEntity child = mappingContext.addPersistentEntity(AMCChild)
        PersistentEntity grandChild = mappingContext.addPersistentEntity(AMCGrandChild)
        PersistentEntity other = mappingContext.addPersistentEntity(AMCExternal)

        expect:
        mappingContext.getChildEntities(root).toSet() == [child, grandChild].toSet()
        mappingContext.getChildEntities(child).toSet() == [grandChild].toSet()
        mappingContext.getChildEntities(other).empty
        mappingContext.getDirectChildEntities(root).toSet() == [child].toSet()
        mappingContext.getDirectChildEntities(other).empty
        mappingContext.getChildEntityByDiscriminator(root, 'AMCGrandChild').is(grandChild)
        mappingContext.getChildEntityByDiscriminator(root, 'Nope') == null
        mappingContext.getChildEntityByDiscriminator(other, 'AMCChild') == null
        mappingContext.isInInheritanceHierarchy(root)
        mappingContext.isInInheritanceHierarchy(child)
        !mappingContext.isInInheritanceHierarchy(other)
        !mappingContext.isInInheritanceHierarchy(null)
    }

    void "the proxy factory defaults to javassist and can be replaced"() {
        expect:
        mappingContext.proxyFactory instanceof JavassistProxyFactory
        mappingContext.proxyHandler.is(mappingContext.proxyFactory)

        when:
        ProxyFactory custom = Mock(ProxyFactory)
        mappingContext.proxyFactory = null

        then:
        mappingContext.proxyFactory instanceof JavassistProxyFactory

        when:
        mappingContext.proxyFactory = custom

        then:
        mappingContext.proxyFactory.is(custom)
    }

    void "validators are stored per entity and resolved through the registry"() {
        given:
        PersistentEntity root = mappingContext.addPersistentEntity(AMCRoot)
        PersistentEntity child = mappingContext.addPersistentEntity(AMCChild)
        Validator validator = Stub(Validator)
        Validator fromRegistry = Stub(Validator)
        ValidatorRegistry registry = Mock(ValidatorRegistry)

        expect:
        mappingContext.getEntityValidator(null) == null
        mappingContext.getEntityValidator(root) == null

        when:
        mappingContext.addEntityValidator(root, validator)
        mappingContext.addEntityValidator(root, null)
        mappingContext.addEntityValidator(null, validator)

        then:
        mappingContext.getEntityValidator(root).is(validator)

        when:
        mappingContext.validatorRegistry = registry
        Validator resolved = mappingContext.getEntityValidator(child)
        Validator cached = mappingContext.getEntityValidator(child)

        then:
        1 * registry.getValidator(child) >> fromRegistry
        mappingContext.validatorRegistry.is(registry)
        resolved.is(fromRegistry)
        cached.is(fromRegistry)
    }

    void "type converters are added to the conversion service"() {
        when:
        mappingContext.addTypeConverter(new Converter<String, AMCRoot>() {
            AMCRoot convert(String source) {
                new AMCRoot(name: source)
            }
        })

        then:
        mappingContext.conversionService.convert('x', AMCRoot).name == 'x'
        mappingContext.converterRegistry.is(mappingContext.conversionService)
    }

    void "entity access is created for entities, subclasses and unknown instances"() {
        given:
        PersistentEntity root = mappingContext.addPersistentEntity(AMCRoot)
        PersistentEntity child = mappingContext.addPersistentEntity(AMCChild)

        when:
        EntityAccess rootAccess = mappingContext.createEntityAccess(root, new AMCRoot(name: 'r'))
        EntityAccess childAccess = mappingContext.createEntityAccess(root, new AMCChild(name: 'c'))
        EntityAccess inferred = mappingContext.createEntityAccess(null, new AMCChild(name: 'i'))
        EntityAccess unknown = mappingContext.createEntityAccess(null, new AMCUnknown())

        then:
        rootAccess instanceof FieldEntityAccess
        rootAccess.persistentEntity.is(root)
        rootAccess.getProperty('name') == 'r'
        childAccess.persistentEntity.is(child)
        childAccess.getProperty('name') == 'c'
        inferred instanceof FieldEntityAccess
        inferred.persistentEntity.is(child)
        unknown instanceof BeanEntityAccess
        mappingContext.getEntityReflector(root).is(FieldEntityAccess.getOrIntializeReflector(root))
    }

    void "embedded entities are created and initialised on demand"() {
        when:
        PersistentEntity embedded = mappingContext.createEmbeddedEntity(AMCComponent)

        then:
        embedded instanceof EmbeddedPersistentEntity
        embedded.initialized
        embedded.getPropertyByName('value') != null
    }

    void "entity initialisation can be deferred until the context is initialised"() {
        given:
        mappingContext.canInitializeEntities = false

        when:
        PersistentEntity root = mappingContext.addPersistentEntity(AMCRoot)

        then:
        !root.initialized
        !mappingContext.initialized

        when:
        mappingContext.initialize()

        then:
        root.initialized
        mappingContext.initialized
        FieldEntityAccess.getReflector(root.name) != null
    }

    void "configuration and mapping strategies are resolved from the class"() {
        expect:
        mappingContext.resolveMappingStrategy(AMCMapWith) == 'keyvalue'
        mappingContext.resolveMappingStrategy(AMCRoot) == null
        mappingContext.isValidMappingStrategy(AMCRoot, null)
        mappingContext.isValidMappingStrategy(AMCMapWith, 'keyvalue')
        mappingContext.isValidMappingStrategy(AMCMapWith, 'KeyValue')
        !mappingContext.isValidMappingStrategy(AMCMapWith, 'neo4j')

        when:
        mappingContext.configure(null)
        mappingContext.configure(new StandardEnvironment())

        then:
        noExceptionThrown()
    }

    void "multi tenancy mode comes from the connection source settings"() {
        given:
        ConnectionSourceSettings settings = new ConnectionSourceSettings()
        settings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DATABASE

        expect:
        mappingContext.multiTenancyMode == MultiTenancySettings.MultiTenancyMode.NONE
        new KeyValueMappingContext('tenants', settings).multiTenancyMode == MultiTenancySettings.MultiTenancyMode.DATABASE
    }

    void "the entity validator interface is satisfied by spring validators"() {
        given:
        Validator validator = new Validator() {
            boolean supports(Class<?> clazz) { true }
            void validate(Object target, Errors errors) { }
        }
        PersistentEntity root = mappingContext.addPersistentEntity(AMCRoot)

        when:
        mappingContext.addEntityValidator(root, validator)

        then:
        mappingContext.getEntityValidator(root).supports(AMCRoot)
    }
}

class AMCRoot {
    Long id
    String name
}

class AMCChild extends AMCRoot {
    String extra
}

class AMCGrandChild extends AMCChild {
    String more
}

class AMCExternal {
    Long id
    String name
}

class AMCUnknown {
    String name
}

class AMCComponent {
    String value
}

class AMCMapWith {
    Long id
    String name
    static mapWith = 'keyvalue'
}
