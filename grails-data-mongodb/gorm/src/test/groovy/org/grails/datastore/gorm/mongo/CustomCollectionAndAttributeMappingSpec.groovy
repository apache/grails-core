package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

/**
 * Tests for the case where a custom mapping is used.
 */
class CustomCollectionAndAttributeMappingSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [CCAAMPerson]
    }

    void "Test that custom collection and attribute names are correctly used"() {
        when:"An entity with custom collection and attribute naming is persisted"
            def p = new CCAAMPerson(groupId:10, pets:[new CCAAMPet(name:"Joe")]).save(flush:true)
            def dbo = CCAAMPerson.collection.find()
                                            .first()
        then:"The underlying mongo collection is correctly populated"
            CCAAMPerson.collection.namespace.collectionName == "persons"
            dbo.gid == 10
            dbo.ps != null
            dbo.ps.size() == 1
            dbo.ps[0].nom  == "Joe"

        when:"An entity is queried"
            session.clear()
            p = CCAAMPerson.get(p.id)

        then:"It is returned in the correct state"
            p.groupId == 10
            p.pets.size() == 1
            p.pets[0].name == "Joe"

        when:"An order by query is used"
            session.clear()
            new CCAAMPerson(groupId:5, pets:[new CCAAMPet(name:"Fred")]).save(flush:true)
            new CCAAMPerson(groupId:15, pets:[new CCAAMPet(name:"Ed")]).save(flush:true)
            def results = CCAAMPerson.list(sort:"groupId")

        then:"The results are in the correct order"
            results.size() == 3
            results[0].groupId == 5
            results[1].groupId == 10
            results[2].groupId == 15

        when:"A dynamic finder is used in a query"
            session.clear()
            results = CCAAMPerson.findAllByGroupId(10)

        then:"The results are correct"
            results.size() == 1
            results[0].groupId == 10
    }
}

@Entity
class CCAAMPerson {
    String id
    Integer groupId
    List<CCAAMPet> pets = []

    static mapWith = "mongo"

    static embedded = ['pets']

    static mapping = {
        collection 'persons'
        groupId attribute: 'gid'
        pets attribute: 'ps'
    }
}

class CCAAMPet {
    String name
    static mapping = {
        name attribute: "nom"
    }
}
