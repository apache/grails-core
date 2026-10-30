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

package springjson

import java.util.concurrent.CompletableFuture

import com.fasterxml.jackson.annotation.JsonView

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

import grails.gorm.transactions.Transactional

class BookController {

    static responseFormats = ['json']

    def show(Long id) {
        respond Book.get(id)
    }

    def summary(Long id) {
        respond Book.get(id), excludes: ['isbn']
    }

    @JsonView(BookViews.Brief)
    def brief(Long id) {
        respond Book.get(id)
    }

    @Transactional
    def save(Book book) {
        if (book.hasErrors()) {
            respond book.errors
            return
        }
        book.save(flush: true)
        respond book, status: HttpStatus.CREATED
    }

    def conflict() {
        respond ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, 'The title is taken'), formats: ['json', 'xml']
    }

    def missing(Long id) {
        throw new BookNotFoundException(id)
    }

    def broken() {
        throw new IllegalStateException('Not handled by any exception handler')
    }

    def search() {
        throw new MissingServletRequestParameterException('title', 'String')
    }

    def unavailable() {
        def exception = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE)
        exception.headers.set(HttpHeaders.RETRY_AFTER, '30')
        respond exception
    }

    def entity(Long id) {
        Book book = Book.get(id)
        book ? ResponseEntity.ok().header('X-Book', book.title).body([id: book.id, title: book.title]) :
                ResponseEntity.notFound().build()
    }

    def retired() {
        ProblemDetail.forStatusAndDetail(HttpStatus.GONE, 'The edition is retired')
    }

    @ResponseBody
    @ResponseStatus(HttpStatus.ACCEPTED)
    def titles() {
        Book.list()*.title
    }

    @ResponseBody
    def later() {
        CompletableFuture.supplyAsync { [title: 'Written later'] }
    }

    ResponseEntity<StreamingResponseBody> download() {
        ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body({ OutputStream out ->
            out << 'Streamed'
        } as StreamingResponseBody)
    }
}
