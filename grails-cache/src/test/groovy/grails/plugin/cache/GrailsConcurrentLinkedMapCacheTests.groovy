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

import org.springframework.cache.support.SimpleValueWrapper
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import org.junit.Test

/**
 * @author Jakob Drangmeister
 */
class GrailsConcurrentLinkedMapCacheTests {

    @Test
   void testCreateCache() {
      GrailsConcurrentLinkedMapCache smallCache = new GrailsConcurrentLinkedMapCache("smallCache", 1000)

      assert smallCache.getName() == "smallCache"
      assert smallCache.getNativeCache() instanceof ConcurrentLinkedHashMap
      assert smallCache.getCapacity() == 1000
      assert smallCache.isAllowNullValues() == true

      GrailsConcurrentLinkedMapCache bigCache = new GrailsConcurrentLinkedMapCache("bigCache", 5000000, false)

      assert bigCache.getName() == "bigCache"
      assert bigCache.getNativeCache() instanceof ConcurrentLinkedHashMap
      assert bigCache.getCapacity() == 5000000
      assert bigCache.isAllowNullValues() == false
   }

   @Test
   void testPutAndGet() {
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache("cache", 1000, true)

      cache.put("key", "value");

      assert cache.getSize() == 1
      GrailsValueWrapper value = cache.get("key")
      assert value.get().equals("value")
   }

   @Test
   void testPutIfAbsent() {
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache("cache", 1000, true)
      cache.put("key", "value")
      cache.putIfAbsent("key", "value") instanceof SimpleValueWrapper
      assert cache.getSize() == 1
   }

   @Test
   void testEvict() {
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache("cache", 10, true)
      cache.put("key", "value");
      assert cache.getSize() == 1

      cache.evict("key")
      assert cache.getSize() == 0

   }

   @Test
   void testCacheCapacity() {
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache("cache", 1000, true)
      assert cache.getCapacity() == 1000

      for(int i = 0; i < 2000; i++) {
         cache.put(i, i)
      }

      assert cache.getSize() == 1000
   }

   @Test
   void testCacheGetHottestKeys() {
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache("cache", 10, true)

      for(int i = 0; i < 10; i++) {
         cache.put(i, i);
      }

      cache.get(1)
      cache.get(2)

      assert cache.getHottestKeys()[0] == 2
      assert cache.getHottestKeys()[1] == 1

      for(int i = 10; i < 19; i++) {
         cache.put(i, i);
      }

      assert cache.getHottestKeys()[cache.getSize()-1] == 2

   }

   @Test
   void testClear() {
      GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache("cache", 1000, true)
      assert cache.getCapacity() == 1000

      cache.put("key", "value")
      assert cache.getSize() == 1

      cache.clear()

      assert cache.getSize() == 0
   }

}
