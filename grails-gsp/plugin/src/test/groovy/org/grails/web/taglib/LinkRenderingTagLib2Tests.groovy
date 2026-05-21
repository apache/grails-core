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
package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

class LinkRenderingTagLib2Tests extends Specification implements UrlMappingsUnitTest<LinkRenderingTagLib2TestUrlMappings> {


    def testLinkWithOnlyId() {
        when:
        def template = '<g:link id="competition">Enter</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/competition">Enter</a>'
    }

    def testLinkWithOnlyIdAndAction() {
        when:
        def template = '<g:link id="competition" controller="content" action="view">Enter</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/competition">Enter</a>'
    }

}

@Artefact('UrlMappings')
class LinkRenderingTagLib2TestUrlMappings {
    static mappings = {
        "/$id?"{
            controller = "content"
            action = "view"
        }

        "/$dir/$id"{
            controller = "content"
            action = "view"
        }
    }
}
