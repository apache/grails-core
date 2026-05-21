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
package grails.gorm.specs.perf

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import groovy.sql.Sql
import groovy.transform.EqualsAndHashCode
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.AccessType

/**
 * Created by graemerocher on 08/12/16.
 */
@Rollback
class JoinPerfSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Author, Book, BookAuthor)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    void setup() {
        for(i in 0..500) {
            Author a = new Author(name: "Author $i").save()

            for(j in 0..3) {
                new Book(title: "Book $i - $j").save()
            }
            datastore.sessionFactory.currentSession.flush()
            datastore.sessionFactory.currentSession.clear()
        }

        Set<List<Long>> seen = []
        int count = 0
        Random random = new Random()
        while(count < 7000) {
            long authorId = Math.abs(random.nextInt() % 500) + 1
            long bookId = Math.abs(random.nextInt() % 1500) + 1
            if(seen.add([authorId, bookId])) {
                Author a = Author.load(authorId)
                Book b = Book.load(bookId)
                if(a && b) {
                    new BookAuthor(book: b, author: a).save()
                    count++
                }
                if(count % 500 == 0) {
                    datastore.sessionFactory.currentSession.flush()
                    datastore.sessionFactory.currentSession.clear()
                }
            }
        }
        datastore.sessionFactory.currentSession.flush()
        datastore.sessionFactory.currentSession.clear()
    }

    void 'test read performance with join query'() {
        when:
        def authors = Author.findAll().groupBy { it.id }
        def books = Book.findAll().groupBy { it.id }
        datastore.sessionFactory.currentSession.clear()
        long time = System.nanoTime();

        BookAuthor.findAll().size()
        long domainsLoadedAt = System.nanoTime()
        long timeOfDomainClassLoad = domainsLoadedAt - time;

        int itemsLoaded = 0
        new Sql(datastore.connectionSources.defaultConnectionSource.dataSource).eachRow("select author_id, book_id from book_author") { row ->
            assert authors.get(row.author_id)
            assert books.get(row.book_id)
            itemsLoaded++
        }
        long timeOfPlainQuery = System.nanoTime() - domainsLoadedAt;

        println "Loaded BookAuthor domains in ${timeOfDomainClassLoad / 1000000.0}ms while query took ${timeOfPlainQuery / 1000000.0}ms"

        then:"the assertion here doesn't matter much, we're testing perf not logic"
        BookAuthor.count() > 6000
    }
}

@Entity
class Author {
    String name
}
@Entity
class Book {
    String title
}

@Entity
@EqualsAndHashCode(includes = ['book', 'author'])
class BookAuthor implements Serializable{
    Book book
    Author author

    static mapping = {
        id composite:['book', 'author']
        version false
        book accessType: AccessType.FIELD
        author accessType: AccessType.FIELD
    }
}