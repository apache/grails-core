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

package test

import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.acl.AclClass
import grails.plugin.springsecurity.acl.AclEntry
import grails.plugin.springsecurity.acl.AclObjectIdentity
import grails.plugin.springsecurity.acl.AclSid
import grails.plugin.springsecurity.ui.RegistrationCode

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder as SCH

@Transactional
class TestDataService {

	def aclService
	def aclUtilService
	def objectIdentityRetrievalStrategy
	SpringSecurityService springSecurityService

	void reset() {
		deleteData()
		createData()
	}

	void createData() {
		createUsersAndRoles()
		createRequestmaps()
		createRegistrationCodes()

		createPersistentTokens()
		createAcls()
	}

	void deleteData() {
		AclEntry.list()*.delete()
		AclObjectIdentity.list()*.delete()
		AclClass.list()*.delete()
		AclSid.list()*.delete()
		Report.list()*.delete()
		UserRole.list()*.delete()
		User.list()*.delete()
		Role.list()*.delete()
		Requestmap.list()*.delete()
		PersistentToken.list()*.delete()
		RegistrationCode.list()*.delete()

		User.withSession { it.flush() }
	}

	protected void createPersistentTokens() {
		Date now = new Date()
		(1..20).each {
			save new PersistentToken(series: "series$it", username: "persistent_login_test_$it",
			                         token: "token$it", lastUsed: now)
		}
	}

	protected void createRegistrationCodes() {
		registrationCodeData.each { String token, String username ->
			save new RegistrationCode(username: username, token: token)
		}
	}

	protected void createRequestmaps() {
		save new Requestmap('/secure/**', 'ROLE_ADMIN')
		save new Requestmap('/j_spring_security_switch_user', 'ROLE_RUN_AS,IS_AUTHENTICATED_FULLY')
		save new Requestmap('/**', 'permitAll')
		springSecurityService.clearCachedRequestmaps()
	}

	protected void createUsersAndRoles() {

		def grantRole = { user, String roleName ->
			UserRole.create user, Role.findByAuthority('ROLE_' + roleName.toUpperCase())
		}

		roleNames.each { save new Role("ROLE_${it.toUpperCase()}") }

		(1..3).each {
			def user = save new User("user$it", "password", "user$it@test.com")
			grantRole user, 'user'
			save new Profile(user:user,myAnswer1:'1234',myQuestion1: "Count to four",myAnswer2: '12345', myQuestion2: 'Count to Five')
		}

		User admin = save new User('admin', 'password', 'admin@test.com')
		admin.accountLocked = false
		admin.enabled = true
		save admin

		['user', 'admin', 'run_as', 'switch_user'].each { grantRole admin, it }

		def flags = [:]
		def create = { String... names -> names.each { save new User([username: it, password: 'x'] + flags) } }

		create 'foon_2','foolkiller', 'foostra', 'sonnyboy', 'abzstrak', 'orchidtemple', 'drake', 'ajc_322'

		flags = [passwordExpired: true]
		create 'hhheeeaaatt', 'mscanio', 'kittal'

		flags = [accountLocked: true]
		create 'achen', 'szhang1999', 'aaaaaasd'

		flags = [accountExpired: true]
		create 'maryrose', 'rome20c', 'ratuig'

		flags = [enabled: false]
		create 'billy9494'

		save new Profile(user:admin,myAnswer1:'1234',myQuestion1: "Count to four",myAnswer2: '12345', myQuestion2: 'Count to Five')
	}

	protected void createAcls() {

		if (!aclService) {
			return
		}

		def BasePermission = Class.forName('org.springframework.security.acls.domain.BasePermission',
			true, Thread.currentThread().contextClassLoader)
		def ADMINISTRATION = BasePermission."ADMINISTRATION"
		def READ = BasePermission."READ"
		def WRITE = BasePermission."WRITE"

		SCH.context.authentication = new UsernamePasswordAuthenticationToken(
			'admin', 'password', [new SimpleGrantedAuthority('ROLE_IGNORED')])

		try {
			def reports = (1..100).collect {
				def report = save new Report(name: "report$it", number: it)
				aclService.createAcl objectIdentityRetrievalStrategy.getObjectIdentity(report)
				report
			}

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
		finally {
			SCH.clearContext()
		}
	}

	protected save(instance) {
		instance.save(failOnError: true)
	}

	private static final registrationCodeData = [
		'e81b1e53648a47e6aef31a937154c7cb': 'registration_test_1',
		'4a7f88afec3746f7aab2f5d0d8df6d8e': 'registration_test_1',
		'c7ac5f23be70495f93e4450a78a27cb4': 'registration_test_1',
		'a50e061e0e2f424fb7fbc2ff3dae597d': 'registration_test_1',
		'd6938ad63c414a69a0da30a8c0619a60': 'registration_test_2',
		'4a589c642ea143abb2ecaea57fa0a0cc': 'registration_test_2',
		'0a154624f36d42e4aa68991a9477bd04': 'registration_test_2',
		'3842a6ae102a431c8e48177c16720713': 'registration_test_3',
		'84cefa66465a460c82f46120d9098686': 'registration_test_3',
		'fd1e40a7b31f4e8282a2a789135ed21d': 'registration_test_3',
		'89f9bbc658b14808ae4c77c6e17e551a': 'registration_test_3',
		'f6779a5e8f2045288b810a3c5e317855': 'registration_test_3',
		'3a85f41311f0421ab0f72ff003dc2aeb': 'registration_test_4',
		'1d5720125c9441688246bda276b2ada8': 'registration_test_4'
	]

	private static final roleNames = [
		'user', 'admin', 'coffee', 'instead', 'melodrama', 'nevertheless', 'phalanger',
		'plagiarisation', 'run_as', 'speechwriter', 'switch_user', 'virtual']
}
