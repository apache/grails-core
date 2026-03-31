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
 * Geb page objects for Book scaffolding views.
 */
class BookListPage extends Page {
    static url = '/book/index'
    static at = { title?.contains('Book') || $('h1').text()?.contains('Book') }

    static content = {
        bookTable { $('table.table') }
        tableRows { $('table.table tbody tr') }
        createButton { $('a', text: contains('New')) ?: $('a.create') }
    }
}

class BookCreatePage extends Page {
    static url = '/book/create'
    static at = { title?.contains('Book') || $('h1').text()?.contains('Book') }

    static content = {
        titleField { $('input[name=title]') }
        isbn { $('input[name=isbn]') }
        description { $('textarea[name=description]') }
        pageCount { $('input[name=pageCount]') }
        price { $('input[name=price]') }
        publicationDate { $('input[name=publicationDate]') }
        inStock { $('input[name=inStock]') }
        author { $('select[name="author.id"]') ?: $('select[name=author]') }
        createButton { $('input[type=submit]', value: contains('Create')) ?: $('button', text: contains('Create')) }
        errors { $('div.errors') ?: $('ul.errors') ?: $('span.error') }
    }
}

class BookShowPage extends Page {
    static url = '/book/show'
    static at = { title?.contains('Book') || $('h1').text()?.contains('Book') }

    static content = {
        bookTitle { $('span.property-value', 0) ?: $('td', 0) }
        authorLink { $('a', text: contains('Author')) ?: $('span.property-value a') }
        editButton { $('a', text: contains('Edit')) ?: $('a.edit') }
        deleteButton { $('button', text: contains('Delete')) ?: $('input[type=submit]', value: contains('Delete')) }
    }
}

class BookEditPage extends Page {
    static url = '/book/edit'
    static at = { title?.contains('Book') || $('h1').text()?.contains('Book') }

    static content = {
        titleField { $('input[name=title]') }
        isbn { $('input[name=isbn]') }
        author { $('select[name="author.id"]') ?: $('select[name=author]') }
        updateButton { $('input[type=submit]', value: contains('Update')) ?: $('button', text: contains('Update')) }
        errors { $('div.errors') ?: $('ul.errors') ?: $('span.error') }
    }
}
