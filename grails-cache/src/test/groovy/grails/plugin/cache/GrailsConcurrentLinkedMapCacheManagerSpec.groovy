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
package grails.plugin.cache

import groovy.concurrent.ParallelScope

import spock.lang.Specification

class GrailsConcurrentLinkedMapCacheManagerSpec extends Specification {

    private final GrailsConcurrentLinkedMapCacheManager manager = new GrailsConcurrentLinkedMapCacheManager()

    void 'cache names are the created caches'() {
        when:
        'groovy'.each { assert manager.getCache(it) }

        then:
        manager.cacheNames.size() == 5
        manager.cacheNames.sort() == ['g', 'o', 'r', 'v', 'y']
        manager.cacheMap.values().size() == 5
        manager.cacheMap.values().every { it instanceof GrailsConcurrentLinkedMapCache }
    }

    void 'getting a cache creates it'() {
        given:
        String key = 'foo123'

        expect:
        !manager.cacheExists(key)

        when:
        def cache = manager.getCache(key)

        then:
        cache
        manager.cacheExists(key)
    }

    void 'a destroyed cache no longer exists'() {
        given:
        String key = 'foo1234'
        assert !manager.cacheExists(key)
        assert manager.getCache(key)

        when:
        boolean destroyed = manager.destroyCache(key)

        then:
        destroyed
        !manager.cacheExists(key)
    }

    void 'the same cache is created once under parallel access'() {
        expect:
        !manager.cacheExists('testCache')

        and:
        100.times {
            assert (0..10).everyParallel { manager.getCache('testCache') != null }
            assert (0..10).collectParallel { manager.getCache('testCache') }.toSet().size() == 1
            manager.destroyCache('testCache')
        }
    }

    void 'an existing cache is returned under parallel access'() {
        given:
        def created = manager.getCache('testCache')

        expect:
        manager.cacheExists('testCache')

        and:
        100.times {
            ParallelScope.withPool(8) { scope ->
                (0..100).eachParallel {
                    assert manager.getCache('testCache').is(created)
                }
            }
        }
    }
}
