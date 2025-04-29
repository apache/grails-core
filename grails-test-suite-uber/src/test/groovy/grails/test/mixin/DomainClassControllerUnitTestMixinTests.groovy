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
package grails.test.mixin

import grails.artefact.Artefact
import grails.converters.XML
import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * A Junit 4 test that tests a scaffolded controllers logic using the new mixins
 */
class DomainClassControllerUnitTestMixinTests extends Specification implements ControllerUnitTest<BookController>, DataTest {

    void setupSpec() {
        mockDomains Book, Author
    }

    void testRelationshipManagementMethods() {
        when:
        def a = new Author(name: "Stephen King")
        a.addToBooks(title: "The Stand", pages: 1100)

        then:
        a.save(flush: true) != null
    }

    void testIndex() {
        when:
        controller.index()

        then:
        "/book/list" == response.redirectedUrl
    }

    void testConvertToXml() {
        when:
        controller.renderXml()

        then:
        response.xml.title.text() == "The Stand"
    }

    void testBinding() {
        given:
        def book = new Book(title:"The Stand", pages: 200)

        expect:
        book.pages == 200

        when:
        book.properties = [pages: 300]

        then:
        book.pages == 300
    }

    void testList() {
        when:
        def model = controller.list()
        def book = new Book(title:"")

        then:
        model.bookInstanceList.size() == 0
        model.bookInstanceTotal == 0

        !book.validate()
        book.errors.allErrors.size() == 1
        book.errors['title'].code == 'nullable'

        when:
        response.reset()
        book.clearErrors()
        book.title = "The Stand"
        book.pages = 1000
        book.save()
        model = controller.list()

        then:
        model.bookInstanceList.size() == 1
        model.bookInstanceTotal == 1
    }

    void testCreate() {
        when:
        params.title = "The Stand"
        params.pages = 500

        def model = controller.create()

        then:
        model.bookInstance?.title == "The Stand"
        model.bookInstance?.pages == 500
    }

    void testSave() {
        when:
        request.method = 'POST'
        controller.save()

        then:
        model.bookInstance != null
        view == '/book/create'

        when:
        params.title = "The Stand"
        params.pages = 500
        controller.save()

        then:
        response.redirectedUrl == '/book/show/1'
        flash.message != null
        Book.count() == 1
    }

    void testShow() {
        when:
        controller.show()

        then:
        flash.message != null
        response.redirectedUrl == '/book/list'

        when:
        def book = new Book(title:"")
        book.title = "The Stand"
        book.pages = 1000

        then:
        book.save() != null

        when:
        params.id = book.id

        def model = controller.show()

        then:
        model.bookInstance == book
    }

    void testEdit() {
        when:
        controller.edit()

        then:
        flash.message != null
        response.redirectedUrl == '/book/list'

        when:
        def book = new Book(title:"")

        book.title = "The Stand"
        book.pages = 1000

        then:
        book.save() != null

        when:
        params.id = book.id

        def model = controller.edit()

        then:
        model.bookInstance == book
    }

    void testUpdate() {
        when:
        request.method = 'POST'
        controller.update()

        then:
        flash.message != null
        response.redirectedUrl == '/book/list'

        when:
        def book = new Book()
        book.title = "The Stand"
        book.pages = 1000

        then:
        book.save() != null

        when:
        response.reset()

        // test invalid parameters in update
        params.id = book.id
        params.title = ""

        controller.update()

        then:
        view == "/book/edit"
        model.bookInstance != null

        when:
        response.reset()
        book.clearErrors()
        params.title = "The Shining"
        params.pages = 500

        controller.update()

        then:
        response.redirectedUrl == "/book/show/$book.id"
        flash.message != null
        Book.get(book.id).title == "The Shining"
        Book.get(book.id).pages == 500
    }

    void testDelete() {
        when:
        request.method = 'POST'
        controller.delete()

        then:
        flash.message != null
        response.redirectedUrl == '/book/list'

        when:
        def book = new Book()
        book.title = "The Stand"
        book.pages = 1000

        then:
        book.save() != null
        Book.count() == 1

        when:
        response.reset()
        params.id = book.id

        controller.delete()

        then:
        Book.count() == 0
        Book.get(book.id) == null
        response.redirectedUrl == '/book/list'
    }

    void testCriteriaQuery() {
        given:
        mockDomain(Book, [[title:"The Stand", pages: 1000], [title:"The Shining", pages:400], [title:"Along Came a Spider", pages:300]])

        expect:
        Book.count() == 3

        when:
        def results = Book.withCriteria {
            like('title', 'The S%')
        }

        then:
        results.size() == 2
    }
}

@Entity
class Book {
    String title
    Date releaseDate = new Date()
    int pages

    static belongsTo = [author:Author]
    static constraints = {
        title blank:false, nullable:false
        author nullable: true
    }
    static mapping = {
        title index:true
    }
}

@Entity
class Author {
    String name

    static hasMany = [books: Book]
}

@Artefact("Controller")
class BookController {

    static allowedMethods = [save: "POST", update: "POST", delete: "POST"]

    def index = {
        redirect(action: "list", params: params)
    }

    def renderXml = {
        render new Book(title:"The Stand") as XML
    }
    def list = {
        params.max = Math.min(params.max ? params.int('max') : 10, 100)
        [bookInstanceList: Book.list(params), bookInstanceTotal: Book.count()]
    }

    def create = {
        def bookInstance = new Book()
        bookInstance.properties = params
        return [bookInstance: bookInstance]
    }

    def save = {
        def bookInstance = new Book(params)
        if (bookInstance.save(flush: true)) {
            flash.message = "${message(code: 'default.created.message', args: [message(code: 'book.label', default: 'Book'), bookInstance.id])}"
            redirect(action: "show", id: bookInstance.id)
        }
        else {
            render(view: "create", model: [bookInstance: bookInstance])
        }
    }

    def show = {
        def bookInstance = Book.get(params.id)
        if (!bookInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'book.label', default: 'Book'), params.id])}"
            redirect(action: "list")
        }
        else {
            [bookInstance: bookInstance]
        }
    }

    def edit = {
        def bookInstance = Book.get(params.id)
        if (!bookInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'book.label', default: 'Book'), params.id])}"
            redirect(action: "list")
        }
        else {
            return [bookInstance: bookInstance]
        }
    }

    def update = {
        def bookInstance = Book.get(params.id)
        if (bookInstance) {
            if (params.version) {
                def version = params.version.toLong()
                if (bookInstance.version > version) {

                    bookInstance.errors.rejectValue("version", "default.optimistic.locking.failure", [message(code: 'book.label', default: 'Book')] as Object[], "Another user has updated this Book while you were editing")
                    render(view: "edit", model: [bookInstance: bookInstance])
                    return
                }
            }
            bookInstance.properties = params
            if (!bookInstance.hasErrors() && bookInstance.save(flush: true)) {
                flash.message = "${message(code: 'default.updated.message', args: [message(code: 'book.label', default: 'Book'), bookInstance.id])}"
                redirect(action: "show", id: bookInstance.id)
            }
            else {
                render(view: "edit", model: [bookInstance: bookInstance])
            }
        }
        else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'book.label', default: 'Book'), params.id])}"
            redirect(action: "list")
        }
    }

    def delete = {
        def bookInstance = Book.get(params.id)
        if (bookInstance) {
            try {
                bookInstance.delete(flush: true)
                flash.message = "${message(code: 'default.deleted.message', args: [message(code: 'book.label', default: 'Book'), params.id])}"
                redirect(action: "list")
            }
            catch (org.springframework.dao.DataIntegrityViolationException e) {
                flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: 'book.label', default: 'Book'), params.id])}"
                redirect(action: "show", id: params.id)
            }
        }
        else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'book.label', default: 'Book'), params.id])}"
            redirect(action: "list")
        }
    }
}
