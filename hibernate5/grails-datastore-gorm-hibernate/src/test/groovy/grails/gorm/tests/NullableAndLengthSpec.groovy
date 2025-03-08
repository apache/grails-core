package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 20/10/16.
 */
class NullableAndLengthSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Node)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10107')
    void "Test nullable and length mapping"() {
        when:"An object is persisted that violates the length mapping"
        new Node(label: "AAAAAAAAAAAA").save(flush:true)

        then:"An exception was thrown"
        thrown(DataIntegrityViolationException)
    }

}
@Entity
class Node {
    String label

    static constraints = {
        label nullable: true
    }

    static mapping = {
        label length: 6
    }
}