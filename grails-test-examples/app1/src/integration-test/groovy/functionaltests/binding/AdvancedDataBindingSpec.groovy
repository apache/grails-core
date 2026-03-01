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

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
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
        def response = httpClient.exchange(
            '/advancedDataBinding/bindEmployee?firstName=John&lastName=Doe&salary=50000',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == 'John'
        response.body().lastName == 'Doe'
        response.body().salary == 50000
    }

    def "test nested object binding"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindEmployee?firstName=Jane&homeAddress.street=123+Main+St&homeAddress.city=Springfield&homeAddress.state=IL',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == 'Jane'
        response.body().homeAddress != null
        response.body().homeAddress.street == '123 Main St'
        response.body().homeAddress.city == 'Springfield'
        response.body().homeAddress.state == 'IL'
    }

    // ========== @BindUsing Annotation Tests ==========

    def "test @BindUsing annotation lowercases and trims email"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindWithBindUsing?email=John.Doe%40Example.COM',
            Map
        )

        then:
        response.status.code == 200
        response.body().email == 'john.doe@example.com'
        response.body().originalEmail == 'John.Doe@Example.COM'
    }

    def "test @BindUsing with mixed case email"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindWithBindUsing?email=TEST.User%40DOMAIN.org',
            Map
        )

        then:
        response.status.code == 200
        response.body().email == 'test.user@domain.org'
    }

    // ========== @BindingFormat Annotation Tests ==========

    def "test @BindingFormat for date parsing - MMddyyyy format"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindWithDateFormat?hireDate=01152020',
            Map
        )

        then:
        response.status.code == 200
        response.body().hireDate == '2020-01-15'
        response.body().hireDateInput == '01152020'
    }

    def "test @BindingFormat for date parsing - yyyy-MM-dd format"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindWithDateFormat?birthDate=1990-05-20',
            Map
        )

        then:
        response.status.code == 200
        response.body().birthDate == '1990-05-20'
        response.body().birthDateInput == '1990-05-20'
    }

    def "test multiple date formats in same request"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindWithDateFormat?hireDate=03012021&birthDate=1985-12-25',
            Map
        )

        then:
        response.status.code == 200
        response.body().hireDate == '2021-03-01'
        response.body().birthDate == '1985-12-25'
    }

    // ========== Collection Binding Tests (List) ==========

    def "test binding to List collection"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindTeamWithMembers?name=Engineering&members%5B0%5D.name=Alice&members%5B0%5D.role=Lead&members%5B1%5D.name=Bob&members%5B1%5D.role=Developer',
            Map
        )

        then:
        response.status.code == 200
        response.body().name == 'Engineering'
        response.body().members.size() == 2
        response.body().members[0].name == 'Alice'
        response.body().members[0].role == 'Lead'
        response.body().members[1].name == 'Bob'
        response.body().members[1].role == 'Developer'
    }

    def "test binding to List with gaps in indices"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindTeamWithMembers?name=QA&members%5B0%5D.name=Carol&members%5B2%5D.name=Dave',
            Map
        )

        then: "only non-null members are returned"
        response.status.code == 200
        response.body().name == 'QA'
        // Members with gaps in indices - we only get non-null entries
        response.body().members.size() == 2
        response.body().members.find { it.name == 'Carol' } != null
        response.body().members.find { it.name == 'Dave' } != null
    }

    // ========== Map Collection Binding Tests ==========

    def "test binding to Map collection"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindProjectWithContributors?name=GrailsCore&contributors%5Blead%5D.name=John&contributors%5Blead%5D.expertise=Architecture&contributors%5Bdev%5D.name=Jane&contributors%5Bdev%5D.expertise=Testing',
            Map
        )

        then:
        response.status.code == 200
        response.body().name == 'GrailsCore'
        response.body().contributors.lead.name == 'John'
        response.body().contributors.lead.expertise == 'Architecture'
        response.body().contributors.dev.name == 'Jane'
        response.body().contributors.dev.expertise == 'Testing'
    }

    // ========== @RequestParameter Annotation Tests ==========

    def "test @RequestParameter maps different parameter names"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindWithRequestParameter?firstName=Robert&lastName=Smith&age=30',
            Map
        )

        then:
        response.status.code == 200
        response.body().givenName == 'Robert'
        response.body().familyName == 'Smith'
        response.body().age == 30
    }

    // ========== bindData with Include/Exclude Tests ==========

    def "test bindData with include - only specified properties bound"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindWithIncludeExclude?firstName=Test&lastName=User&email=test%40example.com&salary=100000',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == 'Test'
        response.body().lastName == 'User'
        response.body().email == null
        response.body().salary == null
    }

    // ========== Selective Property Binding Tests ==========

    def "test selective property binding using subscript operator"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindSelectiveProperties?firstName=Selective&lastName=Test&email=should.not.bind%40test.com&salary=999',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == 'Selective'
        response.body().lastName == 'Test'
        response.body().email == null
        response.body().salary == null
    }

    // ========== Direct Data Binder Usage Tests ==========

    def "test using grailsWebDataBinder directly"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindUsingDirectBinder?firstName=Direct&lastName=Binder&email=DIRECT%40TEST.COM',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == 'Direct'
        response.body().lastName == 'Binder'
        // Email should be lowercased due to @BindUsing
        response.body().email == 'direct@test.com'
    }

    // ========== Command Object Binding Tests ==========

    def "test command object binding with validation - valid data"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindCommandObject?firstName=Valid&lastName=User&email=valid%40email.com',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == 'Valid'
        response.body().lastName == 'User'
        response.body().email == 'valid@email.com'
        response.body().valid == true
        response.body().errors.isEmpty()
    }

    def "test command object binding with validation - invalid data"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindCommandObject?firstName=&lastName=&email=invalid-email',
            Map
        )

        then:
        response.status.code == 200
        response.body().valid == false
        response.body().errors.contains('firstName')
        response.body().errors.contains('lastName')
    }

    def "test nested command object binding"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindNestedCommandObject?name=Contact+Person&address.street=456+Oak+Ave&address.city=Portland',
            Map
        )

        then:
        response.status.code == 200
        response.body().name == 'Contact Person'
        response.body().address.street == '456 Oak Ave'
        response.body().address.city == 'Portland'
    }

    // ========== JSON Body Binding Tests ==========

    def "test JSON body binding to command object"() {
        when:
        def response = httpClient.exchange(
            HttpRequest.POST('/advancedDataBinding/bindJsonBody', [
                firstName: 'JsonFirst',
                lastName: 'JsonLast',
                email: 'json@test.com'
            ]).contentType(MediaType.APPLICATION_JSON),
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == 'JsonFirst'
        response.body().lastName == 'JsonLast'
        response.body().email == 'json@test.com'
        response.body().valid == true
    }

    // ========== Multiple Command Objects Tests ==========

    def "test binding multiple command objects"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindMultipleCommandObjects?employee.firstName=Multi&employee.lastName=Test&address.street=789+Pine+Rd&address.city=Seattle',
            Map
        )

        then:
        response.status.code == 200
        response.body().employee.firstName == 'Multi'
        response.body().employee.lastName == 'Test'
        response.body().address.street == '789 Pine Rd'
        response.body().address.city == 'Seattle'
    }

    // ========== Empty String Conversion Tests ==========

    def "test empty string converts to null"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindEmptyStrings?firstName=&lastName=HasValue',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstNameIsNull == true
        response.body().lastName == 'HasValue'
        response.body().lastNameIsNull == false
    }

    // ========== String Trimming Tests ==========

    def "test string trimming during binding"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindWithTrimming?firstName=+++Trimmed+++',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == 'Trimmed'
        response.body().firstNameLength == 7
    }

    // ========== Type Conversion Tests ==========

    def "test valid type conversion"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindWithTypeConversion?salary=75000&firstName=TypeTest',
            Map
        )

        then:
        response.status.code == 200
        response.body().salary == 75000
        response.body().firstName == 'TypeTest'
    }

    // ========== Edge Cases ==========

    def "test binding with special characters in values"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindEmployee?firstName=O%27Brien&lastName=M%C3%BCller',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == "O'Brien"
        response.body().lastName == 'Müller'
    }

    def "test binding with unicode characters"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindEmployee?firstName=%E6%97%A5%E6%9C%AC%E8%AA%9E',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == '日本語'
    }

    def "test binding with null parameter values"() {
        when:
        def response = httpClient.exchange(
            '/advancedDataBinding/bindEmployee?firstName=TestNull',
            Map
        )

        then:
        response.status.code == 200
        response.body().firstName == 'TestNull'
        response.body().lastName == null
        response.body().homeAddress == null
    }
}
