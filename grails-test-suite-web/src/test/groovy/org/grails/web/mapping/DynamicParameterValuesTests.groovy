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
 * @author Graeme Rocher
 * @since 1.0
 */
class DynamicParameterValuesTests extends Specification implements UrlMappingsUnitTest<UrlMappings> {

    void testImplicitNamedAction() {

        when:
        def info = urlMappingsHolder.match("/book/the_stand")
        assert info
        info.configure(webRequest)

        then:
        "book" == info.controllerName
        "show" == info.actionName
        "The Stand" == info.id
        "The Stand" == webRequest.params.id
        10.5 == webRequest.params.price
    }

    void testTwoNamedVariableMapping() {

        when:
        def info = urlMappingsHolder.match("/help")

        then:
        "page" == info.controllerName
        "index" == info.actionName
        "1" == info.id

        when:
        info = urlMappingsHolder.match("/thing")

        then:
        "page" == info.controllerName
        "show" == info.actionName
        "2" == info.id
    }

    static class UrlMappings {
        static mappings = {
            "/book/the_stand" {
                controller = "book"
                action = "show"
                id = "The Stand"
                price = 10.5
            }

            "/help" { controller = "page"
                action = "index"
                id = "1" }
            "/thing" { controller = "page"
                action = "show"
                id = "2" }
        }
    }
}
