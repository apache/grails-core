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
package openapirest

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.CREATED
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.HttpStatus.NO_CONTENT
import static org.springframework.http.HttpStatus.OK
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY

import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional

@ReadOnly
class BookController {

    BookService bookService

    static responseFormats = ['json', 'xml']
    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond bookService.list(params), model:[bookCount: bookService.count()]
    }

    def show(Serializable id) {
        respond bookService.get(id)
    }

    @Transactional
    def save(Book book) {
        if (book == null) {
            render status: NOT_FOUND
            return
        }
        if (book.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond book.errors
            return
        }

        try {
            bookService.save(book)
        } catch (ValidationException e) {
            respond book.errors
            return
        }

        respond book, [status: CREATED, view:"show"]
    }

    @Transactional
    def update(Book book) {
        if (book == null) {
            render status: NOT_FOUND
            return
        }
        if (book.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond book.errors
            return
        }

        try {
            bookService.save(book)
        } catch (ValidationException e) {
            respond book.errors
            return
        }

        respond book, [status: OK, view:"show"]
    }

    @Transactional
    def delete(Serializable id) {
        if (id == null || bookService.delete(id) == null) {
            render status: NOT_FOUND
            return
        }

        render status: NO_CONTENT
    }
}
