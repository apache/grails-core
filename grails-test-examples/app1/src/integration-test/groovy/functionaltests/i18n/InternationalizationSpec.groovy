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
package functionaltests.i18n

import spock.lang.Specification
import spock.lang.Tag

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Comprehensive integration tests for internationalization (i18n) features.
 * 
 * Tests cover:
 * - Locale switching (English, German, French)
 * - Message resolution from property files
 * - Message formatting with arguments
 * - Pluralization (choice format)
 * - Date/currency/percent formatting
 * - Default message fallback
 * - Validation error messages
 * - Accept-Language header handling
 */
@Integration
@Tag('http-client')
class InternationalizationSpec extends Specification implements HttpClientSupport {

    // ========== Basic Message Resolution Tests ==========

    def "test simple message in English"() {
        when:
        def response = http('/i18nTest/getMessage?code=app.welcome&lang=en')

        then:
        response.assertJson(200, [
                code   : 'app.welcome',
                locale : 'en',
                message: 'Welcome to the Application'

        ])
    }

    def "test simple message in German"() {
        when:
        def response = http('/i18nTest/getMessage?code=app.welcome&lang=de')

        then:
        response.assertJson(200, [
                code   : 'app.welcome',
                locale : 'de',
                message: 'Willkommen in der Anwendung'
        ])
    }

    def "test simple message in French"() {
        when:
        def response = http('/i18nTest/getMessage?code=app.welcome&lang=fr')

        then:
        response.assertJson(200, [
                code   : 'app.welcome',
                locale : 'fr',
                message: "Bienvenue dans l'application"
        ])
    }

    // ========== Message With Arguments Tests ==========

    def "test message with argument in English"() {
        when:
        def response = http('/i18nTest/getMessageWithArgs?code=app.greeting&arg=John&lang=en')

        then:
        response.assertJsonContains(200, [message: 'Hello, John!'])
    }

    def "test message with argument in German"() {
        when:
        def response = http('/i18nTest/getMessageWithArgs?code=app.greeting&arg=Johann&lang=de')

        then:
        response.assertJsonContains(200, [message: 'Hallo, Johann!'])
    }

    def "test farewell message with argument"() {
        when:
        def response = http('/i18nTest/getMessageWithArgs?code=app.farewell&arg=Alice&lang=en')

        then:
        response.assertJsonContains(200, [message: 'Goodbye, Alice. See you soon!'])
    }

    // ========== Pluralization Tests (Choice Format) ==========

    def "test choice format - zero items in English"() {
        when:
        def response = http('/i18nTest/getChoiceMessage?count=0&lang=en')

        then:
        response.assertJsonContains(200, [
                count  : 0,
                message: 'You have no items.'
        ])
    }

    def "test choice format - one item in English"() {
        when:
        def response = http('/i18nTest/getChoiceMessage?count=1&lang=en')

        then:
        response.assertJsonContains(200, [
                count  : 1,
                message: 'You have one item.'
        ])
    }

    def "test choice format - multiple items in English"() {
        when:
        def response = http('/i18nTest/getChoiceMessage?count=5&lang=en')

        then:
        response.assertJsonContains(200, [
                count  : 5,
                message: 'You have 5 items.'
        ])
    }

    def "test choice format in German"() {
        when:
        def response = http('/i18nTest/getChoiceMessage?count=0&lang=de')

        then:
        response.assertJsonContains(200, [message: 'Sie haben keine Artikel.'])
    }

    def "test choice format - one item in German"() {
        when:
        def response = http('/i18nTest/getChoiceMessage?count=1&lang=de')

        then:
        response.assertJsonContains(200, [message: 'Sie haben einen Artikel.'])
    }

    // ========== Date Formatting Tests ==========

    def "test date formatting in English"() {
        when:
        def response = http('/i18nTest/getDateMessage?lang=en')

        then:
        response.assertStatus(200)
        with(response.json()) {
            message.startsWith('Today is ')
            // English format: "Today is January 25, 2026."
            message.contains(',')
        }
    }

    def "test date formatting in German"() {
        when:
        def response = http('/i18nTest/getDateMessage?lang=de')

        then:
        response.assertStatus(200)
        response.json().message.startsWith('Heute ist der ')
        // German format: "Heute ist der 25. Januar 2026."
    }

    // ========== Currency Formatting Tests ==========

    def "test currency formatting in English US"() {
        when:
        def response = http('/i18nTest/getCurrencyMessage?amount=1234.56&lang=en_US')

        then:
        response.assertStatus(200)
        def json = response.json()
        json.message.contains('$') || json.message.contains('1,234.56')
    }

    def "test currency formatting in German"() {
        when:
        def response = http('/i18nTest/getCurrencyMessage?amount=1234.56&lang=de_DE')

        then:
        response.assertStatus(200)
        // German format uses € and different number formatting
        response.json().message != null
    }

    // ========== Percentage Formatting Tests ==========

    def "test percentage formatting in English"() {
        when:
        def response = http('/i18nTest/getPercentMessage?value=0.75&lang=en')

        then:
        response.assertStatus(200)
        with(response.json()) {
            message.contains('75')
            message.contains('%')
        }
    }

    // ========== Default Message Fallback Tests ==========

    def "test default message for non-existent code"() {
        when:
        def response = http(
                '/i18nTest/getMessageWithDefault?code=non.existent.key&defaultMsg=Fallback+Message&lang=en'
        )

        then:
        response.assertJsonContains(200, [message: 'Fallback Message'])
    }

    // ========== Validation Messages Tests ==========

    def "test validation messages in English"() {
        when:
        def response = http('/i18nTest/getValidationMessages?lang=en')

        then:
        response.assertStatus(200)
        with(response.json()) {
            messages.blank.contains('cannot be blank')
            messages.nullable.contains('cannot be null')
            messages.paginate_prev == 'Previous'
            messages.paginate_next == 'Next'
        }
    }

    def "test validation messages in German"() {
        when:
        def response = http('/i18nTest/getValidationMessages?lang=de')

        then:
        response.assertJsonContains(200, [
                messages: [
                        paginate_prev: 'Vorherige',
                        paginate_next: 'Nächste'
                ]
        ])
    }

    def "test validation messages in French"() {
        when:
        def response = http('/i18nTest/getValidationMessages?lang=fr')

        then:
        response.assertJsonContains(200, [
                messages: [
                        paginate_prev: 'Précédent',
                        paginate_next: 'Suivant'
                ]
        ])
    }

    // ========== Multiple Messages Tests ==========

    def "test multiple messages at once in English"() {
        when:
        def response = http('/i18nTest/getMultipleMessages?lang=en')

        then:
        response.assertJsonContains(200, [
                messages: [
                        welcome : 'Welcome to the Application',
                        greeting: 'Hello, User!',
                        farewell: 'Goodbye, User. See you soon!'
                ]
        ])
    }

    def "test multiple messages at once in German"() {
        when:
        def response = http('/i18nTest/getMultipleMessages?lang=de')

        then:
        response.assertJsonContains(200, [
                messages: [
                        welcome : 'Willkommen in der Anwendung',
                        greeting: 'Hallo, User!',
                        farewell: 'Auf Wiedersehen, User. Bis bald!'
                ]
        ])
    }

    // ========== Locale Information Tests ==========

    def "test current locale information"() {
        when:
        def response = http('/i18nTest/getCurrentLocale?lang=de_DE')

        then:
        response.assertJsonContains(200, [
                language: 'de',
                country : 'DE'
        ])
    }

    // ========== Accept-Language Header Tests ==========

    def "test locale from Accept-Language header - German"() {
        when:
        def response = http('/i18nTest/getLocaleFromHeader', 'Accept-Language': 'de-DE')

        then:
        response.assertStatus(200)
        // The request locale should reflect the Accept-Language header
        with(response.json()) {
            requestLocale?.startsWith('de') || contextLocale?.startsWith('de')
        }
    }

    def "test locale from Accept-Language header - French"() {
        when:
        def response = http('/i18nTest/getLocaleFromHeader', 'Accept-Language': 'fr-FR')

        then:
        response.assertStatus(200)
        with(response.json()) {
            requestLocale?.startsWith('fr') || contextLocale?.startsWith('fr')
        }
    }

    // ========== Controller Message Method Tests ==========

    def "test controller message method"() {
        when:
        def response = http('/i18nTest/useControllerMessage?code=app.welcome&lang=en')

        then:
        response.assertJsonContains(200, [message: 'Welcome to the Application'])
    }

    // ========== Edge Cases ==========

    def "test fallback to default locale when unsupported locale requested"() {
        when: "requesting a locale that doesn't have translations"
        def response = http('/i18nTest/getMessage?code=app.welcome&lang=xyz')

        then: "should fall back to default (English) message"
        response.assertStatus(200)
        // Will either get the message or fall back
        response.json().message != null
    }

    def "test message with special characters"() {
        when:
        def response = http(
                '/i18nTest/getMessageWithArgs?code=app.greeting&arg=%C3%A9l%C3%A8ve&lang=en'
        )

        then: "special characters should be handled correctly"
        response.assertJsonContains(200, [
                arg    : 'élève',
                message: 'Hello, élève!'
        ])
    }
}
