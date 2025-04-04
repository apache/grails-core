package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.validation.ValidationException
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SkipValidationSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Author)

    // For whatever reason it may be valid to flush & save without validation (database would obviously fail if the field is too long, but maybe the object is expected to only have an invalid validator?) so continue to support this scenario
    @Rollback
    void "calling save with flush with validate false should skip validation"() {
        when:
        new Author(name: 'false').save(failOnError: true, validate: false, flush: true)

        then:
        noExceptionThrown()
    }

    @Rollback
    void "calling save with flush and invalid attribute"() {
        when:
        new Author(name: 'ThisNameIsTooLong').save(failOnError: true, flush: true)

        then:
        thrown(ValidationException)
    }

    @Rollback
    void "calling validate with property list after save should validate again"() {
        // Save but don't flush, this causes the new author to have skipValidate = true
        Author author = new Author(name: 'Aaron').save(failOnError: true)

        when: "validate is called again with a property list"
        author.name = "ThisNameIsTooLong"
        def isValid = author.validate(['name'])

        then: "it should be invalid but it skips validation instead"
        !isValid
    }

    @Rollback
    void "calling validate with property list after save with flush should validate again"() {
        // Save but don't flush, this causes the new author to have skipValidate = true
        Author author = new Author(name: 'Aaron').save(failOnError: true, flush: true)

        when: "validate is called again with a property list"
        author.name = "ThisNameIsTooLong"
        def isValid = author.validate(['name'])

        then: "it should be invalid but it skips validation instead"
        !isValid
    }

    @Rollback
    void "calling validate with property list after save should validate again on explicit flush"() {
        // Save but don't flush, this causes the new author to have skipValidate = true
        Author author = new Author(name: 'Aaron').save(failOnError: true)

        when: "validate is called again with a property list"
        author.name = "ThisNameIsTooLong"
        Author.withSession { session ->
            session.flush()
        }

        then:
        author.hasErrors()
    }

    @Rollback
    void "calling validate with no list after save should validate again"() {
        // Save but don't flush, this causes the new author to have skipValidate = true
        Author author = new Author(name: 'Aaron').save(failOnError: true)

        when: "validate is called again without any parameters"
        author.name = "ThisNameIsTooLong"
        def isValid = author.validate()

        then: "this works since validate without params doesn't honor skipValidate for some reason"
        !isValid
    }
}

@Entity
class Author {
    String name

    static constraints = {
        name(nullable: false, maxSize: 8, validator: { val, obj ->
            if(val == "false") {
                return "name.invalid"
            }

            println "Validate called"
            true
        })
    }
}