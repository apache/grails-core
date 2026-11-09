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
package org.apache.grails.gsp.taglib.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import spock.lang.Specification

/**
 * Covers the tag libraries and page runtime surviving into an ahead-of-time image. What a page
 * reaches depends on what it renders, so a missing registration shows up on the page that uses that
 * tag rather than at start-up.
 */
class TagLibRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new TagLibRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    private Set<String> registeredTypes() {
        hints.reflection().typeHints().collect { it.type.name } as Set
    }

    private boolean hasCategory(String type, MemberCategory category) {
        def hint = hints.reflection().getTypeHint(TypeReference.of(type))
        hint != null && hint.memberCategories.contains(category)
    }

    void 'the tag libraries on the classpath are registered'() {
        expect:
            registeredTypes().any { it.endsWith('TagLib') }
    }

    void 'the classes a tag library declares inside itself are registered too'() {
        expect: 'the fields plugin keeps the bean stack a nested tag reads in one of these, so a ' +
                'pattern matching only the library itself renders a page until a tag nests'
            registeredTypes().any { it.contains('TagLib$') }
    }

    void 'the page runtime a compiled page writes through is registered'() {
        expect:
            registeredTypes().any { it.startsWith('org.grails.gsp.') }
            registeredTypes().any { it.startsWith('org.grails.buffer.') }
    }

    void 'fields are registered, not only methods'() {
        given: 'a page reads the shared empty body from the tag output rather than calling for it'
            String type = registeredTypes().find { it.startsWith('org.grails.taglib.') }

        expect:
            hasCategory(type, MemberCategory.ACCESS_DECLARED_FIELDS)
            hasCategory(type, MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'a class loader that resolves nothing yields no hints rather than failing'() {
        given:
            RuntimeHints empty = new RuntimeHints()

        when:
            new TagLibRuntimeHints().registerHints(empty, new URLClassLoader(new URL[0], null))

        then:
            noExceptionThrown()
    }
}
