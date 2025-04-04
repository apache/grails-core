package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Issue

/**
 * Created by graemerocher on 25/01/2017.
 */
class SizeConstraintSpec extends GormDatastoreSpec {

    @Issue('https://github.com/grails/grails-data-mapping/issues/846')
    void "test size constraint is used in schema"() {
        when:"A constraint is violated"
        new SizeConstrainedUser(username:"blah", columnAa:"123456", columnBb:"123456").save(flush:true, validate:false)

        then:"an exception is thrown"
        thrown(DataIntegrityViolationException)

        when:"A constraint is violated"
        new SizeConstrainedUser(username:"blah", columnAa:"123456", columnBb:"12345").save(flush:true, validate:false)

        then:"an exception is thrown"
        thrown(DataIntegrityViolationException)

        when:"A constraints are not violated"
        session.clear()
        new SizeConstrainedUser(username:"blah", columnAa:"12345", columnBb:"12345").save(flush:true, validate:false)

        then:"the insert occurred"
        SizeConstrainedUser.count() == 1

    }
    @Override
    List getDomainClasses() {
        [SizeConstrainedUser]
    }
}

@Entity
class SizeConstrainedUser {
    String username
    String columnAa
    String columnBb

    static constraints = {
        username(blank: false)
        columnAa(nullable: true, size: 0..5)
        columnBb(nullable: true, maxSize: 5)
    }
}
