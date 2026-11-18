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

import java.sql.ResultSet

import org.hibernate.engine.spi.SessionImplementor
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A bidirectional many-to-many has one join table. When only the owning side names it with
 * {@code joinTable}, the inverse side must read that table instead of a default-named one.
 */
@Rollback
class ManyToManyInverseJoinTableSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            InvNamedOwner, InvNamedInverse, InvKeyedOwner, InvKeyedInverse,
            InvBook, InvAuthor, InvOwnTableOwner, InvOwnTableInverse
    )
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    void 'the inverse side sees rows the owner wrote when only the owner names the join table'() {
        given:
        var inverse = new InvNamedInverse(name: 'inverse')
        var owner = new InvNamedOwner(name: 'owner')
        owner.addToOthers(inverse)
        owner.save(flush: true, failOnError: true)
        clearSession()

        expect:
        InvNamedInverse.get(inverse.id).owners*.name == ['owner']
        InvNamedOwner.get(owner.id).others*.name == ['inverse']

        and: 'no second, default-named join table is created for the inverse side'
        tableNames().contains('INV_NAMED_OTHERS')
        !tableNames().contains('INV_NAMED_OWNER_OTHERS')
    }

    void 'the inverse side sees rows the owner wrote when the owner names the join table and its key'() {
        given:
        var inverse = new InvKeyedInverse(name: 'inverse')
        var owner = new InvKeyedOwner(name: 'owner')
        owner.addToOthers(inverse)
        owner.save(flush: true, failOnError: true)
        clearSession()

        expect:
        InvKeyedInverse.get(inverse.id).owners*.name == ['owner']
        columnNamesFor('INV_KEYED_JOIN') == ['owner_ref', 'inv_keyed_inverse_id'] as Set
        !tableNames().contains('INV_KEYED_OWNER_OTHERS')
    }

    void 'both sides configuring the same join table keep working'() {
        given:
        var book = new InvBook(title: 'book')
        var author = new InvAuthor(name: 'author')
        author.addToBooks(book)
        author.save(flush: true, failOnError: true)
        clearSession()

        expect:
        InvBook.get(book.id).authors*.name == ['author']
        InvAuthor.get(author.id).books*.title == ['book']
        columnNamesFor('INV_MM_AUTHOR_BOOKS') == ['mm_book_id', 'mm_author_id'] as Set
    }

    void 'an inverse side that names its own join table keeps it'() {
        given:
        var inverse = new InvOwnTableInverse(name: 'inverse')
        var owner = new InvOwnTableOwner(name: 'owner')
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
        query('select table_name from information_schema.tables').collect { it.toUpperCase() }.toSet()
    }

    private Set<String> columnNamesFor(String tableName) {
        query('select column_name from information_schema.columns where table_name = ?', tableName)
                .collect { it.toLowerCase() }
                .toSet()
    }

    private List<String> query(String sql, Object... params) {
        var session = (SessionImplementor) datastore.sessionFactory.currentSession
        session.doReturningWork { connection ->
            List<String> values = []
            try (var statement = connection.prepareStatement(sql)) {
                params.eachWithIndex { Object param, int i -> statement.setObject(i + 1, param) }
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        values << resultSet.getString(1)
                    }
                }
            }
            values
        }
    }
}

@Entity
class InvNamedOwner {

    String name
    Set<InvNamedInverse> others

    static hasMany = [others: InvNamedInverse]

    static mapping = {
        others(joinTable: [name: 'inv_named_others'])
    }
}

@Entity
class InvNamedInverse {

    String name
    Set<InvNamedOwner> owners

    static hasMany = [owners: InvNamedOwner]
    static belongsTo = InvNamedOwner
}

@Entity
class InvKeyedOwner {

    String name
    Set<InvKeyedInverse> others

    static hasMany = [others: InvKeyedInverse]

    static mapping = {
        others(joinTable: [name: 'inv_keyed_join', key: 'owner_ref'])
    }
}

@Entity
class InvKeyedInverse {

    String name
    Set<InvKeyedOwner> owners

    static hasMany = [owners: InvKeyedOwner]
    static belongsTo = InvKeyedOwner
}

@Entity
class InvBook {

    String title

    static belongsTo = InvAuthor
    static hasMany = [authors: InvAuthor]

    static mapping = {
        authors(joinTable: [name: 'inv_mm_author_books', key: 'mm_book_id'])
    }
}

@Entity
class InvAuthor {

    String name

    static hasMany = [books: InvBook]

    static mapping = {
        books(joinTable: [name: 'inv_mm_author_books', key: 'mm_author_id'])
    }
}

@Entity
class InvOwnTableOwner {

    String name
    Set<InvOwnTableInverse> others

    static hasMany = [others: InvOwnTableInverse]

    static mapping = {
        others(joinTable: [name: 'inv_own_table_join'])
    }
}

@Entity
class InvOwnTableInverse {

    String name
    Set<InvOwnTableOwner> owners

    static hasMany = [owners: InvOwnTableOwner]
    static belongsTo = InvOwnTableOwner

    static mapping = {
        owners(joinTable: [name: 'inv_own_table_join'])
    }
}
