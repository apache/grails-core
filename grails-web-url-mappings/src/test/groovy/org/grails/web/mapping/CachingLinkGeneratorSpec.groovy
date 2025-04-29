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

import grails.util.GrailsWebMockUtil
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
        linkGenerator = new MyCachingLinkGenerator("http://grails.org/")
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
        key = linkGenerator.makeKey([action: "bar"])

        then: "its in the key"
        key == "link[controller:foo, action:bar]"

        when: "its in the params"
        key = linkGenerator.makeKey([controller: "foo", action: "bar"])

        then: "its in the key"
        key == "link[controller:foo, action:bar]"
    }

    void "test namespace"() {
        given:
        String key

        when: "not in the request or params"
        key = linkGenerator.makeKey([controller: "foo", action: "bar"])

        then: "its not in the key"
        key == "link[controller:foo, action:bar]"

        when: "its in the params"
        key = linkGenerator.makeKey([controller: "foo", action: "bar", namespace: "foo"])

        then: "its in the key"
        key == "link[controller:foo, action:bar, namespace:foo]"

        when: "its in the request but the controller doesn't match"
        request.setControllerNamespace("fooReq")
        request.setControllerName("x")
        key = linkGenerator.makeKey([controller: "foo", action: "bar"])

        then: "its not in the key"
        key == "link[controller:foo, action:bar]"

        when: "its in the request and the controller matches"
        request.setControllerNamespace("fooReq")
        request.setControllerName("foo")
        key = linkGenerator.makeKey([controller: "foo", action: "bar"])

        then: "its in the key"
        key == "link[controller:foo, action:bar, namespace:fooReq]"

        when: "its in the request and the params"
        request.setControllerNamespace("fooReq")
        key = linkGenerator.makeKey([controller: "foo", action: "bar", namespace: "fooParam"])

        then: "params wins"
        key == "link[controller:foo, action:bar, namespace:fooParam]"
    }

    void "test resource with action"() {
        given:
        String key

        when: "the args are a resource and action"
        request.setControllerNamespace("fooReq")
        request.setControllerName("foo")
        key = linkGenerator.makeKey([resource: new Resource(id: 1), action: "bar"])

        then: "controller and namespace aren't in the key"
        key == "link[resource:org.grails.web.mapping.CachingLinkGeneratorSpec\$Resource->1, action:bar]"
    }


    class MyCachingLinkGenerator extends CachingLinkGenerator {
        public MyCachingLinkGenerator(String serverBaseURL) {
            super(serverBaseURL)
        }

        String makeKey(Map attrs) {
            super.makeKey(LINK_PREFIX, attrs)
        }
    }

    class Resource {
        Long id

        Long ident() {
            id
        }
    }
}
