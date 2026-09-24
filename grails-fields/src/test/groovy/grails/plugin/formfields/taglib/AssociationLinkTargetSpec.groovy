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
package grails.plugin.formfields.taglib

import grails.artefact.Artefact
import grails.plugin.formfields.FormFieldsTagLib
import grails.plugin.formfields.FormFieldsTemplateService
import grails.plugin.formfields.mock.Author
import grails.plugin.formfields.mock.Book
import grails.testing.web.taglib.TagLibUnitTest
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Association links rendered by the fields tags target the controller serving the associated domain
 * class, rather than one assumed to be named after it. The controllers registered here stay registered
 * for the whole specification, so it has one of its own.
 */
class AssociationLinkTargetSpec extends AbstractFormFieldsTagLibSpec implements TagLibUnitTest<FormFieldsTagLib> {

    FormFieldsTemplateService mockFormFieldsTemplateService = Mock(FormFieldsTemplateService)

    def setupSpec() {
        mockDomains(Author, Book)
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, WritersController)
        grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, TitlesController)
    }

    def setup() {
        mockFormFieldsTemplateService.getTemplateFor(_) >> { String name -> name }
        tagLib.formFieldsTemplateService = mockFormFieldsTemplateService
    }

    void 'a displayed to-one association links to the controller serving its domain class'() {
        given:
        def book = new Book(title: 'the title')
        new Author(name: 'Bart Simpson').addToBooks(book)

        when:
        def result = applyTemplate('<f:display bean="book"/>', [book: book])

        then: 'WritersController serves Author, and no AuthorController exists'
        result.contains('<a href="/writers/show">Bart Simpson</a>')
    }

    void 'a displayed to-many association links each item to the controller serving its domain class'() {
        given:
        def author = new Author(name: 'Bart Simpson')
        author.addToBooks(new Book(title: 'book 1'))

        when:
        def result = applyTemplate('<f:display bean="author"/>', [author: author])

        then: 'TitlesController serves Book, and no BookController exists'
        result.contains('<li><a href="/titles/show">book 1</a></li>')
    }

    void 'a one-to-many input links each item, and its add link, to the controller serving its domain class'() {
        given:
        messageSource.addMessage('default.add.label', request.locale, 'Add {0}')
        def author = new Author(name: 'Bart Simpson')
        def book = new Book(title: 'book 1')
        author.addToBooks(book)
        author.save(flush: true, failOnError: true)
        PersistentEntity authorEntity = grailsApplication.mappingContext.getPersistentEntity(Author.name)

        when:
        def output = tagLib.renderDefaultInput([bean: author, beanClass: authorEntity, type: Set, property: 'books',
                constraints: null, persistentProperty: authorEntity.getPropertyByName('books'), value: author.books])

        then:
        output.contains("<a href=\"/titles/show/${book.id}\">book 1</a>")
        output.contains("<a href=\"/titles/create?author.id=${author.id}\">Add Book</a>")
    }
}

/**
 * Stands in for a generic REST controller base class, which declares the domain class a controller serves.
 */
abstract class ServingControllerBase<T> {
}

@Artefact('Controller')
class WritersController extends ServingControllerBase<Author> {
    def show() {}
    def create() {}
}

@Artefact('Controller')
class TitlesController extends ServingControllerBase<Book> {
    def show() {}
    def create() {}
}
