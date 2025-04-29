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

class EmbeddedSimpleObjectSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Space])
    }

    void "Test embedded non-domain object"() {
        when: "An entity with a simple non-domain embedded object is persisted"
        def s = new Space(displayName: "foo", db: new DatabaseConfig(name: "test"))
        s.save(flush: true)
        manager.session.clear()
        s = Space.get(s.id)
        then: "The embedded association is persisted correctly"
        s.db.name == 'test'
    }
}

@Entity
class Space {

    String id
    String displayName

    DatabaseConfig db

    static embedded = ['db']
}

class DatabaseConfig {
    String name
}
