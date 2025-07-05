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

class EmbeddedStringListInsideEmbeddedCollectionSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([ESLIECPerson])
    }

    void "Test that an embedded primitive string can be used inside an embedded collection"() {
        when: "A embedded collection is persisted which has an embedded string"
        def p = new ESLIECPerson(name: "Bob")
        p.cameras << new Camera(name: "Canon 50D", lenses: ["Wide", "Long"])
        p.save(flush: true)
        manager.session.clear()

        p = ESLIECPerson.get(p.id)

        then: "The embedded collection and strings can be read back correctly"
        p.name == "Bob"
        p.cameras.size() == 1
        p.cameras[0].name == "Canon 50D"
        p.cameras[0].lenses == ["Wide", "Long"]

        when: "An embedded collection is updated "
        p.cameras[0].lenses << "Other"
        p.save(flush: true)
        p = ESLIECPerson.get(p.id)

        then: "The embedded collection is updated appropriately"
        p.name == "Bob"
        p.cameras.size() == 1
        p.cameras[0].name == "Canon 50D"
        p.cameras[0].lenses == ["Wide", "Long", "Other"]
    }
}

@Entity
class ESLIECPerson {

    String id
    String name
    List<Camera> cameras = []

    static embedded = ['cameras']
}

class Camera {

    String name
    List<String> lenses = []

    static embedded = ['lenses']
}
