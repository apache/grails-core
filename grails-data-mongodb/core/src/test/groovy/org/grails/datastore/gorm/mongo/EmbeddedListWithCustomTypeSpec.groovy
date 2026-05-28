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
import org.bson.types.ObjectId
import spock.lang.Issue

class EmbeddedListWithCustomTypeSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Person, Family])
    }

    @Issue('GPMONGODB-217')
    void "Test that custom types in an embedded list persist correctly"() {
        given: "An entity with a custom type property"
        final birthdate = new Date()
        def joan = new Person(name: 'joan', birthday: new Birthday(birthdate))

        when: "The person is persisted inside an embedded collection"
        def black = new Family(name: 'black', members: [joan])
        black.save(flush: true)
        manager.session.clear()
        black = Family.findByName('black')

        then: "Custom type is persisted correctly"
        black != null
        black.members.size() == 1
        black.members[0].name == 'joan'
        black.members[0].birthday != null
        black.members[0].birthday.date == birthdate
    }
}

@Entity
class Family {
    ObjectId id
    String name
    List<Person> members
    static hasMany = [members: Person]
//    static embedded = ['members']
}
