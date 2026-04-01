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
package functional.tests.pages

import geb.Page
import geb.module.TextInput

class AuthorListPage extends Page {

    static String pageTitle = 'Author List'

    static url = '/author/index'
    static at = { title == pageTitle }
}

class AuthorShowPage extends Page {

    static String pageTitle = 'Show Author'

    static url = '/author/show'
    static at = { title == pageTitle }
    static content = {
        authorName { $('li.fieldcontain div').text() }
    }
}

class AuthorCreatePage extends Page {

    static String pageTitle = 'Create Author'

    static url = '/author/create'
    static at = { title == pageTitle }
    static content = {
        nameInput { $('input#name').module(TextInput) }
        createButton { $('input#create') }
    }

    void createAuthor(String title) {
        nameInput.value(title)
        createButton.click()
    }
}

