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
package org.grails.web.pages

import grails.test.AbstractGrailsEnvChangingSpec
import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest

class OptionalTagBodySpec extends AbstractGrailsEnvChangingSpec implements TagLibUnitTest<CustomTagLib> {
    def "Test that the existence of a body can be tested with groovy truth"(grailsEnv) {
        when:
            changeGrailsEnv(grailsEnv)
            def content = applyTemplate("<a:myBody />")
            def content2 = applyTemplate("<a:myBody>Hello</a:myBody>")
        then:
            content == 'nobody'
            content2 == 'Hello'
        where:
            grailsEnv << AbstractGrailsEnvChangingSpec.grailsEnvs
    }
}

@Artefact("TagLibrary")
class CustomTagLib {

    static namespace = "a"

    def myBody = { attrs, body ->
        if (body) {
            out << body()
        } else {
            out << 'nobody'
        }
    }
}
