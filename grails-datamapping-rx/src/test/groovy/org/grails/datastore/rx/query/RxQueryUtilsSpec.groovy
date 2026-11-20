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
package org.grails.datastore.rx.query

import grails.gorm.rx.collection.RxUnidirectionalCollection
import grails.gorm.rx.proxy.ObservableProxy
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import rx.Observable
import spock.lang.Specification

import jakarta.persistence.FetchType

class RxQueryUtilsSpec extends Specification {

    RxDatastoreClientImplementor datastoreClient = Mock(RxDatastoreClientImplementor)
    EntityReflector entityReflector = Mock(EntityReflector)
    MappingContext mappingContext = Stub(MappingContext)
    PersistentEntity entity = Stub(PersistentEntity)
    QueryState queryState = new QueryState()

    void setup() {
        entity.mappingContext >> mappingContext
        mappingContext.getEntityReflector(entity) >> entityReflector
    }

    void "returns the original observable unchanged when there are no fetch strategies"() {
        given:
        Observable observable = Observable.just(new Object())

        when:
        Observable result = RxQueryUtils.processFetchStrategies(datastoreClient, observable, entity, [:], queryState)

        then:
        result.is(observable)
    }

    void "leaves the emitted item untouched when it is not an instance of the entity"() {
        given:
        entity.isInstance(_) >> false
        TestEntityInstance item = new TestEntityInstance()

        when:
        def result = process(item, [author: FetchType.EAGER])

        then:
        result.is(item)
        0 * datastoreClient._
        0 * entityReflector.setProperty(*_)
    }

    void "eagerly loads a to-one association via the datastore client when it is not foreign-key-in-child"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        PersistentEntity associatedEntity = Stub(PersistentEntity) {
            getJavaClass() >> Author
        }
        ObservableProxy currentValue = Stub(ObservableProxy) {
            getProxyKey() >> 42L
        }
        ToOne property = Stub(ToOne) {
            getName() >> 'author'
            isEmbedded() >> false
            isForeignKeyInChild() >> false
            getAssociatedEntity() >> associatedEntity
        }
        entity.getPropertyByName('author') >> property
        entityReflector.getProperty(item, 'author') >> currentValue
        Author loadedAuthor = new Author()

        when:
        def result = process(item, [author: FetchType.EAGER])

        then:
        1 * datastoreClient.get(Author, 42L, queryState) >> Observable.just(loadedAuthor)
        1 * entityReflector.setProperty(item, 'author', loadedAuthor)
        result.is(item)
    }

    void "eagerly loads a to-one association through a query when the foreign key is in the child"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        PersistentEntity associatedEntity = Stub(PersistentEntity) {
            getJavaClass() >> Author
        }
        Association inverseSide = Stub(Association) {
            getName() >> 'book'
        }
        ObservableProxy currentValue = Stub(ObservableProxy) {
            getProxyKey() >> 99L
        }
        ToOne property = Stub(ToOne) {
            getName() >> 'author'
            isEmbedded() >> false
            isForeignKeyInChild() >> true
            getAssociatedEntity() >> associatedEntity
            getInverseSide() >> inverseSide
        }
        entity.getPropertyByName('author') >> property
        entityReflector.getProperty(item, 'author') >> currentValue
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        Author loadedAuthor = new Author()

        when:
        def result = process(item, [author: FetchType.EAGER])

        then:
        1 * datastoreClient.createQuery(Author, queryState) >> query
        1 * query.eq('book', item) >> query
        1 * query.max(1) >> query
        1 * query.singleResult() >> Observable.just(loadedAuthor)
        1 * entityReflector.setProperty(item, 'author', loadedAuthor)
        result.is(item)
    }

    void "skips a to-one association that is embedded even when eager and proxied"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        ObservableProxy currentValue = Stub(ObservableProxy)
        ToOne property = Stub(ToOne) {
            getName() >> 'author'
            isEmbedded() >> true
        }
        entity.getPropertyByName('author') >> property
        entityReflector.getProperty(item, 'author') >> currentValue

        when:
        def result = process(item, [author: FetchType.EAGER])

        then:
        result.is(item)
        0 * datastoreClient._
        0 * entityReflector.setProperty(*_)
    }

    void "skips a to-one association whose current value is not an observable proxy"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        ToOne property = Stub(ToOne) {
            getName() >> 'author'
            isEmbedded() >> false
        }
        entity.getPropertyByName('author') >> property
        entityReflector.getProperty(item, 'author') >> 'not-a-proxy'

        when:
        def result = process(item, [author: FetchType.EAGER])

        then:
        result.is(item)
        0 * datastoreClient._
        0 * entityReflector.setProperty(*_)
    }

    void "does not resolve an association when the fetch strategy is not eager"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        ToOne property = Stub(ToOne)
        entity.getPropertyByName('author') >> property

        when:
        def result = process(item, [author: FetchType.LAZY])

        then:
        result.is(item)
        0 * datastoreClient._
        0 * entityReflector.setProperty(*_)
    }

    void "eagerly loads the many side of a bidirectional to-many association and wraps it for dirty checking"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        PersistentEntity ownerEntity = Stub(PersistentEntity) {
            getJavaClass() >> Book
        }
        ManyToOne inverseSide = Stub(ManyToOne) {
            getOwner() >> ownerEntity
            getName() >> 'author'
        }
        ToMany property = Stub(ToMany) {
            getName() >> 'books'
            isBidirectional() >> true
            getInverseSide() >> inverseSide
        }
        entity.getPropertyByName('books') >> property
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        Book bookOne = new Book()
        Book bookTwo = new Book()
        EntityReflector.PropertyWriter propertyWriter = Mock(EntityReflector.PropertyWriter)

        when:
        def result = process(item, [books: FetchType.EAGER])

        then:
        1 * datastoreClient.createQuery(Book, queryState) >> query
        1 * query.eq('author', item) >> query
        1 * query.findAll() >> Observable.just(bookOne, bookTwo)
        1 * entityReflector.getPropertyWriter('books') >> propertyWriter
        1 * propertyWriter.propertyType() >> List
        1 * propertyWriter.write(item, { it.containsAll([bookOne, bookTwo]) })
        result.is(item)
    }

    void "does not add an observable for a bidirectional to-many association whose inverse side is not many-to-one"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        Association inverseSide = Stub(Association) {
            getName() >> 'author'
        }
        ToMany property = Stub(ToMany) {
            getName() >> 'books'
            isBidirectional() >> true
            getInverseSide() >> inverseSide
        }
        entity.getPropertyByName('books') >> property
        entityReflector.getProperty(item, 'books') >> null

        when:
        def result = process(item, [books: FetchType.EAGER])

        then:
        result.is(item)
        0 * datastoreClient._
        0 * entityReflector.setProperty(*_)
        0 * entityReflector.getPropertyWriter(*_)
    }

    void "eagerly loads a unidirectional to-many association by its association keys"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        PersistentProperty identity = Stub(PersistentProperty) {
            getName() >> 'id'
        }
        PersistentEntity associatedEntity = Stub(PersistentEntity) {
            getJavaClass() >> Book
            getIdentity() >> identity
        }
        ToMany property = Stub(ToMany) {
            getName() >> 'books'
            isBidirectional() >> false
            getAssociatedEntity() >> associatedEntity
        }
        entity.getPropertyByName('books') >> property
        RxUnidirectionalCollection currentValue = Stub(RxUnidirectionalCollection) {
            getAssociationKeys() >> [1L, 2L]
        }
        entityReflector.getProperty(item, 'books') >> currentValue
        Query query = Mock(Query, additionalInterfaces: [RxQuery])
        Book bookOne = new Book()
        EntityReflector.PropertyWriter propertyWriter = Mock(EntityReflector.PropertyWriter)

        when:
        def result = process(item, [books: FetchType.EAGER])

        then:
        1 * datastoreClient.createQuery(Book, queryState) >> query
        1 * query.in('id', [1L, 2L]) >> query
        1 * query.findAll() >> Observable.just(bookOne)
        1 * entityReflector.getPropertyWriter('books') >> propertyWriter
        1 * propertyWriter.propertyType() >> List
        1 * propertyWriter.write(item, { it.contains(bookOne) })
        result.is(item)
    }

    void "adds an empty result for a unidirectional to-many association with no association keys"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        ToMany property = Stub(ToMany) {
            getName() >> 'books'
            isBidirectional() >> false
        }
        entity.getPropertyByName('books') >> property
        RxUnidirectionalCollection currentValue = Stub(RxUnidirectionalCollection) {
            getAssociationKeys() >> []
        }
        entityReflector.getProperty(item, 'books') >> currentValue
        EntityReflector.PropertyWriter propertyWriter = Mock(EntityReflector.PropertyWriter)

        when:
        def result = process(item, [books: FetchType.EAGER])

        then:
        0 * datastoreClient.createQuery(*_)
        1 * entityReflector.getPropertyWriter('books') >> propertyWriter
        1 * propertyWriter.propertyType() >> List
        1 * propertyWriter.write(item, [])
        result.is(item)
    }

    void "does not add an observable for a many-to-many association whose current value is not a unidirectional collection"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        ManyToMany property = Stub(ManyToMany) {
            getName() >> 'tags'
            isBidirectional() >> true
        }
        entity.getPropertyByName('tags') >> property
        entityReflector.getProperty(item, 'tags') >> ['a', 'b']

        when:
        def result = process(item, [tags: FetchType.EAGER])

        then:
        result.is(item)
        0 * datastoreClient._
        0 * entityReflector.setProperty(*_)
        0 * entityReflector.getPropertyWriter(*_)
    }

    void "does nothing for an eager fetch strategy configured against a non-association property"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()
        PersistentProperty property = Stub(PersistentProperty) {
            getName() >> 'title'
        }
        entity.getPropertyByName('title') >> property
        entityReflector.getProperty(item, 'title') >> 'some value'

        when:
        def result = process(item, [title: FetchType.EAGER])

        then:
        result.is(item)
        0 * datastoreClient._
        0 * entityReflector.setProperty(*_)
    }

    void "resolves multiple eager associations in fetch-strategy order and applies each to its own property"() {
        given:
        entity.isInstance(_) >> true
        TestEntityInstance item = new TestEntityInstance()

        PersistentEntity authorEntity = Stub(PersistentEntity) {
            getJavaClass() >> Author
        }
        ObservableProxy authorProxy = Stub(ObservableProxy) {
            getProxyKey() >> 1L
        }
        ToOne authorProperty = Stub(ToOne) {
            getName() >> 'author'
            isEmbedded() >> false
            isForeignKeyInChild() >> false
            getAssociatedEntity() >> authorEntity
        }

        PersistentEntity ownerEntity = Stub(PersistentEntity) {
            getJavaClass() >> Book
        }
        ManyToOne inverseSide = Stub(ManyToOne) {
            getOwner() >> ownerEntity
            getName() >> 'publisher'
        }
        ToMany booksProperty = Stub(ToMany) {
            getName() >> 'books'
            isBidirectional() >> true
            getInverseSide() >> inverseSide
        }

        entity.getPropertyByName('author') >> authorProperty
        entity.getPropertyByName('books') >> booksProperty
        entityReflector.getProperty(item, 'author') >> authorProxy
        entityReflector.getProperty(item, 'books') >> null

        Author loadedAuthor = new Author()
        Book loadedBook = new Book()
        Query booksQuery = Mock(Query, additionalInterfaces: [RxQuery])
        EntityReflector.PropertyWriter booksWriter = Mock(EntityReflector.PropertyWriter)

        Map<String, FetchType> fetchStrategies = new LinkedHashMap<>()
        fetchStrategies.author = FetchType.EAGER
        fetchStrategies.books = FetchType.EAGER

        when:
        def result = process(item, fetchStrategies)

        then:
        1 * datastoreClient.get(Author, 1L, queryState) >> Observable.just(loadedAuthor)
        1 * entityReflector.setProperty(item, 'author', loadedAuthor)
        1 * datastoreClient.createQuery(Book, queryState) >> booksQuery
        1 * booksQuery.eq('publisher', item) >> booksQuery
        1 * booksQuery.findAll() >> Observable.just(loadedBook)
        1 * entityReflector.getPropertyWriter('books') >> booksWriter
        1 * booksWriter.propertyType() >> List
        1 * booksWriter.write(item, { it.contains(loadedBook) })
        result.is(item)
    }

    private Object process(Object item, Map<String, FetchType> fetchStrategies) {
        RxQueryUtils.processFetchStrategies(datastoreClient, Observable.just(item), entity, fetchStrategies, queryState)
                .toBlocking()
                .single()
    }

    private static class TestEntityInstance implements DirtyCheckable {
    }

    private static class Author {
    }

    private static class Book {
    }
}
