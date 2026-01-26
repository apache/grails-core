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

import functionaltests.Application
import grails.testing.mixin.integration.Integration
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Integration tests for Command Objects with Dependency Injection.
 * Tests the ability to inject Spring services into Grails command objects.
 */
@Integration(applicationClass = Application)
class CommandObjectDISpec extends Specification {

    private HttpClient createClient() {
        HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    // ========== Basic Service Injection Tests ==========

    def "service is properly injected into command object"() {
        given: "a request to test service injection"
        def client = createClient()

        when: "calling the test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/testServiceInjection"),
            Map
        )

        then: "service is injected"
        response.status.code == 200
        response.body().serviceInjected == true
        response.body().serviceId == 'ValidationHelperService-v1'

        cleanup:
        client.close()
    }

    def "multiple services can be injected into single command object"() {
        given: "a request to test multiple service injection"
        def client = createClient()

        when: "calling the endpoint with order command"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/testMultipleServices"),
            Map
        )

        then: "both services are injected"
        response.status.code == 200
        response.body().pricingServiceInjected == true
        response.body().notificationServiceInjected == true
        response.body().pricingServiceId == 'PricingService-v1'
        response.body().notificationServiceId == 'NotificationService-v1'

        cleanup:
        client.close()
    }

    def "@Autowired annotation works for service injection"() {
        given: "a request to test @Autowired injection"
        def client = createClient()

        when: "calling the endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/testAutowiredAnnotation"),
            Map
        )

        then: "service is injected via @Autowired"
        response.status.code == 200
        response.body().serviceInjected == true
        response.body().serviceId == 'ValidationHelperService-v1'

        cleanup:
        client.close()
    }

    // ========== Validation with Injected Service Tests ==========

    def "custom validation using injected service - valid username"() {
        given: "a valid user registration request"
        def client = createClient()

        when: "registering with valid data"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/registerUser?username=johndoe&email=john@example.com&age=25"),
            Map
        )

        then: "validation passes"
        response.status.code == 200
        response.body().valid == true
        response.body().errors.isEmpty()
        response.body().username == 'johndoe'
        response.body().usernameAvailable == true

        cleanup:
        client.close()
    }

    def "custom validation using injected service - reserved username rejected"() {
        given: "a registration request with reserved username"
        def client = createClient()

        when: "registering with reserved username 'admin'"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/registerUser?username=admin&email=admin@example.com"),
            Map
        )

        then: "validation fails with username error"
        response.status.code == 200
        response.body().valid == false
        response.body().errors.any { it.field == 'username' }
        response.body().usernameAvailable == false

        cleanup:
        client.close()
    }

    @Unroll
    def "email domain validation - #email is #expectedResult"() {
        given: "a registration request with specific email"
        def client = createClient()

        when: "registering with specific email"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/registerUser?username=testuser&email=${URLEncoder.encode(email, 'UTF-8')}"),
            Map
        )

        then: "validation result is as expected"
        response.status.code == 200
        response.body().valid == expectedValid

        cleanup:
        client.close()

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
        given: "a registration request with specific age"
        def client = createClient()

        when: "registering with specific age"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/registerUser?username=testuser&email=user@example.com&age=${age}"),
            Map
        )

        then: "validation result matches expectation"
        response.status.code == 200
        if (expectedValid) {
            response.body().errors.findAll { it.field == 'age' }.isEmpty()
        } else {
            response.body().errors.any { it.field == 'age' }
        }

        cleanup:
        client.close()

        where:
        age  | expectedValid | expectedResult
        18   | true          | 'valid (minimum)'
        25   | true          | 'valid'
        120  | true          | 'valid (maximum)'
        17   | false         | 'invalid (too young)'
        121  | false         | 'invalid (too old)'
    }

    def "phone number validation using service"() {
        given: "a registration request with phone number"
        def client = createClient()

        when: "registering with valid phone"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/registerUser?username=testuser&email=user@example.com&phone=555-123-4567"),
            Map
        )

        then: "validation passes for valid phone"
        response.status.code == 200
        response.body().errors.findAll { it.field == 'phone' }.isEmpty()

        cleanup:
        client.close()
    }

    def "phone number validation fails for invalid phone"() {
        given: "a registration request with invalid phone"
        def client = createClient()

        when: "registering with too short phone"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/registerUser?username=testuser&email=user@example.com&phone=123"),
            Map
        )

        then: "validation fails for invalid phone"
        response.status.code == 200
        response.body().errors.any { it.field == 'phone' }

        cleanup:
        client.close()
    }

    // ========== Calculated Properties Tests ==========

    @Unroll
    def "discount calculation - quantity #quantity gives #expectedDiscount discount"() {
        given: "an order with specific quantity"
        def client = createClient()

        when: "calculating order"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/calculateOrder?productName=Widget&quantity=${quantity}&unitPrice=10.00"),
            Map
        )

        then: "discount is calculated correctly"
        response.status.code == 200
        response.body().discount == expectedDiscount

        cleanup:
        client.close()

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
        given: "an order for 100 items at 10 dollars each"
        def client = createClient()

        when: "calculating order"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/calculateOrder?productName=Widget&quantity=100&unitPrice=10.00"),
            Map
        )

        then: "total price includes 25% discount (100*10=1000, minus 25% = 750)"
        response.status.code == 200
        response.body().totalPrice == 750.0

        cleanup:
        client.close()
    }

    def "price with tax calculation"() {
        given: "an order for 10 items at 100 dollars each"
        def client = createClient()

        when: "calculating order"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/calculateOrder?productName=Premium+Widget&quantity=10&unitPrice=100.00"),
            Map
        )

        then: "price with tax is calculated (10*100=1000, 10% discount=900, 8% tax=972)"
        response.status.code == 200
        response.body().totalPrice == 900.0
        response.body().priceWithTax == 972.0

        cleanup:
        client.close()
    }

    // ========== Order Validation Tests ==========

    def "order validation with valid data"() {
        given: "a valid order"
        def client = createClient()

        when: "validating order"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/validateOrder?productName=Widget&quantity=5&unitPrice=25.00"),
            Map
        )

        then: "order is valid"
        response.status.code == 200
        response.body().valid == true
        response.body().errors.isEmpty()

        cleanup:
        client.close()
    }

    def "order validation fails for price out of range"() {
        given: "an order with price too high"
        def client = createClient()

        when: "validating order with excessive price"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/validateOrder?productName=Widget&quantity=5&unitPrice=99999.00"),
            Map
        )

        then: "order is invalid due to price"
        response.status.code == 200
        response.body().valid == false
        response.body().errors.any { it.field == 'unitPrice' }

        cleanup:
        client.close()
    }

    // ========== Service Method Invocation Tests ==========

    def "command object can invoke service method to send notification"() {
        given: "a notification request"
        def client = createClient()

        when: "sending order notification"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/sendOrderNotification?customerEmail=customer@example.com&orderId=ORD-12345&message=Thank+you+for+your+order!"),
            Map
        )

        then: "notification is sent successfully"
        response.status.code == 200
        response.body().notificationSent == true
        response.body().lastNotification.recipient == 'customer@example.com'
        response.body().lastNotification.message == 'Thank you for your order!'

        cleanup:
        client.close()
    }

    // ========== Prototype Scope Tests ==========

    def "command object is prototype scoped - fresh instance per request"() {
        given: "two separate requests"
        def client = createClient()

        when: "making first request"
        def response1 = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/testPrototypeScope"),
            Map
        )

        then: "counter is 2 (incremented twice within request)"
        response1.status.code == 200
        response1.body().counter == 2

        when: "making second request"
        def response2 = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/testPrototypeScope"),
            Map
        )

        then: "counter is still 2 (fresh command instance)"
        response2.status.code == 200
        response2.body().counter == 2
        response2.body().expectedValue == 2

        cleanup:
        client.close()
    }

    // ========== Service Persistence Tests ==========

    def "service remains injected after validation"() {
        given: "a request to test service persistence"
        def client = createClient()

        when: "testing service after validation"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/testServiceAfterValidation?username=admin&email=test@example.com"),
            Map
        )

        then: "service remains available after multiple validations"
        response.status.code == 200
        response.body().serviceAfterFirst == true
        response.body().serviceAfterSecond == true

        cleanup:
        client.close()
    }

    // ========== Optional Service Tests ==========

    def "missing optional service is handled gracefully"() {
        given: "a command with optional service"
        def client = createClient()

        when: "testing optional service"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/testOptionalService?data=test"),
            Map
        )

        then: "required service present, optional is null"
        response.status.code == 200
        response.body().requiredServicePresent == true
        response.body().optionalServicePresent == false

        cleanup:
        client.close()
    }

    // ========== State Dependent Validation Tests ==========

    def "validation can depend on service state"() {
        given: "a request with price"
        def client = createClient()

        when: "validating with positive price"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/validateWithServiceState?price=100.00"),
            Map
        )

        then: "validation passes and shows current tax rate"
        response.status.code == 200
        response.body().valid == true
        response.body().currentTaxRate == 0.08

        cleanup:
        client.close()
    }

    // ========== Edge Cases ==========

    def "command object handles valid user request"() {
        given: "a basic valid request"
        def client = createClient()

        when: "validating user"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/registerUser?username=validuser&email=user@example.com"),
            Map
        )

        then: "request succeeds"
        response.status.code == 200
        response.body().username == 'validuser'

        cleanup:
        client.close()
    }

    @Unroll
    def "reserved username '#username' is rejected"() {
        given: "a registration request with reserved username"
        def client = createClient()

        when: "registering with reserved username"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/commandDI/registerUser?username=${username}&email=test@example.com"),
            Map
        )

        then: "validation fails"
        response.status.code == 200
        response.body().valid == false
        response.body().usernameAvailable == false

        cleanup:
        client.close()

        where:
        username << ['admin', 'root', 'system', 'administrator', 'ADMIN', 'Root']
    }
}
