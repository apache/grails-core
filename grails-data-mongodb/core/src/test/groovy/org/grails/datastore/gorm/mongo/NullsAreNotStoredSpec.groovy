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

import grails.mongodb.MongoEntity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId
import grails.persistence.Entity

/**
 *
 */
class NullsAreNotStoredSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([NANSPerson])
    }

    void "Test that null values are not stored on domain creation"() {
        given: "A domain model with fields that are null"
        NANSPerson person = new NANSPerson()
        person.save(flush: true, validate: false)
        manager.session.clear()

        when: "The instance is read from the database"
        Document personObj = NANSPerson.collection.find(new Document('_id', person.id)).first()

        then: "The null-valued fields are not stored"
        personObj != null
        !personObj.containsKey("name")
    }

    void "Test that null values are not stored on domain update"() {
        given: "A domain model with fields that are null"
        NANSPerson person = new NANSPerson(name: "John Smith")
        person.save(flush: true, validate: false)
        manager.session.clear()

        when: "The instance is updated and read from the database"
        person = NANSPerson.get(person.id)
        person.name = null
        person.save(flush: true, validate: false)
        manager.session.clear()
        Document personObj = NANSPerson.collection.find(new Document('_id', person.id)).first()

        then: "The null-valued fields are not stored"
        personObj != null
        !personObj.containsKey("name")
    }
}

@Entity
class NANSPerson implements MongoEntity<NANSPerson> {
    ObjectId id
    String name
}
