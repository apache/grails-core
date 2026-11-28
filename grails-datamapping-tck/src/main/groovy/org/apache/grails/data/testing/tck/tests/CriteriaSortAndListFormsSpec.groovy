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

package org.apache.grails.data.testing.tck.tests

import grails.gorm.DetachedCriteria
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.CriteriaRanked
import spock.lang.Unroll

/**
 * The forms in which a query is sorted and given a list, tried at every entry point: criteria, detached criteria, where
 * queries and dynamic finders. A sort is applied or refused and never dropped, and an empty list matches nothing without
 * a database error, and its negation matches every row.
 */
class CriteriaSortAndListFormsSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([CriteriaRanked])
    }

    /** tier 1: bob, dan; tier 2: ann, cat; inserted in an order that no sort below produces */
    private void seed() {
        new CriteriaRanked(name: 'cat', tier: 2).save(failOnError: true)
        new CriteriaRanked(name: 'ann', tier: 2).save(failOnError: true)
        new CriteriaRanked(name: 'dan', tier: 1).save(failOnError: true)
        new CriteriaRanked(name: 'bob', tier: 1).save(flush: true, failOnError: true)
        manager.session.clear()
    }

    @Unroll
    void "sort form: #form"() {
        given:
        seed()

        when:
        List<String> found = query.call()

        then:
        found == expected

        where:
        form                                                | query                                                                                                              | expected
        'criteria, sort and order params'                   | { -> CriteriaRanked.createCriteria().list(sort: 'name', order: 'desc') { gt('tier', 0) }*.name }                  | ['dan', 'cat', 'bob', 'ann']
        'criteria, a map of sort properties'                | { -> CriteriaRanked.createCriteria().list(sort: [tier: 'asc', name: 'desc']) { gt('tier', 0) }*.name }            | ['dan', 'bob', 'cat', 'ann']
        'criteria, order calls in the closure'              | { -> CriteriaRanked.createCriteria().list { gt('tier', 0); order('tier', 'asc'); order('name', 'desc') }*.name }  | ['dan', 'bob', 'cat', 'ann']
        'static list, sort and order params'                | { -> CriteriaRanked.list(sort: 'name', order: 'desc')*.name }                                                      | ['dan', 'cat', 'bob', 'ann']
        'static list, a map of sort properties'             | { -> CriteriaRanked.list(sort: [tier: 'asc', name: 'desc'])*.name }                                                | ['dan', 'bob', 'cat', 'ann']
        'dynamic finder, sort and order params'             | { -> CriteriaRanked.findAllByTierGreaterThan(0, [sort: 'name', order: 'desc'])*.name }                            | ['dan', 'cat', 'bob', 'ann']
        'dynamic finder, a map of sort properties'          | { -> CriteriaRanked.findAllByTierGreaterThan(0, [sort: [tier: 'asc', name: 'desc']])*.name }                      | ['dan', 'bob', 'cat', 'ann']
        'where query, sort and order params'                | { -> CriteriaRanked.where { tier > 0 }.list(sort: 'name', order: 'desc')*.name }                                  | ['dan', 'cat', 'bob', 'ann']
        'where query, a map of sort properties'             | { -> CriteriaRanked.where { tier > 0 }.list(sort: [tier: 'asc', name: 'desc'])*.name }                            | ['dan', 'bob', 'cat', 'ann']
        'detached criteria, sort and order params'          | { -> new DetachedCriteria(CriteriaRanked).build { gt('tier', 0) }.list(sort: 'name', order: 'desc')*.name }       | ['dan', 'cat', 'bob', 'ann']
        'detached criteria, a map of sort properties'       | { -> new DetachedCriteria(CriteriaRanked).build { gt('tier', 0) }.list(sort: [tier: 'asc', name: 'desc'])*.name } | ['dan', 'bob', 'cat', 'ann']
    }

    @Unroll
    void "list form: #form"() {
        given:
        seed()

        when:
        def found = query.call()

        then:
        found == expected

        where:
        form                                              | query                                                                                                          | expected
        'criteria, an empty list'                         | { -> CriteriaRanked.createCriteria().list { inList('name', []) }*.name }                                      | []
        'criteria, an empty list in an or beside a name'  | { -> CriteriaRanked.createCriteria().list { or { inList('name', []); eq('name', 'ann') } }*.name }            | ['ann']
        'criteria, an empty list in a negation'           | { -> CriteriaRanked.createCriteria().list { not { inList('name', []) }; order('name') }*.name }               | ['ann', 'bob', 'cat', 'dan']
        'criteria, a count of an empty list'              | { -> CriteriaRanked.createCriteria().count { inList('name', []) } }                                           | 0
        'dynamic finder, an empty list'                   | { -> CriteriaRanked.findAllByNameInList([])*.name }                                                           | []
        'dynamic finder, a null list'                     | { -> CriteriaRanked.findAllByNameInList(null)*.name }                                                         | []
        'dynamic finder, a count of an empty list'        | { -> CriteriaRanked.countByNameInList([]) }                                                                   | 0
        'dynamic finder, an empty list beside a property' | { -> CriteriaRanked.findAllByNameInListAndTier([], 1)*.name }                                                 | []
        'where query, an empty list'                      | { -> CriteriaRanked.where { name in [] }.list()*.name }                                                       | []
        'detached criteria, an empty list'                | { -> new DetachedCriteria(CriteriaRanked).build { inList('name', []) }.list()*.name }                         | []
    }
}
