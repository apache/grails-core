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

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import grails.testing.mixin.integration.Integration

/**
 * Integration tests for GSP Tag Libraries.
 * Tests both custom tag libraries and built-in Grails tags.
 */
@Integration
class TagLibSpec extends Specification {

    @Shared
    HttpClient client

    def setup() {
        client = client ?: HttpClient.create(new URL("http://localhost:$serverPort"))
    }

    def cleanupSpec() {
        client.close()
    }

    // ========== Custom Tag: hello ==========

    def "custom:hello tag renders greeting with name attribute"() {
        when: "calling the hello tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testHelloTag?name=Grails'),
            String
        )

        then: "greeting is rendered with the name"
        response.status.code == 200
        response.body().contains('Hello, Grails!')
    }

    def "custom:hello tag uses default name when not provided"() {
        when: "calling the hello tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testHelloTag'),
            String
        )

        then: "greeting is rendered with default name"
        response.status.code == 200
        response.body().contains('Hello, World!')
    }

    // ========== Custom Tag: wrapper ==========

    def "custom:wrapper tag renders title and body content"() {
        when: "calling the wrapper tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testWrapperTag?title=My%20Section&content=Section%20content'),
            String
        )

        then: "wrapper with title and content is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<div class="wrapper">')
        body.contains('<h2>My Section</h2>')
        body.contains('Section content')
    }

    def "custom:wrapper tag applies custom CSS class"() {
        when: "calling the wrapper tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testWrapperTag?title=Test&content=Test&cssClass=custom-wrapper'),
            String
        )

        then: "custom CSS class is applied"
        response.status.code == 200
        response.body().contains('<div class="custom-wrapper">')
    }

    // ========== Custom Tag: iterate ==========

    def "custom:iterate tag iterates over items"() {
        when: "calling the iterate tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testIterateTag?items=A,B,C'),
            String
        )

        then: "all items are rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('Item: A')
        body.contains('Item: B')
        body.contains('Item: C')
    }

    def "custom:iterate tag uses separator between items"() {
        when: "calling the iterate tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testIterateTag?items=X,Y,Z&separator=-'),
            String
        )

        then: "items are separated by the specified separator"
        response.status.code == 200
        def body = response.body()
        body.contains('Item: X-Item: Y-Item: Z')
    }

    // ========== Custom Tag: showIf/hideIf ==========

    def "custom:showIf tag shows content when condition is true"() {
        when: "calling the conditional tags test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testConditionalTags?condition=true'),
            String
        )

        then: "showIf content is visible, hideIf content is hidden"
        response.status.code == 200
        def body = response.body()
        body.contains('id="showIf-result">VISIBLE')
        !body.contains('id="hideIf-result">HIDDEN')
    }

    def "custom:hideIf tag shows content when condition is false"() {
        when: "calling the conditional tags test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testConditionalTags?condition=false'),
            String
        )

        then: "showIf content is hidden, hideIf content is visible"
        response.status.code == 200
        def body = response.body()
        !body.contains('id="showIf-result">VISIBLE')
        body.contains('id="hideIf-result">HIDDEN')
    }

    // ========== Custom Tag: formatted ==========

    @Unroll
    def "custom:formatted tag formats value as #format"() {
        when: "calling the formatted tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/tagLibTest/testFormattedTag?value=${value}&format=${format}&decimals=${decimals}"),
            String
        )

        then: "value is formatted correctly"
        response.status.code == 200
        response.body().contains(expected)

        where:
        value    | format       | decimals | expected
        100.0    | 'currency'   | 2        | '$100.00'
        0.25     | 'percentage' | 1        | '25.0%'
        1234.567 | 'number'     | 2        | '1234.57'
    }

    // ========== Custom Tag: list ==========

    def "custom:list tag renders unordered list by default"() {
        when: "calling the list tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testListTag?items=Apple,Banana,Cherry'),
            String
        )

        then: "unordered list is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<ul>')
        body.contains('<li>Apple</li>')
        body.contains('<li>Banana</li>')
        body.contains('<li>Cherry</li>')
        body.contains('</ul>')
    }

    def "custom:list tag renders ordered list when type is ordered"() {
        when: "calling the list tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testListTag?items=First,Second,Third&type=ordered'),
            String
        )

        then: "ordered list is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<ol>')
        body.contains('</ol>')
    }

    // ========== Custom Tag: panel ==========

    def "custom:panel tag renders panel with title and body"() {
        when: "calling the panel tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testPanelTag?title=Info%20Panel&type=info&content=Panel%20content'),
            String
        )

        then: "panel is rendered correctly"
        response.status.code == 200
        def body = response.body()
        body.contains('class="panel panel-info"')
        body.contains('class="panel-header"')
        body.contains('<h3>Info Panel</h3>')
        body.contains('class="panel-body"')
        body.contains('Panel content')
    }

    def "custom:panel tag renders collapse button when collapsible"() {
        when: "calling the panel tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testPanelTag?title=Collapsible&collapsible=true'),
            String
        )

        then: "collapse button is rendered"
        response.status.code == 200
        response.body().contains('class="collapse-btn"')
    }

    // ========== Custom Tag: badge ==========

    @Unroll
    def "custom:badge tag renders badge with type=#type and size=#size"() {
        when: "calling the badge tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/tagLibTest/testBadgeTag?type=${type}&size=${size}&content=${content}"),
            String
        )

        then: "badge is rendered with correct classes"
        response.status.code == 200
        def body = response.body()
        body.contains("badge-${type}")
        body.contains("badge-${size}")
        body.contains(">${content}<")

        where:
        type      | size    | content
        'success' | 'small' | '10'
        'warning' | 'large' | '5'
        'danger'  | 'normal'| '99'
    }

    // ========== Custom Tag: progress ==========

    def "custom:progress tag renders progress bar with percentage"() {
        when: "calling the progress tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testProgressTag?value=75&max=100'),
            String
        )

        then: "progress bar is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('class="progress"')
        body.contains('class="progress-bar"')
        body.contains('style="width: 75%"')
        body.contains('75%')
    }

    def "custom:progress tag hides label when showLabel is false"() {
        when: "calling the progress tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testProgressTag?value=50&max=100&showLabel=false'),
            String
        )

        then: "progress bar is rendered without label text"
        response.status.code == 200
        def body = response.body()
        body.contains('class="progress-bar"')
        !body.contains('>50%<')
    }

    // ========== Custom Tag: repeat ==========

    def "custom:repeat tag repeats body content specified times"() {
        when: "calling the repeat tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testRepeatTag?times=3'),
            String
        )

        then: "content is repeated"
        response.status.code == 200
        def body = response.body()
        body.contains('Repeat #1')
        body.contains('Repeat #2')
        body.contains('Repeat #3')
    }

    def "custom:repeat tag uses separator between repetitions"() {
        when: "calling the repeat tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testRepeatTag?times=2&separator=%20-%20'),
            String
        )

        then: "repetitions are separated"
        response.status.code == 200
        response.body().contains('Repeat #1 - Repeat #2')
    }

    // ========== Custom Tag: raw ==========

    def "custom:raw tag outputs unescaped HTML content"() {
        when: "calling the raw tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testRawTag'),
            String
        )

        then: "HTML content is not escaped"
        response.status.code == 200
        response.body().contains('<strong>Bold Text</strong>')
    }

    // ========== Custom Tag: definitionList ==========

    def "custom:definitionList tag renders definition list from map"() {
        when: "calling the definition list tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testDefinitionListTag'),
            String
        )

        then: "definition list is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<dl>')
        body.contains('<dt>name</dt>')
        body.contains('<dd>John Doe</dd>')
        body.contains('<dt>age</dt>')
        body.contains('<dd>30</dd>')
        body.contains('</dl>')
    }

    // ========== Custom Tag: requestInfo ==========

    def "custom:requestInfo tag retrieves request attributes"() {
        when: "calling the request info tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testRequestInfoTag?attr=method'),
            String
        )

        then: "request attribute is output"
        response.status.code == 200
        response.body().contains('GET')
    }

    // ========== Custom Tag: sessionValue ==========

    def "custom:sessionValue tag displays default when session value not set"() {
        when: "calling the session value tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testSessionValueTag?key=nonexistent&default=DefaultUser'),
            String
        )

        then: "default value is displayed"
        response.status.code == 200
        response.body().contains('DefaultUser')
    }

    // ========== Custom Tag: setVar ==========

    def "custom:setVar tag sets pageScope variable"() {
        when: "calling the setVar tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testSetVarTag?varName=testVar&varValue=TestValue'),
            String
        )

        then: "variable is set and accessible"
        response.status.code == 200
        response.body().contains('Variable testVar = TestValue')
    }

    // ========== Custom Tag: alert ==========

    @Unroll
    def "custom:alert tag renders #type alert"() {
        when: "calling the alert tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET("/tagLibTest/testAlertTag?type=${type}&message=${URLEncoder.encode(message, 'UTF-8')}"),
            String
        )

        then: "alert is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains("alert-${type}")
        body.contains(message)

        where:
        type      | message
        'info'    | 'InfoMessage'
        'warning' | 'WarningMessage'
        'danger'  | 'DangerMessage'
        'success' | 'SuccessMessage'
    }

    def "custom:alert tag renders dismissible button when dismissible=true"() {
        when: "calling the alert tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testAlertTag?type=info&dismissible=true'),
            String
        )

        then: "close button is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('alert-dismissible')
        body.contains('class="close"')
    }

    // ========== Custom Tag: join ==========

    def "custom:join tag joins items with separator"() {
        when: "calling the join tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testJoinTag?items=red,green,blue&separator=-'),
            String
        )

        then: "items are joined with separator"
        response.status.code == 200
        response.body().contains('red-green-blue')
    }

    // ========== Custom Tag: cssClass ==========

    def "custom:cssClass tag builds class string from boolean attributes"() {
        when: "calling the cssClass tag test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testCssClassTag?base=btn&active=true&disabled=false&highlighted=true'),
            String
        )

        then: "class string is built correctly"
        response.status.code == 200
        def body = response.body()
        body.contains('btn')
        body.contains('active')
        body.contains('highlighted')
        !body.contains('disabled')
    }

    // ========== Built-in Tag: g:if ==========

    def "g:if tag shows content when condition is true"() {
        when: "calling the built-in if test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInIf?value=10'),
            String
        )

        then: "if condition content is shown"
        response.status.code == 200
        def body = response.body()
        body.contains('Greater than 5')
        !body.contains('Less than 5')
    }

    def "g:elseif and g:else work correctly"() {
        when: "calling the built-in if test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInIf?value=30'),
            String
        )

        then: "elseif content is shown"
        response.status.code == 200
        response.body().contains('Over 20')
    }

    // ========== Built-in Tag: g:each ==========

    def "g:each tag iterates over collection"() {
        when: "calling the built-in each test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInEach?items=A,B,C'),
            String
        )

        then: "each item is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('[A]')
        body.contains('[B]')
        body.contains('[C]')
    }

    def "g:each tag provides status variable"() {
        when: "calling the built-in each test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInEach?items=X,Y,Z'),
            String
        )

        then: "status index is available"
        response.status.code == 200
        def body = response.body()
        body.contains('0:X')
        body.contains('1:Y')
        body.contains('2:Z')
    }

    // ========== Built-in Tag: g:collect ==========

    def "g:collect tag transforms items"() {
        when: "calling the built-in collect test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInCollect?items=apple,banana'),
            String
        )

        then: "items are transformed"
        response.status.code == 200
        def body = response.body()
        body.contains('APPLE')
        body.contains('BANANA')
    }

    // ========== Built-in Tag: g:findAll ==========

    def "g:findAll tag filters items"() {
        when: "calling the built-in findAll test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInFindAll?threshold=7'),
            String
        )

        then: "only matching items are rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('8')
        body.contains('9')
        body.contains('10')
        !body.contains('7 ')
    }

    // ========== Built-in Tag: g:link ==========

    def "g:link tag creates link with controller and action"() {
        when: "calling the built-in link test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInLink?targetController=book&targetAction=show&targetId=42&linkText=View'),
            String
        )

        then: "link is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<a href=')
        body.contains('/book/show/42')
        body.contains('>View</a>')
    }

    // ========== Built-in Tag: g:createLink ==========

    def "g:createLink tag creates URL string"() {
        when: "calling the built-in createLink test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInCreateLink?targetController=book&targetAction=list'),
            String
        )

        then: "URL is rendered"
        response.status.code == 200
        response.body().contains('/book/list')
    }

    // ========== Built-in Tag: g:form ==========

    def "g:form tag creates form with action"() {
        when: "calling the built-in form test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInForm?targetController=book&targetAction=save'),
            String
        )

        then: "form is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<form')
        body.contains('action=')
        body.contains('/book/save')
        body.contains('method="post"')
    }

    // ========== Built-in Tag: g:message ==========

    def "g:message tag renders message with default"() {
        when: "calling the built-in message test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInMessage?code=nonexistent.key&default=Default%20Message'),
            String
        )

        then: "default message is rendered"
        response.status.code == 200
        response.body().contains('Default Message')
    }

    // ========== Built-in Tag: g:formatDate ==========

    def "g:formatDate tag formats date"() {
        when: "calling the built-in formatDate test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInFormatDate'),
            String
        )

        then: "date is formatted"
        response.status.code == 200
        // Just verify it rendered something in date format
        response.body() =~ /\d{4}-\d{2}-\d{2}/
    }

    // ========== Built-in Tag: g:formatNumber ==========

    def "g:formatNumber tag formats number"() {
        when: "calling the built-in formatNumber test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInFormatNumber?number=1234567.89'),
            String
        )

        then: "number is formatted"
        response.status.code == 200
        // Should contain formatted number (locale-dependent)
        response.body() =~ /1.*234.*567/
    }

    // ========== Built-in Tag: g:set ==========

    def "g:set tag sets and updates variables"() {
        when: "calling the built-in set test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInSet?initialValue=First&newValue=Second'),
            String
        )

        then: "variable values are correct"
        response.status.code == 200
        def body = response.body()
        body.contains('id="set-initial">First')
        body.contains('id="set-updated">Second')
    }

    // ========== Built-in Tag: g:join ==========

    def "g:join tag joins items with delimiter"() {
        when: "calling the built-in join test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInJoin?items=red,green,blue&delimiter=%20-%20'),
            String
        )

        then: "items are joined"
        response.status.code == 200
        response.body().contains('red - green - blue')
    }

    // ========== Built-in Tag: g:include ==========

    def "g:include tag includes content from another action"() {
        when: "calling the built-in include test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInInclude?message=Test%20Message'),
            String
        )

        then: "included content is rendered"
        response.status.code == 200
        response.body().contains('Included content: Test Message')
    }

    // ========== Built-in Tag: g:render ==========

    def "g:render tag renders template with model"() {
        when: "calling the built-in render test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInRender?text=Hello%20Template'),
            String
        )

        then: "template is rendered"
        response.status.code == 200
        response.body().contains('Template content: Hello Template')
    }

    // ========== Built-in Tag: g:while ==========

    def "g:while tag loops while condition is true"() {
        when: "calling the built-in while test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInWhile?count=3'),
            String
        )

        then: "loop executes correct number of times"
        response.status.code == 200
        def body = response.body()
        body.contains('Count: 1')
        body.contains('Count: 2')
        body.contains('Count: 3')
        !body.contains('Count: 4')
    }

    // ========== Built-in Tag: g:uploadForm ==========

    def "g:uploadForm tag creates multipart form"() {
        when: "calling the built-in uploadForm test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInUploadForm'),
            String
        )

        then: "multipart form is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<form')
        body.contains('enctype="multipart/form-data"')
    }

    // ========== Built-in Tag: g:select ==========

    def "g:select tag creates select element with options"() {
        when: "calling the built-in select test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInSelect?selected=2'),
            String
        )

        then: "select with options is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<select')
        body.contains('<option')
        body.contains('value="2" selected')
    }

    // ========== Built-in Tag: g:radio ==========

    def "g:radio tag creates radio buttons"() {
        when: "calling the built-in radio test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInRadio?selected=Option%20B'),
            String
        )

        then: "radio buttons are rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('type="radio"')
        body.contains('checked="checked"')
    }

    // ========== Built-in Tag: g:checkBox ==========

    def "g:checkBox tag creates checkbox"() {
        when: "calling the built-in checkbox test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInCheckBox?checked=true'),
            String
        )

        then: "checkbox is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('type="checkbox"')
        body.contains('checked="checked"')
    }

    // ========== Built-in Tag: g:textArea ==========

    def "g:textArea tag creates textarea"() {
        when: "calling the built-in textarea test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInTextArea?value=Test%20Content&rows=5&cols=40'),
            String
        )

        then: "textarea is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<textarea')
        body.contains('rows="5"')
        body.contains('cols="40"')
        body.contains('Test Content')
    }

    // ========== Built-in Tag: g:textField ==========

    def "g:textField tag creates text input"() {
        when: "calling the built-in textField test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInTextField?value=Test%20Value&maxlength=50'),
            String
        )

        then: "text field is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('type="text"')
        body.contains('value="Test Value"')
        body.contains('maxlength="50"')
    }

    // ========== Built-in Tag: g:passwordField ==========

    def "g:passwordField tag creates password input"() {
        when: "calling the built-in passwordField test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInPasswordField'),
            String
        )

        then: "password field is rendered"
        response.status.code == 200
        response.body().contains('type="password"')
    }

    // ========== Built-in Tag: g:hiddenField ==========

    def "g:hiddenField tag creates hidden input"() {
        when: "calling the built-in hiddenField test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInHiddenField?value=secret-value'),
            String
        )

        then: "hidden field is rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('type="hidden"')
        body.contains('value="secret-value"')
    }

    // ========== Built-in Tag: g:fieldValue ==========

    def "g:fieldValue tag extracts bean field value"() {
        when: "calling the built-in fieldValue test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInFieldValue?field=title'),
            String
        )

        then: "field value is extracted"
        response.status.code == 200
        response.body().contains('Grails in Action')
    }

    // ========== Built-in Tag: g:sortableColumn ==========

    def "g:sortableColumn tag creates sortable table header"() {
        when: "calling the built-in sortableColumn test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInSortableColumn'),
            String
        )

        then: "sortable columns are rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<th')
        body.contains('Title')
        body.contains('Author')
    }

    // ========== Built-in Tag: g:paginate ==========

    def "g:paginate tag creates pagination links"() {
        when: "calling the built-in paginate test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testBuiltInPaginate?total=100&max=10&offset=0'),
            String
        )

        then: "pagination links are rendered"
        response.status.code == 200
        def body = response.body()
        // Pagination should contain some links
        body.contains('class=')
    }

    // ========== Complex/Combined Tests ==========

    def "nested custom tags render correctly"() {
        when: "calling the nested tags test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testNestedTags'),
            String
        )

        then: "nested tags are rendered"
        response.status.code == 200
        def body = response.body()
        body.contains('<ul>')
        body.contains('class="badge')
    }

    def "tags work with complex model data"() {
        when: "calling the tags with model test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testTagsWithModel'),
            String
        )

        then: "model data is processed correctly"
        response.status.code == 200
        def body = response.body()
        body.contains('panel-success')  // Alice is active
        body.contains('panel-default')  // Charlie is not active
        body.contains('Alice')
        body.contains('Bob')
        body.contains('Charlie')
        body.contains('Admin')
        body.contains('User')
    }

    def "encoding tags properly escape content"() {
        when: "calling the encoding tags test endpoint"
        def response = client.toBlocking().exchange(
            HttpRequest.GET('/tagLibTest/testEncodingTags'),
            String
        )

        then: "content is properly encoded"
        response.status.code == 200
        def body = response.body()
        // HTML encoded content should have escaped tags
        body.contains('&lt;script&gt;')
        // Raw content should preserve HTML
        body.contains('id="raw-content">')
    }
}
