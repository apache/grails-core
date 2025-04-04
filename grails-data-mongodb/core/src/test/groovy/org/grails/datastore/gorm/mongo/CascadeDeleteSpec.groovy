package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import org.bson.types.ObjectId

import spock.lang.Issue

class CascadeDeleteSpec extends GormDatastoreSpec {

    @Issue(['GPMONGODB-187', 'GPMONGODB-285'])
    void "Test that a delete cascade from owner to child"() {
        expect:"No existing user settings"
            CascadeUserSettings.findAll().isEmpty()

        when:"An owner with a child object is saved"
            def u = new CascadeUser(name:"user2")
            def s = new CascadeUserSettings(user: u)
            u.settings = [s] as Set

            u.save(flush:true)

        and:"The owner is queried"
            def found1 = CascadeUser.findByName("user2")
            def found1a = CascadeUserSettings.findByUser(found1)

        then:"The data is correct"
            found1 != null
            found1.settings.size()  == 1

        when:"The owner is deleted"
            found1.delete(flush:true)
            def found2 = CascadeUser.findByName("user2")
            def allUserSettings = CascadeUserSettings.findAll()

        then:"So is the child"
            found2 == null
            allUserSettings.isEmpty()
    }

    @Override
    List getDomainClasses() {
        [CascadeUser, CascadeUserSettings]
    }
}

@Entity
class CascadeUser {

    ObjectId id
    String name

    Set<CascadeUserSettings> settings
    static hasMany = [settings:CascadeUserSettings]
}

@Entity
class CascadeUserSettings {

    ObjectId id
    boolean someSetting = true

    static belongsTo = [user:CascadeUser]
}
