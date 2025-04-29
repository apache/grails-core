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
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 0.4
 */
class ControllerTagLibMethodDispatchSpec extends Specification implements ControllerUnitTest<TestController> {

    void setupSpec() {
        mockTagLibs(MyTagLib, TwoTagLib)
    }

    void testControllerTagLibMethodDispatch() {
        when:
        controller.foo()
        
        then:
        '<a href="/test/foo"></a>hello! bar' == response.contentAsString
    }
}

@Artefact('Controller')
class TestController {
    def foo = {
        // test invoke core tag
        response.writer << link(controller:'test',action:'foo')
        // test invoke namespaced tag
        response.writer << my.test2(foo:"bar")
    }
}

@Artefact('TagLib')
class MyTagLib {
    static namespace = "my"
    Closure test1 = { attrs, body ->
        out << body(foo:"bar", one:2)
    }

    Closure test2 = { attrs, body ->
        out << "hello! ${attrs.foo}"
    }
}

@Artefact('TagLib')
class TwoTagLib {
    static namespace = "two"

    Closure test1 = { attrs, body ->
        out << my.test2(foo:"bar3")
    }
}
