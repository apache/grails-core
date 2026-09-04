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

package gspstatic

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class DemoController {

    def index() {
        [title: 'Statically compiled', books: [new Book(title: 'Dune', pages: 412),
                                               new Book(title: 'Emma', pages: 474)]]
    }

    def declared() {
        render(view: 'declared', model: [book: new Book(title: 'Ubik', pages: 224), count: 3])
    }

    def frameworkNames() {
        flash.message = 'from flash'
        render(view: 'frameworkNames')
    }
}
