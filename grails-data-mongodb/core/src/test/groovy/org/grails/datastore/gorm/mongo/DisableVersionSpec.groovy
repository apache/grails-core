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

class DisableVersionSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([NoVersion])
    }

    void "Test that disabling the version does not persist the version field"() {
        when: "An object with a disabled version is persisted"
        def nv = new NoVersion(name: "Bob").save(flush: true)
        manager.session.clear()
        nv = NoVersion.findByName("Bob")

        then: "The version field is not persisted"
        nv.name == "Bob"
        nv.version == null
        nv.dbo.version == null
        !nv.dbo.containsKey("version")
    }
}

@Entity
class NoVersion {

    Long id
    Long version
    String name

    static mapping = {
        version false
    }
}
