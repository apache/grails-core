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
package functionaltests.pages

import geb.Page
import geb.module.TextInput

class BookListPage extends Page {

    static String pageTitle = 'Book List'

    static url = '/book/index'
    static at = { title == pageTitle }
}

class BookShowPage extends Page {

    static List<String> pageTitles = [
            'Show Book',
            'Book anzeigen'
    ]

    static url = '/book/show'
    static at = { title in pageTitles }
    static content = {
        createButton { $('a', class: 'create') }
        deleteButton { $('input', class: 'delete') }
    }
}

class BookCreatePage extends Page {

    static String pageTitle = 'Create Book'

    static url = '/book/create'
    static at = { title == pageTitle }
    static content = {
        titleField { $('#title').module(TextInput) }
        createButton { $('#create') }
    }

    void createBook(String title) {
        titleField.value(title)
        createButton.click()
    }
}
