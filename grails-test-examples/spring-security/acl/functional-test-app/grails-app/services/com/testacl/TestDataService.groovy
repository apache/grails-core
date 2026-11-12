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

package com.testacl

import grails.gorm.DetachedCriteria
import grails.plugin.springsecurity.acl.AclClass
import grails.plugin.springsecurity.acl.AclEntry
import grails.plugin.springsecurity.acl.AclObjectIdentity
import grails.plugin.springsecurity.acl.AclService
import grails.plugin.springsecurity.acl.AclSid
import grails.plugin.springsecurity.acl.AclUtilService
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct
import org.springframework.security.acls.model.ObjectIdentityRetrievalStrategy
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder as SCH

import static org.springframework.security.acls.domain.BasePermission.ADMINISTRATION
import static org.springframework.security.acls.domain.BasePermission.READ
import static org.springframework.security.acls.domain.BasePermission.WRITE

@Slf4j
@Transactional
class TestDataService {

	AclService aclService
	AclUtilService aclUtilService
	ObjectIdentityRetrievalStrategy objectIdentityRetrievalStrategy

	@PostConstruct
	void init() {
		reset()
	}

	void reset() {
		deleteAll()
		createData()
	}

	void deleteAll() {
		log.debug 'deleteAll'

		[AclEntry, AclObjectIdentity, AclSid, AclClass, UserRole, User, Role, Report].each { clazz ->

			if (!clazz.count()) return

			log.debug 'deleteAll for {}', clazz.simpleName

			DetachedCriteria dc = clazz == UserRole ?
					UserRole.where({ user != null }) :
					new DetachedCriteria(clazz).build { gt 'id', 0L }
			int deleted = dc.deleteAll()
			log.debug 'Deleted {} from {}', deleted, clazz.simpleName

			clazz.withSession { it.clear() }

			int remaining = clazz.count()
			assert remaining == 0, "Didn't delete all from $clazz.simpleName - $remaining remaining"
		}
	}

	void createData() {
		createUsers()

		// Set a user account that will initially own all the created data
		SCH.context.authentication = new UsernamePasswordAuthenticationToken(
				'admin', 'password', [new SimpleGrantedAuthority('ROLE_IGNORED')])

		grantPermissions()

		// logout
		SCH.clearContext()
	}

	private void createUsers() {
		log.debug 'createData: users'

		def roleAdmin = new Role('ROLE_ADMIN').save(failOnError: true)
		def roleUser = new Role('ROLE_USER').save(failOnError: true)

		3.times {
			long id = it + 1
			def user = new User(username:"user$id", password: 'password').save(failOnError: true)
			UserRole.create user, roleUser
		}

		def admin = new User(username: 'admin', password:'password').save(failOnError: true)

		UserRole.create admin, roleUser
		UserRole.create admin, roleAdmin
	}

	private void grantPermissions() {
		log.debug 'createData: reports'

		def reports = (1..100).collect { int number ->
			def report = new Report("report$number", number).save(failOnError: true)
			aclService.createAcl objectIdentityRetrievalStrategy.getObjectIdentity(report)
			report
		}

		log.debug 'createData: permissions'

		// grant user 1 admin on 11,12 and read on 1-67
		aclUtilService.addPermission reports[10], 'user1', ADMINISTRATION
		aclUtilService.addPermission reports[11], 'user1', ADMINISTRATION
		67.times {
			aclUtilService.addPermission reports[it], 'user1', READ
		}

		// grant user 2 read on 1-5, write on 5
		5.times {
			aclUtilService.addPermission reports[it], 'user2', READ
		}
		aclUtilService.addPermission reports[4], 'user2', WRITE

		// user 3 has no grants

		// grant admin read and admin on all
		for (report in reports) {
			aclUtilService.addPermission report, 'admin', READ
			aclUtilService.addPermission report, 'admin', ADMINISTRATION
		}

		// grant user 1 ownership on 1,2 to allow the user to grant
		aclUtilService.changeOwner reports[0], 'user1'
		aclUtilService.changeOwner reports[1], 'user1'
	}
}
