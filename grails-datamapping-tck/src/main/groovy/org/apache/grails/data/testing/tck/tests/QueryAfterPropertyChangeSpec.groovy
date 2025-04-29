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

import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author graemerocher
 */
class QueryAfterPropertyChangeSpec extends GrailsDataTckSpec {

    void "Test that an entity is de-indexed after a change to an indexed property"() {
        given:
        def person = new Person(firstName: "Homer", lastName: "Simpson").save(flush: true)

        when:
        manager.session.clear()
        person = Person.findByFirstName("Homer")

        then:
        person != null

        when:
        person.firstName = "Marge"
        person.save(flush: true)
        manager.session.clear()
        person = Person.findByFirstName("Homer")

        then:
        Person.findByFirstName("Marge") != null
        person == null
    }
}
