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
package functionaltests.commanddi

import io.micronaut.http.HttpRequest
import spock.lang.Specification
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.httpclient.HttpClientSupport

/**
 * Integration tests for Command Objects with Dependency Injection.
 * Tests the ability to inject Spring services into Grails command objects.
 */
@Integration
class CommandObjectDISpec extends Specification implements HttpClientSupport {

    // ========== Basic Service Injection Tests ==========

    def "service is properly injected into command object"() {
        when: "calling the test endpoint"
        def response = httpClient.exchange(
            '/commandDI/testServiceInjection',
            Map
        )

        then: "service is injected"
        response.status.code == 200
        response.body().serviceInjected == true
        response.body().serviceId == 'ValidationHelperService-v1'
    }

    def "multiple services can be injected into single command object"() {
        when: "calling the endpoint with order command"
        def response = httpClient.exchange(
            '/commandDI/testMultipleServices',
            Map
        )

        then: "both services are injected"
        response.status.code == 200
        response.body().pricingServiceInjected == true
        response.body().notificationServiceInjected == true
        response.body().pricingServiceId == 'PricingService-v1'
        response.body().notificationServiceId == 'NotificationService-v1'
    }

    def "@Autowired annotation works for service injection"() {
        when: "calling the endpoint"
        def response = httpClient.exchange(
            '/commandDI/testAutowiredAnnotation',
            Map
        )

        then: "service is injected via @Autowired"
        response.status.code == 200
        response.body().serviceInjected == true
        response.body().serviceId == 'ValidationHelperService-v1'
    }

    // ========== Validation with Injected Service Tests ==========

    def "custom validation using injected service - valid username"() {
        when: "registering with valid data"
        def response = httpClient.exchange(
            '/commandDI/registerUser?username=johndoe&email=john@example.com&age=25',
            Map
        )

        then: "validation passes"
        response.status.code == 200
        response.body().valid == true
        response.body().errors.isEmpty()
        response.body().username == 'johndoe'
        response.body().usernameAvailable == true
    }

    def "custom validation using injected service - reserved username rejected"() {
        when: "registering with reserved username 'admin'"
        def response = httpClient.exchange(
            '/commandDI/registerUser?username=admin&email=admin@example.com',
            Map
        )

        then: "validation fails with username error"
        response.status.code == 200
        response.body().valid == false
        response.body().errors.any { it.field == 'username' }
        response.body().usernameAvailable == false
    }

    @Unroll
    def "email domain validation - #email is #expectedResult"() {
        when: "registering with specific email"
        def response = httpClient.exchange(
            "/commandDI/registerUser?username=testuser&email=${URLEncoder.encode(email, 'UTF-8')}",
            Map
        )

        then: "validation result is as expected"
        response.status.code == 200
        response.body().valid == expectedValid

        where:
        email                    | expectedValid | expectedResult
        'user@example.com'       | true          | 'valid'
        'user@company.com'       | true          | 'valid'
        'user@test.org'          | true          | 'valid'
        'user@unknown.com'       | false         | 'invalid'
        'user@gmail.com'         | false         | 'invalid'
    }

    @Unroll
    def "age validation - age #age is #expectedResult"() {
        when: "registering with specific age"
        def response = httpClient.exchange(
            "/commandDI/registerUser?username=testuser&email=user@example.com&age=${age}",
            Map
        )

        then: "validation result matches expectation"
        response.status.code == 200
        if (expectedValid) {
            response.body().errors.findAll { it.field == 'age' }.isEmpty()
        } else {
            response.body().errors.any { it.field == 'age' }
        }

        where:
        age  | expectedValid | expectedResult
        18   | true          | 'valid (minimum)'
        25   | true          | 'valid'
        120  | true          | 'valid (maximum)'
        17   | false         | 'invalid (too young)'
        121  | false         | 'invalid (too old)'
    }

    def "phone number validation using service"() {
        when: "registering with valid phone"
        def response = httpClient.exchange(
            '/commandDI/registerUser?username=testuser&email=user@example.com&phone=555-123-4567',
            Map
        )

        then: "validation passes for valid phone"
        response.status.code == 200
        response.body().errors.findAll { it.field == 'phone' }.isEmpty()
    }

    def "phone number validation fails for invalid phone"() {
        when: "registering with too short phone"
        def response = httpClient.exchange(
            '/commandDI/registerUser?username=testuser&email=user@example.com&phone=123',
            Map
        )

        then: "validation fails for invalid phone"
        response.status.code == 200
        response.body().errors.any { it.field == 'phone' }
    }

    // ========== Calculated Properties Tests ==========

    @Unroll
    def "discount calculation - quantity #quantity gives #expectedDiscount discount"() {
        when: "calculating order"
        def response = httpClient.exchange(
            "/commandDI/calculateOrder?productName=Widget&quantity=${quantity}&unitPrice=10.00",
            Map
        )

        then: "discount is calculated correctly"
        response.status.code == 200
        response.body().discount == expectedDiscount

        where:
        quantity | expectedDiscount
        1        | 0.0
        9        | 0.0
        10       | 0.10
        49       | 0.10
        50       | 0.15
        99       | 0.15
        100      | 0.25
        200      | 0.25
    }

    def "total price calculation with discount"() {
        when: "calculating order"
        def response = httpClient.exchange(
            '/commandDI/calculateOrder?productName=Widget&quantity=100&unitPrice=10.00',
            Map
        )

        then: "total price includes 25% discount (100*10=1000, minus 25% = 750)"
        response.status.code == 200
        response.body().totalPrice == 750.0
    }

    def "price with tax calculation"() {
        when: "calculating order"
        def response = httpClient.exchange(
            '/commandDI/calculateOrder?productName=Premium+Widget&quantity=10&unitPrice=100.00',
            Map
        )

        then: "price with tax is calculated (10*100=1000, 10% discount=900, 8% tax=972)"
        response.status.code == 200
        response.body().totalPrice == 900.0
        response.body().priceWithTax == 972.0
    }

    // ========== Order Validation Tests ==========

    def "order validation with valid data"() {
        when: "validating order"
        def response = httpClient.exchange(
            '/commandDI/validateOrder?productName=Widget&quantity=5&unitPrice=25.00',
            Map
        )

        then: "order is valid"
        response.status.code == 200
        response.body().valid == true
        response.body().errors.isEmpty()
    }

    def "order validation fails for price out of range"() {
        when: "validating order with excessive price"
        def response = httpClient.exchange(
            '/commandDI/validateOrder?productName=Widget&quantity=5&unitPrice=99999.00',
            Map
        )

        then: "order is invalid due to price"
        response.status.code == 200
        response.body().valid == false
        response.body().errors.any { it.field == 'unitPrice' }
    }

    // ========== Service Method Invocation Tests ==========

    def "command object can invoke service method to send notification"() {
        when: "sending order notification"
        def response = httpClient.exchange(
            '/commandDI/sendOrderNotification?customerEmail=customer@example.com&orderId=ORD-12345&message=Thank+you+for+your+order!',
            Map
        )

        then: "notification is sent successfully"
        response.status.code == 200
        response.body().notificationSent == true
        response.body().lastNotification.recipient == 'customer@example.com'
        response.body().lastNotification.message == 'Thank you for your order!'
    }

    // ========== Prototype Scope Tests ==========

    def "command object is prototype scoped - fresh instance per request"() {
        when: "making first request"
        def response1 = httpClient.exchange(
            '/commandDI/testPrototypeScope',
            Map
        )

        then: "counter is 2 (incremented twice within request)"
        response1.status.code == 200
        response1.body().counter == 2

        when: "making second request"
        def response2 = httpClient.exchange(
            '/commandDI/testPrototypeScope',
            Map
        )

        then: "counter is still 2 (fresh command instance)"
        response2.status.code == 200
        response2.body().counter == 2
        response2.body().expectedValue == 2
    }

    // ========== Service Persistence Tests ==========

    def "service remains injected after validation"() {
        when: "testing service after validation"
        def response = httpClient.exchange(
            '/commandDI/testServiceAfterValidation?username=admin&email=test@example.com',
            Map
        )

        then: "service remains available after multiple validations"
        response.status.code == 200
        response.body().serviceAfterFirst == true
        response.body().serviceAfterSecond == true
    }

    // ========== Optional Service Tests ==========

    def "missing optional service is handled gracefully"() {
        when: "testing optional service"
        def response = httpClient.exchange(
            '/commandDI/testOptionalService?data=test',
            Map
        )

        then: "required service present, optional is null"
        response.status.code == 200
        response.body().requiredServicePresent == true
        response.body().optionalServicePresent == false
    }

    // ========== State Dependent Validation Tests ==========

    def "validation can depend on service state"() {
        when: "validating with positive price"
        def response = httpClient.exchange(
            '/commandDI/validateWithServiceState?price=100.00',
            Map
        )

        then: "validation passes and shows current tax rate"
        response.status.code == 200
        response.body().valid == true
        response.body().currentTaxRate == 0.08
    }

    // ========== Edge Cases ==========

    def "command object handles valid user request"() {
        when: "validating user"
        def response = httpClient.exchange(
            '/commandDI/registerUser?username=validuser&email=user@example.com',
            Map
        )

        then: "request succeeds"
        response.status.code == 200
        response.body().username == 'validuser'
    }

    @Unroll
    def "reserved username '#username' is rejected"() {
        when: "registering with reserved username"
        def response = httpClient.exchange(
            "/commandDI/registerUser?username=${username}&email=test@example.com",
            Map
        )

        then: "validation fails"
        response.status.code == 200
        response.body().valid == false
        response.body().usernameAvailable == false

        where:
        username << ['admin', 'root', 'system', 'administrator', 'ADMIN', 'Root']
    }
}
