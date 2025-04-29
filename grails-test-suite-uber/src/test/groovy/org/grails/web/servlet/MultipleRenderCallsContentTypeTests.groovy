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
package org.grails.web.servlet

import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification
import grails.artefact.Artefact

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class MultipleRenderCallsContentTypeTests extends Specification implements ControllerUnitTest<MultipleRenderController> {

    void testLastContentTypeWins() {
        when:
        controller.test()

        then:
        "application/json;charset=utf-8" == response.contentType
    }

    void testPriorSetContentTypeWins() {
        when:
        controller.test2()

        then:
        "text/xml" == response.contentType
    }
}

@Artefact('Controller')
class MultipleRenderController {

    def test = {
        render(text:"foo",contentType:"text/xml")
        render(text:"bar",contentType:"application/json")
    }

    def test2 = {
        response.contentType = "text/xml"

        render(text:"bar")
    }
}
