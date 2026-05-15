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
package org.grails.orm.hibernate.connections

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.hibernate.dialect.H2Dialect
import spock.lang.Issue

/**
 * Created by graemerocher on 20/02/2017.*/
class MultipleDataSourcesWithEventsSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([EventsBook, SecondaryBook])
        manager.grailsConfig = [
                'dataSource': [
                        'url'        : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                        'dbCreate'   : 'update',
                        'dialect'    : H2Dialect.name,
                        'formatSql'  : 'true'
                ],
                'dataSources': [
                        'books': [
                                'url'        : "jdbc:h2:mem:books;LOCK_TIMEOUT=10000",
                                'dbCreate'   : 'update',
                                'dialect'    : H2Dialect.name,
                                'formatSql'  : 'true'
                        ]
                ],
                'hibernate': [
                        'flush.mode'  : 'COMMIT',
                        'cache.queries': 'true',
                        'cache'       : ['use_second_level_cache': true, 'region.factory_class': 'org.hibernate.cache.jcache.internal.JCacheRegionFactory'],
                        'hbm2ddl.auto': 'create-drop'
                ]
        ]
    }

    @Issue('https://github.com/grails/grails-core/issues/10451')
    void "Test multiple data sources register the correct events"() {
        given: "A configuration for multiple data sources"

        when: "A entity is saved with the default connection"
        EventsBook book = new EventsBook(name: "test")
        EventsBook.withTransaction {
            book.save(flush: true)
            book.discard()
            book = EventsBook.get(book.id)
        }

        then: "The events were triggered"
        book != null
        book.name == 'TEST'
        book.time.startsWith("Time: ")

        when: "A entity is saved with a secondary connection connection"
        EventsBook book2 = new EventsBook(name: "test2")
        EventsBook.books.withTransaction {
            book2.books.save(flush: true)
            book2.books.discard()
            book2 = EventsBook.books.get(book2.id)
        }

        then: "The events were triggered"
        book2 != null
        book2.name == 'TEST2'
        book2.time.startsWith("Time: ")

        when: "An entity is saved that uses only a secondary datasource"
        SecondaryBook book3 = new SecondaryBook(name: "test3")
        SecondaryBook.books.withTransaction {
            book3.books.save(flush: true)
            book3.books.discard()
            book3 = SecondaryBook.books.get(book3.id)
        }

        then: "The events were triggered"
        book3 != null
        book3.name == 'TEST3'
        book3.time.startsWith("Time: ")
    }
}

@Entity
class SecondaryBook {
    String time
    String name

    def beforeValidate() {
        time = "Time: ${System.currentTimeMillis()}"
    }

    def beforeInsert() {
        name = name.toUpperCase()
    }

    static mapping = {
        datasource "books"
    }
}

@Entity
class EventsBook {
    String time
    String name

    def beforeValidate() {
        time = "Time: ${System.currentTimeMillis()}"
    }

    def beforeInsert() {
        name = name.toUpperCase()
    }

    static mapping = {
        datasource ConnectionSource.ALL
    }
}
