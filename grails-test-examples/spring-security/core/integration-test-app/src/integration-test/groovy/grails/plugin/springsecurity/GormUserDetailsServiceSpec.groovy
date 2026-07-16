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

import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import test.TestRole
import test.TestRoleGroup
import test.TestRoleGroupRoles
import test.TestUser
import test.TestUserRole
import test.TestUserRoleGroup

/**
 * Integration tests for GormUserDetailsService.
 *
 * @author Burt Beckwith
 */
class GormUserDetailsServiceSpec extends AbstractIntegrationSpec {

	private static final String ADMIN_ROLE_NAME = 'ROLE_ADMIN'
	private static final String SUPER_ADMIN_ROLE_NAME = 'ROLE_SUPERADMIN'

	private TestRole adminRole
	private TestRole superAdminRole

	private securityConfigGroupPropertyNames = ['useRoleGroups', 'authority.className', 'authority.nameField',
	                                            'authority.groupAuthorityNameField', 'userLookup.authoritiesPropertyName']
	private securityConfigGroupPropertyValues

	def passwordEncoder
	UserDetailsService userDetailsService

	void setup() {
		securityConfigGroupPropertyValues = securityConfigGroupPropertyNames.collectEntries { String name ->
			[(name): ReflectionUtils.getConfigProperty(name)]
		}

		assert !TestRole.count()

		adminRole = save(new TestRole(auth: ADMIN_ROLE_NAME, description: 'admin'))
		superAdminRole = save(new TestRole(auth: SUPER_ADMIN_ROLE_NAME, description: 'super admin'))
		flushAndClear()

		assert 2 == TestRole.count()
	}

	void cleanup() {
		securityConfigGroupPropertyValues.each { key, value ->
			ReflectionUtils.setConfigProperty key, value
		}
	}

	void 'loadUserByUsername not found'() {
		when:
		userDetailsService.loadUserByUsername 'not_a_user'

		then:
		UsernameNotFoundException e = thrown()
		e.message.contains 'not found'
	}

	void 'loadUserByUsername no roles'() {
		setup:
		String loginName = 'loginName'

		expect:
		!TestUser.count()

		when:
		save new TestUser(loginName: loginName, passwrrd: 'password')
		flushAndClear()

		then:
		1 == TestUser.count()

		when:
		def details = userDetailsService.loadUserByUsername(loginName)

		then:
		1 == details.authorities.size()
		'ROLE_NO_ROLES' == details.authorities.iterator().next().authority
	}

	void 'loadUserByUsername'() {
		setup:
		String loginName = 'loginName'
		String password = 'password123'

		expect:
		!TestUser.count()

		when:
		def user = save(new TestUser(loginName: loginName, passwrrd: password))
		flushAndClear()

		then:
		1 == TestUser.count()

		when:
		TestUserRole.create user, adminRole
		TestUserRole.create user, superAdminRole
		flushAndClear()

		then:
		2 == TestUserRole.count()

		when:
		def details = userDetailsService.loadUserByUsername(loginName)

		then:
		details

		passwordEncoder.matches password, "{noop}$details.password"
		loginName == details.username
		details.enabled
		details.accountNonExpired
		details.accountNonLocked
		details.credentialsNonExpired
		[ADMIN_ROLE_NAME, SUPER_ADMIN_ROLE_NAME] == details.authorities*.authority.sort()
	}

	void 'loadUserByUsername userLookup.usernameIgnoreCase'() {
		setup:
		ReflectionUtils.setConfigProperty 'userLookup.usernameIgnoreCase', true

		String loginName = 'loginName'
		String password = 'password123'

		expect:
		!TestUser.count()

		when:
		save(new TestUser(loginName: loginName, passwrrd: password))
		flushAndClear()

		then:
		1 == TestUser.count()
		0 == TestRoleGroup.count()

		when:
		def details = userDetailsService.loadUserByUsername(loginName.toLowerCase())

		then:
		loginName == details.username
	}

	void 'loadUserByUsername using role groups'() {
		setup:
		//Change the config to use authority groups
		ReflectionUtils.setConfigProperty 'useRoleGroups', true
		ReflectionUtils.setConfigProperty 'authority.className', 'test.TestRoleGroup'
		ReflectionUtils.setConfigProperty 'authority.nameField', 'auth'
		ReflectionUtils.setConfigProperty 'authority.groupAuthorityNameField', 'roles'
		ReflectionUtils.setConfigProperty 'userLookup.authoritiesPropertyName', 'groups'

		String loginName = 'loginName'
		String password = 'password123'

		expect:
		!TestUser.count()

		when:
		def user = save(new TestUser(loginName: loginName, passwrrd: password))
		flushAndClear()

		then:
		1 == TestUser.count()
		0 == TestRoleGroup.count()

		when:
		def roleGroup = save(new TestRoleGroup(name: 'testRoleGroup1'))
		flushAndClear()

		then:
		1 == TestRoleGroup.count()

		when:
		TestRoleGroupRoles.create roleGroup, adminRole
		TestRoleGroupRoles.create roleGroup, superAdminRole
		flushAndClear()

		then:
		2 == TestRoleGroupRoles.count()
		!TestUserRoleGroup.count()

		when:
		TestUserRoleGroup.create user, roleGroup
		flushAndClear()

		then:
		1 == TestUserRoleGroup.count()

		when:
		def details = userDetailsService.loadUserByUsername(loginName)

		then:
		details

		[ADMIN_ROLE_NAME, SUPER_ADMIN_ROLE_NAME] == details.authorities*.authority.sort()
	}

	void 'loadUserByUsername skip roles'() {
		setup:
		String loginName = 'loginName'
		String password = 'password123'

		expect:
		!TestUser.count()

		when:
		def user = save(new TestUser(loginName: loginName, passwrrd: password))
		flushAndClear()

		then:
		1 == TestUser.count()

		when:
		TestUserRole.create user, adminRole
		TestUserRole.create user, superAdminRole
		flushAndClear()

		def details = userDetailsService.loadUserByUsername(loginName, false)

		then:
		details

		passwordEncoder.matches password, "{noop}$details.password"
		loginName == details.username
		details.enabled
		details.accountNonExpired
		details.accountNonLocked
		details.credentialsNonExpired
		!details.authorities
	}
}
