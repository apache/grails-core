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

package grails.test.mixin

import grails.persistence.Entity
import grails.testing.gorm.DataTest
import spock.lang.Specification

class CascadeValidationForEmbeddedSpec extends Specification implements DataTest {

    void setupSpec() {
        mockDomains(Company, CompanyAddress)
    }

    void "Test that validation cascades to embedded entities"() {

        when:"An entity with an invalid embedded entity is created"
            def company = new Company()
            company.address = new CompanyAddress()

        then:"The entity is invalid"
            company.validate() == false

        when:"The embedded entity is made valid"
            company.address.country = "Spain"

        then:"The root entity validates"
            company.validate() == true
    }
}

@Entity
class Company {
    CompanyAddress address

    static embedded = ['address']

    static constraints = {
        address(nullable:false)
    }
}

@Entity
class CompanyAddress {
    String country

    static constraints = {
        country(blank:false)
    }
}