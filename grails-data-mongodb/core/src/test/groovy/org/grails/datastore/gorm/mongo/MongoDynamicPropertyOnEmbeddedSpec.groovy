package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.Document
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class MongoDynamicPropertyOnEmbeddedSpec extends GormDatastoreSpec{

    @Issue('GPMONGODB-290')
    void "Test that accessing dynamic attributes on embedded objects use the embedded collection"() {
        when:"An embedded collection is created"
            Container.collection.insertOne(new Document(name:'big box of items',
                    contents:(0..9).collect { [name:"Item $it"]}))
            def collectionNames = Container.DB.listCollectionNames().sort()

        then:"The embedded collection is valid"
            Container.count() == 1
            Container.first().contents.size() == 10
            Container.first().contents.first().name ==~ /Item \d/
            collectionNames.any { it =~ /^container\b/ }
            !collectionNames.any { it =~ /^item\b/ }
            session.clear()

        when:"An embedded dynamic property is accessed"
            Container.first().contents.first().nonexistentProperty == null
        then:"A collection is not created for the embedded property"
            Container.DB.listCollectionNames().sort() == collectionNames
            !collectionNames.any { it =~ /^item\b/ }
    }

    @Override
    List getDomainClasses() {
        [Container]
    }
}

@Entity
class Container {
    static mapWith='mongo'
    static embedded = ['contents']
    static mapping = {
        version false
        cache false
    }
    static constraints = {
        contents nullable:true
    }


    ObjectId id
    String name

    Set<Item> contents = new HashSet<Item>()
}
@Entity
class Item {
    static mapWith='mongo'
    static mapping = {
        version false
    }
    static constraints = {

    }
    static belongsTo = Container

    ObjectId id
    String name


}