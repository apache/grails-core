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
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.web.taglib.FormTagLib
import spock.lang.Specification

/**
 * Tests that {@code g:localeSelect}'s {@code pinDefault}/{@code loc.default} honor
 * {@code grails.i18n.default.locale} — the same property that drives the i18n plugin's
 * available-locale discovery — rather than only the Spring Boot {@code spring.web.locale}.
 */
class LocaleSelectConfiguredDefaultSpec extends Specification implements TagLibUnitTest<FormTagLib> {

    Closure doWithConfig() {{ PropertySourcesConfig config ->
        config.merge(['grails': ['i18n': ['default': ['locale': 'es']]]])
    }}

    void 'pinDefault pins the grails.i18n.default.locale locale and flags it as default'() {
        given:
        request.servletContext.setAttribute('availableLocales',
                [Locale.forLanguageTag('en'), Locale.forLanguageTag('es'), Locale.forLanguageTag('it')])

        when:
        String output = applyTemplate(
                '<g:localeSelect available="true" pinDefault="true" var="loc">[${loc.tag}:${loc.default}]</g:localeSelect>')

        then: 'es leads the list and is the only entry flagged as the default'
        output.indexOf('[es:true]') == output.indexOf('[')
        output.contains('[en:false]')
        output.contains('[it:false]')
    }
}
