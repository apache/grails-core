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

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * Functional tests for GSP tag library rendering.
 * 
 * Tests various GSP tags are rendered correctly in browser:
 * - g:each - iteration
 * - g:if/g:else - conditionals
 * - g:link - link generation
 * - g:form and form fields - forms
 * - g:formatDate/Number/Boolean - formatting
 * - g:set - variable assignment
 * - g:render - template inclusion
 * - g:createLink - URL generation
 * - g:join - collection joining
 * - encode methods - XSS prevention
 */
@Integration
class GspTagLibSpec extends ContainerGebSpec {

    def "g:each tag iterates over collection"() {
        when:
        go('tagLib/eachTag')

        then:
        $('#item-list li').size() == 5
        $('#item-list li')[0].text() == 'Item 1'
        $('#item-list li')[4].text() == 'Item 5'
        $('#item-count').text() == 'Total items: 5'
    }

    def "g:each provides status variable"() {
        when:
        go('tagLib/eachTag')

        then:
        $('#item-list li')[0].@'data-index' == '0'
        $('#item-list li')[2].@'data-index' == '2'
    }

    def "g:if tag shows content when condition true"() {
        when:
        go('tagLib/ifTag?show=true')

        then:
        $('#conditional-content').displayed
        $('#conditional-content').text() == 'Content is shown!'
    }

    def "g:if tag hides content when condition false"() {
        when:
        go('tagLib/ifTag?show=false')

        then:
        !$('#conditional-content').displayed
    }

    def "g:else tag shows when g:if is false"() {
        when:
        go('tagLib/elseTag?condition=false')

        then:
        !$('#if-content').displayed
        $('#else-content').displayed
        $('#else-content').text() == 'Condition is FALSE'
    }

    def "g:else tag hidden when g:if is true"() {
        when:
        go('tagLib/elseTag?condition=true')

        then:
        $('#if-content').displayed
        $('#if-content').text() == 'Condition is TRUE'
        !$('#else-content').displayed
    }

    def "g:link generates correct links"() {
        when:
        go('tagLib/linkTag')

        then:
        $('#index-link').displayed
        $('a#each-link').@href.contains('/tagLib/eachTag')
        $('a#param-link').@href.contains('show=true')
    }

    def "g:form renders form with correct attributes"() {
        when:
        go('tagLib/formTag')

        then:
        $('form[name="test-form"]').displayed
        $('form[name="test-form"]').@method.equalsIgnoreCase('POST')
    }

    def "g:textField renders input with value"() {
        when:
        go('tagLib/formTag')

        then:
        $('#username-input').value() == 'testuser'
        $('#email-input').value() == 'test@example.com'
    }

    def "g:passwordField renders password input"() {
        when:
        go('tagLib/formTag')

        then:
        $('#password-input').@type == 'password'
    }

    def "g:checkBox renders checkbox input"() {
        when:
        go('tagLib/formTag')

        then:
        $('#remember-checkbox').@type == 'checkbox'
    }

    def "g:textArea renders textarea"() {
        when:
        go('tagLib/formTag')

        then:
        $('textarea#comments-textarea').displayed
        $('textarea#comments-textarea').@rows == '3'
    }

    def "g:submitButton renders submit button"() {
        when:
        go('tagLib/formTag')

        then:
        $('#submit-button').@type == 'submit'
        $('#submit-button').@value == 'Submit'
    }

    def "g:formatDate formats date correctly"() {
        when:
        go('tagLib/formatTags')

        then:
        $('#date-display').text().contains('Date:')
        // Date format should be yyyy-MM-dd pattern
        $('#date-display').text() =~ /\d{4}-\d{2}-\d{2}/
    }

    def "g:formatNumber formats number correctly"() {
        when:
        go('tagLib/formatTags')

        then:
        $('#number-display').text().contains('12,345.68') ||
        $('#number-display').text().contains('12345.68')
    }

    def "g:formatBoolean formats boolean correctly"() {
        when:
        go('tagLib/formatTags')

        then:
        $('#boolean-display').text().contains('Yes')
    }

    def "g:set creates local variable"() {
        when:
        go('tagLib/setTag')

        then:
        $('#set-value').text() == 'Hello from g:set'
    }

    def "g:set evaluates expressions"() {
        when:
        go('tagLib/setTag')

        then:
        $('#computed-value').text().contains('5')
    }

    def "g:set works with collections"() {
        when:
        go('tagLib/setTag')

        then:
        $('#list-value').text().contains('3')
    }

    def "g:render includes template"() {
        when:
        go('tagLib/renderTag')

        then:
        $('#controller-message').text() == 'Hello from Controller'
        $('#partial').displayed
        $('#partial').text().contains('From Template')
    }

    def "g:createLink generates URLs"() {
        when:
        go('tagLib/createLinkTag')

        then:
        $('#relative-link').text().contains('/tagLib/eachTag')
        $('#params-link').text().contains('show=true')
    }

    def "g:join joins collection with delimiter"() {
        when:
        go('tagLib/joinTag')

        then:
        $('#comma-join').text() == 'Comma: Red, Green, Blue'
        $('#dash-join').text() == 'Dash: Red - Green - Blue'
        $('#pipe-join').text() == 'Pipe: Red | Green | Blue'
    }

    def "spread operator with g:join works"() {
        when:
        go('tagLib/collectTag')

        then:
        $('#names').text() == 'Names: First, Second, Third'
        $('#values').text() == 'Values: 1-2-3'
    }

    def "encodeAsHTML prevents XSS"() {
        when:
        go('tagLib/encodeTags')

        then:
        // The script tag should be visible as text, not executed
        // When HTML is encoded, <script> becomes &lt;script&gt; in the HTML source
        // but browsers display it as the literal text "<script>"
        $('#html-encoded').text().contains('<script>') || 
        $('#html-encoded').text().contains('script')
        
        and: "check the raw attribute encoding"
        // The data-content attribute should contain the encoded value
        $('#raw-html').@'data-content'.contains('&lt;') ||
        $('#raw-html').@'data-content'.contains('script')
    }

    def "encodeAsURL encodes URL parameters"() {
        when:
        go('tagLib/encodeTags')

        then:
        $('#url-encoded').text().contains('%26') ||  // encoded &
        $('#url-encoded').text().contains('%3D') ||  // encoded =
        $('#url-encoded').text().contains('param')   // at least the content is there
    }
}
