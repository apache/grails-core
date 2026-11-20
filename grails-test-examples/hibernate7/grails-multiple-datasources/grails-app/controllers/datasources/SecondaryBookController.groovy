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

package datasources

import ds2.Book
import org.hibernate.Session

class SecondaryBookController {

    def withSessionTest() {
        boolean sessionObtained = false
        Book.secondary.withSession { Session session ->
            sessionObtained = session != null
        }
        render "sessionObtained:${sessionObtained}"
    }

    def crudViaWithSession() {
        Book.withTransaction {
            new Book(title: 'OSIV Test').save(flush: true)
        }
        int count = 0
        Book.secondary.withSession { Session session ->
            count = Book.count()
        }
        render "count:${count}"
    }

    def validateCommandObject() {
        Book book = new Book()
        boolean validated = false
        Book.secondary.withSession { Session session ->
            validated = true
            book.validate()
        }
        render "validated:${validated},hasErrors:${book.hasErrors()}"
    }

    def sessionAfterExecuteUpdate() {
        Book.withTransaction {
            new Book(title: 'Before Update').save(flush: true)
        }
        Book.secondary.withTransaction {
            Book.executeUpdate('UPDATE Book b SET b.title = :newTitle WHERE b.title = :oldTitle',
                    [newTitle: 'After Update', oldTitle: 'Before Update'])
        }
        String title = null
        Book.secondary.withSession { Session session ->
            session.clear()
            title = Book.first()?.title
        }
        render "title:${title}"
    }

    def cleanup() {
        Book.withTransaction {
            Book.list()*.delete(flush: true)
        }
        render "cleaned"
    }
}
