package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.validation.ValidationException
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class EmbeddedWithValidationExceptionSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(DomainWithEmbedded)

    @Rollback
    @Issue("https://github.com/grails/gorm-hibernate5/issues/110")
    void "test validation exception with embedded in domain"() {
        when:
        new DomainWithEmbedded(
                foo: 'not valid',
                myEmbedded: new MyEmbedded(
                        a: 1,
                        b: 'foo'
                )
        ).save(failOnError: true)

        then:
        thrown(ValidationException)
    }
}

@Entity
class DomainWithEmbedded {
    MyEmbedded myEmbedded
    String foo

    static embedded = ['myEmbedded']

    static constraints = {
        foo(validator: { val, self ->
            return 'not.valid.foo'
        })
    }
}

class MyEmbedded {
    Integer a
    String b

    static constraints = {
        a(nullable: true)
        b(nullalbe: true)
    }
}