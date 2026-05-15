/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.model.config

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.GormKeyValueMappingFactory
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.MappingFactory
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import spock.lang.Specification
import java.beans.PropertyDescriptor

class GormMappingConfigurationStrategySpec extends Specification {

    void 'test isPersistentEntity'() {
        given:
        def strategy = new GormMappingConfigurationStrategy(new GormKeyValueMappingFactory('test'))

        expect:
        strategy.isPersistentEntity(AnnotatedEntity)
        strategy.isPersistentEntity(GormAnnotatedEntity)
        !strategy.isPersistentEntity(NotAnEntity)
        !strategy.isPersistentEntity(null)
        !strategy.isPersistentEntity(EnumEntity)
        !strategy.isPersistentEntity(Closure)
    }

    void 'test getAssociationMap subclass overrides parent'() {

        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(B)
        def strategy = new GormMappingConfigurationStrategy(new GormKeyValueMappingFactory('test'))

        when:
        Map associations = strategy.getAssociationMap(cpf)

        then:
        associations.size() == 1
        associations.get('foo') == Integer
    }

    void 'test getIdentity'() {
        given:
        def mappingFactory = Mock(MappingFactory)
        def strategy = new GormMappingConfigurationStrategy(mappingFactory)
        def context = Mock(MappingContext)
        def entity = Mock(PersistentEntity)
        def classMapping = Mock(ClassMapping)
        def identityMapping = Mock(IdentityMapping)

        context.getPersistentEntity(SimpleIdEntity.name) >> entity
        entity.getJavaClass() >> SimpleIdEntity
        entity.getMapping() >> classMapping
        classMapping.getIdentifier() >> identityMapping
        identityMapping.getIdentifierName() >> (['id'] as String[])

        when:
        strategy.getIdentity(SimpleIdEntity, context)

        then:
        1 * mappingFactory.createIdentity(entity, context, _)
    }

    void 'test getCompositeIdentity'() {
        given:
        def mappingFactory = Mock(MappingFactory)
        def strategy = new GormMappingConfigurationStrategy(mappingFactory)
        def context = Mock(MappingContext)
        def entity = Mock(PersistentEntity)
        def classMapping = Mock(ClassMapping)
        def identityMapping = Mock(IdentityMapping)

        context.getPersistentEntity(CompositeIdEntity.name) >> entity
        entity.getJavaClass() >> CompositeIdEntity
        entity.getMapping() >> classMapping
        classMapping.getIdentifier() >> identityMapping
        identityMapping.getIdentifierName() >> (['id1', 'id2'] as String[])
        entity.getPropertyByName('id1') >> null
        entity.getPropertyByName('id2') >> null

        when:
        strategy.getCompositeIdentity(CompositeIdEntity, context)

        then:
        2 * mappingFactory.createIdentity(entity, context, _)
    }

    void 'test getPersistentProperties with basic properties and transients'() {
        given:
        def mappingFactory = Mock(MappingFactory)
        def strategy = new GormMappingConfigurationStrategy(mappingFactory)
        def context = Mock(MappingContext)
        def entity = Mock(PersistentEntity)

        entity.getJavaClass() >> PropertyEntity
        mappingFactory.isSimpleType(String) >> true
        mappingFactory.isSimpleType(Integer) >> true
        mappingFactory.createPropertyDescriptor(_, _) >> { Class cls, mp ->
            new PropertyDescriptor(mp.name, cls, "get${mp.name.capitalize()}', 'set${mp.name.capitalize()}")
        }

        when:
        def props = strategy.getPersistentProperties(entity, context, null)

        then:
        props.size() == 2
        1 * mappingFactory.createSimple(entity, context, { it.name == 'name' })
        1 * mappingFactory.createSimple(entity, context, { it.name == 'age' })
        0 * mappingFactory.createSimple(entity, context, { it.name == 'transientProp' })
    }

    void 'test getIdentity returns null when no identity is present'() {
        given:
        def mappingFactory = Mock(MappingFactory)
        def strategy = new GormMappingConfigurationStrategy(mappingFactory)
        def context = Mock(MappingContext)
        def entity = Mock(PersistentEntity)
        def classMapping = Mock(ClassMapping)
        def identityMapping = Mock(IdentityMapping)

        context.getPersistentEntity(NoIdEntity.name) >> entity
        entity.getJavaClass() >> NoIdEntity
        entity.getMapping() >> classMapping
        classMapping.getIdentifier() >> identityMapping
        identityMapping.getIdentifierName() >> ([] as String[])

        when:
        def result = strategy.getIdentity(NoIdEntity, context)

        then:
        result == null
    }

    void 'test getCompositeIdentity returns empty array when no identity is present'() {
        given:
        def mappingFactory = Mock(MappingFactory)
        def strategy = new GormMappingConfigurationStrategy(mappingFactory)
        def context = Mock(MappingContext)
        def entity = Mock(PersistentEntity)
        def classMapping = Mock(ClassMapping)
        def identityMapping = Mock(IdentityMapping)

        context.getPersistentEntity(NoIdEntity.name) >> entity
        entity.getJavaClass() >> NoIdEntity
        entity.getMapping() >> classMapping
        classMapping.getIdentifier() >> identityMapping
        identityMapping.getIdentifierName() >> ([] as String[])

        when:
        def result = strategy.getCompositeIdentity(NoIdEntity, context)

        then:
        result.length == 0
    }

    void 'test getOwningEntities'() {
        given:
        def strategy = new GormMappingConfigurationStrategy(Mock(MappingFactory))

        when:
        def owners = strategy.getOwningEntities(ChildEntity, Mock(MappingContext))

        then:
        owners.size() == 1
        owners.contains(ParentEntity)
    }
}

@jakarta.persistence.Entity
class AnnotatedEntity {}

@Entity
class GormAnnotatedEntity {}

class NotAnEntity {}

enum EnumEntity { FIRST }

class A {

    static hasMany = [foo: String]
}
class B extends A {

    static hasMany = [foo: Integer]
}

class SimpleIdEntity {

    Long id
}

class CompositeIdEntity {

    Long id1
    Long id2
}

class PropertyEntity {

    String name
    Integer age
    String transientProp
    static transients = ['transientProp']
}

class ParentEntity {}
class ChildEntity {

    static belongsTo = [parent: ParentEntity]
}

class NoIdEntity {}
