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
package org.grails.web.taglib

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.plugins.web.taglib.FormTagLib
import spock.lang.Specification

/**
 * Tests the {@code available="true"} mode of {@code g:localeSelect}, which restricts the options to
 * the locales the i18n plugin publishes to the servlet context (the app's real translations) instead
 * of every locale the JVM knows about.
 */
class LocaleSelectAvailableSpec extends Specification implements TagLibUnitTest<FormTagLib> {

    void 'available="true" lists only the locales published to the servlet context'() {
        given:
        request.servletContext.setAttribute('availableLocales',
                [Locale.forLanguageTag('en'), Locale.forLanguageTag('es')])

        when:
        String output = applyTemplate('<g:localeSelect name="lang" available="true"/>')

        then: 'the two published locales are options'
        output.contains('name="lang"')
        output.contains('value="en"')
        output.contains('value="es"')

        and: 'a locale that has no bundle is not offered'
        !output.contains('value="fr"')
    }

    void 'without available="true" the tag still lists every JVM locale'() {
        when:
        String output = applyTemplate('<g:localeSelect name="lang"/>')

        then:
        output.contains('value="fr"')
    }

    void 'an explicit available="false" keeps the full JVM locale list even when locales are published'() {
        given:
        request.servletContext.setAttribute('availableLocales',
                [Locale.forLanguageTag('en'), Locale.forLanguageTag('es')])

        when:
        String output = applyTemplate('<g:localeSelect name="lang" available="false"/>')

        then: 'a locale outside the published list is still offered'
        output.contains('value="fr"')
    }

    void 'available="true" falls back to the current locale when nothing is published'() {
        when:
        String output = applyTemplate('<g:localeSelect name="lang" available="true"/>')

        then: 'no servlet-context attribute is present, so the select is not empty and not the full JVM list'
        output.contains('<select')
        !output.contains('value="fr"')
    }

    void 'the published list can drive a custom link dropdown like the create-app layout does'() {
        given: 'the same servlet-context attribute the i18n plugin publishes and the create-app main.gsp reads'
        request.servletContext.setAttribute('availableLocales',
                [Locale.forLanguageTag('en'), Locale.forLanguageTag('es')])

        when: 'the navbar snippet from the generated layout is rendered'
        String output = applyTemplate('''
            <g:set var="availableLocales" value="${application.getAttribute('availableLocales')}"/>
            <g:if test="${availableLocales && availableLocales.size() > 1}">
                <g:each in="${availableLocales}" var="availableLocale">
                    <a href="?lang=${availableLocale.toLanguageTag()}">${availableLocale.getDisplayName(availableLocale)}</a>
                </g:each>
            </g:if>
        '''.stripIndent())

        then: 'one ?lang= link per published locale is produced'
        output.contains('href="?lang=en"')
        output.contains('href="?lang=es"')
    }
}
