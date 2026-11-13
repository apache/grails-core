/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.i18n

import spock.lang.Specification

import org.gradle.api.InvalidUserDataException

class I18nBundleIndexSpec extends Specification {

    void 'an application bundle and its locale variants resolve to one base name'() {
        when:
        I18nBundleIndex index = I18nBundleIndex.from(
                ['messages.properties', 'messages_de.properties', 'messages_fr.properties'], [])

        then:
        index.basenames == ['messages']
        index.locales == ['de', 'fr']
    }

    void 'a language_COUNTRY suffix is recognised as a locale, not part of the base name'() {
        when:
        I18nBundleIndex index = I18nBundleIndex.from(
                ['messages.properties', 'messages_pt_BR.properties'], [])

        then:
        index.basenames == ['messages']
        index.locales == ['pt_BR']
    }

    void 'a plugin may ship several namespaced bundles'() {
        when:
        I18nBundleIndex index = I18nBundleIndex.from([
                'spring-security-core.properties',
                'spring-security-core_fr.properties',
                'spring-security-core-validation.properties',
                'spring-security-core-validation_fr.properties'], [])

        then: 'the longer base name claims its own variant rather than the shorter one claiming it'
        index.basenames == ['spring-security-core', 'spring-security-core-validation']
        index.locales == ['fr']
    }

    void 'an underscored application base name is not split at the first underscore'() {
        when: 'errors is not a valid ISO language, so api_errors is a base name in its own right'
        I18nBundleIndex index = I18nBundleIndex.from(
                ['api_errors.properties', 'api_errors_fr.properties'], [])

        then:
        index.basenames == ['api_errors']
        index.locales == ['fr']
    }

    void 'a base name ending in a valid locale identifier is read as a locale variant'() {
        when: 'the reserved-suffix convention takes api_fr to be base name api in French'
        I18nBundleIndex.from(['api_fr.properties'], [])

        then: 'which then has no locale-independent bundle, so the ambiguity surfaces as an error'
        InvalidUserDataException e = thrown()
        e.message.contains("No locale-independent bundle for 'api.properties'")
    }

    void 'declaring the base name overrides the reserved-suffix convention'() {
        when:
        I18nBundleIndex index = I18nBundleIndex.from(['api_fr.properties'], ['api_fr'])

        then:
        index.basenames == ['api_fr']
        index.locales == []
    }

    void 'a mistyped locale variant of an existing base name fails the build'() {
        when:
        I18nBundleIndex.from(['messages.properties', 'messages_dee.properties'], [])

        then:
        InvalidUserDataException e = thrown()
        e.message.contains("Cannot classify i18n bundle 'messages_dee.properties'")
        e.message.contains("'dee' is not a valid locale")
    }

    void 'a legal sibling that looks like a mistyped locale must be declared'() {
        when: 'errors is not a valid locale, so api_errors reads as a likely typo of api'
        I18nBundleIndex.from(['api.properties', 'api_errors.properties'], [])

        then:
        InvalidUserDataException e = thrown()
        e.message.contains("Cannot classify i18n bundle 'api_errors.properties'")

        when: 'declaring both resolves it'
        I18nBundleIndex index = I18nBundleIndex.from(
                ['api.properties', 'api_errors.properties'], ['api', 'api_errors'])

        then:
        index.basenames == ['api', 'api_errors']
        index.locales == []
    }

    void 'a bundle with only locale variants fails because Boot would register no message source'() {
        when:
        I18nBundleIndex.from(['messages_de.properties', 'messages_fr.properties'], [])

        then:
        InvalidUserDataException e = thrown()
        e.message.contains("No locale-independent bundle for 'messages.properties'")
    }

    void 'an artifact shipping no bundles produces an empty index'() {
        when:
        I18nBundleIndex index = I18nBundleIndex.from([], [])

        then:
        index.empty
        index.basenames == []
        index.locales == []
    }

    void 'a malformed empty suffix is rejected'() {
        when:
        I18nBundleIndex.from(['messages.properties', 'messages_.properties'], [])

        then:
        thrown(InvalidUserDataException)
    }

    void 'the result does not depend on the order the file system lists bundles'() {
        given:
        List<String> names = ['messages.properties', 'messages_de.properties', 'api_errors.properties']

        expect:
        I18nBundleIndex.from(names, []).basenames == I18nBundleIndex.from(names.reverse(), []).basenames
        I18nBundleIndex.from(names, []).locales == I18nBundleIndex.from(names.reverse(), []).locales
    }
}
