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
package grails.plugin.springsecurity.ui

import grails.plugin.springsecurity.ReflectionUtils
import grails.plugin.springsecurity.ui.strategy.RequestmapStrategy
import org.springframework.http.HttpMethod

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class RequestmapController extends AbstractS2UiDomainController {

	/** Dependency injection for the 'uiRequestmapStrategy' bean. */
	RequestmapStrategy uiRequestmapStrategy

	def springSecurityService

	def create() {
		super.create()
	}

	def save() {
		withForm {
			if (!(param('url'))) {
				params.remove('url')
			}
			doSave(uiRequestmapStrategy.saveRequestmap(params)) {
				springSecurityService.clearCachedRequestmaps()
			}
		}.invalidToken {
			doSaveWithInvalidToken(params.url)
		}
	}

	def edit() {
		super.edit()
	}

	def update() {
		withForm {
			if (!(param('url'))) {
				params.remove('url')
			}
			doUpdate { requestmap ->
				uiRequestmapStrategy.updateRequestmap params, requestmap
			}
		}.invalidToken {
			doUpdateWithInvalidToken(params.url)
		}
	}

	def delete() {
		withForm {
			tryDelete { requestmap ->
				uiRequestmapStrategy.deleteRequestmap requestmap
			}
		}.invalidToken {
			doDeleteWithInvalidToken()
		}
	}

	def search() {
		if (!isSearch()) {
			// show the form
			return [hasHttpMethod: hasHttpMethod]
		}

		def results = doSearch { ->
			like 'configAttribute', delegate
			like 'url', delegate
			if (param('httpMethod')) {
				eq 'httpMethod', HttpMethod.valueOf(params.httpMethod), delegate
			}
		}

		renderSearch([results: results, totalCount: results.totalCount, hasHttpMethod: hasHttpMethod],
		              'configAttribute', 'httpMethod', 'url')
	}

	protected Class<?> getClazz() { Requestmap }
	protected String getClassLabelCode() { 'requestmap.label' }
	protected Map model(requestmap, String action) {
		[requestmap: requestmap, hasHttpMethod: hasHttpMethod]
	}

	protected boolean hasHttpMethod
	protected Class<?> Requestmap

	void afterPropertiesSet() {
		super.afterPropertiesSet()

		if (!conf.requestMap.className) {
			return
		}

		hasHttpMethod = ReflectionUtils.requestmapClassSupportsHttpMethod()

		Requestmap = getDomainClassClass(conf.requestMap.className)
	}
}
