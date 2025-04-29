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

/**
 * @author mike
 */
class ResponseCodeUrlMappingTests extends Specification implements UrlMappingsUnitTest<UrlMappings> {

    void testParse() {
        expect:
        urlMappingsHolder
    }

    void testMatch() {
        expect:
        !urlMappingsHolder.match("/")
    }

    void testMatchStatusCodeAndException() {
        when:
        def info = urlMappingsHolder.matchStatusCode(500)

        then:
        "error500" == info.actionName

        when:
        info = urlMappingsHolder.matchStatusCode(500, new IllegalArgumentException())

        then:
        "custom" == info.actionName
    }

    void testForwardMapping() {
        when:
        def info = urlMappingsHolder.matchStatusCode(404)

        then:
        info
        "errors" == info.getControllerName()
        "error404" == info.getActionName()
    }

    void testForwardMappingWithNamedArgs() {
        when:
        def info = urlMappingsHolder.matchStatusCode(500)

        then:
        info
        "errors" == info.getControllerName()
        "error500" ==  info.getActionName()
    }

    void testMissingForwardMapping() {
        when:
        def info = urlMappingsHolder.matchStatusCode(501)
        then:
        !info
    }

    void testNoReverseMappingOccures() {
        when:
        def creator = urlMappingsHolder.getReverseMapping("errors", "error404", null)

        then:
        creator.delegate instanceof DefaultUrlCreator
    }

    static class UrlMappings {
        static mappings = {
            "404"{
                controller = "errors"
                action = "error404"
            }

            "500"(controller:"errors", action:"custom", exception:IllegalArgumentException)
            "500"(controller:"errors", action:"error500")
        }
    }
}
