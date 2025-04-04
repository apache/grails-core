package grails.gorm.tests.validation

import grails.gorm.transactions.Rollback
import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Rollback
class UniqueFalseConstraintSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(User)

    @Issue('https://github.com/grails/grails-data-mapping/issues/1059')
    void 'unique:false constraint is ignored and does not behave as unique:true'() {
        given: 'a user'
        def user1 = new User(name: 'John')
        user1.save(flush: true)

        when: 'trying to save another user with the same name'
        def user2 = new User(name: 'John')
        user2.save(flush: true)

        then: 'both users are saved without errors'
        !user1.hasErrors()
        !user2.hasErrors()
    }
}

@Entity
class User {
    Long id
    String name

    static constraints = {
        name unique: false
    }

    static mapping = {
        table '`user`'
    }
}
