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

import grails.web.mime.MimeType
import grails.config.Config
import org.grails.config.PropertySourcesConfig
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import spock.lang.Specification

class GrailsContentNegotiationStrategySpec extends Specification {

    private static final MimeType[] MIME_TYPES = [
            new MimeType('text/html', 'html'),
            new MimeType('application/json', 'json'),
            new MimeType('application/xml', 'xml'),
            MimeType.ALL
    ] as MimeType[]

    void "Spring media types are resolved from the Grails accept header parser"() {
        given:
        def request = new MockHttpServletRequest()
        request.addHeader('Accept', 'application/json;profile=v1;q=0.8, application/xml;q=0.9')

        when:
        List<MediaType> mediaTypes = strategy().resolveMediaTypes(new ServletWebRequest(request))

        then:
        mediaTypes*.type == ['application', 'application']
        mediaTypes*.subtype == ['xml', 'json']
        mediaTypes[0].qualityValue == 0.9d
        mediaTypes[1].parameters.profile == 'v1'
    }

    void "format parameter remains authoritative over the accept header"() {
        given:
        def request = new MockHttpServletRequest()
        request.setParameter('format', 'json')
        request.addHeader('Accept', 'application/xml')

        expect:
        strategy().resolveMimeTypes(request)*.extension == ['json']
    }

    void "response format request attribute remains an authoritative compatibility override"() {
        given:
        def request = new MockHttpServletRequest()
        request.setAttribute(GrailsApplicationAttributes.RESPONSE_FORMAT, 'xml')
        request.addHeader('Accept', 'application/json')

        expect:
        strategy().resolveMimeTypes(request)*.extension == ['xml']
    }

    void "missing accept header resolves to the configured all format"() {
        expect:
        strategy().resolveMimeTypes(new MockHttpServletRequest())*.extension == ['all']
    }

    void "configured browser user agent suppression retains the XHR exception"() {
        given:
        Config config = config(['grails.mime.disable.accept.header.userAgents': ['Trident']])
        def strategy = strategy(config)
        def request = new MockHttpServletRequest()
        request.addHeader('User-Agent', 'Trident/7.0')
        request.addHeader('Accept', 'application/json')

        expect:
        strategy.resolveMimeTypes(request)*.extension == ['all']

        when:
        request.addHeader('X-Requested-With', 'XMLHttpRequest')

        then:
        strategy.resolveMimeTypes(request)*.extension == ['json']
    }

    void "the Web MVC configurer contributes format aliases and leaves Spring's strategies alone"() {
        given:
        def configurer = Mock(ContentNegotiationConfigurer)

        when:
        new GrailsMimeTypesWebMvcConfigurer(strategy()).configureContentNegotiation(configurer)

        then: "Spring keeps its own header strategy, so unknown media types still get a 406"
        0 * configurer.strategies(_)

        and: "the configured extensions are registered as Spring format aliases"
        1 * configurer.mediaTypes({ Map<String, MediaType> aliases ->
            aliases['json'] == MediaType.APPLICATION_JSON &&
                    aliases['xml'] == MediaType.APPLICATION_XML &&
                    aliases['html'] == MediaType.TEXT_HTML &&
                    !aliases.containsKey('all')
        })
    }

    private static GrailsContentNegotiationStrategy strategy(Config config = config([:])) {
        return new GrailsContentNegotiationStrategy(MIME_TYPES, config)
    }

    private static Config config(Map<String, Object> values) {
        def propertySources = new MutablePropertySources()
        propertySources.addLast(new MapPropertySource('test', values))
        return new PropertySourcesConfig(propertySources)
    }
}
