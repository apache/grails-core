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

import spock.lang.Specification

class GrailsConcurrentLinkedMapCacheSpec extends Specification {

    void "evictIfPresent removes a key and reports whether the cache held it"() {
        given:
        def cache = new GrailsConcurrentLinkedMapCache('cache', 10, true)
        cache.put('key', 'value')

        expect:
        cache.evictIfPresent('key')
        cache.get('key') == null
        !cache.evictIfPresent('key')
        !cache.evictIfPresent('missing')
    }

    void "evictIfPresent reports a key whose value is null as held"() {
        given:
        def cache = new GrailsConcurrentLinkedMapCache('cache', 10, true)
        cache.put('key', null)

        expect:
        cache.evictIfPresent('key')
        cache.size == 0
        !cache.evictIfPresent('key')
    }
}
