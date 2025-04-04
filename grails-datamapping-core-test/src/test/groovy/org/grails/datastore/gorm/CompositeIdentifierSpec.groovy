package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import spock.lang.PendingFeature

/**
 * TODO: Support composite ids
 */
class CompositeIdentifierSpec extends GormDatastoreSpec {

    @PendingFeature(reason = 'Composite ids not supported')
    void "Test that a composite identifier is treated as assigned"() {
        given:"A domain model with a composite identifier"
            def u = new User(name: "Bob").save()
            def r = new Role(name: "Admin").save()
            def ur = new UserRole(user: u, role: r)
            ur.save flush: true
            session.clear()

        when:"The entity is queried"
            ur = UserRole.get(new UserRole(user: u, role: r))

        then:"it is found"
            ur != null
    }

    @Override
    List getDomainClasses() {
        [User, Role, UserRole]
    }
}

@Entity
class UserRole implements Serializable {

    User user
    Role role

    static mapping = {
        id composite: ['role', 'user']
    }
}

@Entity
class User {
    Long id
    String name
}

@Entity
class Role {
    Long id
    String name
}
