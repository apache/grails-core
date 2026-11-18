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
package org.grails.web.servlet.mvc

import grails.config.Config
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.spring.BeanBuilder
import grails.util.GrailsWebMockUtil
import grails.web.mime.MimeType
import org.grails.config.PropertySourcesConfig
import org.grails.core.lifecycle.ShutdownOperations
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.grails.web.mime.DefaultMimeUtility
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
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
        RequestContextHolder.resetRequestAttributes()
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

    void "an application context without a MimeUtility resolves the request format from the configured media types"() {
        given:
        def servletContext = new MockServletContext()
        def ctx = new GenericWebApplicationContext(servletContext)
        ctx.refresh()
        MockHttpServletRequest request = GrailsWebMockUtil.bindMockWebRequest(ctx).currentRequest
        request.contentType = 'application/json'

        expect:
        request.format == 'json'
        request.mimeTypes*.extension == ['json']
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

    void "Test Accept header is honored for non-XHR requests with a browser User-Agent"() {
        when: "The request has a browser User-Agent but no X-Requested-With header"
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            def response = webRequest.currentResponse
            request.contentType = "application/json"
            request.addHeader('Accept', 'text/json')
            request.addHeader('User-Agent', 'Webkit')

        then: "The Accept header is still used, so the response format is 'json'"
            request.getFormat() == "json"
            request.getFormat() == "json" // call twice to test cached value
            request.format == 'json'
            response.format == 'json'
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

    void "media type names in the Accept header are matched case-insensitively"() {
        when: "The Accept header names a configured media type in mixed case"
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            request.addHeader('Accept', 'Application/JSON')

        then:
            webRequest.currentResponse.format == 'json'
    }

    @Unroll
    void "a configured media type with capitals matches #header in the Accept and Content-Type headers"() {
        when:
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            def response = webRequest.currentResponse
            request.contentType = header
            request.addHeader('Accept', header)
            def result = response.withFormat {
                json { 'json' }
                acme { 'acme' }
            }

        then:
            request.format == 'acme'
            response.format == 'acme'
            result == 'acme'

        where:
            header << ['application/vnd.Acme+json', 'application/vnd.acme+json']
    }

    void "a Content-Type no configured media type matches is the request's type in lower case, as Spring reads it"() {
        when:
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            request.contentType = 'application/VND.API+json;charset=UTF-8'

        then: 'it equals the JSON API type a binding source creator is registered for'
            request.mimeTypes[0].name == 'application/vnd.api+json'
            request.mimeTypes[0] == MimeType.JSON_API
            request.mimeTypes[0].parameters.charset == 'UTF-8'
    }

    void "a bare * in the Accept header matches no configured media type"() {
        when:
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            request.addHeader('Accept', '*')

        then: "as in Grails 8, the whole configured list is the answer, not */* alone"
            webRequest.currentResponse.format == 'all'
            webRequest.currentResponse.mimeTypes.size() > 1
    }

    @Unroll
    void "with the strategy MimeTypesConfiguration defines, as in an application, an Accept header of #accept resolves to #format"() {
        given: "the configurer MimeTypesConfiguration defines over the application's settings, and MIME types of its own"
            final webRequest = boundMimeTypeRequest()
            def context = (GenericWebApplicationContext) webRequest.applicationContext
            def own = new DefaultMimeUtility((buildMimeTypes() + new MimeType('application/vnd.own+json', 'own')) as MimeType[])
            def configurer = Spy(MimeTypesConfiguration.grailsMimeTypesWebMvcConfigurer(
                    Stub(ObjectProvider) { getObject() >> own }, (GrailsApplication) application))
            context.beanFactory.registerSingleton('grailsMimeTypesWebMvcConfigurer', configurer)
            webRequest.currentRequest.addHeader('Accept', accept)

        when:
            String resolved = webRequest.currentResponse.format

        then: "its strategy resolves the format"
            (1.._) * configurer.getContentNegotiationStrategy()
            resolved == format

        where:
            accept                      || format
            'Application/JSON'          || 'json'
            'application/vnd.acme+json' || 'acme'
            '*'                         || 'all'
            'image/avif, image/webp'    || 'all'
            'application/vnd.own+json'  || 'own'
    }

    void "the format parameter overrides the Accept header"() {
        when:
            final webRequest = boundMimeTypeRequest()
            webRequest.currentRequest.addHeader('Accept', 'application/json')
            webRequest.params.format = 'xml'

        then:
            webRequest.currentResponse.format == 'xml'
    }

    void "the response format request attribute overrides the Accept header"() {
        when:
            final webRequest = boundMimeTypeRequest()
            webRequest.currentRequest.addHeader('Accept', 'application/json')
            webRequest.currentRequest.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT, 'xml')

        then:
            webRequest.currentResponse.format == 'xml'
    }

    void "an Accept header without a configured media type resolves to the all format"() {
        when: "The Accept header names only media types that are not configured"
            final webRequest = boundMimeTypeRequest()
            MockHttpServletRequest request = webRequest.currentRequest
            request.addHeader('Accept', 'image/avif, image/webp')

        then:
            webRequest.currentResponse.format == 'all'
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
            'got html'      | 'application/xml, text/html, */*'  | 'Trident'    | ''
            'got html'      | 'application/xml, text/html, */*'  | 'Trident'    | 'grails.mime.disable.accept.header.userAgents = []'
            'got json'      | 'application/xml, text/html, */*'  | 'Trident'    | 'grails.mime.disable.accept.header.userAgents = ["Trident"]'
            'got json'      | 'application/xml, text/html, */*'  | 'trident'    | 'grails.mime.disable.accept.header.userAgents = ["Trident", "Gecko"]'
            'got json'      | 'application/xml, text/html, */*'  | 'gecko'      | 'grails.mime.disable.accept.header.userAgents = ["Trident", "Gecko"]'
            'got json'      | 'application/xml, text/html, */*'  | 'MSIE 10.0'  | 'grails.mime.disable.accept.header.userAgents = ["MSIE \\\\d+"]'
            'got html'      | 'application/xml, text/html, */*'  | 'MSIE x'     | 'grails.mime.disable.accept.header.userAgents = ["MSIE \\\\d+"]'
            'got json'      | 'application/xml, text/html, */*'  | 'gecko'      | 'grails.mime.disable.accept.header.userAgents = "Trident,Gecko"'
            'got json'      | 'application/xml, text/html, */*'  | 'trident'    | 'grails.mime.disable.accept.header.userAgents = ["(?-i)Gecko", "Trident"]'
            'got html'      | 'application/xml, text/html, */*'  | 'gecko'      | 'grails.mime.disable.accept.header.userAgents = ["(?-i)Gecko", "Trident"]'
            'got json'      | 'application/xml, text/html, */*'  | 'Moozilla'   | 'grails.mime.disable.accept.header.userAgents = ["(\\\\w)\\\\1"]'
            'got html'      | 'application/xml, text/html, */*'  | 'Mozilla'    | 'grails.mime.disable.accept.header.userAgents = ["", "Trident"]'
            'got html'      | 'application/xml, text/html, */*'  | 'Mozilla'    | 'grails.mime.disable.accept.header.userAgents = "Trident,,"'
//            'got html'      | 'application/xml, text/html, */*'  | 'Trident'    | 'grails.mime.disable.accept.header.userAgents = null' // TODO: can no longer detect if something is set to null using new Config API, investigate..
    }

    void "a Pattern the setting lists is used as it is, with its own case"() {
        given: 'a case-sensitive pattern among the user agents, and a user agent it does not match in that case'
            def config = getTestConfig()
            config.merge(new ConfigSlurper().parse(
                    'grails.mime.disable.accept.header.userAgents = [java.util.regex.Pattern.compile("Gecko")]'))
            application.setConfig(config)
            final webRequest = boundMimeTypeRequest()
            webRequest.currentRequest.addHeader('Accept', 'application/xml')
            webRequest.currentRequest.addHeader('User-Agent', 'gecko-bot')

        expect: 'the Accept header is honoured'
            webRequest.currentResponse.withFormat {
                json { 'got json' }
                xml { 'got xml' }
            } == 'got xml'
    }

    void "a user agent the setting lists that quotes what follows it applies to itself alone"() {
        given: 'an entry that would quote the rest of a pattern it were joined into'
            def config = getTestConfig()
            config.merge(new ConfigSlurper().parse('grails.mime.disable.accept.header.userAgents = ["\\\\QMozilla", "Trident"]'))
            application.setConfig(config)
            final webRequest = boundMimeTypeRequest()
            webRequest.currentRequest.addHeader('Accept', 'application/xml, text/html, */*')
            webRequest.currentRequest.addHeader('User-Agent', 'Trident/7.0')

        expect: 'the configurer is created, and the other entry is found'
            MimeTypesConfiguration.grailsMimeTypesWebMvcConfigurer(Stub(ObjectProvider), (GrailsApplication) application)
            webRequest.currentResponse.withFormat {
                json { 'got json' }
                html { 'got html' }
            } == 'got json'
    }

    void "a user agent the setting lists that is not a regular expression is reported as the application starts"() {
        given:
            def config = getTestConfig()
            config.merge(new ConfigSlurper().parse('grails.mime.disable.accept.header.userAgents = ["Trident", "*bot*"]'))
            application.setConfig(config)

        when: 'the configurer an application has is created'
            MimeTypesConfiguration.grailsMimeTypesWebMvcConfigurer(Stub(ObjectProvider), (GrailsApplication) application)

        then:
            IllegalArgumentException e = thrown()
            e.message.startsWith('grails.mime.disable.accept.header.userAgents lists [*bot*], which is not a regular expression:')

        when: 'a request is answered without the configurer'
            final webRequest = boundMimeTypeRequest()
            webRequest.currentRequest.addHeader('Accept', 'application/json')
            webRequest.currentResponse.format

        then:
            e = thrown()
            e.message.startsWith('grails.mime.disable.accept.header.userAgents lists [*bot*], which is not a regular expression:')
    }

    

    @Unroll
    void "withFormat follows the Accept header of an XHR request from #userAgent unless #additionalConfig"() {
        setup:
            def config = getTestConfig()
            config.merge(new ConfigSlurper().parse('grails.mime.disable.accept.header.userAgents = ["Trident"]\n' + additionalConfig))
            application.setConfig(config)
            final webRequest = boundMimeTypeRequest()
            def request = webRequest.currentRequest
            request.addHeader('Accept', 'application/xml, text/html, */*')
            request.addHeader('User-Agent', userAgent)
            request.addHeader('X-Requested-With', 'XMLHttpRequest')
            def responseResult = webRequest.currentResponse.withFormat {
                json { 'got json' }
                html { 'got html' }
            }

        expect:
            responseResult == formatResponse

        where:
            formatResponse | userAgent | additionalConfig
            'got html'     | 'Trident' | ''
            'got json'     | 'Trident' | 'grails.mime.disable.accept.header.userAgentsXhr = true'
            'got json'     | 'Mozilla' | 'grails.mime.use.accept.header = false'
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
                      acme: 'application/vnd.Acme+json',
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
