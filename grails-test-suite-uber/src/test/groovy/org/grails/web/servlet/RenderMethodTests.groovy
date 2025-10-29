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

import grails.testing.web.controllers.ControllerUnitTest
import grails.web.http.HttpHeaders
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.grails.web.servlet.mvc.exceptions.ControllerExecutionException
import grails.artefact.Artefact
import spock.lang.Specification

/**
 * Tests for the render method.
 *
 * @author Graeme Rocher
 */
class RenderMethodTests extends Specification implements ControllerUnitTest<RenderController> {

    void testRenderFile() {
        when:
        controller.render file:"hello".bytes, contentType:"text/plain"

        then:
        "hello" == response.contentAsString

        when:
        response.reset()
        controller.render file:"hello".bytes
       
        then:
        thrown(ControllerExecutionException)

        when:
        response.reset()
        controller.render file:new ByteArrayInputStream("hello".bytes), contentType:"text/plain"

        then:
        "hello" == response.contentAsString
        null == response.getHeader(HttpHeaders.CONTENT_DISPOSITION)

        when:
        response.reset()
        controller.render file:new ByteArrayInputStream("hello".bytes), contentType:"text/plain", fileName:"hello.txt"
        
        then:
        "hello" == response.contentAsString
        "attachment;filename=\"hello.txt\"" == response.getHeader(HttpHeaders.CONTENT_DISPOSITION)
    }

    void testRenderMethodWithStatus() {
        when:
        controller.renderMessageWithStatus()
        GrailsMockHttpServletResponse response = controller.response

        then:
        "test" == response.contentAsString
        500 == response.status
    }

    // bug GRAILS-3393
    void testMissingNamedArgumentKey() {
        when:
        controller.renderBug()
        
        then:
        thrown(MissingMethodException)
    }

    void testRenderObject() {
        when:
        controller.renderObject()
        GrailsMockHttpServletResponse response = controller.response

        then:
        "bar" == response.contentAsString
    }
    
    void testRenderClosureWithStatus() {
        when:
        controller.renderClosureWithStatus()
        GrailsMockHttpServletResponse response = controller.response

        then:
        500 == response.status
    }
    
    void testRenderList() {
        when:
        controller.renderList()
        GrailsMockHttpServletResponse response = controller.response

        then:
        "[1, 2, 3]" == response.contentAsString
    }

    void testRenderMap() {
        when:
        controller.renderMap()
        GrailsMockHttpServletResponse response = controller.response
        
        then:
        response.contentAsString == "['a':1, 'b':2]"
    }
    
    void testRenderGString() {
        when:
        controller.renderGString()
        GrailsMockHttpServletRequest request = controller.request
        GrailsMockHttpServletResponse response = controller.response

        then:
        request != null
        response != null
        response.contentAsString == "test render"
    }

    void testRenderText() {
        when:
        controller.renderText()
        GrailsMockHttpServletRequest request = controller.request
        GrailsMockHttpServletResponse response = controller.response

        then:
        request != null
        response != null
        response.contentAsString == "test render"
    }

    void testRenderXml() {
        when:
        controller.renderXML()
        GrailsMockHttpServletRequest request = controller.request
        GrailsMockHttpServletResponse response = controller.response

        then:
        response != null
        request != null
        response.contentAsString == "<hello>world</hello>"
        response.contentType == "text/xml;charset=utf-8"
    }

    void testRenderView() {
        when:
        controller.renderView()

        then:
        controller.modelAndView
        controller.modelAndView.viewName == '/render/testView'
    }

    void testRenderViewWithContentType() {
        when:
        controller.renderXmlView()

        then:
        controller.modelAndView
        controller.modelAndView.viewName == '/render/xmlView'
        response.contentType == 'text/xml;charset=utf-8'
    }

    void testRenderTemplate() {
        when:
        views["/render/_testTemplate.gsp"] = 'hello ${hello}!'
        controller.renderTemplate()

        then:
        response.contentType == "text/html;charset=UTF-8"
        response.contentAsString == "hello world!"
    }

    void testRenderTemplateWithCollectionUsingImplicitITVariable() {
        given:
        def templateName = 'testRenderTemplateWithCollectionUsingImplicitITVariable'

        when:
        views["/render/_${templateName}.gsp" as String] = '${it.firstName} ${it.middleName}<br/>'
        controller.renderTemplateWithCollection(templateName)

        then:
        response.contentAsString == 'Jacob Ray<br/>Zachary Scott<br/>'
    }

    void testRenderTemplateWithCollectionUsingExplicitVariableName() {
        given:
        def templateName = 'testRenderTemplateWithCollectionUsingExplicitVariableName'

        when:
        views["/render/_${templateName}.gsp" as String] = '${person.firstName} ${person.middleName}<br/>'
        controller.renderTemplateWithCollectionAndExplicitVarName(templateName)

        then:
        response.contentAsString == 'Jacob Ray<br/>Zachary Scott<br/>'
    }

    void testRenderTemplateWithContentType() {
        when:
        views["/render/_xmlTemplate.gsp"] = '<hello>world</hello>'
        controller.renderXmlTemplate()

        then:
        response.contentAsString == "<hello>world</hello>"
        response.contentType == "text/xml;charset=utf-8"
    }
}

@Artefact('Controller')
class RenderController {

    def renderBug() {
        render(view:'login', [foo:"bar"])
    }

    def renderView() { render(view:'testView') }
    def renderXmlView() {
        render(view:'xmlView', contentType:'text/xml')
    }
    def renderObject() {
        render new RenderTest(foo:"bar")
    }
    def renderClosureWithStatus() {
        render(status: 500) {
        }
    }
    def renderMessageWithStatus() {
        render text:"test", status:500
    }
    def renderList() {
        render([1, 2, 3])
    }
    def renderMap() {
        render([a:1, b:2])
    }
    def renderText() { render "test render" }
    def renderGString() {
        def foo = 'render'
        render "test $foo"
    }
    def renderXML() {
        render(contentType:"text/xml") { hello("world") }
    }
    def renderTemplate() {
        render(template:"testTemplate", model:[hello:"world"])
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
        render(template:"xmlTemplate",contentType:"text/xml")
    }
}

class RenderTest {
    String foo

    String toString() { foo }
}
