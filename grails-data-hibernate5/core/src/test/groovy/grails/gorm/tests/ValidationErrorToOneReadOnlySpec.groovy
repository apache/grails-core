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
package grails.gorm.tests

import org.hibernate.Session
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import org.springframework.transaction.PlatformTransactionManager

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.proxy.HibernateProxyHandler

@Rollback
class ValidationErrorToOneReadOnlySpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(
            ReadOnlyOnErrorAuthor,
            ReadOnlyOnErrorBook
    )

    @Shared
    @SuppressWarnings('unused') // Read by the code @Rollback generates
    PlatformTransactionManager transactionManager = datastore.transactionManager

    void 'a failed save marks the to-one association read-only so its pending changes are not flushed'() {
        given: 'a persisted author with an unsaved change'
            def author = new ReadOnlyOnErrorAuthor(name: 'Original').save(flush: true)
            author.name = 'Changed'

        when: 'a book referencing the author fails validation'
            def book = new ReadOnlyOnErrorBook(title: '', author: author)
            def result = book.save()

        then:
            result == null
            book.hasErrors()
            ReadOnlyOnErrorBook.withSession { Session session -> session.isReadOnly(author) }

        when: 'the session is flushed explicitly and the author reloaded'
            ReadOnlyOnErrorBook.withSession { Session session ->
                session.flush()
                session.clear()
            }

        then: 'the change to the author was not written'
            ReadOnlyOnErrorAuthor.get(author.id).name == 'Original'
    }

    void 'a failed save leaves an uninitialized to-one proxy uninitialized'() {
        given: 'a book whose author is loaded as an uninitialized proxy'
            def authorId = new ReadOnlyOnErrorAuthor(name: 'Original').save(flush: true).id
            ReadOnlyOnErrorBook.withSession { Session session -> session.clear() }
            def author = ReadOnlyOnErrorAuthor.load(authorId)
            def proxyHandler = new HibernateProxyHandler()

        expect:
            !proxyHandler.isInitialized(author)

        when: 'the book fails validation'
            def book = new ReadOnlyOnErrorBook(title: '', author: author)
            def result = book.save()

        then: 'the proxy was not loaded to mark it read-only'
            result == null
            book.hasErrors()
            !proxyHandler.isInitialized(author)
    }
}

@Entity
class ReadOnlyOnErrorAuthor {

    String name
}

@Entity
class ReadOnlyOnErrorBook {

    String title
    ReadOnlyOnErrorAuthor author

    static constraints = {
        title(blank: false)
    }
}
