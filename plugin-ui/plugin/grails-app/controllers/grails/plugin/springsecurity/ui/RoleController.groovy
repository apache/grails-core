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

import grails.plugin.springsecurity.ui.strategy.RoleStrategy
import grails.util.GrailsNameUtils

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class RoleController extends AbstractS2UiDomainController {

	/** Dependency injection for the 'uiRoleStrategy' bean. */
	RoleStrategy uiRoleStrategy

	def create() {
		super.create()
	}

	def save() {
		withForm {
			doSave uiRoleStrategy.saveRole(params)
		}.invalidToken {
			doSaveWithInvalidToken(params.authority)
		}
	}

	def edit() {
		super.edit()
	}

	def update() {
		withForm {
			doUpdate { role ->
				uiRoleStrategy.updateRole params, role
			}
		}.invalidToken {
			doUpdateWithInvalidToken(params.authority)
		}
	}

	def delete() {
		withForm {
			tryDelete { role ->
				uiRoleStrategy.deleteRole role
			}
		}.invalidToken {
			doDeleteWithInvalidToken()
		}
	}

	def search() {

		if (!isSearch()) {
			// show the form
			return
		}

		boolean useOffset = params.containsKey('offset')
		params.sort = 'authority'
		if (!param('authority')) params.authority = 'ROLE_'

		def results = doSearch {
			like 'authority', delegate
		}

		if (results.totalCount == 1 && !useOffset) {
			forward action: 'edit', params: [name: results[0][authorityNameField]]
			return
		}

		renderSearch([results: results, totalCount: results.totalCount], 'authority')
	}

	protected lookupFromParams() {
		def role = params.name ? Role.findWhere((authorityNameField): params.name) : null
		role ?: byId()
	}

	protected getTabData() {[
		[name: 'roleinfo', icon: 'icon_role',  message: message(code: 'spring.security.ui.role.info')],
		[name: 'users',    icon: 'icon_users', message: message(code: 'spring.security.ui.role.users')]
	]}

	protected Map model(role, String action) {

		def model = [role: role, tabData: tabData]

		if (action == 'edit' || action == 'update') {
			maxAndOffset()
			model.users = UserRole."findAllBy$roleClassSimpleName"(role, params)*."$userField"
			model.userCount = UserRole."countBy$roleClassSimpleName"(role)
		}

		model
	}

	protected Class<?> getClazz() { Role }
	protected String getClassLabelCode() { 'role.label' }
	protected int getAutoCompleteMinLength() { 2 }

	protected String roleClassSimpleName
	protected String userField

	void afterPropertiesSet() {
		super.afterPropertiesSet()

		if (!conf.authority.className) {
			return
		}

		roleClassSimpleName = Role.simpleName
		userField = GrailsNameUtils.getPropertyName(User.simpleName)
	}
}
