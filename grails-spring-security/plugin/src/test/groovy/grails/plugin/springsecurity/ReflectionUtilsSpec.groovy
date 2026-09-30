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
package grails.plugin.springsecurity

import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import grails.web.mime.MimeType
import org.grails.web.mime.HttpServletResponseExtension
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * @author Burt Beckwith
 */
class ReflectionUtilsSpec extends AbstractUnitSpec {

    void 'set config property'() {
        when:
        def foo = SpringSecurityUtils.securityConfig.foo

        then:
        foo instanceof Map
        !foo

        when:
        ReflectionUtils.setConfigProperty 'foo', 'bar'

        then:
        'bar' == SpringSecurityUtils.securityConfig.foo
    }

    void 'get config property'() {
        when:
        def d = ReflectionUtils.getConfigProperty('a.b.c')

        then:
        d instanceof Map
        !d

        when:
        ReflectionUtils.setConfigProperty 'a.b.c', 'd'

        then:
        'd' == ReflectionUtils.getConfigProperty('a.b.c')
        'd' == SpringSecurityUtils.securityConfig.a.b.c
    }

    void 'get role authority'() {
        when:
        String authorityName = 'ROLE_FOO'
        def role = [authority: authorityName]

        then:
        authorityName == ReflectionUtils.getRoleAuthority(role)
    }

    void 'get requestmap url'() {
        when:
        String url = '/admin/**'
        def requestmap = [url: url]

        then:
        url == ReflectionUtils.getRequestmapUrl(requestmap)
    }

    void 'get requestmap config attribute'() {
        when:
        String configAttribute = 'ROLE_ADMIN'
        def requestmap = [configAttribute: configAttribute]

        then:
        configAttribute == ReflectionUtils.getRequestmapConfigAttribute(requestmap)
    }

    void 'as list'() {
        when:
        def list = ReflectionUtils.asList(null)

        then:
        list instanceof List
        !list

        when:
        list = ReflectionUtils.asList([1, 2, 3])

        then:
        list instanceof List
        3 == list.size()

        when:
        String[] strings = ['a', 'b']
        list = ReflectionUtils.asList(strings)

        then:
        list instanceof List
        2 == list.size()
    }

    void 'split map'() {
        when:
        def listOfMaps = [
                [pattern: '/foo', access: ['a', 'b']],
                [pattern: '/user/**', access: ['c'], httpMethod: HttpMethod.POST],
                [pattern: '/bar/**', access: 'd', httpMethod: 'GET']
        ]
        List<InterceptedUrl> split = ReflectionUtils.splitMap(listOfMaps)

        then:
        3 == split.size()

        and:
        split[0].pattern == '/foo'
        split[0].configAttributes*.toString() == ['a', 'b']
        !split[0].httpMethod

        and:
        split[1].pattern == '/user/**'
        split[1].configAttributes*.toString() == ['c']
        split[1].httpMethod == HttpMethod.POST

        and:
        split[2].pattern == '/bar/**'
        split[2].configAttributes*.toString() == ['d']
        split[2].httpMethod == HttpMethod.GET
    }

    void 'get grails serverURL when set'() {
        when:
        String url = 'http://somewhere.org'
        ReflectionUtils.application.config.grails.serverURL = url

        then:
        ReflectionUtils.getGrailsServerURL() == url
    }

    void 'get grails serverURL when not set'() {
        when:
        ReflectionUtils.application.config.grails.serverURL = null

        then:
        ReflectionUtils.getGrailsServerURL() == null
    }

    void 'findFilterNames coerces boolean settings without throwing (including null values)'() {
        when: 'settings are supplied as booleans, strings, or null (e.g. a key absent from config)'
        ConfigObject config = [
                'filterChain.filterNames' : 'dummy',
                'secureChannel.definition': secureChannelValue,
                'ipRestrictions'          : ipRestrictionsValue,
                'useX509'                 : x509Value,
                'useDigestAuth'           : digestAuthValue,
                'useBasicAuth'            : basicAuthValue,
                'useSwitchUserFilter'     : switchUserFilterValue
        ] as ConfigObject
        ReflectionUtils.findFilterChainNames(config)

        then: 'no exception is thrown - Groovy 5 rejects `null as boolean`, so null must coerce to false'
        noExceptionThrown()

        where:
        secureChannelValue | ipRestrictionsValue | x509Value | digestAuthValue | basicAuthValue | switchUserFilterValue
        true               | false               | false     | false           | true           | false
        'true'             | 'false'             | 'false'   | 'false'         | 'true'         | 'false'
        true               | 'false'             | null      | null            | 'true'         | null
        null               | null                | null      | null            | null           | null
    }

    void "findFilterNames includes the x509 filter only when useX509 coerces to true (value=#x509Value)"() {
        given: 'a config that does not list filter names explicitly, so the boolean flags drive the chain'
        ConfigObject config = ['useX509': x509Value] as ConfigObject

        when:
        SortedMap<Integer, String> names = ReflectionUtils.findFilterChainNames(config)

        then: 'x509ProcessingFilter is present only when the flag is truthy; null coerces to false without throwing'
        names.containsValue('x509ProcessingFilter') == x509Enabled

        where:
        x509Value || x509Enabled
        true      || true
        false     || false
        'true'    || true
        null      || false
        ''        || false
    }

    void 'get intercept url map with empty httpMethod config'() {
        when:
        List<Map<String, Object>> interceptUrlMap = [
                [
                        pattern   : '/secure/**',
                        access    : ['IS_AUTHENTICATED_ANONYMOUSLY'],
                        httpMethod: null //This is the case with missing config.
                ]
        ]
        List<InterceptedUrl> interceptedUrls = ReflectionUtils.splitMap(interceptUrlMap)

        then:
        notThrown(GroovyRuntimeException)
        interceptedUrls?.size() == 1
        interceptedUrls.first()?.pattern == '/secure/**'
        interceptedUrls.first().configAttributes?.size() == 1
        interceptedUrls.first().configAttributes.first().attribute == 'IS_AUTHENTICATED_ANONYMOUSLY'
    }

    void 'url mappings are matched on the method the request will be routed as'() {
        given: 'a browser form POST asking to be treated as a DELETE'
        def request = new MockHttpServletRequest(method: 'POST', requestURI: '/book/1')
        request.addParameter('_method', 'DELETE')
        def grailsRequest = new GrailsWebRequest(request, new MockHttpServletResponse(), servletContext)

        and: 'a holder that records the method it was asked to match'
        String matchedWith = null
        def holder = [matchAll: { String uri, String httpMethod, String version ->
            matchedWith = httpMethod
            new UrlMappingInfo[0]
        }] as UrlMappingsHolder

        when: 'the security chain resolves the request to a mapping, before the dispatcher has run'
        ReflectionUtils.matchAllUrlMappings(holder, '/book/1', grailsRequest,
                [getMimeTypeForRequest: { Object... a -> MimeType.HTML }] as HttpServletResponseExtension)

        then: 'it matches on DELETE, so it authorizes the action the dispatcher is about to run'
        matchedWith == 'DELETE'
    }

    void 'url mappings are matched on the request method when nothing asked for an override'() {
        given:
        def request = new MockHttpServletRequest(method: 'POST', requestURI: '/book')
        def grailsRequest = new GrailsWebRequest(request, new MockHttpServletResponse(), servletContext)

        String matchedWith = null
        def holder = [matchAll: { String uri, String httpMethod, String version ->
            matchedWith = httpMethod
            new UrlMappingInfo[0]
        }] as UrlMappingsHolder

        when:
        ReflectionUtils.matchAllUrlMappings(holder, '/book', grailsRequest,
                [getMimeTypeForRequest: { Object... a -> MimeType.HTML }] as HttpServletResponseExtension)

        then:
        matchedWith == 'POST'
    }
}
