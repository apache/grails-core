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
package functionaltests.binding

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

/**
 * Comprehensive integration tests for advanced data binding features.
 * 
 * Tests cover:
 * - Map-based binding with nested objects
 * - @BindUsing annotation
 * - @BindingFormat annotation for dates
 * - Collection binding (List and Map)
 * - @RequestParameter annotation
 * - bindData with include/exclude
 * - Selective property binding
 * - Direct data binder usage
 * - Type conversion errors
 * - Command object binding
 * - JSON body binding
 * - Empty string to null conversion
 * - String trimming
 */
@Integration
class AdvancedDataBindingSpec extends Specification implements HttpClientSupport {

    // ========== Map-Based Binding Tests ==========

    def "test basic map-based binding"() {
        when:
        def response = http(
            '/advancedDataBinding/bindEmployee?firstName=John&lastName=Doe&salary=50000'
        )

        then:
        response.expectJsonContains(200, [
                firstName: 'John',
                lastName: 'Doe',
                salary: 50000
        ])
    }

    def "test nested object binding"() {
        when:
        def response = http(
            '/advancedDataBinding/bindEmployee?firstName=Jane&homeAddress.street=123+Main+St&homeAddress.city=Springfield&homeAddress.state=IL'
        )

        then:
        response.expectStatus(200)
        with(response.json()) {
            firstName == 'Jane'
            homeAddress != null
            homeAddress.street == '123 Main St'
            homeAddress.city == 'Springfield'
            homeAddress.state == 'IL'
        }
    }

    // ========== @BindUsing Annotation Tests ==========

    def "test @BindUsing annotation lowercases and trims email"() {
        when:
        def response = http(
            '/advancedDataBinding/bindWithBindUsing?email=John.Doe%40Example.COM'
        )

        then:
        response.expectJson(200, [
                email: 'john.doe@example.com',
                originalEmail: 'John.Doe@Example.COM'
        ])
    }

    def "test @BindUsing with mixed case email"() {
        when:
        def response = http(
            '/advancedDataBinding/bindWithBindUsing?email=TEST.User%40DOMAIN.org'
        )

        then:
        response.expectJsonContains(200, [email: 'test.user@domain.org'])
    }

    // ========== @BindingFormat Annotation Tests ==========

    def "test @BindingFormat for date parsing - MMddyyyy format"() {
        when:
        def response = http('/advancedDataBinding/bindWithDateFormat?hireDate=01152020')

        then:
        response.expectJsonContains(200, [
                hireDate: '2020-01-15',
                hireDateInput: '01152020'
        ])
    }

    def "test @BindingFormat for date parsing - yyyy-MM-dd format"() {
        when:
        def response = http(
            '/advancedDataBinding/bindWithDateFormat?birthDate=1990-05-20',
        )

        then:
        response.expectJsonContains(200, [
                birthDate: '1990-05-20',
                birthDateInput: '1990-05-20'
        ])
    }

    def "test multiple date formats in same request"() {
        when:
        def response = http(
            '/advancedDataBinding/bindWithDateFormat?hireDate=03012021&birthDate=1985-12-25',
        )

        then:
        response.expectJsonContains(200, [
                hireDate: '2021-03-01',
                birthDate: '1985-12-25'
        ])
    }

    // ========== Collection Binding Tests (List) ==========

    def "test binding to List collection"() {
        when:
        def response = http(
            '/advancedDataBinding/bindTeamWithMembers?name=Engineering&members%5B0%5D.name=Alice&members%5B0%5D.role=Lead&members%5B1%5D.name=Bob&members%5B1%5D.role=Developer',
        )

        then:
        response.expectStatus(200)
        with(response.json()) {
            name == 'Engineering'
            members.size() == 2
            members[0].name == 'Alice'
            members[0].role == 'Lead'
            members[1].name == 'Bob'
            members[1].role == 'Developer'
        }
    }

    def "test binding to List with gaps in indices"() {
        when:
        def response = http(
            '/advancedDataBinding/bindTeamWithMembers?name=QA&members%5B0%5D.name=Carol&members%5B2%5D.name=Dave',
        )

        then: "only non-null members are returned"
        response.expectStatus(200)
        with(response.json()) {
            name == 'QA'
            // Members with gaps in indices - we only get non-null entries
            members.size() == 2
            members.find { it.name == 'Carol' } != null
            members.find { it.name == 'Dave' } != null
        }
    }

    // ========== Map Collection Binding Tests ==========

    def "test binding to Map collection"() {
        when:
        def response = http(
            '/advancedDataBinding/bindProjectWithContributors?name=GrailsCore&contributors%5Blead%5D.name=John&contributors%5Blead%5D.expertise=Architecture&contributors%5Bdev%5D.name=Jane&contributors%5Bdev%5D.expertise=Testing',
        )

        then:
        response.expectStatus(200)
        with(response.json()) {
            name == 'GrailsCore'
            contributors.lead.name == 'John'
            contributors.lead.expertise == 'Architecture'
            contributors.dev.name == 'Jane'
            contributors.dev.expertise == 'Testing'
        }
    }

    // ========== @RequestParameter Annotation Tests ==========

    def "test @RequestParameter maps different parameter names"() {
        when:
        def response = http(
            '/advancedDataBinding/bindWithRequestParameter?firstName=Robert&lastName=Smith&age=30',
        )

        then:
        response.expectJson(200, [
                givenName: 'Robert',
                familyName: 'Smith',
                age: 30
        ])
    }

    // ========== bindData with Include/Exclude Tests ==========

    def "test bindData with include - only specified properties bound"() {
        when:
        def response = http(
            '/advancedDataBinding/bindWithIncludeExclude?firstName=Test&lastName=User&email=test%40example.com&salary=100000',
        )

        then:
        response.expectStatus(200)
        with(response.json()) {
            firstName == 'Test'
            lastName == 'User'
            email == null
            salary == null
        }
    }

    // ========== Selective Property Binding Tests ==========

    def "test selective property binding using subscript operator"() {
        when:
        def response = http(
            '/advancedDataBinding/bindSelectiveProperties?firstName=Selective&lastName=Test&email=should.not.bind%40test.com&salary=999',
        )

        then:
        response.expectStatus(200)
        with(response.json()) {
            firstName == 'Selective'
            lastName == 'Test'
            email == null
            salary == null
        }
    }

    // ========== Direct Data Binder Usage Tests ==========

    def "test using grailsWebDataBinder directly"() {
        when:
        def response = http(
            '/advancedDataBinding/bindUsingDirectBinder?firstName=Direct&lastName=Binder&email=DIRECT%40TEST.COM',
        )

        then:
        response.expectJson(200, [
                firstName: 'Direct',
                lastName: 'Binder',
                // Email should be lowercased due to @BindUsing
                email: 'direct@test.com'
        ])
    }

    // ========== Command Object Binding Tests ==========

    def "test command object binding with validation - valid data"() {
        when:
        def response = http(
            '/advancedDataBinding/bindCommandObject?firstName=Valid&lastName=User&email=valid%40email.com',
        )

        then:
        response.expectStatus(200)
        with(response.json()) {
            firstName == 'Valid'
            lastName == 'User'
            email == 'valid@email.com'
            valid == true
            errors.isEmpty()
        }
    }

    def "test command object binding with validation - invalid data"() {
        when:
        def response = http(
            '/advancedDataBinding/bindCommandObject?firstName=&lastName=&email=invalid-email',
        )

        then:
        response.expectStatus(200)
        with(response.json()) {
            valid == false
            errors.contains('firstName')
            errors.contains('lastName')
        }
    }

    def "test nested command object binding"() {
        when:
        def response = http(
            '/advancedDataBinding/bindNestedCommandObject?name=Contact+Person&address.street=456+Oak+Ave&address.city=Portland',
        )

        then:
        response.expectJsonContains(200, [
                name: 'Contact Person',
                address: [
                        street: '456 Oak Ave',
                        city: 'Portland'
                ]
        ])
    }

    // ========== JSON Body Binding Tests ==========

    def "test JSON body binding to command object"() {
        when:
        def response = httpPost('/advancedDataBinding/bindJsonBody', [
                firstName: 'JsonFirst',
                lastName: 'JsonLast',
                email: 'json@test.com'
            ]
        )

        then:
        response.expectJson(200, [
            firstName: 'JsonFirst',
            lastName: 'JsonLast',
            email: 'json@test.com',
            valid: true
        ])
    }

    // ========== Multiple Command Objects Tests ==========

    def "test binding multiple command objects"() {
        when:
        def response = http(
            '/advancedDataBinding/bindMultipleCommandObjects?employee.firstName=Multi&employee.lastName=Test&address.street=789+Pine+Rd&address.city=Seattle',
        )

        then:
        response.expectJson(200, [
                employee: [
                        firstName: 'Multi',
                        lastName: 'Test'
                ],
                address: [
                        street: '789 Pine Rd',
                        city: 'Seattle'
                ]
        ])
    }

    // ========== Empty String Conversion Tests ==========

    def "test empty string converts to null"() {
        when:
        def response = http(
            '/advancedDataBinding/bindEmptyStrings?firstName=&lastName=HasValue',
        )

        then:
        response.expectJsonContains(200, [
                firstNameIsNull: true,
                lastName: 'HasValue',
                lastNameIsNull: false
        ])
    }

    // ========== String Trimming Tests ==========

    def "test string trimming during binding"() {
        when:
        def response = http(
            '/advancedDataBinding/bindWithTrimming?firstName=+++Trimmed+++',
        )

        then:
        response.expectJsonContains(200, [
                firstName: 'Trimmed',
                firstNameLength: 7
        ])
    }

    // ========== Type Conversion Tests ==========

    def "test valid type conversion"() {
        when:
        def response = http(
            '/advancedDataBinding/bindWithTypeConversion?salary=75000&firstName=TypeTest',
        )

        then:
        response.expectJsonContains(200, [
                salary: 75000,
                firstName: 'TypeTest'
        ])
    }

    // ========== Edge Cases ==========

    def "test binding with special characters in values"() {
        when:
        def response = http(
            '/advancedDataBinding/bindEmployee?firstName=O%27Brien&lastName=M%C3%BCller',
        )

        then:
        response.expectJsonContains(200, [
                firstName: "O'Brien",
                lastName: 'Müller'
        ])
    }

    def "test binding with unicode characters"() {
        when:
        def response = http(
            '/advancedDataBinding/bindEmployee?firstName=%E6%97%A5%E6%9C%AC%E8%AA%9E',
        )

        then:
        response.expectJsonContains(200, [firstName: '日本語'])
    }

    def "test binding with null parameter values"() {
        when:
        def response = http(
            '/advancedDataBinding/bindEmployee?firstName=TestNull',
        )

        then:
        response.expectStatus(200)
        with(response.json()) {
            firstName == 'TestNull'
            lastName == null
            homeAddress == null
        }
    }
}
