package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.cfg.Settings
import org.springframework.core.env.PropertyResolver
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 13/09/2016.
 */
class DefaultConstraintsSpec extends Specification {

    @Shared PropertyResolver configuration = DatastoreUtils.createPropertyResolver(
            (Settings.SETTING_DB_CREATE):'create',
        'grails.gorm.default.constraints':{
            '*'(nullable: true)
        }
    )
    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(configuration,Book)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/746')
    void "Test that when constraints are nullable true by default, they can be altered to nullable false"() {
        when:"An object is validated"
        Book book = new Book()
        book.validate()

        then:"It has errors"
        book.hasErrors()
        book.errors.getFieldError("title")

        when:"The title is set"
        book.title = "The Stand"
        book.clearErrors()
        book.validate()

        then:"It validates"
        !book.hasErrors()

        when:"Validation is bypassed"
        book.title = null
        book.save(validate:false)

        then:"A constraint violation exception is thrown"
        Book.count() == 0
        thrown DataIntegrityViolationException
    }
}

@Entity
class Book {
    String title
    String author

    static constraints = {
        title nullable:false
    }
}
