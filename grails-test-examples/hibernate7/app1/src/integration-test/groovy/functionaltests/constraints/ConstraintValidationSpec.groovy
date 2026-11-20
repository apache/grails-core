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
package functionaltests.constraints

import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Integration tests for GORM constraint validation.
 * Tests built-in constraints, custom validators, and cross-field validation.
 */
@Rollback
@Integration
class ConstraintValidationSpec extends Specification {

    // ========== Product Constraints Tests ==========

    def "Product with all valid fields passes validation"() {
        given: "a product with all valid field values"
        def product = new Product(
            sku: 'SKU-12345',
            name: 'Test Product',
            description: 'A test product description',
            category: 'Electronics',
            price: 99.99,
            stockQuantity: 100,
            email: 'test@example.com',
            website: 'https://example.com',
            productCode: 'ABC-1234',
            discount: 10.50
        )

        when: "validating the product"
        def isValid = product.validate()

        then: "validation passes"
        isValid
        product.errors.errorCount == 0
    }

    def "Product SKU must be unique"() {
        given: "an existing product with a SKU"
        def existingProduct = new Product(
            sku: 'UNIQUE-SKU',
            name: 'Existing Product',
            category: 'Books',
            price: 19.99,
            stockQuantity: 50
        )
        existingProduct.save(flush: true)

        and: "a new product with the same SKU"
        def newProduct = new Product(
            sku: 'UNIQUE-SKU',
            name: 'New Product',
            category: 'Books',
            price: 29.99,
            stockQuantity: 25
        )

        when: "validating the new product"
        def isValid = newProduct.validate()

        then: "validation fails due to unique constraint"
        !isValid
        newProduct.errors.hasFieldErrors('sku')
        newProduct.errors.getFieldError('sku').code == 'unique'
    }

    @Unroll
    def "Product SKU size constraint: '#sku' is #validity"() {
        given: "a product with the specified SKU"
        def product = new Product(
            sku: sku,
            name: 'Test Product',
            category: 'Electronics',
            price: 10.00,
            stockQuantity: 10
        )

        when: "validating"
        def isValid = product.validate()

        then: "validation result matches expected"
        isValid == valid

        where:
        sku                       | valid | validity
        'ABC'                     | false | 'invalid (too short)'
        'ABCDE'                   | true  | 'valid (minimum length)'
        'ABCDEFGHIJ1234567890'    | true  | 'valid (maximum length)'
        'ABCDEFGHIJ12345678901'   | false | 'invalid (too long)'
    }

    def "Product name cannot be blank"() {
        given: "a product with blank name"
        def product = new Product(
            sku: 'SKU-BLANK',
            name: '',
            category: 'Electronics',
            price: 10.00,
            stockQuantity: 10
        )

        when: "validating"
        def isValid = product.validate()

        then: "validation fails"
        !isValid
        product.errors.hasFieldErrors('name')
    }

    @Unroll
    def "Product category inList constraint: '#category' is #validity"() {
        given: "a product with the specified category"
        def product = new Product(
            sku: 'SKU-CAT01',
            name: 'Test Product',
            category: category,
            price: 10.00,
            stockQuantity: 10
        )

        when: "validating"
        def isValid = product.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !product.errors.hasFieldErrors('category')
        } else {
            !isValid && product.errors.hasFieldErrors('category')
        }

        where:
        category       | valid | validity
        'Electronics'  | true  | 'valid'
        'Books'        | true  | 'valid'
        'Clothing'     | true  | 'valid'
        'Food'         | true  | 'valid'
        'Toys'         | true  | 'valid'
        'Furniture'    | false | 'invalid'
        'Sports'       | false | 'invalid'
    }

    @Unroll
    def "Product price min/max constraint: #price is #validity"() {
        given: "a product with the specified price"
        def product = new Product(
            sku: 'SKU-PRICE',
            name: 'Test Product',
            category: 'Electronics',
            price: price,
            stockQuantity: 10
        )

        when: "validating"
        def isValid = product.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !product.errors.hasFieldErrors('price')
        } else {
            !isValid && product.errors.hasFieldErrors('price')
        }

        where:
        price        | valid | validity
        0.01         | true  | 'valid (minimum)'
        999999.99    | true  | 'valid (maximum)'
        0.00         | false | 'invalid (below minimum)'
        -1.00        | false | 'invalid (negative)'
        1000000.00   | false | 'invalid (above maximum)'
    }

    @Unroll
    def "Product stockQuantity range constraint: #quantity is #validity"() {
        given: "a product with the specified stock quantity"
        def product = new Product(
            sku: 'SKU-STOCK',
            name: 'Test Product',
            category: 'Electronics',
            price: 10.00,
            stockQuantity: quantity
        )

        when: "validating"
        def isValid = product.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !product.errors.hasFieldErrors('stockQuantity')
        } else {
            !isValid && product.errors.hasFieldErrors('stockQuantity')
        }

        where:
        quantity | valid | validity
        0        | true  | 'valid (minimum)'
        5000     | true  | 'valid (middle)'
        10000    | true  | 'valid (maximum)'
        -1       | false | 'invalid (negative)'
        10001    | false | 'invalid (above maximum)'
    }

    @Unroll
    def "Product email constraint: '#email' is #validity"() {
        given: "a product with the specified email"
        def product = new Product(
            sku: 'SKU-EMAIL',
            name: 'Test Product',
            category: 'Electronics',
            price: 10.00,
            stockQuantity: 10,
            email: email
        )

        when: "validating"
        def isValid = product.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !product.errors.hasFieldErrors('email')
        } else {
            !isValid && product.errors.hasFieldErrors('email')
        }

        where:
        email                  | valid | validity
        'test@example.com'     | true  | 'valid'
        'user.name@domain.org' | true  | 'valid'
        null                   | true  | 'valid (nullable)'
        'invalid-email'        | false | 'invalid (no @)'
        '@nodomain.com'        | false | 'invalid (no local part)'
        'no-at-sign'           | false | 'invalid'
    }

    @Unroll
    def "Product URL constraint: '#url' is #validity"() {
        given: "a product with the specified website URL"
        def product = new Product(
            sku: 'SKU-URL01',
            name: 'Test Product',
            category: 'Electronics',
            price: 10.00,
            stockQuantity: 10,
            website: url
        )

        when: "validating"
        def isValid = product.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !product.errors.hasFieldErrors('website')
        } else {
            !isValid && product.errors.hasFieldErrors('website')
        }

        where:
        url                           | valid | validity
        'https://example.com'         | true  | 'valid (https)'
        'http://example.com/path'     | true  | 'valid (with path)'
        null                          | true  | 'valid (nullable)'
        'not-a-url'                   | false | 'invalid'
        'ftp://example.com'           | true  | 'valid (ftp)'
    }

    @Unroll
    def "Product productCode matches pattern: '#code' is #validity"() {
        given: "a product with the specified product code"
        def product = new Product(
            sku: 'SKU-CODE1',
            name: 'Test Product',
            category: 'Electronics',
            price: 10.00,
            stockQuantity: 10,
            productCode: code
        )

        when: "validating"
        def isValid = product.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !product.errors.hasFieldErrors('productCode')
        } else {
            !isValid && product.errors.hasFieldErrors('productCode')
        }

        where:
        code        | valid | validity
        'ABC-1234'  | true  | 'valid'
        'XYZ-9999'  | true  | 'valid'
        null        | true  | 'valid (nullable)'
        'abc-1234'  | false | 'invalid (lowercase)'
        'ABCD-1234' | false | 'invalid (4 letters)'
        'ABC-12345' | false | 'invalid (5 digits)'
        'ABC1234'   | false | 'invalid (no dash)'
    }

    // ========== Registration Custom Validator Tests ==========

    def "Registration with all valid fields passes validation"() {
        given: "a registration with all valid field values"
        def reg = new Registration(
            username: 'john_doe',
            password: 'Password123',
            confirmPassword: 'Password123',
            email: 'john@example.com',
            birthDate: new Date() - (365 * 25),  // 25 years ago
            country: 'US',
            state: 'CA',
            termsAccepted: true
        )

        when: "validating the registration"
        def isValid = reg.validate()

        then: "validation passes"
        isValid
        reg.errors.errorCount == 0
    }

    @Unroll
    def "Registration username custom validator: '#username' is #validity"() {
        given: "a registration with the specified username"
        def reg = new Registration(
            username: username,
            password: 'Password123',
            confirmPassword: 'Password123',
            email: 'test@example.com',
            birthDate: new Date() - (365 * 25),
            country: 'Other',
            termsAccepted: true
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !reg.errors.hasFieldErrors('username')
        } else {
            !isValid && reg.errors.hasFieldErrors('username')
        }

        where:
        username       | valid | validity
        'john_doe'     | true  | 'valid'
        'user123'      | true  | 'valid'
        'A_user'       | true  | 'valid (starts with letter)'
        '123user'      | false | 'invalid (starts with number)'
        '_user'        | false | 'invalid (starts with underscore)'
        'user@name'    | false | 'invalid (special char)'
        'user name'    | false | 'invalid (space)'
    }

    @Unroll
    def "Registration password strength: '#password' is #validity"() {
        given: "a registration with the specified password"
        def reg = new Registration(
            username: 'testuser',
            password: password,
            confirmPassword: password,
            email: 'test@example.com',
            birthDate: new Date() - (365 * 25),
            country: 'Other',
            termsAccepted: true
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !reg.errors.hasFieldErrors('password')
        } else {
            !isValid && reg.errors.hasFieldErrors('password')
        }

        where:
        password        | valid | validity
        'Password123'   | true  | 'valid (upper, lower, digit)'
        'MyPass1'       | false | 'invalid (too short)'
        'password123'   | false | 'invalid (no uppercase)'
        'PASSWORD123'   | false | 'invalid (no lowercase)'
        'PasswordABC'   | false | 'invalid (no digit)'
    }

    def "Registration confirmPassword must match password"() {
        given: "a registration where passwords don't match"
        def reg = new Registration(
            username: 'testuser',
            password: 'Password123',
            confirmPassword: 'DifferentPass456',
            email: 'test@example.com',
            birthDate: new Date() - (365 * 25),
            country: 'Other',
            termsAccepted: true
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation fails on confirmPassword"
        !isValid
        reg.errors.hasFieldErrors('confirmPassword')
    }

    def "Registration birthDate age validation - too young"() {
        given: "a registration for someone under 13"
        def reg = new Registration(
            username: 'younguser',
            password: 'Password123',
            confirmPassword: 'Password123',
            email: 'test@example.com',
            birthDate: new Date() - (365 * 10),  // 10 years ago
            country: 'Other',
            termsAccepted: true
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation fails on birthDate"
        !isValid
        reg.errors.hasFieldErrors('birthDate')
    }

    def "Registration US phone format validation"() {
        given: "a US registration with invalid phone"
        def reg = new Registration(
            username: 'ususer',
            password: 'Password123',
            confirmPassword: 'Password123',
            email: 'test@example.com',
            birthDate: new Date() - (365 * 25),
            country: 'US',
            state: 'CA',
            phone: '1234567890',  // Missing formatting
            termsAccepted: true
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation fails on phone"
        !isValid
        reg.errors.hasFieldErrors('phone')
    }

    def "Registration US phone format validation - valid format"() {
        given: "a US registration with valid phone"
        def reg = new Registration(
            username: 'ususer2',
            password: 'Password123',
            confirmPassword: 'Password123',
            email: 'test@example.com',
            birthDate: new Date() - (365 * 25),
            country: 'US',
            state: 'CA',
            phone: '(555) 123-4567',
            termsAccepted: true
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation passes"
        isValid || !reg.errors.hasFieldErrors('phone')
    }

    def "Registration state required for US"() {
        given: "a US registration without state"
        def reg = new Registration(
            username: 'ususer3',
            password: 'Password123',
            confirmPassword: 'Password123',
            email: 'test@example.com',
            birthDate: new Date() - (365 * 25),
            country: 'US',
            state: null,  // Missing state
            termsAccepted: true
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation fails on state"
        !isValid
        reg.errors.hasFieldErrors('state')
    }

    def "Registration state not required for non-US"() {
        given: "a non-US registration without state"
        def reg = new Registration(
            username: 'ukuser',
            password: 'Password123',
            confirmPassword: 'Password123',
            email: 'test@example.com',
            birthDate: new Date() - (365 * 25),
            country: 'UK',
            state: null,
            termsAccepted: true
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation passes (state not required)"
        isValid || !reg.errors.hasFieldErrors('state')
    }

    def "Registration terms must be accepted"() {
        given: "a registration without accepting terms"
        def reg = new Registration(
            username: 'termuser',
            password: 'Password123',
            confirmPassword: 'Password123',
            email: 'test@example.com',
            birthDate: new Date() - (365 * 25),
            country: 'Other',
            termsAccepted: false
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation fails on termsAccepted"
        !isValid
        reg.errors.hasFieldErrors('termsAccepted')
    }

    @Unroll
    def "Registration promo code format: '#code' is #validity"() {
        given: "a registration with the specified promo code"
        def reg = new Registration(
            username: 'promouser',
            password: 'Password123',
            confirmPassword: 'Password123',
            email: 'test@example.com',
            birthDate: new Date() - (365 * 25),
            country: 'Other',
            termsAccepted: true,
            promoCode: code
        )

        when: "validating"
        def isValid = reg.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !reg.errors.hasFieldErrors('promoCode')
        } else {
            !isValid && reg.errors.hasFieldErrors('promoCode')
        }

        where:
        code          | valid | validity
        'PROMO-AB12'  | true  | 'valid'
        'PROMO-9Z8Y'  | true  | 'valid'
        null          | true  | 'valid (nullable)'
        'PROMO-abc1'  | false | 'invalid (lowercase)'
        'PROMO-12345' | false | 'invalid (5 chars)'
        'DISCOUNT-AB' | false | 'invalid (wrong prefix)'
    }

    // ========== Appointment Date Validation Tests ==========

    def "Appointment with valid dates passes validation"() {
        given: "an appointment with valid future dates"
        def futureStart = new Date() + 1  // Tomorrow
        def futureEnd = new Date() + 1
        futureEnd.hours = futureStart.hours + 2
        
        def appt = new Appointment(
            title: 'Team Meeting',
            startDate: futureStart,
            endDate: futureEnd,
            status: 'Scheduled'
        )

        when: "validating"
        def isValid = appt.validate()

        then: "validation passes"
        isValid
    }

    def "Appointment startDate must be in the future"() {
        given: "an appointment with past start date"
        def appt = new Appointment(
            title: 'Past Meeting',
            startDate: new Date() - 1,  // Yesterday
            endDate: new Date() + 1,
            status: 'Scheduled'
        )

        when: "validating"
        def isValid = appt.validate()

        then: "validation fails on startDate"
        !isValid
        appt.errors.hasFieldErrors('startDate')
    }

    def "Appointment endDate must be after startDate"() {
        given: "an appointment where end is before start"
        def futureStart = new Date() + 2
        def futureEnd = new Date() + 1  // Before start
        
        def appt = new Appointment(
            title: 'Invalid Meeting',
            startDate: futureStart,
            endDate: futureEnd,
            status: 'Scheduled'
        )

        when: "validating"
        def isValid = appt.validate()

        then: "validation fails on endDate"
        !isValid
        appt.errors.hasFieldErrors('endDate')
    }

    def "Appointment reminder must be before start date"() {
        given: "an appointment with reminder after start"
        def futureStart = new Date() + 7
        def futureEnd = new Date() + 7
        futureEnd.hours = futureStart.hours + 1
        
        def appt = new Appointment(
            title: 'Meeting',
            startDate: futureStart,
            endDate: futureEnd,
            reminderDate: futureStart + 1,  // After start
            status: 'Scheduled'
        )

        when: "validating"
        def isValid = appt.validate()

        then: "validation fails on reminderDate"
        !isValid
        appt.errors.hasFieldErrors('reminderDate')
    }

    def "Appointment priority range validation"() {
        given: "an appointment with invalid priority"
        def futureStart = new Date() + 1
        def futureEnd = new Date() + 1
        futureEnd.hours = futureStart.hours + 1
        
        def appt = new Appointment(
            title: 'Priority Meeting',
            startDate: futureStart,
            endDate: futureEnd,
            priority: 6,  // Out of range (1-5)
            status: 'Scheduled'
        )

        when: "validating"
        def isValid = appt.validate()

        then: "validation fails on priority"
        !isValid
        appt.errors.hasFieldErrors('priority')
    }

    // ========== PaymentInfo Financial Validation Tests ==========

    def "PaymentInfo with valid Visa card passes validation"() {
        given: "a payment with valid Visa card"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Visa',
            cardNumber: '4111111111111111',  // Test Visa number
            cardholderName: 'John Doe',
            expiryMonth: 12,
            expiryYear: currentYear + 2,
            cvv: '123',
            amount: 100.00,
            currency: 'USD',
            taxRate: 8.25,
            taxAmount: 8.25,
            totalAmount: 108.25,
            billingAddress: '123 Main Street, Apt 456',
            billingZip: '12345',
            isRecurring: false
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation passes"
        isValid
    }

    def "PaymentInfo with valid Amex card passes validation"() {
        given: "a payment with valid Amex card"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Amex',
            cardNumber: '378282246310005',  // Test Amex number
            cardholderName: 'Jane Smith',
            expiryMonth: 6,
            expiryYear: currentYear + 1,
            cvv: '1234',  // 4-digit CVV for Amex
            amount: 250.00,
            currency: 'USD',
            totalAmount: 250.00,
            billingAddress: '456 Oak Avenue, Suite 789',
            billingZip: '54321-1234',
            isRecurring: false
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation passes"
        isValid
    }

    def "PaymentInfo card number fails Luhn check"() {
        given: "a payment with invalid card number"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Visa',
            cardNumber: '4111111111111112',  // Invalid checksum
            cardholderName: 'John Doe',
            expiryMonth: 12,
            expiryYear: currentYear + 1,
            cvv: '123',
            amount: 100.00,
            currency: 'USD',
            totalAmount: 100.00,
            billingAddress: '123 Main Street, Apt 456',
            billingZip: '12345',
            isRecurring: false
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation fails on cardNumber"
        !isValid
        payment.errors.hasFieldErrors('cardNumber')
    }

    def "PaymentInfo Amex CVV must be 4 digits"() {
        given: "an Amex payment with 3-digit CVV"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Amex',
            cardNumber: '378282246310005',
            cardholderName: 'Jane Smith',
            expiryMonth: 6,
            expiryYear: currentYear + 1,
            cvv: '123',  // 3-digit CVV (should be 4 for Amex)
            amount: 100.00,
            currency: 'USD',
            totalAmount: 100.00,
            billingAddress: '123 Main Street, Apt 456',
            billingZip: '12345',
            isRecurring: false
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation fails on cvv"
        !isValid
        payment.errors.hasFieldErrors('cvv')
    }

    def "PaymentInfo expired card fails validation"() {
        given: "a payment with expired card"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Visa',
            cardNumber: '4111111111111111',
            cardholderName: 'John Doe',
            expiryMonth: 1,
            expiryYear: currentYear - 1,  // Last year
            cvv: '123',
            amount: 100.00,
            currency: 'USD',
            totalAmount: 100.00,
            billingAddress: '123 Main Street, Apt 456',
            billingZip: '12345',
            isRecurring: false
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation fails on expiryYear"
        !isValid
        payment.errors.hasFieldErrors('expiryYear')
    }

    def "PaymentInfo tax calculation must match"() {
        given: "a payment with incorrect tax calculation"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Visa',
            cardNumber: '4111111111111111',
            cardholderName: 'John Doe',
            expiryMonth: 12,
            expiryYear: currentYear + 1,
            cvv: '123',
            amount: 100.00,
            currency: 'USD',
            taxRate: 10.0,
            taxAmount: 15.00,  // Should be 10.00
            totalAmount: 115.00,
            billingAddress: '123 Main Street, Apt 456',
            billingZip: '12345',
            isRecurring: false
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation fails on taxAmount"
        !isValid
        payment.errors.hasFieldErrors('taxAmount')
    }

    def "PaymentInfo total must equal amount plus tax"() {
        given: "a payment with incorrect total"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Visa',
            cardNumber: '4111111111111111',
            cardholderName: 'John Doe',
            expiryMonth: 12,
            expiryYear: currentYear + 1,
            cvv: '123',
            amount: 100.00,
            currency: 'USD',
            taxRate: 10.0,
            taxAmount: 10.00,
            totalAmount: 120.00,  // Should be 110.00
            billingAddress: '123 Main Street, Apt 456',
            billingZip: '12345',
            isRecurring: false
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation fails on totalAmount"
        !isValid
        payment.errors.hasFieldErrors('totalAmount')
    }

    def "PaymentInfo recurring interval required when isRecurring is true"() {
        given: "a recurring payment without interval"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Visa',
            cardNumber: '4111111111111111',
            cardholderName: 'John Doe',
            expiryMonth: 12,
            expiryYear: currentYear + 1,
            cvv: '123',
            amount: 100.00,
            currency: 'USD',
            totalAmount: 100.00,
            billingAddress: '123 Main Street, Apt 456',
            billingZip: '12345',
            isRecurring: true,
            recurringIntervalDays: null  // Missing required field
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation fails on recurringIntervalDays"
        !isValid
        payment.errors.hasFieldErrors('recurringIntervalDays')
    }

    def "PaymentInfo recurring interval not allowed when isRecurring is false"() {
        given: "a non-recurring payment with interval"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Visa',
            cardNumber: '4111111111111111',
            cardholderName: 'John Doe',
            expiryMonth: 12,
            expiryYear: currentYear + 1,
            cvv: '123',
            amount: 100.00,
            currency: 'USD',
            totalAmount: 100.00,
            billingAddress: '123 Main Street, Apt 456',
            billingZip: '12345',
            isRecurring: false,
            recurringIntervalDays: 30  // Not allowed
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation fails on recurringIntervalDays"
        !isValid
        payment.errors.hasFieldErrors('recurringIntervalDays')
    }

    @Unroll
    def "PaymentInfo billing zip format: '#zip' is #validity"() {
        given: "a payment with the specified zip"
        def currentYear = Calendar.getInstance().get(Calendar.YEAR)
        def payment = new PaymentInfo(
            cardType: 'Visa',
            cardNumber: '4111111111111111',
            cardholderName: 'John Doe',
            expiryMonth: 12,
            expiryYear: currentYear + 1,
            cvv: '123',
            amount: 100.00,
            currency: 'USD',
            totalAmount: 100.00,
            billingAddress: '123 Main Street, Apt 456',
            billingZip: zip,
            isRecurring: false
        )

        when: "validating"
        def isValid = payment.validate()

        then: "validation result matches expected"
        if (valid) {
            isValid || !payment.errors.hasFieldErrors('billingZip')
        } else {
            !isValid && payment.errors.hasFieldErrors('billingZip')
        }

        where:
        zip          | valid | validity
        '12345'      | true  | 'valid (5 digits)'
        '12345-6789' | true  | 'valid (ZIP+4)'
        '1234'       | false | 'invalid (4 digits)'
        '123456'     | false | 'invalid (6 digits)'
        'ABCDE'      | false | 'invalid (letters)'
        '12345-678'  | false | 'invalid (ZIP+3)'
    }

    // ========== Error Message Tests ==========

    def "Constraint violations return appropriate error codes"() {
        given: "a product with multiple invalid fields"
        def product = new Product(
            sku: 'ABC',        // Too short
            name: '',          // Blank
            category: 'Invalid',  // Not in list
            price: -10.00,     // Below minimum
            stockQuantity: -5  // Below range
        )

        when: "validating"
        product.validate()

        then: "appropriate error codes are set"
        product.errors.hasFieldErrors('sku')
        product.errors.hasFieldErrors('name')
        product.errors.hasFieldErrors('category')
        product.errors.hasFieldErrors('price')
        product.errors.hasFieldErrors('stockQuantity')
        
        product.errors.getFieldError('sku').codes.any { it.contains('size') || it.contains('Size') }
        product.errors.getFieldError('category').codes.any { it.contains('inList') || it.contains('InList') }
    }
}
