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
