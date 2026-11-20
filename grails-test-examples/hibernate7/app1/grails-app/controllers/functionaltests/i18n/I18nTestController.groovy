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

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.web.servlet.LocaleResolver

import grails.converters.JSON

/**
 * Controller for testing internationalization (i18n) features.
 * Tests locale switching, message resolution, and formatting.
 */
class I18nTestController {

    MessageSource messageSource

    /**
     * Get a simple message using the current locale.
     */
    def getMessage() {
        def code = params.code ?: 'app.welcome'
        def locale = resolveLocale()
        def message = messageSource.getMessage(code, null, locale)
        render([
            code: code,
            locale: locale.toString(),
            message: message
        ] as JSON)
    }

    /**
     * Get a message with arguments.
     */
    def getMessageWithArgs() {
        def code = params.code ?: 'app.greeting'
        def arg = params.arg ?: 'World'
        def locale = resolveLocale()
        def message = messageSource.getMessage(code, [arg] as Object[], locale)
        render([
            code: code,
            arg: arg,
            locale: locale.toString(),
            message: message
        ] as JSON)
    }

    /**
     * Get a message with choice format (pluralization).
     */
    def getChoiceMessage() {
        def count = params.int('count') ?: 0
        def locale = resolveLocale()
        def message = messageSource.getMessage('app.itemcount', [count] as Object[], locale)
        render([
            code: 'app.itemcount',
            count: count,
            locale: locale.toString(),
            message: message
        ] as JSON)
    }

    /**
     * Get a date formatted message.
     */
    def getDateMessage() {
        def locale = resolveLocale()
        def date = new Date()
        def message = messageSource.getMessage('app.date.format', [date] as Object[], locale)
        render([
            code: 'app.date.format',
            date: date.format('yyyy-MM-dd'),
            locale: locale.toString(),
            message: message
        ] as JSON)
    }

    /**
     * Get a currency formatted message.
     */
    def getCurrencyMessage() {
        def amount = params.double('amount') ?: 1234.56
        def locale = resolveLocale()
        def message = messageSource.getMessage('app.currency', [amount] as Object[], locale)
        render([
            code: 'app.currency',
            amount: amount,
            locale: locale.toString(),
            message: message
        ] as JSON)
    }

    /**
     * Get a percentage formatted message.
     */
    def getPercentMessage() {
        def value = params.double('value') ?: 0.75
        def locale = resolveLocale()
        def message = messageSource.getMessage('app.percent', [value] as Object[], locale)
        render([
            code: 'app.percent',
            value: value,
            locale: locale.toString(),
            message: message
        ] as JSON)
    }

    /**
     * Test message using controller's message() method.
     */
    def useControllerMessage() {
        def code = params.code ?: 'app.welcome'
        def locale = resolveLocale()
        // Use the controller's built-in message method
        def msg = message(code: code, locale: locale)
        render([
            code: code,
            locale: locale.toString(),
            message: msg
        ] as JSON)
    }

    /**
     * Test message with default value.
     */
    def getMessageWithDefault() {
        def code = params.code ?: 'non.existent.code'
        def defaultMsg = params.defaultMsg ?: 'Default Message'
        def locale = resolveLocale()
        def message = messageSource.getMessage(code, null, defaultMsg, locale)
        render([
            code: code,
            defaultMsg: defaultMsg,
            locale: locale.toString(),
            message: message
        ] as JSON)
    }

    /**
     * Get validation error messages.
     */
    def getValidationMessages() {
        def locale = resolveLocale()
        def messages = [:]
        messages.blank = messageSource.getMessage('default.blank.message', ['name', 'User'] as Object[], locale)
        messages.nullable = messageSource.getMessage('default.null.message', ['email', 'User'] as Object[], locale)
        messages.paginate_prev = messageSource.getMessage('default.paginate.prev', null, locale)
        messages.paginate_next = messageSource.getMessage('default.paginate.next', null, locale)
        render([
            locale: locale.toString(),
            messages: messages
        ] as JSON)
    }

    /**
     * Test multiple messages at once.
     */
    def getMultipleMessages() {
        def locale = resolveLocale()
        def messages = [:]
        messages.welcome = messageSource.getMessage('app.welcome', null, locale)
        messages.greeting = messageSource.getMessage('app.greeting', ['User'] as Object[], locale)
        messages.farewell = messageSource.getMessage('app.farewell', ['User'] as Object[], locale)
        render([
            locale: locale.toString(),
            messages: messages
        ] as JSON)
    }

    /**
     * Get current locale information.
     */
    def getCurrentLocale() {
        def locale = resolveLocale()
        render([
            language: locale.language,
            country: locale.country,
            displayName: locale.displayName,
            full: locale.toString()
        ] as JSON)
    }

    /**
     * Test locale from Accept-Language header.
     */
    def getLocaleFromHeader() {
        def requestLocale = request.locale
        def contextLocale = LocaleContextHolder.locale
        render([
            requestLocale: requestLocale?.toString(),
            contextLocale: contextLocale?.toString()
        ] as JSON)
    }

    private Locale resolveLocale() {
        def lang = params.lang
        if (lang) {
            // Parse locale from parameter
            def parts = lang.split('_')
            if (parts.length == 2) {
                return new Locale(parts[0], parts[1])
            }
            return new Locale(lang)
        }
        // Fall back to request locale or default
        return request.locale ?: Locale.ENGLISH
    }
}
