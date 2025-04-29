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

class CascadeDeleteSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([CascadeUser, CascadeUserSettings])
    }

    @Issue(['GPMONGODB-187', 'GPMONGODB-285'])
    void "Test that a delete cascade from owner to child"() {
        expect: "No existing user settings"
        CascadeUserSettings.findAll().isEmpty()

        when: "An owner with a child object is saved"
        def u = new CascadeUser(name: "user2")
        def s = new CascadeUserSettings(user: u)
        u.settings = [s] as Set

        u.save(flush: true)

        and: "The owner is queried"
        def found1 = CascadeUser.findByName("user2")
        def found1a = CascadeUserSettings.findByUser(found1)

        then: "The data is correct"
        found1 != null
        found1.settings.size() == 1

        when: "The owner is deleted"
        found1.delete(flush: true)
        def found2 = CascadeUser.findByName("user2")
        def allUserSettings = CascadeUserSettings.findAll()

        then: "So is the child"
        found2 == null
        allUserSettings.isEmpty()
    }
}

@Entity
class CascadeUser {

    ObjectId id
    String name

    Set<CascadeUserSettings> settings
    static hasMany = [settings: CascadeUserSettings]
}

@Entity
class CascadeUserSettings {

    ObjectId id
    boolean someSetting = true

    static belongsTo = [user: CascadeUser]
}
