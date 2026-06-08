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
package grails.plugin.springsecurity


import static grails.web.http.HttpHeaders.ACCEPT_VERSION

import org.grails.config.PropertySourcesConfig
import org.grails.web.mime.HttpServletResponseExtension
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySource
import org.springframework.expression.Expression
import org.springframework.expression.ParseException
import org.springframework.http.HttpMethod
import org.springframework.security.access.AccessDecisionVoter
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.SecurityConfig
import org.springframework.security.access.expression.SecurityExpressionHandler
import grails.core.GrailsApplication
import grails.plugin.springsecurity.web.access.expression.WebExpressionConfigAttribute
import grails.util.Holders
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import groovy.util.logging.Slf4j

/**
 * Helper methods that use dynamic Groovy.
 *
 * @author Burt Beckwith
 */
@Slf4j
class ReflectionUtils {

	// set at startup
	static GrailsApplication application

	private ReflectionUtils() {
		// static only
	}

	static Object getConfigProperty(String name, config = SpringSecurityUtils.securityConfig) {
		def value = config
		name.split('\\.').each { String part -> value = value."$part" }
		value
	}

	static void setConfigProperty(String name, value) {
		def config = SpringSecurityUtils.securityConfig

		List parts = name.split('\\.')
		name = parts.remove(parts.size() - 1)

		parts.each { String part -> config = config."$part" }

		config."$name" = value
	}

	static String getRoleAuthority(role) {
		lookupPropertyValue role, 'authority.nameField'
	}

	static String getRequestmapUrl(requestmap) {
		lookupPropertyValue requestmap, 'requestMap.urlField'
	}

	static String getRequestmapConfigAttribute(requestmap) {
		lookupPropertyValue requestmap, 'requestMap.configAttributeField'
	}

	static HttpMethod getRequestmapHttpMethod(requestmap) {
		lookupPropertyValue requestmap, 'requestMap.httpMethodField'
	}

	static List loadAllRequestmaps() {
		Class Requestmap = requestMapClass
		Requestmap.withTransaction {
			Requestmap.list()
		}
	}

	static boolean requestmapClassSupportsHttpMethod() {
		String httpMethodField = SpringSecurityUtils.securityConfig.requestMap.httpMethodField
		if (!httpMethodField) return false

		requestMapClass.metaClass.properties.find { MetaProperty p -> p.name == httpMethodField }
	}

	static Class getRequestMapClass() {
		String className = SpringSecurityUtils.securityConfig.requestMap.className ?: ''
		assert className, "Cannot load Requestmaps; 'requestMap.className' property is not specified"

		Class Requestmap = getApplication().getClassForName(className)
		assert Requestmap, "Cannot load Requestmaps; 'requestMap.className' property '$className' is invalid"

		Requestmap
	}

	static List asList(o) { o ? o as List : [] }

	static ConfigObject getSecurityConfig() {
		def grailsConfig = getApplication().config
		if (grailsConfig.containsProperty('grails.plugins.springsecurity')) {
			log.error "Your security configuration settings use the old prefix 'grails.plugins.springsecurity' but must now use 'grails.plugin.springsecurity'"
		}
		grailsConfig.getProperty('grails.plugin.springsecurity', ConfigObject.class)
	}

	static void setSecurityConfig(ConfigObject c) {
		ConfigObject config = new ConfigObject()
		config.grails.plugin.springsecurity = c

		PropertySource propertySource = new MapPropertySource('SecurityConfig', [:] << config)

		def propertySources = application.mainContext.environment.propertySources
		propertySources.addFirst propertySource
		getApplication().config = new PropertySourcesConfig(propertySources)
	}

	static List<InterceptedUrl> splitMap(List<Map<String, Object>> map) {
		map.collect { Map<String, Object> row ->

			List<String> tokens
			def value = row.access
			if (value instanceof Collection || value.getClass().array) {
				tokens = value*.toString()
			}
			else { // String/GString
				tokens = [value.toString()]
			}

			def httpMethod = row.httpMethod ?: null
			if (httpMethod instanceof CharSequence) {
				httpMethod = HttpMethod.valueOf(httpMethod)
			}

			new InterceptedUrl(row.pattern as String, tokens, httpMethod as HttpMethod)
		}
	}

	static Collection<ConfigAttribute> buildConfigAttributes(Collection<String> tokens, boolean expressions = true) {
		Collection<ConfigAttribute> configAttributes = [] as Set

		def ctx = getApplication().mainContext
		SecurityExpressionHandler expressionHandler = ctx.getBean('webExpressionHandler')
		AccessDecisionVoter roleVoter = ctx.getBean('roleVoter')
		AccessDecisionVoter authenticatedVoter = ctx.getBean('authenticatedVoter')

		for (String token in tokens) {
			ConfigAttribute config = new SecurityConfig(token)
			boolean supports = !expressions || token.startsWith('RUN_AS') || token.startsWith('SCOPE') ||
			                   supports(config, roleVoter) || supports(config, authenticatedVoter)
			if (supports) {
				configAttributes << config
			}
			else {
				try {
					Expression expression = expressionHandler.expressionParser.parseExpression(token)
					configAttributes << new WebExpressionConfigAttribute(expression)
				}
				catch (ParseException e) {
					log.error "\nError parsing expression '$token': $e.message\n", e
					throw e
				}
			}
		}

		log.trace 'Built ConfigAttributes {} for tokens {}', configAttributes, tokens
		configAttributes
	}

	static String getGrailsServerURL() {
		getApplication().config.grails.serverURL ?: null
	}

	private static boolean supports(ConfigAttribute config, AccessDecisionVoter<?> voter) {
		voter.supports config
	}

	private static lookupPropertyValue(o, String name) {
		o."${getConfigProperty(name)}"
	}

	private static GrailsApplication getApplication() {
		if (!application) {
			application = Holders.grailsApplication
		}
		application
	}

	static UrlMappingInfo[] matchAllUrlMappings(UrlMappingsHolder urlMappingsHolder, String requestUrl,
	                                            GrailsWebRequest grailsRequest, HttpServletResponseExtension extension) {
		String method = grailsRequest.currentRequest.method
		String version = grailsRequest.getHeader(ACCEPT_VERSION) ?: extension.getMimeTypeForRequest(grailsRequest).version
		urlMappingsHolder.matchAll requestUrl, method, version == null ? UrlMapping.ANY_VERSION : version
	}

	static SortedMap<Integer, String> findFilterChainNames(ConfigObject conf) {
		SpringSecurityUtils.findFilterChainNames conf.filterChain.filterNames,
				conf.secureChannel.definition as boolean, conf.ipRestrictions as boolean, conf.useX509 as boolean,
				conf.useDigestAuth as boolean, conf.useBasicAuth as boolean, conf.useSwitchUserFilter as boolean
	}
}
