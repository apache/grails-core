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
package org.grails.web.servlet.mvc

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class TagLibDynamicMethodsTests extends Specification implements TagLibUnitTest<TestTagLib> {

    void testFlashObject() {
        when:
        tagLib.flash.test = "hello"

        then:
        tagLib.flash.test == "hello"
    }

    void testParamsObject() {
        when:
        tagLib.params.test = "hello"

        then:
        tagLib.params.test == "hello"
    }

    void testSessionObject() {
        when:
        tagLib.session.test = "hello"

        then:
        tagLib.session.test == "hello"
    }

    void testGrailsAttributesObject() {
        expect:
        tagLib.grailsAttributes != null
    }

    void testRequestObjects() {
        expect:
        tagLib.request != null

        tagLib.response != null
        tagLib.servletContext != null
    }
}

@Artefact("TagLibrary")
class TestTagLib {
    def myTag = {attrs, body -> body() }
 }

