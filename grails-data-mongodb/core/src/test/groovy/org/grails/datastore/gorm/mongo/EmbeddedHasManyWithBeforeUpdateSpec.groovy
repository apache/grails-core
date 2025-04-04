package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import org.bson.types.ObjectId

class EmbeddedHasManyWithBeforeUpdateSpec extends GormDatastoreSpec {

    void "Test embedded hasMany with beforeUpdate event"() {
        given:
            def user = User.findByName("Ratler")
            if (!user) {
               user = new User(name: "Ratler")
            }
            def address  = new UserAddress(type:"home")
            user.addresses = [address]
            user.save(flush: true)
            session.clear()

        when:
            user = User.findByName("Ratler")

        then:
            user != null
            user.addresses.size() == 1
            user.addresses[0].type == 'home'

        when:
            user.name = "Bob"
            user.save(flush:true)
            session.clear()
            user = User.findByName("bob")

        then:
            user != null
            user.addresses.size() == 1
            user.addresses[0].type == 'home'
    }

    @Override
    List getDomainClasses() {
        [User, UserAddress]
    }
}

@Entity
class User {
    ObjectId id
    String name
    List<UserAddress> addresses

    static embedded = ['addresses']
    static hasMany = [addresses:UserAddress]

    def beforeUpdate() {
        this.name = name.toLowerCase()
    }
}

@Entity
class UserAddress {
    ObjectId id
    String type
}
