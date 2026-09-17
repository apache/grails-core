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

import java.util.concurrent.ConcurrentMap

import org.springframework.cache.support.SimpleValueWrapper
import spock.lang.Specification

/**
 * @author Jakob Drangmeister
 */
class GrailsConcurrentLinkedMapCacheTests extends Specification {

   void 'creates caches with configured capacity and null value policy'() {
      when:
      GrailsConcurrentLinkedMapCache smallCache = new GrailsConcurrentLinkedMapCache('smallCache', 1000)

      then:
      smallCache.name == 'smallCache'
      smallCache.nativeCache instanceof ConcurrentMap
      smallCache.capacity == 1000
      smallCache.allowNullValues

      when:
      GrailsConcurrentLinkedMapCache bigCache = new GrailsConcurrentLinkedMapCache('bigCache', 5000000, false)

      then:
      bigCache.name == 'bigCache'
      bigCache.nativeCache instanceof ConcurrentMap
      bigCache.capacity == 5000000
      !bigCache.allowNullValues
   }

   void 'exposes Caffeine native cache as concurrent map'() {
      given:
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('cache', 10, true)

      when:
      cache.put("key", "value")

      then:
      cache.nativeCache.get('key') == 'value'
      cache.nativeCache.getClass().name.startsWith('com.github.benmanes.caffeine.cache.')
   }

   void 'puts and gets cache entries'() {
      given:
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('cache', 1000, true)

      when:
      cache.put('key', 'value')

      then:
      cache.size == 1
      GrailsValueWrapper value = cache.get("key")
      value.get() == 'value'
   }

   void 'putIfAbsent keeps existing cache value'() {
      given:
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('cache', 1000, true)
      cache.put('key', 'value')

      expect:
      cache.putIfAbsent('key', 'value') instanceof SimpleValueWrapper
      cache.size == 1
   }

   void 'evicts cache entries'() {
      given:
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('cache', 10, true)
      cache.put('key', 'value')

      expect:
      cache.size == 1

      when:
      cache.evict('key')

      then:
      cache.size == 0
   }

   void 'limits cache size to configured capacity'() {
      given:
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('cache', 1000, true)

      when:
      for (int i = 0; i < 2000; i++) {
          cache.put(i, i)
      }

      then:
      cache.capacity == 1000
      cache.size == 1000
   }

   void 'returns hottest keys from cache eviction policy'() {
      given:
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('cache', 10, true)

      for (int i = 0; i < 10; i++) {
         cache.put(i, i)
      }

      when:
      cache.get(1)
      cache.get(2)

      then:
      cache.hottestKeys.containsAll([1, 2])

      when:
      for (int i = 10; i < 19; i++) {
         cache.put(i, i)
      }

      then:
      cache.hottestKeys.contains(2)
   }

   void 'clears all cache entries'() {
      given:
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('cache', 1000, true)
      cache.put('key', 'value')

      expect:
      cache.capacity == 1000
      cache.size == 1

      when:
      cache.clear()

      then:
      cache.size == 0
   }

}
