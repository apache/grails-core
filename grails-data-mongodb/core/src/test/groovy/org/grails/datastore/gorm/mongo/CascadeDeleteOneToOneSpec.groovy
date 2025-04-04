package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

import org.bson.types.ObjectId

/**
 * @author Graeme Rocher
 */
class CascadeDeleteOneToOneSpec extends GormDatastoreSpec {

    void "Test owner deletes child in one-to-one cascade"() {

       when:"A owner with a one-to-one relation is persisted"
           def u = new SystemUser(name:"user2")
           u.settings = new UserSettings(user:u)
           u.save(flush:true)
           session.clear()
           def found1 = SystemUser.findByName("user2")
           def found1a = UserSettings.findByUser(found1)

        then:"The user is found"
            found1 != null
            found1a != null

        when:"An owner is deleted"
           found1.delete(flush:true)
           def found2 = SystemUser.findByName("user2")
           def found1b = UserSettings.findByUser(found1)

        then:"The child association is deleted too"
            assert found2 == null
            assert found1b == null
    }

    void "Test delete doesnt cascade if no belongsTo"() {
        when:"A one-to-one with no belongsTo is persisted"
            def c = new Company(name:"Apple", ceo:new Executive(name:"Tim Cook").save(), designer:new Employee(name:"Bob"))
            c.save flush:true
            session.clear()
            c = Company.get(c.id)

        then:"The relationship can be retrieved"
            c != null
            c.ceo != null
            c.designer != null

        when:"The the entity is deleted"
            c.delete flush:true
            session.clear()

        then:"The associated entity is not deleted"
            Company.count() == 0
            Employee.count() == 0
            Executive.count() == 1
    }

    @Override
    List getDomainClasses() {
        [SystemUser, UserSettings, Company, Executive,Employee]
    }
}

@Entity
class SystemUser {
   ObjectId id

   String name
   UserSettings settings
}

@Entity
class UserSettings {
   ObjectId id

   boolean someSetting = true

   SystemUser user
   static belongsTo = [user:SystemUser]

   static mapping = {
      collection "user_settings"
   }
}

@Entity
class Company {
    String id
    String name
    Executive ceo
    Employee designer
}

@Entity
class Executive {
    String id
    String name
}

@Entity
class Employee {
    String id
    String name
    static belongsTo = Company
}
