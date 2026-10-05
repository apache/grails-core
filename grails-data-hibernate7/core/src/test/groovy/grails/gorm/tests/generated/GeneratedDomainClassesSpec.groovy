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
package grails.gorm.tests.generated

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.Hibernate
import spock.lang.PendingFeature
import org.hibernate.proxy.HibernateProxy
import org.hibernate.persister.entity.EntityPersister

import org.grails.orm.hibernate.cfg.domainbinding.jpa.GrailsDomainGenerator

/**
 * Boots the datastore through the generated-domain-class path and checks that Hibernate binds classes generated from the
 * GORM mapping while the application's real instances are what is persisted and loaded: identity of instances, entity
 * resolution, CRUD, associations, lazy loading, proxies, dirty checking, queries (finders, HQL, criteria, where, query by
 * example), versioning, inheritance, embedded objects, enums and the schema.
 */
class GeneratedDomainClassesSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        registerGeneratedDomainClasses(GdcAuthor, GdcBook, GdcNovel)
    }

    private GdcBook savedBook(String title = 'Dune', GdcAuthor author = new GdcAuthor(name: 'Herbert').save(flush: true)) {
        return new GdcBook(title: title, author: author, pages: 500).save(flush: true)
    }

    def "Hibernate binds the generated classes, not the domain classes"() {
        when:
        EntityPersister persister = sessionFactory.mappingMetamodel.getEntityDescriptor(GdcBook)

        then: 'the entity is the generated class; the class Hibernate instantiates is the real one'
        persister.entityName == GrailsDomainGenerator.GENERATED_PACKAGE + '.' + GdcBook.name.replace('.', '_')
        persister.mappedClass == GdcBook
    }

    def "the entity name of a real instance is resolved and the JPA metamodel is typed with the real class"() {
        given:
        GdcBook book = savedBook()

        expect:
        sessionFactory.currentSession.getEntityName(book).endsWith('GdcBook')
        sessionFactory.metamodel.entity(GdcBook).javaType == GdcBook
        sessionFactory.metamodel.entity(GdcNovel).javaType == GdcNovel
    }

    def "a real instance is saved and loaded as an instance of the real class"() {
        given:
        GdcBook saved = savedBook()
        session.clear()

        when:
        GdcBook loaded = GdcBook.get(saved.id)

        then:
        loaded.getClass() == GdcBook
        loaded.title == 'Dune'
        loaded.pages == 500
        !loaded.is(saved)
    }

    def "statistics are kept for the entity under the generated entity name"() {
        given:
        sessionFactory.statistics.statisticsEnabled = true
        GdcBook book = savedBook()
        String entityName = sessionFactory.mappingMetamodel.getEntityDescriptor(GdcBook).entityName

        expect:
        sessionFactory.statistics.getEntityStatistics(entityName).insertCount == 1
    }

    @PendingFeature(reason = 'Hibernate keys statistics, second-level cache regions and entity graphs by entity name, which is the name of the generated class, not the domain class name')
    def "statistics are available under the domain class name"() {
        given:
        sessionFactory.statistics.statisticsEnabled = true
        savedBook()

        expect:
        sessionFactory.statistics.getEntityStatistics(GdcBook.name).insertCount == 1
    }

    def "the id is generated and the instance is attached to the session it was saved in"() {
        when:
        GdcBook book = savedBook()

        then:
        book.id != null
        book.version == 0
        GdcBook.get(book.id).is(book)
    }

    def "update, delete and count work on real instances"() {
        given:
        GdcBook book = savedBook()

        when:
        book.title = 'Dune Messiah'
        book.save(flush: true)
        session.clear()

        then:
        GdcBook.get(book.id).title == 'Dune Messiah'
        GdcBook.count() == 1

        when:
        GdcBook.get(book.id).delete(flush: true)

        then:
        GdcBook.count() == 0
        GdcBook.get(book.id) == null
    }

    def "the schema is created from the generated mapping"() {
        when:
        List tables = sessionFactory.currentSession
                .createNativeQuery("select table_name from information_schema.tables where table_schema = 'PUBLIC'", String)
                .list()*.toUpperCase()
        List columns = sessionFactory.currentSession
                .createNativeQuery("select column_name from information_schema.columns where table_name = 'GDC_BOOK'", String)
                .list()*.toUpperCase()

        then:
        tables.containsAll(['GDC_AUTHOR', 'GDC_BOOK'])
        columns.containsAll(['ID', 'VERSION', 'TITLE', 'STATUS', 'PAGES', 'AUTHOR_ID', 'SIZE_WIDTH', 'SIZE_HEIGHT', 'CHAPTERS', 'CLASS'])
    }

    def "a decimal column keeps the scale of its type"() {
        when:
        List scales = sessionFactory.currentSession
                .createNativeQuery("select numeric_scale from information_schema.columns where table_name = 'GDC_BOOK' and column_name = 'PRICE'", Integer)
                .list()

        then:
        scales == [2]
    }

    def "a hasMany association cascades and loads lazily"() {
        given:
        GdcAuthor author = new GdcAuthor(name: 'Herbert')
        author.addToBooks(new GdcBook(title: 'Dune'))
        author.addToBooks(new GdcBook(title: 'Children of Dune'))
        author.save(flush: true)
        session.clear()

        when:
        GdcAuthor loaded = GdcAuthor.get(author.id)

        then: 'the collection is a lazy persistent collection of real instances'
        loaded.getClass() == GdcAuthor
        !Hibernate.isInitialized(loaded.books)

        when:
        Set titles = loaded.books*.title as Set

        then:
        Hibernate.isInitialized(loaded.books)
        titles == ['Dune', 'Children of Dune'] as Set
        loaded.books.every { it.getClass() == GdcBook }
        loaded.books.every { it.author.is(loaded) }
    }

    def "a to-one association is a lazy proxy of the real class"() {
        given:
        GdcBook saved = savedBook()
        session.clear()

        when:
        GdcBook book = GdcBook.get(saved.id)
        GdcAuthor author = book.author

        then:
        author instanceof HibernateProxy
        author instanceof GdcAuthor
        !Hibernate.isInitialized(author)
        Hibernate.getClass(author) == GdcAuthor

        when:
        String name = author.name

        then:
        name == 'Herbert'
        Hibernate.isInitialized(author)
    }

    def "a proxy for a real class is created by id"() {
        given:
        GdcBook saved = savedBook()
        session.clear()

        when:
        GdcBook proxy = GdcBook.proxy(saved.id)

        then:
        proxy instanceof HibernateProxy
        proxy.id == saved.id
        proxy.title == 'Dune'
    }

    def "cascading delete follows belongsTo"() {
        given:
        GdcAuthor author = new GdcAuthor(name: 'Herbert')
        author.addToBooks(new GdcBook(title: 'Dune'))
        author.save(flush: true)

        when:
        author.delete(flush: true)

        then:
        GdcBook.count() == 0
        GdcAuthor.count() == 0
    }

    def "dirty checking tracks changes of real instances"() {
        given:
        GdcBook book = savedBook()

        expect:
        !book.isDirty()

        when:
        book.title = 'Changed'

        then:
        book.isDirty()
        book.isDirty('title')
        book.dirtyPropertyNames == ['title']
        book.getPersistentValue('title') == 'Dune'

        when:
        book.save(flush: true)

        then:
        !book.isDirty()
    }

    def "a loaded instance is not dirty"() {
        given:
        GdcBook saved = savedBook()
        session.clear()

        when:
        GdcBook loaded = GdcBook.get(saved.id)

        then:
        !loaded.isDirty()
    }

    def "the version is incremented by an update"() {
        given:
        GdcBook book = savedBook()

        when:
        book.title = 'Changed'
        book.save(flush: true)

        then:
        book.version == 1
    }

    def "an update of a stale version is rejected"() {
        given:
        GdcBook book = savedBook()
        sessionFactory.currentSession.createMutationQuery('update GdcBook set title = :t, version = version + 1').setParameter('t', 'Other').executeUpdate()

        when:
        book.title = 'Mine'
        book.save(flush: true)

        then:
        thrown(org.springframework.dao.OptimisticLockingFailureException)
    }

    def "dynamic finders, where queries and criteria return real instances"() {
        given:
        GdcBook saved = savedBook()
        session.clear()

        expect:
        GdcBook.findByTitle('Dune').id == saved.id
        GdcBook.findByTitle('Dune').getClass() == GdcBook
        GdcBook.findAllByPagesGreaterThan(100)*.title == ['Dune']
        GdcBook.countByTitle('Dune') == 1
        GdcBook.where { title == 'Dune' }.list()*.id == [saved.id]
        GdcBook.createCriteria().list { eq('title', 'Dune') }*.id == [saved.id]
        GdcBook.createCriteria().get { eq('title', 'Dune') }.getClass() == GdcBook
        GdcBook.createCriteria().list { projections { property('title') } } == ['Dune']
        GdcBook.createCriteria().count { gt('pages', 100) } == 1
        GdcBook.createCriteria().list { author { eq('name', 'Herbert') } }*.id == [saved.id]
    }

    def "HQL addresses the entity by its simple name and by the domain class name, and returns real instances"() {
        given:
        GdcBook saved = savedBook()
        session.clear()

        expect:
        GdcBook.executeQuery('from GdcBook where title = :t', [t: 'Dune'])*.id == [saved.id]
        GdcBook.executeQuery('from ' + GdcBook.name + ' b where b.pages > 100')*.id == [saved.id]
        GdcBook.executeQuery('select b.author.name from GdcBook b') == ['Herbert']
        GdcBook.find('from GdcBook b where b.title = ?1', ['Dune']).getClass() == GdcBook
        GdcBook.findAll('from GdcBook')*.id == [saved.id]
        GdcBook.executeUpdate('update GdcBook set pages = 10') == 1
    }

    def "query by example finds real instances"() {
        given:
        GdcBook saved = savedBook()
        session.clear()

        expect:
        GdcBook.find(new GdcBook(title: 'Dune')).id == saved.id
        GdcBook.findAll(new GdcBook(title: 'Dune'))*.id == [saved.id]
    }

    def "inheritance is polymorphic and every instance is of its real class"() {
        given:
        GdcAuthor author = new GdcAuthor(name: 'Herbert').save(flush: true)
        new GdcBook(title: 'Essay', author: author).save(flush: true)
        new GdcNovel(title: 'Dune', author: author, chapters: 40).save(flush: true)
        session.clear()

        expect:
        GdcBook.count() == 2
        GdcNovel.count() == 1
        GdcBook.list(sort: 'title')*.getClass() == [GdcNovel, GdcBook]
        GdcBook.findByTitle('Dune') instanceof GdcNovel
        GdcNovel.findByTitle('Dune').chapters == 40
        GdcBook.executeQuery('from GdcNovel')*.title == ['Dune']
    }

    def "embedded objects and enums round trip"() {
        given:
        GdcBook saved = new GdcBook(title: 'Dune', status: GdcStatus.PUBLISHED, size: new GdcDimensions(width: 10, height: 20))
                .save(flush: true)
        session.clear()

        when:
        GdcBook loaded = GdcBook.get(saved.id)

        then:
        loaded.size.getClass() == GdcDimensions
        loaded.size.width == 10
        loaded.size.height == 20
        loaded.status == GdcStatus.PUBLISHED
        GdcBook.findAllByStatus(GdcStatus.PUBLISHED)*.id == [saved.id]
        GdcBook.createCriteria().list { eq('status', GdcStatus.DRAFT) } == []
    }

    def "a saved association is stored as a foreign key"() {
        given:
        GdcBook saved = savedBook()

        when:
        Object authorId = sessionFactory.currentSession
                .createNativeQuery('select author_id from gdc_book where id = :id', Long)
                .setParameter('id', saved.id).singleResult

        then:
        authorId == saved.author.id
    }
}

enum GdcStatus { DRAFT, PUBLISHED }

class GdcDimensions {
    Integer width
    Integer height
}

@Entity
class GdcAuthor {
    String name
    static hasMany = [books: GdcBook]
}

@Entity
class GdcBook {
    String title
    GdcStatus status = GdcStatus.DRAFT
    GdcDimensions size
    Integer pages
    BigDecimal price
    static belongsTo = [author: GdcAuthor]
    static embedded = ['size']
    static constraints = {
        author nullable: true
        size nullable: true
        pages nullable: true
        price nullable: true
    }
}

@Entity
class GdcNovel extends GdcBook {
    Integer chapters
    static constraints = {
        chapters nullable: true
    }
}
