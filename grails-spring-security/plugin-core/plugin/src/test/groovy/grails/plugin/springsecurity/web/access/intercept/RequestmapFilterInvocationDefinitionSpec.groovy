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
import grails.plugin.springsecurity.InterceptedUrl
import groovy.util.logging.Slf4j
import org.springframework.mock.web.MockFilterChain
import org.springframework.security.web.FilterInvocation

/**
 * Unit tests for RequestmapFilterInvocationDefinition.
 *
 * @author Burt Beckwith
 */
class RequestmapFilterInvocationDefinitionSpec extends AbstractUnitSpec {

	private RequestmapFilterInvocationDefinition fid = new TestRequestmapFilterInvocationDefinition()

	void 'split'() {
		expect:
		['ROLE_1', 'ROLE_2', 'ROLE_3', 'ROLE_4', 'ROLE_5'] == fid.split('ROLE_1, ROLE_2,,,ROLE_3 ,ROLE_4,ROLE_5')
		['hasAnyRole("ROLE_1","ROLE_2")'] == fid.split('hasAnyRole("ROLE_1","ROLE_2")')
	}

	void 'storeMapping'() {
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

	void 'reset'() {
		when:
		fid.roleVoter = applicationContext.getBean('roleVoter')
		fid.authenticatedVoter = applicationContext.getBean('authenticatedVoter')

		then:
		!fid.configAttributeMap

		when:
		fid.reset()

		then:
		2 == fid.configAttributeMap.size()
	}

	void 'initialize'() {
		when:
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

	void "test determineUrl with segmented url"() {
		when:
		def chain = new MockFilterChain()
		request.contextPath = '/context'
		request.requestURI = '/context/foo;f1=WWW/bar;s1=1;s2=2/index.html;i1=1;i2=2'

		then:
		'/foo/bar/index.html' == fid.determineUrl(new FilterInvocation(request, response, chain))
	}

	void "test determineUrl context path and query params are stripped from url"() {
		when:
		def chain = new MockFilterChain()
		request.contextPath = '/context'
		request.requestURI = '/context/foo/bar/index.html?type=HelloWorld'

		then:
		'/foo/bar/index.html' == fid.determineUrl(new FilterInvocation(request, response, chain))
	}

	void 'supports'() {
		expect:
		fid.supports FilterInvocation
	}
}

@Slf4j
class TestRequestmapFilterInvocationDefinition extends RequestmapFilterInvocationDefinition {
	protected List<InterceptedUrl> loadRequestmaps() {
		[new InterceptedUrl('/foo/bar', ['ROLE_USER'], null), new InterceptedUrl('/admin/**', ['ROLE_ADMIN'], null)]
	}
}
