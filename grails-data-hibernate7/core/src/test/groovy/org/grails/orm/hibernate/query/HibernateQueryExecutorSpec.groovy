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
package org.grails.orm.hibernate.query

import jakarta.persistence.LockModeType
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.ParameterExpression
import jakarta.persistence.criteria.Root

import org.hibernate.NonUniqueResultException
import org.hibernate.Session
import org.hibernate.query.Query
import org.hibernate.query.QueryFlushMode
import org.hibernate.query.criteria.JpaCriteriaQuery

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.proxy.HibernateProxyHandler

class HibernateQueryExecutorSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(ExecutorItem)
    }

    private void seed() {
        (1..3).each { new ExecutorItem(name: "n${it}", amount: it).save(flush: true, failOnError: true) }
    }

    private JpaCriteriaQuery<ExecutorItem> itemsWithAmountAbove(int minimum) {
        CriteriaBuilder cb = sessionFactory.criteriaBuilder
        JpaCriteriaQuery<ExecutorItem> cq = (JpaCriteriaQuery<ExecutorItem>) cb.createQuery(ExecutorItem)
        Root<ExecutorItem> root = cq.from(ExecutorItem)
        cq.select(root).where(cb.greaterThan(root.<Integer> get('amount'), minimum))
        cq
    }

    private static HibernateQueryExecutor executor(Integer maxResults = null) {
        new HibernateQueryExecutor(null, maxResults, null, null, null, null, null, null, new HibernateProxyHandler())
    }

    @Rollback
    void "uniqueResult returns the row when exactly one matches"() {
        given:
        seed()

        when:
        ExecutorItem item = executor().uniqueResult(sessionFactory.currentSession, itemsWithAmountAbove(2), [:])

        then:
        item.name == 'n3'
    }

    @Rollback
    void "uniqueResult returns null when nothing matches"() {
        given:
        seed()

        expect:
        executor().uniqueResult(sessionFactory.currentSession, itemsWithAmountAbove(99), [:]) == null
    }

    @Rollback
    void "uniqueResult fails and reports how many rows matched when several do"() {
        given:
        seed()

        when:
        executor().uniqueResult(sessionFactory.currentSession, itemsWithAmountAbove(0), [:])

        then:
        NonUniqueResultException e = thrown()
        e.message.contains('3')
    }

    @Rollback
    void "uniqueResult sees only the rows maxResults allows"() {
        given:
        seed()

        when:
        ExecutorItem item = executor(1).uniqueResult(sessionFactory.currentSession, itemsWithAmountAbove(0), [:])

        then:
        item != null
    }

    @Rollback
    void "singleResult still returns the first row when several match"() {
        given:
        seed()

        when:
        ExecutorItem item = executor().singleResult(sessionFactory.currentSession, itemsWithAmountAbove(0), [:])

        then:
        item != null
        item.amount in [1, 2, 3]
    }

    @Rollback
    void "singleResult returns null when nothing matches"() {
        given:
        seed()

        expect:
        executor().singleResult(sessionFactory.currentSession, itemsWithAmountAbove(99), [:]) == null
    }

    private static HibernateQueryExecutor executorWith(Map settings) {
        new HibernateQueryExecutor(settings.offset, settings.maxResults, settings.lock, settings.queryCache,
                settings.fetchSize, settings.timeout, settings.flushMode, settings.readOnly, new HibernateProxyHandler())
    }

    void "list applies every setting it was given to the query"() {
        given:
        Session session = Mock()
        Query query = Mock()
        JpaCriteriaQuery<ExecutorItem> cq = itemsWithAmountAbove(0)
        def executor = executorWith(offset: 5, maxResults: 10, lock: LockModeType.PESSIMISTIC_WRITE, queryCache: true,
                fetchSize: 20, timeout: 30, flushMode: QueryFlushMode.FLUSH, readOnly: true)

        when:
        def rows = executor.list(session, cq, [:])

        then:
        1 * session.createQuery(cq) >> query
        1 * query.setFirstResult(5)
        1 * query.setMaxResults(10)
        1 * query.setLockMode(LockModeType.PESSIMISTIC_WRITE)
        1 * query.setHint('org.hibernate.cacheable', true)
        1 * query.setFetchSize(20)
        1 * query.setTimeout(30)
        1 * query.setQueryFlushMode(QueryFlushMode.FLUSH)
        1 * query.setReadOnly(true)
        1 * query.getResultList() >> [new ExecutorItem(name: 'x', amount: 1)]
        rows*.name == ['x']
    }

    void "readOnly false and queryCache false are still applied"() {
        given:
        Session session = Mock()
        Query query = Mock()
        JpaCriteriaQuery<ExecutorItem> cq = itemsWithAmountAbove(0)

        when:
        executorWith(queryCache: false, readOnly: false).list(session, cq, [:])

        then:
        1 * session.createQuery(cq) >> query
        1 * query.setHint('org.hibernate.cacheable', false)
        1 * query.setReadOnly(false)
        1 * query.getResultList() >> []
    }

    void "list leaves a setting alone when it is null or not positive"() {
        given:
        Session session = Mock()
        Query query = Mock()
        JpaCriteriaQuery<ExecutorItem> cq = itemsWithAmountAbove(0)

        when:
        executorWith(settings).list(session, cq, [:])

        then:
        1 * session.createQuery(cq) >> query
        0 * query.setFirstResult(_)
        0 * query.setMaxResults(_)
        0 * query.setLockMode(_)
        0 * query.setHint(_, _)
        0 * query.setFetchSize(_)
        0 * query.setTimeout(_)
        0 * query.setQueryFlushMode(_)
        0 * query.setReadOnly(_)
        1 * query.getResultList() >> []

        where:
        settings << [
                [:],
                [offset: 0, maxResults: 0, fetchSize: 0, timeout: 0],
                [offset: -1, maxResults: -1, fetchSize: -1, timeout: -1]
        ]
    }

    void "list sets the query parameters it was given"() {
        given:
        Session session = Mock()
        Query query = Mock()
        CriteriaBuilder cb = sessionFactory.criteriaBuilder
        JpaCriteriaQuery<ExecutorItem> cq = itemsWithAmountAbove(0)
        ParameterExpression<Integer> minimum = cb.parameter(Integer)

        when:
        executor().list(session, cq, [(minimum): 2])

        then:
        1 * session.createQuery(cq) >> query
        1 * query.setParameter(minimum, 2)
        1 * query.getResultList() >> []
    }

    void "a tuple query returns its rows as they are"() {
        given:
        Session session = Mock()
        Query query = Mock()
        CriteriaBuilder cb = sessionFactory.criteriaBuilder
        JpaCriteriaQuery cq = (JpaCriteriaQuery) cb.createTupleQuery()

        when:
        executor().list(session, cq, [:])

        then:
        1 * session.createQuery(cq) >> query
        1 * query.setTupleTransformer(_)
        1 * query.getResultList() >> []
    }

    void "a query that is not a tuple query keeps the default row transformer"() {
        given:
        Session session = Mock()
        Query query = Mock()
        JpaCriteriaQuery<ExecutorItem> cq = itemsWithAmountAbove(0)

        when:
        executor().list(session, cq, [:])

        then:
        1 * session.createQuery(cq) >> query
        0 * query.setTupleTransformer(_)
        1 * query.getResultList() >> []
    }
}

@Entity
class ExecutorItem implements HibernateEntity<ExecutorItem> {
    String name
    Integer amount
}
