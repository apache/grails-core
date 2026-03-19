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

class BookListPage extends Page {

    static String pageTitle = 'Book List'

    static url = '/book/index'
    static at = { title == pageTitle }
}

class BookShowPage extends Page {

    static String pageTitle = 'Show Book'

    static url = '/book/show'
    static at = { title == pageTitle }
    static content = {
        bookTitle { $('li.fieldcontain div').text() }
    }
}

class BookCreatePage extends Page {

    static String pageTitle = 'Create Book'

    static url = '/book/create'
    static at = { title == pageTitle }
    static content = {
        titleInput { $('input#title').module(TextInput) }
        createButton { $('input#create') }
    }

    void createBook(String title) {
        titleInput.value(title)
        createButton.click()
    }
}
