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

package functional.tests.api

import functional.tests.Book


class BookController {

    static namespace = 'api'

    static responseFormats = ['json', 'hal']

    def index() {
        respond new Book(title: 'API - The Shining')
    }

    def nested() {
        respond new Book(title: 'API - The Shining')
    }

    def testRender() {
        render(view: "index", model: [book: new Book(title: 'API - The Shining')])
    }

    def testRespond() {
        respond(new Book(title: 'API - The Shining'), view: 'index')
    }

    def testRespondOutsideNamespace() {
        respond(new Book(title: 'API - The Shining'), view: 'indexOutsideNamespace')
    }

    def message() {
        [value: 'Hello API']
    }
}