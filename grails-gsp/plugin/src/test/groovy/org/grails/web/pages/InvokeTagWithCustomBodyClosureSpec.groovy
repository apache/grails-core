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

import grails.artefact.Artefact
import grails.test.AbstractGrailsEnvChangingSpec
import grails.testing.web.taglib.TagLibUnitTest

class InvokeTagWithCustomBodyClosureSpec extends AbstractGrailsEnvChangingSpec implements TagLibUnitTest<CustomApplicationTagLib> {
    def "Test that a custom tag library can invoke another tag with a closure body"(grailsEnv) {
        when:'We call a custom tag that invokes an existing tag with a closure body'
            changeGrailsEnv(grailsEnv)
            def content = applyTemplate("<a:myLink />")
            def content2 = applyTemplate("<a:myLink />")
        then:"The expected result is rendered and when we call it a second time the cached version is used so we test that too"
            content == '<a href="/one/two"></a><a href="/foo/bar">Hello World</a>'
            content == content2
        where:
            grailsEnv << AbstractGrailsEnvChangingSpec.grailsEnvs
    }

    def 'Test invoking a tag with and then without attributes'() {
        when:
            def content = applyTemplate("<a:myLink foo='bar'/><a:myLink/>")

        then:
            content == '<a href="/one/two"></a><a href="/foo/bar">Hello World</a><a href="/one/two"></a><a href="/foo/bar">Hello World</a>'
    }
}

@Artefact("TagLibrary")
class CustomApplicationTagLib {
    static namespace = "a"
    def myLink = { attrs, body ->
       out << g.link(controller:"one", action:"two")
       out << g.link(controller: "foo", action:"bar") {
           setLink(attrs, "World")
       }
    }

    private setLink(attrs, name) {
        "Hello $name"
    }
}
