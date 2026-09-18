/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable

class Issue16349Spec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Issue16349IdentityBook, Issue16349IncrementBook,
                Issue16349AssignedBook, Issue16349UnionRoot, Issue16349UnionSub,
                Issue16349Shelf, Issue16349ShelvedBook)
    }

    void 'identity generator: save does not issue an extra update'() {
        given:
        def statistics = manager.sessionFactory.statistics
        statistics.statisticsEnabled = true
        statistics.clear()

        when:
        def book = new Issue16349IdentityBook(title: 'original').save(flush: true, failOnError: true)

        then:
        book.version == 0
        statistics.entityInsertCount == 1
        statistics.entityUpdateCount == 0
    }

    void 'increment generator: save does not issue an extra update'() {
        given:
        def statistics = manager.sessionFactory.statistics
        statistics.statisticsEnabled = true
        statistics.clear()

        when:
        def book = new Issue16349IncrementBook(title: 'original').save(flush: true, failOnError: true)

        then:
        book.version == 0
        statistics.entityInsertCount == 1
        statistics.entityUpdateCount == 0
    }

    void 'table-per-concrete-class subclass, which cannot use the identity generator: save does not issue an extra update'() {
        given:
        def statistics = manager.sessionFactory.statistics
        statistics.statisticsEnabled = true
        statistics.clear()

        when:
        def book = new Issue16349UnionSub(title: 'original', extra: 'subclass state').save(flush: true, failOnError: true)

        then:
        book.version == 0
        statistics.entityInsertCount == 1
        statistics.entityUpdateCount == 0
    }

    void 'assigned generator, which saves through a merge: save does not issue an extra update'() {
        given:
        def statistics = manager.sessionFactory.statistics
        statistics.statisticsEnabled = true
        statistics.clear()

        when: 'the identifier is already set, so GORM routes the save through session.merge'
        def book = new Issue16349AssignedBook(id: 'isbn-1', title: 'original').save(flush: true, failOnError: true)

        then:
        book.version == 0
        statistics.entityInsertCount == 1
        statistics.entityUpdateCount == 0
    }

    void 'increment generator: a child persisted by the flush-time cascade does not issue an extra update'() {
        given: 'a managed parent, so the child is reached by the cascade Hibernate runs at flush rather than by save'
        def shelf = new Issue16349Shelf(name: 'fiction').save(flush: true, failOnError: true)
        def statistics = manager.sessionFactory.statistics
        statistics.statisticsEnabled = true
        statistics.clear()

        when:
        def book = new Issue16349ShelvedBook(title: 'cascaded')
        shelf.addToBooks(book)
        Issue16349Shelf.withSession { it.flush() }

        then: 'the persist-on-flush listener activates tracking before the insert, as an explicit persist does'
        book.version == 0

        and: 'the child is written by a single insert; the one update is the parent, whose collection changed'
        def childStatistics = statistics.getEntityStatistics(Issue16349ShelvedBook.name)
        childStatistics.insertCount == 1
        childStatistics.updateCount == 0
    }

    void 'increment generator: manually activating dirty-check tracking before flush avoids the extra update'() {
        given:
        def statistics = manager.sessionFactory.statistics
        statistics.statisticsEnabled = true
        statistics.clear()
        def book = new Issue16349IncrementBook(title: 'original')

        when:
        (book as DirtyCheckable).trackChanges()
        book.save(flush: true, failOnError: true)

        then:
        book.version == 0
        statistics.entityInsertCount == 1
        statistics.entityUpdateCount == 0
    }
}

@Entity
class Issue16349IdentityBook {
    Long id
    Long version
    String title
}

@Entity
class Issue16349IncrementBook {
    Long id
    Long version
    String title

    static mapping = {
        id generator: 'increment'
    }
}

@Entity
class Issue16349AssignedBook {
    String id
    Long version
    String title

    static mapping = {
        id generator: 'assigned'
    }
}

@Entity
class Issue16349UnionRoot {
    Long id
    Long version
    String title

    static mapping = {
        tablePerConcreteClass true
        id generator: 'increment'
    }
}

@Entity
class Issue16349UnionSub extends Issue16349UnionRoot {
    String extra
}

@Entity
class Issue16349Shelf {
    Long id
    Long version
    String name

    static hasMany = [books: Issue16349ShelvedBook]

    static mapping = {
        id generator: 'increment'
        books cascade: 'all'
    }
}

@Entity
class Issue16349ShelvedBook {
    Long id
    Long version
    String title

    static belongsTo = [shelf: Issue16349Shelf]

    static mapping = {
        id generator: 'increment'
    }
}
