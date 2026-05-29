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

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import jakarta.persistence.LockModeType
import org.hibernate.query.QueryFlushMode

import java.lang.reflect.Method

class SelectQueryDelegateSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([SelectQueryDelegateTestBook])
    }

    void setup() {
        new SelectQueryDelegateTestBook(title: "Alpha", pages: 100).save(flush: true, failOnError: true)
        new SelectQueryDelegateTestBook(title: "Beta",  pages: 200).save(flush: true, failOnError: true)
        new SelectQueryDelegateTestBook(title: "Gamma", pages: 300).save(flush: true, failOnError: true)
    }

    private SelectQueryDelegate buildDelegate(String hql) {
        def query = sessionFactory.currentSession.createQuery(hql, Object[])
        new SelectQueryDelegate(query)
    }

    void "constructor wraps a SELECT query"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook")

        expect:
        delegate != null
        delegate.selectQuery() != null
    }

    void "list() returns all results"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook ORDER BY title")

        when:
        def results = delegate.list()

        then:
        results.size() == 3
    }

    void "setMaxResults limits results"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook ORDER BY title")

        when:
        delegate.setMaxResults(2)
        def results = delegate.list()

        then:
        results.size() == 2
    }

    void "setFirstResult offsets results"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook ORDER BY title")

        when:
        delegate.setFirstResult(2)
        def results = delegate.list()

        then:
        results.size() == 1
    }

    void "setParameter by name filters results"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook WHERE title = :title")

        when:
        delegate.setParameter("title", "Alpha")
        def results = delegate.list()

        then:
        results.size() == 1
    }

    void "setParameter by name with type filters results"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook WHERE pages = :p")

        when:
        delegate.setParameter("p", Integer.valueOf(200), Integer.class)
        def results = delegate.list()

        then:
        results.size() == 1
    }

    void "setParameter by position filters results"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook WHERE title = ?1")
        Method m = SelectQueryDelegate.getDeclaredMethod("setParameter", int.class, Object.class)
        m.accessible = true

        when:
        m.invoke(delegate, 1, "Beta")
        def results = delegate.list()

        then:
        results.size() == 1
    }

    void "setParameter by position with type filters results"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook WHERE pages = ?1")
        Method m = SelectQueryDelegate.getDeclaredMethod("setParameter", int.class, Object.class, Class.class)
        m.accessible = true

        when:
        m.invoke(delegate, 1, (Object) Integer.valueOf(300), (Object) Integer.class)
        def results = delegate.list()

        then:
        results.size() == 1
    }

    void "setParameterList(Collection) filters with IN clause"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook WHERE title IN (:titles)")

        when:
        delegate.setParameterList("titles", ["Alpha", "Beta"] as Collection)
        def results = delegate.list()

        then:
        results.size() == 2
    }

    void "setParameterList(array) filters with IN clause"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook WHERE title IN (:titles)")
        Method m = SelectQueryDelegate.getDeclaredMethod("setParameterList", String.class, Object[].class)
        m.accessible = true

        when:
        m.invoke(delegate, "titles", (Object) (["Alpha", "Gamma"] as Object[]))
        def results = delegate.list()

        then:
        results.size() == 2
    }

    void "setTimeout does not throw"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook")

        when:
        delegate.setTimeout(30)

        then:
        noExceptionThrown()
    }

    void "setQueryFlushMode does not throw"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook")

        when:
        delegate.setQueryFlushMode(QueryFlushMode.NO_FLUSH)

        then:
        noExceptionThrown()
    }

    void "setCacheable does not throw"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook")

        when:
        delegate.setCacheable(true)

        then:
        noExceptionThrown()
    }

    void "setFetchSize does not throw"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook")

        when:
        delegate.setFetchSize(10)

        then:
        noExceptionThrown()
    }

    void "setReadOnly does not throw"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook")

        when:
        delegate.setReadOnly(true)

        then:
        noExceptionThrown()
    }

    void "setLockMode does not throw"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook")

        when:
        delegate.setLockMode(LockModeType.READ)

        then:
        noExceptionThrown()
    }

    void "setHint does not throw"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook")

        when:
        delegate.setHint("org.hibernate.readOnly", true)

        then:
        noExceptionThrown()
    }

    void "executeUpdate throws UnsupportedOperationException"() {
        given:
        def delegate = buildDelegate("FROM SelectQueryDelegateTestBook")

        when:
        delegate.executeUpdate()

        then:
        thrown(UnsupportedOperationException)
    }
}

@Entity
class SelectQueryDelegateTestBook {
    String title
    Integer pages

    static constraints = {
        title nullable: false
        pages nullable: false
    }
}
