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

import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

class UrlMappingTests extends Specification implements UrlMappingsUnitTest<UrlMappings> {

    void testReverseTopLevelMapping() {

        when:
        def reverse = urlMappingsHolder.getReverseMapping("competition", null, null)

        then:
        "/competition/foo" == reverse.createURL("competition", "foo", null, "utf-8")
        "/competition/foo?name=bob" == reverse.createURL("competition", "foo", [name: "bob"], "utf-8")

        when:
        reverse = urlMappingsHolder.getReverseMapping("competition", "enter", [name: "bob"])

        then:
        reverse
        "/competition/enter" == reverse.createURL("competition", "enter", null, "utf-8")
        "/competition/enter?name=bob" == reverse.createURL("competition", "enter", [name: "bob"], "utf-8")

        when:
        reverse = urlMappingsHolder.getReverseMapping("content", null, null)

        then:
        reverse
        "/tsandcs" == reverse.createURL(id: "tsandcs", "utf-8")
        "/tsandcs?foo=bar" == reverse.createURL(id: "tsandcs", foo: "bar", "utf-8")

        when:
        reverse = urlMappingsHolder.getReverseMapping("content", null, [foo: "bar"])

        then:
        reverse
        "/tsandcs" == reverse.createURL(id: "tsandcs", "utf-8")
        "/tsandcs?foo=bar" == reverse.createURL(id: "tsandcs", foo: "bar", "utf-8")
    }

    void testTopLevelMapping() {

        when:
        def info = urlMappingsHolder.match("/competition/foo")

        then:
        assert info
        "competition" == info.controllerName

        when:
        info = urlMappingsHolder.match("/survey/bar")

        then:
        info
        "survey" == info.controllerName

        when:
        info = urlMappingsHolder.match("/tsandcs")

        then:
        info
        "content" == info.controllerName
        "view" == info.actionName

        when:
        info = urlMappingsHolder.match("/api/foobar/10")

        then:
        info
        "10" == info.id
    }

    static class UrlMappings {
        static mappings = {
            "/competition/$action?"{
                controller = "competition"
            }

            "/survey/$action?"{
                controller = "survey"
            }

            "/$id?"{
                controller = "content"
                action = "view"
            }

            group "/api", {
                "/test"(resources: "test")
                "/foobar/$id"(controller:"foobar")
            }
        }
    }
}
