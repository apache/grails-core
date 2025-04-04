package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 03/05/2017.
 */
class SaveWithInvalidEntitySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(A, B)

    /**
     * This currently fails with a NPE. See explanation https://github.com/grails/grails-core/issues/10604#issuecomment-298943022
     */
    @Rollback
    @Ignore
    @Issue('https://github.com/grails/grails-core/issues/10604')
    void "test save with an invalid entity"() {
        when:
        hibernateDatastore.currentSession.persist(new A(b:new B(field2: "test")))
        hibernateDatastore.currentSession.flush()

        then:
        A.count() == 1

    }
}

@Entity
class A {
    B b
}
@Entity
class B {
    String field1
    String field2
}
