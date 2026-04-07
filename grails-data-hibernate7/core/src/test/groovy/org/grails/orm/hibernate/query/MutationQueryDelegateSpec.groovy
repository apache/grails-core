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
import org.hibernate.query.QueryFlushMode

class MutationQueryDelegateSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([MutationQueryDelegateTestBook])
    }

    void setup() {
        MutationQueryDelegateTestBook.withTransaction {
            new MutationQueryDelegateTestBook(title: "Book One",   pages: 100).save(flush: true, failOnError: true)
            new MutationQueryDelegateTestBook(title: "Book Two",   pages: 200).save(flush: true, failOnError: true)
            new MutationQueryDelegateTestBook(title: "Book Three", pages: 300).save(flush: true, failOnError: true)
        }
    }

    void cleanup() {
        MutationQueryDelegateTestBook.withTransaction {
            MutationQueryDelegateTestBook.executeUpdate("DELETE FROM MutationQueryDelegateTestBook")
        }
    }

    private MutationQuery buildMutationQuery(String hql) {
        sessionFactory.currentSession.createMutationQuery(hql)
    }

    void "constructor wraps the MutationQuery"() {
        given:
        MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET title = :title WHERE title = :old"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)

            expect:
            delegate != null
        }
    }

    void "setTimeout delegates to the underlying MutationQuery"() {
        when:
        MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setTimeout(30)
        }

        then:
        noExceptionThrown()
    }

    void "setQueryFlushMode delegates to the underlying MutationQuery"() {
        when:
        MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setQueryFlushMode(QueryFlushMode.MANUAL)
        }

        then:
        noExceptionThrown()
    }

    void "setParameter by name delegates and executeUpdate returns affected count"() {
        when:
        int count = MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = :newPages WHERE title = :title"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setParameter("newPages", 999)
            delegate.setParameter("title", "Book One")
            delegate.executeUpdate()
        } as int

        then:
        count == 1
    }

    void "setParameter by name with type delegates to MutationQuery"() {
        when:
        int count = MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = :newPages WHERE title = :title"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setParameter("newPages", Integer.valueOf(42), Integer.class)
            delegate.setParameter("title", "Book Two", String.class)
            delegate.executeUpdate()
        } as int

        then:
        count == 1
    }

    void "setParameter by position delegates to MutationQuery"() {
        when:
        int count = MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = ?1 WHERE title = ?2"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setParameter(1, 77)
            delegate.setParameter(2, "Book Three")
            delegate.executeUpdate()
        } as int

        then:
        count == 1
    }

    void "setParameter by position with type delegates to MutationQuery"() {
        when:
        int count = MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = ?1 WHERE title = ?2"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setParameter(1, Integer.valueOf(88), Integer.class)
            delegate.setParameter(2, "Book One", String.class)
            delegate.executeUpdate()
        } as int

        then:
        count == 1
    }

    void "setParameterList with Collection delegates as parameter value"() {
        when:
        int count = MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "DELETE FROM MutationQueryDelegateTestBook WHERE title IN (:titles)"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setParameterList("titles", (Collection<?>) ["Book One", "Book Two"])
            delegate.executeUpdate()
        } as int

        then:
        count == 2
    }

    void "setParameterList with Object array delegates as parameter value"() {
        when:
        int count = MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "DELETE FROM MutationQueryDelegateTestBook WHERE title IN (:titles)"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.setParameterList("titles", ["Book Two", "Book Three"] as Object[])
            delegate.executeUpdate()
        } as int

        then:
        count == 2
    }

    void "list() throws UnsupportedOperationException"() {
        when:
        MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.list()
        }

        then:
        thrown(UnsupportedOperationException)
    }

    void "selectQuery() returns null for mutation queries"() {
        expect:
        MutationQueryDelegateTestBook.withTransaction {
            MutationQuery mq = buildMutationQuery(
                "UPDATE MutationQueryDelegateTestBook SET pages = 0 WHERE pages > 0"
            )
            MutationQueryDelegate delegate = new MutationQueryDelegate(mq)
            delegate.selectQuery() == null
        }
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
