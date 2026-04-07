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
import org.hibernate.query.MutationQuery
import org.hibernate.query.QueryArgumentException
import org.hibernate.query.QueryFlushMode

/**
 * Covers all delegation methods in {@link MutationQueryDelegate}.
 *
 * Each feature method runs inside a transaction started by the TCK manager
 * (GrailsDataHibernate7TckManager) that is automatically rolled back after
 * the test, providing full isolation without any explicit cleanup method.
 */
class MutationQueryDelegateSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([MutationQueryDelegateTestBook])
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

    void "setParameter by position delegates and executeUpdate returns row count"() {
        when:
        int count = MutationQueryDelegateTestBook.withTransaction {
            buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = :newPages WHERE title = :title"
            ).with {
                setParameter("newPages", 77)
                setParameter("title", "Book Three")
                new MutationQueryDelegate(it).executeUpdate()
            }
        }

        then:
        count == 1
    }

    void "setParameter by position with type delegates and executeUpdate returns row count"() {
        when:
        int count = MutationQueryDelegateTestBook.withTransaction {
            buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = :newPages WHERE title = :title"
            ).with {
                setParameter("newPages", Integer.valueOf(88), Integer.class)
                setParameter("title", "Book One", String.class)
                new MutationQueryDelegate(it).executeUpdate()
            }
        }

        then:
        count == 1
    }

    void "setParameterList with Collection delegates as parameter value and executes DELETE"() {
        when:
        int count = MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "DELETE FROM MutationQueryDelegateTestBook WHERE title IN (:titles)"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setParameterList("titles", (Collection<?>) ["Book One", "Book Two"])
            delegate.executeUpdate()
        }

        then:
        count == 2
    }

    void "setParameterList with Object array delegates to setParameter and throws QueryArgumentException"() {
        when:
        MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "DELETE FROM MutationQueryDelegateTestBook WHERE title IN (:titles)"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setParameterList("titles", ["Book Two", "Book Three"] as Object[])
        }

        then:
        thrown(QueryArgumentException)
    }

    void "list throws UnsupportedOperationException"() {
        when:
        MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
            )
            new MutationQueryDelegate(mq).list()
        }

        then:
        thrown(UnsupportedOperationException)
    }

    void "selectQuery returns null for mutation queries"() {
        when:
        def result = MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
            )
            new MutationQueryDelegate(mq).selectQuery()
        }

        then:
        result == null
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
