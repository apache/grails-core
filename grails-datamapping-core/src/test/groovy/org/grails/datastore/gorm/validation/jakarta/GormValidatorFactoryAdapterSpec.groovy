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
package org.grails.datastore.gorm.validation.jakarta

import jakarta.validation.ClockProvider
import jakarta.validation.ConstraintValidatorFactory
import jakarta.validation.MessageInterpolator
import jakarta.validation.ParameterNameProvider
import jakarta.validation.TraversableResolver
import jakarta.validation.Validator
import jakarta.validation.ValidatorContext
import jakarta.validation.ValidatorFactory
import jakarta.validation.valueextraction.ValueExtractor

import spock.lang.Specification

class GormValidatorFactoryAdapterSpec extends Specification {

    ValidatorFactory delegate = Mock(ValidatorFactory)
    GormValidatorFactoryAdapter adapter = new GormValidatorFactoryAdapter(delegate)

    void "getValidator wraps the delegate validator in a GormValidatorAdapter"() {
        given:
        Validator delegateValidator = Mock(Validator)

        when:
        Validator result = adapter.getValidator()

        then:
        1 * delegate.getValidator() >> delegateValidator
        result instanceof GormValidatorAdapter
        ((GormValidatorAdapter) result).thisValidator.is(delegateValidator)
    }

    void "delegates simple accessor methods to the wrapped factory"() {
        given:
        ClockProvider clockProvider = Mock(ClockProvider)
        MessageInterpolator messageInterpolator = Mock(MessageInterpolator)
        TraversableResolver traversableResolver = Mock(TraversableResolver)
        ConstraintValidatorFactory constraintValidatorFactory = Mock(ConstraintValidatorFactory)
        ParameterNameProvider parameterNameProvider = Mock(ParameterNameProvider)
        delegate.getClockProvider() >> clockProvider
        delegate.getMessageInterpolator() >> messageInterpolator
        delegate.getTraversableResolver() >> traversableResolver
        delegate.getConstraintValidatorFactory() >> constraintValidatorFactory
        delegate.getParameterNameProvider() >> parameterNameProvider

        expect:
        adapter.clockProvider.is(clockProvider)
        adapter.messageInterpolator.is(messageInterpolator)
        adapter.traversableResolver.is(traversableResolver)
        adapter.constraintValidatorFactory.is(constraintValidatorFactory)
        adapter.parameterNameProvider.is(parameterNameProvider)
    }

    void "unwrap delegates to the wrapped factory"() {
        given:
        def unwrapped = new Object()
        delegate.unwrap(Object) >> unwrapped

        expect:
        adapter.unwrap(Object).is(unwrapped)
    }

    void "close delegates to the wrapped factory"() {
        when:
        adapter.close()

        then:
        1 * delegate.close()
    }

    void "usingContext wraps the delegate context in a GormValidatorContext"() {
        given:
        ValidatorContext delegateContext = Mock(ValidatorContext)
        delegate.usingContext() >> delegateContext

        when:
        ValidatorContext context = adapter.usingContext()

        then:
        context instanceof GormValidatorFactoryAdapter.GormValidatorContext
    }

    void "GormValidatorContext#getValidator wraps the delegate context's validator"() {
        given:
        ValidatorContext delegateContext = Mock(ValidatorContext)
        Validator delegateValidator = Mock(Validator)
        delegateContext.getValidator() >> delegateValidator
        def context = new GormValidatorFactoryAdapter.GormValidatorContext(delegateContext)

        when:
        Validator wrapped = context.getValidator()

        then:
        wrapped instanceof GormValidatorAdapter
        ((GormValidatorAdapter) wrapped).thisValidator.is(delegateValidator)
    }

    void "GormValidatorContext builder methods delegate to the wrapped context and return themselves"() {
        given:
        ValidatorContext delegateContext = Mock(ValidatorContext)
        def context = new GormValidatorFactoryAdapter.GormValidatorContext(delegateContext)
        MessageInterpolator messageInterpolator = Mock(MessageInterpolator)
        TraversableResolver traversableResolver = Mock(TraversableResolver)
        ConstraintValidatorFactory constraintValidatorFactory = Mock(ConstraintValidatorFactory)
        ParameterNameProvider parameterNameProvider = Mock(ParameterNameProvider)
        ClockProvider clockProvider = Mock(ClockProvider)
        ValueExtractor valueExtractor = Mock(ValueExtractor)

        when:
        def r1 = context.messageInterpolator(messageInterpolator)
        def r2 = context.traversableResolver(traversableResolver)
        def r3 = context.constraintValidatorFactory(constraintValidatorFactory)
        def r4 = context.parameterNameProvider(parameterNameProvider)
        def r5 = context.clockProvider(clockProvider)
        def r6 = context.addValueExtractor(valueExtractor)

        then:
        1 * delegateContext.messageInterpolator(messageInterpolator)
        1 * delegateContext.traversableResolver(traversableResolver)
        1 * delegateContext.constraintValidatorFactory(constraintValidatorFactory)
        1 * delegateContext.parameterNameProvider(parameterNameProvider)
        1 * delegateContext.clockProvider(clockProvider)
        1 * delegateContext.addValueExtractor(valueExtractor)
        r1.is(context)
        r2.is(context)
        r3.is(context)
        r4.is(context)
        r5.is(context)
        r6.is(context)
    }
}
