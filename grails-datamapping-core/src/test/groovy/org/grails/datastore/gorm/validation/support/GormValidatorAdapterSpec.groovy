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
package org.grails.datastore.gorm.validation.support

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.validation.jakarta.GormValidatorAdapter
import org.springframework.validation.BeanPropertyBindingResult
import spock.lang.Specification

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import jakarta.validation.Valid
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull

/**
 * Created by graemerocher on 30/12/2016.
 */
class GormValidatorAdapterSpec extends Specification {


    void "test propagate jakarta.valdiation errors to gorm object"() {

        given:
        def factory = Validation.byDefaultProvider().configure().buildValidatorFactory()

        Validator v = factory.getValidator()
        def adapter = new GormValidatorAdapter(v)


        when:
        def product = new Product(price: "foo")
        adapter.validate(product)

        then:
        adapter.forExecutables()
        product.errors.hasErrors()
        product.errors.getFieldError('price')
    }

    void 'a class level violation of the validated object is a global error'() {
        given:
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        def range = new PriceRange(low: 5, high: 1, currency: 'EUR')
        def errors = new BeanPropertyBindingResult(range, 'range')

        when:
        adapter.validate(range, errors)

        then:
        errors.errorCount == 1
        errors.globalErrors*.code == ['OrderedRange']
    }

    void 'a class level violation under a nested path is an error of the path, as when Bean Validation cascades through @Valid'() {
        given: 'a quote whose range violates its class level constraint and a property annotation'
        def adapter = new GormValidatorAdapter(Validation.buildDefaultValidatorFactory().validator)
        def quote = new Quote(range: new PriceRange(low: 5, high: 1))

        and: 'the errors of Bean Validation cascading from the quote to the range'
        def cascaded = new BeanPropertyBindingResult(quote, 'quote')
        adapter.validate(quote, cascaded)

        when: 'the range is validated under the nested path of the errors of the quote'
        def errors = new BeanPropertyBindingResult(quote, 'quote')
        errors.pushNestedPath('range')
        adapter.validate(quote.range, errors)
        errors.popNestedPath()

        then: 'the class level violation is reported on the path, without the separator'
        errors.globalErrorCount == 0
        errors.fieldErrors*.field.sort() == ['range', 'range.currency']
        errors.getFieldError('range').code == 'OrderedRange'
        errors.getFieldError('range').rejectedValue.is(quote.range)
        errors.getFieldError('range.currency').code == 'NotNull'

        and: 'with the codes, arguments and message of the cascaded error'
        def error = errors.getFieldError('range')
        def expected = cascaded.getFieldError('range')
        error.codes == expected.codes
        error.arguments == expected.arguments
        error.defaultMessage == expected.defaultMessage
    }
}

class Product implements GormValidateable {
    @Digits(integer = 6, fraction = 2)
    String price
}

@Constraint(validatedBy = [OrderedRangeValidator])
@Target([ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@interface OrderedRange {

    String message() default 'the low price must not exceed the high price'

    Class<?>[] groups() default []

    Class<? extends Payload>[] payload() default []
}

class OrderedRangeValidator implements ConstraintValidator<OrderedRange, PriceRange> {

    @Override
    boolean isValid(PriceRange range, ConstraintValidatorContext context) {
        range == null || range.low == null || range.high == null || range.low <= range.high
    }
}

@OrderedRange
class PriceRange {

    Integer low

    Integer high

    @NotNull
    String currency
}

class Quote {

    @Valid
    PriceRange range
}
