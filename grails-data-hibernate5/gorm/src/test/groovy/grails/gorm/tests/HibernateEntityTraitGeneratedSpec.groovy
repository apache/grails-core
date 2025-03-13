package grails.gorm.tests

import grails.gorm.transactions.Rollback
import groovy.transform.Generated
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

@Rollback
class HibernateEntityTraitGeneratedSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Club)

    void "test that all HibernateEntity trait methods are marked as Generated"() {
        // Unfortunately static methods have to check directly one by one
        expect:
        Club.getMethod('findAllWithSql', CharSequence).isAnnotationPresent(Generated)
        Club.getMethod('findWithSql', CharSequence).isAnnotationPresent(Generated)
        Club.getMethod('findAllWithSql', CharSequence, Map).isAnnotationPresent(Generated)
        Club.getMethod('findWithSql', CharSequence, Map).isAnnotationPresent(Generated)
    }

}
