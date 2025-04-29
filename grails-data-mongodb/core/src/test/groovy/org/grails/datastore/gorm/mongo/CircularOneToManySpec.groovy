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
import spock.lang.Issue
/**
 * @author Graeme Rocher
 */
class CircularOneToManySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Profile])
    }

    @Issue('GPMONGODB-254')
    void "Test store and retrieve circular one-to-many association"() {
        given: "A circular one-to-many"
        new Profile(name: "Fred")
                .addToFriends(name: "Bob")
                .addToFriends(name: "Frank")
                .save(flush: true)

        manager.session.clear()

        when: "The entity is loaded"
        def fred = Profile.get(1L)

        then: "The association is valid"
        fred.name == "Fred"
        fred.friends.size() == 2
        fred.friends.any { it.name == "Bob" }
        fred.friends.any { it.name == "Frank" }

    }

    @Issue('https://github.com/grails/gorm-mongodb/issues/7')
    void "Test that deleting a child doesn't not delete the parent in a circular association"() {
        given: "A circular one-to-many"
        new Profile(name: "Fred")
                .addToFriends(name: "Bob")
                .addToFriends(name: "Frank")
                .save(flush: true)

        manager.session.clear()

        when: "A child is deleted"
        Profile.findByName("Bob").delete(flush: true)
        manager.session.clear()

        then: "The parent wasn't deleted"
        Profile.count() == 2
    }
}

@Entity
class Profile {
    Long id
    String name
    List<Profile> friends

    static hasMany = [friends: Profile]
}