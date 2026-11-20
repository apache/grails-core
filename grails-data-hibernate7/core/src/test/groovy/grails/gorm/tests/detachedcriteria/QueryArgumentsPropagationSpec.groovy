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
package grails.gorm.tests.detachedcriteria

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import spock.lang.Unroll

import org.grails.orm.hibernate.query.HibernateQuery
import org.hibernate.FlushMode
import org.hibernate.Session

class QueryArgumentsPropagationSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(QueryArgsEntity)
    }

    private void seed() {
        (1..3).each { new QueryArgsEntity(name: "n${it}", amount: it).save(flush: true) }
        QueryArgsEntity.withSession { Session s -> s.clear() }
    }

    private boolean isReadOnly(QueryArgsEntity entity) {
        QueryArgsEntity.withSession { Session s -> s.isReadOnly(entity) }
    }

    @Unroll
    void "readOnly: #readOnly reaches a criteria query through #route"() {
        given:
        seed()

        when:
        List<QueryArgsEntity> found = fetch(route, [readOnly: readOnly])

        then:
        found.size() == 3
        found.every { isReadOnly(it) == readOnly }

        where:
        route                | readOnly
        'where.list'         | true
        'where.list'         | false
        'findAll closure'    | true
        'findAll closure'    | false
        'withCriteria'       | true
        'withCriteria'       | false
    }

    private List<QueryArgsEntity> fetch(String route, Map args) {
        switch (route) {
            case 'where.list':
                return QueryArgsEntity.where { amount > 0 }.list(args)
            case 'findAll closure':
                return QueryArgsEntity.findAll(args) { amount > 0 }
            default:
                return QueryArgsEntity.withCriteria(args) { gt('amount', 0) }
        }
    }

    void "readOnly on a single result criteria query is honoured"() {
        given:
        seed()

        when:
        QueryArgsEntity found = QueryArgsEntity.where { amount == 2 }.find(readOnly: true)

        then:
        found.name == 'n2'
        isReadOnly(found)
    }

    void "flushMode MANUAL keeps a pending change out of a criteria query and ALWAYS flushes it"() {
        given:
        seed()
        QueryArgsEntity first = QueryArgsEntity.findByName('n1')
        first.amount = 99

        when:
        List<QueryArgsEntity> manual = QueryArgsEntity.where { amount == 99 }.list(flushMode: 'MANUAL')
        List<QueryArgsEntity> always = QueryArgsEntity.where { amount == 99 }.list(flushMode: 'ALWAYS')

        then:
        manual.isEmpty()
        always*.name == ['n1']
    }

    @Unroll
    void "the total of a page through #route with flushMode #mode #outcome the pending change"() {
        given: 'a session that flushes by itself, so only the flush mode of the arguments keeps a change out'
        seed()
        QueryArgsEntity.withSession { Session s -> s.setHibernateFlushMode(FlushMode.AUTO) }
        QueryArgsEntity.findByName('n1').amount = 99

        when:
        def page = paged(route, [max: 10, flushMode: mode])

        then: 'the page and the count behind its total read the same data'
        page.size() == expected
        page.totalCount == expected

        where:
        route                | mode     || expected
        'where.list'         | 'MANUAL' || 0
        'where.list'         | 'ALWAYS' || 1
        'findAll closure'    | 'MANUAL' || 0
        'findAll closure'    | 'ALWAYS' || 1
        'createCriteria'     | 'MANUAL' || 0
        'createCriteria'     | 'ALWAYS' || 1
        'grouped criteria'   | 'MANUAL' || 0
        'grouped criteria'   | 'ALWAYS' || 1
        outcome = expected ? 'flushes' : 'leaves out'
    }

    private def paged(String route, Map args) {
        switch (route) {
            case 'where.list':
                return QueryArgsEntity.where { amount == 99 }.list(args)
            case 'findAll closure':
                return QueryArgsEntity.findAll(args) { amount == 99 }
            case 'createCriteria':
                return QueryArgsEntity.createCriteria().list(args) { eq('amount', 99) }
            default:
                return QueryArgsEntity.createCriteria().list(args) {
                    eq('amount', 99)
                    projections { groupProperty('name') }
                }
        }
    }

    void "an unparsable fetchSize is rejected with the argument name"() {
        given:
        HibernateQuery query = manager.hibernateDatastore.connect().createQuery(QueryArgsEntity) as HibernateQuery

        when:
        query.arguments = [fetchSize: 'lots']

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('fetchSize')
        e.message.contains('lots')
    }
}

@Entity
class QueryArgsEntity implements HibernateEntity<QueryArgsEntity> {
    String name
    Integer amount
}
