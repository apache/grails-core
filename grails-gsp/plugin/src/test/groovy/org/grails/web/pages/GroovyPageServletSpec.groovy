package org.grails.web.pages

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.grails.core.io.SimpleMapResourceLoader
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.gsp.io.GroovyPageScriptSource
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.core.io.ByteArrayResource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletConfig
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.util.WebUtils
import spock.lang.Specification

/**
 *  Tests for the GSP servlet
 */
class GroovyPageServletSpec extends Specification{

    private SimpleMapResourceLoader resourceLoader

    void setup() {
        RequestContextHolder.resetRequestAttributes()
    }
    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void "Test create response writer method"() {
        when:"a response writer is created without a web request"
            def gps = new GroovyPagesServlet()
            def writer = gps.createResponseWriter(new MockHttpServletResponse())
        then:"An exception is thrown"
            thrown IllegalStateException

        when:"A response writer is created with a web request"
            RequestContextHolder.setRequestAttributes new GrailsWebRequest(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new MockServletContext())

            gps = new GroovyPagesServlet()
            writer = gps.createResponseWriter(new MockHttpServletResponse())

        then:"The writer is created correctly"
            writer != null
    }

    void "Test default 404 response"() {
        given:"An initialized gsp servlet"
            GroovyPagesServlet servlet = systemUnderTest()

        when:"A basic request is sent"
            def request = new MockHttpServletRequest()
            request.method = "GET"
            def response = new MockHttpServletResponse()
            servlet.service(request, response)
        then:"A 404 is returned"
            response.status == HttpServletResponse.SC_NOT_FOUND
    }

    void "Test a 404 is sent for forbidden GSP pages"() {
        given:"An initialized gsp servlet and a non public GSP"
            GroovyPagesServlet servlet = systemUnderTest()
            nonPublicGsgPage()

        when:"The page is queried in the script engine"
            def page = servlet.groovyPagesTemplateEngine.findScriptSource("/foo/nonPublic.gsp")

        then:"The page is found"
            page != null
            !page.isPublic()

        when:"A non public page is rendered"
            def request = new MockHttpServletRequest()
            request.method = "GET"
            request.servletPath = "/foo/nonPublic.gsp"
            def response = new MockHttpServletResponse()
            servlet.service(request, response)
        then:"A 404 is sent"
            response.status == HttpServletResponse.SC_NOT_FOUND
    }

    void "Test include is allowed for forbidden GSP pages"() {
        given:"An initialized gsp servlet and a non public GSP"
            GroovyPagesServlet servlet = systemUnderTest()
            nonPublicGsgPage()

        when:"The page is queried in the script engine"
            def page = servlet.groovyPagesTemplateEngine.findScriptSource("/foo/nonPublic.gsp")

        then:"The page is found"
            page != null
            !page.isPublic()

        when:"A non public page is rendered"
            def request = new MockHttpServletRequest()
            request.method = "GET"
            request.setAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE, "/foo/nonPublic.gsp")
            request.servletPath = "/foo/nonPublic.gsp"
            def response = new MockHttpServletResponse()
            servlet.service(request, response)
        then:"A 404 is sent"
            response.status == HttpServletResponse.SC_OK
            servlet.pageRendered.URI == page.URI
    }
    void "Test a publicly exposed page is rendered"() {
        given:"An initialized gsp servlet and a non public GSP"
            GroovyPagesServlet servlet = systemUnderTest()
            publicGsgPage()

        when:"The page is queried in the script engine"
            def page = servlet.groovyPagesTemplateEngine.findScriptSource("/foo/public.gsp")

        then:"The page is found"
            page != null
            page.isPublic()

        when:"A non public page is rendered"
            def request = new MockHttpServletRequest()
            request.method = "GET"
            request.servletPath = "/foo/public.gsp"
            def response = new MockHttpServletResponse()
            servlet.service(request, response)
        then:"A 404 is sent"
            response.status == HttpServletResponse.SC_OK
            servlet.pageRendered.URI == page.URI
    }

    protected def nonPublicGsgPage() {
        resourceLoader.resources['/WEB-INF/grails-app/views/foo/nonPublic.gsp'] = new ByteArrayResource("Hello".bytes) {
            @Override
            URL getURL() {
                new URL('http://localhost/WEB-INF/grails-app/views/foo/nonPublic.gsp')
            }
        }
    }

    protected def publicGsgPage() {
        resourceLoader.resources['/foo/public.gsp'] = new ByteArrayResource("Hello".bytes) {
            @Override
            URL getURL() {
                return new URL('http://localhost/foo/public.gsp')
            }
        }
    }

    GroovyPagesServlet systemUnderTest() {
        def appCtx = new GenericWebApplicationContext(new MockServletContext())
        appCtx.refresh()
        final engine = new GroovyPagesTemplateEngine()
        final locator = new GrailsConventionGroovyPageLocator()
        resourceLoader = new SimpleMapResourceLoader()
        locator.addResourceLoader(resourceLoader)
        engine.setGroovyPageLocator(locator)
        appCtx.beanFactory.registerSingleton("groovyPagesTemplateEngine", engine)

        def servlet = new GroovyPagesServlet() {
            GroovyPageScriptSource pageRendered
            @Override
            protected WebApplicationContext findWebApplicationContext() {
                return appCtx
            }

            @Override
            protected void renderPageWithEngine(GroovyPagesTemplateEngine e, HttpServletRequest request, HttpServletResponse response, GroovyPageScriptSource scriptSource) {
                pageRendered = scriptSource
            }
        }

        servlet.init(new MockServletConfig())
        return servlet
    }
}
