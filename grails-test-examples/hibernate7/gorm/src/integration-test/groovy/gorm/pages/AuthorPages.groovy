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

package gorm.pages

import geb.Page

/**
 * Geb page objects for Author scaffolding views.
 */
class AuthorListPage extends Page {
    static url = '/author/index'
    static at = { title?.contains('Author') || $('h1').text()?.contains('Author') }

    static content = {
        authorTable { $('table.table') }
        tableRows { $('table.table tbody tr') }
        createButton { $('a', text: contains('New')) ?: $('a.create') }
    }
}

class AuthorCreatePage extends Page {
    static url = '/author/create'
    static at = { title?.contains('Author') || $('h1').text()?.contains('Author') }

    static content = {
        name { $('input[name=name]') }
        email { $('input[name=email]') }
        birthDate { $('input[name=birthDate]') }
        biography { $('textarea[name=biography]') }
        active { $('input[name=active]') }
        createButton { $('input[type=submit]', value: contains('Create')) ?: $('button', text: contains('Create')) }
        errors { $('div.errors') ?: $('ul.errors') ?: $('span.error') }
    }
}

class AuthorShowPage extends Page {
    static url = '/author/show'
    static at = { title?.contains('Author') || $('h1').text()?.contains('Author') }

    static content = {
        authorName { $('span.property-value', 0) ?: $('td', 0) }
        editButton { $('a', text: contains('Edit')) ?: $('a.edit') }
        deleteButton { $('button', text: contains('Delete')) ?: $('input[type=submit]', value: contains('Delete')) }
        booksList { $('span.property-value a') }
    }
}

class AuthorEditPage extends Page {
    static url = '/author/edit'
    static at = { title?.contains('Author') || $('h1').text()?.contains('Author') }

    static content = {
        name { $('input[name=name]') }
        email { $('input[name=email]') }
        updateButton { $('input[type=submit]', value: contains('Update')) ?: $('button', text: contains('Update')) }
        errors { $('div.errors') ?: $('ul.errors') ?: $('span.error') }
    }
}
