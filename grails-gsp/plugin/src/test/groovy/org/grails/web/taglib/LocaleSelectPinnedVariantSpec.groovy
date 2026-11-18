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
 * Tests {@code pinDefault} against a country-variant default locale: the exact variant is
 * pinned, its sibling variants stay in the list, and only the pinned entry is flagged as
 * the default - a list can ship pt_BR and pt_PT with no bare pt bundle.
 */
class LocaleSelectPinnedVariantSpec extends Specification implements TagLibUnitTest<FormTagLib> {

    Closure doWithConfig() {{ PropertySourcesConfig config ->
        config.merge(['grails': ['i18n': ['default': ['locale': 'pt_BR']]]])
    }}

    void 'pinDefault pins the exact variant, keeps its siblings and flags a single default'() {
        given:
        request.servletContext.setAttribute('availableLocales',
                [Locale.forLanguageTag('en'), Locale.forLanguageTag('pt-PT'), Locale.forLanguageTag('pt-BR')])

        when:
        String output = applyTemplate(
                '<g:localeSelect available="true" pinDefault="true" var="loc">[${loc.tag}:${loc.default}]</g:localeSelect>')

        then: 'pt-BR leads the list and is the only entry flagged as the default'
        output.indexOf('[pt-BR:true]') == output.indexOf('[')

        and: 'the sibling variant is not dropped from the selector'
        output.contains('[pt-PT:false]')
        output.contains('[en:false]')
    }
}
