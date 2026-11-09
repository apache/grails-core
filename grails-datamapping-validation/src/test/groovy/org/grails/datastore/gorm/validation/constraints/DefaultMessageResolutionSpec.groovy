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
 * Regression guard for {@link ConstrainedProperty#DEFAULT_MESSAGES} on Groovy 5 (GROOVY-12063):
 * verifies that {@code AbstractConstraint.getDefaultMessage} resolves the correct default message
 * from the map when no Spring {@code MessageSource} is available.
 *
 * <p>{@code DEFAULT_MESSAGES} must be built with a Groovy map literal rather than an
 * anonymous-{@code HashMap}-with-instance-initializer. On Groovy 5 the bareword references to the
 * sibling {@code DEFAULT_*_MESSAGE} constants inside such an initializer compile to dynamic
 * {@code getProperty} calls on {@code this} (the {@code HashMap}); because the receiver is a
 * {@code Map}, the MOP resolves them as key lookups on the still-empty map, so every entry was
 * captured as {@code null} and {@code DEFAULT_MESSAGES.get(code)} returned {@code null}. The map
 * literal resolves the constants against the enclosing interface scope and is correct on every
 * version; this spec fails if that regresses.</p>
 */
class DefaultMessageResolutionSpec extends Specification {

    @Unroll
    void "getDefaultMessage resolves the bundle message for #code when no MessageSource is present"() {
        given: "a constraint constructed without a Spring MessageSource"
        def constraint = new TestConstraint((MessageSource) null)

        expect: "the message is resolved from the resource bundle rather than returning null"
        constraint.resolveDefaultMessage(code) != null
        constraint.resolveDefaultMessage(code) == ConstrainedProperty.MESSAGE_BUNDLE.getString(code)

        where: "every code in the default error-message bundle is covered by DEFAULT_MESSAGES"
        code << ConstrainedProperty.MESSAGE_BUNDLE.keySet()
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
