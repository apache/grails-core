package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import grails.mongodb.geo.Point
import org.bson.Document
import org.grails.datastore.mapping.model.types.Custom

import static grails.mongodb.mapping.MappingBuilder.*
/**
 * Created by graemerocher on 02/02/2017.
 */
class DocumentMappingSpec extends GormDatastoreSpec {

    void "test custom document mapping"() {
        when:"A document is saved with a custom mapping"
        new CustomMapping(name: "test", loc: Point.valueOf(10, 15) ).save(flush:true)
        Document doc = CustomMapping.collection.find().first()
        then:
        CustomMapping.collection.namespace.collectionName == 'mycoll'
        CustomMapping.collection.namespace.databaseName == 'mydb'
        doc.get("my_name") == "test"
        doc.get("loc").inspect() == '[\'type\':\'Point\', \'coordinates\':[10.0, 15.0]]'

    }
    @Override
    List getDomainClasses() {
        [CustomMapping]
    }
}

@Entity
class CustomMapping implements MongoEntity<CustomMapping> {

    String name
    Point loc

    static mapping = document {
        collection "mycoll"
        database "mydb"
        name property {
            reference false
            attr "my_name"
        }
        loc property {
            geoIndex "2dsphere"
        }
    }
}
