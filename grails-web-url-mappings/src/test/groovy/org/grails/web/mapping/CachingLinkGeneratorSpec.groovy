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

package org.grails.web.mapping

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.CamelCaseUrlConverter
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.CachingLinkGenerator
import org.grails.web.servlet.mvc.DefaultRequestStateLookupStrategy
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Shared
import spock.lang.Specification

/**
 * Tests for the {@link org.grails.web.mapping.CachingLinkGenerator} class
 */
class CachingLinkGeneratorSpec extends Specification {

    @Shared
    MyCachingLinkGenerator linkGenerator

    @Shared
    GrailsWebRequest request

    void setup() {
        linkGenerator = new MyCachingLinkGenerator("https://grails.apache.org/")
        request = GrailsWebMockUtil.bindMockWebRequest()
        linkGenerator.requestStateLookupStrategy = new DefaultRequestStateLookupStrategy(request)
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void "test controller"() {
        given:
        String key

        when: "its in the request"
        request.setControllerName("foo")
        key = linkGenerator.linkKey([action: "bar"])

        then: "the controller the link resolves to is in the key"
        key == "link[action:bar]target[controller:foo, namespace:null, action:bar, method:*]"

        when: "its in the params"
        key = linkGenerator.linkKey([controller: "foo", action: "bar"])

        then: "its in the key"
        key == "link[controller:foo, action:bar]target[controller:foo, namespace:null, action:bar, method:*]"
    }

    void "test namespace"() {
        given:
        String key

        when: "not in the request or params"
        key = linkGenerator.linkKey([controller: "foo", action: "bar"])

        then: "no namespace is resolved"
        key == "link[controller:foo, action:bar]target[controller:foo, namespace:null, action:bar, method:*]"

        when: "its in the params"
        key = linkGenerator.linkKey([controller: "foo", action: "bar", namespace: "foo"])

        then: "its in the key"
        key == "link[controller:foo, action:bar, namespace:foo]target[controller:foo, namespace:foo, action:bar, method:*]"

        when: "its in the request and the target is a controller the application does not define"
        request.setControllerNamespace("fooReq")
        request.setControllerName("x")
        key = linkGenerator.linkKey([controller: "foo", action: "bar"])

        then: "the request namespace is resolved"
        key == "link[controller:foo, action:bar]target[controller:foo, namespace:fooReq, action:bar, method:*]"

        when: "its in the request and the controller matches"
        request.setControllerNamespace("fooReq")
        request.setControllerName("foo")
        key = linkGenerator.linkKey([controller: "foo", action: "bar"])

        then: "the resolved namespace is in the key"
        key == "link[controller:foo, action:bar]target[controller:foo, namespace:fooReq, action:bar, method:*]"

        when: "its in the request and the params"
        request.setControllerNamespace("fooReq")
        key = linkGenerator.linkKey([controller: "foo", action: "bar", namespace: "fooParam"])

        then: "params wins"
        key == "link[controller:foo, action:bar, namespace:fooParam]target[controller:foo, namespace:fooParam, action:bar, method:*]"
    }

    void "test resource with action"() {
        given:
        String key

        when: "the args are a resource and action"
        request.setControllerNamespace("fooReq")
        request.setControllerName("foo")
        key = linkGenerator.linkKey([resource: new Resource(id: 1), action: "bar"])

        then: "the key holds the controller the resource resolves to, never the one handling the request"
        key == "link[resource:org.grails.web.mapping.CachingLinkGeneratorSpec\$Resource->1, action:bar]target[controller:widget, namespace:fooReq, action:bar, method:GET]"
    }


    void "a link is cached separately for each encoding it is generated in"() {
        given: 'a caching link generator over a real URL mapping'
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def generator = new CachingLinkGenerator('http://localhost', '')
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.urlMappingsHolder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            "/$controller/$action?/$id?"()
        })
        def attrs = [controller: 'book', action: 'list', params: [q: '\u00e9']]

        expect: 'the same link generated in two encodings encodes its parameters in each'
        generator.link(attrs, 'UTF-8') == '/book/list?q=%C3%A9'
        generator.link(attrs, 'ISO-8859-1') == '/book/list?q=%E9'
    }

    void "a cached link is keyed on what it resolves to when a subclass overrides makeKey"() {
        given: 'a generator whose makeKey renders only the attributes, as the asset pipeline plugin overrides it'
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def generator = new AttributesOnlyKeyLinkGenerator('http://localhost', '')
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.urlMappingsHolder = new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings {
            "/$controller/$action?/$id?"()
        })

        when: 'the same action-only link is generated while two controllers handle the request'
        request.setControllerName('author')
        def fromAuthor = generator.link(action: 'create')
        request.setControllerName('book')
        def fromBook = generator.link(action: 'create')

        then: 'each targets the controller handling its request, not the one cached first'
        fromAuthor == '/author/create'
        fromBook == '/book/create'
    }

    class MyCachingLinkGenerator extends CachingLinkGenerator {
        public MyCachingLinkGenerator(String serverBaseURL) {
            super(serverBaseURL)
        }
    }

    /**
     * Renders a key from the attributes alone, without calling the superclass, as the asset pipeline
     * plugin's {@code AssetSupportingCachingLinkGenerator} overrides {@code makeKey}.
     */
    static class AttributesOnlyKeyLinkGenerator extends CachingLinkGenerator {
        AttributesOnlyKeyLinkGenerator(String serverBaseURL, String contextPath) {
            super(serverBaseURL, contextPath)
        }

        @Override
        protected String makeKey(String prefix, Map attrs) {
            StringBuilder sb = new StringBuilder(prefix)
            appendMapKey(sb, attrs)
            sb.toString()
        }
    }

    class Resource {
        Long id

        Long ident() {
            id
        }

        String toString() {
            'widget'
        }
    }
}
