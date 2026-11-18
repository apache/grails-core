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
package org.apache.grails.data.simple.querytail

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.springframework.dao.InvalidDataAccessResourceUsageException
import spock.lang.Specification
import spock.lang.TempDir

/**
 * What the simple datastore records for query-tail, through GORM as an application calls it, read back
 * from the capture file.
 */
class SimpleMapQueryTailSpec extends Specification {

    static final String SECRET = 'example-secret'

    /** A secret shaped like a property name, which GORM accepts where it expects one. */
    static final String NAME_SECRET = 'exampleSecret'

    static final String BOOK = QueryTailShelfBook.name

    static final String AUTHOR = QueryTailShelfAuthor.name

    static final String EDITION = QueryTailShelfEdition.name

    @TempDir
    File tempDir

    List<SimpleMapDatastore> datastores = []

    void cleanup() {
        datastores*.close()
    }

    void "a datastore built without the setting carries no capture and writes no file"() {
        given:
        File file = new File(tempDir, 'capture.log')

        when:
        SimpleMapDatastore datastore = open([(QueryTailSettings.SETTING_FILE): file.path])
        inSession(datastore) { new QueryTailShelfBook(title: SECRET, pages: 1).save(flush: true) }

        then:
        datastore.queryTail == null
        !file.exists()
    }

    void "the capture opens with the datastore and closes with it, under the name of its connection source"() {
        given:
        File file = new File(tempDir, 'capture.log')

        when:
        SimpleMapDatastore datastore = open(enabled(file))

        then:
        datastore.queryTail.dataSourceName == ConnectionSource.DEFAULT
        datastore.queryTail.capture.open
        file.exists()

        when:
        datastore.close()

        then:
        !datastore.queryTail.capture.open
    }

    void "each connection source records under its own name, and closes with the datastore"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(enabled(file)),
                ['secondary'], QueryTailShelfBook, QueryTailShelfAuthor, QueryTailShelfEdition)
        datastores << datastore
        SimpleMapDatastore secondary = (SimpleMapDatastore) datastore.getDatastoreForConnection('secondary')

        when:
        Session session = secondary.connect()
        try {
            session.retrieve(QueryTailShelfBook, 1L)
        }
        finally {
            session.disconnect()
        }

        then:
        records(file).find { it.sql == "select * from ${BOOK} where id = ?".toString() }?.dataSource == 'secondary'
        secondary.queryTail.capture.open

        when:
        datastore.close()

        then:
        !secondary.queryTail.capture.open
        !datastore.queryTail.capture.open
    }

    void "a datastore that fails to build opens no capture, neither its own nor that of another of its connection sources"() {
        given:
        File file = new File(tempDir, 'capture.log')

        when:
        new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(enabled(file)), ['secondary'], QueryTailShelfBook) {
            @Override
            protected GormEnhancer initialize(ConnectionSourceSettings settings) {
                throw new IllegalStateException('cannot be built')
            }
        }

        then:
        thrown(IllegalStateException)
        !file.exists()

        when: 'the next datastore is built'
        SimpleMapDatastore datastore = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(enabled(file)), ['secondary'],
                QueryTailShelfBook)
        datastores << datastore

        then: 'it opens the captures of both, in a run of its own'
        datastore.queryTail.capture.open
        ((SimpleMapDatastore) datastore.getDatastoreForConnection('secondary')).queryTail.capture.open
        QueryTailRecord.parseHeader(file.readLines().first()) != null
    }

    void "a connection source added at runtime captures as the application does, and closes its capture with the datastore"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))

        when:
        datastore.addTenantForSchema('added')
        SimpleMapDatastore added = (SimpleMapDatastore) datastore.getDatastoreForConnection('added')
        Session session = added.connect()
        try {
            session.retrieve(QueryTailShelfBook, 1L)
        }
        finally {
            session.disconnect()
        }

        then:
        added.queryTail.file == file
        added.queryTail.capture.open
        records(file).find { it.sql == "select * from ${BOOK} where id = ?".toString() }?.dataSource == 'added'

        when:
        datastore.close()

        then:
        !added.queryTail.capture.open
    }

    void "a connection source added at runtime with query-tail settings of its own takes those"() {
        given:
        File file = new File(tempDir, 'capture.log')
        File own = new File(tempDir, 'own-capture.log')
        SimpleMapDatastore datastore = open(enabled(file))

        when:
        ConnectionSource<Map<String, Map>, ConnectionSourceSettings> off = datastore.connectionSources.addConnectionSource('off',
                [(QueryTailSettings.SETTING_ENABLED): 'false'])
        ConnectionSource<Map<String, Map>, ConnectionSourceSettings> elsewhere = datastore.connectionSources.addConnectionSource('elsewhere',
                [(QueryTailSettings.SETTING_ENABLED): 'true', (QueryTailSettings.SETTING_FILE): own.path])

        then:
        !off.settings.queryTail.enabled
        elsewhere.settings.queryTail.enabled
        elsewhere.settings.queryTail.file == own.path
    }

    void "a datastore built without the setting adds connection sources at runtime that capture nothing"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open([(QueryTailSettings.SETTING_FILE): file.path])

        when:
        datastore.addTenantForSchema('added')

        then:
        ((SimpleMapDatastore) datastore.getDatastoreForConnection('added')).queryTail == null
        !file.exists()
    }

    void "a dynamic finder is one statement with its criteria, order and paging, and none of its values"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))
        inSession(datastore) {
            (1..3).each { new QueryTailShelfBook(title: SECRET, pages: it * 10).save() }
        }

        when:
        List<String> statements = recorded(file) {
            inSession(datastore) {
                QueryTailShelfBook.findAllByTitleAndPagesGreaterThan(SECRET, 5, [sort: 'pages', order: 'desc', max: 2, offset: 1])
            }
        }

        then:
        statements == ["select * from ${BOOK} where title = ? and pages > ? order by pages desc limit ? offset ?".toString()]
        records(file).every { it.document }
        !file.text.contains(SECRET)
    }

    void "criteria are recorded with their junctions, associations, comparisons and projections"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))
        inSession(datastore) {
            QueryTailShelfAuthor author = new QueryTailShelfAuthor(name: SECRET).save()
            new QueryTailShelfBook(title: SECRET, pages: 5, author: author).save()
        }

        when:
        List<String> statements = recorded(file) {
            inSession(datastore) {
                QueryTailShelfBook.withCriteria {
                    or {
                        eq 'title', SECRET
                        ilike 'title', "%${SECRET}%"
                    }
                    author {
                        eq 'name', SECRET
                    }
                    between 'pages', 1, 9
                    isNotNull 'title'
                    not {
                        inList 'id', [7L, 8L]
                    }
                    projections {
                        count()
                    }
                }
            }
        }

        then:
        statements == ["select count(*) from ${BOOK} where (title = ? or title ilike ?) and author.name = ? " +
                "and pages between ? and ? and title is not null and not (id in (?))"].collect { it.toString() }
        !file.text.contains(SECRET)
    }

    void "a subquery is part of the statement that runs it, and is not recorded on its own"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))
        inSession(datastore) {
            new QueryTailShelfBook(title: SECRET, pages: 5).save()
            new QueryTailShelfBook(title: 'other', pages: 50).save()
        }

        when:
        List<String> statements = recorded(file) {
            inSession(datastore) {
                QueryTailShelfBook.withCriteria {
                    gtAll 'pages', new DetachedCriteria(QueryTailShelfBook).build { eq 'title', SECRET }.property('pages')
                }*.title
            }
        }

        then:
        statements == ["select * from ${BOOK} where pages > all (select pages from ${BOOK} where title = ?)".toString()]
        !file.text.contains(SECRET)
    }

    void "a name the mapping does not know is recorded as ?, and so is every segment of a path past it"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))

        when: 'a sort taken from a request, which GORM lets through when it is shaped like an alias path'
        List<String> sorted = recorded(file) {
            inSession(datastore) {
                QueryTailShelfBook.list(sort: "${NAME_SECRET}.${NAME_SECRET}", order: 'desc')
            }
        }

        then:
        sorted == ["select * from ${BOOK} order by ?.? desc".toString()]

        when: 'a query on names the mapping does not know, past a property and past an association, which the datastore rejects as it runs'
        inSession(datastore) {
            datastore.currentSession.createQuery(QueryTailShelfBook)
                    .isNull("title.${NAME_SECRET}")
                    .order(Query.Order.asc("author.${NAME_SECRET}"))
                    .list()
        }

        then: 'it is recorded as it is tried'
        thrown(InvalidDataAccessResourceUsageException)
        records(file).last().sql == "select * from ${BOOK} where title.? is null order by author.? asc".toString()

        when:
        inSession(datastore) {
            datastore.currentSession.createQuery(QueryTailShelfBook).eq(SECRET, 'x').list()
        }

        then:
        thrown(InvalidDataAccessResourceUsageException)
        records(file).last().sql == "select * from ${BOOK} where ? = ?".toString()
        !file.text.contains(SECRET)
        !file.text.contains(NAME_SECRET)
    }

    void "a query loads its rows as one read, and a lazy association each row loads later is a read of its own"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))
        inSession(datastore) {
            (1..3).each {
                new QueryTailShelfBook(title: "title-${it}", pages: it, author: new QueryTailShelfAuthor(name: "author-${it}").save()).save()
            }
        }

        when:
        List<String> statements = recorded(file) {
            inSession(datastore) {
                QueryTailShelfBook.list()*.author*.name
            }
        }

        then: 'the N+1 of loading each author when it is first used'
        statements == ["select * from ${BOOK}".toString()] + ["select * from ${AUTHOR} where id = ?".toString()] * 3
    }

    void "an entry read by its key is recorded when it is not in the session, once"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))
        Long id = inSession(datastore) { new QueryTailShelfBook(title: SECRET, pages: 1).save().id }

        when:
        List<String> statements = recorded(file) {
            inSession(datastore) {
                QueryTailShelfBook.get(id)
                QueryTailShelfBook.get(id)
            }
        }

        then:
        statements == ["select * from ${BOOK} where id = ?".toString()]
    }

    void "an association an entry loads eagerly is part of the read that loads the entry, by its key or by a query"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))
        Long id = inSession(datastore) {
            new QueryTailShelfEdition(label: 'first', book: new QueryTailShelfBook(title: 'eager', pages: 1).save()).save().id
        }

        when:
        List<String> got = recorded(file) {
            inSession(datastore) { QueryTailShelfEdition.get(id).book.title }
        }
        List<String> listed = recorded(file) {
            inSession(datastore) { QueryTailShelfEdition.list()*.book*.title }
        }

        then:
        got == ["select * from ${EDITION} where id = ?".toString()]
        listed == ["select * from ${EDITION}".toString()]
    }

    void "entries read by their keys together are one read, whether getAll, an array of keys or a lazy collection reads them"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))
        List<Long> ids = inSession(datastore) {
            QueryTailShelfAuthor author = new QueryTailShelfAuthor(name: SECRET)
            (1..3).each { author.addToBooks(new QueryTailShelfBook(title: "title-${it}", pages: it)) }
            author.save()
            [author.id] + author.books*.id
        }

        when:
        List<String> fetched = recorded(file) {
            inSession(datastore) {
                QueryTailShelfBook.getAll(ids.tail())
            }
        }
        List<String> arrayed = recorded(file) {
            inSession(datastore) {
                datastore.currentSession.getPersister(QueryTailShelfBook).retrieveAll(ids.tail() as Serializable[])
            }
        }
        List<String> loaded = recorded(file) {
            inSession(datastore) {
                QueryTailShelfAuthor.get(ids.head()).books*.title
            }
        }

        then:
        fetched == ["select * from ${BOOK} where id in (?)".toString()]
        arrayed == fetched
        loaded == ["select * from ${AUTHOR} where id = ?".toString(), "select * from ${BOOK} where id in (?)".toString()]
    }

    void "each insert, update and delete is recorded, and an entry a write reads is part of it"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SimpleMapDatastore datastore = open(enabled(file))

        when:
        List<String> inserted = recorded(file) {
            inSession(datastore) { new QueryTailShelfBook(title: SECRET, pages: 1).save(flush: true) }
        }
        QueryTailShelfBook detached = inSession(datastore) { QueryTailShelfBook.findByTitle(SECRET) }
        List<String> updated = recorded(file) {
            inSession(datastore) {
                detached.pages = 2
                detached.save(flush: true)
            }
        }
        List<String> deleted = recorded(file) {
            inSession(datastore) { QueryTailShelfBook.get(detached.id).delete(flush: true) }
        }

        then:
        inserted == ["insert into ${BOOK}".toString()]
        updated == ["update ${BOOK} where id = ?".toString()]
        deleted == ["select * from ${BOOK} where id = ?".toString(), "delete from ${BOOK} where id = ?".toString()]
        !file.text.contains(SECRET)
    }

    private SimpleMapDatastore open(Map config) {
        SimpleMapDatastore datastore = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(config),
                new DefaultApplicationEventPublisher(), QueryTailShelfBook, QueryTailShelfAuthor, QueryTailShelfEdition)
        datastores << datastore
        datastore
    }

    private static Map enabled(File file) {
        [(QueryTailSettings.SETTING_ENABLED): 'true', (QueryTailSettings.SETTING_FILE): file.path]
    }

    private static <T> T inSession(SimpleMapDatastore datastore, Closure<T> work) {
        Session session = datastore.connect()
        DatastoreUtils.bindSession(session)
        try {
            T result = work.call()
            session.flush()
            result
        }
        finally {
            DatastoreUtils.unbindSession(session)
            session.disconnect()
        }
    }

    /**
     * @return the statements recorded while the work ran
     */
    private static List<String> recorded(File file, Closure work) {
        int before = records(file).size()
        work.call()
        records(file).drop(before)*.sql
    }

    private static List<QueryTailRecord> records(File file) {
        file.exists() ? file.readLines().collect { QueryTailRecord.parse(it) }.findAll { it != null } : []
    }
}

@Entity
class QueryTailShelfBook {
    Long id
    String title
    Integer pages
    QueryTailShelfAuthor author

    static constraints = {
        author nullable: true
    }
}

@Entity
class QueryTailShelfAuthor {
    Long id
    String name

    static hasMany = [books: QueryTailShelfBook]
}

@Entity
class QueryTailShelfEdition {
    Long id
    String label
    QueryTailShelfBook book

    static mapping = {
        book fetch: 'join'
    }
}
