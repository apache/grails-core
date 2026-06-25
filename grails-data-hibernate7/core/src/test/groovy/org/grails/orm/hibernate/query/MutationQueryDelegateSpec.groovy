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

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.hibernate.query.MutationQuery
import org.hibernate.query.QueryArgumentException
import org.hibernate.query.QueryFlushMode

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class MutationQueryDelegateSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(MutationQueryDelegateTestBook)
    }

    void setup() {
        new MutationQueryDelegateTestBook(title: "Book One",   pages: 100).save(flush: true, failOnError: true)
        new MutationQueryDelegateTestBook(title: "Book Two",   pages: 200).save(flush: true, failOnError: true)
        new MutationQueryDelegateTestBook(title: "Book Three", pages: 300).save(flush: true, failOnError: true)
    }

    private MutationQuery buildMutationQuery(String hql) {
        sessionFactory.currentSession.createMutationQuery(hql)
    }

    void "constructor wraps MutationQuery"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "UPDATE MutationQueryDelegateTestBook SET title = :title WHERE title = :old"
        )

        when:
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)

        then:
        delegate != null
    }

    void "setTimeout delegates to MutationQuery"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)

        when:
        delegate.setTimeout(30)

        then:
        noExceptionThrown()
    }

    void "setQueryFlushMode delegates to MutationQuery"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)

        when:
        delegate.setQueryFlushMode(QueryFlushMode.NO_FLUSH)

        then:
        noExceptionThrown()
    }

    void "setParameter by name delegates and executeUpdate returns row count"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "UPDATE MutationQueryDelegateTestBook SET pages = :newPages WHERE title = :title"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
        delegate.setParameter("newPages", 999)
        delegate.setParameter("title", "Book One")

        when:
        int count = delegate.executeUpdate()

        then:
        count == 1
    }

    void "setParameter by name with type delegates and executeUpdate returns row count"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "UPDATE MutationQueryDelegateTestBook SET pages = :newPages WHERE title = :title"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
        delegate.setParameter("newPages", Integer.valueOf(42), Integer.class)
        delegate.setParameter("title", "Book Two", String.class)

        when:
        int count = delegate.executeUpdate()

        then:
        count == 1
    }

    void "setParameter by int position delegates and executeUpdate returns row count"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "UPDATE MutationQueryDelegateTestBook SET pages = ?1 WHERE title = ?2"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
        Method setParamInt = MutationQueryDelegate.class.getDeclaredMethod("setParameter", int.class, Object.class)
        setParamInt.setAccessible(true)

        when:
        setParamInt.invoke(delegate, 1, (Object) 77)
        setParamInt.invoke(delegate, 2, (Object) "Book Three")
        int count = delegate.executeUpdate()

        then:
        count == 1
    }

    void "setParameter by int position with type delegates and executeUpdate returns row count"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "UPDATE MutationQueryDelegateTestBook SET pages = ?1 WHERE title = ?2"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
        Method setParamIntTyped = MutationQueryDelegate.class.getDeclaredMethod("setParameter", int.class, Object.class, Class.class)
        setParamIntTyped.setAccessible(true)

        when:
        setParamIntTyped.invoke(delegate, 1, (Object) Integer.valueOf(88), (Object) Integer.class)
        setParamIntTyped.invoke(delegate, 2, (Object) "Book One", (Object) String.class)
        int count = delegate.executeUpdate()

        then:
        count == 1
    }

    void "setParameterList with Collection delegates as parameter value and executes DELETE"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "DELETE FROM MutationQueryDelegateTestBook WHERE title IN (:titles)"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
        delegate.setParameterList("titles", (Collection<?>) ["Book One", "Book Two"])

        when:
        int count = delegate.executeUpdate()

        then:
        count == 2
    }

    void "setParameterList with Object array delegates to setParameter via reflection"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "DELETE FROM MutationQueryDelegateTestBook WHERE title IN (:titles)"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
        Method setParamList = MutationQueryDelegate.class.getDeclaredMethod("setParameterList", String.class, Object[].class)
        setParamList.setAccessible(true)

        when:
        setParamList.invoke(delegate, "titles", (Object) (["Book Two", "Book Three"] as Object[]))

        then:
        InvocationTargetException ex = thrown(InvocationTargetException)
        ex.cause instanceof QueryArgumentException
    }

    void "setParameterList with Object array directly delegates to mutationQuery setParameter"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "DELETE FROM MutationQueryDelegateTestBook WHERE title IN (:titles)"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)

        when:
        delegate.setParameterList("titles", ["Book One", "Book Two"] as Object[])

        then:
        thrown(org.hibernate.query.QueryArgumentException)
    }

    void "list throws UnsupportedOperationException"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)

        when:
        delegate.list()

        then:
        thrown(UnsupportedOperationException)
    }

    void "select-only methods are no-ops"() {
        given:
        MutationQuery mq = buildMutationQuery("UPDATE MutationQueryDelegateTestBook SET pages = 0")
        HqlQueryDelegate delegate = new MutationQueryDelegate(mq)

        when:
        delegate.setMaxResults(10)
        delegate.setFirstResult(5)
        delegate.setCacheable(true)
        delegate.setFetchSize(100)
        delegate.setReadOnly(true)
        delegate.setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)

        then:
        noExceptionThrown()
    }

    void "setHint delegates to MutationQuery"() {
        given:
        MutationQuery mq = buildMutationQuery("UPDATE MutationQueryDelegateTestBook SET pages = 0")
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)

        when:
        delegate.setHint("org.hibernate.comment", "my comment")

        then:
        noExceptionThrown()
    }

    void "selectQuery returns null for mutation queries"() {
        given:
        MutationQuery mq = buildMutationQuery(
            "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
        )
        MutationQueryDelegate delegate = new MutationQueryDelegate(mq)

        expect:
        delegate.selectQuery() == null
    }
}

@Entity
class MutationQueryDelegateTestBook {
    String title
    Integer pages

    static constraints = {
        title nullable: false
        pages nullable: false
    }
}
