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

import groovy.transform.CompileStatic
import org.grails.plugin.cache.GrailsCacheManager;

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.springframework.cache.Cache

/**
 * Based on org.springframework.cache.concurrent.ConcurrentMapCacheManager.
 *
 * @author Juergen Hoeller
 * @author Burt Beckwith
 */
@CompileStatic
class GrailsConcurrentMapCacheManager implements GrailsCacheManager {

	protected final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<String, Cache>()

	Collection<String> getCacheNames() {
		Collections.unmodifiableSet(cacheMap.keySet())
	}

	Cache getCache(String name) {
		Cache cache = cacheMap.get(name)
		if (cache == null) {
			cache = createConcurrentMapCache(name)
			Cache existing = cacheMap.putIfAbsent(name, cache)
			if (existing != null) {
				cache = existing
			}
		}
		cache
	}

	boolean cacheExists(String name) {
		getCacheNames().contains(name)
	}

	boolean destroyCache(String name) {
		cacheMap.remove(name) != null
	}

	protected GrailsConcurrentMapCache createConcurrentMapCache(String name) {
		new GrailsConcurrentMapCache(name)
	}

	void setConfiguration(CachePluginConfiguration configuration) {
		configuration.caches.each { String key, CachePluginConfiguration.CacheConfig value ->
			getCache(key)
		}
	}
}
