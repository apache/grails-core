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

package grails.test.mixin

import grails.artefact.Artefact
import grails.converters.JSON
import grails.converters.XML
import grails.testing.web.controllers.ControllerUnitTest
import grails.validation.Validateable
import grails.web.Controller
import grails.web.mime.MimeUtility
import org.grails.plugins.testing.GrailsMockMultipartFile
import org.grails.web.servlet.mvc.SynchronizerTokensHolder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.web.multipart.MultipartFile
import spock.lang.Specification

import jakarta.servlet.http.HttpServletResponse

/**
 * Specification for the behavior of the ControllerUnitTestMixin
 *
 * @author Graeme Rocher
 */
class ControllerUnitTestMixinTests extends Specification implements ControllerUnitTest<TestController> {

    void testRenderText() {
        when:
        controller.renderText()
        
        then:
        response.text == "good"
    }

    void testCallingSuperMethod() {
        when:
        def subController = mockController(SubController)
        subController.method1()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 1'
    }

    void testSimpleControllerRedirect() {
        when:
        controller.redirectToController()
        
        then:
        response.redirectedUrl == '/bar'
    }

    void testRenderView() {
        when:
        controller.renderView()

        then:
        "/test/foo" == view
    }

    void testRenderXml() {
        when:
        controller.renderXml()

        then:
        "<book title='Great'/>" == controller.response.contentAsString
        "Great" == controller.response.xml.@title.text()
    }

    void testRenderJson() {
        when:
        controller.renderJson()

        then:
        '{"book":"Great"}' == controller.response.contentAsString
        "Great" == controller.response.json.book
    }

    void testRenderAsJson() {
        when:
        controller.renderAsJson()

        then:
        '{"foo":"bar"}' == controller.response.contentAsString
        "bar" == controller.response.json.foo
    }

    void testRenderState() {
        when:
        params.foo = "bar"
        request.bar = "foo"
        
        controller.renderState()

        def xml = response.xml

        then:
        xml.parameter.find { it.@name == 'foo' }.@value.text() == 'bar'
        xml.attribute.find { it.@name == 'bar' }.@value.text() == 'foo'
    }

    void testInjectedProperties() {
        expect:
        request != null
        response != null
        servletContext != null
        params != null
        grailsApplication != null
        applicationContext != null
        webRequest != null
    }

    void testControllerAutowiring() {
        messageSource.addMessage("foo.bar", request.locale, "Hello World")

        
        when:
        controller.renderMessage()

        then:
        'Hello World' == controller.response.contentAsString
    }

    void testRenderWithFormatXml() {
        when:
        response.format = 'xml'
        controller.renderWithFormat()

        then:
        '<?xml version="1.0" encoding="UTF-8"?><map><entry key="foo">bar</entry></map>' == response.contentAsString
    }

    void testRenderWithFormatHtml() {
        when:
        response.format = 'html'
        def model = controller.renderWithFormat()

        then:
        model?.foo == 'bar'
    }

    void testRenderWithRequestFormat() {
        when:
        request.format = 'xml'
        controller.renderWithRequestFormat()

        then:
        '<?xml version="1.0" encoding="UTF-8"?><map><entry key="foo">bar</entry></map>' == response.contentAsString
    }

    void testWithFormTokenSynchronization() {
        when:
        controller.renderWithForm()

        then:
        "Bad" == response.contentAsString

        when:
        def holder = SynchronizerTokensHolder.store(session)
        def token = holder.generateToken('/test')
        params[SynchronizerTokensHolder.TOKEN_URI] = '/test'
        params[SynchronizerTokensHolder.TOKEN_KEY] = token

        response.reset()

        controller.renderWithForm()

        then:
        "Good" == response.contentAsString
    }

    void testFileUpload() {
        when:
        final file = new GrailsMockMultipartFile("myFile", "foo".bytes)
        request.addFile(file)
        controller.uploadFile()

        then:
        file.targetFileLocation.path == "${File.separatorChar}local${File.separatorChar}disk${File.separatorChar}myFile"
    }

    void testRenderBasicTemplateNoTags() {
        given:
        def templateName = 'testRenderBasicTemplateNoTags'

        when:
        groovyPages["/test/_${templateName}.gsp" as String] = 'Hello <%= 10 %>'
        controller.renderTemplate(templateName)

        then:
        response.contentAsString == "Hello 10"
    }

    void testRenderBasicTemplateWithTags() {
        given:
        def templateName = 'testRenderBasicTemplateWithTags'

        when:
        messageSource.addMessage("foo.bar", request.locale, "World")
        groovyPages["/test/_${templateName}.gsp" as String] = 'Hello <g:message code="foo.bar" />'
        controller.renderTemplate(templateName)

        then:
        response.contentAsString == "Hello World"
    }

    void testRenderBasicTemplateWithLinkTag() {
        given:
        def templateName = 'testRenderBasicTemplateWithLinkTag'

        when:
        groovyPages["/test/_${templateName}.gsp" as String] = 'Hello <g:createLink controller="bar" />'
        controller.renderTemplate(templateName)

        then:
        response.contentAsString == "Hello /bar"
    }

    void testInvokeTagLibraryMethod() {
        when:
        controller.renderTemplateContents()

        then:
        response.contentAsString == "/foo"
    }

    void testInvokeTagLibraryMethodViaNamespace() {
        when:
        groovyPages['/test/_bar.gsp'] = 'Hello <g:message code="foo.bar" />'

        controller.renderTemplateContentsViaNamespace()

        then:
        response.contentAsString == "Hello World"
    }

    void testInvokeWithCommandObject() {
        given:
        def cmd = new TestCommand()
        cmd.name = ''

        when:
        cmd.validate()
        controller.handleCommand(cmd)

        then:
        response.contentAsString == 'Bad'

        when:
        response.reset()
        cmd.name = "Bob"
        cmd.clearErrors()
        cmd.validate()
        controller.handleCommand(cmd)

        then:
        response.contentAsString == 'Good'
    }

    void testAllowedMethods() {
        when:
        controller.action1()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'action 1'

        when:
        response.reset()
        request.method = "POST"
        controller.action1()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'action 1'

        when:
        response.reset()
        request.method = "PUT"
        controller.action1()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'action 1'

        when:
        response.reset()
        request.method = "PATCH"
        controller.action1()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'action 1'

        when:
        response.reset()
        request.method = "DELETE"
        controller.action1()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'action 1'

        when:
        response.reset()
        request.method = 'POST'
        controller.action2()
        
        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'action 2'

        when:
        response.reset()
        request.method = 'GET'
        controller.action2()
        
        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'PUT'
        controller.action2()
        
        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'PATCH'
        controller.action2()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'DELETE'
        controller.action2()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'POST'
        controller.action3()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'action 3'

        when:
        response.reset()
        request.method = 'PUT'
        controller.action3()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'action 3'

        when:
        response.reset()
        request.method = 'PATCH'
        controller.action3()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'action 3'

        when:
        response.reset()
        request.method = 'GET'
        controller.action3()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'DELETE'
        controller.action3()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'GET'
        controller.method1()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 1'

        when:
        response.reset()
        request.method = "POST"
        controller.method1()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 1'

        when:
        response.reset()
        request.method = "PUT"
        controller.method1()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 1'

        when:
        response.reset()
        request.method = "PATCH"
        controller.method1()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 1'

        when:
        response.reset()
        request.method = "DELETE"
        controller.method1()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 1'

        when:
        response.reset()
        request.method = 'POST'
        controller.method2()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 2'

        when:
        response.reset()
        request.method = 'GET'
        controller.method2()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'PUT'
        controller.method2()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'PATCH'
        controller.method2()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'DELETE'
        controller.method2()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'POST'
        controller.method3()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 3'

        when:
        response.reset()
        request.method = 'PUT'
        controller.method3()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 3'

        when:
        response.reset()
        request.method = 'PATCH'
        controller.method3()

        then:
        response.status == HttpServletResponse.SC_OK
        response.contentAsString == 'method 3'

        when:
        response.reset()
        request.method = 'GET'
        controller.method3()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'DELETE'
        controller.method3()

        then:
        response.status == HttpServletResponse.SC_METHOD_NOT_ALLOWED
    }

    void testContentTypeConstantsAreAddedToTheTest() {
        expect:
        FORM_CONTENT_TYPE == 'application/x-www-form-urlencoded'
        MULTIPART_FORM_CONTENT_TYPE == 'multipart/form-data'
        ALL_CONTENT_TYPE == '*/*'
        HTML_CONTENT_TYPE == 'text/html'
        XHTML_CONTENT_TYPE == 'application/xhtml+xml'
        XML_CONTENT_TYPE == 'application/xml'
        JSON_CONTENT_TYPE == 'application/json'
        TEXT_XML_CONTENT_TYPE == 'text/xml'
        TEXT_JSON_CONTENT_TYPE == 'text/json'
        HAL_JSON_CONTENT_TYPE == 'application/hal+json'
        HAL_XML_CONTENT_TYPE == 'application/hal+xml'
        ATOM_XML_CONTENT_TYPE == 'application/atom+xml'
    }
    
    void testDefaultRequestMethod() {
        expect:
        request.method == 'GET'
    }
}

@Controller
class TestController  {

    static allowedMethods = [action2: 'POST', action3: ['POST', 'PUT', 'PATCH'], method2: 'POST', method3: ['POST', 'PUT', 'PATCH']]

    def action1 = {
        render 'action 1'
    }

    def action2 = {
        render 'action 2'
    }

    def action3 = {
        render 'action 3'
    }

    def method1() {
        render 'method 1'
    }

    def method2() {
        render 'method 2'
    }

    def method3() {
        render 'method 3'
    }

    def handleCommand( TestCommand test ) {
        if (test.hasErrors()) {
            render "Bad"
        }
        else {
            render "Good"
        }
    }

    def uploadFile = {
        assert request.method == 'POST'
        assert request.contentType == "multipart/form-data"
        MultipartFile file = request.getFile("myFile")
        file.transferTo(new File("/local/disk/myFile"))
    }

    def renderTemplateContents = {
        def contents = createLink(controller:"foo")
        render contents
    }
    def renderTemplateContentsViaNamespace = {
        def contents = g.render(template:"bar")

        render contents
    }
    def renderText = {
        render "good"
    }

    def redirectToController = {
        redirect(controller:"bar")
    }

    def renderView = {
        render(view:"foo")
    }

    def renderTemplate(String template) {
        render(template: template)
    }

    def renderXml = {
        render(contentType:"text/xml") {
            book(title:"Great")
        }
    }

    def renderJson = {
        render(contentType:"text/json") {
            book "Great"
        }
    }

    def renderAsJson = {
        render([foo:"bar"] as JSON)
    }

    def renderWithFormat = {
        def data = [foo:"bar"]
        withFormat {
            xml { render data as XML }
            html data
        }
    }

    def renderWithRequestFormat = {
        def data = [foo:"bar"]
        request.withFormat {
            xml { render data as XML }
            html data
        }
    }

    def renderState = {
        render(contentType:"text/xml") {
            println params.foo
            println request.bar
            requestInfo {
                for (p in params) {
                    parameter(name:p.key, value:p.value)
                }
                request.each {
                    attribute(name:it.key, value:it.value)
                }
            }
        }
    }

    MessageSource messageSource
    @Autowired
    MimeUtility mimeUtility

    def renderMessage() {
        assert mimeUtility !=null
        assert grailsLinkGenerator != null
        render messageSource.getMessage("foo.bar", null, request.locale)
    }

    def renderWithForm() {
        withForm {
            render "Good"
        }.invalidToken {
            render "Bad"
        }
    }
}

class TestCommand {
    String name

    static constraints = {
        name blank:false
    }
}

@Artefact("Controller")
class SubController extends TestController {
    def method1() {
        super.method1()
    }
}

class SomeValidateableThing implements Validateable {
    String name
    String email

    static constraints = {
        name validator: { val ->
            if (val.size() % 2 == 1) {
                'no.odd.characters'
            }
        }
        email email: true
    }
}
