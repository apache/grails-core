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

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * The total of a page grouped by a property is the number of groups whether or not the query cache is on, and it is
 * never put in the query cache.
 */
@Issue('https://github.com/apache/grails-core/issues/14431')
class GroupedTotalCountQueryCacheSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore cachingEverything = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
            (Settings.SETTING_DB_CREATE)     : 'create-drop',
            'dataSource.url'                 : 'jdbc:h2:mem:groupedTotalQueryCache;LOCK_TIMEOUT=10000',
            'grails.hibernate.cache.queries' : true,
            'hibernate.cache.use_query_cache': 'true',
            'hibernate.cache'                : ['use_second_level_cache': true, 'region.factory_class': 'org.hibernate.cache.ehcache.EhCacheRegionFactory']
    ]), GcCachedItem, GcCachedPlain)

    void setup() {
        [GcCachedItem, GcCachedPlain].each { Class type ->
            type.withNewTransaction {
                type.executeUpdate("delete from ${type.simpleName}".toString())
                [['a', 'x'], ['a', 'x'], ['a', 'y'], ['b', 'x'], ['c', 'z']].each { List pair ->
                    type.newInstance(city: pair[0], name: pair[1]).save(failOnError: true)
                }
            }
        }
        cachingEverything.sessionFactory.statistics.setStatisticsEnabled(true)
    }

    void "the total of a grouped page is the number of groups, whatever the query cache is asked to do, and is not cached"() {
        given:
        List<Map> types = [[type: GcCachedItem, mapping: 'a cached mapping'], [type: GcCachedPlain, mapping: 'queries cached everywhere']]
        List<Map> asks = [[args: [:], asked: 'nothing'], [args: [cache: true], asked: 'cache: true'], [args: [cache: false], asked: 'cache: false'], [args: [:], asked: 'cache(true) in the block', inBlock: true]]
        List<Map> shapes = [
                [shape: 'one property', grouping: { -> projections { groupProperty('city') } }, expected: 3],
                [shape: 'two properties', grouping: { -> projections { groupProperty('city'); groupProperty('name') } }, expected: 4],
                [shape: 'two properties and a count', grouping: { -> projections { groupProperty('city'); groupProperty('name'); rowCount() } }, expected: 4]
        ]
        List<String> mismatches = []

        when:
        types.each { Map t ->
            asks.each { Map a ->
                shapes.each { Map sh ->
                    List<String> results = []
                    2.times {
                        try {
                            Class type = (Class) t.type
                            int total = type.withNewSession {
                                cachingEverything.sessionFactory.statistics.clear()
                                Closure grouping = (Closure) sh.grouping
                                if (a.inBlock) {
                                    Closure inner = grouping
                                    grouping = { ->
                                        Closure copy = (Closure) inner.clone()
                                        copy.delegate = delegate
                                        copy.resolveStrategy = Closure.DELEGATE_FIRST
                                        copy.call()
                                        cache(true)
                                    }
                                }
                                def page = type.createCriteria().list([max: 2] + (Map) a.args, grouping)
                                def stats = cachingEverything.sessionFactory.statistics
                                results << (stats.queryCachePutCount + stats.queryCacheHitCount > 0 ? 'page cached' : 'page uncached')
                                stats.clear()
                                int counted = page.totalCount
                                results << (cachingEverything.sessionFactory.statistics.queryCachePutCount == 0 ? 'uncached' : 'CACHED')
                                counted
                            }
                            results << String.valueOf(total)
                        }
                        catch (Throwable e) {
                            results << ('THROWS ' + e.class.simpleName + ': ' + (e.message ?: '').replaceAll('\\s+', ' ').take(90))
                        }
                    }
                    boolean engaged = a.asked == 'cache: true' || a.asked == 'cache(true) in the block' || (a.asked == 'nothing' && t.type == GcCachedItem)
                    String pageWord = engaged ? 'page cached' : 'page uncached'
                    String want = "${pageWord} uncached ${sh.expected} ${pageWord} uncached ${sh.expected}"
                    if (results.join(' ') != want) {
                        mismatches << "${t.mapping} | ${a.asked} | ${sh.shape}: ${results}".toString()
                    }
                }
            }
        }

        then: 'the number of groups, read twice, and never put in the query cache'
        mismatches == []
    }

    void "the total of a grouped page counts a group that was saved after the page was fetched"() {
        when:
        int total = GcCachedPlain.withNewTransaction {
            def page = GcCachedPlain.createCriteria().list(max: 1) { projections { groupProperty('city') } }
            new GcCachedPlain(city: 'd', name: 'q').save(failOnError: true)
            page.totalCount
        }

        then: 'a, b, c and the new group d'
        total == 4
    }

    void "the total of a grouped page and an unpaged list of the same groups do not share a cached result"() {
        when:
        List<String> outcome = []
        cachingEverything.sessionFactory.statistics.clear()
        try {
            outcome << GcCachedItem.withNewSession {
                GcCachedItem.createCriteria().list { projections { groupProperty('city') } }.sort().toString()
            }
            outcome << (cachingEverything.sessionFactory.statistics.queryCachePutCount > 0 ? 'cache is on' : 'CACHE IS NOT ON')
            outcome << GcCachedItem.withNewSession {
                GcCachedItem.createCriteria().list(max: 1) { projections { groupProperty('city') } }.totalCount.toString()
            }
            outcome << GcCachedItem.withNewSession {
                GcCachedItem.createCriteria().list { projections { groupProperty('city') } }.sort().toString()
            }
        }
        catch (Throwable e) {
            outcome << ('THROWS ' + e.class.simpleName + ': ' + (e.message ?: '').replaceAll('\\s+', ' ').take(100))
        }

        then: 'the groups, proof that the query cache was used, their number, and the groups again'
        outcome == ['[a, b, c]', 'cache is on', '3', '[a, b, c]']
    }

    void "a page past the last group still reports the number of groups"() {
        when:
        int total = GcCachedPlain.withNewSession {
            def page = GcCachedPlain.createCriteria().list(max: 2, offset: 99) {
                projections { groupProperty('city') }
                order('city')
            }
            assert page.isEmpty()
            page.totalCount
        }

        then:
        total == 3
    }
}

@Entity
class GcCachedItem {
    String city
    String name

    static mapping = {
        cache true
    }
}

@Entity
class GcCachedPlain {
    String city
    String name
}
