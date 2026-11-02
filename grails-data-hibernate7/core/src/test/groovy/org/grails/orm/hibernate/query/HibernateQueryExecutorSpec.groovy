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

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Root

import org.hibernate.NonUniqueResultException
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
}

@Entity
class ExecutorItem implements HibernateEntity<ExecutorItem> {
    String name
    Integer amount
}
