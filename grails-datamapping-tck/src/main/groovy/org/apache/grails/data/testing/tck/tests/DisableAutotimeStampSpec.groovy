/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.Record
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author Graeme Rocher
 */
class DisableAutotimeStampSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([Record])
    }

    void "Test that when auto timestamping is disabled the dateCreated and lastUpdated properties are not set"() {
        when: "An entity is persisted"
        def r = new Record(name: "Test")
        r.save(flush: true)
        manager.session.clear()
        r = Record.get(r.id)

        then: "There are errors and dateCreated / lastUpdated were not set"
        r.lastUpdated == null
        r.dateCreated == null

        when: "The entity is saved successfully and updated"
        def d = new Date().parse('yyyy/MM/dd', '1973/07/21')
        r.lastUpdated = d
        r.dateCreated = d
        r.save(flush: true)
        manager.session.clear()
        r = Record.get(r.id)

        then: "lastUpdated is not changed"
        r != null
        r.lastUpdated == d
        r.dateCreated == d
    }
}
