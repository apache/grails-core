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
package grails.plugin.springsecurity.acl.access.method

import grails.plugin.springsecurity.acl.util.ProxyUtils
import groovy.transform.CompileStatic
import org.springframework.beans.factory.InitializingBean
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.method.AbstractMethodSecurityMetadataSource
import org.springframework.security.access.method.MethodSecurityMetadataSource
import org.springframework.util.Assert

import java.lang.reflect.Method

/**
 * Replacement for DelegatingMethodSecurityMetadataSource which is final.
 * <p>
 * Makes two changes; unproxies classes, and treats an empty return value from
 * <code>MethodSecurityMetadataSource.getAttributes()</code> as equivalent to a
 * <code>null</code> return since an empty return stops the loop and another
 * source might have attributes.
 *
 * @author Ben Alex
 * @author Luke Taylor
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class ProxyAwareDelegatingMethodSecurityMetadataSource
extends AbstractMethodSecurityMetadataSource
implements InitializingBean {

	protected static final List<ConfigAttribute> NULL_CONFIG_ATTRIBUTE = Collections.emptyList()

	protected List<MethodSecurityMetadataSource> methodSecurityMetadataSources
	protected final Map<DefaultCacheKey, Collection<ConfigAttribute>> cache = [:]

	Collection<ConfigAttribute> getAttributes(Method m, Class<?> tc) {

		Method method = ProxyUtils.unproxy(m)
		Class<?> targetClass = ProxyUtils.unproxy(tc)

		DefaultCacheKey cacheKey = new DefaultCacheKey(method, targetClass)
		synchronized (cache) {
			Collection<ConfigAttribute> cached = cache[cacheKey]
			// Check for canonical value indicating there is no config attribute,
			if (cached == NULL_CONFIG_ATTRIBUTE) {
				return null
			}

			if (cached != null) {
				return cached
			}

			// No cached value, so query the sources to find a result
			Collection<ConfigAttribute> attributes
			for (MethodSecurityMetadataSource s in methodSecurityMetadataSources) {
				attributes = s.getAttributes(method, targetClass)
				if (attributes) {
					break
				}
			}

			// Put it in the cache.
			if (attributes == null) {
				cache[cacheKey] = NULL_CONFIG_ATTRIBUTE
				return null
			}

			logger.debug "Adding security method [$cacheKey] with attributes $attributes"

			cache[cacheKey] = attributes

			attributes
		}
	}

	Collection<ConfigAttribute> getAllConfigAttributes() {
		Set<ConfigAttribute> set = new HashSet<ConfigAttribute>()
		for (MethodSecurityMetadataSource s in methodSecurityMetadataSources) {
			Collection<ConfigAttribute> attrs = s.allConfigAttributes
			if (attrs) {
				set.addAll attrs
			}
		}
		set
	}

	/**
	 * Dependency injection for the sources.
	 * @param sources  the sources
	 */
	void setMethodSecurityMetadataSources(List<MethodSecurityMetadataSource> sources) {
		methodSecurityMetadataSources = sources
	}

	void afterPropertiesSet() {
		Assert.notEmpty methodSecurityMetadataSources, 'A list of MethodSecurityMetadataSources is required'
	}

	@CompileStatic
	static class DefaultCacheKey {
		protected final Method method
		protected final Class<?> targetClass

		DefaultCacheKey(Method m, Class<?> target) {
			method = m
			targetClass = target
		}

		@Override
		boolean equals(other) {
			if (is(other)) {
				return true
			}

			if (other instanceof DefaultCacheKey) {
				return method == other.method && targetClass == other?.targetClass
			}
		}

		@Override
		int hashCode() {
			method.hashCode() * 21 + (targetClass?.hashCode() ?: 0)
		}

		@Override
		String toString() {
			'CacheKey[' + (targetClass?.name ?: '-') + '; ' + method + ']'
		}
	}
}
