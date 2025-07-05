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

/**
 * Created by graemerocher on 20/04/16.
 */
class LastUpdatedSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([LastUpdateMe])
    }

    void "Test lastUpdated and dateCreated"() {
        when: "An object is saved"
        def lum = new LastUpdateMe(name: "Fred")
        lum.save(flush: true)
        manager.session.clear()
        lum = LastUpdateMe.get(lum.id)

        then: "The dateCreated and lastUpdated properties are populated"
        lum.dateCreated != null
        lum.lastUpdated != null

        when: "The object is updated"
        sleep 1000
        def previousLastUpdated = lum.lastUpdated
        def previousDateCreated = lum.dateCreated
        lum.name = "Bob"
        lum.save(flush: true)
        manager.session.clear()

        lum = LastUpdateMe.get(lum.id)

        then: "lastUpdated is updated but date created is the same"
        lum.lastUpdated != previousLastUpdated
        lum.lastUpdated > lum.dateCreated
        lum.dateCreated == previousDateCreated
    }
}

@Entity
class LastUpdateMe {

    String name
    Date dateCreated
    Date lastUpdated
}
