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

import grails.converters.JSON
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.ui.strategy.AclStrategy
import grails.plugin.springsecurity.ui.strategy.PropertiesStrategy
import grails.plugin.springsecurity.ui.strategy.QueryStrategy
import groovy.util.logging.Slf4j
import org.springframework.dao.DataIntegrityViolationException

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Slf4j
abstract class AbstractS2UiDomainController extends AbstractS2UiController {

	static allowedMethods = [save: 'POST', update: 'POST', delete: 'POST']
	static defaultAction = 'search'

	/** Dependency injection for the 'uiAclStrategy' bean. */
	AclStrategy uiAclStrategy

	/** Dependency injection for the 'uiPropertiesStrategy' bean. */
	PropertiesStrategy uiPropertiesStrategy

	/** Dependency injection for the 'uiQueryStrategy' bean. */
	QueryStrategy uiQueryStrategy

	def create() {
		doCreate()
	}

	protected doCreate() {
		model fromParams(), 'create'
	}

	protected doSave(instance, Closure afterSave = null) {

		if (instance.hasErrors()) {
			renderCreate model(instance, 'save')
			return
		}

		if (afterSave) {
			afterSave()
		}

		flashCreated instance.id
		redirectToEdit instance.id
	}

	protected doSaveWithInvalidToken(String flashArg = "") {
		if (!flashArg) {
			flashArg = "${message(code: 'spring.security.ui.invalid.form.default.arg')}"
		}
		response.status = 500
		log.warn('User: {} possible CSRF or double submit: {}', SpringSecurityUtils.authentication.principal.id as String, params as String)
		flash.message = "${message(code: 'spring.security.ui.invalid.save.form', args: [flashArg])}"
		redirect action: 'create'
	}

	protected void renderCreate(Map model) {
		render view: 'create', model: model
	}

	def edit() {
		doEdit()
	}

	protected doEdit() {
		def instance = lookupFromParams()
		if (!instance) return

		model instance, 'edit'
	}

	protected doUpdate(Closure update) {
		def instance = lookupFromParams()
		if (!instance || !versionCheck(instance)) {
			return
		}

		update instance

		if (instance.hasErrors()) {
			renderEdit model(instance, 'update')
		}
		else {
			flashUpdated()
			redirectToEdit()
		}
	}

	protected doUpdateWithInvalidToken(String flashArg = "") {
		if (!flashArg) {
			flashArg = "${message(code: 'spring.security.ui.invalid.form.default.arg')}"
		}
		response.status = 500
		log.warn('User: {} possible CSRF or double submit: {}', SpringSecurityUtils.authentication.principal.id as String, params as String)
		flash.message = "${message(code: 'spring.security.ui.invalid.update.form', args: [flashArg])}"
		redirectToSearch()
	}

	protected void renderEdit(Map model) {
		render view: 'edit', model: model
	}

	protected void tryDelete(Closure delete) {
		def instance = lookupFromParams()
		if (!instance) return

		try {
			delete instance
			flashDeleted()
			redirectToSearch()
		}
		catch (DataIntegrityViolationException e) {
			flashNotDeleted()
			redirectToEdit()
		}
	}

	protected doDeleteWithInvalidToken(String flashArg = "") {
		if (!flashArg) {
			flashArg = "${message(code: 'spring.security.ui.invalid.form.default.arg')}"
		}
		response.status = 500
		log.warn('User: {} possible CSRF or double submit: {}', SpringSecurityUtils.authentication.principal.id as String, params as String)
		flash.message = "${message(code: 'spring.security.ui.invalid.delete.form', args: [flashArg])}"
		redirectToSearch()
	}

	protected abstract search()

	protected boolean isSearch(String... optionalParamNames) {
		optionalParamNames.any { param it } || request.post ||
		params.max || params.offset || params.sort || params.order
	}

	protected doSearch(Closure projection = null, Closure criteria) {
		log.trace 'Search params: {}', params.toString()

		def criterias = [criteria]
		if (projection) criterias << projection

		def (int max, int offset) = maxAndOffset()
		String sort = params.sort
		String direction
		String propertyName = toPropertyName(sort)
		String sortBy = ''
		if (sort) {
			direction = params.order ?: 'asc'
			sortBy = ' order by "' + propertyName + '" ' + direction
			criterias << {

				if (propertyName.indexOf('.') > -1 && !propertyName.endsWith('.id')) {
					String first = propertyName.split('\\.')[0]
					createAlias first, '_' + first
					propertyName = '_' + propertyName
				}

				order propertyName, direction
			}
		}

		log.trace 'Search: firstResult {}  maxResults {} {}', offset as String, max as String, sortBy as String

		uiQueryStrategy.runCriteria(clazz, criterias, [max: max, offset: offset])
	}

	def ajaxSearch() {

		def jsonData

		String term = params.term
		if (term?.length() >= autoCompleteMinLength) {
			String paramName = params.paramName
			String propertyName = toPropertyName(paramName)
			params.sort = paramName
			params.order = 'asc'
			def results = doSearch {
				like 'term', paramName, delegate
				projections {
					distinct propertyName
				}
			}

			jsonData = results.collect { result -> [value: result] }
		}

		renderJson(jsonData ?: [])
	}

	protected void renderJson(jsonData) {
		render text: jsonData as JSON, contentType: 'text/plain'
	}

	protected Closure buildProjection(String path, String criterionMethod, List args) {
		uiQueryStrategy.buildProjection path, criterionMethod, args
	}

	protected void eqBoolean(String paramName, delegate) {
		// this is called for the radioGroup properties where 1 indicates true,
		// -1 indicates false, and 0 indicates either, so only include the
		// criterion if it's not null and not 0
		Boolean value
		Integer i = params.int(paramName)
		if (i) {
			value = i == 1
		}
		eq 'boolean', paramName, value, delegate
	}

	protected void eqInt(String paramName, delegate) {
		eq 'int', paramName, params.int(paramName), delegate
	}

	protected void eqLong(String paramName, delegate) {
		eq 'long', paramName, params.long(paramName), delegate
	}

	protected void eqLongId(String paramName, delegate) {
		eqLong paramName + '.id', delegate
	}

	protected void eq(String type, String paramName, value, delegate) {
		String propertyName = toPropertyName(paramName)
		traceCriterion type, paramName, propertyName, value
		if (value != null) {
			delegate.eq propertyName, value
		}
	}

	protected void like(String paramName, String propertyName = null, delegate) {
		if (propertyName == null) {
			propertyName = toPropertyName(paramName)
		}
		def value = params[paramName]
		log.trace '{}', traceCriterion('ilike', paramName, propertyName, value)
		if (value) {
			delegate.ilike propertyName, '%' + value + '%'
		}
	}

	protected String traceCriterion(String type, String paramName, String propertyName, value) {
		def sb = new StringBuilder()
		if (type == 'ilike') {
			sb << type
		}
		else {
			sb << 'eq (' << type << ')'
		}
		sb << ' param: "' << paramName << '"'

		if (propertyName != paramName) {
			sb << ' (property: "' << propertyName << '")'
		}
		sb << ' value: '

		if (type == 'ilike') {
			if (value == null) {
				sb << 'null'
			}
			else {
				sb << '"' << value << '"'
			}
		}
		else {
			sb << value
		}

		sb.toString()
	}

	protected void renderSearch(Map model, String... queryParamNames) {
		model.searched = true
		addQueryParamsToModelForPaging model, queryParamNames
		render view: 'search', model: model
	}

	protected void addQueryParamsToModelForPaging(Map model, String... names) {
		def allNames = names as List

		def queryParams = model.queryParams = [:]

		for (name in allNames) {
			def value = params[name]
			if (value != null) {
				String modelKey = name
				if (modelKey.endsWith('.id')) {
					modelKey = modelKey[0..-4]
				}
				queryParams[name] = model[modelKey] = value
			}
		}

		model.order = params.order
		model.sort = params.sort
	}

	protected void redirectToEdit(id = params.id) {
		redirect action: 'edit', id: id
	}

	protected void redirectToSearch() {
		redirect action: 'search'
	}

	protected abstract Map model(instance, String action)

	protected boolean versionCheck(instance) {

		Long version = params.long('version')
		if (version) {
			def instanceVersion = instance.version
			if (instanceVersion instanceof Number && instanceVersion > version) {
				instance.errors.rejectValue('version', 'default.optimistic.locking.failure',
						[message(code: classLabelCode, default: simpleClassName)] as Object[],
						'Another user has updated this ' + simpleClassName + ' while you were editing')
				renderEdit model(instance, 'update')
				return false
			}
		}

		true
	}

	protected lookupFromParams() {
		byId()
	}

	protected byId() {
		def instance = clazz.get(params.id)
		if (instance) return instance

		flashNotFound()
		redirectToSearch()
	}

	protected List maxAndOffset() {
		int max = setIfMissing('max', defaultPageSize, maxPageSize)
		int offset = setIfMissing('offset', 0)
		[max, offset]
	}

	protected fromParams() {
		uiPropertiesStrategy.setProperties params, clazz, null
	}

	protected int setIfMissing(String paramName, int valueIfMissing, Integer max = null) {
		int value = (params[paramName] ?: valueIfMissing) as int
		if (max) {
			value = Math.min(value, max)
		}
		params[paramName] = value
		value
	}

	protected boolean param(String paramName) {
		params.containsKey(paramName) && params[paramName] != 'null'
	}

	protected String toPropertyName(String paramName) {
		uiPropertiesStrategy.paramNameToPropertyName paramName, controllerName
	}

	protected String labelMessage() {
		message(code: classLabelCode, default: simpleClassName)
	}

	protected void flashCreated(id) {
		flash.message = message(code: 'default.created.message', args: [labelMessage(), id])
	}

	protected void flashUpdated() {
		flash.message = message(code: 'default.updated.message', args: [labelMessage(), params.id])
	}

	protected void flashDeleted() {
		flash.message = message(code: 'default.deleted.message', args: [labelMessage(), params.id])
	}

	protected void flashNotDeleted() {
		flash.message = message(code: 'default.not.deleted.message', args: [labelMessage(), params.id])
	}

	protected void flashNotFound() {
		flash.message = message(code: 'default.not.found.message', args: [labelMessage(), params.id])
	}

	protected abstract String getClassLabelCode()

	protected abstract Class<?> getClazz()

	protected String getSimpleClassName() { clazz.simpleName }

	protected int getDefaultPageSize() { 10 }

	protected int getMaxPageSize() { 100 }

	protected int getAutoCompleteMinLength() { 3 }

	protected Class<?> AclClass
	protected Class<?> AclSid

	void afterPropertiesSet() {
		super.afterPropertiesSet()
		AclClass = getDomainClassClass('grails.plugin.springsecurity.acl.AclClass')
		AclSid = getDomainClassClass('grails.plugin.springsecurity.acl.AclSid')
	}
}
