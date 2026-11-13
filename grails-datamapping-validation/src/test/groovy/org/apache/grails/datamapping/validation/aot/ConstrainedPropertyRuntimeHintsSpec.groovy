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
package org.apache.grails.datamapping.validation.aot

import grails.gorm.validation.DefaultConstrainedProperty

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference

import spock.lang.Specification

/**
 * Covers a domain class's constraints surviving into an ahead-of-time image.
 *
 * <p>A constraint that names no registered Constraint is applied by setting a property of the same
 * name through Groovy, so it is a reflective call the compiler never sees. Without these hints an
 * application starts and then fails the first time anything is validated.</p>
 */
class ConstrainedPropertyRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new ConstrainedPropertyRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    private boolean registered(Class<?> type) {
        def hint = hints.reflection().getTypeHint(TypeReference.of(type))
        hint != null && hint.memberCategories.contains(MemberCategory.INVOKE_PUBLIC_METHODS)
    }

    void 'the property a constraint is set on is registered'() {
        expect: 'password: true is a reflective call to setPassword(boolean), which an image keeps ' +
                'only when something asked for it'
            registered(DefaultConstrainedProperty)
    }

    void 'a setter a constraint is applied through is one of the methods registered'() {
        given: 'the constraint that failed in an image, named the way the failure named it'
            def setter = DefaultConstrainedProperty.getMethod('setPassword', boolean)

        expect: 'declared methods are registered, so the setter is reachable rather than stripped'
            setter != null
            hints.reflection().getTypeHint(TypeReference.of(DefaultConstrainedProperty))
                    .memberCategories.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'the builder that applies them is registered'() {
        expect:
            registered(org.grails.datastore.gorm.validation.constraints.builder.ConstrainedPropertyBuilder)
    }
}
