package grails.gorm.tests

import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Tests validation semantics.
 */
class ValidationSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        return [ClassWithListArgBeforeValidate, ClassWithNoArgBeforeValidate,
                ClassWithOverloadedBeforeValidate]
    }


    void "Test validate() method"() {
        // test assumes name cannot be blank
        given:
        def t

        when:
        t = new TestEntity(name:"")
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


    void "Test that validate is called on save()"() {

        given:
        def t

        when:
        t = new TestEntity(name:"")

        then:
        t.save() == null
        t.hasErrors() == true
        0 == TestEntity.count()

        when:
        t.clearErrors()
        t.name = "Bob"
        t.age = 45
        t.child = new ChildEntity(name:"Fred")
        t = t.save()

        then:
        t != null
        1 == TestEntity.count()
    }

    void "Test beforeValidate gets called on save()"() {
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

    void "Test beforeValidate gets called on validate()"() {
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

    void "Test beforeValidate gets called on validate() and passing a list of field names to validate"() {
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

    void "Test that validate works without a bound Session"() {

        given:
        def t

        when:
        session.disconnect()
        def resource
        if (TransactionSynchronizationManager.hasResource(session.datastore.sessionFactory)) {
            resource = TransactionSynchronizationManager.unbindResource(session.datastore.sessionFactory)
        }

        t = new TestEntity(name:"")

        then:
        TransactionSynchronizationManager.getResource(session.datastore.sessionFactory) == null
        t.save() == null
        t.hasErrors() == true

        when:
        TransactionSynchronizationManager.bindResource(session.datastore.sessionFactory, resource)

        then:
        1 == t.errors.allErrors.size()
        0 == TestEntity.count()

        when:
        t.clearErrors()
        t.name = "Bob"
        t.age = 45
        t.child = new ChildEntity(name:"Fred")
        t = t.save(flush: true)

        then:
        t != null
        1 == TestEntity.count()
    }
}
