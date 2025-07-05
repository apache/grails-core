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
package org.grails.datastore.gorm.mongo

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class EmbeddedWithCustomFieldMappingSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([EWCFMPerson, EWCFMPet])
    }

    void "Test that embedded collections map to the correct underlying attributes"() {
        when: "An entity with custom attribute mappings is persisted"
        def p = new EWCFMPerson(groupId: 1)
        p.pets << new EWCFMPet(name: "Fred")
        p.save(flush: true)
        manager.session.clear()

        p = EWCFMPerson.get(p.id)

        then: "The data can be correctly read"
        p != null
        p.pets.size() == 1
    }
}

@Entity
class EWCFMPerson {
    String id
    Integer groupId
    List<EWCFMPet> pets = []

    static mapWith = "mongo"

    static embedded = ['pets']

    static mapping = {
        collection 'persons'
        groupId field: 'gid'
        pets field: 'ps'
    }
}

@Entity
class EWCFMPet {
    static mapWith = "mongo"
    String id
    String name
}
