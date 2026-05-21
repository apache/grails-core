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
package org.grails.datastore.gorm.mongo.connections

import grails.gorm.annotation.Entity
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared

class MultipleDataSourceConnectionsSpec extends AutoStartedMongoSpec {

    @Shared
    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                'grails.mongodb.url'        : "mongodb://${mongoHost}:${mongoPort}/grailsDB" as String,
                'grails.mongodb.connections': [
                        'books'    : ['url': "mongodb://${mongoHost}:${mongoPort}/books" as String],
                        'moreBooks': ['url': "mongodb://${mongoHost}:${mongoPort}/moreBooks" as String],
                ],
        ]

        datastore  = new MongoDatastore(DatastoreUtils.createPropertyResolver(config), Book, Author)
    }

    void "Test map to multiple data sources"() {

        when: "The default data source is used"
        int result = Author.withTransaction {
            new Author(name: 'Fred').save(flush: true)
            Author.count()
        }

        then: "The default data source is bound"
        result == 1
        Book.withNewSession { Session s ->
            assert s.datastore.defaultDatabase == 'books'
            return true
        }
        Book.moreBooks.withNewSession { Session s ->
            assert s.datastore.defaultDatabase == 'moreBooks'
            return true
        }
        Author.withNewSession { Author.count() == 1 }
        Author.withNewSession { s ->
            assert s.datastore.defaultDatabase == 'grailsDB'
            return true
        }
        Author.books.withNewSession { Session s ->
            assert s.datastore.defaultDatabase == 'books'
            return true
        }
        Author.moreBooks.withNewSession { Session s ->
            assert s.datastore.defaultDatabase == 'moreBooks'
            return true
        }

        when: "A book is saved"
        Book b = Book.withTransaction {
            new Book(name: "The Stand").save(flush: true)
            Book.first()
        }


        then: "The data was saved correctly"
        b.name == 'The Stand'
        b.dateCreated
        b.lastUpdated


        when: "A new data source is added at runtime"
        datastore.connectionSources.addConnectionSource("yetAnother", ['url': 'mongodb://localhost/yetAnotherDB'])

        then: "The other data sources have not been touched"
        Author.withTransaction { Author.count() } == 1
        Book.withTransaction { Book.count() } == 1
        Author.yetAnother.withNewSession { s ->
            assert s.datastore.defaultDatabase == 'yetAnotherDB'
            return true
        }

        cleanup:
        Author.list()*.delete(flush: true)
        Book.list()*.delete(flush: true)
    }

    void "test @Transactional with connection property to non-default database"() {

        when:
        TestService testService = datastore.getDatastoreForConnection("books").getService(TestService)
        testService.doSomething()

        then:
        noExceptionThrown()
    }
}

@Entity
class Book {
    Long id
    Long version
    String name
    Date dateCreated
    Date lastUpdated

    static mapping = {
        connections(['books', 'moreBooks'])
    }
    static constraints = {
        name blank: false
    }
}

@Entity
class Author {
    Long id
    Long version
    String name

    static mapping = {
        connection ConnectionSource.ALL
    }
    static constraints = {
        name blank: false
    }
}

@Service
@Transactional(connection = "books")
class TestService {

    def doSomething() {}
}



