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

import grails.plugin.springsecurity.AbstractUnitSpec
import grails.plugin.springsecurity.ReflectionUtils
import grails.web.mapping.UrlMappingInfo
import groovy.util.logging.Slf4j
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockFilterChain
import org.springframework.security.access.SecurityConfig
import org.springframework.security.access.vote.AuthenticatedVoter
import org.springframework.security.access.vote.RoleVoter
import org.springframework.security.web.FilterInvocation

/**
 * @author Burt Beckwith
 */
class InterceptUrlMapFilterInvocationDefinitionSpec extends AbstractUnitSpec {

	private InterceptUrlMapFilterInvocationDefinition fid = new InterceptUrlMapFilterInvocationDefinition()

	void 'store mapping'() {
		expect:
		!fid.configAttributeMap

		when:
		fid.storeMapping '/foo/bar', null, ['ROLE_ADMIN']

		then:
		1 == fid.configAttributeMap.size()

		when:
		fid.storeMapping '/foo/bar', null, ['ROLE_USER']

		then:
		1 == fid.configAttributeMap.size()

		when:
		fid.storeMapping '/other/path', null, ['ROLE_SUPERUSER']

		then:
		2 == fid.configAttributeMap.size()
	}

	void 'initialize'() {
		when:
		ReflectionUtils.setConfigProperty('interceptUrlMap',
				[[pattern: '/foo/**', access: 'ROLE_ADMIN'],
				 [pattern: '/bar/**', access: ['ROLE_BAR', 'ROLE_BAZ']]])

		fid.roleVoter = applicationContext.getBean('roleVoter')
		fid.authenticatedVoter = applicationContext.getBean('authenticatedVoter')

		then:
		!fid.configAttributeMap

		when:
		fid.initialize()

		then:
		2 == fid.configAttributeMap.size()

		when:
		fid.resetConfigs()

		fid.initialize()

		then:
		!fid.configAttributeMap
	}

	void 'initialize with new syntax'() {
		when:
		ReflectionUtils.setConfigProperty('interceptUrlMap',
				[[pattern: '/foo/**', access: 'ROLE_ADMIN', httpMethod: HttpMethod.POST],
				 [pattern: '/bar/**', access: ['ROLE_BAR', 'ROLE_BAZ']]])

		fid.roleVoter = new RoleVoter()
		fid.authenticatedVoter = new AuthenticatedVoter()

		then:
		!fid.configAttributeMap

		when:
		fid.initialize()

		then:
		2 == fid.configAttributeMap.size()

		when:
		def interceptedUrls = ([] + fid.configAttributeMap).sort { it.pattern }

		then:
		interceptedUrls[0].pattern == '/bar/**'
		!interceptedUrls[0].httpMethod
		null == interceptedUrls[0].https
		interceptedUrls[0].configAttributes*.attribute.sort() == ['ROLE_BAR', 'ROLE_BAZ']

		interceptedUrls[1].pattern == '/foo/**'
		interceptedUrls[1].httpMethod == HttpMethod.POST
		null == interceptedUrls[1].https
		interceptedUrls[1].configAttributes*.attribute == ['ROLE_ADMIN']
	}

	void 'determineUrl'() {
		when:
		def chain = new MockFilterChain()
		request.contextPath = '/context'

		request.requestURI = '/context/foo'

		then:
		'/foo' == fid.determineUrl(new FilterInvocation(request, response, chain))

		when:
		request.requestURI = '/context/fOo/Bar?x=1&y=2'

		then:
		'/foo/bar' == fid.determineUrl(new FilterInvocation(request, response, chain))
	}

	void 'supports'() {
		expect:
		fid.supports FilterInvocation
	}

	void 'getAttributes'() {
		when:
		def chain = new MockFilterChain()
		FilterInvocation filterInvocation = new FilterInvocation(request, response, chain)

		MockInterceptUrlMapFilterInvocationDefinition fid

		def initializeFid = {
			fid = new MockInterceptUrlMapFilterInvocationDefinition()
			fid.initialize()
			WebUtils.storeGrailsWebRequest new GrailsWebRequest(request, response, servletContext)
			fid
		}

		def checkConfigAttributeForUrl = {config, String url ->
			request.requestURI = url
			fid.url = url
			assert config == fid.getAttributes(filterInvocation), "Checking config for $url"
			true
		}

		def configAttribute = [new SecurityConfig('ROLE_ADMIN'), new SecurityConfig('ROLE_SUPERUSER')]
		def moreSpecificConfigAttribute = [new SecurityConfig('ROLE_SUPERUSER')]
		fid = initializeFid()
		fid.storeMapping '/secure/**', null, configAttribute
		fid.storeMapping '/secure/reallysecure/**', null, moreSpecificConfigAttribute

		then:
		checkConfigAttributeForUrl(configAttribute, '/secure/reallysecure/list')
		checkConfigAttributeForUrl(configAttribute, '/secure/list')

		when:
		fid = initializeFid()
		fid.storeMapping '/secure/reallysecure/**', null, moreSpecificConfigAttribute
		fid.storeMapping '/secure/**', null, configAttribute

		then:
		checkConfigAttributeForUrl(moreSpecificConfigAttribute, '/secure/reallysecure/list')
		checkConfigAttributeForUrl(configAttribute, '/secure/list')

		when:
		fid = initializeFid()
		configAttribute = [new SecurityConfig('IS_AUTHENTICATED_FULLY')]
		moreSpecificConfigAttribute = [new SecurityConfig('IS_AUTHENTICATED_ANONYMOUSLY')]
		fid.storeMapping '/unprotected/**', null, moreSpecificConfigAttribute
		fid.storeMapping '/**/*.jsp', null, configAttribute

		then:
		checkConfigAttributeForUrl(moreSpecificConfigAttribute, '/unprotected/b.jsp')
		checkConfigAttributeForUrl(moreSpecificConfigAttribute, '/unprotected/path')
		checkConfigAttributeForUrl(moreSpecificConfigAttribute, '/unprotected/path/x.jsp')
		checkConfigAttributeForUrl(configAttribute, '/b.jsp')
		checkConfigAttributeForUrl(null, '/path')
	}
}

@Slf4j
class MockInterceptUrlMapFilterInvocationDefinition extends InterceptUrlMapFilterInvocationDefinition {
	String url
	protected String findGrailsUrl(UrlMappingInfo mapping) { url }
}
