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
package org.grails.plugins.xml

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class ProblemXmlBindingSpec extends Specification implements ControllerUnitTest<ProblemBindingController> {

    void 'a content type of application/problem+xml is the xml format, whose body binds as XML'() {
        given:
        request.method = 'POST'
        request.contentType = 'application/problem+xml'
        request.content = '<problem><title>Conflict</title><status>409</status></problem>'.getBytes('UTF-8')

        when:
        controller.problem()

        then:
        request.format == 'xml'
        response.text == 'Conflict 409'
    }
}

@Artefact('Controller')
class ProblemBindingController {

    def problem(ProblemBindingCommand command) {
        render "${command.title} ${command.status}"
    }
}

class ProblemBindingCommand {
    String title
    Integer status
}
