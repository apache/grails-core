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
package org.grails.web.servlet

import spock.lang.Issue
import spock.lang.Specification

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.http.HttpHeaders
import org.grails.web.servlet.mvc.exceptions.ControllerExecutionException

/**
 * Tests for the render method.
 */
class RenderMethodTests extends Specification implements ControllerUnitTest<RenderController> {

    void 'renders file bytes with an explicit content type'() {
        when: 'the controller renders file bytes'
        controller.render(
                file: 'hello'.bytes,
                contentType: 'text/plain'
        )

        then: 'the file contents are written to the response'
        response.contentAsString == 'hello'
    }

    void 'requires a content type when rendering file bytes'() {
        when: 'the controller renders file bytes without a content type'
        controller.render(file: 'hello'.bytes)

        then: 'a controller execution exception is thrown'
        def e = thrown(ControllerExecutionException)
        e.message == 'Argument [file] of render method specified without valid [contentType] argument'
    }

    void 'renders an input stream without setting content disposition by default'() {
        when: 'the controller renders a file input stream'
        controller.render(
                file: new ByteArrayInputStream('hello'.bytes),
                contentType: 'text/plain'
        )

        then: 'the response contains the stream contents and no attachment header'
        response.contentAsString == 'hello'
        response.getHeader(HttpHeaders.CONTENT_DISPOSITION) == null
    }

    void 'renders an input stream as an attachment when a filename is provided'() {
        when: 'the controller renders a file input stream with a filename'
        controller.render(
                file: new ByteArrayInputStream('hello'.bytes),
                contentType: 'text/plain',
                fileName: 'hello.txt'
        )

        then: 'the response includes the file contents and attachment header'
        response.contentAsString == 'hello'
        response.getHeader(HttpHeaders.CONTENT_DISPOSITION) == 'attachment;filename="hello.txt"'
    }

    void 'renders text with the configured status code'() {
        when: 'the controller renders a message with a status'
        controller.renderMessageWithStatus()

        then: 'the response body and status are both preserved'
        response.contentAsString == 'test'
        response.status == 500
    }

    @Issue('GRAILS-3393')
    void 'rejects render invocations with a missing named argument key'() {
        when: 'render is invoked with a positional map after named arguments'
        controller.renderBug()

        then: 'a missing method exception is thrown'
        thrown(MissingMethodException)
    }

    void 'renders an object using its string representation'() {
        when: 'the controller renders an object'
        controller.renderObject()

        then: 'the object string value is written to the response'
        response.contentAsString == 'bar'
    }

    void 'renders a closure with the configured status code'() {
        when: 'the controller renders a closure with a status'
        controller.renderClosureWithStatus()

        then: 'the response status is updated'
        response.status == 500
    }

    void 'renders a list'() {
        when: 'the controller renders a list'
        controller.renderList()

        then: 'the response contains the list representation'
        response.contentAsString == '[1, 2, 3]'
    }

    void 'renders a map'() {
        when: 'the controller renders a map'
        controller.renderMap()

        then: 'the response contains the map representation'
        response.contentAsString == "['a':1, 'b':2]"
    }

    void 'renders a GString'() {
        when: 'the controller renders a GString'
        controller.renderGString()

        then: 'the rendered response is available'
        response.contentAsString == 'test render'
    }

    void 'renders plain text'() {
        when: 'the controller renders text'
        controller.renderText()

        then: 'the rendered response are available'
        response.contentAsString == 'test render'
    }

    void 'renders xml markup with the requested content type'() {
        when: 'the controller renders XML markup'
        controller.renderXML()

        then: 'the response contains XML and the XML content type'
        response.contentAsString == '<hello>world</hello>'
        response.contentType == 'text/xml;charset=utf-8'
    }

    void 'renders a view'() {
        when: 'the controller renders a view'
        controller.renderView()

        then: 'the expected view is selected'
        view == '/render/testView'
    }

    void 'renders a view with an explicit content type'() {
        when: 'the controller renders an XML view'
        controller.renderXmlView()

        then: 'the expected view and content type are selected'
        view == '/render/xmlView'
        response.contentType == 'text/xml;charset=utf-8'
    }

    void 'renders a template with a model'() {
        given: 'a template is available for rendering'
        views['/render/_testTemplate.gsp'] = 'hello ${hello}!'

        when: 'the controller renders the template'
        controller.renderTemplate()

        then: 'the rendered output contains the model values'
        response.contentType == 'text/html;charset=UTF-8'
        response.contentAsString == 'hello world!'
    }

    void 'renders a template collection using the implicit it variable'() {
        given: 'a template that uses the implicit it variable'
        def templateName = 'testRenderTemplateWithCollectionUsingImplicitITVariable'
        views["/render/_${templateName}.gsp" as String] = '${it.firstName} ${it.middleName}<br/>'

        when: 'the controller renders the template for each collection element'
        controller.renderTemplateWithCollection(templateName)

        then: 'each collection element is rendered with the implicit variable'
        response.contentAsString == 'Jacob Ray<br/>Zachary Scott<br/>'
    }

    void 'renders a template collection using an explicit variable name'() {
        given: 'a template that uses an explicit variable name'
        def templateName = 'testRenderTemplateWithCollectionUsingExplicitVariableName'
        views["/render/_${templateName}.gsp" as String] = '${person.firstName} ${person.middleName}<br/>'

        when: 'the controller renders the template for each collection element'
        controller.renderTemplateWithCollectionAndExplicitVarName(templateName)

        then: 'each collection element is rendered with the explicit variable'
        response.contentAsString == 'Jacob Ray<br/>Zachary Scott<br/>'
    }

    void 'renders a template with an explicit content type'() {
        given: 'an XML template is available for rendering'
        views['/render/_xmlTemplate.gsp'] = '<hello>world</hello>'

        when: 'the controller renders the XML template'
        controller.renderXmlTemplate()

        then: 'the response contains the template output and XML content type'
        response.contentAsString == '<hello>world</hello>'
        response.contentType == 'text/xml;charset=utf-8'
    }
}

@Artefact('Controller')
class RenderController {

    def renderBug() {
        render(view: 'login', [foo: 'bar'])
    }

    def renderView() {
        render(view: 'testView')
    }

    def renderXmlView() {
        render(view: 'xmlView', contentType: 'text/xml')
    }

    def renderObject() {
        render(new RenderTest(foo: 'bar'))
    }

    def renderClosureWithStatus() {
        render(status: 500) {}
    }

    def renderMessageWithStatus() {
        render(text: 'test', status: 500)
    }

    def renderList() {
        render([1, 2, 3])
    }

    def renderMap() {
        render([a: 1, b: 2])
    }

    def renderText() {
        render('test render')
    }

    def renderGString() {
        def foo = 'render'
        render("test $foo")
    }

    def renderXML() {
        render(contentType: 'text/xml') {
            hello('world')
        }
    }

    def renderTemplate() {
        render(template: 'testTemplate', model: [hello: 'world'])
    }

    def renderTemplateWithCollection(String template) {
        def people = [
            [firstName: 'Jacob', middleName: 'Ray'],
            [firstName: 'Zachary', middleName: 'Scott']
        ]
        render(template: template, collection: people)
    }

    def renderTemplateWithCollectionAndExplicitVarName(String template) {
        def people = [
            [firstName: 'Jacob', middleName: 'Ray'],
            [firstName: 'Zachary', middleName: 'Scott']
        ]
        render(var: 'person', template: template, collection: people)
    }

    def renderXmlTemplate() {
        render(template: 'xmlTemplate', contentType: 'text/xml')
    }
}

class RenderTest {

    String foo

    String toString() { foo }
}
