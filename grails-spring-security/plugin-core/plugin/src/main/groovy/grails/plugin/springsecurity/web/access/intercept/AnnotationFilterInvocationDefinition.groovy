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

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.GrailsControllerClass
import grails.core.GrailsDomainClass
import grails.plugin.springsecurity.InterceptedUrl
import grails.plugin.springsecurity.ReflectionUtils as PluginReflectionUtils
import grails.plugin.springsecurity.access.vote.ClosureConfigAttribute
import grails.plugin.springsecurity.annotation.Secured as PluginSecured
import grails.rest.Resource
import grails.web.Action
import grails.web.UrlConverter
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import grails.web.servlet.mvc.GrailsParameterMap
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo
import org.grails.web.mime.HttpServletResponseExtension
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
import org.springframework.http.HttpMethod
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.annotation.Secured as SpringSecured
import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource
import org.springframework.util.ReflectionUtils
import org.springframework.util.StringUtils
import org.springframework.web.context.ServletContextAware

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import java.lang.annotation.Annotation
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * A {@link FilterInvocationSecurityMetadataSource} that uses rules defined with
 * Controller annotations combined with static rules defined in
 * <code>SecurityConfig.groovy</code>, e.g. for js, images, css or for rules
 * that cannot be expressed in a controller like '/**'.
 *
 * @author Burt Beckwith
 */
@Slf4j
@CompileStatic
class AnnotationFilterInvocationDefinition extends AbstractFilterInvocationDefinition implements ServletContextAware {

	protected static String SLASH = '/'

	protected UrlMappingsHolder urlMappingsHolder

	ServletContext servletContext

	/** Dependency injection for the application. */
	GrailsApplication application

	/** Dependency injection for the httpServletResponseExtension bean. */
	HttpServletResponseExtension httpServletResponseExtension

	/** Dependency injection for the grailsUrlConverter bean. */
	UrlConverter grailsUrlConverter

	@Override
	protected String determineUrl(FilterInvocation filterInvocation) {
		HttpServletRequest request = filterInvocation.httpRequest
		HttpServletResponse response = filterInvocation.httpResponse

		String requestUrl = urlPathHelper.getRequestUri(request)
		requestUrl = stripContextPath(requestUrl, request)

		GrailsWebRequest existingRequest
		try {
			existingRequest = WebUtils.retrieveGrailsWebRequest()
		}
		catch (IllegalStateException e) {
			if (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) == 404) {
				ERROR404
			}
			else {
				requestUrl
			}
		}

		log.trace 'Requested url: {}', requestUrl

		String url
		try {
			GrailsWebRequest grailsRequest = new GrailsWebRequest(request, response, servletContext)
			WebUtils.storeGrailsWebRequest grailsRequest

			Map<String, Object> savedParams = copyParams(grailsRequest)

			UrlMappingInfo[] urlInfos = PluginReflectionUtils.matchAllUrlMappings(
					urlMappingsHolder, requestUrl, grailsRequest, httpServletResponseExtension)

			for (UrlMappingInfo mapping : urlInfos) {
				if (mapping.redirectInfo) {
					log.trace 'Mapping {} is a redirect', mapping
					break
				}

				configureMapping mapping, grailsRequest, savedParams

				url = findGrailsUrl(mapping)
				if (url) {
					break
				}
			}
		}
		finally {
			if (existingRequest) {
				WebUtils.storeGrailsWebRequest(existingRequest)
			}
			else {
				WebUtils.clearGrailsWebRequest()
			}
		}

		if (!StringUtils.hasLength(url)) {
			// probably css/js/image
			url = requestUrl
		}

		String finalUrl = lowercaseAndStripQuerystring(url)
		log.trace 'Final url is {}', finalUrl
		finalUrl
	}

	protected String findGrailsUrl(UrlMappingInfo mapping) {

		String uri = mapping.URI
		if (uri) {
			return uri
		}

		String viewName = mapping.viewName
		if (viewName != null) {
			if (!viewName.startsWith(SLASH)) {
				viewName = SLASH + viewName
			}
			return viewName
		}

		if (!(mapping instanceof GrailsControllerUrlMappingInfo)) {
			return
		}

		String namespace = mapping.namespace
		String controllerName = mapping.controllerName
		if (namespace) {
			controllerName = resolveFullControllerName(controllerName, namespace)
		}

		createControllerUri controllerName, mapping.actionName ?: ''
	}

	protected String createControllerUri(String controllerName, String actionName) {
		if (!actionName || 'null' == actionName) {
			actionName = 'index'
		}
		(SLASH + controllerName + SLASH + actionName).trim()
	}

	protected void configureMapping(UrlMappingInfo mapping, GrailsWebRequest grailsRequest, Map<String, Object> savedParams) {

		// reset params since mapping.configure() sets values
		GrailsParameterMap params = grailsRequest.params
		params.clear()
		params.putAll(savedParams)

		mapping.configure grailsRequest
	}

	@SuppressWarnings('unchecked')
	protected Map<String, Object> copyParams(GrailsWebRequest grailsRequest) {
		([:] << grailsRequest.params) as Map<String, Object>
	}

	/**
	 * Called by the plugin to set controller role info.<br>
	 *
	 * Reinitialize by calling <code>ctx.objectDefinitionSource.initialize(
	 * 	ctx.authenticateService.securityConfig.security.annotationStaticRules,
	 * 	ctx.grailsUrlMappingsHolder,
	 * 	grailsApplication.controllerClasses)</code>
	 *
	 * @param staticRules data from the controllerAnnotations.staticRules config attribute
	 * @param mappingsHolder mapping holder
	 * @param controllerClasses all controllers
	 * @param domainClasses all domain classes
	 */
	void initialize(staticRules, UrlMappingsHolder mappingsHolder, GrailsClass[] controllerClasses, GrailsClass[] domainClasses) {

		assert staticRules != null, 'staticRules map is required'
		assert mappingsHolder, 'urlMappingsHolder is required'

		resetConfigs()

		urlMappingsHolder = mappingsHolder

		Map<String, List<InterceptedUrl>> actionRoles = [:]
		List<InterceptedUrl> classRoles = []
		Map<String, List<InterceptedUrl>> actionClosures = [:]
		List<InterceptedUrl> classClosures = []

		for (GrailsClass controllerClass in controllerClasses) {
			findControllerAnnotations((GrailsControllerClass)controllerClass, actionRoles, classRoles, actionClosures, classClosures)
		}

		for (GrailsClass domainClass in domainClasses) {
			findDomainAnnotations((GrailsDomainClass) domainClass, actionRoles, classRoles, actionClosures, classClosures)
		}

		compileStaticRules staticRules
		compileActionClosures actionClosures
		compileClassClosures classClosures
		compileActionRoles actionRoles
		compileClassRoles classRoles

		if (log.traceEnabled) {
			for (InterceptedUrl url in configAttributeMap) {
				log.trace 'URL: {} | Roles: {}', url.pattern, url.configAttributes
			}
		}
	}

	protected void compileActionRoles(Map<String, List<InterceptedUrl>> map) {
		map.each { String controllerName, List<InterceptedUrl> urls ->
			for (InterceptedUrl iu in urls) {
				Collection<ConfigAttribute> configAttributes = iu.configAttributes
				String actionName = iu.pattern
				HttpMethod method = iu.httpMethod
				storeMapping controllerName, actionName, configAttributes, false, method
				if (actionName.endsWith('Flow')) {
					// WebFlow actions end in Flow but are accessed without the suffix, so guard both
					storeMapping controllerName, actionName.substring(0, actionName.length() - 4), configAttributes, false, method
				}
			}
		}
	}

	protected void compileActionClosures(Map<String, List<InterceptedUrl>> map) {
		map.each { String controllerName, List<InterceptedUrl> actionClosures ->
			for (InterceptedUrl iu in actionClosures) {
				String actionName = iu.pattern
				Class<?> closureClass = iu.closureClass
				HttpMethod method = iu.httpMethod
				storeMapping controllerName, actionName, closureClass, method
				if (actionName.endsWith('Flow')) {
					// WebFlow actions end in Flow but are accessed without the suffix, so guard both
					storeMapping controllerName, actionName.substring(0, actionName.length() - 4), closureClass, method
				}
			}
		}
	}

	protected void compileClassRoles(List<InterceptedUrl> classRoles) {
		for (InterceptedUrl iu in classRoles) {
			storeMapping iu.pattern, null, iu.configAttributes, false, iu.httpMethod
		}
	}

	protected void compileClassClosures(List<InterceptedUrl> classClosures) {
		for (InterceptedUrl iu in classClosures) {
			storeMapping iu.pattern, null, iu.closureClass, iu.httpMethod
		}
	}

	protected Closure<?> newInstance(Class<?> closureClass) {
		try {
			Constructor<?> constructor = closureClass.getConstructor(Object, Object)
			ReflectionUtils.makeAccessible constructor
			(Closure<?>) constructor.newInstance(this, this)
		}
		catch (NoSuchMethodException | InstantiationException | IllegalAccessException e) {
			ReflectionUtils.handleReflectionException e
		}
		catch (InvocationTargetException e) {
			ReflectionUtils.handleInvocationTargetException e
		}
	}

	@SuppressWarnings('unchecked')
	protected void compileStaticRules(staticRules) {
		if (staticRules instanceof Map) {
			throw new IllegalArgumentException("Static rules defined as a Map are not supported; must be specified as a " +
					"List of Maps as described in section 'Configuring Request Mappings to Secure URLs' of the reference documentation")
		}

		if (!(staticRules instanceof List)) {
			return
		}

		for (InterceptedUrl iu in PluginReflectionUtils.splitMap((List<Map<String, Object>>)staticRules)) {
			storeMapping iu.pattern, null, iu.configAttributes, true, iu.httpMethod
		}
	}

	protected void storeMapping(String controllerNameOrPattern, String actionName,
	                            Collection<ConfigAttribute> configAttributes, boolean isPattern, HttpMethod method) {

		for (String pattern in generatePatterns(controllerNameOrPattern, actionName, isPattern)) {
			doStoreMapping pattern, method, configAttributes
		}
	}

	protected void storeMapping(String controllerName, String actionName, Class<?> closureClass, HttpMethod method) {
		if (closureClass == PluginSecured) {
			return
		}

		for (String pattern in generatePatterns(controllerName, actionName, false)) {
			Collection<ConfigAttribute> configAttributes = [new ClosureConfigAttribute(newInstance(closureClass))] as Collection<ConfigAttribute>

			String key = pattern.toLowerCase()
			InterceptedUrl replaced = storeMapping(key, method, configAttributes)
			if (replaced) {
				log.warn "replaced rule for '{}' with tokens {} with tokens {}",
						  key, replaced.configAttributes, configAttributes
			}
		}
	}

	protected List<String> generatePatterns(String controllerNameOrPattern, String actionName, boolean isPattern) {

		if (isPattern) {
			return [controllerNameOrPattern]
		}

		StringBuilder sb = new StringBuilder()
		sb << '/' << controllerNameOrPattern
		if (actionName) {
			sb << '/' << actionName
		}
		List<String> patterns = [sb.toString(), sb.toString() + '.*'] // TODO

		if (actionName != '') {
			sb << '/**'
		}

		patterns << sb.toString()

		log.trace 'Patterns generated for controller "{}" action "{}" -> {}', controllerNameOrPattern, actionName, patterns

		patterns
	}

	protected void doStoreMapping(String fullPattern, HttpMethod method, Collection<ConfigAttribute> configAttributes) {
		String key = fullPattern.toString().toLowerCase()
		InterceptedUrl replaced = storeMapping(key, method, configAttributes)
		if (replaced) {
			log.warn "Replaced rule for '{}' and ConfigAttributes {} with ConfigAttributes {}", key, replaced.configAttributes, configAttributes
		}
		else {
			log.trace "Storing ConfigAttributes {} for '{}' and HttpMethod {}", key, configAttributes, method
		}
	}

	protected void findControllerAnnotations(GrailsControllerClass controllerClass, Map<String, List<InterceptedUrl>> actionRoles,
	                                         List<InterceptedUrl> classRoles, Map<String, List<InterceptedUrl>> actionClosures,
	                                         List<InterceptedUrl> classClosures) {

		Class<?> clazz = controllerClass.clazz
		String controllerUri = resolveFullControllerName(controllerClass)

		findAnnotations actionRoles, classRoles, actionClosures, classClosures, clazz, controllerUri
	}

	protected void findDomainAnnotations(GrailsDomainClass domainClass, Map<String, List<InterceptedUrl>> actionRoles,
	                                     List<InterceptedUrl> classRoles, Map<String, List<InterceptedUrl>> actionClosures,
	                                     List<InterceptedUrl> classClosures) {

		Class<?> clazz = domainClass.clazz
		if (clazz.getAnnotation(Resource)) {
			findAnnotations actionRoles, classRoles, actionClosures, classClosures, clazz, clazz.simpleName.toLowerCase(), false
		}
	}

	private void findAnnotations(Map<String, List<InterceptedUrl>> actionRoles, List<InterceptedUrl> classRoles,
	                             Map<String, List<InterceptedUrl>> actionClosures, List<InterceptedUrl> classClosures,
	                             Class<?> clazz, String controllerUri, boolean forController = true) {

		Annotation annotation = clazz.getAnnotation(SpringSecured)
		if (!annotation) {
			annotation = clazz.getAnnotation(PluginSecured)
			if (annotation) {
				Class<?> closureClass = findClosureClass((PluginSecured)annotation)
				if (closureClass) {
					log.trace 'found class-scope annotation with a closure in {}', clazz.name
					classClosures << new InterceptedUrl(controllerUri, closureClass, getHttpMethod(annotation))
				}
				else {
					Collection<String> values = getValue(annotation)
					log.trace 'found class-scope annotation in {} with value(s) {}', clazz.name, values
					classRoles << new InterceptedUrl(controllerUri, values, getHttpMethod(annotation))
				}
			}
		}
		else {
			Collection<String> values = getValue(annotation)
			log.trace 'found class-scope annotation in {} with value(s) {}', clazz.name, values
			classRoles << new InterceptedUrl(controllerUri, values, null)
		}

		if (!forController) {
			return
		}

		List<InterceptedUrl> actionData = findActionRoles(clazz)
		if (actionData) {
			actionRoles[controllerUri] = actionData
		}

		List<InterceptedUrl> closureAnnotatedData = findActionClosures(clazz)
		if (closureAnnotatedData) {
			actionClosures[controllerUri] = closureAnnotatedData
		}
	}

	protected String resolveFullControllerName(GrailsControllerClass controllerClass) {
		String namespace = controllerClass.namespace
		if (namespace) {
			namespace = grailsUrlConverter.toUrlElement(namespace)
		}
		resolveFullControllerName grailsUrlConverter.toUrlElement(controllerClass.name), namespace
	}

	protected String resolveFullControllerName(String controllerNameInUrlFormat, String namespaceInUrlFormat) {
		String fullControllerName = namespaceInUrlFormat ? namespaceInUrlFormat + ':' + controllerNameInUrlFormat : controllerNameInUrlFormat

		log.trace 'Resolved full controller name for controller "{}" and namespace "{}" as "{}"',
				  controllerNameInUrlFormat, namespaceInUrlFormat, fullControllerName

		fullControllerName
	}

	protected List<InterceptedUrl> findActionRoles(Class<?> clazz) {

		log.trace 'finding @Secured annotations for actions in {}', clazz.name

		GrailsControllerClass cc = (GrailsControllerClass)application.getArtefact(ControllerArtefactHandler.TYPE, clazz.name)
		String defaultAction = cc.defaultAction

		List<InterceptedUrl> actionRoles = []
		for (Method method in findActions(clazz)) {
			Annotation annotation = findSecuredAnnotation(method)
			if (annotation) {
				Collection<String> values = getValue(annotation)
				if (values) {
					log.trace 'found annotated method {} in {} with value(s) {}', method.name, clazz.name, values
					HttpMethod httpMethod = getHttpMethod(annotation)
					actionRoles << new InterceptedUrl(grailsUrlConverter.toUrlElement(method.name), values, httpMethod)

					if (method.name == defaultAction) {
						actionRoles << new InterceptedUrl('', values, httpMethod)
					}
				}
			}
		}
		actionRoles
	}

	protected List<InterceptedUrl> findActionClosures(Class<?> clazz) {
		List<InterceptedUrl> actionClosures = []
		for (Method method in findActions(clazz)) {
			PluginSecured annotation = method.getAnnotation(PluginSecured)
			if (annotation && annotation.closure() != PluginSecured) {
				log.trace 'found annotation with a closure on method {} in {}', method.name, clazz.name
				actionClosures << new InterceptedUrl(grailsUrlConverter.toUrlElement(method.name),
						annotation.closure(), getHttpMethod(annotation))
			}
		}
		actionClosures
	}
	
	protected Collection<Method> findActions(Class<?> clazz) {
		return clazz.methods.findAll { it.getAnnotation(Action) != null }
	}

	protected Class<?> findClosureClass(PluginSecured annotation) {
		Class<?> closureClass = annotation.closure()
		closureClass == PluginSecured ? null : closureClass
	}

	protected Annotation findSecuredAnnotation(AccessibleObject annotatedTarget) {
		annotatedTarget.getAnnotation(PluginSecured) ?: annotatedTarget.getAnnotation(SpringSecured)
	}

	protected Collection<String> getValue(Annotation annotation) {
		String[] strings
		if (annotation instanceof PluginSecured) {
			strings = ((PluginSecured)annotation).value()
		}
		else {
			strings = ((SpringSecured)annotation).value()
		}
		new LinkedHashSet<String>(Arrays.asList(strings))
	}

	protected HttpMethod getHttpMethod(Annotation annotation) {
		String method
		if (annotation instanceof PluginSecured) {
			method = ((PluginSecured)annotation).httpMethod()
			if (PluginSecured.ANY_METHOD == method) {
				method = null
			}
		}
		method == null ? null : HttpMethod.valueOf(method)
	}
}
