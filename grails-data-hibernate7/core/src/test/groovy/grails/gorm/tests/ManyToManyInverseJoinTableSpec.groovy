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

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.engine.spi.SessionImplementor
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.ResultSet

/**
 * A bidirectional many-to-many binds one join table for both sides. When only the owning side
 * configures {@code joinTable}, the inverse side must read the table the owner writes instead of
 * deriving a second, default-named table from its own mapping.
 */
@Rollback
class ManyToManyInverseJoinTableSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            InvNamedOwner, InvNamedInverse, InvKeyedOwner, InvKeyedInverse, InvBook, InvAuthor, InvOwnTableOwner, InvOwnTableInverse)

    void "the inverse side sees rows the owner wrote when only the owner names the join table"() {
        given:
        InvNamedInverse inverse = new InvNamedInverse(name: 'inverse')
        InvNamedOwner owner = new InvNamedOwner(name: 'owner')
        owner.addToOthers(inverse)
        owner.save(flush: true, failOnError: true)
        clearSession()

        expect:
        InvNamedInverse.get(inverse.id).owners*.name == ['owner']
        tableNames().contains('INV_NAMED_OTHERS')
    }

    void "the inverse side takes the owner's key and column names, swapped, when only the owner configures them"() {
        given:
        InvKeyedInverse inverse = new InvKeyedInverse(name: 'inverse')
        InvKeyedOwner owner = new InvKeyedOwner(name: 'owner')
        owner.addToOthers(inverse)
        owner.save(flush: true, failOnError: true)
        clearSession()

        expect:
        InvKeyedInverse.get(inverse.id).owners*.name == ['owner']
        columnNamesFor('INV_KEYED_JOIN') == ['owner_ref', 'inverse_ref'] as Set
        !tableNames().any { it.startsWith('INV_KEYED_OWNER_') }
    }

    void "the inverse side can also be written and the owner reads it back"() {
        given:
        InvKeyedOwner owner = new InvKeyedOwner(name: 'owner')
        InvKeyedInverse inverse = new InvKeyedInverse(name: 'inverse')
        owner.addToOthers(inverse)
        owner.save(flush: true, failOnError: true)
        clearSession()

        expect:
        InvKeyedOwner.get(owner.id).others*.name == ['inverse']
    }

    void "both sides configuring the documented joinTable keep working"() {
        given:
        InvBook book = new InvBook(title: 'book')
        InvAuthor author = new InvAuthor(name: 'author')
        author.addToBooks(book)
        author.save(flush: true, failOnError: true)
        clearSession()

        expect:
        InvBook.get(book.id).authors*.name == ['author']
        InvAuthor.get(author.id).books*.title == ['book']
        columnNamesFor('INV_MM_AUTHOR_BOOKS').containsAll(['mm_book_id', 'mm_author_id'])
    }

    void "an inverse side that names its own join table keeps it"() {
        given:
        InvOwnTableInverse inverse = new InvOwnTableInverse(name: 'inverse')
        InvOwnTableOwner owner = new InvOwnTableOwner(name: 'owner')
        owner.addToOthers(inverse)
        owner.save(flush: true, failOnError: true)
        clearSession()

        expect:
        InvOwnTableInverse.get(inverse.id).owners*.name == ['owner']
        tableNames().contains('INV_OWN_TABLE_JOIN')
    }

    private void clearSession() {
        datastore.sessionFactory.currentSession.clear()
    }

    private Set<String> tableNames() {
        SessionImplementor sessionImplementor = (SessionImplementor) datastore.sessionFactory.currentSession
        sessionImplementor.doReturningWork { connection ->
            Set<String> names = []
            try (def statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery('select table_name from information_schema.tables')) {
                    while (resultSet.next()) {
                        names << resultSet.getString(1).toUpperCase()
                    }
                }
            }
            names
        }
    }

    private Set<String> columnNamesFor(String tableName) {
        SessionImplementor sessionImplementor = (SessionImplementor) datastore.sessionFactory.currentSession
        sessionImplementor.doReturningWork { connection ->
            Set<String> columnNames = []
            try (def statement = connection.prepareStatement(
                    'select column_name from information_schema.columns where table_name = ?')) {
                statement.setString(1, tableName)
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        columnNames << resultSet.getString('column_name').toLowerCase()
                    }
                }
            }
            columnNames
        }
    }
}

@Entity
class InvNamedOwner {
    String name
    Set<InvNamedInverse> others

    static hasMany = [others: InvNamedInverse]

    static mapping = {
        others joinTable: [name: 'inv_named_others']
    }
}

@Entity
class InvNamedInverse {
    String name
    Set<InvNamedOwner> owners

    static hasMany = [owners: InvNamedOwner]
    static belongsTo = [owners: InvNamedOwner]
}

@Entity
class InvKeyedOwner {
    String name
    Set<InvKeyedInverse> others

    static hasMany = [others: InvKeyedInverse]

    static mapping = {
        others joinTable: [name: 'inv_keyed_join', key: 'owner_ref', column: 'inverse_ref']
    }
}

@Entity
class InvKeyedInverse {
    String name
    Set<InvKeyedOwner> owners

    static hasMany = [owners: InvKeyedOwner]
    static belongsTo = [owners: InvKeyedOwner]
}

@Entity
class InvBook {
    String title

    static belongsTo = InvAuthor
    static hasMany = [authors: InvAuthor]

    static mapping = {
        authors joinTable: [name: 'inv_mm_author_books', key: 'mm_book_id']
    }
}

@Entity
class InvAuthor {
    String name

    static hasMany = [books: InvBook]

    static mapping = {
        books joinTable: [name: 'inv_mm_author_books', key: 'mm_author_id']
    }
}

@Entity
class InvOwnTableOwner {
    String name
    Set<InvOwnTableInverse> others

    static hasMany = [others: InvOwnTableInverse]

    static mapping = {
        others joinTable: [name: 'inv_own_table_join']
    }
}

@Entity
class InvOwnTableInverse {
    String name
    Set<InvOwnTableOwner> owners

    static hasMany = [owners: InvOwnTableOwner]
    static belongsTo = [owners: InvOwnTableOwner]

    static mapping = {
        owners joinTable: [name: 'inv_own_table_join']
    }
}
