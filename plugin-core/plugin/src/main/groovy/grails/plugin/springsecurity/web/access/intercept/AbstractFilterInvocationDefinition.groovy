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

import groovy.util.logging.Slf4j
import org.springframework.web.util.UrlPathHelper

import java.util.concurrent.CopyOnWriteArrayList

import jakarta.servlet.http.HttpServletRequest

import org.springframework.context.support.MessageSourceAccessor
import org.springframework.http.HttpMethod
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.SecurityConfig
import org.springframework.security.access.vote.AuthenticatedVoter
import org.springframework.security.access.vote.RoleVoter
import org.springframework.security.core.SpringSecurityMessageSource
import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource
import org.springframework.util.AntPathMatcher
import org.springframework.util.StringUtils

import grails.plugin.springsecurity.InterceptedUrl
import grails.util.GrailsUtil
import groovy.transform.CompileStatic

/**
 * @author Burt Beckwith
 */
@Slf4j
@CompileStatic
abstract class AbstractFilterInvocationDefinition implements FilterInvocationSecurityMetadataSource {

	protected static final Collection<ConfigAttribute> DENY = Collections.singletonList((ConfigAttribute)new SecurityConfig('_DENY_'))
	protected static final Collection<ConfigAttribute> ALLOW404 = Collections.singletonList((ConfigAttribute)new SecurityConfig('permitAll'))
	protected static final String ERROR404 = '__ERROR404__'

	protected RoleVoter roleVoter
	protected AuthenticatedVoter authenticatedVoter
	protected final List<InterceptedUrl> compiled = new CopyOnWriteArrayList<InterceptedUrl>()
	protected MessageSourceAccessor messages = SpringSecurityMessageSource.accessor
	protected AntPathMatcher urlMatcher = new AntPathMatcher()
	protected boolean initialized
	protected UrlPathHelper urlPathHelper = UrlPathHelper.defaultInstance

	/** Dependency injection for whether to reject if there's no matching rule. */
	boolean rejectIfNoRule

	/**
	 * Allows subclasses to be externally reset.
	 */
	void reset() {
		// override if necessary
	}

	Collection<ConfigAttribute> getAttributes(object) throws IllegalArgumentException {
		assert object, 'Object must be a FilterInvocation'
		assert supports(object.getClass()), 'Object must be a FilterInvocation'

		FilterInvocation filterInvocation = (FilterInvocation)object

		String url = determineUrl(filterInvocation)
		if (url == ERROR404) {
			return ALLOW404
		}

		log.trace 'getAttributes(): url is {} for FilterInvocation {}', url, filterInvocation

		Collection<ConfigAttribute> configAttributes = findConfigAttributes(url, filterInvocation.request.method)

		if (rejectIfNoRule && !configAttributes) {
			log.trace 'Returning DENY, rejectIfNoRule is true and no ConfigAttributes'
			// return something that cannot be valid this will cause the voters to abstain or deny
			return DENY
		}

		log.trace 'ConfigAttributes are {}', configAttributes
		configAttributes
	}

	protected String determineUrl(FilterInvocation filterInvocation) {
        final HttpServletRequest request = filterInvocation.request
		lowercaseAndStripQuerystring stripContextPath(urlPathHelper.getRequestUri(request), request)
	}

	protected boolean stopAtFirstMatch() {
		false
	}

	// for testing
	InterceptedUrl getInterceptedUrl(String url, HttpMethod httpMethod) {

		initialize()

		for (InterceptedUrl iu in compiled) {
			if (iu.httpMethod == httpMethod && iu.pattern == url) {
				return iu
			}
		}
	}

	protected Collection<ConfigAttribute> findConfigAttributes(String url, String requestMethod) {

		initialize()

		Collection<ConfigAttribute> configAttributes
		String configAttributePattern

		boolean stopAtFirstMatch = stopAtFirstMatch()
		for (InterceptedUrl iu in compiled) {

			if (requestMethod && iu.httpMethod && iu.httpMethod != HttpMethod.valueOf(requestMethod)) {
				log.debug "Request '{} {}' doesn't match '{} {}'", requestMethod, url, iu.httpMethod, iu.pattern
				continue
			}

			if (urlMatcher.match(iu.pattern, url)) {
				if (configAttributes == null || urlMatcher.match(configAttributePattern, iu.pattern)) {
					configAttributes = iu.configAttributes
					configAttributePattern = iu.pattern
					log.trace "new candidate for '{}': '{}':{}", url, iu.pattern, configAttributes
					if (stopAtFirstMatch) {
						break
					}
				}
			}
		}

		if (log.traceEnabled) {
			if (configAttributes == null) {
				log.trace "no config for '{}'", url
			}
			else {
				log.trace "config for '{}' is '{}':{}", url, configAttributePattern, configAttributes
			}
		}

		configAttributes
	}

	protected void initialize() {
		// override if necessary
	}

	boolean supports(Class<?> clazz) {
		FilterInvocation.isAssignableFrom clazz
	}

	Collection<ConfigAttribute> getAllConfigAttributes() {
		try {
			initialize()
		}
		catch (e) {
			log.error e.message, GrailsUtil.deepSanitize(e)
		}

		Collection<ConfigAttribute> all = new LinkedHashSet<ConfigAttribute>()
		for (InterceptedUrl iu in compiled) {
			all.addAll iu.configAttributes
		}
		Collections.unmodifiableCollection all
	}

    /**
     * Resolve the URI from {@link jakarta.servlet.http.HttpServletRequest}
     * @param request The {@link jakarta.servlet.http.HttpServletRequest}
     *
     * @return The resolved URI string
     * @deprecated Use {@link org.springframework.web.util.UrlPathHelper#getRequestUri(jakarta.servlet.http.HttpServletRequest request)} and {@link #stripContextPath} instead
     */
    @Deprecated
    protected String calculateUri(HttpServletRequest request) {
        stripContextPath(urlPathHelper.getRequestUri(request), request)
    }

    protected String stripContextPath(String uri, HttpServletRequest request) {
        String contextPath = request.contextPath
        if (contextPath && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length())
        }
        uri
    }

	protected String lowercaseAndStripQuerystring(String url) {

		String fixed = url.toLowerCase()

		int firstQuestionMarkIndex = fixed.indexOf('?')
		if (firstQuestionMarkIndex != -1) {
			fixed = fixed.substring(0, firstQuestionMarkIndex)
		}

		int firstHashtagIndex = fixed.indexOf('#')
		if (firstHashtagIndex != -1) {
			fixed = fixed.substring(0, firstHashtagIndex)
		}

		fixed
	}

	/**
	 * For debugging.
	 * @return an unmodifiable map of {@link AnnotationFilterInvocationDefinition}ConfigAttributeDefinition
	 * keyed by compiled patterns
	 */
	List<InterceptedUrl> getConfigAttributeMap() {
		Collections.unmodifiableList compiled
	}

	// fixes extra spaces, trailing commas, etc.
	protected List<String> split(String value) {
		if (!value.startsWith('ROLE_') && !value.startsWith('IS_')) {
			// an expression
			return Collections.singletonList(value)
		}

		String[] parts = StringUtils.commaDelimitedListToStringArray(value)
		List<String> cleaned = []
		for (String part in parts) {
			part = part.trim()
			if (part) {
				cleaned << part
			}
		}
		cleaned
	}

	protected void compileAndStoreMapping(InterceptedUrl iu) {
		String pattern = iu.pattern
		HttpMethod method = iu.httpMethod

		String key = pattern.toLowerCase()

		Collection<ConfigAttribute> configAttributes = iu.configAttributes

		InterceptedUrl replaced = storeMapping(key, method, Collections.unmodifiableCollection(configAttributes))
		if (replaced) {
			log.warn "Replaced rule for '{}' and ConfigAttributes {} with ConfigAttributes {}", key, replaced.configAttributes, configAttributes
		}
		else {
			log.trace "Storing ConfigAttributes {} for '{}' and HttpMethod {}", key, configAttributes, method
		}
	}

	protected InterceptedUrl storeMapping(String pattern, HttpMethod method, Collection<ConfigAttribute> configAttributes) {

		InterceptedUrl existing
		for (InterceptedUrl iu : compiled) {
			if (iu.pattern == pattern && iu.httpMethod == method) {
				existing = iu
				break
			}
		}

		if (existing) {
			log.trace 'Replacing existing mapping {}', existing
			compiled.remove existing
		}

		InterceptedUrl mapping = new InterceptedUrl(pattern, method, configAttributes)
		compiled << mapping
		log.trace 'Stored mapping {} for pattern "{}", HttpMethod {}, ConfigAttributes {}', mapping, pattern, method, configAttributes

		existing
	}

	protected void resetConfigs() {
		compiled.clear()
	}

	/**
	 * For admin/debugging - find all config attributes that apply to the specified URL (doesn't consider request method restrictions).
	 * @param url the URL
	 * @return matching attributes
	 */
	Collection<ConfigAttribute> findMatchingAttributes(String url) {
		for (InterceptedUrl iu in compiled) {
			if (urlMatcher.match(iu.pattern, url)) {
				return iu.configAttributes
			}
		}
		Collections.emptyList()
	}
}
