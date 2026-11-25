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
package org.grails.orm.hibernate.connections

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Whether a query is put in the query cache, by what asks for it: the mapping of the entity, the cache argument and the
 * cache call inside a criteria, at each way in. An explicit false wins over a mapping that caches, an explicit true over
 * a mapping that does not, and the argument wins over the call inside the criteria. The total
 * count of a paged list is never put in the query cache, also when the list is.
 */
@Issue(['https://github.com/apache/grails-core/issues/14472', 'https://github.com/apache/grails-core/issues/14458'])
class QueryCacheOptionSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
            'dataSource.url'          : 'jdbc:h2:mem:queryCacheOption;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate'     : 'create-drop',
            'dataSource.dialect'      : H2Dialect.name,
            'hibernate.cache.use_query_cache': 'true',
            'hibernate.cache'         : ['use_second_level_cache': true, 'region.factory_class': 'org.hibernate.cache.ehcache.EhCacheRegionFactory']
    ]), QcCached, QcPlain, QcBook, QcNote)

    @Shared
    @AutoCleanup
    HibernateDatastore cachingEverything = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
            'dataSource.url'                : 'jdbc:h2:mem:queryCacheOptionGlobal;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate'           : 'create-drop',
            'dataSource.dialect'            : H2Dialect.name,
            'grails.hibernate.cache.queries': true,
            'hibernate.cache.use_query_cache': 'true',
            'hibernate.cache'               : ['use_second_level_cache': true, 'region.factory_class': 'org.hibernate.cache.ehcache.EhCacheRegionFactory']
    ]), QcgCached, QcgPlain)

    void setup() {
        [QcCached, QcPlain, QcgCached, QcgPlain].each { Class type ->
            type.withNewTransaction {
                (1..3).each { int i -> type.newInstance(name: "n${i}").save(failOnError: true) }
            }
        }
        QcBook.withNewTransaction {
            QcCached author = QcCached.findByName('n1')
            (1..2).each { int i ->
                new QcBook(title: "b${i}", author: author).save(failOnError: true)
                new QcNote(title: "b${i}", author: author).save(failOnError: true)
            }
        }
        [datastore, cachingEverything].each { it.sessionFactory.statistics.setStatisticsEnabled(true) }
    }

    void cleanup() {
        [QcBook, QcNote, QcCached, QcPlain, QcgCached, QcgPlain].each { Class type -> type.withNewTransaction { type.executeUpdate("delete from ${type.simpleName}".toString()) } }
    }

    private int putsDuring(HibernateDatastore store, Closure query) {
        store.sessionFactory.statistics.clear()
        query.call()
        store.sessionFactory.statistics.queryCachePutCount
    }


    @Unroll
    void "#form on #mapping is #outcome"() {
        expect:
        putsDuring(store) { type.withNewSession { query.call(type) } } == (cached ? 1 : 0)

        where:
        form                                      | mapping                | store     | type     | query                                                                              || cached
        'a criteria list'                         | 'a cached mapping'     | datastore | QcCached | { Class t -> t.createCriteria().list { } }                                         || true
        'a criteria list with cache(false)'       | 'a cached mapping'     | datastore | QcCached | { Class t -> t.createCriteria().list { cache(false) } }                            || false
        'a criteria list with cache: false'       | 'a cached mapping'     | datastore | QcCached | { Class t -> t.createCriteria().list([cache: false]) { } }                        || false
        'a criteria list with cache: true'        | 'a cached mapping'     | datastore | QcCached | { Class t -> t.createCriteria().list([cache: true]) { } }                         || true
        'a criteria list'                         | 'an uncached mapping'  | datastore | QcPlain  | { Class t -> t.createCriteria().list { } }                                         || false
        'a criteria list with cache(true)'        | 'an uncached mapping'  | datastore | QcPlain  | { Class t -> t.createCriteria().list { cache(true) } }                             || true
        'a criteria list with cache: true'        | 'an uncached mapping'  | datastore | QcPlain  | { Class t -> t.createCriteria().list([cache: true]) { } }                          || true
        'a criteria count with cache(false)'      | 'a cached mapping'     | datastore | QcCached | { Class t -> t.createCriteria().count { cache(false) } }                           || false
        'a static list'                           | 'a cached mapping'     | datastore | QcCached | { Class t -> t.list() }                                                            || true
        'a static list with cache: false'         | 'a cached mapping'     | datastore | QcCached | { Class t -> t.list(cache: false) }                                                || false
        'a static list with cache: true'          | 'an uncached mapping'  | datastore | QcPlain  | { Class t -> t.list(cache: true) }                                                 || true
        'a dynamic finder with cache: false'      | 'a cached mapping'     | datastore | QcCached | { Class t -> t.findAllByName('n1', [cache: false]) }                              || false
        'a dynamic finder'                        | 'a cached mapping'     | datastore | QcCached | { Class t -> t.findAllByName('n1') }                                               || true
        'a where query with cache: false'         | 'a cached mapping'     | datastore | QcCached | { Class t -> t.where { name == 'n1' }.list(cache: false) }                         || false
        'a criteria list([:]) with cache(false)'  | 'a cached mapping'     | datastore | QcCached | { Class t -> t.createCriteria().list([:]) { cache(false) } }                       || false
        'a criteria list([:])'                    | 'a cached mapping'     | datastore | QcCached | { Class t -> t.createCriteria().list([:]) { } }                                   || true
        'a criteria list([:])'                    | 'an uncached mapping'  | datastore | QcPlain  | { Class t -> t.createCriteria().list([:]) { } }                                    || false
        'a criteria list([:]) with cache(true)'   | 'an uncached mapping'  | datastore | QcPlain  | { Class t -> t.createCriteria().list([:]) { cache(true) } }                        || true
        'a criteria list sorted through a cached association with cache(false)' | 'a cached mapping' | datastore | QcBook | { Class t -> t.createCriteria().list(max: 2, sort: 'author.name') { cache(false) } } || false
        'a criteria list sorted through a cached association with cache: false' | 'a cached mapping' | datastore | QcBook | { Class t -> t.createCriteria().list(max: 2, sort: 'author.name', cache: false) { } } || false
        'a static list sorted through a cached association with cache: false' | 'a cached mapping' | datastore | QcBook | { Class t -> t.list(max: 2, sort: 'author.name', cache: false) } || false
        'a dynamic finder sorted through a cached association with cache: false' | 'a cached mapping' | datastore | QcBook | { Class t -> t.findAllByTitle('b1', [sort: 'author.name', cache: false]) } || false
        'a criteria list sorted through a cached association' | 'a cached mapping' | datastore | QcBook | { Class t -> t.createCriteria().list(max: 2, sort: 'author.name') { } } || true
        'findAllWhere sorted through a cached association with cache: false' | 'a cached mapping' | datastore | QcBook | { Class t -> t.findAllWhere([title: 'b1'], [sort: 'author.name', cache: false]) } || false
        'findAllWhere sorted through a cached association' | 'a cached mapping' | datastore | QcBook | { Class t -> t.findAllWhere([title: 'b1'], [sort: 'author.name']) } || true
        'findAllWhere sorted through a cached association' | 'an uncached mapping' | datastore | QcNote | { Class t -> t.findAllWhere([title: 'b1'], [sort: 'author.name']) } || false
        'findAllWhere sorted through a cached association with cache: true' | 'an uncached mapping' | datastore | QcNote | { Class t -> t.findAllWhere([title: 'b1'], [sort: 'author.name', cache: true]) } || true
        'findWhere sorted through a cached association' | 'an uncached mapping' | datastore | QcNote | { Class t -> t.findWhere([title: 'b1'], [sort: 'author.name']) } || false
        'find by example sorted through a cached association' | 'an uncached mapping' | datastore | QcNote | { Class t -> t.find(new QcNote(title: 'b1'), [sort: 'author.name']) } || false
        'findAll by example sorted through a cached association' | 'an uncached mapping' | datastore | QcNote | { Class t -> t.findAll(new QcNote(title: 'b1'), [sort: 'author.name']) } || false
        'a criteria list sorted through a cached association with lock: true' | 'a cached mapping' | datastore | QcBook | { Class t -> t.createCriteria().list(max: 2, sort: 'author.name', lock: true) { } } || false
        'a criteria list with cache: true and cache(false) in the closure' | 'a cached mapping' | datastore | QcCached | { Class t -> t.createCriteria().list([cache: true]) { cache(false) } } || false
        'a criteria list with cache: false and cache(true) in the closure' | 'an uncached mapping' | datastore | QcPlain | { Class t -> t.createCriteria().list([cache: false]) { cache(true) } } || true
        'a criteria list sorted through a cached association' | 'an uncached mapping' | datastore | QcNote | { Class t -> t.createCriteria().list(max: 2, sort: 'author.name') { } } || false
        'a static list sorted through a cached association' | 'an uncached mapping' | datastore | QcNote | { Class t -> t.list(max: 2, sort: 'author.name') } || false
        'a criteria list sorted through a cached association with cache(true)' | 'an uncached mapping' | datastore | QcNote | { Class t -> t.createCriteria().list(max: 2, sort: 'author.name') { cache(true) } } || true
        'a criteria list, with every query cached' | 'a cached mapping'    | cachingEverything | QcgCached | { Class t -> t.createCriteria().list { } }                             || true
        'a criteria list with cache(false), with every query cached' | 'a cached mapping' | cachingEverything | QcgCached | { Class t -> t.createCriteria().list { cache(false) } }     || false
        'a criteria list([:]) with cache(false), with every query cached' | 'a cached mapping' | cachingEverything | QcgCached | { Class t -> t.createCriteria().list([:]) { cache(false) } } || false
        'a criteria list with cache: false, with every query cached' | 'an uncached mapping' | cachingEverything | QcgPlain | { Class t -> t.createCriteria().list([cache: false]) { } } || false
        'a static list with cache: false, with every query cached' | 'a cached mapping' | cachingEverything | QcgCached | { Class t -> t.list(cache: false) }                    || false
        'a dynamic finder with cache: false, with every query cached' | 'a cached mapping' | cachingEverything | QcgCached | { Class t -> t.findAllByName('n1', [cache: false]) } || false
        outcome = cached ? 'put in the query cache' : 'not put in the query cache'
    }

    private static Closure closureFor(String call) {
        switch (call) {
            case 'cache(true)': return { cache(true) }
            case 'cache(false)': return { cache(false) }
            case 'setCacheable(true)': return { setCacheable(true) }
            case 'setCacheable(false)': return { setCacheable(false) }
            default: return { }
        }
    }

    @Unroll
    void "a criteria list on #mapping with the argument #argument and #call in the closure is #outcome"() {
        given: 'the closure decides when it says anything, then the argument, then the mapping'
        boolean cached = call == 'cache(true)' || call == 'setCacheable(true)' ? true :
                call == 'cache(false)' || call == 'setCacheable(false)' ? false :
                argument != null ? argument : mapping == 'a cached mapping'
        Map args = argument == null ? [max: 2] : [max: 2, cache: argument]

        expect:
        putsDuring(datastore) { type.withNewSession { type.createCriteria().list(args, closureFor(call)) } } == (cached ? 1 : 0)

        where:
        [mapping, type, argument, call] << [
                [['a cached mapping', QcCached], ['an uncached mapping', QcPlain]],
                [null, true, false],
                ['nothing', 'cache(true)', 'cache(false)', 'setCacheable(true)', 'setCacheable(false)']
        ].combinations().collect { List row -> [row[0][0], row[0][1], row[1], row[2]] }
        outcome = 'put in the query cache or not, as the closure, the argument and the mapping decide'
    }

    @Unroll
    void "the total count of #form puts #puts queries in the cache"() {
        expect:
        putsDuring(store) {
            type.withNewSession {
                page.call(type).totalCount
            }
        } == puts

        where:
        form                                                         | store             | type      | page                                                                       || puts
        'a criteria list with cache: false on a cached mapping'      | datastore         | QcCached  | { Class t -> t.createCriteria().list(max: 2, cache: false) { } }          || 0
        'a criteria list with cache: false, with every query cached' | cachingEverything | QcgCached | { Class t -> t.createCriteria().list(max: 2, cache: false) { } }         || 0
        'a criteria list with cache: false on an uncached mapping, with every query cached' | cachingEverything | QcgPlain | { Class t -> t.createCriteria().list(max: 2, cache: false) { } } || 0
        'a criteria list([:]) with cache(false) on a cached mapping' | datastore         | QcCached  | { Class t -> t.createCriteria().list(max: 2) { cache(false) } }           || 0
        'a criteria list sorted through a cached association with cache(false)' | datastore | QcBook | { Class t -> t.createCriteria().list(max: 2, sort: 'author.name') { cache(false) } } || 0
        'a static list with cache: false on a cached mapping'        | datastore         | QcCached  | { Class t -> t.list(max: 2, cache: false) }                               || 0
        'a static list with cache: false, with every query cached'   | cachingEverything | QcgCached | { Class t -> t.list(max: 2, cache: false) }                               || 0
        'a where query with cache: false on a cached mapping'        | datastore         | QcCached  | { Class t -> t.where { name != 'x' }.list(max: 2, cache: false) }         || 0
        'a criteria list on a cached mapping, which is itself put in the cache'      | datastore         | QcCached  | { Class t -> t.createCriteria().list(max: 2) { } }                        || 1
        'a static list on a cached mapping, which is itself put in the cache'        | datastore         | QcCached  | { Class t -> t.list(max: 2) }                                             || 1
    }
}

@Entity
class QcCached {
    String name

    static mapping = {
        cache usage: 'nonstrict-read-write', include: 'all'
    }
}

@Entity
class QcPlain {
    String name
}

@Entity
class QcgCached {
    String name

    static mapping = {
        cache usage: 'nonstrict-read-write', include: 'all'
    }
}

@Entity
class QcgPlain {
    String name
}

@Entity
class QcBook {
    String title
    QcCached author

    static mapping = {
        cache usage: 'nonstrict-read-write', include: 'all'
    }
}

@Entity
class QcNote {
    String title
    QcCached author
}
