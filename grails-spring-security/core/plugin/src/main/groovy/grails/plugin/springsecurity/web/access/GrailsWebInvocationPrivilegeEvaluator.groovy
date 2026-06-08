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
package grails.plugin.springsecurity.web.access

import groovy.util.logging.Slf4j

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.intercept.AbstractSecurityInterceptor
import org.springframework.security.core.Authentication
import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.DefaultWebInvocationPrivilegeEvaluator

import grails.util.GrailsUtil
import groovy.transform.CompileStatic

/**
 * <code>createFilterInvocation()</code> is private in the base class so this is required to create
 * a mock request that works with Grails - more methods get called than are expected in the mock request
 * that the base class uses.
 *
 * @author Burt Beckwith
 */
@Slf4j
@CompileStatic
class GrailsWebInvocationPrivilegeEvaluator extends DefaultWebInvocationPrivilegeEvaluator {

	protected static final FilterChain DUMMY_CHAIN = new FilterChain() {
		void doFilter(ServletRequest req, ServletResponse res) {
			throw new UnsupportedOperationException('GrailsWebInvocationPrivilegeEvaluator does not support filter chains')
		}
	}

	protected static final HttpServletResponse DUMMY_RESPONSE = DummyResponseCreator.createInstance()

	protected AbstractSecurityInterceptor interceptor

	/**
	 * Constructor.
	 * @param securityInterceptor the security interceptor
	 */
	GrailsWebInvocationPrivilegeEvaluator(final AbstractSecurityInterceptor securityInterceptor) {
		super(securityInterceptor)
		interceptor = securityInterceptor
	}

	@Override
	boolean isAllowed(String contextPath, String uri, String method, Authentication authentication) {
		assert uri, 'uri parameter is required'

		if (contextPath == null) {
			contextPath = '/ctxpath'
		}

		FilterInvocation fi = createFilterInvocation(contextPath, uri, method)
		log.trace "isAllowed: contextPath '{}' uri '{}' method '{}' Authentication {} FilterInvocation {}",
				contextPath, uri, method, authentication, fi

		Collection<ConfigAttribute> attrs = interceptor.obtainSecurityMetadataSource().getAttributes(fi)
		if (attrs == null) {
			log.trace 'No ConfigAttributes found'
			return !interceptor.rejectPublicInvocations
		}

		if (!authentication) {
			log.trace 'Not authenticated'
			return false
		}

		try {
			interceptor.accessDecisionManager.decide authentication, fi, attrs
			log.trace "{} allowed for {}", fi, authentication
			true
		}
		catch (AccessDeniedException unauthorized) {
			if (log.debugEnabled) {
				log.debug "$fi denied for $authentication", GrailsUtil.deepSanitize(unauthorized)
			}
			false
		}
	}

	protected FilterInvocation createFilterInvocation(String contextPath, String uri, String method) {
		assert uri, 'URI required'
		new FilterInvocation(DummyRequestCreator.createInstance(contextPath, method, contextPath + uri), DUMMY_RESPONSE, DUMMY_CHAIN)
	}
}

@CompileStatic
class DummyRequestCreator {

	static HttpServletRequest createInstance(String contextPath, String httpMethod, String requestURI) {
		Map<String, Object> attributes = [:]

		(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.classLoader,
				[HttpServletRequest] as Class[], new InvocationHandler() {
			def invoke(proxy, Method method, Object[] args) {

				String methodName = method.name

				if ('getContextPath' == methodName) return contextPath
				if ('getMethod' == methodName) return httpMethod
				if ('getRequestURI' == methodName) return requestURI
				if ('setAttribute' == methodName) {
					attributes[(String)args[0]] = args[1]
					return null
				}
				if ('getAttribute' == methodName) {
					return attributes[args[0]]
				}

				if ('getProtocol' == methodName || 'getScheme' == methodName) return 'http'
				if ('getServerName' == methodName) return 'localhost'
				if ('getServerPort' == methodName) return 8080

				if (methodName.startsWith('is')) return false

				if ('getParameterMap' == methodName) return Collections.emptyMap()

				if ('getAttributeNames' == methodName ||
				    'getHeaderNames' == methodName ||
				    'getHeaders' == methodName ||
				    'getLocales' == methodName ||
				    'getParameterNames' == methodName) {
					return Collections.enumeration(Collections.emptySet())
				}
			}
		})
	}
}

@CompileStatic
class DummyResponseCreator {
	static HttpServletResponse createInstance() {
		(HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.classLoader,
				[HttpServletResponse] as Class[], new InvocationHandler() {
			def invoke(proxy, Method method, Object[] args) {
				throw new UnsupportedOperationException()
			}
		})
	}
}
