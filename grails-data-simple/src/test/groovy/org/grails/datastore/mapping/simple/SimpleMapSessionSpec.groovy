package org.grails.datastore.mapping.simple

import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import grails.gorm.multitenancy.Tenants
import spock.lang.Specification

class SimpleMapSessionSpec extends Specification {

    def "test logical isolation in DISCRIMINATOR mode"() {
        given: "A datastore in DISCRIMINATOR mode"
        SimpleMapDatastore datastore = new SimpleMapDatastore(
                ["grails.gorm.multiTenancy.mode": MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR],
                SimpleMapSessionSpec.class.getPackage()
        )
        
        when: "We are in tenant 1"
        SimpleMapSession session = (SimpleMapSession) datastore.connect()
        Map map1
        Map indices1
        Tenants.withId(datastore, "1") {
            map1 = session.getBackingMap()
            indices1 = session.getIndices()
            map1.put("foo", "bar")
            indices1.put("idx1", ["a", "b"])
        }
        
        then: "Data is stored"
        map1.get("foo") == "bar"
        indices1.get("idx1") == ["a", "b"]

        when: "We are in tenant 2"
        Map map2
        Map indices2
        Tenants.withId(datastore, "2") {
            map2 = session.getBackingMap()
            indices2 = session.getIndices()
        }

        then: "Data is isolated"
        map2.get("foo") == null
        indices2.get("idx1") == null
        
        and: "The physical map contains both with prefixes"
        datastore.sharedState.inmemoryData.containsKey("1:foo")
        datastore.sharedState.inmemoryData.get("1:foo") == "bar"
        datastore.sharedState.indices.containsKey("1:idx1")
    }
}
