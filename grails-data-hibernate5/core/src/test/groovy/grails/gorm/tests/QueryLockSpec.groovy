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

    @Rollback
    @Unroll
    void 'cache(#first) then lock(true) then cache(#second) then lock(false) leaves the cache #outcome'() {
        given:
        HibernateQuery query = hibernateDatastore.currentSession.createQuery(Club) as HibernateQuery

        when:
        query.cache(first).lock(true).cache(second)

        then: 'a locking query is never cached, whatever cache() is asked for'
        !((CriteriaImpl) query.hibernateCriteria).cacheable

        when:
        query.lock(false)

        then: 'the setting that was asked for last is the one that comes back'
        ((CriteriaImpl) query.hibernateCriteria).cacheable == second

        where:
        first | second
        true  | false
        false | true
        true  | true
        false | false
        outcome = second ? 'on' : 'off'
    }

    private List<Club> viaRoute(String route, Object flag) {
        Map args = [lock: flag]
        switch (route) {
            case 'findBy': return [Club.findByName('a', args)]
            case 'findAllBy': return Club.findAllByName('a', args)
            case 'paged findAllBy': return Club.findAllByName('a', args + [max: 1])
            case 'list': return Club.list(args)
            case 'findAll closure': return Club.findAll(args) { name == 'a' }
            case 'where.list': return Club.where { name == 'a' }.list(args)
            case 'where.find': return [Club.where { name == 'a' }.find(args)]
            case 'criteria list': return Club.createCriteria().list(args) { eq('name', 'a') }
            default: throw new IllegalArgumentException(route)
        }
    }

    @Rollback
    @Unroll
    void 'lock: #flag on #route #outcome'() {
        given:
        new Club(name: 'a').save(flush: true)
        Club.withSession { it.clear() }

        when:
        List<String> sql = selects { viaRoute(route, flag) }

        then: 'only true, or the text true, ends in for update'
        sql.any { it.toLowerCase().contains('for update') } == locks

        where:
        [route, flag] << [
                ['findBy', 'findAllBy', 'paged findAllBy', 'list', 'findAll closure', 'where.list', 'where.find', 'criteria list'],
                [true, false, null, 'true', 'false']
        ].combinations()
        locks = flag == true || flag == 'true'
        outcome = locks ? 'takes a write lock' : 'takes no lock'
    }

    @Rollback
    @Unroll
    void 'the calls #calls leave the query #lockText and #cacheText'() {
        given:
        new Club(name: 'a').save(flush: true)
        HibernateQuery query = hibernateDatastore.currentSession.createQuery(Club) as HibernateQuery
        boolean locked = false
        Boolean asked = null

        when:
        List<String> sql = selects {
            calls.each { String call ->
                switch (call) {
                    case 'lock(true)': query.lock(true); locked = true; break
                    case 'lock(false)': query.lock(false); locked = false; break
                    case 'cache(true)': query.cache(true); asked = true; break
                    default: query.cache(false); asked = false
                }
            }
            query.list()
        }

        then: 'the lock is the last one asked for, and a locking query is never cached'
        sql.any { it.toLowerCase().contains('for update') } == locked
        ((CriteriaImpl) query.hibernateCriteria).cacheable == (!locked && asked == true)

        where:
        calls << [
                ['lock(true)'], ['lock(false)'], ['lock(true)', 'lock(true)'], ['lock(false)', 'lock(true)'], ['lock(true)', 'lock(false)'],
                ['cache(true)', 'lock(true)'], ['lock(true)', 'cache(true)'], ['cache(true)', 'lock(true)', 'lock(false)'],
                ['lock(true)', 'cache(true)', 'lock(false)'], ['cache(true)', 'lock(true)', 'cache(false)', 'lock(false)'],
                ['cache(false)', 'lock(true)', 'cache(true)', 'lock(false)'], ['cache(true)', 'lock(true)', 'lock(false)', 'lock(true)'],
                ['cache(true)', 'lock(true)', 'lock(false)', 'lock(true)', 'lock(false)'], ['lock(true)', 'lock(false)', 'cache(true)'],
                ['cache(true)', 'cache(false)', 'lock(true)', 'lock(false)']
        ]
        lockText = calls.findAll { it.startsWith('lock') }.with { it ? it.last() : 'not asked' }
        cacheText = calls.findAll { it.startsWith('cache') }.with { it ? it.last() : 'not asked' }
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
