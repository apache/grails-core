/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * 'License'); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import spock.lang.Requires

import grails.gorm.transactions.Rollback
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Unroll

import org.springframework.validation.Validator

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.ClassWithListArgBeforeValidate
import org.apache.grails.data.testing.tck.domains.ClassWithNoArgBeforeValidate
import org.apache.grails.data.testing.tck.domains.ClassWithOverloadedBeforeValidate
import org.apache.grails.data.testing.tck.domains.Task
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.grails.datastore.gorm.validation.CascadingValidator
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Tests validation semantics.
 */
@Requires({ System.getProperty('hibernate7.gorm.suite') == 'true' })
class ValidationHibernateSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([ClassWithListArgBeforeValidate, ClassWithNoArgBeforeValidate,
                                     ClassWithOverloadedBeforeValidate, TestEntity, Task])
    }

    @Rollback
    void 'Test validate() method'() {
        // test assumes name cannot be blank
        given:
        def t

        when:
        t = new TestEntity(name: '')
        boolean validationResult = t.validate()
        def errors = t.errors

        then:
        !validationResult
        t.hasErrors()
        errors != null
        errors.hasErrors()

        when:
        t.clearErrors()

        then:
        !t.hasErrors()
    }

    @Rollback
    void 'Test that validate is called on save()'() {
        given:
        def t

        when:
        t = new TestEntity(name: '')

        then:
        t.save() == null
        t.hasErrors() == true
        0 == TestEntity.count()

        when:
        t.clearErrors()
        t.name = 'Bob'
        t.age = 45
        t.child = new ChildEntity(name: 'Fred')
        t = t.save()

        then:
        t != null
        1 == TestEntity.count()
    }

    @Rollback
    void 'Test beforeValidate gets called on save()'() {
        given:
        def entityWithNoArgBeforeValidateMethod
        def entityWithListArgBeforeValidateMethod
        def entityWithOverloadedBeforeValidateMethod

        when:
        entityWithNoArgBeforeValidateMethod = new ClassWithNoArgBeforeValidate()
        entityWithListArgBeforeValidateMethod = new ClassWithListArgBeforeValidate()
        entityWithOverloadedBeforeValidateMethod = new ClassWithOverloadedBeforeValidate()
        entityWithNoArgBeforeValidateMethod.save()
        entityWithListArgBeforeValidateMethod.save()
        entityWithOverloadedBeforeValidateMethod.save()

        then:
        1 == entityWithNoArgBeforeValidateMethod.noArgCounter
        1 == entityWithListArgBeforeValidateMethod.listArgCounter
        1 == entityWithOverloadedBeforeValidateMethod.noArgCounter
        0 == entityWithOverloadedBeforeValidateMethod.listArgCounter
    }

    void 'Test beforeValidate gets called on validate()'() {
        given:
        def entityWithNoArgBeforeValidateMethod
        def entityWithListArgBeforeValidateMethod
        def entityWithOverloadedBeforeValidateMethod

        when:
        entityWithNoArgBeforeValidateMethod = new ClassWithNoArgBeforeValidate()
        entityWithListArgBeforeValidateMethod = new ClassWithListArgBeforeValidate()
        entityWithOverloadedBeforeValidateMethod = new ClassWithOverloadedBeforeValidate()
        entityWithNoArgBeforeValidateMethod.validate()
        entityWithListArgBeforeValidateMethod.validate()
        entityWithOverloadedBeforeValidateMethod.validate()

        then:
        1 == entityWithNoArgBeforeValidateMethod.noArgCounter
        1 == entityWithListArgBeforeValidateMethod.listArgCounter
        1 == entityWithOverloadedBeforeValidateMethod.noArgCounter
        0 == entityWithOverloadedBeforeValidateMethod.listArgCounter
    }

    void 'Test beforeValidate gets called on validate() and passing a list of field names to validate'() {
        given:
        def entityWithNoArgBeforeValidateMethod
        def entityWithListArgBeforeValidateMethod
        def entityWithOverloadedBeforeValidateMethod

        when:
        entityWithNoArgBeforeValidateMethod = new ClassWithNoArgBeforeValidate()
        entityWithListArgBeforeValidateMethod = new ClassWithListArgBeforeValidate()
        entityWithOverloadedBeforeValidateMethod = new ClassWithOverloadedBeforeValidate()
        entityWithNoArgBeforeValidateMethod.validate(['name'])
        entityWithListArgBeforeValidateMethod.validate(['name'])
        entityWithOverloadedBeforeValidateMethod.validate(['name'])

        then:
        1 == entityWithNoArgBeforeValidateMethod.noArgCounter
        1 == entityWithListArgBeforeValidateMethod.listArgCounter
        0 == entityWithOverloadedBeforeValidateMethod.noArgCounter
        1 == entityWithOverloadedBeforeValidateMethod.listArgCounter
        ['name'] == entityWithOverloadedBeforeValidateMethod.propertiesPassedToBeforeValidate
    }

    @Rollback
    void 'Test that validate works without a bound Session'() {

        given:
        def t

        when:
        manager.session.disconnect()
        def resource
        if (TransactionSynchronizationManager.hasResource(manager.session.datastore.sessionFactory)) {
            resource = TransactionSynchronizationManager.unbindResource(manager.session.datastore.sessionFactory)
        }

        t = new TestEntity(name: '')

        then:
        TransactionSynchronizationManager.getResource(manager.session.datastore.sessionFactory) == null
        t.save() == null
        t.hasErrors() == true

        when:
        TransactionSynchronizationManager.bindResource(manager.session.datastore.sessionFactory, resource)

        then:
        1 == t.errors.allErrors.size()
        0 == TestEntity.count()

        when:
        t.clearErrors()
        t.name = 'Bob'
        t.age = 45
        t.child = new ChildEntity(name: 'Fred')
        t = t.save(flush: true)

        then:
        t != null
        1 == TestEntity.count()
    }

    // Hibernate did not originally have this test and it fails for it
    @Rollback
    void 'Test validating an object that has had values rejected with an ObjectError'() {
        given:
        def t = new TestEntity(name: 'someName')

        when:
        t.errors.reject('foo')
        boolean isValid = t.validate()
        int errorCount = t.errors.errorCount

        then:
        !isValid
        1 == errorCount
    }

    // Hibernate did not originally have this test and it fails for it
    @Rollback
    void 'Test disable validation'() {
        // test assumes name cannot be blank
        given:
        def t

        when:
        t = new TestEntity(name: '', child: new ChildEntity(name: 'child'))
        boolean validationResult = t.validate()
        def errors = t.errors

        then:
        !validationResult
        t.hasErrors()
        errors != null
        errors.hasErrors()

        when:
        t = new TestEntity(name: '', child: new ChildEntity(name: 'child'))
        t.save(validate: false, flush: true)

        then:
        t.id != null
        !t.hasErrors()
    }

    @Rollback
    void 'Test validate() method'() {
        // test assumes name cannot be blank
        given:
        def t

        when:
        t = new TestEntity(name: '')
        boolean validationResult = t.validate()
        def errors = t.errors

        then:
        !validationResult
        t.hasErrors()
        errors != null
        errors.hasErrors()

        when:
        t.clearErrors()

        then:
        !t.hasErrors()
    }

    @Rollback
    void 'Test that validate is called on save()'() {

        given:
        def t

        when:
        t = new TestEntity(name: '')

        then:
        t.save() == null
        t.hasErrors() == true
        0 == TestEntity.count()

        when:
        t.clearErrors()
        t.name = 'Bob'
        t.age = 45
        t.child = new ChildEntity(name: 'Fred')
        t = t.save()

        then:
        t != null
        1 == TestEntity.count()
    }

    @Rollback
    void 'Test beforeValidate gets called on save()'() {
        given:
        def entityWithNoArgBeforeValidateMethod
        def entityWithListArgBeforeValidateMethod
        def entityWithOverloadedBeforeValidateMethod

        when:
        entityWithNoArgBeforeValidateMethod = new ClassWithNoArgBeforeValidate()
        entityWithListArgBeforeValidateMethod = new ClassWithListArgBeforeValidate()
        entityWithOverloadedBeforeValidateMethod = new ClassWithOverloadedBeforeValidate()
        entityWithNoArgBeforeValidateMethod.save()
        entityWithListArgBeforeValidateMethod.save()
        entityWithOverloadedBeforeValidateMethod.save()

        then:
        1 == entityWithNoArgBeforeValidateMethod.noArgCounter
        1 == entityWithListArgBeforeValidateMethod.listArgCounter
        1 == entityWithOverloadedBeforeValidateMethod.noArgCounter
        0 == entityWithOverloadedBeforeValidateMethod.listArgCounter
    }

    @Rollback
    void 'Test beforeValidate gets called on validate()'() {
        given:
        def entityWithNoArgBeforeValidateMethod
        def entityWithListArgBeforeValidateMethod
        def entityWithOverloadedBeforeValidateMethod

        when:
        entityWithNoArgBeforeValidateMethod = new ClassWithNoArgBeforeValidate()
        entityWithListArgBeforeValidateMethod = new ClassWithListArgBeforeValidate()
        entityWithOverloadedBeforeValidateMethod = new ClassWithOverloadedBeforeValidate()
        entityWithNoArgBeforeValidateMethod.validate()
        entityWithListArgBeforeValidateMethod.validate()
        entityWithOverloadedBeforeValidateMethod.validate()

        then:
        1 == entityWithNoArgBeforeValidateMethod.noArgCounter
        1 == entityWithListArgBeforeValidateMethod.listArgCounter
        1 == entityWithOverloadedBeforeValidateMethod.noArgCounter
        0 == entityWithOverloadedBeforeValidateMethod.listArgCounter
    }

    @Rollback
    void 'Test beforeValidate gets called on validate() and passing a list of field names to validate'() {
        given:
        def entityWithNoArgBeforeValidateMethod
        def entityWithListArgBeforeValidateMethod
        def entityWithOverloadedBeforeValidateMethod

        when:
        entityWithNoArgBeforeValidateMethod = new ClassWithNoArgBeforeValidate()
        entityWithListArgBeforeValidateMethod = new ClassWithListArgBeforeValidate()
        entityWithOverloadedBeforeValidateMethod = new ClassWithOverloadedBeforeValidate()
        entityWithNoArgBeforeValidateMethod.validate(['name'])
        entityWithListArgBeforeValidateMethod.validate(['name'])
        entityWithOverloadedBeforeValidateMethod.validate(['name'])

        then:
        1 == entityWithNoArgBeforeValidateMethod.noArgCounter
        1 == entityWithListArgBeforeValidateMethod.listArgCounter
        0 == entityWithOverloadedBeforeValidateMethod.noArgCounter
        1 == entityWithOverloadedBeforeValidateMethod.listArgCounter
        ['name'] == entityWithOverloadedBeforeValidateMethod.propertiesPassedToBeforeValidate
    }

    @Unroll
    void 'Test that validate works without a bound Session'() {
        given:
        def t
        def initialCount = TestEntity.count()

        when:
        manager.session.disconnect()
        t = new TestEntity(name: '')

        then:
        !manager.session.isConnected()
        t.save() == null
        t.hasErrors() == true
        1 == t.errors.allErrors.size()
        TestEntity.count() == initialCount

        when:
        t.clearErrors()
        t.name = 'Bob'
        t.age = 45
        t.child = new ChildEntity(name: 'Fred')
        t = t.save(flush: true)

        then:
        !manager.session.isConnected()
        t != null
        TestEntity.count() == initialCount + 1
    }

    @Unroll
    void 'Two parameter validate is called on entity validator if it implements Validator interface'() {
        given:
        def mockValidator = Mock(Validator)
        manager.session.mappingContext.addEntityValidator(persistentEntityFor(Task), mockValidator)
        def task = new Task()

        when:
        task.validate()

        then:
        1 * mockValidator.validate(task, _)
    }

    @Unroll
    void 'deepValidate parameter is honoured if entity validator implements CascadingValidator'() {
        given:
        def mockValidator = Mock(CascadingValidator)
        manager.session.mappingContext.addEntityValidator(persistentEntityFor(Task), mockValidator)
        def task = new Task()

        when:

        task.validate(validateParams)

        then:
        1 * mockValidator.validate(task, _, cascade)

        where:
        validateParams        | cascade
        [deepValidate: false] | false
        [:]                   | true
        [deepValidate: true]  | true

    }

    private PersistentEntity persistentEntityFor(Class c) {
        manager.session.mappingContext.persistentEntities.find { it.javaClass == c }
    }
}
