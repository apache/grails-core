package org.grails.web.servlet.mvc

import grails.config.Config
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.spring.BeanBuilder
import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import org.grails.config.PropertySourcesConfig
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.grails.web.mime.DefaultMimeUtility
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for mime type resolution
 */
class RequestAndResponseMimeTypesApiSpec extends Specification{
    def responseMimeTypesApiInstance
    def application

    void setupSpec() {
        // ensure clean state
        ShutdownOperations.runOperations()
    }

    void setup() {
        application = new DefaultGrailsApplication()
        application.config = testConfig
    }
    
    void cleanup() {
        ShutdownOperations.runOperations()
    }

    void "Test format property is valid for CONTENT_TYPE header only"() {
        when: "The request CONTENT_TYPE header is 'text/xml'"
            final webRequest = boundMimeTypeRequest()
            def request = webRequest.currentRequest
            def response = webRequest.currentResponse
            request.contentType = "text/xml"

        then: "The request format should be 'xml'"
            request.getFormat() == "xml"
            request.getFormat() == "xml" // call twice to test cached value
            request.format == 'xml'
            response.format == 'all'
    }

    private GrailsWebRequest boundMimeTypeRequest() {
        def servletContext = new MockServletContext()
        def ctx = new GenericWebApplicationContext(servletContext)
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, ctx)
        ctx.beanFactory.registerSingleton("mimeUtility", new DefaultMimeUtility(buildMimeTypes()))
        ctx.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, application)
        ctx.refresh()
        GrailsWebMockUtil.bindMockWebRequest(ctx)
    }

    void "Test format property is valid for CONTENT_TYPE and Accept header"() {
        when: "The request CONTENT_TYPE header is 'text/xml'"
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            def response = webRequest.currentResponse
            request.contentType = "text/xml"
            request.addHeader('Accept', 'text/json')

        then: "The request format should be 'xml'"
            request.getFormat() == "xml"
            request.getFormat() == "xml" // call twice to test cached value
            request.format == 'xml'
            response.format == 'json'
    }

    void "Test format property is valid for XHR and Accept header with User-Agent"() {
        when: "The request CONTENT_TYPE header is 'text/xml'"
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            def response = webRequest.currentResponse
            request.contentType = "application/json"
            request.addHeader('Accept', 'text/json')
            request.addHeader('X-Requested-With', 'XMLHttpRequest')
            request.addHeader('User-Agent', 'Webkit')

        then: "The request format should be 'json'"
            request.getFormat() == "json"
            request.getFormat() == "json" // call twice to test cached value
            request.format == 'json'
            response.format == 'json'
    }

    void "Test format property is ignored for non-XHR and Accept header with User-Agent"() {
        when: "The request CONTENT_TYPE header is 'text/xml'"
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            def response = webRequest.currentResponse
            request.contentType = "application/json"
            request.addHeader('Accept', 'text/json')
            request.addHeader('User-Agent', 'Webkit')

        then: "The request format should be 'json'"
            request.getFormat() == "json"
            request.getFormat() == "json" // call twice to test cached value
            request.format == 'json'
            response.format == 'all'
    }

   void "Test format property is valid for Accept header only"() {
        when: "The request CONTENT_TYPE header is 'text/xml'"
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            def response = webRequest.currentResponse
            request.addHeader('Accept', 'text/json')

        then: "The request format should be 'xml'"
            request.getFormat() == "all"
            request.getFormat() == "all" // call twice to test cached value
            request.format == 'all'
            response.format == 'json'
    }

    void "Test withFormat method with CONTENT_TYPE header only"() {
        when: "The request CONTENT_TYPE header is 'text/xml' and withFormat is used"
            final webRequest = boundMimeTypeRequest()
            def request = webRequest.currentRequest
            def response = webRequest.currentResponse

            request.contentType = "text/xml"
            def requestResult = request.withFormat {
                html { "got html"}
                xml { "got xml"}
            }

            def responseResult = response.withFormat {
                html { 'got html' }
                xml { 'got xml' }
            }

        then: 'The xml closure is invoked'
            requestResult == 'got xml'
            responseResult == 'got html'

    }

    void "Test withFormat method with Accept header only"() {
        when: "The request Accept header is 'text/xml' and withFormat is used"
            def webRequest = boundMimeTypeRequest()
            def request = webRequest.currentRequest
            def response = webRequest.currentResponse

            request.addHeader('Accept', "text/xml")

            def requestResult = request.withFormat {
                html { "got html"}
                xml { "got xml"}

            }

            def responseResult = response.withFormat {
                html { 'got html' }
                xml { 'got xml' }
            }

        then: 'The xml closure is invoked'
            requestResult == 'got html'
            responseResult == 'got xml'

        when:"The Accept header is JSON and there is a catch-all"
            webRequest = boundMimeTypeRequest()
            request = webRequest.currentRequest
            response = webRequest.currentResponse
            request.addHeader('Accept', "application/json")
            responseResult = response.withFormat {
                html { 'got html' }
                xml { 'got xml' }
                '*' { 'got everything' }
            }
        then: 'The * closure is invoked'
            responseResult == 'got everything'

    }
    
    @Issue("GRAILS-10973")
    void "request.withFormat should choose wildcard choice when format == all"() {
        when:
            final webRequest = boundMimeTypeRequest()
            def request = webRequest.currentRequest
            def response = webRequest.currentResponse
            def requestResult = request.withFormat {
                    html { 'got html' }
                    xml { 'got xml' }
                    '*' { 'got everything' }
                }
        then: 'format is all'
            request.format == 'all'
        then: 'The * closure is invoked'
            requestResult == 'got everything'
    }

    void "Test withFormat returns first block if no format provided"() {
        when: "No Accept header, URI extension or format param"
        final webRequest = boundMimeTypeRequest()
        def request = webRequest.currentRequest
        def response = webRequest.currentResponse

        def responseResult = response.withFormat {
            json { 'got json' }
            xml { 'got xml' }
        }

        then: "The first withFormat block should be returned"
        responseResult == 'got json'
    }

    void "Test withFormat method when Accept header contains the all (*/*) and non-matching formats"() {
        setup: "The request Acept header is 'application/xml, text/csv, */*' and withFormat is used"
            final webRequest = boundMimeTypeRequest()
            def request = webRequest.currentRequest
            def response = webRequest.currentResponse
            request.addHeader('Accept', acceptHeader)
            def responseResult = response.withFormat {
                json { 'got json' }
                text { 'got text' }
                html { 'got html' }
            }

        expect:
            formatResponse == responseResult

        where:
            formatResponse  | acceptHeader
            null            | 'application/xml, text/csv'
            'got html'      | 'application/xml, text/html, */*'
            'got json'      | 'application/xml, */*, text/html'
            'got json'      | 'application/xml, text/csv, */*'

    }

    @Unroll
    void "Test withFormat method when Accept header and User-Agent #userAgent #additionalConfig #acceptHeader"() {
        setup:
            def config = getTestConfig()
            if(additionalConfig) {
                config.merge(new ConfigSlurper().parse(String.valueOf(additionalConfig)))
            }
            application.setConfig(config)
            final webRequest = boundMimeTypeRequest()
            def request = webRequest.currentRequest
            def response = webRequest.currentResponse
            request.addHeader('Accept', acceptHeader)
            request.addHeader('User-Agent', userAgent)
            def responseResult = response.withFormat {
                json { 'got json' }
                text { 'got text' }
                html { 'got html' }
            }

        expect:
            formatResponse == responseResult

        where:
            formatResponse  | acceptHeader                       | userAgent    | additionalConfig
            null            | 'application/xml, text/csv'        | 'Mozilla'    | ''
            'got html'      | 'application/xml, text/html, */*'  | 'Mozilla'    | ''
            'got json'      | 'application/xml, */*, text/html'  | 'Mozilla'    | ''
            'got json'      | 'application/xml, text/csv, */*'   | 'Mozilla'    | ''
            'got json'      | 'application/xml, text/html, */*'  | 'Trident'    | ''
            'got html'      | 'application/xml, text/html, */*'  | 'Trident'    | 'grails.mime.disable.accept.header.userAgents = []'
//            'got html'      | 'application/xml, text/html, */*'  | 'Trident'    | 'grails.mime.disable.accept.header.userAgents = null' // TODO: can no longer detect if something is set to null using new Config API, investigate..
    }

    

    private MimeType[] buildMimeTypes() {
        final def mainContext = new GenericApplicationContext()
        mainContext.refresh()
        application.setApplicationContext(mainContext)

        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = application
            mimeConfiguration(MimeTypesConfiguration, application, [])
        }
        final ApplicationContext context = bb.createApplicationContext()
        final MimeTypesConfiguration mimeTypesConfiguration = context.getBean(MimeTypesConfiguration)
        mimeTypesConfiguration.mimeTypes()
    }

    String applicationConfigText = '''
grails.mime.file.extensions = true // enables the parsing of file extensions from URLs into the request format
grails.mime.use.accept.header = true
grails.mime.types = [ 
                      all: '*/*',
                      html: ['text/html','application/xhtml+xml'],
                      xml: ['text/xml', 'application/xml'],
                      text: 'text/plain',
                      js: 'text/javascript',
                      rss: 'application/rss+xml',
                      atom: 'application/atom+xml',
                      css: 'text/css',
                      csv: 'text/csv',
                      json: ['application/json','text/json'],
                      form: 'application/x-www-form-urlencoded',
                      multipartForm: 'multipart/form-data'
                    ]
'''

    private Config getTestConfig() {
        def s = new ConfigSlurper()
        def config = s.parse(String.valueOf(applicationConfigText))

        def propertySources = new MutablePropertySources()
        propertySources.addLast(new MapPropertySource("grails", config))


        return new PropertySourcesConfig(propertySources)
    }
}
