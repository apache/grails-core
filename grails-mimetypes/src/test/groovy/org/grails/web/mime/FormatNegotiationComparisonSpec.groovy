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
package org.grails.web.mime

import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.EnableWebMvc

import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.mapping.UrlMappingInfo
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.web.mime.MimeTypesConfiguration
import org.grails.web.servlet.mvc.GrailsWebRequest
import spock.lang.Shared
import spock.lang.Specification

/**
 * Runs a matrix of requests a URL mapping handles through Grails' negotiation and through Spring MVC's, which
 * {@code grails.mime.negotiation.spring} selects, under several configurations, and records every answer that differs:
 * the format {@code response.format} resolves, the block {@code withFormat} runs, the format {@code request.format}
 * resolves and the media types Spring MVC's content negotiation manager resolves for what it writes for the request.
 * The record is the evidence the upgrade guide summarizes, and a change to either policy that changes an answer fails it.
 */
class FormatNegotiationComparisonSpec extends Specification {

    private static final String CHROME = 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8'

    private static final String BROWSER = 'Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0'

    /** The configurations the requests are negotiated under. */
    @Shared
    Map<String, Map<String, Object>> configurations = [
            'defaults'                : [:],
            'merged vendor type'      : [(Settings.MIME_TYPES_MERGE_DEFAULTS): true, (Settings.MIME_TYPES): [
                    all: '*/*',
                    vnd: 'application/vnd.app1.v1+json'
            ]],
            'without all'             : [(Settings.MIME_TYPES): [
                    html: ['text/html', 'application/xhtml+xml'],
                    json: 'application/json',
                    xml : ['text/xml', 'application/xml']
            ]],
            'browser user agents'     : [(Settings.MIME_DISABLE_ACCEPT_HEADER_FOR_USER_AGENTS): ['Gecko', 'WebKit', 'Presto', 'Trident']],
            'use.accept.header: false': [(Settings.MIME_USE_ACCEPT_HEADER): false]
    ]

    /**
     * The requests the configurations that change how the {@code Accept} header is read are negotiated with, which
     * negotiate any other request as the defaults do.
     */
    @Shared
    Map<String, List<Map<String, Object>>> acceptHeaderRequests = [
            'browser user agents'     : [
                    [accept: 'application/json'],
                    [accept: 'application/json', userAgent: BROWSER],
                    [accept: 'application/json', userAgent: BROWSER, xhr: true],
                    [accept: CHROME, userAgent: BROWSER],
                    [accept: 'application/json', userAgent: BROWSER, format: 'xml']
            ],
            'use.accept.header: false': [
                    [accept: 'application/json'],
                    [accept: CHROME],
                    [accept: 'application/json;q=abc'],
                    [accept: 'application/json', format: 'xml']
            ]
    ]

    /** The requests, each with the headers and the format it is sent with. */
    @Shared
    List<Map<String, Object>> requests = [
            [:],
            [accept: '*/*'],
            [accept: 'text/html'],
            [accept: CHROME],
            [accept: 'application/json'],
            [accept: 'Application/JSON'],
            [accept: 'application/json;q=0.5, application/xml'],
            [accept: '*/*, application/json'],
            [accept: 'application/json, */*'],
            [accept: 'application/xml, */*, text/html'],
            [accept: 'text/*'],
            [accept: 'application/*'],
            [accept: 'application/problem+json'],
            [accept: 'application/problem+xml'],
            [accept: 'application/hal+json'],
            [accept: 'application/vnd.api+json'],
            [accept: 'application/soap+xml'],
            [accept: 'application/xml, application/atom+xml'],
            [accept: 'text/xml;q=0.5, application/xml;q=0.9, application/json;q=0.7'],
            [accept: 'application/json;q=0, */*;q=0.1'],
            [accept: 'image/png'],
            [accept: 'application/json;q=abc'],
            [accept: '*'],
            [accept: 'application/json', userAgent: BROWSER],
            [accept: 'application/json', userAgent: BROWSER, xhr: true],
            [accept: CHROME, userAgent: BROWSER],
            [accept: 'application/json', format: 'xml'],
            [accept: 'application/json', format: 'html'],
            [accept: 'application/json', format: 'all'],
            [accept: 'application/json', format: 'unknown'],
            [accept: 'text/html', format: 'json'],
            [contentType: 'application/json;charset=UTF-8'],
            [contentType: 'text/xml'],
            [contentType: 'application/vnd.api+json'],
            [contentType: 'application/problem+json'],
            [contentType: 'application/json, application/xml'],
            [contentType: 'application/json;charset='],
            [contentType: 'image/png']
    ]

    /** Every answer that differs, as {@code configuration | request | answer: Grails rules -> Spring MVC's}. */
    private static final List<String> RECORDED = [
            'defaults | Accept: */*, application/json | response.format: all -> json',
            'defaults | Accept: */*, application/json | withFormat(html, json, xml): html -> json',
            'defaults | Accept: */*, application/json | withFormat(json, xml, *): * -> json',
            'defaults | Accept: application/xml, */*, text/html | withFormat(pdf, html): pdf -> html',
            'defaults | Accept: text/* | withFormat(pdf, html): pdf -> html',
            'defaults | Accept: application/vnd.api+json | response.format: all -> json',
            'defaults | Accept: application/vnd.api+json | withFormat(html, json, xml): html -> json',
            'defaults | Accept: application/vnd.api+json | withFormat(json, xml, *): * -> json',
            'defaults | Accept: application/vnd.api+json | withFormat(pdf, html): pdf -> null',
            'defaults | Accept: application/soap+xml | response.format: all -> xml',
            'defaults | Accept: application/soap+xml | withFormat(html, json, xml): html -> xml',
            'defaults | Accept: application/soap+xml | withFormat(json, xml, *): * -> xml',
            'defaults | Accept: application/soap+xml | withFormat(pdf, html): pdf -> null',
            'defaults | Accept: application/xml, application/atom+xml | Spring MVC: [application/xml, application/atom+xml] -> [application/atom+xml, application/xml]',
            'defaults | Accept: image/png | response.format: all -> null',
            'defaults | Accept: image/png | withFormat(html, json, xml): html -> null',
            'defaults | Accept: image/png | withFormat(pdf, html): pdf -> null',
            'defaults | Accept: application/json;q=abc | response.format: json -> null',
            'defaults | Accept: application/json;q=abc | withFormat(html, json, xml): json -> null',
            'defaults | Accept: application/json;q=abc | withFormat(json, xml, *): json -> *',
            'defaults | Accept: application/json, format=xml | Spring MVC: [application/json] -> [text/xml, application/xml, application/*+xml]',
            'defaults | Accept: application/json, format=html | Spring MVC: [application/json] -> [text/html, application/xhtml+xml]',
            'defaults | Accept: application/json, format=all | response.format: all -> json',
            'defaults | Accept: application/json, format=all | withFormat(html, json, xml): html -> json',
            'defaults | Accept: application/json, format=all | withFormat(json, xml, *): * -> json',
            'defaults | Accept: application/json, format=all | withFormat(pdf, html): pdf -> null',
            'defaults | Accept: application/json, format=unknown | response.format: all -> null',
            'defaults | Accept: application/json, format=unknown | withFormat(html, json, xml): html -> json',
            'defaults | Accept: application/json, format=unknown | withFormat(json, xml, *): * -> json',
            'defaults | Accept: application/json, format=unknown | withFormat(pdf, html): pdf -> null',
            'defaults | Accept: application/json, format=unknown | Spring MVC: [application/json] -> not acceptable',
            'defaults | Accept: text/html, format=json | Spring MVC: [text/html] -> [application/json, text/json, application/*+json]',
            'defaults | Content-Type: application/vnd.api+json | request.format: null -> json',
            'defaults | Content-Type: application/json, application/xml | request.format: json -> null',
            'defaults | Content-Type: application/json;charset= | request.format: json -> null',
            'merged vendor type | Accept: */*, application/json | response.format: all -> json',
            'merged vendor type | Accept: */*, application/json | withFormat(html, json, xml): html -> json',
            'merged vendor type | Accept: */*, application/json | withFormat(json, xml, *): * -> json',
            'merged vendor type | Accept: application/xml, */*, text/html | withFormat(pdf, html): pdf -> html',
            'merged vendor type | Accept: text/* | withFormat(pdf, html): pdf -> html',
            'merged vendor type | Accept: application/vnd.api+json | response.format: all -> json',
            'merged vendor type | Accept: application/vnd.api+json | withFormat(html, json, xml): html -> json',
            'merged vendor type | Accept: application/vnd.api+json | withFormat(json, xml, *): * -> json',
            'merged vendor type | Accept: application/vnd.api+json | withFormat(pdf, html): pdf -> null',
            'merged vendor type | Accept: application/soap+xml | response.format: all -> xml',
            'merged vendor type | Accept: application/soap+xml | withFormat(html, json, xml): html -> xml',
            'merged vendor type | Accept: application/soap+xml | withFormat(json, xml, *): * -> xml',
            'merged vendor type | Accept: application/soap+xml | withFormat(pdf, html): pdf -> null',
            'merged vendor type | Accept: application/xml, application/atom+xml | Spring MVC: [application/xml, application/atom+xml] -> [application/atom+xml, application/xml]',
            'merged vendor type | Accept: image/png | response.format: all -> null',
            'merged vendor type | Accept: image/png | withFormat(html, json, xml): html -> null',
            'merged vendor type | Accept: image/png | withFormat(pdf, html): pdf -> null',
            'merged vendor type | Accept: application/json;q=abc | response.format: json -> null',
            'merged vendor type | Accept: application/json;q=abc | withFormat(html, json, xml): json -> null',
            'merged vendor type | Accept: application/json;q=abc | withFormat(json, xml, *): json -> *',
            'merged vendor type | Accept: application/json, format=xml | Spring MVC: [application/json] -> [text/xml, application/xml, application/*+xml]',
            'merged vendor type | Accept: application/json, format=html | Spring MVC: [application/json] -> [text/html, application/xhtml+xml]',
            'merged vendor type | Accept: application/json, format=all | response.format: all -> json',
            'merged vendor type | Accept: application/json, format=all | withFormat(html, json, xml): html -> json',
            'merged vendor type | Accept: application/json, format=all | withFormat(json, xml, *): * -> json',
            'merged vendor type | Accept: application/json, format=all | withFormat(pdf, html): pdf -> null',
            'merged vendor type | Accept: application/json, format=unknown | response.format: all -> null',
            'merged vendor type | Accept: application/json, format=unknown | withFormat(html, json, xml): html -> json',
            'merged vendor type | Accept: application/json, format=unknown | withFormat(json, xml, *): * -> json',
            'merged vendor type | Accept: application/json, format=unknown | withFormat(pdf, html): pdf -> null',
            'merged vendor type | Accept: application/json, format=unknown | Spring MVC: [application/json] -> not acceptable',
            'merged vendor type | Accept: text/html, format=json | Spring MVC: [text/html] -> [application/json, text/json, application/*+json]',
            'merged vendor type | Content-Type: application/vnd.api+json | request.format: null -> json',
            'merged vendor type | Content-Type: application/json, application/xml | request.format: json -> null',
            'merged vendor type | Content-Type: application/json;charset= | request.format: json -> null',
            'without all | no headers | response.format: html -> all',
            'without all | no headers | withFormat(json, xml, *): json -> *',
            'without all | no headers | withFormat(pdf, html): html -> pdf',
            'without all | Accept: */*, application/json | withFormat(pdf, html): null -> pdf',
            'without all | Accept: application/json, */* | withFormat(pdf, html): null -> pdf',
            'without all | Accept: text/* | withFormat(pdf, html): pdf -> html',
            'without all | Accept: application/* | withFormat(pdf, html): pdf -> html',
            'without all | Accept: application/hal+json | response.format: all -> json',
            'without all | Accept: application/hal+json | withFormat(html, json, xml): html -> json',
            'without all | Accept: application/hal+json | withFormat(json, xml, *): * -> json',
            'without all | Accept: application/hal+json | withFormat(pdf, html): pdf -> null',
            'without all | Accept: application/vnd.api+json | response.format: all -> json',
            'without all | Accept: application/vnd.api+json | withFormat(html, json, xml): html -> json',
            'without all | Accept: application/vnd.api+json | withFormat(json, xml, *): * -> json',
            'without all | Accept: application/vnd.api+json | withFormat(pdf, html): pdf -> null',
            'without all | Accept: application/soap+xml | response.format: all -> xml',
            'without all | Accept: application/soap+xml | withFormat(html, json, xml): html -> xml',
            'without all | Accept: application/soap+xml | withFormat(json, xml, *): * -> xml',
            'without all | Accept: application/soap+xml | withFormat(pdf, html): pdf -> null',
            'without all | Accept: application/xml, application/atom+xml | Spring MVC: [application/xml, application/atom+xml] -> [application/atom+xml, application/xml]',
            'without all | Accept: application/json;q=0, */*;q=0.1 | response.format: json -> all',
            'without all | Accept: application/json;q=0, */*;q=0.1 | withFormat(html, json, xml): json -> html',
            'without all | Accept: application/json;q=0, */*;q=0.1 | withFormat(json, xml, *): json -> *',
            'without all | Accept: application/json;q=0, */*;q=0.1 | withFormat(pdf, html): null -> pdf',
            'without all | Accept: image/png | response.format: all -> null',
            'without all | Accept: image/png | withFormat(html, json, xml): html -> null',
            'without all | Accept: image/png | withFormat(pdf, html): pdf -> null',
            'without all | Accept: application/json;q=abc | response.format: json -> null',
            'without all | Accept: application/json;q=abc | withFormat(html, json, xml): json -> null',
            'without all | Accept: application/json;q=abc | withFormat(json, xml, *): json -> *',
            'without all | Accept: application/json, format=xml | Spring MVC: [application/json] -> [text/xml, application/xml, application/*+xml]',
            'without all | Accept: application/json, format=html | Spring MVC: [application/json] -> [text/html, application/xhtml+xml]',
            'without all | Accept: application/json, format=all | response.format: html -> json',
            'without all | Accept: application/json, format=all | withFormat(html, json, xml): html -> json',
            'without all | Accept: application/json, format=all | withFormat(pdf, html): html -> null',
            'without all | Accept: application/json, format=unknown | response.format: html -> null',
            'without all | Accept: application/json, format=unknown | withFormat(html, json, xml): html -> json',
            'without all | Accept: application/json, format=unknown | withFormat(pdf, html): html -> null',
            'without all | Accept: application/json, format=unknown | Spring MVC: [application/json] -> not acceptable',
            'without all | Accept: text/html, format=json | Spring MVC: [text/html] -> [application/json, application/*+json]',
            'without all | Content-Type: application/json;charset=UTF-8 | response.format: html -> all',
            'without all | Content-Type: application/json;charset=UTF-8 | withFormat(json, xml, *): json -> *',
            'without all | Content-Type: application/json;charset=UTF-8 | withFormat(pdf, html): html -> pdf',
            'without all | Content-Type: text/xml | response.format: html -> all',
            'without all | Content-Type: text/xml | withFormat(json, xml, *): json -> *',
            'without all | Content-Type: text/xml | withFormat(pdf, html): html -> pdf',
            'without all | Content-Type: application/vnd.api+json | response.format: html -> all',
            'without all | Content-Type: application/vnd.api+json | withFormat(json, xml, *): json -> *',
            'without all | Content-Type: application/vnd.api+json | withFormat(pdf, html): html -> pdf',
            'without all | Content-Type: application/vnd.api+json | request.format: null -> json',
            'without all | Content-Type: application/problem+json | response.format: html -> all',
            'without all | Content-Type: application/problem+json | withFormat(json, xml, *): json -> *',
            'without all | Content-Type: application/problem+json | withFormat(pdf, html): html -> pdf',
            'without all | Content-Type: application/json, application/xml | response.format: html -> all',
            'without all | Content-Type: application/json, application/xml | withFormat(json, xml, *): json -> *',
            'without all | Content-Type: application/json, application/xml | withFormat(pdf, html): html -> pdf',
            'without all | Content-Type: application/json, application/xml | request.format: json -> null',
            'without all | Content-Type: application/json;charset= | response.format: html -> all',
            'without all | Content-Type: application/json;charset= | withFormat(json, xml, *): json -> *',
            'without all | Content-Type: application/json;charset= | withFormat(pdf, html): html -> pdf',
            'without all | Content-Type: application/json;charset= | request.format: json -> null',
            'without all | Content-Type: image/png | response.format: html -> all',
            'without all | Content-Type: image/png | withFormat(json, xml, *): json -> *',
            'without all | Content-Type: image/png | withFormat(pdf, html): html -> pdf',
            'browser user agents | Accept: application/json, browser User-Agent | Spring MVC: [application/json] -> [*/*]',
            'browser user agents | Accept: <Chrome>, browser User-Agent | Spring MVC: [text/html, application/xhtml+xml, image/avif, image/webp, application/xml;q=0.9, */*;q=0.8] -> [*/*]',
            'browser user agents | Accept: application/json, browser User-Agent, format=xml | Spring MVC: [application/json] -> [text/xml, application/xml, application/*+xml]',
            'use.accept.header: false | Accept: application/json | Spring MVC: [application/json] -> [*/*]',
            'use.accept.header: false | Accept: <Chrome> | Spring MVC: [text/html, application/xhtml+xml, image/avif, image/webp, application/xml;q=0.9, */*;q=0.8] -> [*/*]',
            'use.accept.header: false | Accept: application/json;q=abc | Spring MVC: not acceptable -> [*/*]',
            'use.accept.header: false | Accept: application/json, format=xml | Spring MVC: [application/json] -> [text/xml, application/xml, application/*+xml]'
    ]

    @Shared
    Map<String, AnnotationConfigWebApplicationContext> contexts = [:]

    void cleanupSpec() {
        contexts.values()*.close()
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'every answer that differs between the two policies is recorded'() {
        when:
        List<String> differences = []
        configurations.each { String configuration, Map<String, Object> properties ->
            for (Map<String, Object> sent in requestsFor(configuration)) {
                Map<String, Object> grails = answers(configuration, properties, sent, false)
                Map<String, Object> spring = answers(configuration, properties, sent, true)
                grails.each { String answer, Object value ->
                    if (value != spring[answer]) {
                        differences << "${configuration} | ${describe(sent)} | ${answer}: ${value} -> ${spring[answer]}".toString()
                    }
                }
            }
        }

        then:
        differences == RECORDED
    }

    void "with the setting, the first type Spring MVC's manager resolves for a request a URL mapping handles is of the format Grails resolves"() {
        when: 'the format of the first media type the manager resolves for each request of the matrix is not the one response.format resolves'
        List<String> diverging = []
        configurations.each { String configuration, Map<String, Object> properties ->
            requestsFor(configuration).findAll { !it.contentType }.each { Map<String, Object> sent ->
                Map<String, Object> spring = answers(configuration, properties, sent, true)
                Object format = spring['Spring MVC'] == 'not acceptable' ? null : formatOf(configuration, properties, sent)
                if (spring['response.format'] != format) {
                    diverging << "${configuration} | ${describe(sent)} | ${spring['response.format']} -> ${format}".toString()
                }
            }
        }

        then:
        diverging.isEmpty()
    }

    private Map<String, Object> answers(String configuration, Map<String, Object> properties, Map<String, Object> sent,
                                        boolean springNegotiation) {
        Map<String, Object> answers = [:]
        answers['response.format'] = respond(configuration, properties, sent, springNegotiation) { GrailsWebRequest webRequest ->
            webRequest.currentResponse.format
        }
        answers['withFormat(html, json, xml)'] = respond(configuration, properties, sent, springNegotiation) { GrailsWebRequest webRequest ->
            webRequest.currentResponse.withFormat {
                html { 'html' }
                json { 'json' }
                xml { 'xml' }
            }
        }
        answers['withFormat(json, xml, *)'] = respond(configuration, properties, sent, springNegotiation) { GrailsWebRequest webRequest ->
            webRequest.currentResponse.withFormat {
                json { 'json' }
                xml { 'xml' }
                '*' { '*' }
            }
        }
        answers['withFormat(pdf, html)'] = respond(configuration, properties, sent, springNegotiation) { GrailsWebRequest webRequest ->
            webRequest.currentResponse.withFormat {
                pdf { 'pdf' }
                html { 'html' }
            }
        }
        if (sent.contentType) {
            answers['request.format'] = respond(configuration, properties, sent, springNegotiation) { GrailsWebRequest webRequest ->
                webRequest.currentRequest.format
            }
        }
        answers['Spring MVC'] = respond(configuration, properties, sent, springNegotiation) { GrailsWebRequest webRequest ->
            try {
                manager(configuration, properties, springNegotiation).resolveMediaTypes(new ServletWebRequest(webRequest.currentRequest)).toString()
            }
            catch (HttpMediaTypeNotAcceptableException ignored) {
                'not acceptable'
            }
        }
        answers
    }

    /**
     * The format of the first media type Spring MVC's manager resolves for a request, as Grails resolves an
     * {@code Accept} header of that type alone under the MIME types of the configuration.
     */
    private Object formatOf(String configuration, Map<String, Object> properties, Map<String, Object> sent) {
        Object first = respond(configuration, properties, sent, true) { GrailsWebRequest webRequest ->
            manager(configuration, properties, true).resolveMediaTypes(new ServletWebRequest(webRequest.currentRequest)).first()
        }
        Map<String, Object> types = properties.findAll { String key, Object value -> key.startsWith('grails.mime.types') || key == Settings.MIME_TYPES_MERGE_DEFAULTS }
        respond("${configuration} types".toString(), types, [accept: first.toString()], true) { GrailsWebRequest webRequest ->
            webRequest.currentResponse.format
        }
    }

    private List<Map<String, Object>> requestsFor(String configuration) {
        acceptHeaderRequests[configuration] ?: requests
    }

    private Object respond(String configuration, Map<String, Object> properties, Map<String, Object> sent,
                           boolean springNegotiation, Closure<Object> answer) {
        AnnotationConfigWebApplicationContext context = context(configuration, properties, springNegotiation)
        def request = new MockHttpServletRequest(context.servletContext)
        if (sent.accept) {
            request.addHeader('Accept', sent.accept)
        }
        if (sent.userAgent) {
            request.addHeader('User-Agent', sent.userAgent)
        }
        if (sent.xhr) {
            request.addHeader('X-Requested-With', 'XMLHttpRequest')
        }
        if (sent.contentType) {
            request.contentType = sent.contentType
        }
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, Stub(UrlMappingInfo))
        GrailsWebRequest webRequest = GrailsWebMockUtil.bindMockWebRequest(context, request, new MockHttpServletResponse())
        if (sent.format) {
            webRequest.params.format = sent.format
        }
        try {
            return answer.call(webRequest)
        }
        finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    private ContentNegotiationManager manager(String configuration, Map<String, Object> properties, boolean springNegotiation) {
        context(configuration, properties, springNegotiation)
                .getBean(GrailsContentNegotiationStrategy.MVC_CONTENT_NEGOTIATION_MANAGER, ContentNegotiationManager)
    }

    private AnnotationConfigWebApplicationContext context(String configuration, Map<String, Object> properties,
                                                          boolean springNegotiation) {
        contexts.computeIfAbsent("${configuration}:${springNegotiation}".toString()) {
            def context = new AnnotationConfigWebApplicationContext()
            context.servletContext = new MockServletContext()
            context.environment.propertySources.addFirst(new MapPropertySource('test',
                    properties + [(Settings.MIME_NEGOTIATION_SPRING): springNegotiation]))
            context.register(ComparisonConfiguration)
            context.refresh()
            context
        }
    }

    private static String describe(Map<String, Object> sent) {
        List<String> parts = []
        if (sent.accept) {
            parts << "Accept: ${sent.accept == CHROME ? '<Chrome>' : sent.accept}".toString()
        }
        if (sent.userAgent) {
            parts << 'browser User-Agent'
        }
        if (sent.xhr) {
            parts << 'XHR'
        }
        if (sent.format) {
            parts << "format=${sent.format}".toString()
        }
        if (sent.contentType) {
            parts << "Content-Type: ${sent.contentType}".toString()
        }
        parts ? parts.join(', ') : 'no headers'
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(MimeTypesConfiguration)
    static class ComparisonConfiguration {

        @Bean
        GrailsApplication grailsApplication(ApplicationContext applicationContext, ConfigurableEnvironment environment) {
            def application = new DefaultGrailsApplication()
            application.config = new PropertySourcesConfig(environment.propertySources)
            application.applicationContext = applicationContext
            application.mainContext = applicationContext
            application
        }
    }
}
