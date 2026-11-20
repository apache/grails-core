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

class DemoController {

// end::basic_declaration[]
    static allowedMethods = [clearDatabase: 'DELETE']

    // tag::render_hello[]
    def hello() {
        render 'Hello, World!'
    }

    // end::render_hello[]
    def clearDatabase() {
        render 'Success'
    }

    def invokeTagWhichInvokesTag() {
        response.writer << one.sayHello()
    }

    def invokeCoreTagAsMethod() {
        // test invoke core tag
        response.writer << link(controller:'demo',action:'clearDatabase')
    }

    private String privateMethod() {
        'From Private'
    }

    protected String protectedMethod() {
        'From Protected'
    }

// tag::basic_declaration[]
}
// end::basic_declaration[]
