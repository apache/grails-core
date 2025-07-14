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

package functional.tests

import grails.gorm.transactions.Transactional
import grails.mongodb.MongoEntity
import org.grails.datastore.gorm.GormEntity

import static org.springframework.http.HttpStatus.CREATED
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.HttpStatus.NO_CONTENT
import static org.springframework.http.HttpStatus.OK

@Transactional(readOnly = true)
class AuthorController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond Author.list(params), model:[authorCount: Author.count()]
    }

    def show(Author author) {
        assert !(author instanceof MongoEntity)
        assert author instanceof GormEntity
        respond author
    }

    def create() {
        respond new Author(params)
    }

    @Transactional
    def save(Author author) {
        if (author == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        if (author.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond author.errors, view:'create'
            return
        }

        author.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'author.label', default: 'Author'), author.id])
                redirect author
            }
            '*' { respond author, [status: CREATED] }
        }
    }

    def edit(Author author) {
        respond author
    }

    @Transactional
    def update(Author author) {
        if (author == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        if (author.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond author.errors, view:'edit'
            return
        }

        author.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'author.label', default: 'Author'), author.id])
                redirect author
            }
            '*'{ respond author, [status: OK] }
        }
    }

    @Transactional
    def delete(Author author) {

        if (author == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        author.delete flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'author.label', default: 'Author'), author.id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'author.label', default: 'Author'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
