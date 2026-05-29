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

package org.grails.datastore.gorm.validation.constraints

import grails.gorm.validation.ConstrainedProperty

import org.springframework.context.MessageSource
import org.springframework.validation.Errors

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Reproduces the Groovy 5 {@link ConstrainedProperty} interface static-initialisation
 * regression and verifies that {@code AbstractConstraint.getDefaultMessage} still resolves
 * the correct default message when no Spring {@code MessageSource} is available.
 *
 * <p>On Groovy 5 the {@code ConstrainedProperty.DEFAULT_MESSAGES} map is initialised with
 * null values: its map initialiser runs before the {@code DEFAULT_*_MESSAGE} constants it
 * references are assigned, so {@code DEFAULT_MESSAGES.get(code)} returns null even though
 * {@code MESSAGE_BUNDLE} resolves the same code correctly. {@code AbstractConstraint} works
 * around this by falling back to {@code MESSAGE_BUNDLE} - this spec guards that fallback so
 * it is not removed again while the regression is present.</p>
 */
class DefaultMessageResolutionSpec extends Specification {

    @Unroll
    void "getDefaultMessage resolves the bundle message for #code when no MessageSource is present"() {
        given: "a constraint constructed without a Spring MessageSource"
        def constraint = new TestConstraint((MessageSource) null)

        expect: "the message is resolved from the resource bundle rather than returning null"
        constraint.resolveDefaultMessage(code) != null
        constraint.resolveDefaultMessage(code) == ConstrainedProperty.MESSAGE_BUNDLE.getString(code)

        where:
        code << [
                ConstrainedProperty.DEFAULT_BLANK_MESSAGE_CODE,
                ConstrainedProperty.DEFAULT_NULL_MESSAGE_CODE,
                ConstrainedProperty.DEFAULT_INVALID_EMAIL_MESSAGE_CODE,
                ConstrainedProperty.DEFAULT_INVALID_URL_MESSAGE_CODE,
                ConstrainedProperty.DEFAULT_INVALID_RANGE_MESSAGE_CODE,
                ConstrainedProperty.DEFAULT_NOT_INLIST_MESSAGE_CODE,
                ConstrainedProperty.DEFAULT_INVALID_VALIDATOR_MESSAGE_CODE
        ]
    }

    static class TestConstraint extends AbstractConstraint {

        TestConstraint(MessageSource messageSource) {
            super(MessageTarget, 'name', 'param', messageSource)
        }

        @Override
        String getName() { 'test' }

        @Override
        boolean supports(Class type) { true }

        @Override
        protected Object validateParameter(Object constraintParameter) { constraintParameter }

        @Override
        protected void processValidate(Object target, Object propertyValue, Errors errors) { }

        String resolveDefaultMessage(String code) { getDefaultMessage(code) }
    }

    static class MessageTarget {
        String name
    }
}
