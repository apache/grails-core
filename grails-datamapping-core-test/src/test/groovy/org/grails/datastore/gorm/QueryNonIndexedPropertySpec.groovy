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
package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class QueryNonIndexedPropertySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Company, CompanyAddress])
    }

    def "Test that we can query a property that has no indices specified"() {

        given: "A valid set of persisted domain instances"
        def address = new CompanyAddress(postCode: "30483").save()
        def person = new Company(name: "Bob", address: address)
        person.save(flush: true)

        when: "An indexed property is queried"
        def found = Company.findByName("Bob")

        then: "A result is returned"
        found != null
        found.name == "Bob"

        when: "A non-indexed property is queried"
        found = Company.findByAddress(address)

        then: "A result is returned"
        found != null
        found.name == "Bob"
    }
}

@Entity
class Company {
    Long id
    String name
    CompanyAddress address
}

@Entity
class CompanyAddress {
    Long id
    String postCode
}
