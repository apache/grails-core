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

import groovyx.gpars.GParsPool
import org.junit.Test

/**
 * @author Burt Beckwith
 */
class GrailsConcurrentMapCacheManagerTests {

	private GrailsConcurrentMapCacheManager manager = new GrailsConcurrentMapCacheManager()

	@Test
	void testGetCacheNames() {
		'groovy'.each { assert manager.getCache(it) }
		assert 5 == manager.cacheNames.size()
		assert ['g', 'o', 'r', 'v', 'y'] == manager.cacheNames.sort()
		assert 5 == manager.cacheMap.values().size()
		assert manager.cacheMap.values().every { it instanceof GrailsConcurrentMapCache }
	}

	@Test
	void testGetCache() {

		String key = 'foo123'

		assert !manager.cacheExists(key)

		assert manager.getCache(key)
		assert manager.cacheExists(key)
	}

	@Test
	void testCacheExists() {
		String key = 'foo'
		assert !manager.cacheExists(key)
		assert manager.getCache(key)
		assert manager.cacheExists(key)
	}

	@Test
	void testDestroyCache() {

		String key = 'foo1234'

		assert !manager.cacheExists(key)

		assert manager.getCache(key)

		assert manager.destroyCache(key)
		assert !manager.cacheExists(key)
	}

	@Test
	void testCacheCreationParallelAccess() {
		assert !manager.cacheExists('testCache')

		100.times {
			GParsPool.withPool {
				(0..10).everyParallel {
					assert manager.getCache('testCache')
				}

				manager.destroyCache('testCache')
			}
		}
	}

	@Test
	void testCacheGetParallelAccess() {
		manager.getCache('testCache')

		assert manager.cacheExists('testCache')

		100.times {
			GParsPool.withPool {
				(0..100).eachParallel {
					assert manager.getCache('testCache')
				}
			}
		}
	}
}
