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
package org.grails.datastore.gorm.validation.jakarta

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import spock.lang.Specification

import jakarta.validation.Path
import java.lang.annotation.ElementType

/**
 * Created by graemerocher on 14/02/2017.
 */
class MappingContextTraversableResolverSpec extends Specification {

    void "test isReachable for initialized entity"() {
        given:
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.addPersistentEntities(Book, Author)
        mappingContext.initialize()

        MappingContextTraversableResolver resolver = new MappingContextTraversableResolver(mappingContext)

        Book book = new Book(author: new Author(name: "Stephen King"), title: "The Stand")
        Path.Node node = Mock(Path.Node)
        node.getName() >> "author"
        Path path = Mock(Path)

        expect:
        resolver.isReachable(book, node, Book, path, ElementType.TYPE)
    }

    void "test isReachable for proxy entity"() {
        given:
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.addPersistentEntities(Book, Author)
        mappingContext.initialize()

        MappingContextTraversableResolver resolver = new MappingContextTraversableResolver(mappingContext)
        def session = Mock(Session)
        session.getMappingContext() >> mappingContext

        Book book = mappingContext.proxyFactory.createProxy(session, Book, 1L)
        Path.Node node = Mock(Path.Node)
        node.getName() >> "author"
        Path path = Mock(Path)

        expect:
        !resolver.isReachable(book, node, Book, path, ElementType.TYPE)
    }

    void "test isReachable for proxy association"() {
        given:
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.addPersistentEntities(Book, Author)
        mappingContext.initialize()

        MappingContextTraversableResolver resolver = new MappingContextTraversableResolver(mappingContext)
        def session = Mock(Session)
        session.getMappingContext() >> mappingContext

        Book book = new Book()
        book.author = mappingContext.proxyFactory.createProxy(session, Author, 1L)
        Path.Node node = Mock(Path.Node)
        node.getName() >> "author"
        Path path = Mock(Path)

        expect:
        !resolver.isReachable(book, node, Book, path, ElementType.TYPE)
    }

    void "test isCascadeable for initialized entity non-owning side"() {
        given:
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.addPersistentEntities(Book, Author)
        mappingContext.initialize()

        MappingContextTraversableResolver resolver = new MappingContextTraversableResolver(mappingContext)

        def author = new Author(name: "Stephen King")
        Book book = new Book(author: author, title: "The Stand")
        author.books = new HashSet<>()
        author.books.add(book)

        Path.Node node = Mock(Path.Node)
        node.getName() >> "author"
        Path path = Mock(Path)
        path.iterator() >> [node].iterator()

        expect: "Should not cascade to non-owning side"
        !resolver.isCascadable(book, node, Book, path, ElementType.TYPE)
    }

    void "test isCascadeable for initialized entity owning side"() {
        given:
        MappingContext mappingContext = new KeyValueMappingContext("test")
        mappingContext.addPersistentEntities(Book, Author)
        mappingContext.initialize()

        MappingContextTraversableResolver resolver = new MappingContextTraversableResolver(mappingContext)

        def author = new Author(name: "Stephen King")
        Book book = new Book(author: author, title: "The Stand")
        author.books = new HashSet<>()
        author.books.add(book)

        Path.Node node = Mock(Path.Node)
        node.getName() >> "books"
        Path path = Mock(Path)
        path.iterator() >> [node].iterator()

        expect: "Should not cascade to non-owning side"
        resolver.isCascadable(author, node, Author, path, ElementType.TYPE)
    }
}

@Entity
class Book {
    String title
    Author author

    static belongsTo = [author:Author]
}

@Entity
class Author {
    String name

    static hasMany = [books:Book]
}
