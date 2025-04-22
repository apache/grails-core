/* Copyright 2012-2013 SpringSource.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.cache;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cache.concurrent.ConcurrentMapCache;

/**
 * Extends the default implementation to return GrailsValueWrapper instances instead of
 * SimpleValueWrapper. This isn't useful for this implementation but is for others where
 * the native wrapper has more useful information that would otherwise be lost (for example
 * the TTL in the Ehcache Element class). This implementation exists so that all caches
 * consistently return a GrailsValueWrapper.
 *
 * @author Burt Beckwith
 */
public class GrailsConcurrentMapCache extends ConcurrentMapCache implements GrailsCache {

	public GrailsConcurrentMapCache(String name) {
		super(name);
	}

	public GrailsConcurrentMapCache(String name, boolean allowNullValues) {
		super(name, allowNullValues);
	}

	public GrailsConcurrentMapCache(String name, ConcurrentMap<Object, Object> store, boolean allowNullValues) {
		super(name, store, allowNullValues);
	}

	@Override
	public GrailsValueWrapper get(Object key) {
		Object value = getNativeCache().get(key);
		return value == null ? null : new GrailsValueWrapper(fromStoreValue(value), null);
	}

	@SuppressWarnings("unchecked")
	public Collection<Object> getAllKeys() {
		return getNativeCache().keySet();
	}
}
