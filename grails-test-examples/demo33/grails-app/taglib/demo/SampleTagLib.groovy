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

class SampleTagLib {

    static defaultEncodeAs = [taglib:'html']

    static namespace = 'demo'

    // end::basic_declaration[]
    // tag::hello_world[]
    def helloWorld = { attrs ->
        out << 'Hello, World!'
    }
    // end::hello_world[]
    // tag::say_hello[]
    def sayHello = { attrs ->
        out << "Hello, ${attrs.name}!"
    }
    // end::say_hello[]
    // tag::render_some_number[]
    def renderSomeNumber = { attrs ->
        int number = attrs.int('value', -1)
        out << "The Number Is ${number}"
    }
    // end::render_some_number[]

    def renderMessage = {
        out << message(code: 'some.custom.message', locale: request.locale)
    }
// tag::basic_declaration[]
}
// end::basic_declaration[]
