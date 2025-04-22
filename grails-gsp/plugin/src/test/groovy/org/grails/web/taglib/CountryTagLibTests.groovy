/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.taglib

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.plugins.web.taglib.CountryTagLib
import org.grails.plugins.web.taglib.FormTagLib
import org.springframework.web.servlet.support.RequestContextUtils as RCU
import spock.lang.Specification

class CountryTagLibTests extends Specification implements TagLibUnitTest<FormTagLib> {

    def testFullCountryListWithSelection() {
        when:
        def template = '<g:countrySelect name="foo" value="gbr" />'

        def result = applyTemplate(template, [:])

        then:
        result.contains('<option value="gbr" selected="selected" >United Kingdom</option>')

        CountryTagLib.ISO3166_3.every {
            result.contains("<option value=\"${it.key}\"")
            result.contains(">${it.value.encodeAsHTML()}</option>")
        }
    }

    def testReducedCountryListWithSelection() {
        when:
        def template = '<g:countrySelect name="foo" value="usa" from="[\'gbr\', \'usa\', \'deu\']"/>'
        def result = applyTemplate(template, [:])

        then:
        result.contains('<option value="usa" selected="selected" >United States</option>')

        ['gbr', 'usa', 'deu'].every {
            def value = CountryTagLib.ISO3166_3[it]
            result.contains("<option value=\"${it}\"")
            result.contains(">${value.encodeAsHTML()}</option>")
        }
    }

    def testCountryNamesWithValueMessagePrefix() {
        // Prepare the custom message source.
        when:
        def msgPrefix = "country"
        def codeMap = [gbr: "Royaume Uni", usa: "Les Etats Unis", deu: "Allemagne"]
        codeMap.each { code, val ->
            messageSource.addMessage(msgPrefix + "." + code, RCU.getLocale(request), val)
        }

        // Execute the template.
        def template = "<g:countrySelect name=\"foo\" valueMessagePrefix=\"${msgPrefix}\" value=\"usa\" from=\"['gbr', 'usa', 'deu']\"/>".toString()
        def result = applyTemplate(template, [:])

        then:
        result.contains("<option value=\"usa\" selected=\"selected\" >${codeMap['usa']}</option>")

        codeMap.every { code, val ->
            result.contains("<option value=\"${code}\"")
            result.contains(">${val}</option>")
        }
    }

    def testDefault() {

        when:
        def template = '<g:countrySelect name="foo" default="deu" from="[\'gbr\', \'usa\', \'deu\']"/>'
        def result = applyTemplate(template, [:])

        then:
        result.contains('<option value="deu" selected="selected" >Germany</option>')

        ['gbr', 'usa', 'deu'].every {
            def value = CountryTagLib.ISO3166_3[it]
            result.contains("<option value=\"${it}\"")
            result.contains(">${value.encodeAsHTML()}</option>")
        }
    }

    def testCountryDisplay() {
        when:
        def template = '<g:country code="deu"/>'
        String output = applyTemplate(template)

        then:
        output.contains('Germany')
    }

}
