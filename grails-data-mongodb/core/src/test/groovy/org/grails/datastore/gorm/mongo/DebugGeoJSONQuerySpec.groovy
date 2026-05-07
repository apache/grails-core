package org.grails.datastore.gorm.mongo

import org.apache.grails.data.mongo.core.MongoDatastoreSpec
import grails.mongodb.geo.GeometryCollection
import grails.mongodb.geo.Point
import grails.persistence.Entity
import com.mongodb.client.MongoCollection
import org.bson.Document

class DebugGeoJSONQuerySpec extends MongoDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([PlaceWithGeoJSONQuery])
    }

    void "test raw query for GeoJSON collection field"() {
        when: "A Place with a GeometryCollection is saved"
        def col = new GeometryCollection()
        col << Point.valueOf(5, 10)
        
        def p = new PlaceWithGeoJSONQuery(geometryCollection: col)
        p.save(flush: true, validate: false)
        def savedId = p.id
        println "Saved Place with id: ${savedId}"
        
        and: "The session is cleared"
        manager.session.clear()
        
        and: "We do a raw query for the document"
        def entity = manager.mongoDatastore.mappingContext.getPersistentEntity(PlaceWithGeoJSONQuery.name)
        def collection = manager.session.getCollection(entity)
        println "Collection: ${collection}"
        
        def query = new Document('_id', savedId)
        println "Query: ${query}"
        
        def doc = collection.find(query).first()
        println "Raw document found: ${doc}"
        
        then: "The document should exist"
        doc != null
    }
}

@Entity
class PlaceWithGeoJSONQuery {
    Long id
    Point point
    GeometryCollection geometryCollection
}
