package org.grails.datastore.gorm.mongo.bugs

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class GPMongoDB295Spec extends GormDatastoreSpec {


    @Issue('GPMONGODB-295')
    void "Test that 'com.mongodb.DBRef cannot be cast to java.io.Serializable' exception is not thrown"() {
        given:"Some test data"
            UserGroup group = new UserGroup(name: 'group', company: 'JFrog').save(flush: true, failOnError: true)
            User user = new User(lastName: 'lastName', name: 'user', group: group).save(flush: true, failOnError: true)
            UserObject obj = new UserObject(objName: 'obj').save(flush: true, failOnError: true)
            group.addToUsers(user)
            user.addToObjects(obj)
            user.save(flush: true, failOnError: true)
            group.save(failOnError: true, flush: true)

        expect:"The exception is not thrown"
            !user.hasErrors()
            !group.hasErrors()
            getAllSavedDataWithANewSession()
    }

    private getAllSavedDataWithANewSession() {
        UserGroup.withNewSession {
            UserGroup userGroup = UserGroup.findByName('group')
            assert userGroup
            assert userGroup.users.size()
            User user = User.findByName('user')
            user.objects.size()
            assert user.objects.size() == 1
            return user
        }
    }

    @Override
    List getDomainClasses() {
        [InheritUser, ObjParent, UserGroup, User, UserObject]
    }
}

@Entity
class InheritUser {
    String id
    String name
    static constraints = {
        name unique: true, nullable: false
    }

}
@Entity
class ObjParent {
    String id
    String dateCreated
    String lastUpdated

}

@Entity
class UserGroup extends InheritUser {
    Set users
    static hasMany = [users: User]
    static mappedBy = [users: 'group']

    static constraints = {
        company nullable: false
    }

    String id
    String company
}
@Entity
class User extends InheritUser {
    Set objects
    static hasMany = [objects: UserObject]
    UserGroup group
    static belongsTo = [group: UserGroup]
    static constraints = {
        group nullable: false
        lastName nullable: false
    }
    String id
    String lastName
}

@Entity
class UserObject extends ObjParent {
    static constraints = {
        objName nullable: false, unique: true
    }
    String objName
    String id
    String dateCreated
    String lastUpdated
}
