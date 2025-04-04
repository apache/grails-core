package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class BeforeInsertUpdateSpec extends GormDatastoreSpec{

    @Issue('GPMONGODB-251')
    void "Test that before insert and update events are triggered without issue"() {
        when:"A user is persisted"
            def u = new BeforeInsertUser(login: "fred", password: "bar")
            u.save(flush:true)
            session.clear()
            u = BeforeInsertUser.findByLogin("fred")

        then:"The before insert event was triggered and the password encoded"
            u != null
            u.password == 'foo'

        when:"A user is updated"
            u.password = "bar"
            u.save(flush: true)
            session.clear()
            u = BeforeInsertUser.findByLogin("fred")

        then:"The before update event was triggered"
            u != null
            u.password == 'foo'

    }

    @Override
    List getDomainClasses() {
        [BeforeInsertUser]
    }
}

@Entity
class BeforeInsertUser {

    ObjectId id
    String login
    String password

    transient isPasswordEncoded = false

    def beforeInsert() {
        if (!isPasswordEncoded) {
            encodePassword()
            isPasswordEncoded = true
        }
    }

    def beforeUpdate() {
        if (!isPasswordEncoded) {
            encodePassword()
            isPasswordEncoded = true
        }
    }

    protected void encodePassword() {
        password = "foo"
    }
}