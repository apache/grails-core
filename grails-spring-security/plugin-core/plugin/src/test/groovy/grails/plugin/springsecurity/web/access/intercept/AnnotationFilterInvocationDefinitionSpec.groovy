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

import grails.core.GrailsClass
import grails.plugin.springsecurity.AbstractUnitSpec
import grails.plugin.springsecurity.InterceptedUrl
import grails.plugin.springsecurity.access.vote.ClosureConfigAttribute
import grails.plugin.springsecurity.annotation.Secured
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import groovy.util.logging.Slf4j
import org.grails.core.DefaultGrailsControllerClass
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.mime.HttpServletResponseExtension
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.SecurityConfig
import org.springframework.security.web.FilterInvocation
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Ignore
import spock.lang.Shared

/**
 * Unit tests for AnnotationFilterInvocationDefinition.
 *
 * @author Burt Beckwith
 */
class AnnotationFilterInvocationDefinitionSpec extends AbstractUnitSpec {

	private @Shared HttpServletResponseExtension httpServletResponseExtension = new HttpServletResponseExtension()
	private @Shared AnnotationFilterInvocationDefinition fid = new AnnotationFilterInvocationDefinition(
			httpServletResponseExtension: httpServletResponseExtension)

	void 'supports'() {
		expect:
		fid.supports FilterInvocation
	}

	void 'lowercaseAndStripQuerystring'() {
		expect:
		'/foo/bar' == fid.lowercaseAndStripQuerystring('/foo/BAR')
		'/foo/bar' == fid.lowercaseAndStripQuerystring('/foo/bar')
		'/foo/bar' == fid.lowercaseAndStripQuerystring('/foo/BAR?x=1')
	}

	void 'lowercaseAndStringQuerystring removes fragement'() {
		expect:
		'/foo/bar' == fid.lowercaseAndStripQuerystring('/foo/BAR#tableOfContents')
	}

	void 'getAttributes for null arg'() {
		when:
		fid.getAttributes null

		then:
		thrown AssertionError
	}

	void 'getAttributes when supports is false'() {
		when:
		fid.getAttributes 'foo'

		then:
		thrown AssertionError
	}

	void 'getAttributes'() {
		setup:
		def chain = new MockFilterChain()
		FilterInvocation filterInvocation = new FilterInvocation(request, response, chain)

		fid = new MockAnnotationFilterInvocationDefinition(httpServletResponseExtension: httpServletResponseExtension)

		def urlMappingsHolder = [matchAll: { String uri, String httpMethod, String version -> [] as UrlMappingInfo[] }] as UrlMappingsHolder
		fid.initialize([], urlMappingsHolder, [] as GrailsClass[])
		WebUtils.storeGrailsWebRequest new GrailsWebRequest(request, response, servletContext)

		when:
		String pattern = '/foo/**'
		def configAttribute = [new SecurityConfig('ROLE_ADMIN')]
		fid.storeMapping pattern, null, configAttribute

		request.requestURI = '/foo/bar'
		fid.url = request.requestURI

		then:
		configAttribute == fid.getAttributes(filterInvocation)

		when:
		fid.rejectIfNoRule = false
		request.requestURI = '/bar/foo'
		fid.url = request.requestURI

		then:
		!fid.getAttributes(filterInvocation)

		when:
		fid.rejectIfNoRule = true

		then:
		AbstractFilterInvocationDefinition.DENY == fid.getAttributes(filterInvocation)

		when:
		String moreSpecificPattern = '/foo/ba*'
		def moreSpecificConfigAttribute = [new SecurityConfig('ROLE_SUPERADMIN')]
		fid.storeMapping moreSpecificPattern, null, moreSpecificConfigAttribute

		request.requestURI = '/foo/bar'
		fid.url = request.requestURI

		then:
		moreSpecificConfigAttribute == fid.getAttributes(filterInvocation)
	}

	void 'determineUrl for static request'() {
		when:
		def request = new MockHttpServletRequest()
		def response = new MockHttpServletResponse()
		def filterChain = new MockFilterChain()

		request.requestURI = 'requestURI'

		fid = new MockAnnotationFilterInvocationDefinition(httpServletResponseExtension: httpServletResponseExtension)

		def urlMappingsHolder = [matchAll: { String uri, String httpMethod, String version -> [] as UrlMappingInfo[] }] as UrlMappingsHolder
		fid.initialize([], urlMappingsHolder, [] as GrailsClass[])
		WebUtils.storeGrailsWebRequest new GrailsWebRequest(request, response, servletContext)

		FilterInvocation filterInvocation = new FilterInvocation(request, response, filterChain)

		then:
		'requesturi' == fid.determineUrl(filterInvocation)
	}

	void 'determineUrl for dynamic request'() {
		when:
		def request = new MockHttpServletRequest()
		def response = new MockHttpServletResponse()
		def filterChain = new MockFilterChain()

		request.requestURI = 'requestURI'

		fid = new MockAnnotationFilterInvocationDefinition(url: 'FOO?x=1', application: grailsApplication,
		                                                   httpServletResponseExtension: httpServletResponseExtension)

		UrlMappingInfo[] mappings = [[getControllerName: { -> 'foo' },
		                              getActionName: { -> 'bar' },
		                              configure: { GrailsWebRequest r -> },
		                              getRedirectInfo: { -> }] as UrlMappingInfo]
		def urlMappingsHolder = [matchAll: { String uri, String httpMethod, String version -> mappings }] as UrlMappingsHolder
		fid.initialize([], urlMappingsHolder, [] as GrailsClass[])
		WebUtils.storeGrailsWebRequest new GrailsWebRequest(request, response, servletContext)

		FilterInvocation filterInvocation = new FilterInvocation(request, response, filterChain)

		then:
		'foo' == fid.determineUrl(filterInvocation)
	}

	@Ignore
	void 'initialize'() {
		when:
		def mappings = {

			"/admin/user/$action?/$id?"(controller: 'adminUser')

			"/$controller/$action?/$id?" {}

			"/"(view: '/index')

			/**** Error Mappings ****/

			"403"(controller: 'errors', action: 'accessDenied')
			"404"(controller: 'errors', action: 'notFound')
			"405"(controller: 'errors', action: 'notAllowed')
			"500"(view: '/error')
		}

		servletContext.setAttribute WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext

		def mappingEvaluator = new DefaultUrlMappingEvaluator(servletContext)

		def urlMappingsHolder = new DefaultUrlMappingsHolder(
				mappings.collect { mappingEvaluator.evaluateMappings(mappings) }.flatten())

		List<Map<String, Collection<String>>> staticRules = [[pattern: '/js/admin/**', access: ['ROLE_ADMIN']]]
		GrailsClass[] controllerClasses = [new DefaultGrailsControllerClass(ClassAnnotatedController),
		                                   new DefaultGrailsControllerClass(MethodAnnotatedController)]
		controllerClasses.each { cc -> grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, cc) }

		fid.roleVoter = applicationContext.getBean('roleVoter')
		fid.authenticatedVoter = applicationContext.getBean('authenticatedVoter')
		fid.grailsUrlConverter = new CamelCaseUrlConverter()

		fid.initialize staticRules, urlMappingsHolder, controllerClasses

		then:
		16 == fid.configAttributeMap.size()

		when:
		InterceptedUrl iu

		then:
		for (key in ['/classannotated', '/classannotated.*', '/classannotated/**']) {
			iu = fid.getInterceptedUrl(key, null)
			assert 1 == iu.configAttributes.size()
			assert 'ROLE_ADMIN' == iu.configAttributes.iterator().next().attribute
			assert !iu.httpMethod
		}

		for (key in ['/classannotated/list', '/classannotated/list.*', '/classannotated/list/**']) {
			iu = fid.getInterceptedUrl(key, null)
			assert 2 == iu.configAttributes.size()
			assert ['ROLE_FOO', 'ROLE_SUPERADMIN'] as Set == iu.configAttributes*.attribute as Set
			assert !iu.httpMethod
		}

		for (key in ['/methodannotated/list', '/methodannotated/list.*', '/methodannotated/list/**']) {
			iu = fid.getInterceptedUrl(key, null)
			assert 1 == iu.configAttributes.size()
			assert 'ROLE_ADMIN' == iu.configAttributes.iterator().next().attribute
			assert !iu.httpMethod
		}

		for (key in ['/methodannotated/bar', '/methodannotated/bar.*', '/methodannotated/bar/**']) {
			iu = fid.getInterceptedUrl(key, HttpMethod.PUT)
			assert 1 == iu.configAttributes.size()
			assert 'ROLE_ADMIN' == iu.configAttributes.iterator().next().attribute
			assert HttpMethod.PUT == iu.httpMethod
		}

		for (key in ['/methodannotated/foo', '/methodannotated/foo.*', '/methodannotated/foo/**']) {
			iu = fid.getInterceptedUrl(key, null)
			assert 1 == iu.configAttributes.size()
			assert iu.configAttributes.iterator().next() instanceof ClosureConfigAttribute
			assert !iu.httpMethod
		}

		when:
		iu = fid.getInterceptedUrl('/js/admin/**', null)

		then:
		1 == iu.configAttributes.size()
		'ROLE_ADMIN' == iu.configAttributes.iterator().next().attribute
	}

	void 'findConfigAttribute'() {
		when:
		String pattern = '/foo/**'
		def configAttributes = [new SecurityConfig('ROLE_ADMIN')]
		fid.storeMapping pattern, null, configAttributes

		then:
		configAttributes == fid.findConfigAttributes('/foo/bar', null)
		!fid.findConfigAttributes('/bar/foo', null)
	}

	void cleanup() {
		RequestContextHolder.resetRequestAttributes()
	}
}

@Slf4j
class MockAnnotationFilterInvocationDefinition extends AnnotationFilterInvocationDefinition {
	String url
	protected String findGrailsUrl(UrlMappingInfo mapping) { url }
}

@Secured('ROLE_ADMIN')
class ClassAnnotatedController {

	def index() {}

	@Secured(['ROLE_SUPERADMIN', 'ROLE_FOO'])
	def list() { [results: []] }
}

class MethodAnnotatedController {

	def index() {}

	@Secured('ROLE_ADMIN')
	def list() { [results: []] }

	@Secured(closure = {
		assert request
		assert ctx
		authentication.name == 'admin1'
	})
	def foo() { [results: []] }

	@Secured(value=['ROLE_ADMIN'], httpMethod='PUT')
	def bar() { [results: []] }
}
