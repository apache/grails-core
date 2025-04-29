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
package org.grails.plugins.web.interceptors

import grails.artefact.Interceptor
import grails.util.Environment
import grails.web.mapping.UrlMappingInfo
import spock.lang.Issue
import spock.lang.Specification

class UrlMappingMatcherSpec extends Specification {

    @Issue('https://github.com/apache/grails-core/issues/9179')
    void 'test a matcher with a uri does not match all requests'() {
        given:
        def matcher = new UrlMappingMatcher(Mock(Interceptor))
        def mappingInfo = Mock(UrlMappingInfo)

        when:
        matcher.matches(uri: '/test/**')

        then:
        !matcher.doesMatch('/demo/index', mappingInfo)
    }

    @Issue("https://github.com/apache/grails-core/issues/9208")
    void "test caching of results in production"() {
        given:
        System.setProperty(Environment.KEY, "prod")
        String controller = "foo"
        String url = "/foo/test"
        def info = Mock(UrlMappingInfo)
        info.getControllerName() >> controller

        when:
        def matcher = new UrlMappingMatcher(Mock(Interceptor))
        matcher.matches(controller: controller)


        then:
        matcher.doesMatch(url, info)


        when:
        matcher = new UrlMappingMatcher(Mock(Interceptor))
        matcher.matches(controller: 'bar')

        then:
        !matcher.doesMatch(url, info)

        cleanup:
        System.setProperty(Environment.KEY, "test")
    }
}
