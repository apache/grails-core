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
package grails.plugin.cache

import grails.plugin.cache.util.ClassUtils
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.grails.buffer.StreamCharBuffer
import org.grails.gsp.GroovyPageTemplate
import org.grails.plugin.cache.GrailsCacheManager
import org.grails.web.gsp.GroovyPagesTemplateRenderer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder

@Slf4j
class CacheTagLib {

	static namespace = 'cache'

	GrailsCacheManager grailsCacheManager
	GroovyPagesTemplateRenderer groovyPagesTemplateRenderer

	/**
	 * Renders a block of markup and caches the result so the next time the same block
	 * is rendered, it does not need to be evaluated again.
	 *
	 * @attr key An optional cache key allowing the same block to be cached with different content
	 * @attr cache Cache name ("grailsBlocksCache" is used if not specified)
	 */
	def block = { attrs, body ->
		if (!grailsCacheManager) {
			out << body()
			return
		}

		try {
			def cache = grailsCacheManager.getCache(attrs.cache ?: 'grailsBlocksCache')
			def bodyClosure = ClassUtils.getPropertyOrFieldValue(body, 'bodyClosure')
			def closureClass = bodyClosure.getClass()
			def key = closureClass.getName()
			def expired = false

			if (attrs.key) {
				key += ':' + attrs.key
			}

			if (attrs.ttl) {
				expired = honorTTL(key, attrs.ttl.toLong())
			}

			def content = cache.get(key)
			if (content == null || expired) {
				content = cloneIfNecessary(body())
				cache.put(key, content)
			} else {
				content = content.get()
			}

			out << content
		} catch (RuntimeException e) {
			log.error("Cache block experienced an error, ignoring cache and outputting the body content instead.", e)
			out << body()
		}
	}

	/**
	 * Renders a GSP template and caches the result so the next time the same template
	 * is rendered, it does not need to be evaluated again.
	 *
	 * @attr template REQUIRED The name of the template to apply
	 * @attr key An optional cache key allowing the same template to be cached with different content
	 * @attr contextPath the context path to use (relative to the application context path). Defaults to "" or path to the plugin for a plugin view or template.
	 * @attr bean The bean to apply the template against
	 * @attr model The model to apply the template against as a java.util.Map
	 * @attr collection A collection of model objects to apply the template to
	 * @attr var The variable name of the bean to be referenced in the template
	 * @attr plugin The plugin to look for the template in
	 * @attr cache Cache name ("grailsTemplatesCache" is used if not specified)
	 */
	def render = { attrs ->
		if (!grailsCacheManager) {
			out << g.render(attrs)
			return
		}

		try {
			//Make this empty string to save error later in grails gsp core
			attrs.plugin = attrs.plugin ?: ''

			def cache = grailsCacheManager.getCache(attrs.cache ?: 'grailsTemplatesCache')
			def key = calculateFullKey(attrs.template, attrs.contextPath, attrs.plugin)
			def expired = false

			if (attrs.key) {
				key += ':' + attrs.key
			}

			if (attrs.ttl) {
				expired = honorTTL(key, attrs.ttl.toLong())
			}

			def content = cache.get(key)
			if (content == null || expired) {
				content = cloneIfNecessary(g.render(attrs))
				cache.put(key, content)
			} else {
				content = content.get()
			}
			out << content
		} catch (RuntimeException e) {
			log.error("Cache render experienced an error, ignoring cache and outputting the template un-cached.", e)
			out << cloneIfNecessary(g.render(attrs))
		}
	}

	@Memoized(maxCacheSize = 100)
	protected String calculateFullKey(String templateName, String contextPath, String pluginName) {
		GrailsWebRequest webRequest = RequestContextHolder.currentRequestAttributes()
		String uri = webRequest.attributes.getTemplateUri(templateName, webRequest.request)
		def artefact = grailsApplication.getArtefactByLogicalPropertyName("Controller", controllerName)
		def controller = grailsApplication.mainContext.getBean(artefact?.clazz?.name)

		GroovyPageTemplate t = groovyPagesTemplateRenderer.findAndCacheTemplate(
				controller, webRequest, pageScope, templateName, contextPath, pluginName, uri)
		if (!t) {
			throwTagError("Template not found for name [$templateName] and path [$uri]")
		}

		return t.metaInfo.pageClass.name
	}

	protected static cloneIfNecessary(content) {
		if (content instanceof StreamCharBuffer) {
			if (content instanceof Cloneable) {
				content = content.clone()
			} else {
				// pre Grails 2.3
				content = content.toString()
			}
		}
		content
	}

	/**
	 * updates the ttl and returns whether the content is expired
	 * @param key
	 * @param ttl in seconds comes form the view
	 * @return boolean whether we wrote the a new ttl in or not
	 */
	protected Boolean honorTTL(String key, Long ttl) {
		def cache = grailsCacheManager.getCache("TagLibTTLCache")
		String ttlKey = key + ":ttl"
		Long ttlInMilliseconds = ttl * 1000
		Long currentTime = System.currentTimeMillis()
		Boolean expired
		def valueInCache
		Long cacheInsertionTime

		try {
			valueInCache = cache.get(ttlKey)
			cacheInsertionTime = valueInCache ? valueInCache.get().toLong() : 0
			expired = valueInCache && ((currentTime - cacheInsertionTime) > ttlInMilliseconds)
		} catch (Exception e) {
			cache.put(ttlKey, currentTime)
			return true // we overwrote the cache key
		}

		if (expired || !valueInCache) {
			cache.put(ttlKey, currentTime)
		}

		return expired
	}
}
