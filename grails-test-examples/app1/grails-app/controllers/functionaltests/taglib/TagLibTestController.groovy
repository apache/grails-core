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

import jakarta.servlet.http.Cookie

/**
 * Controller for testing GSP Tag Libraries.
 * Provides actions that render GSP views using both custom and built-in tags.
 */
class TagLibTestController {

    // ========== Custom Tag Tests ==========

    def testHelloTag() {
        [name: params.name ?: 'World']
    }

    def testWrapperTag() {
        [title: params.title ?: 'My Title', content: params.content ?: 'Default content', cssClass: params.cssClass]
    }

    def testIterateTag() {
        def items = params.items?.split(',')?.toList() ?: ['Item 1', 'Item 2', 'Item 3']
        [items: items, separator: params.separator]
    }

    def testConditionalTags() {
        def condition = params.condition?.toString() == 'true'
        [condition: condition]
    }

    def testFormattedTag() {
        def value = params.value ? params.value as Double : 123.456
        def format = params.format ?: 'currency'
        def decimals = params.decimals ?: '2'
        [value: value, format: format, decimals: decimals]
    }

    def testListTag() {
        def items = params.items?.split(',')?.toList() ?: ['Apple', 'Banana', 'Cherry']
        def type = params.type ?: 'unordered'
        [items: items, type: type, cssClass: params.cssClass]
    }

    def testPanelTag() {
        [title: params.title ?: 'Panel Title', type: params.type ?: 'default', 
         collapsible: params.collapsible?.toString() == 'true', content: params.content ?: 'Panel body content']
    }

    def testAppInfoTag() {
        [:]
    }

    def testBadgeTag() {
        [type: params.type ?: 'default', size: params.size ?: 'normal', content: params.content ?: '5']
    }

    def testProgressTag() {
        def value = params.value ? params.value as Integer : 75
        def max = params.max ? params.max as Integer : 100
        def showLabel = params.showLabel?.toString() != 'false'
        [value: value, max: max, showLabel: showLabel]
    }

    def testRepeatTag() {
        def times = params.times ? params.times as Integer : 3
        def separator = params.separator ?: ''
        [times: times, separator: separator]
    }

    def testRawTag() {
        def content = params.content ?: '<strong>Bold Text</strong>'
        [content: content]
    }

    def testDefinitionListTag() {
        def items = [name: 'John Doe', age: '30', city: 'New York']
        [items: items, cssClass: params.cssClass]
    }

    def testRequestInfoTag() {
        [attr: params.attr ?: 'contextPath']
    }

    def testSessionValueTag() {
        if (params.setValue) {
            session.setAttribute('testKey', params.setValue)
        }
        [key: params.key ?: 'testKey', defaultValue: params.default ?: 'Anonymous']
    }

    def testSetVarTag() {
        [varName: params.varName ?: 'myVar', varValue: params.varValue ?: 'myValue']
    }

    def testAlertTag() {
        [type: params.type ?: 'info', dismissible: params.dismissible?.toString() == 'true', 
         icon: params.icon, message: params.message ?: 'This is an alert message']
    }

    def testJoinTag() {
        def items = params.items?.split(',')?.toList() ?: ['apple', 'banana', 'cherry']
        def separator = params.separator ?: ', '
        [items: items, separator: separator]
    }

    def testCssClassTag() {
        def active = params.active?.toString() == 'true'
        def disabled = params.disabled?.toString() == 'true'
        def highlighted = params.highlighted?.toString() == 'true'
        [base: params.base ?: 'btn', active: active, disabled: disabled, highlighted: highlighted]
    }

    // ========== Built-in Tag Tests ==========

    def testBuiltInIf() {
        def value = params.value ? params.value as Integer : 10
        [value: value]
    }

    def testBuiltInEach() {
        def items = params.items?.split(',')?.toList() ?: ['A', 'B', 'C', 'D', 'E']
        [items: items]
    }

    def testBuiltInCollect() {
        def items = params.items?.split(',')?.toList() ?: ['apple', 'banana', 'cherry']
        [items: items]
    }

    def testBuiltInFindAll() {
        def items = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
        def threshold = params.threshold ? params.threshold as Integer : 5
        [items: items, threshold: threshold]
    }

    def testBuiltInLink() {
        [controller: params.targetController ?: 'book', action: params.targetAction ?: 'show', 
         id: params.targetId ?: '1', linkText: params.linkText ?: 'View Book']
    }

    def testBuiltInCreateLink() {
        [controller: params.targetController ?: 'book', action: params.targetAction ?: 'list']
    }

    def testBuiltInForm() {
        [controller: params.targetController ?: 'book', action: params.targetAction ?: 'save']
    }

    def testBuiltInMessage() {
        [code: params.code ?: 'default.welcome.message', 
         defaultMsg: params.default ?: 'Welcome!', 
         args: params.args?.split(',')?.toList()]
    }

    def testBuiltInFormatDate() {
        def date = new Date()
        [date: date, format: params.format ?: 'yyyy-MM-dd HH:mm:ss', dateStyle: params.dateStyle, timeStyle: params.timeStyle]
    }

    def testBuiltInFormatNumber() {
        def number = params.number ? params.number as Double : 1234567.89
        [number: number, type: params.type ?: 'number', currencyCode: params.currencyCode ?: 'USD',
         maxFractionDigits: params.maxFractionDigits ? params.maxFractionDigits as Integer : 2]
    }

    def testBuiltInResource() {
        [dir: params.dir ?: 'images', file: params.file ?: 'logo.png']
    }

    def testBuiltInSet() {
        def initialValue = params.initialValue ?: 'Initial'
        def newValue = params.newValue ?: 'Updated'
        [initialValue: initialValue, newValue: newValue]
    }

    def testBuiltInJoin() {
        def items = params.items?.split(',')?.toList() ?: ['red', 'green', 'blue']
        def delimiter = params.delimiter ?: ' | '
        [items: items, delimiter: delimiter]
    }

    def testBuiltInInclude() {
        [message: params.message ?: 'Hello from include', controller: 'tagLibTest', action: 'includedContent']
    }

    def includedContent() {
        // params.message may be a list if passed through g:include, get the actual value
        def msg = params.list('message')?.find() ?: params.message ?: 'No message'
        render "Included content: ${msg}"
    }

    def testBuiltInRender() {
        [templateName: params.template ?: 'simpleTemplate', model: [text: params.text ?: 'Template text']]
    }

    def testBuiltInWhile() {
        def count = params.count ? params.count as Integer : 5
        [count: count]
    }

    def testBuiltInCookie() {
        if (params.setCookie) {
            response.addCookie(new Cookie('testCookie', params.setCookie))
        }
        [cookieName: params.cookieName ?: 'testCookie', defaultValue: params.default ?: 'no cookie']
    }

    def testBuiltInHeader() {
        if (params.setHeader) {
            response.setHeader('X-Custom-Header', params.setHeader)
        }
        [headerName: params.headerName ?: 'X-Custom-Header']
    }

    def testBuiltInUploadForm() {
        [controller: params.targetController ?: 'upload', action: params.targetAction ?: 'process']
    }

    def testBuiltInSelect() {
        def options = [
            [key: '1', value: 'Option 1'],
            [key: '2', value: 'Option 2'],
            [key: '3', value: 'Option 3']
        ]
        def selected = params.selected ?: '2'
        [options: options, selected: selected, name: params.name ?: 'mySelect']
    }

    def testBuiltInRadio() {
        def options = ['Option A', 'Option B', 'Option C']
        def selected = params.selected ?: 'Option B'
        [options: options, selected: selected, name: params.name ?: 'myRadio']
    }

    def testBuiltInCheckBox() {
        def checked = params.checked?.toString() == 'true'
        [checked: checked, name: params.name ?: 'myCheckbox', value: params.value ?: 'yes']
    }

    def testBuiltInTextArea() {
        [name: params.name ?: 'myTextArea', value: params.value ?: 'Default text content', 
         rows: params.rows ?: '5', cols: params.cols ?: '40']
    }

    def testBuiltInTextField() {
        [name: params.name ?: 'myTextField', value: params.value ?: 'Default value', 
         maxlength: params.maxlength ?: '100']
    }

    def testBuiltInPasswordField() {
        [name: params.name ?: 'myPassword', value: params.value ?: '']
    }

    def testBuiltInHiddenField() {
        [name: params.name ?: 'myHidden', value: params.value ?: 'hidden-value']
    }

    def testBuiltInFieldValue() {
        // g:fieldValue requires a proper bean with property access, use Expando for dynamic properties
        def book = new Expando(title: 'Grails in Action', author: 'Glen Smith', year: 2014)
        [book: book, field: params.field ?: 'title']
    }

    def testBuiltInSortableColumn() {
        def currentSort = params.sort ?: 'title'
        def currentOrder = params.order ?: 'asc'
        [currentSort: currentSort, currentOrder: currentOrder]
    }

    def testBuiltInPaginate() {
        def total = params.total ? params.total as Integer : 100
        def max = params.max ? params.max as Integer : 10
        def offset = params.offset ? params.offset as Integer : 0
        [total: total, max: max, offset: offset]
    }

    // ========== Combined/Complex Tests ==========

    def testNestedTags() {
        def items = ['First', 'Second', 'Third']
        [items: items]
    }

    def testTagsWithModel() {
        def users = [
            [name: 'Alice', role: 'Admin', active: true],
            [name: 'Bob', role: 'User', active: true],
            [name: 'Charlie', role: 'User', active: false]
        ]
        [users: users]
    }

    def testEncodingTags() {
        def htmlContent = '<script>alert("XSS")</script>'
        def urlContent = 'hello world&foo=bar'
        def jsonContent = [name: 'Test', value: 123]
        [htmlContent: htmlContent, urlContent: urlContent, jsonContent: jsonContent]
    }

    def testLayoutTags() {
        [title: params.title ?: 'Page Title', content: params.content ?: 'Page content goes here']
    }

    def testAssetTags() {
        [stylesheetName: params.stylesheet ?: 'application', scriptName: params.script ?: 'application']
    }
}
