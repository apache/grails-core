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

import org.hibernate.Session
import org.hibernate.stat.Statistics
import spock.lang.Issue

/**
 * The query cache is a per-call setting of a criteria builder: {@code cache true} in the closure of one call makes
 * that query cacheable, and the next call on the same builder is not. It runs with the query cache on, so a cacheable
 * query shows as a put to the cache and a repeated one as a hit.
 */
@Issue('https://github.com/apache/grails-core/issues/16862')
class ReusedBuilderQueryCacheSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.grailsConfig = [
                'dataSource': ['url': 'jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000', 'dbCreate': 'create-drop'],
                'hibernate' : [
                        'flush'  : ['mode': 'COMMIT'],
                        'hbm2ddl': ['auto': 'create'],
                        'cache'  : [
                                'queries'               : true,
                                'use_second_level_cache': true,
                                'use_query_cache'       : true,
                                'region'                : ['factory_class': 'org.hibernate.cache.jcache.internal.JCacheRegionFactory']
                        ]
                ]
        ]
        manager.registerDomainClasses(ReusedStateClub, ReusedStateTeam)
    }

    void setup() {
        // committed, because the query cache does not serve a table that an open transaction has written
        ReusedStateTeam.withNewTransaction {
            ReusedStateTeam.executeUpdate('delete from ReusedStateTeam')
            ['both', 'x', 'y', 'z'].each { new ReusedStateTeam(name: it).save(failOnError: true) }
        }
        ReusedStateTeam.withSession { Session session ->
            session.clear()
            Statistics statistics = session.sessionFactory.statistics
            statistics.statisticsEnabled = true
            statistics.clear()
        }
    }

    void 'a cacheable query is put to the cache and a repeated one is a hit'() {
        when:
        newSession {
            def c = ReusedStateTeam.createCriteria()
            c.list { cache true }
            c.list { cache true }
        }

        then:
        statistics().queryCachePutCount == 1
        statistics().queryCacheHitCount == 1
    }

    void 'cache set in the closure of one call does not reach the next call'() {
        when:
        newSession {
            def c = ReusedStateTeam.createCriteria()
            c.list { cache true }
            c.list { }
        }

        then:
        statistics().queryCachePutCount == 1
        statistics().queryCacheHitCount == 0
    }

    /**
     * A cached result is only served to a session that began after the last write to its table, and the session of
     * the test began before the fixture was committed.
     */
    private static void newSession(Closure<?> work) {
        ReusedStateTeam.withNewSession(work)
    }

    private static Statistics statistics() {
        ReusedStateTeam.withSession { Session session -> session.sessionFactory.statistics }
    }
}
