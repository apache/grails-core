package org.grails.datastore.gorm.mongo

import com.mongodb.client.MongoCollection
import grails.gorm.tests.GormDatastoreSpec

import grails.mongodb.MongoEntity
import spock.lang.*

@IgnoreIf( { System.getenv('TRAVIS_BRANCH') != null } )
class MongoGormEnhancerSpec extends GormDatastoreSpec{

    def "Test is MongoEntity"() {
        expect:
        MongoEntity.isAssignableFrom(MyMongoEntity)
    }
    def "Test getCollectionName static method" () {

        when:
            def collectionName = MyMongoEntity.collectionName

        then:
            collectionName == "mycollection"

    }

    def "Test getCollection static method" () {
        when:
            MongoCollection collection = MyMongoEntity.collection

        then:
            collection.namespace.collectionName == 'mycollection'
    }

    @Override
    List getDomainClasses() {
        [MyMongoEntity]
    }
}
