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

package grails.gorm.tests

import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.query.HibernateQuery
import org.hibernate.internal.CriteriaImpl
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The {@code lock} argument of the finders takes a write lock for true and none for false. A finder asked not to lock
 * must not end in {@code for update}, whatever the code does with a flag that is only a value of a request parameter.
 */
@Issue('https://github.com/apache/grails-core/issues/14501')
class QueryLockSpec extends Specification {

    @Shared SqlCapture sqlCapture = new SqlCapture()

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'hibernate.session_factory.statement_inspector': sqlCapture
            ),
            Club
    )

    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    private List<String> selects(Closure query) {
        sqlCapture.clear()
        query.call()
        sqlCapture.statements.findAll { it.toLowerCase().startsWith('select') }
    }

    @Rollback
    @Unroll
    void 'findByName with lock: #flag #outcome'() {
        given:
        new Club(name: 'a').save(flush: true)

        when:
        List<String> sql = selects { Club.findByName('a', [lock: flag]) }

        then:
        sql.any { it.toLowerCase().contains('for update') } == flag

        where:
        flag << [true, false]
        outcome = flag ? 'takes a write lock' : 'takes no lock'
    }

    @Rollback
    @Unroll
    void 'findAllByName with max and lock: #flag #outcome'() {
        given:
        new Club(name: 'a').save(flush: true)
        new Club(name: 'a').save(flush: true)

        when:
        List<String> sql = selects { Club.findAllByName('a', [max: 1, lock: flag]) }

        then:
        sql.any { it.toLowerCase().contains('for update') } == flag

        where:
        flag << [true, false]
        outcome = flag ? 'takes a write lock' : 'takes no lock'
    }

    @Rollback
    void 'a finder that is not asked to lock takes no lock'() {
        given:
        new Club(name: 'a').save(flush: true)

        when:
        List<String> sql = selects { Club.findByName('a') }

        then:
        !sql.any { it.toLowerCase().contains('for update') }
    }

    @Rollback
    void 'lock(false) after lock(true) takes no lock'() {
        given:
        new Club(name: 'a').save(flush: true)

        when:
        List<String> sql = selects {
            HibernateQuery query = hibernateDatastore.currentSession.createQuery(Club) as HibernateQuery
            query.lock(true).lock(false)
            query.list()
        }

        then:
        !sql.any { it.toLowerCase().contains('for update') }
    }

    @Rollback
    @Unroll
    void 'a query that was cached is #outcome after lock(true).lock(false)'() {
        given:
        HibernateQuery query = hibernateDatastore.currentSession.createQuery(Club) as HibernateQuery
        query.cache(cached)

        when:
        query.lock(true)

        then: 'a locking query is never cached'
        !((CriteriaImpl) query.hibernateCriteria).cacheable

        when:
        query.lock(false)

        then: 'a query that does not lock is cached as it was before it was asked to lock'
        ((CriteriaImpl) query.hibernateCriteria).cacheable == cached

        where:
        cached << [true, false]
        outcome = cached ? 'cached again' : 'still not cached'
    }

    static class SqlCapture implements StatementInspector {
        final List<String> statements = Collections.synchronizedList(new ArrayList<String>())

        @Override
        String inspect(String sql) {
            statements.add(sql)
            return sql
        }

        void clear() {
            statements.clear()
        }
    }
}
