/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The AS
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The AS licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.simple.engine

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import grails.gorm.multitenancy.Tenants
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SimpleMapEntityPersisterSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(TestEntity, Author, Book)

    def 'test multi-tenancy logical isolation'() {
        given: 'A datastore in DISCRIMINATOR mode'
        SimpleMapDatastore mtDatastore = new SimpleMapDatastore(
                ['grails.gorm.multiTenancy.mode': MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR],
                TestEntity
        )
        def session = mtDatastore.connect()
        def persister = session.getPersister(TestEntity)
        
        when: 'We save in tenant 1'
        def id1
        Tenants.withId(mtDatastore, '1') {
            def entity1 = new TestEntity(name: 'tenant1')
            id1 = persister.persist(entity1)
            session.flush()
        }

        then: 'It is stored in tenant 1'
        id1 != null
        Tenants.withId(mtDatastore, '1') {
            persister.retrieveEntry(persister.persistentEntity, persister.entityFamily, id1) != null
        }

        when: 'We check tenant 2'
        then: 'It is not there'
        Tenants.withId(mtDatastore, '2') {
            persister.retrieveEntry(persister.persistentEntity, persister.entityFamily, id1) == null
        }

        cleanup:
        session.disconnect()
    }

    def 'test store and retrieve entry'() {
        given:
        def session = datastore.connect()
        def persister = session.getPersister(TestEntity)
        def entity = new TestEntity(name: 'test')

        when:
        def id = persister.persist(entity)
        session.flush()
        def family = persister.entityFamily
        def entry = persister.retrieveEntry(persister.persistentEntity, family, id)
        
        then:
        id != null
        entry != null
        entry.name == 'test'
        
        cleanup:
        session.disconnect()
    }

    def 'test property indexing'() {
        given:
        def session = datastore.connect()
        def persister = session.getPersister(TestEntity)
        def entity = new TestEntity(name: 'indexed')

        when: 'entity is persisted'
        persister.persist(entity)
        session.flush()
        def prop = persister.persistentEntity.getPropertyByName('name')
        def indexer = persister.getPropertyIndexer(prop)
        def indexedIds = indexer.query('indexed')

        then: 'index is created'
        indexedIds == [entity.id]

        when: 'entity is updated'
        entity.name = 'updated'
        persister.persist(entity)
        session.flush()
        
        then: 'index is updated'
        indexer.query('indexed') == []
        indexer.query('updated') == [entity.id]
        
        when: 'entity is deleted'
        persister.delete(entity)
        session.flush()
        
        then: 'index is cleared'
        indexer.query('updated') == []

        cleanup:
        session.disconnect()
    }

    def 'test many-to-many association indexing'() {
        given:
        def session = datastore.connect()
        def author = new Author(name: 'Stephen King')
        def book1 = new Book(title: 'The Stand')
        def book2 = new Book(title: 'The Shining')
        
        author.books = [book1, book2] as Set
        book1.authors = [author] as Set
        book2.authors = [author] as Set

        when:
        session.persist(author)
        session.persist(book1)
        session.persist(book2)
        session.flush()

        def authorPersister = session.getPersister(author)
        def authorEntry = authorPersister.retrieveEntry(authorPersister.persistentEntity, authorPersister.entityFamily, author.id)
        
        def bookPersister = session.getPersister(book1)
        def book1Entry = bookPersister.retrieveEntry(bookPersister.persistentEntity, bookPersister.entityFamily, book1.id)

        then:
        authorEntry != null
        authorEntry.books == [book1.id, book2.id]
        
        book1Entry != null
        book1Entry.authors == [author.id]

        cleanup:
        session.disconnect()
    }
}

@Entity
class TestEntity implements grails.gorm.MultiTenant<TestEntity> {

    Long id
    String name
    String tenantId
    static mapping = {
        name index: true
        multiTenancy strategy: 'DISCRIMINATOR'
    }
}

@Entity
class Author {

    Long id
    String name
    Set books
    static hasMany = [books: Book]
}

@Entity
class Book {

    Long id
    String title
    Set authors
    static belongsTo = [Author]
    static hasMany = [authors: Author]
}
