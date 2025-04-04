package grails.gorm.tests

import grails.gorm.tck.ChildEntity
import grails.gorm.tck.ClassWithListArgBeforeValidate
import grails.gorm.tck.ClassWithNoArgBeforeValidate
import grails.gorm.tck.ClassWithOverloadedBeforeValidate
import grails.gorm.tck.TestEntity
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Tests validation semantics.
 */
class HibernateValidationSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        return [ClassWithListArgBeforeValidate, ClassWithNoArgBeforeValidate,
                ClassWithOverloadedBeforeValidate]
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
