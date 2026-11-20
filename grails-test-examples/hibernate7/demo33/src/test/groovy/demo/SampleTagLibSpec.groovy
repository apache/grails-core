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
// tag::basic_declaration[]
package demo

import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class SampleTagLibSpec extends Specification implements TagLibUnitTest<SampleTagLib> {

// end::basic_declaration[]
    // tag::test_simple_tag_as_method[]
    void "test simple tag as method"() {
        expect:
        tagLib.helloWorld() == 'Hello, World!'
    }
    // end::test_simple_tag_as_method[]
    // tag::test_simple_tag_with_applyTemplate[]
    void "test tags with applyTemplate"() {
        expect:
        applyTemplate('<demo:helloWorld/>') == 'Hello, World!'
        applyTemplate('<demo:sayHello name="Adrian"/>') == 'Hello, Adrian!'
    }
    // end::test_simple_tag_with_applyTemplate[]
    // tag::test_tag_as_method_with_parameters[]
    void "test tag as method with parameters"() {
        expect:
        tagLib.sayHello(name: 'Robert') == 'Hello, Robert!'
    }
    // end::test_tag_as_method_with_parameters[]
    // tag::test_with_model[]
    void "test a tag that access the model"() {
        expect: 'the value attribute is used in the output'
        applyTemplate('<demo:renderSomeNumber value="${x + y}"/>',
                      [x: 23, y: 19]) == 'The Number Is 42'
    }
    // end::test_with_model[]
// tag::basic_declaration[]
}
// end::basic_declaration[]


