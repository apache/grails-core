/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
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

        then: "Backing maps are SHARED in DISCRIMINATOR mode"
        map1.is(map2)
        indices1.is(indices2)
        
        and: "Data is NOT isolated at the map level because they share the map"
        map2.get("foo") == "bar"
        
        and: "The physical map contains the keys without prefixes"
        datastore.sharedState.inmemoryData.containsKey("foo")
        datastore.sharedState.inmemoryData.get("foo") == "bar"
        datastore.sharedState.indices.containsKey("idx1")
    }
}
