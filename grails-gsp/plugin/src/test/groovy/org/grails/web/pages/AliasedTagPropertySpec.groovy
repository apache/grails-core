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

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification
import grails.artefact.Artefact

/**
 *
 */
class AliasedTagPropertySpec extends Specification implements TagLibUnitTest<AliasedTagLib> {

    def "Test that a property assigned to a tag is also a tag"() {
        when:"We call a regular tag"
            def content = applyTemplate "<a:hello />"

        then:"the tag is invoked as per normal"
            content == "Hello"

        when:"We call an aliased version of the tag "
            content = applyTemplate '<a:hola spanish="true"/>'

        then:"The alias is also a valid tag"
            content == "Hola"

    }
}

@Artefact("TagLibrary")
class AliasedTagLib {
    static namespace = "a"

    def hello = { attrs, body ->
        out << (attrs.spanish ? "Hola" : "Hello")
    }

    def hola = hello
}
