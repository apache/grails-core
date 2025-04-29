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
package grails.test.mixin

import grails.artefact.TagLibrary
import grails.gsp.TagLib
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class TagLibraryInvokeBodySpec extends Specification implements TagLibUnitTest<SimpleTagLib> {

    void "Test that a tag can be invoked with a custom body"() {
        given:"A custom body"
            def body = { params ->
                "hello ${params.param}"
            }

        when:"A tag is invoked with the custom body"
            def result = tagLib.output([param: "test"], body)

        then:"The output is rendered correctly"
            result == "hello test"
    }
}

@TagLib
class SimpleTagLib implements TagLibrary {
    def output = { attrs, body ->
        def param = attrs.param
        out << body(param: param)
    }
}
