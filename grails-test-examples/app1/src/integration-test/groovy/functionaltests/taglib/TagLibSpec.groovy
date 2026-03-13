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
package functionaltests.taglib

import spock.lang.Specification
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Integration tests for GSP Tag Libraries.
 * Tests both custom tag libraries and built-in Grails tags.
 */
@Integration
class TagLibSpec extends Specification implements HttpClientSupport {

    // ========== Custom Tag: hello ==========

    def "custom:hello tag renders greeting with name attribute"() {
        when: "calling the hello tag test endpoint"
        def response = http('/tagLibTest/testHelloTag?name=Grails')

        then: "greeting is rendered with the name"
        response.expectContains(200, 'Hello, Grails!')
    }

    def "custom:hello tag uses default name when not provided"() {
        when: "calling the hello tag test endpoint"
        def response = http('/tagLibTest/testHelloTag')

        then: "greeting is rendered with default name"
        response.expectContains(200, 'Hello, World!')
    }

    // ========== Custom Tag: wrapper ==========

    def "custom:wrapper tag renders title and body content"() {
        when: "calling the wrapper tag test endpoint"
        def response = http(
            '/tagLibTest/testWrapperTag?title=My%20Section&content=Section%20content'
        )

        then: "wrapper with title and content is rendered"
        response.expectContains(200, '<div class="wrapper">')
                .expectContains('<h2>My Section</h2>')
                .expectContains('Section content')
    }

    def "custom:wrapper tag applies custom CSS class"() {
        when: "calling the wrapper tag test endpoint"
        def response = http(
            '/tagLibTest/testWrapperTag?title=Test&content=Test&cssClass=custom-wrapper'
        )

        then: "custom CSS class is applied"
        response.expectContains(200, '<div class="custom-wrapper">')
    }

    // ========== Custom Tag: iterate ==========

    def "custom:iterate tag iterates over items"() {
        when: "calling the iterate tag test endpoint"
        def response = http('/tagLibTest/testIterateTag?items=A,B,C')

        then: "all items are rendered"
        response.expectContains(200, 'Item: A')
                .expectContains('Item: B')
                .expectContains('Item: C')
    }

    def "custom:iterate tag uses separator between items"() {
        when: "calling the iterate tag test endpoint"
        def response = http('/tagLibTest/testIterateTag?items=X,Y,Z&separator=-')

        then: "items are separated by the specified separator"
        response.expectContains(200, 'Item: X-Item: Y-Item: Z')
    }

    // ========== Custom Tag: showIf/hideIf ==========

    def "custom:showIf tag shows content when condition is true"() {
        when: "calling the conditional tags test endpoint"
        def response = http('/tagLibTest/testConditionalTags?condition=true')

        then: "showIf content is visible, hideIf content is hidden"
        response.expectContains(200, 'id="showIf-result">VISIBLE')
                .expectNotBodyContains('id="hideIf-result">HIDDEN')
    }

    def "custom:hideIf tag shows content when condition is false"() {
        when: "calling the conditional tags test endpoint"
        def response = http('/tagLibTest/testConditionalTags?condition=false')

        then: "showIf content is hidden, hideIf content is visible"
        response.expectContains(200, 'id="hideIf-result">HIDDEN')
                .expectNotBodyContains('id="showIf-result">VISIBLE')
    }

    // ========== Custom Tag: formatted ==========

    @Unroll
    def "custom:formatted tag formats value as #format"() {
        when: "calling the formatted tag test endpoint"
        def response = http(
            "/tagLibTest/testFormattedTag?value=${value}&format=${format}&decimals=${decimals}"
        )

        then: "value is formatted correctly"
        response.expectContains(200, expected)

        where:
        value    | format       | decimals | expected
        100.0    | 'currency'   | 2        | '$100.00'
        0.25     | 'percentage' | 1        | '25.0%'
        1234.567 | 'number'     | 2        | '1234.57'
    }

    // ========== Custom Tag: list ==========

    def "custom:list tag renders unordered list by default"() {
        when: "calling the list tag test endpoint"
        def response = http('/tagLibTest/testListTag?items=Apple,Banana,Cherry')

        then: "unordered list is rendered"
        response.expectContains(200, '<ul>')
                .expectContains('<li>Apple</li>')
                .expectContains('<li>Banana</li>')
                .expectContains('<li>Cherry</li>')
                .expectContains('</ul>')
    }

    def "custom:list tag renders ordered list when type is ordered"() {
        when: "calling the list tag test endpoint"
        def response = http('/tagLibTest/testListTag?items=First,Second,Third&type=ordered')

        then: "ordered list is rendered"
        response.expectContains(200, '<ol>')
                .expectContains('</ol>')
    }

    // ========== Custom Tag: panel ==========

    def "custom:panel tag renders panel with title and body"() {
        when: "calling the panel tag test endpoint"
        def response = http(
                '/tagLibTest/testPanelTag?title=Info%20Panel&type=info&content=Panel%20content'
        )

        then: "panel is rendered correctly"
        response.expectContains(200, 'class="panel panel-info"')
                .expectContains('class="panel-header"')
                .expectContains('<h3>Info Panel</h3>')
                .expectContains('class="panel-body"')
                .expectContains('Panel content')
    }

    def "custom:panel tag renders collapse button when collapsible"() {
        when: "calling the panel tag test endpoint"
        def response = http(
            '/tagLibTest/testPanelTag?title=Collapsible&collapsible=true'
        )

        then: "collapse button is rendered"
        response.expectContains(200, 'class="collapse-btn"')
    }

    // ========== Custom Tag: badge ==========

    @Unroll
    def "custom:badge tag renders badge with type=#type and size=#size"() {
        when: "calling the badge tag test endpoint"
        def response = http(
            "/tagLibTest/testBadgeTag?type=${type}&size=${size}&content=${content}"
        )

        then: "badge is rendered with correct classes"
        response.expectContains(200, "badge-${type}")
                .expectContains("badge-${size}")
                .expectContains(">${content}<")

        where:
        type      | size    | content
        'success' | 'small' | '10'
        'warning' | 'large' | '5'
        'danger'  | 'normal'| '99'
    }

    // ========== Custom Tag: progress ==========

    def "custom:progress tag renders progress bar with percentage"() {
        when: "calling the progress tag test endpoint"
        def response = http('/tagLibTest/testProgressTag?value=75&max=100')

        then: "progress bar is rendered"
        response.expectContains(200, 'class="progress"')
                .expectContains('class="progress-bar"')
                .expectContains('style="width: 75%"')
                .expectContains('75%')
    }

    def "custom:progress tag hides label when showLabel is false"() {
        when: "calling the progress tag test endpoint"
        def response = http('/tagLibTest/testProgressTag?value=50&max=100&showLabel=false')

        then: "progress bar is rendered without label text"
        response.expectContains(200, 'class="progress-bar"')
                .expectNotBodyContains('>50%<')
    }

    // ========== Custom Tag: repeat ==========

    def "custom:repeat tag repeats body content specified times"() {
        when: "calling the repeat tag test endpoint"
        def response = http('/tagLibTest/testRepeatTag?times=3')

        then: "content is repeated"
        response.expectContains(200, 'Repeat #1')
                .expectContains('Repeat #2')
                .expectContains('Repeat #3')
    }

    def "custom:repeat tag uses separator between repetitions"() {
        when: "calling the repeat tag test endpoint"
        def response = http('/tagLibTest/testRepeatTag?times=2&separator=%20-%20')

        then: "repetitions are separated"
        response.expectContains(200, 'Repeat #1 - Repeat #2')
    }

    // ========== Custom Tag: raw ==========

    def "custom:raw tag outputs unescaped HTML content"() {
        when: "calling the raw tag test endpoint"
        def response = http('/tagLibTest/testRawTag')

        then: "HTML content is not escaped"
        response.expectContains(200, '<strong>Bold Text</strong>')
    }

    // ========== Custom Tag: definitionList ==========

    def "custom:definitionList tag renders definition list from map"() {
        when: "calling the definition list tag test endpoint"
        def response = http('/tagLibTest/testDefinitionListTag')

        then: "definition list is rendered"
        response.expectContains(200, '<dl>')
                .expectContains('<dt>name</dt>')
                .expectContains('<dd>John Doe</dd>')
                .expectContains('<dt>age</dt>')
                .expectContains('<dd>30</dd>')
                .expectContains('</dl>')
    }

    // ========== Custom Tag: requestInfo ==========

    def "custom:requestInfo tag retrieves request attributes"() {
        when: "calling the request info tag test endpoint"
        def response = http('/tagLibTest/testRequestInfoTag?attr=method')

        then: "request attribute is output"
        response.expectContains(200, 'GET')
    }

    // ========== Custom Tag: sessionValue ==========

    def "custom:sessionValue tag displays default when session value not set"() {
        when: "calling the session value tag test endpoint"
        def response = http(
            '/tagLibTest/testSessionValueTag?key=nonexistent&default=DefaultUser'
        )

        then: "default value is displayed"
        response.expectContains(200, 'DefaultUser')
    }

    // ========== Custom Tag: setVar ==========

    def "custom:setVar tag sets pageScope variable"() {
        when: "calling the setVar tag test endpoint"
        def response = http(
            '/tagLibTest/testSetVarTag?varName=testVar&varValue=TestValue'
        )

        then: "variable is set and accessible"
        response.expectContains(200, 'Variable testVar = TestValue')
    }

    // ========== Custom Tag: alert ==========

    @Unroll
    def "custom:alert tag renders #type alert"() {
        when: "calling the alert tag test endpoint"
        def response = http(
            "/tagLibTest/testAlertTag?type=${type}&message=${URLEncoder.encode(message, 'UTF-8')}"
        )

        then: "alert is rendered"
        response.expectContains(200, "alert-${type}")
                .expectContains(message)

        where:
        type      | message
        'info'    | 'InfoMessage'
        'warning' | 'WarningMessage'
        'danger'  | 'DangerMessage'
        'success' | 'SuccessMessage'
    }

    def "custom:alert tag renders dismissible button when dismissible=true"() {
        when: "calling the alert tag test endpoint"
        def response = http('/tagLibTest/testAlertTag?type=info&dismissible=true')

        then: "close button is rendered"
        response.expectContains(200, 'alert-dismissible')
                .expectContains('class="close"')
    }

    // ========== Custom Tag: join ==========

    def "custom:join tag joins items with separator"() {
        when: "calling the join tag test endpoint"
        def response = http(
            '/tagLibTest/testJoinTag?items=red,green,blue&separator=-'
        )

        then: "items are joined with separator"
        response.expectContains(200, 'red-green-blue')
    }

    // ========== Custom Tag: cssClass ==========

    def "custom:cssClass tag builds class string from boolean attributes"() {
        when: "calling the cssClass tag test endpoint"
        def response = http(
            '/tagLibTest/testCssClassTag?base=btn&active=true&disabled=false&highlighted=true'
        )

        then: "class string is built correctly"
        response.expectContains(200, 'btn')
                .expectContains('active')
                .expectContains('highlighted')
                .expectNotBodyContains('disabled')
    }

    // ========== Built-in Tag: g:if ==========

    def "g:if tag shows content when condition is true"() {
        when: "calling the built-in if test endpoint"
        def response = http('/tagLibTest/testBuiltInIf?value=10')

        then: "if condition content is shown"
        response.expectContains(200, 'Greater than 5')
                .expectNotBodyContains('Less than 5')
    }

    def "g:elseif and g:else work correctly"() {
        when: "calling the built-in if test endpoint"
        def response = http('/tagLibTest/testBuiltInIf?value=30')

        then: "elseif content is shown"
        response.expectContains(200, 'Over 20')
    }

    // ========== Built-in Tag: g:each ==========

    def "g:each tag iterates over collection"() {
        when: "calling the built-in each test endpoint"
        def response = http('/tagLibTest/testBuiltInEach?items=A,B,C')

        then: "each item is rendered"
        response.expectContains(200, '[A]')
                .expectContains('[B]')
                .expectContains('[C]')
    }

    def "g:each tag provides status variable"() {
        when: "calling the built-in each test endpoint"
        def response = http('/tagLibTest/testBuiltInEach?items=X,Y,Z')

        then: "status index is available"
        response.expectContains(200, '0:X')
                .expectContains('1:Y')
                .expectContains('2:Z')
    }

    // ========== Built-in Tag: g:collect ==========

    def "g:collect tag transforms items"() {
        when: "calling the built-in collect test endpoint"
        def response = http('/tagLibTest/testBuiltInCollect?items=apple,banana')

        then: "items are transformed"
        response.expectContains(200, 'APPLE')
                .expectContains('BANANA')
    }

    // ========== Built-in Tag: g:findAll ==========

    def "g:findAll tag filters items"() {
        when: "calling the built-in findAll test endpoint"
        def response = http('/tagLibTest/testBuiltInFindAll?threshold=7')

        then: "only matching items are rendered"
        response.expectContains(200, '8')
                .expectContains('9')
                .expectContains('10')
                .expectNotBodyContains('7 ')
    }

    // ========== Built-in Tag: g:link ==========

    def "g:link tag creates link with controller and action"() {
        when: "calling the built-in link test endpoint"
        def response = http(
            '/tagLibTest/testBuiltInLink?targetController=book&targetAction=show&targetId=42&linkText=View'
        )

        then: "link is rendered"
        response.expectContains(200, '<a href=')
                .expectContains('/book/show/42')
                .expectContains('>View</a>')
    }

    // ========== Built-in Tag: g:createLink ==========

    def "g:createLink tag creates URL string"() {
        when: "calling the built-in createLink test endpoint"
        def response = http(
            '/tagLibTest/testBuiltInCreateLink?targetController=book&targetAction=list'
        )

        then: "URL is rendered"
        response.expectContains(200, '/book/list')
    }

    // ========== Built-in Tag: g:form ==========

    def "g:form tag creates form with action"() {
        when: "calling the built-in form test endpoint"
        def response = http(
            '/tagLibTest/testBuiltInForm?targetController=book&targetAction=save'
        )

        then: "form is rendered"
        response.expectContains(200, '<form')
                .expectContains('action=')
                .expectContains('/book/save')
                .expectContains('method="post"')
    }

    // ========== Built-in Tag: g:message ==========

    def "g:message tag renders message with default"() {
        when: "calling the built-in message test endpoint"
        def response = http(
            '/tagLibTest/testBuiltInMessage?code=nonexistent.key&default=Default%20Message'
        )

        then: "default message is rendered"
        response.expectContains(200, 'Default Message')
    }

    // ========== Built-in Tag: g:formatDate ==========

    def "g:formatDate tag formats date"() {
        when: "calling the built-in formatDate test endpoint"
        def response = http('/tagLibTest/testBuiltInFormatDate')

        then: "date is formatted"
        // Just verify it rendered something in date format
        response.expectContainsMatches(200, ~/\d{4}-\d{2}-\d{2}/)
    }

    // ========== Built-in Tag: g:formatNumber ==========

    def "g:formatNumber tag formats number"() {
        when: "calling the built-in formatNumber test endpoint"
        def response = http('/tagLibTest/testBuiltInFormatNumber?number=1234567.89')

        then: "number is formatted"
        // Should contain formatted number (locale-dependent)
        response.expectContainsMatches(200, ~/1.*234.*567/)
    }

    // ========== Built-in Tag: g:set ==========

    def "g:set tag sets and updates variables"() {
        when: "calling the built-in set test endpoint"
        def response = http(
            '/tagLibTest/testBuiltInSet?initialValue=First&newValue=Second'
        )

        then: "variable values are correct"
        response.expectContains(200, 'id="set-initial">First')
                .expectContains('id="set-updated">Second')
    }

    // ========== Built-in Tag: g:join ==========

    def "g:join tag joins items with delimiter"() {
        when: "calling the built-in join test endpoint"
        def response = http(
            '/tagLibTest/testBuiltInJoin?items=red,green,blue&delimiter=%20-%20'
        )

        then: "items are joined"
        response.expectContains(200, 'red - green - blue')
    }

    // ========== Built-in Tag: g:include ==========

    def "g:include tag includes content from another action"() {
        when: "calling the built-in include test endpoint"
        def response = http('/tagLibTest/testBuiltInInclude?message=Test%20Message')

        then: "included content is rendered"
        response.expectContains(200, 'Included content: Test Message')
    }

    // ========== Built-in Tag: g:render ==========

    def "g:render tag renders template with model"() {
        when: "calling the built-in render test endpoint"
        def response = http('/tagLibTest/testBuiltInRender?text=Hello%20Template')

        then: "template is rendered"
        response.expectContains(200, 'Template content: Hello Template')
    }

    // ========== Built-in Tag: g:while ==========

    def "g:while tag loops while condition is true"() {
        when: "calling the built-in while test endpoint"
        def response = http('/tagLibTest/testBuiltInWhile?count=3')

        then: "loop executes correct number of times"
        response.expectContains(200, 'Count: 1')
                .expectContains('Count: 2')
                .expectContains('Count: 3')
                .expectNotBodyContains('Count: 4')
    }

    // ========== Built-in Tag: g:uploadForm ==========

    def "g:uploadForm tag creates multipart form"() {
        when: "calling the built-in uploadForm test endpoint"
        def response = http('/tagLibTest/testBuiltInUploadForm')

        then: "multipart form is rendered"
        response.expectContains(200, '<form')
                .expectContains('enctype="multipart/form-data"')
    }

    // ========== Built-in Tag: g:select ==========

    def "g:select tag creates select element with options"() {
        when: "calling the built-in select test endpoint"
        def response = http('/tagLibTest/testBuiltInSelect?selected=2')

        then: "select with options is rendered"
        response.expectContains(200, '<select')
                .expectContains('<option')
                .expectContains('value="2" selected')
    }

    // ========== Built-in Tag: g:radio ==========

    def "g:radio tag creates radio buttons"() {
        when: "calling the built-in radio test endpoint"
        def response = http('/tagLibTest/testBuiltInRadio?selected=Option%20B')

        then: "radio buttons are rendered"
        response.expectContains(200, 'type="radio"')
                .expectContains('checked="checked"')
    }

    // ========== Built-in Tag: g:checkBox ==========

    def "g:checkBox tag creates checkbox"() {
        when: "calling the built-in checkbox test endpoint"
        def response = http('/tagLibTest/testBuiltInCheckBox?checked=true')

        then: "checkbox is rendered"
        response.expectContains(200, 'type="checkbox"')
                .expectContains('checked="checked"')
    }

    // ========== Built-in Tag: g:textArea ==========

    def "g:textArea tag creates textarea"() {
        when: "calling the built-in textarea test endpoint"
        def response = http(
            '/tagLibTest/testBuiltInTextArea?value=Test%20Content&rows=5&cols=40'
        )

        then: "textarea is rendered"
        response.expectContains(200, '<textarea')
                .expectContains('rows="5"')
                .expectContains('cols="40"')
                .expectContains('Test Content')
    }

    // ========== Built-in Tag: g:textField ==========

    def "g:textField tag creates text input"() {
        when: "calling the built-in textField test endpoint"
        def response = http('/tagLibTest/testBuiltInTextField?value=Test%20Value&maxlength=50')

        then: "text field is rendered"
        response.expectContains(200, 'type="text"')
                .expectContains('value="Test Value"')
                .expectContains('maxlength="50"')
    }

    // ========== Built-in Tag: g:passwordField ==========

    def "g:passwordField tag creates password input"() {
        when: "calling the built-in passwordField test endpoint"
        def response = http('/tagLibTest/testBuiltInPasswordField')

        then: "password field is rendered"
        response.expectContains(200, 'type="password"')
    }

    // ========== Built-in Tag: g:hiddenField ==========

    def "g:hiddenField tag creates hidden input"() {
        when: "calling the built-in hiddenField test endpoint"
        def response = http('/tagLibTest/testBuiltInHiddenField?value=secret-value')

        then: "hidden field is rendered"
        response.expectContains(200, 'type="hidden"')
                .expectContains('value="secret-value"')
    }

    // ========== Built-in Tag: g:fieldValue ==========

    def "g:fieldValue tag extracts bean field value"() {
        when: "calling the built-in fieldValue test endpoint"
        def response = http('/tagLibTest/testBuiltInFieldValue?field=title')

        then: "field value is extracted"
        response.expectContains(200, 'Grails in Action')
    }

    // ========== Built-in Tag: g:sortableColumn ==========

    def "g:sortableColumn tag creates sortable table header"() {
        when: "calling the built-in sortableColumn test endpoint"
        def response = http('/tagLibTest/testBuiltInSortableColumn')

        then: "sortable columns are rendered"
        response.expectContains(200, '<th')
                .expectContains('Title')
                .expectContains('Author')
    }

    // ========== Built-in Tag: g:paginate ==========

    def "g:paginate tag creates pagination links"() {
        when: "calling the built-in paginate test endpoint"
        def response = http('/tagLibTest/testBuiltInPaginate?total=100&max=10&offset=0')

        then: "pagination links are rendered"
        // Pagination should contain some links
        response.expectContains(200, 'class=')
    }

    // ========== Complex/Combined Tests ==========

    def "nested custom tags render correctly"() {
        when: "calling the nested tags test endpoint"
        def response = http('/tagLibTest/testNestedTags')

        then: "nested tags are rendered"
        response.expectContains(200, '<ul>')
                .expectContains('class="badge')
    }

    def "tags work with complex model data"() {
        when: "calling the tags with model test endpoint"
        def response = http('/tagLibTest/testTagsWithModel')

        then: "model data is processed correctly"
        response.expectContains(200, 'panel-success')  // Alice is active
                .expectContains('panel-default')  // Charlie is not active
                .expectContains('Alice')
                .expectContains('Bob')
                .expectContains('Charlie')
                .expectContains('Admin')
                .expectContains('User')
    }

    def "encoding tags properly escape content"() {
        when: "calling the encoding tags test endpoint"
        def response = http('/tagLibTest/testEncodingTags')

        then: "content is properly encoded"
        response.expectContains(200, '&lt;script&gt;') // HTML encoded content should have escaped tags
                .expectContains('id="raw-content">') // Raw content should preserve HTML
    }
}
