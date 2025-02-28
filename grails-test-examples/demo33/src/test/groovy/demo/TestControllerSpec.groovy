package demo

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.plugins.testing.GrailsMockMultipartFile
import org.grails.web.servlet.mvc.SynchronizerTokensHolder
import spock.lang.Ignore
import spock.lang.Specification

import jakarta.servlet.http.HttpServletResponse

class TestControllerSpec extends Specification implements ControllerUnitTest<TestController>, DataTest {

    void 'test render text'() {
        when:
        controller.renderText()

        then:
        response.text == "good"
    }

    void 'test simple controller redirect'() {
        when:
        controller.redirectToController()

        then:
        response.redirectedUrl == '/bar'
    }

    void 'test render view'() {
        when:
        controller.renderView()

        then:
        "/test/foo" == view
    }

    void 'test render XML'() {
        when:
        controller.renderXml()

        then:
        "<book title='Great'/>" == response.text
        "Great" == response.xml.@title.text()
    }

    void 'test render JSON'() {
        when:
        controller.renderJson()

        then:
        '{"book":"Great"}' == response.text
        "Great" == response.json.book
    }

    void 'test render as JSON'() {
        when:
        controller.renderAsJson()

        then:
        '{"foo":"bar"}' == response.text
        "bar" == response.json.foo
    }

    void 'test render state'() {
        when:
        params.foo = "bar"
        request.bar = "foo"
        controller.renderState()
        def xml = response.xml

        then:
        xml.parameter.find { it.@name == 'foo' }.@value.text() == 'bar'
        xml.attribute.find { it.@name == 'bar' }.@value.text() == 'foo'
    }

    void 'test injected properties'() {
        expect:
        request != null
        response != null
        servletContext != null
        params != null
        grailsApplication != null
        applicationContext != null
        webRequest != null
    }

    void 'test controller autowiring'() {
        when:
        messageSource.addMessage("foo.bar", request.locale, "Hello World")
        controller.renderMessage()

        then:
        'Hello World' == response.text
    }

    void 'test render withFormalt XML'() {
        when:
        response.format = 'xml'
        controller.renderWithFormat()

        then:
        '<?xml version="1.0" encoding="UTF-8"?><map><entry key="foo">bar</entry></map>' == response.text
    }

    void 'test render withFormat HTML'() {
        when:
        response.format = 'html'
        def model = controller.renderWithFormat()

        then:
        model?.foo == 'bar'
    }

    void 'test render with request format'() {
        when:
        request.format = 'xml'
        controller.renderWithRequestFormat()

        then:

        '<?xml version="1.0" encoding="UTF-8"?><map><entry key="foo">bar</entry></map>' == response.text
    }

    void 'test form token synchronization'() {
        when:
        controller.renderWithForm()

        then:
        "Bad" == response.text

        when:
        def holder = SynchronizerTokensHolder.store(session)
        def token = holder.generateToken('/test')
        params[SynchronizerTokensHolder.TOKEN_URI] = '/test'
        params[SynchronizerTokensHolder.TOKEN_KEY] = token
        response.reset()
        controller.renderWithForm()

        then:
        "Good" == response.text
    }

    void 'test file upload'() {
        when:
        final file = new GrailsMockMultipartFile("myFile", "foo".bytes)
        request.addFile(file)
        controller.uploadFile()

        then:
        file.targetFileLocation.path == "${File.separatorChar}local${File.separatorChar}disk${File.separatorChar}myFile"
    }

    void 'test render basic template with no tags'() {
        when:
        groovyPages['/test/_bar.gsp'] = 'Hello <%= 10 %>'
        controller.renderTemplate()

        then:
        response.text == "Hello 10"
    }

    @Ignore
    void 'test render basic template with tags'() {
        when:
        messageSource.addMessage("foo.bar", request.locale, "World")
        groovyPages['/test/_bar.gsp'] = 'Hello <g:message code="foo.bar" />'
        controller.renderTemplate()

        then:
        response.text == "Hello World"
    }

    @Ignore
    void 'test render basic template with link tag'() {
        when:
        groovyPages['/test/_bar.gsp'] = 'Hello <g:createLink controller="bar" />'
        controller.renderTemplate()

        then:
        response.text == "Hello /bar"
    }

    void 'test invoke tag library method'() {
        when:
        controller.renderTemplateContents()

        then:
        response.text == "/foo"
    }

    void 'test invoke tag library method via namespace'() {
        when:
        groovyPages['/test/_bar.gsp'] = 'Hello <g:message code="foo.baz" />'
        controller.renderTemplateContentsViaNamespace()

        then:
        response.text == "Hello foo.baz"
    }

    void 'test invoke with command object'() {
        when:
        def cmd = new TestCommand()
        cmd.name = ''
        cmd.validate()
        controller.handleCommand(cmd)

        then:
        response.text == 'Bad'

        when:
        response.reset()
        cmd.name = "Bob"
        cmd.clearErrors()
        cmd.validate()
        controller.handleCommand(cmd)

        then:
        response.text == 'Good'
    }

    void 'test allowed methods'() {
        when:
        controller.action1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'action 1'

        when:
        response.reset()
        request.method = "POST"
        controller.action1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'action 1'

        when:
        response.reset()
        request.method = "PUT"
        controller.action1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'action 1'

        when:
        response.reset()
        request.method = "PATCH"
        controller.action1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'action 1'

        when:
        response.reset()
        request.method = "DELETE"
        controller.action1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'action 1'

        when:
        response.reset()
        request.method = 'POST'
        controller.action2()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'action 2'

        when:
        response.reset()
        request.method = 'GET'
        controller.action2()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'PUT'
        controller.action2()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'PATCH'
        controller.action2()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'DELETE'
        controller.action2()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'POST'
        controller.action3()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'action 3'

        when:
        response.reset()
        request.method = 'PUT'
        controller.action3()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'action 3'

        when:
        response.reset()
        request.method = 'PATCH'
        controller.action3()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'action 3'

        when:
        response.reset()
        request.method = 'GET'
        controller.action3()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'DELETE'
        controller.action3()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'GET'
        controller.method1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'method 1'

        when:
        response.reset()
        request.method = "POST"
        controller.method1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'method 1'

        when:
        response.reset()
        request.method = "PUT"
        controller.method1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'method 1'

        when:
        response.reset()
        request.method = "PATCH"
        controller.method1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'method 1'

        when:
        response.reset()
        request.method = "DELETE"
        controller.method1()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'method 1'

        when:
        response.reset()
        request.method = 'POST'
        controller.method2()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'method 2'

        when:
        response.reset()
        request.method = 'GET'
        controller.method2()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'PUT'
        controller.method2()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'PATCH'
        controller.method2()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'DELETE'
        controller.method2()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'POST'
        controller.method3()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'method 3'

        when:
        response.reset()
        request.method = 'PUT'
        controller.method3()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'method 3'

        when:
        response.reset()
        request.method = 'PATCH'
        controller.method3()

        then:
        status == HttpServletResponse.SC_OK
        response.text == 'method 3'

        when:
        response.reset()
        request.method = 'GET'
        controller.method3()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED

        when:
        response.reset()
        request.method = 'DELETE'
        controller.method3()

        then:
        status == HttpServletResponse.SC_METHOD_NOT_ALLOWED
    }

    void 'test content type constants are added to the test'() {
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

    void 'test default request method'() {
        expect:
        request.method == 'GET'
    }
}
