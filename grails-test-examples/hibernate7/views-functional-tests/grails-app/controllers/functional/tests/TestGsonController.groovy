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

package functional.tests

import grails.plugin.json.view.JsonViewTemplateEngine
import groovy.text.Template
import org.springframework.beans.factory.annotation.Autowired

class TestGsonController {

    @Autowired
    JsonViewTemplateEngine templateEngine

    def testTemplateEngine() {
        Template t = templateEngine.resolveTemplate('/book/show')
        def writable = t.make(book: new Book(title:"The Stand"))
        def sw = new StringWriter()
        writable.writeTo( sw )

        render sw
    }

    def testRespond() {
        def test = new Test(name:"Bob")
        respond test
    }

    def testRespondWithTemplateForDomain() {
        def test = new Test(name:"Bob")
        respond test
    }

    def testCompilationError() {
        [one:"two"]
    }

    def testRuntimeError() {
        [one:"two"]
    }

    def testTemplate() {
        [test: new Test(name:"Bob"), child: new Test(name:"Joe")]
    }

    def testGsonFromPlugin() {
        render view:"/fromPlugin"
    }

    def testInheritsFromPlugin() {
        [:]
    }

    def testRespondWithMap() {
        respond one:'two'
    }

    def testRespondWithMapObjectTemplate() {
        respond one:'two'
    }

    def testLinks() {

    }

    def testAugmentModel() {
        respond new Test(name: "John"), model: [age: 20]
    }
}
