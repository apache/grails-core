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

package com.testapp

import grails.gorm.transactions.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.access.annotation.Secured

@Transactional
@Secured('permitAll')
class TestUserController {

	def springSecurityService

	static defaultAction = 'list'

	def list() {
		params.max = Math.min((params.max ?: 10).toInteger(), 100)
		[personList: TestUser.list(params), personCount: TestUser.count()]
	}

	def create(TestUser person) {
		[person: person, authorityList: TestRole.list()]
	}

	def save(TestUser person) {
		if (person.save(flush: true)) {
			addRoles person
			redirect action: 'show', id: person.id
		}
		else {
			render view: 'create', model: [authorityList: TestRole.list(), person: person]
		}
	}

	def show(TestUser person, Long id) {
		if (!person) {
			flash.message = "TestUser not found with id $id"
			redirect action: 'list'
			return
		}
		List roleNames = person.authorities*.authority
		[person: person, roleNames: roleNames.sort()]
	}

	def edit(TestUser person, Long id) {
		if (!person) {
			flash.message = "TestUser not found with id $id"
			redirect action: 'list'
			return
		}

		return buildPersonModel(person)
	}

	def update(TestUser person, Long id, Long version) {
		if (!person) {
			flash.message = "TestUser not found with id $id"
			redirect action: 'edit', id: id
			return
		}

		if (version != null && person.version > version) {
			person.errors.rejectValue 'version', 'default.optimistic.locking.failure',
				'Another user has updated this TestUser while you were editing'
				render view: 'edit', model: buildPersonModel(person)
			return
		}

		def oldPassword = person.password
		person.properties = params

		if (person.save(flush: true)) {
			TestUserTestRole.removeAll person
			addRoles person
			redirect action: 'show', id: person.id
		}
		else {
			render view: 'edit', model: buildPersonModel(person)
		}
	}

	def delete(TestUser person, Long id) {
		if (person) {
			def authPrincipal = springSecurityService.principal
			// avoid self-delete if the logged-in user is an admin
			if (!(authPrincipal instanceof String) && authPrincipal.username == person.username) {
				flash.message = 'You can not delete yourself, please login as another admin and try again'
			}
			else {
				try {
					TestUserTestRole.removeAll person
					person.delete flush: true
					flash.message = "TestUser $id deleted."
				}
				catch (DataIntegrityViolationException e) {
					flash.message = "TestUser $id could not be deleted"
					redirect action: 'show', id: id
				}
			}
		}
		else {
			flash.message = "TestUser not found with id $id"
		}

		redirect action: 'list'
	}

	def accountLocked() {
		render 'accountLocked'
	}

	def accountDisabled() {
		render 'accountDisabled'
	}

	def accountExpired() {
		render 'accountExpired'
	}

	def passwordExpired() {
		render 'passwordExpired'
	}

	private void addRoles(person) {
		for (String key in params.keySet()) {
			if (key.contains('ROLE') && 'on' == params.get(key)) {
				TestUserTestRole.create person, TestRole.findByAuthority(key), true
			}
		}
	}

	private Map buildPersonModel(person) {

		List roles = TestRole.list().sort { it.authority }
		Set userRoleNames = person.authorities*.authority
		LinkedHashMap<TestRole, Boolean> roleMap = [:]
		for (role in roles) {
			roleMap[(role)] = userRoleNames.contains(role.authority)
		}

		[person: person, roleMap: roleMap]
	}
}
