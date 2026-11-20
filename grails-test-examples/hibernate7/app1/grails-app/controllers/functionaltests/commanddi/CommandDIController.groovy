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

import grails.converters.JSON
import grails.validation.Validateable
import org.springframework.beans.factory.annotation.Autowired

/**
 * Controller demonstrating command objects with dependency injection.
 */
class CommandDIController {

    /**
     * Test basic service injection into command object.
     */
    def testServiceInjection(UserRegistrationCommand cmd) {
        render([
            serviceInjected: cmd.validationHelperService != null,
            serviceId: cmd.validationHelperService?.getServiceId()
        ] as JSON)
    }

    /**
     * Test custom validation using injected service.
     */
    def registerUser(UserRegistrationCommand cmd) {
        def result = [
            valid: cmd.validate(),
            errors: cmd.errors?.allErrors?.collect { 
                [field: it.field, code: it.code] 
            } ?: [],
            username: cmd.username,
            email: cmd.email,
            usernameAvailable: cmd.validationHelperService?.isUsernameAvailable(cmd.username)
        ]
        render(result as JSON)
    }

    /**
     * Test multiple service injection.
     */
    def testMultipleServices(OrderCommand cmd) {
        render([
            pricingServiceInjected: cmd.pricingService != null,
            notificationServiceInjected: cmd.notificationService != null,
            pricingServiceId: cmd.pricingService?.getServiceId(),
            notificationServiceId: cmd.notificationService?.getServiceId()
        ] as JSON)
    }

    /**
     * Test calculated fields using injected service.
     */
    def calculateOrder(OrderCommand cmd) {
        render([
            productName: cmd.productName,
            quantity: cmd.quantity,
            unitPrice: cmd.unitPrice,
            discount: cmd.getCalculatedDiscount(),
            totalPrice: cmd.getCalculatedTotalPrice(),
            priceWithTax: cmd.getPriceWithTax()
        ] as JSON)
    }

    /**
     * Test validation with service-based rules.
     */
    def validateOrder(OrderCommand cmd) {
        render([
            valid: cmd.validate(),
            errors: cmd.errors?.allErrors?.collect {
                [field: it.field, code: it.code]
            } ?: [],
            productName: cmd.productName,
            quantity: cmd.quantity
        ] as JSON)
    }

    /**
     * Test nested command objects with DI.
     */
    def processCheckout(CheckoutCommand cmd) {
        render([
            customerValid: cmd.customer?.validate() ?: false,
            orderValid: cmd.order?.validate() ?: false,
            customerServiceInjected: cmd.customer?.validationHelperService != null,
            orderServiceInjected: cmd.order?.pricingService != null,
            totalPrice: cmd.order?.getCalculatedTotalPrice() ?: 0
        ] as JSON)
    }

    /**
     * Test command object method using injected service.
     */
    def sendOrderNotification(OrderNotificationCommand cmd) {
        def sent = cmd.sendConfirmation()
        render([
            notificationSent: sent,
            lastNotification: cmd.notificationService?.getLastNotification()
        ] as JSON)
    }

    /**
     * Test prototype scope behavior - each request gets fresh command.
     */
    def testPrototypeScope(CounterCommand cmd) {
        cmd.increment()
        cmd.increment()
        render([
            counter: cmd.getCounter(),
            expectedValue: 2  // Should always be 2 for fresh command
        ] as JSON)
    }

    /**
     * Test command validation that depends on service state.
     */
    def validateWithServiceState(StateDependentCommand cmd) {
        render([
            valid: cmd.validate(),
            errors: cmd.errors?.allErrors?.collect {
                [field: it.field, message: it.defaultMessage]
            } ?: [],
            currentTaxRate: cmd.pricingService?.getTaxRate()
        ] as JSON)
    }

    /**
     * Test @Autowired annotation in command object.
     */
    def testAutowiredAnnotation(AutowiredCommand cmd) {
        render([
            serviceInjected: cmd.validationHelperService != null,
            serviceId: cmd.validationHelperService?.getServiceId()
        ] as JSON)
    }

    /**
     * Test command object with optional service.
     */
    def testOptionalService(OptionalServiceCommand cmd) {
        render([
            requiredServicePresent: cmd.pricingService != null,
            optionalServicePresent: cmd.optionalService != null,
            calculatedValue: cmd.pricingService?.calculateDiscount(10) ?: 'N/A'
        ] as JSON)
    }

    /**
     * Test service injection persistence across validation.
     */
    def testServiceAfterValidation(UserRegistrationCommand cmd) {
        // First validation
        def firstValidation = cmd.validate()
        def serviceAfterFirst = cmd.validationHelperService != null
        
        // Modify and validate again
        cmd.username = 'newuser'
        def secondValidation = cmd.validate()
        def serviceAfterSecond = cmd.validationHelperService != null
        
        render([
            firstValidation: firstValidation,
            serviceAfterFirst: serviceAfterFirst,
            secondValidation: secondValidation,
            serviceAfterSecond: serviceAfterSecond
        ] as JSON)
    }
}

/**
 * Command object with service injection for user registration.
 */
class UserRegistrationCommand implements Validateable {
    
    // Service injected by Grails
    ValidationHelperService validationHelperService
    
    String username
    String email
    Integer age
    String phone
    
    static constraints = {
        username blank: false, validator: { val, obj ->
            if (!obj.validationHelperService) {
                return true  // Skip if service not available
            }
            if (!obj.validationHelperService.isUsernameAvailable(val)) {
                return ['username.reserved', val]
            }
            return true
        }
        email blank: false, validator: { val, obj ->
            if (!obj.validationHelperService) {
                return true
            }
            if (!obj.validationHelperService.isEmailDomainAllowed(val)) {
                return ['email.domain.not.allowed', val]
            }
            return true
        }
        age nullable: true, validator: { val, obj ->
            if (val == null) return true
            if (!obj.validationHelperService) return true
            if (!obj.validationHelperService.isAgeValid(val)) {
                return ['age.invalid.range', val]
            }
            return true
        }
        phone nullable: true, validator: { val, obj ->
            if (!val) return true
            if (!obj.validationHelperService) return true
            def result = obj.validationHelperService.validatePhoneNumber(val)
            if (!result.valid) {
                return ['phone.invalid', result.error]
            }
            return true
        }
    }
}

/**
 * Command object with multiple service injections.
 */
class OrderCommand implements Validateable {
    
    PricingService pricingService
    NotificationService notificationService
    
    String productName
    Integer quantity
    BigDecimal unitPrice
    BigDecimal minPrice = 0.01
    BigDecimal maxPrice = 10000.00
    
    static constraints = {
        productName blank: false
        quantity min: 1
        unitPrice validator: { val, obj ->
            if (!val) return ['price.required']
            if (!obj.pricingService) return true
            if (!obj.pricingService.isPriceValid(val, obj.minPrice, obj.maxPrice)) {
                return ['price.out.of.range', obj.minPrice, obj.maxPrice]
            }
            return true
        }
    }
    
    /**
     * Calculate discount using injected service.
     */
    BigDecimal getCalculatedDiscount() {
        if (!pricingService || !quantity) return 0.0
        return pricingService.calculateDiscount(quantity)
    }
    
    /**
     * Calculate total price using injected service.
     */
    BigDecimal getCalculatedTotalPrice() {
        if (!pricingService || !unitPrice || !quantity) return 0.0
        return pricingService.calculateTotalPrice(unitPrice, quantity)
    }
    
    /**
     * Calculate price with tax using injected service.
     */
    BigDecimal getPriceWithTax() {
        def total = getCalculatedTotalPrice()
        if (!pricingService || total == 0) return 0.0
        return pricingService.calculatePriceWithTax(total)
    }
}

/**
 * Command object for sending order notifications.
 */
class OrderNotificationCommand implements Validateable {
    
    NotificationService notificationService
    
    String customerEmail
    String orderId
    String message
    
    static constraints = {
        customerEmail blank: false, email: true
        orderId blank: false
        message nullable: true
    }
    
    /**
     * Send confirmation using injected service.
     */
    boolean sendConfirmation() {
        if (!notificationService || !customerEmail || !orderId) return false
        def msg = message ?: "Your order ${orderId} has been confirmed."
        return notificationService.sendNotification(customerEmail, msg)
    }
}

/**
 * Command for checkout process with nested commands.
 */
class CheckoutCommand implements Validateable {
    
    UserRegistrationCommand customer
    OrderCommand order
    
    static constraints = {
        customer nullable: false
        order nullable: false
    }
}

/**
 * Command to test prototype scope behavior.
 */
class CounterCommand implements Validateable {
    
    private int counter = 0
    
    ValidationHelperService validationHelperService
    
    void increment() {
        counter++
    }
    
    int getCounter() {
        return counter
    }
}

/**
 * Command with validation dependent on service state.
 */
class StateDependentCommand implements Validateable {
    
    PricingService pricingService
    
    BigDecimal price
    
    static constraints = {
        price validator: { val, obj ->
            if (!val) return ['price.required']
            if (!obj.pricingService) return true
            // Price must be positive after tax
            def priceWithTax = obj.pricingService.calculatePriceWithTax(val)
            if (priceWithTax <= 0) {
                return ['price.invalid.after.tax']
            }
            return true
        }
    }
}

/**
 * Command using @Autowired annotation explicitly.
 */
class AutowiredCommand implements Validateable {
    
    @Autowired
    ValidationHelperService validationHelperService
    
    String value
    
    static constraints = {
        value nullable: true
    }
}

/**
 * Command with optional service (not all services may be present).
 */
class OptionalServiceCommand implements Validateable {
    
    PricingService pricingService
    
    // This service doesn't exist - testing graceful handling
    def optionalService
    
    String data
    
    static constraints = {
        data nullable: true
    }
}
