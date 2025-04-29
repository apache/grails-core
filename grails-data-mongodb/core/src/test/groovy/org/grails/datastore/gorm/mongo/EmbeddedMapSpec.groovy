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
 * Created by graemerocher on 20/04/16.
 */
class EmbeddedMapSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([EmbeddedMapPerson])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/691')
    void "Test that persisting and loading an embedded map works as expected"() {
        when: "An entity with a map is persisted"
        new EmbeddedMapPerson(name: "John Doe",
                moreinfo: [
                        qualifications: ["graduation": "B-Tech.", "undergraduate": "MS"],
                        experience: [company1: "TO THE NEW Digital"]
                ]).save(flush: true)

        manager.session.clear()
        EmbeddedMapPerson p = EmbeddedMapPerson.first()

        then: "The entity can be retrieved correctly"
        p != null
        p.moreinfo
        p.moreinfo.qualifications == ["graduation": "B-Tech.", "undergraduate": "MS"]
        p.moreinfo.experience == [company1: "TO THE NEW Digital"]
    }
}

@Entity
class EmbeddedMapPerson {
    String name
    Map moreinfo

    static constraints = {
    }

    static embedded = ['moreinfo']
}