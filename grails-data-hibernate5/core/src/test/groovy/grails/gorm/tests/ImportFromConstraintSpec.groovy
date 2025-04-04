package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 20/10/16.
 */
class ImportFromConstraintSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(TestA, TestB)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Rollback
    void "test regular mas size constraints"() {
        when:"An entity is saved that validates the max size constraint"
        def result = new TestB(name: "12345678").save()

        then:"The entity was not saved"
        result == null
        TestB.count == 0

        when:"An entity is saved and validation bypassed"
        new TestB(name: "12345678").save(validate:false, flush:true)

        then:"A constraint violation is thrown"
        thrown(DataIntegrityViolationException)
    }

    @Rollback
    void "test importFrom mas size constraints"() {
        when:"An entity is saved that validates the max size constraint"
        def result = new TestA(name: "12345678").save()

        then:"The entity was not saved"
        result == null
        TestA.count == 0

        when:"An entity is saved and validation bypassed"
        new TestA(name: "12345678").save(validate:false, flush:true)

        then:"A constraint violation is thrown"
        thrown(DataIntegrityViolationException)
    }
}

@Entity
class TestB {

    String name

    static constraints = {
        name (nullable: true, maxSize: 6)
    }
}
@Entity
class TestA {

    String name

    static constraints = {
        importFrom(TestB)
    }
}