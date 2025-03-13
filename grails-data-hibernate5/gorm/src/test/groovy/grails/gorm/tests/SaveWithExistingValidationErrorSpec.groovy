package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 21/10/16.
 */
class SaveWithExistingValidationErrorSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(ObjectA, ObjectB)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/9820')
    void "test saving an object with another invalid object"() {
        when:"An object with a validation error is assigned"
        def testB = new ObjectB()
        testB.save(flush: true) //fails because name is not nullable

        def testA = new ObjectA(test: testB)
        testA.save(flush: true)

        then:"Neither objects were saved"
        ObjectA.count == 0
        ObjectB.count == 0
        testA.errors.getFieldError("test.name")
    }

}
@Entity
class ObjectA {

    ObjectB test

    static constraints = {
    }
}
@Entity
class ObjectB {

    String name

    static constraints = {
    }
}