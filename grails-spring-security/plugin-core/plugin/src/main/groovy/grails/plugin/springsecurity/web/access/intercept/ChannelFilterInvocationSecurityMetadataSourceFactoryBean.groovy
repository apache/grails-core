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
package grails.plugin.springsecurity.web.access.intercept

import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.SecurityConfig
import org.springframework.security.web.access.intercept.DefaultFilterInvocationSecurityMetadataSource
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.util.AntPathMatcher

import groovy.transform.CompileStatic

/**
 * Factory bean that builds a {@link FilterInvocationSecurityMetadataSource} for channel security.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class ChannelFilterInvocationSecurityMetadataSourceFactoryBean implements FactoryBean<FilterInvocationSecurityMetadataSource>, InitializingBean {

	protected static final Collection<String> SUPPORTED = [
			'ANY_CHANNEL', 'REQUIRES_SECURE_CHANNEL', 'REQUIRES_INSECURE_CHANNEL']
	protected AntPathMatcher urlMatcher = new AntPathMatcher()
	protected DefaultFilterInvocationSecurityMetadataSource source

	/**
	 * Dependency injection for the definition maps. Each map has a single entry, with URL patterns stored under the
	 * 'pattern' key and ANY_CHANNEL, REQUIRES_SECURE_CHANNEL, or REQUIRES_INSECURE_CHANNEL stored under the 'access' key.
	 */
	List<Map<String, String>> definition

	FilterInvocationSecurityMetadataSource getObject() {
		source
	}

	Class<DefaultFilterInvocationSecurityMetadataSource> getObjectType() {
		DefaultFilterInvocationSecurityMetadataSource
	}

	boolean isSingleton() {
		true
	}

	void afterPropertiesSet() {
		assert definition, 'definition map is required'
		assert urlMatcher, 'urlMatcher is required'

		source = new DefaultFilterInvocationSecurityMetadataSource(buildMap())
	}

	protected LinkedHashMap<RequestMatcher, Collection<ConfigAttribute>> buildMap() {
		LinkedHashMap<RequestMatcher, Collection<ConfigAttribute>> map = [:]
		definition.each { Map<String, String> entry ->
			String access = entry.access
			assert access != null, "The rule for URL '$access' cannot be null"
			access = access.trim()

			assert SUPPORTED.contains(access),
				"The rule for URL '$access' must be one of REQUIRES_SECURE_CHANNEL, REQUIRES_INSECURE_CHANNEL, or ANY_CHANNEL"

			map[new AntPathRequestMatcher(entry.pattern, null, false)] = SecurityConfig.createList(access)
		}

		map
	}
}
