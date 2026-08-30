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
package grails.gorm

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Exercises {@link CriteriaBuilder} end-to-end against a real {@link SimpleMapDatastore}, as
 * opposed to {@link CriteriaBuilderSpec}, which mocks its {@code Query}/{@code QueryCreator}
 * collaborators. In particular this covers {@code cache}/{@code join}/{@code select}/{@code order}
 * being called directly on a bare {@code createCriteria()} result, before any {@code .list{}}/
 * {@code .get{}} closure has initialized the underlying query.
 */
class CriteriaBuilderIntegrationSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(CriteriaBuilderSpecBook, CriteriaBuilderSpecAuthor)

    void "cache/select/order can be called directly on a bare createCriteria() without a wrapping closure"() {
        given: "a criteria builder obtained directly, not via .list{}/.get{} - previously NPE'd on a null query"
        def criteria = CriteriaBuilderSpecBook.createCriteria()

        expect:
        criteria.cache(true).is(criteria)
        criteria.select('title').is(criteria)
        criteria.order('title').is(criteria)
        criteria.order('title', 'desc').is(criteria)
        criteria.getPersistentEntity() != null
    }

    void "join(String) can be called directly on a bare createCriteria() without a wrapping closure"() {
        given: "join(String) had the same NPE as cache/select before this fix - test with a real association"
        def criteria = CriteriaBuilderSpecAuthor.createCriteria()

        expect:
        criteria.join('books').is(criteria)
    }

    void "list(Closure) executes a real query and returns matching results"() {
        given:
        CriteriaBuilderSpecBook.newInstance(title: 'Groovy in Action').save(flush: true)
        CriteriaBuilderSpecBook.newInstance(title: 'Grails in Action').save(flush: true)

        when:
        def results = CriteriaBuilderSpecBook.createCriteria().list {
            like('title', '%Action%')
            order('title')
        }

        then:
        results.size() == 2
        results.every { it.title.contains('Action') }
    }

    void "get(Closure) returns a single matching result"() {
        given:
        CriteriaBuilderSpecBook.newInstance(title: 'Unique Title').save(flush: true)

        when:
        def result = CriteriaBuilderSpecBook.createCriteria().get {
            eq('title', 'Unique Title')
        }

        then:
        result != null
        result.title == 'Unique Title'
    }

    void "count(Closure) returns the matching row count"() {
        given:
        CriteriaBuilderSpecBook.newInstance(title: 'Countable').save(flush: true)
        CriteriaBuilderSpecBook.newInstance(title: 'Countable').save(flush: true)

        when:
        def count = CriteriaBuilderSpecBook.createCriteria().count {
            eq('title', 'Countable')
        }

        then:
        count == 2
    }

    void "call(Closure) - the new brand-new method - executes a list query when uniqueResult is not set"() {
        given:
        CriteriaBuilderSpecBook.newInstance(title: 'Callable Book').save(flush: true)

        when:
        def result = CriteriaBuilderSpecBook.createCriteria().call { eq('title', 'Callable Book') }

        then:
        result instanceof List
        result.size() == 1
        result[0].title == 'Callable Book'
    }
}

@Entity
class CriteriaBuilderSpecBook {
    String title
}

@Entity
class CriteriaBuilderSpecAuthor {
    String name
    static hasMany = [books: CriteriaBuilderSpecBook]
}
