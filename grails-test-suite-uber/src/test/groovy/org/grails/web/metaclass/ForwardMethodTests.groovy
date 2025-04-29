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
package org.grails.web.metaclass

import grails.artefact.Artefact
import grails.testing.web.GrailsWebUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ForwardMethodTests extends Specification implements GrailsWebUnitTest{

    void testForwardMethod() {
        given:
        def testController = new ForwardingController()

        webRequest.controllerName = "fowarding"

        expect:
        "/fowarding/two" == testController.one()
        "/next/go" == testController.three()
        "/next/go/10" == testController.four()
        "bar" == request.foo
    }
}

@Artefact('Controller')
class ForwardingController {
    def one = {
        forward(action:'two')
    }

    def two = {
        render 'me'
    }

    def three = {
        forward(controller:'next', action:'go')
    }

    def four = {
       forward(controller:'next', action:'go',id:10, model:[foo:'bar'])
    }
}
