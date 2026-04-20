package org.grails.datastore.mapping.simple

import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.gorm.GormEnhancer
import grails.gorm.annotation.Entity
import spock.lang.Specification
import org.grails.datastore.mapping.simple.connections.SimpleMapConnectionSourceFactory

class SimpleMapDatastoreSpec extends Specification {

    void "test that multiple datastores share the same backing map for the same connection"() {
        given:
        // Clear global state before test
        SimpleMapConnectionSourceFactory.clearSettings()
        
        // Instance 1 with two connections
        def ds1 = new SimpleMapDatastore(["default", "one"], PersonEntity)
        
        // Instance 2 with same two connections
        def ds2 = new SimpleMapDatastore(["default", "one"], PersonEntity)
        
        expect:"Maps are shared for default"
        ds1.getBackingMap().is(ds2.getBackingMap())
        
        when:"Data is saved to instance 1 using GORM"
        GormEnhancer.doWithDatastore(ds1) {
            new PersonEntity(name: "Giggs").save(flush:true)
        }
        
        then:"It is visible in instance 2"
        ds2.getBackingMap().containsKey(PersonEntity.name)
        ds2.getBackingMap()[PersonEntity.name].size() == 1
        
        when:"Data is saved to a specific connection in instance 1"
        def ds1_one = ds1.getDatastoreForConnection("one")
        GormEnhancer.doWithDatastore(ds1_one) {
            new PersonEntity(name: "Neville").save(flush:true)
        }
        
        then:"It is isolated from 'default' but visible in other instances of 'one'"
        ds1.getBackingMap()[PersonEntity.name].size() == 1 // Only Giggs
        
        def ds2_one = ds2.getDatastoreForConnection("one")
        ds2_one.getBackingMap().is(ds1_one.getBackingMap())
        ds2_one.getBackingMap()[PersonEntity.name].size() == 1 // Only Neville
        
        cleanup:
        ds1.clearData()
    }
}

@Entity
class PersonEntity {
    Long id
    String name
}
