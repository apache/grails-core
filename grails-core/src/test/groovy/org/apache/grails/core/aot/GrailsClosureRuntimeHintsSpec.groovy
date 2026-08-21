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
package org.apache.grails.core.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.aot.hint.TypeReference
import spock.lang.Specification

/**
 * Covers the framework's closures being found while the hints are written, rather than named one by
 * one. Groovy reads {@code doCall}'s parameter types to choose an overload, so a closure missing
 * from an image fails where it is used rather than at start-up.
 */
class GrailsClosureRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    private List<String> registeredTypes() {
        hints.reflection().typeHints().collect { it.type.name }
    }

    void 'the framework closures on the classpath are registered'() {
        when:
            new GrailsClosureRuntimeHints().registerHints(hints, getClass().classLoader)

        then: 'this module alone ships plenty, so a scan that found nothing would be broken'
            registeredTypes().count { it.contains('_closure') } > 0
    }

    void 'a registered closure can have its parameter types read'() {
        given:
            new GrailsClosureRuntimeHints().registerHints(hints, getClass().classLoader)

        when:
            def closure = registeredTypes().find { it.contains('_closure') }
            def hint = hints.reflection().getTypeHint(TypeReference.of(closure))

        then: 'that is the access Groovy needs to select an overload'
            hint.memberCategories.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'only framework and plugin descriptor closures are registered'() {
        when:
            new GrailsClosureRuntimeHints().registerHints(hints, getClass().classLoader)

        then: 'a plugin may sit in any package, but nothing else should be swept up'
            registeredTypes().every {
                it.startsWith('grails.') || it.startsWith('org.grails.') || it.contains('GrailsPlugin$')
            }
    }

    void 'where the plugins are listed is carried'() {
        given:
            new GrailsClosureRuntimeHints().registerHints(hints, getClass().classLoader)

        expect: 'read to find the plugins at all, and an image carries a resource only when asked'
            RuntimeHintsPredicates.resource().forResource('META-INF/grails.factories').test(hints)
    }

    void 'a closure is skipped when the class it was written inside cannot be loaded'() {
        given:
            def registrar = new GrailsClosureRuntimeHints()
            ClassLoader loader = getClass().classLoader

        expect: 'analysing a closure means reading the method it was written in, so a closure whose ' +
                'surroundings are absent fails the build rather than being left out of it'
            !registrar.enclosingLoads('com.example.NotHere$_someMethod_closure1', loader)

        and: 'one written inside a class that is present is kept'
            registrar.enclosingLoads('org.grails.config.NavigableMap$_flattenKeys_closure3', loader)

        and: 'a class that is not a closure has no surroundings to ask about'
            registrar.enclosingLoads('org.grails.config.NavigableMap', loader)
    }

    void 'a class loader that resolves nothing yields no hints rather than failing'() {
        when:
            new GrailsClosureRuntimeHints().registerHints(hints, new URLClassLoader(new URL[0], null))

        then:
            noExceptionThrown()
    }
}
