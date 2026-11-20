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
import grails.compiler.GrailsCompileStatic
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class ControllerCompileStaticTagLibSpec extends Specification implements ControllerUnitTest<CompileStaticTagController> {

    void setup() {
        mockTagLibs(CompileStaticDefaultTagLib, CompileStaticNamespacedTagLib)
    }

    void "controller with @GrailsCompileStatic can call a default-namespace tag directly"() {
        when:
        controller.useDefaultNamespaceTag()

        then:
        response.contentAsString == 'hello! World'
    }

    void "controller with @GrailsCompileStatic can call a tag via namespace dispatcher property"() {
        when:
        controller.useNamespacedTag()

        then:
        response.contentAsString == 'hello! World'
    }
}

@Artefact('Controller')
@GrailsCompileStatic
class CompileStaticTagController {

    def useDefaultNamespaceTag() {
        // tag in default namespace invoked directly on this; dispatched at runtime
        // through TagLibraryInvoker.methodMissing
        response.writer << greet(name: 'World')
    }

    def useNamespacedTag() {
        // namespace dispatcher property resolved at runtime through
        // TagLibraryInvoker.propertyMissing, tag invoked on the resulting dispatcher
        response.writer << cst.greet(name: 'World')
    }
}

@Artefact('TagLib')
class CompileStaticDefaultTagLib {
    Closure greet = { attrs, body ->
        out << "hello! ${attrs.name}"
    }
}

@Artefact('TagLib')
class CompileStaticNamespacedTagLib {
    static namespace = 'cst'

    Closure greet = { attrs, body ->
        out << "hello! ${attrs.name}"
    }
}
