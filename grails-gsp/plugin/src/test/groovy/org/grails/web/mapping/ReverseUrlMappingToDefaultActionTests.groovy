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

import grails.artefact.Artefact
import grails.testing.spock.OnceBefore
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
class ReverseUrlMappingToDefaultActionTests extends Specification implements UrlMappingsUnitTest<ReverseUrlMappingToDefaultActionUrlMappings> {

    @OnceBefore
    void mockControllers() {
        mockController(ReverseUrlMappingContentController)
        mockController(ReverseUrlMappingTestController)
    }

    def testLinkTagRendering() {
        when:
        def template = '<g:link url="[controller:\'reverseUrlMappingContent\', params:[dir:\'about\'], id:\'index\']">click</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/about/index">click</a>'
    }
}

@Artefact('UrlMappings')
class ReverseUrlMappingToDefaultActionUrlMappings {
    static mappings = {
        "/$id?"{
            controller = "reverseUrlMappingContent"
            action = "view"
        }

        "/$dir/$id?"{
            controller = "reverseUrlMappingContent"
            action = "view"
        }
    }
}

@Artefact("Controller")
class ReverseUrlMappingContentController {
    def view() {}
}
@Artefact("Controller")
class ReverseUrlMappingTestController {
    def foo() {}
    def index() {}
}
