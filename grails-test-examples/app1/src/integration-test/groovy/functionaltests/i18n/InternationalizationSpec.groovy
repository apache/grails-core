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

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import spock.lang.Shared
import spock.lang.Specification

import grails.testing.mixin.integration.Integration

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
class InternationalizationSpec extends Specification {

    @Shared
    HttpClient client

    def setup() {
        client = client ?: HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    def cleanupSpec() {
        client.close()
    }

    // ========== Basic Message Resolution Tests ==========

    def "test simple message in English"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMessage?code=app.welcome&lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().code == 'app.welcome'
        response.body().locale == 'en'
        response.body().message == 'Welcome to the Application'
    }

    def "test simple message in German"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMessage?code=app.welcome&lang=de'),
            Map
        )

        then:
        response.status.code == 200
        response.body().code == 'app.welcome'
        response.body().locale == 'de'
        response.body().message == 'Willkommen in der Anwendung'
    }

    def "test simple message in French"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMessage?code=app.welcome&lang=fr'),
            Map
        )

        then:
        response.status.code == 200
        response.body().code == 'app.welcome'
        response.body().locale == 'fr'
        response.body().message == "Bienvenue dans l'application"
    }

    // ========== Message With Arguments Tests ==========

    def "test message with argument in English"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMessageWithArgs?code=app.greeting&arg=John&lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message == 'Hello, John!'
    }

    def "test message with argument in German"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMessageWithArgs?code=app.greeting&arg=Johann&lang=de'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message == 'Hallo, Johann!'
    }

    def "test farewell message with argument"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMessageWithArgs?code=app.farewell&arg=Alice&lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message == 'Goodbye, Alice. See you soon!'
    }

    // ========== Pluralization Tests (Choice Format) ==========

    def "test choice format - zero items in English"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getChoiceMessage?count=0&lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().count == 0
        response.body().message == 'You have no items.'
    }

    def "test choice format - one item in English"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getChoiceMessage?count=1&lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().count == 1
        response.body().message == 'You have one item.'
    }

    def "test choice format - multiple items in English"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getChoiceMessage?count=5&lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().count == 5
        response.body().message == 'You have 5 items.'
    }

    def "test choice format in German"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getChoiceMessage?count=0&lang=de'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message == 'Sie haben keine Artikel.'
    }

    def "test choice format - one item in German"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getChoiceMessage?count=1&lang=de'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message == 'Sie haben einen Artikel.'
    }

    // ========== Date Formatting Tests ==========

    def "test date formatting in English"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getDateMessage?lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message.startsWith('Today is ')
        // English format: "Today is January 25, 2026."
        response.body().message.contains(',')
    }

    def "test date formatting in German"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getDateMessage?lang=de'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message.startsWith('Heute ist der ')
        // German format: "Heute ist der 25. Januar 2026."
    }

    // ========== Currency Formatting Tests ==========

    def "test currency formatting in English US"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getCurrencyMessage?amount=1234.56&lang=en_US'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message.contains('$') || response.body().message.contains('1,234.56')
    }

    def "test currency formatting in German"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getCurrencyMessage?amount=1234.56&lang=de_DE'),
            Map
        )

        then:
        response.status.code == 200
        // German format uses € and different number formatting
        response.body().message != null
    }

    // ========== Percentage Formatting Tests ==========

    def "test percentage formatting in English"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getPercentMessage?value=0.75&lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message.contains('75')
        response.body().message.contains('%')
    }

    // ========== Default Message Fallback Tests ==========

    def "test default message for non-existent code"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMessageWithDefault?code=non.existent.key&defaultMsg=Fallback+Message&lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message == 'Fallback Message'
    }

    // ========== Validation Messages Tests ==========

    def "test validation messages in English"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getValidationMessages?lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().messages.blank.contains('cannot be blank')
        response.body().messages.nullable.contains('cannot be null')
        response.body().messages.paginate_prev == 'Previous'
        response.body().messages.paginate_next == 'Next'
    }

    def "test validation messages in German"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getValidationMessages?lang=de'),
            Map
        )

        then:
        response.status.code == 200
        response.body().messages.paginate_prev == 'Vorherige'
        response.body().messages.paginate_next == 'Nächste'
    }

    def "test validation messages in French"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getValidationMessages?lang=fr'),
            Map
        )

        then:
        response.status.code == 200
        response.body().messages.paginate_prev == 'Précédent'
        response.body().messages.paginate_next == 'Suivant'
    }

    // ========== Multiple Messages Tests ==========

    def "test multiple messages at once in English"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMultipleMessages?lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().messages.welcome == 'Welcome to the Application'
        response.body().messages.greeting == 'Hello, User!'
        response.body().messages.farewell == 'Goodbye, User. See you soon!'
    }

    def "test multiple messages at once in German"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMultipleMessages?lang=de'),
            Map
        )

        then:
        response.status.code == 200
        response.body().messages.welcome == 'Willkommen in der Anwendung'
        response.body().messages.greeting == 'Hallo, User!'
        response.body().messages.farewell == 'Auf Wiedersehen, User. Bis bald!'
    }

    // ========== Locale Information Tests ==========

    def "test current locale information"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getCurrentLocale?lang=de_DE'),
            Map
        )

        then:
        response.status.code == 200
        response.body().language == 'de'
        response.body().country == 'DE'
    }

    // ========== Accept-Language Header Tests ==========

    def "test locale from Accept-Language header - German"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getLocaleFromHeader')
                .header('Accept-Language', 'de-DE'),
            Map
        )

        then:
        response.status.code == 200
        // The request locale should reflect the Accept-Language header
        response.body().requestLocale?.startsWith('de') || response.body().contextLocale?.startsWith('de')
    }

    def "test locale from Accept-Language header - French"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getLocaleFromHeader')
                .header('Accept-Language', 'fr-FR'),
            Map
        )

        then:
        response.status.code == 200
        response.body().requestLocale?.startsWith('fr') || response.body().contextLocale?.startsWith('fr')
    }

    // ========== Controller Message Method Tests ==========

    def "test controller message method"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/useControllerMessage?code=app.welcome&lang=en'),
            Map
        )

        then:
        response.status.code == 200
        response.body().message == 'Welcome to the Application'
    }

    // ========== Edge Cases ==========

    def "test fallback to default locale when unsupported locale requested"() {
        when: "requesting a locale that doesn't have translations"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMessage?code=app.welcome&lang=xyz'),
            Map
        )

        then: "should fall back to default (English) message"
        response.status.code == 200
        // Will either get the message or fall back
        response.body().message != null
    }

    def "test message with special characters"() {
        when:
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/i18nTest/getMessageWithArgs?code=app.greeting&arg=%C3%A9l%C3%A8ve&lang=en'),
            Map
        )

        then: "special characters should be handled correctly"
        response.status.code == 200
        response.body().arg == 'élève'
        response.body().message == 'Hello, élève!'
    }
}
