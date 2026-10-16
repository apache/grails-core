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

import org.grails.datastore.mapping.query.Query
import spock.lang.Specification

class PagedResultListSpec extends Specification {

    void "test a null query produces an empty result list with default max/offset/totalCount"() {
        given:
        def pagedList = new PagedResultList(null)

        expect:
        pagedList.resultList == []
        pagedList.max == -1
        pagedList.offset == 0
        pagedList.totalCount == 0
    }

    void "test resultList is populated eagerly from the query's list at construction time"() {
        given:
        def query = Mock(Query)
        query.list() >> ['a', 'b', 'c']

        when:
        def pagedList = new PagedResultList(query)

        then:
        pagedList.resultList == ['a', 'b', 'c']
        pagedList.size() == 3
    }

    void "test getMax falls back to -1 when the query has no max set"() {
        given:
        def query = Mock(Query)
        query.list() >> []
        query.getMax() >> null

        expect:
        new PagedResultList(query).max == -1
    }

    void "test getMax returns the query's configured max"() {
        given:
        def query = Mock(Query)
        query.list() >> []
        query.getMax() >> 5

        expect:
        new PagedResultList(query).max == 5
    }

    void "test getOffset falls back to 0 when the query has no offset set"() {
        given:
        def query = Mock(Query)
        query.list() >> []
        query.getOffset() >> null

        expect:
        new PagedResultList(query).offset == 0
    }

    void "test getOffset returns the query's configured offset"() {
        given:
        def query = Mock(Query)
        query.list() >> []
        query.getOffset() >> 10

        expect:
        new PagedResultList(query).offset == 10
    }

    void "test getTotalCount runs a cloned, unpaged, unordered counting query and caches the result"() {
        given:
        def countingQuery = Mock(Query)
        countingQuery.projections() >> new Query.ProjectionList()
        def query = Mock(Query)
        query.list() >> ['a']
        query.clone() >> countingQuery
        def pagedList = new PagedResultList(query)

        when:
        def firstCall = pagedList.totalCount
        def secondCall = pagedList.totalCount

        then:
        firstCall == 7
        secondCall == 7
        1 * countingQuery.offset(0)
        1 * countingQuery.max(-1)
        1 * countingQuery.clearOrders()
        1 * countingQuery.singleResult() >> 7
    }

    void "test getTotalCount is 0 when the counting query returns no result"() {
        given:
        def countingQuery = Mock(Query)
        countingQuery.singleResult() >> null
        countingQuery.projections() >> new Query.ProjectionList()
        def query = Mock(Query)
        query.list() >> []
        query.clone() >> countingQuery

        expect:
        new PagedResultList(query).totalCount == 0
    }

    void "test getQuery returns the original query"() {
        given:
        def query = Mock(Query)
        query.list() >> []

        expect:
        new PagedResultList(query).query.is(query)
    }

    void "test list mutation methods delegate to the underlying result list"() {
        given:
        def query = Mock(Query)
        query.list() >> ['a', 'b', 'c']
        def pagedList = new PagedResultList(query)

        expect:
        pagedList.get(1) == 'b'
        pagedList.indexOf('c') == 2
        pagedList.contains('a')
        !pagedList.isEmpty()

        when:
        pagedList.add('d')
        pagedList.add(0, 'first')
        pagedList.set(1, 'A')

        then:
        pagedList.resultList == ['first', 'A', 'b', 'c', 'd']

        when:
        pagedList.remove('d')
        pagedList.remove(0)

        then:
        pagedList.resultList == ['A', 'b', 'c']

        when:
        pagedList.clear()

        then:
        pagedList.isEmpty()
        pagedList.size() == 0
    }

    void "test equals and hashCode delegate to the underlying result list"() {
        given:
        def query = Mock(Query)
        query.list() >> ['a', 'b']

        expect:
        new PagedResultList(query) == ['a', 'b']
        new PagedResultList(query).hashCode() == ['a', 'b'].hashCode()
    }
}
