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
import spock.lang.Unroll

class TagLibMethodMissingSpec extends AbstractGrailsEnvChangingSpec implements TagLibUnitTest<TagLibMethodMissingTagLib> {

    def setup() {
        mockArtefact(TagLibMethodMissingBTagLib)
    }

    @Unroll("template #template / expected content: #expectedContent / env: #grailsEnv")
    def "Test tag library method missing handling"(template, expectedContent, grailsEnv) {
        when:'We call a tag that invokes an existing tag in other TagLib'
            changeGrailsEnv(grailsEnv)
            def content = applyTemplate(template)
            def content2 = applyTemplate(template)
        then:"The expected result is rendered and when we call it a second time the cached version is used so we test that too"
            content == expectedContent
            content == content2
        where:
            [template, expectedContent, grailsEnv] <<  createCombinationsForGrailsEnvs([
                ['<a:zeroArguments />', 'ab'],
                ['${a.zeroArguments()}', 'ab'],
                ['a${g.renderErrors()}b', 'ab'],
                ['a${renderErrors()}b', 'ab'],
                ['<a:myLink/>', '<a href="/one/two"></a><a href="/foo/bar">Hello World</a>'],
                ['<a:myLink2/>', '<a href="/one/two"></a><a href="/foo/bar">Hello World</a>'],
                ['<a:bodyTag>hello</a:bodyTag>', 'hellohellohellohello'],
                ['hello ${other.printBody{"world"}}!', 'hello world!']
            ])
    }
}
@Artefact("TagLibrary")
class TagLibMethodMissingTagLib {
    static namespace = "a"

    def zeroArguments = { attrs, body ->
        out << 'a'
        g.renderErrors()
        renderErrors()
        out << 'b'
    }

    def myLink = { attrs, body ->
       out << g.link(controller:"one", action:"two")
       out << g.link(controller: "foo", action:"bar") {
           setLink(attrs, "World")
       }
    }

    def myLink2 = { attrs, body ->
        out << link(controller:"one", action:"two")
        out << link(controller: "foo", action:"bar") {
            setLink(attrs, "World")
        }
    }

    def bodyTag = { attrs, body ->
        out << other.printBody(body)
        out << other.printBody([:], body() as String)
        out << other.printBody([:], body())
        out << other.printBody{body()}
    }

    private setLink(attrs, name) {
        "Hello $name"
    }
}

@Artefact("TagLib")
class TagLibMethodMissingBTagLib {
    static namespace = "other"

    Closure printBody = { attrs, body ->
        out << body()
    }
}