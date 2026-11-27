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
package grails.rest.web

import grails.artefact.Artefact
import grails.converters.JSON
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class PrettyPrintJsonRenderingSpec extends Specification implements ControllerUnitTest<ScalarJsonController> {

    Closure doWithConfig() {
        { config -> config['grails.converters.json.pretty.print'] = true }
    }

    void 'pretty printing supports scalar roots through converters, render and respond'() {
        given:
        def output = new StringWriter()
        def json = new JSON(value)
        json.prettyPrint = true

        when:
        json.render(output)
        controller.render(new JSON(value))

        then:
        output.toString() == expected
        response.contentAsString == expected

        when:
        response.reset()
        response.format = 'json'
        controller.respond(value)

        then:
        response.contentAsString == expected

        where:
        value              | expected
        'ok'               | '"ok"'
        "Hi ${'Ada'}"      | '"Hi Ada"'
        ScalarRole.HEAD    | '"HEAD"'
        42                 | '42'
        1.5d               | '1.5'
        true               | 'true'
        false              | 'false'
    }

    void 'an enum can be cast to the pretty printed legacy converter'() {
        when:
        controller.render(ScalarRole.HEAD as JSON)

        then:
        response.contentAsString == '"HEAD"'
    }

    void 'the pretty printed converter accepts a null root'() {
        given:
        def output = new StringWriter()
        def json = new JSON(null)
        json.prettyPrint = true

        when:
        json.render(output)

        then:
        output.toString() == 'null'
    }
}

@Artefact('Controller')
class ScalarJsonController { }

enum ScalarRole {
    HEAD
}
