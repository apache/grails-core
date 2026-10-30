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
package grails.web.databinding

import spock.lang.Issue
import spock.lang.Specification

import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.Errors
import org.springframework.validation.Validator

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.validation.ConstrainedEntity
import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import grails.util.Holders
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.proxy.ProxyFactory

/**
 * Binds instances of the entities an application maps, which are bound with the constraints the validators of
 * their entities hold, and objects binding has to tell apart from them.
 */
@Issue('https://github.com/apache/grails-core/issues/16558')
class EntityConstraintsBindingSpec extends Specification {

    private GrailsApplication previousApplication
    private KeyValueMappingContext mappingContext

    void setup() {
        previousApplication = Holders.findApplication()
        mappingContext = new KeyValueMappingContext('test')
        def application = new DefaultGrailsApplication()
        application.mappingContext = mappingContext
        Holders.setGrailsApplication(application)
    }

    void cleanup() {
        Holders.setGrailsApplication(previousApplication)
    }

    void 'an entity is bound with the constraints its validator holds'() {
        given: 'a validator that constrains secret bindable: false, which the class itself does not'
        PersistentEntity entity = mappingContext.addPersistentEntity(ValidatedRecord)
        mappingContext.addEntityValidator(entity, new HeldConstraintsValidator(ValidatedRecord, 'secret'))
        def record = new ValidatedRecord()

        when:
        DataBindingUtils.bindObjectToInstance(record, [name: 'Grails', secret: 'Hidden'])

        then:
        record.name == 'Grails'
        record.secret == null
    }

    void 'a proxy of an entity is bound with the constraints the validator of the entity holds'() {
        given: 'a datastore that generates a proxy as a subclass of the entity it stands for, and unwraps it to the entity'
        mappingContext.proxyFactory = Stub(ProxyFactory) {
            isProxy(_ as ProxyOfValidatedRecord) >> true
            getProxiedClass(_ as ProxyOfValidatedRecord) >> ValidatedRecord
            unwrap(_ as ProxyOfValidatedRecord) >> new ValidatedRecord()
        }
        PersistentEntity entity = mappingContext.addPersistentEntity(ValidatedRecord)
        mappingContext.addEntityValidator(entity, new HeldConstraintsValidator(ValidatedRecord, 'secret'))
        def proxy = new ProxyOfValidatedRecord()

        when:
        DataBindingUtils.bindObjectToInstance(proxy, [name: 'Grails', secret: 'Hidden'])

        then:
        proxy.name == 'Grails'
        proxy.secret == null
    }

    void 'an entity without a validator is bound with the constraints its class declares'() {
        given:
        mappingContext.addPersistentEntity(SelfConstrainedRecord)
        def record = new SelfConstrainedRecord()

        when:
        DataBindingUtils.bindObjectToInstance(record, [name: 'Grails', secret: 'Hidden'])

        then:
        record.name == 'Grails'
        record.secret == null
    }

    void 'a class whose name resolves to the entity of another class is bound with the constraints it declares'() {
        given: 'an entity whose validator constrains secret bindable: false'
        PersistentEntity entity = mappingContext.addPersistentEntity(ValidatedRecord)
        mappingContext.addEntityValidator(entity, new HeldConstraintsValidator(ValidatedRecord, 'secret'))

        and: 'a class that is not a proxy, but whose name the mapping context cuts at the proxy marker to the name of that entity'
        def probe = new ValidatedRecord_$$_Probe()
        assert mappingContext.getPersistentEntity(ValidatedRecord_$$_Probe.name).is(entity)

        when:
        DataBindingUtils.bindObjectToInstance(probe, [name: 'Grails', secret: 'Shown'])

        then: 'the constraints of the class were used, not those of the entity'
        probe.name == null
        probe.secret == 'Shown'
    }
}

class ValidatedRecord {

    Long id
    String name
    String secret
}

// Stands for the subclass a datastore generates at runtime to proxy an instance of the entity it extends.
class ProxyOfValidatedRecord extends ValidatedRecord {
}

class SelfConstrainedRecord {

    Long id
    String name
    String secret

    static constraints = {
        secret bindable: false
    }
}

@SuppressWarnings('ClassName')
class ValidatedRecord_$$_Probe {

    String name
    String secret

    static constraints = {
        name bindable: false
    }
}

// Holds the constraints of the entity it validates, as the validator GORM creates for an entity does.
class HeldConstraintsValidator implements Validator, ConstrainedEntity {

    final Map<String, ConstrainedProperty> constrainedProperties

    HeldConstraintsValidator(Class<?> owner, String unbindablePropertyName) {
        def unbindableProperty = new DefaultConstrainedProperty(owner, unbindablePropertyName, String,
                new DefaultConstraintRegistry(new StaticMessageSource()))
        unbindableProperty.addMetaConstraint('bindable', false)
        constrainedProperties = [(unbindablePropertyName): (ConstrainedProperty) unbindableProperty]
    }

    @Override
    boolean supports(Class<?> clazz) {
        true
    }

    @Override
    void validate(Object target, Errors errors) {
    }
}
