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

import grails.gorm.annotation.LastModifiedDate
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * Tests that properties set in beforeUpdate() are actually persisted to MongoDB.
 * This specifically tests the scenario where a property is set in beforeUpdate()
 * but was NOT explicitly modified by the user code.
 */
class BeforeUpdatePropertyPersistenceSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([UserWithBeforeUpdate, UserWithBeforeUpdateAndAutoTimestamp])
    }

    @Issue('GRAILS-15139')
    void "Test that properties set in beforeUpdate are persisted"() {
        given: "A user is created"
        def user = new UserWithBeforeUpdate(name: "Fred")
        def saved = user.save(flush: true)

        expect: "The user was saved successfully"
        saved != null
        user.id != null
        user.errors.allErrors.size() == 0

        when: "The session is cleared and user is retrieved"
        manager.session.clear()
        user = UserWithBeforeUpdate.get(user.id)

        then: "beforeInsert was called and random was set"
        user != null
        user.name == "Fred"
        user.random == "Not Updated"

        when: "The user's name is updated (but random is not explicitly modified)"
        def previousRandom = user.random
        user.name = "Bob"
        user.save(flush: true)
        manager.session.clear()
        user = UserWithBeforeUpdate.get(user.id)

        then: "beforeUpdate was called and random was changed and persisted"
        user != null
        user.name == "Bob"
        user.random != previousRandom
        user.random != "Not Updated"
        user.random.length() == 5 // UUID substring [0..4]
    }

    void "Test that beforeUpdate is called even when no properties are explicitly modified"() {
        given: "A user is created"
        def user = new UserWithBeforeUpdate(name: "Fred")
        user.save(flush: true)
        manager.session.clear()
        user = UserWithBeforeUpdate.get(user.id)
        def previousRandom = user.random

        when: "The user is saved without any explicit property changes"
        user.save(flush: true)
        manager.session.clear()
        user = UserWithBeforeUpdate.get(user.id)

        then: "beforeUpdate was called and random was updated"
        user != null
        user.random != previousRandom
        user.random != "Not Updated"
        user.random.length() == 5
    }

    void "Test that multiple updates continue to trigger beforeUpdate"() {
        given: "A user is created"
        def user = new UserWithBeforeUpdate(name: "Fred")
        user.save(flush: true)
        manager.session.clear()

        when: "The user is updated multiple times"
        def randomValues = []
        3.times {
            user = UserWithBeforeUpdate.get(user.id)
            randomValues << user.random
            user.name = "Name${it}"
            user.save(flush: true)
            manager.session.clear()
        }
        user = UserWithBeforeUpdate.get(user.id)

        then: "Each update generated a new random value"
        randomValues.size() == 3
        randomValues[0] == "Not Updated" // from beforeInsert
        randomValues[1] != "Not Updated" // first update
        randomValues[2] != randomValues[1] // second update
        user.random != randomValues[2] // third update
        user.random.length() == 5
    }

    @Issue('GRAILS-15120')
    void "Test that properties set in beforeUpdate with AutoTimestamp are persisted"() {
        given: "A user with auto timestamp is created"
        def user = new UserWithBeforeUpdateAndAutoTimestamp(name: "Fred")
        user.save(flush: true)
        manager.session.clear()

        when: "The user is retrieved"
        user = UserWithBeforeUpdateAndAutoTimestamp.get(user.id)

        then: "beforeInsert was called and random was set"
        user != null
        user.name == "Fred"
        user.random == "Not Updated"
        user.dateCreated != null
        user.lastUpdated != null
        user.modified != null

        when: "The user's name is updated"
        sleep 100 // ensure lastUpdated differs
        def previousRandom = user.random
        def previousLastUpdated = user.lastUpdated
        def previousModified = user.modified
        user.name = "Bob"
        user.save(flush: true)
        manager.session.clear()
        user = UserWithBeforeUpdateAndAutoTimestamp.get(user.id)

        then: "beforeUpdate was called, random was changed, and lastUpdated was updated"
        user != null
        user.name == "Bob"
        user.random != previousRandom
        user.random != "Not Updated"
        user.random.length() == 5
        user.lastUpdated > previousLastUpdated
        user.modified > previousModified
    }
}

@Entity
class UserWithBeforeUpdate {

    ObjectId id
    String name
    String random

    static constraints = {
        random nullable: true
    }

    def beforeInsert() {
        random = "Not Updated"
    }

    def beforeUpdate() {
        random = UUID.randomUUID().toString()[0..4]
    }
}

@Entity
class UserWithBeforeUpdateAndAutoTimestamp {

    ObjectId id
    String name
    String random
    Date dateCreated
    Date lastUpdated
    @LastModifiedDate Date modified

    static constraints = {
        random nullable: true
        modified nullable: true
    }

    def beforeInsert() {
        random = "Not Updated"
    }

    def beforeUpdate() {
        random = UUID.randomUUID().toString()[0..4]
    }
}
