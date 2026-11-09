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

import org.springframework.aot.hint.RuntimeHints
import org.springframework.core.SpringProperties
import spock.lang.Specification

/**
 * Covers the resources an application reads by name surviving into an image, and only those.
 *
 * <p>An asset is asked for by request path and a message bundle by locale, so nothing in the code
 * names either and an image built without them serves every page with a missing stylesheet and an
 * untranslated string. What is registered has to be bounded all the same: a pattern matching every
 * properties file at every depth carries the whole classpath into the image to keep the few that
 * are bundles.</p>
 */
class GrailsResourceRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void cleanup() {
        SpringProperties.setProperty(GrailsResourceRuntimeHints.ADDITIONAL_PATTERNS_PROPERTY, null)
    }

    /**
     * What was registered. Spring records the parent directory of each pattern alongside it, so
     * these are what was asked for and not only what this class named.
     */
    private Set<String> registeredPatterns() {
        new GrailsResourceRuntimeHints().registerHints(hints, getClass().classLoader)
        hints.resources().resourcePatternHints().toList()
                .collectMany { it.includes }
                .collect { it.pattern } as Set
    }

    void 'the compiled assets are registered'() {
        expect: 'asset-pipeline serves them from the classpath by the path that was asked for'
            registeredPatterns().containsAll(['assets/*', 'assets/**'])
    }

    void 'the message bundles at the root of the classpath are registered'() {
        expect: 'which is what PluginAwareResourceBundleMessageSource scans, and where a plugin ' +
                "shipping its own bundle lands them"
            '*.properties' in registeredPatterns()
    }

    void 'every properties file on the classpath is not'() {
        expect: 'nothing reads a bundle below the root, and registering the pattern would carry ' +
                'every dependency configuration into the image to keep the few that are bundles'
            !('**/*.properties' in registeredPatterns())
    }

    void 'an application says what else it reads by name'() {
        given:
            SpringProperties.setProperty(GrailsResourceRuntimeHints.ADDITIONAL_PATTERNS_PROPERTY,
                    'db/migration/**,templates/*.vm')

        expect: 'read as a property because this runs while the code is being generated'
            registeredPatterns().containsAll(['db/migration/**', 'templates/*.vm'])
    }

    void 'a list written with spaces after its commas is the list it looks like'() {
        given:
            SpringProperties.setProperty(GrailsResourceRuntimeHints.ADDITIONAL_PATTERNS_PROPERTY,
                    'db/migration/**,  templates/*.vm ,')

        expect: 'a pattern carrying a leading space matches nothing, and does so silently'
            registeredPatterns().containsAll(['db/migration/**', 'templates/*.vm'])
            !registeredPatterns().any { it != it.trim() }
    }

    void 'an application that says nothing gets what it would have'() {
        given:
            SpringProperties.setProperty(GrailsResourceRuntimeHints.ADDITIONAL_PATTERNS_PROPERTY, blank)

        expect:
            registeredPatterns().containsAll(['assets/*', 'assets/**', '*.properties'])
            !('db/migration/**' in registeredPatterns())

        where:
            blank << [null, '', '   ']
    }
}
