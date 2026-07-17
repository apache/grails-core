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

import org.springframework.context.MessageSource
import test.TestRole
import test.TestRoleGroup
import test.TestRoleGroupRoles
import test.TestUser
import test.TestUserRoleGroup

/**
 * Integration tests for the RoleGroup and RoleGroupRoles domain classes.
 *
 * @author Burt Beckwith
 */
class RoleGroupSpec extends AbstractIntegrationSpec {

	MessageSource messageSource

	void 'roleGroup get roles'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRoleGroup rg2 = save(new TestRoleGroup(name: 'rg2'))

		5.times { i ->
			def r = save(new TestRole(auth: "r$i", description: "r$i"))
			TestRoleGroupRoles.create rg1, r
			if (i > 2) {
				TestRoleGroupRoles.create rg2, r
			}
		}

		flushAndClear()

		rg1 = TestRoleGroup.get(rg1.id)
		rg2 = TestRoleGroup.get(rg2.id)

		then:
		['r0', 'r1', 'r2', 'r3', 'r4'] == rg1.roles*.auth.sort()
		['r3', 'r4'] == rg2.roles*.auth.sort()
	}

	void 'UserRoleGroup equals and hashCode'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRoleGroup rg2 = save(new TestRoleGroup(name: 'rg2'))
		TestUser u1 = save(new TestUser(loginName: 'u1', passwrrd: 'u1'))
		TestUser u2 = save(new TestUser(loginName: 'u2', passwrrd: 'u2'))

		TestUserRoleGroup urg1 = new TestUserRoleGroup(user: u1, roleGroup: rg1)
		TestUserRoleGroup urg2 = new TestUserRoleGroup(user: u1, roleGroup: rg1)

		then:
		urg1 == urg2
		urg1.hashCode() == urg2.hashCode()

		when:
		urg1.user = u2

		then:
		urg1 != urg2
		urg1.hashCode() != urg2.hashCode()

		when:
		urg1.user = u1
		urg1.roleGroup = rg2

		then:
		urg1 != urg2
		urg1.hashCode() != urg2.hashCode()

		when:
		urg1.user = u2
		urg1.roleGroup = rg1

		then:
		urg1 != urg2
		urg1.hashCode() != urg2.hashCode()

		when:
		urg1.roleGroup = rg2

		then:
		urg1 != urg2
		urg1.hashCode() != urg2.hashCode()
	}

	void 'RoleGroupRole equals and hashCode'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRoleGroup rg2 = save(new TestRoleGroup(name: 'rg2'))
		TestRole r1 = save(new TestRole(auth: 'r1', description: 'r1'))
		TestRole r2 = save(new TestRole(auth: 'r2', description: 'r2'))

		TestRoleGroupRoles rgr1 = new TestRoleGroupRoles(roleGroup: rg1, role: r1)
		TestRoleGroupRoles rgr2 = new TestRoleGroupRoles(roleGroup: rg1, role: r1)

		then:
		rgr1 == rgr2
		rgr1.hashCode() == rgr2.hashCode()

		when:
		rgr1.role = r2

		then:
		rgr1 != rgr2
		rgr1.hashCode() != rgr2.hashCode()

		when:
		rgr1.role = r1
		rgr1.roleGroup = rg2

		then:
		rgr1 != rgr2
		rgr1.hashCode() != rgr2.hashCode()

		when:
		rgr1.role = r2
		rgr1.roleGroup = rg1

		then:
		rgr1 != rgr2
		rgr1.hashCode() != rgr2.hashCode()

		when:
		rgr1.roleGroup = rg2

		then:
		rgr1 != rgr2
		rgr1.hashCode() != rgr2.hashCode()
	}

	void 'UserRoleGroup create'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestUser u1 = save(new TestUser(loginName: 'u1', passwrrd: 'u1'))

		def instance = TestUserRoleGroup.create(null, null)

		then:
		instance
		instance.hasErrors()
		2 == instance.errors.errorCount

		when:
		def fieldError = instance.errors.getFieldError('user')

		then:
		fieldError
		'Property [user] of class [class test.TestUserRoleGroup] cannot be null' == getMessage(fieldError)

		when:
		fieldError = instance.errors.getFieldError('roleGroup')

		then:
		fieldError
		'Property [roleGroup] of class [class test.TestUserRoleGroup] cannot be null' == getMessage(fieldError)

		when:
		instance = TestUserRoleGroup.create(null, rg1)

		then:
		instance
		instance.hasErrors()
		1 == instance.errors.errorCount

		when:
		fieldError = instance.errors.getFieldError('user')

		then:
		fieldError
		'Property [user] of class [class test.TestUserRoleGroup] cannot be null' == getMessage(fieldError)

		when:
		instance = TestUserRoleGroup.create(u1, null)

		then:
		instance
		instance.hasErrors()
		1 == instance.errors.errorCount

		when:
		fieldError = instance.errors.getFieldError('roleGroup')

		then:
		fieldError
		'Property [roleGroup] of class [class test.TestUserRoleGroup] cannot be null' == getMessage(fieldError)

		when:
		instance = TestUserRoleGroup.create(u1, rg1)

		then:
		instance
		!instance.hasErrors()

		flushAndClear()

		when:
		instance = TestUserRoleGroup.create(u1, rg1)

		then:
		instance
		instance.hasErrors()
		1 == instance.errors.errorCount

		when:
		fieldError = instance.errors.getFieldError('user')

		then:
		fieldError
		'userGroup.exists' == fieldError.code
	}

	void 'RoleGroupRole create'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRole r1 = save(new TestRole(auth: 'r1', description: 'r1'))

		def instance = TestRoleGroupRoles.create(null, null)

		then:
		instance
		instance.hasErrors()
		2 == instance.errors.errorCount

		when:
		def fieldError = instance.errors.getFieldError('role')

		then:
		fieldError
		'Property [role] of class [class test.TestRoleGroupRoles] cannot be null' == getMessage(fieldError)

		when:
		fieldError = instance.errors.getFieldError('roleGroup')

		then:
		fieldError
		'Property [roleGroup] of class [class test.TestRoleGroupRoles] cannot be null' == getMessage(fieldError)

		when:
		instance = TestRoleGroupRoles.create(rg1, null)

		then:
		instance
		instance.hasErrors()
		1 == instance.errors.errorCount

		when:
		fieldError = instance.errors.getFieldError('role')

		then:
		fieldError
		'Property [role] of class [class test.TestRoleGroupRoles] cannot be null' == getMessage(fieldError)

		when:
		instance = TestRoleGroupRoles.create(null, r1)

		then:
		instance
		instance.hasErrors()
		1 == instance.errors.errorCount

		when:
		fieldError = instance.errors.getFieldError('roleGroup')

		then:
		fieldError
		'Property [roleGroup] of class [class test.TestRoleGroupRoles] cannot be null' == getMessage(fieldError)

		when:
		instance = TestRoleGroupRoles.create(rg1, r1)

		then:
		instance
		!instance.hasErrors()

		when:
		flushAndClear()

		instance = TestRoleGroupRoles.create(rg1, r1)

		then:
		instance
		instance.hasErrors()
		1 == instance.errors.errorCount

		when:
		fieldError = instance.errors.getFieldError('role')

		then:
		fieldError
		'roleGroup.exists' == fieldError.code
	}

	void 'UserRoleGroup get'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestUser u1 = save(new TestUser(loginName: 'u1', passwrrd: 'u1'))
		flushAndClear()

		then:
		!TestUserRoleGroup.get(u1.id, rg1.id)

		when:
		TestUserRoleGroup.create u1, rg1
		flushAndClear()

		then:
		TestUserRoleGroup.get(u1.id, rg1.id)
	}

	void 'RoleGroupRole get'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRole r1 = save(new TestRole(auth: 'r1', description: 'r1'))
		flushAndClear()

		then:
		!TestRoleGroupRoles.get(rg1.id, r1.id)

		when:
		TestRoleGroupRoles.create rg1, r1
		flushAndClear()

		then:
		TestRoleGroupRoles.get(rg1.id, r1.id)
	}

	void 'UserRoleGroup exists'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestUser u1 = save(new TestUser(loginName: 'u1', passwrrd: 'u1'))
		flushAndClear()

		then:
		!TestUserRoleGroup.exists(u1.id, rg1.id)

		when:
		TestUserRoleGroup.create u1, rg1
		flushAndClear()

		then:
		TestUserRoleGroup.exists(u1.id, rg1.id)
	}

	void 'RoleGroupRole exists'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRole r1 = save(new TestRole(auth: 'r1', description: 'r1'))
		flushAndClear()

		then:
		!TestRoleGroupRoles.exists(rg1.id, r1.id)

		when:
		TestRoleGroupRoles.create rg1, r1
		flushAndClear()

		then:
		TestRoleGroupRoles.exists(rg1.id, r1.id)
	}

	void 'UserRoleGroup remove'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestUser u1 = save(new TestUser(loginName: 'u1', passwrrd: 'u1'))
		flushAndClear()

		then:
		!TestUserRoleGroup.remove(u1, rg1)

		when:
		TestUserRoleGroup.create u1, rg1
		flushAndClear()

		then:
		TestUserRoleGroup.remove(u1, rg1)
	}

	void 'RoleGroupRole remove'() {
		when:
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRole r1 = save(new TestRole(auth: 'r1', description: 'r1'))
		flushAndClear()

		then:
		!TestRoleGroupRoles.remove(rg1, r1)

		when:
		TestRoleGroupRoles.create rg1, r1
		flushAndClear()

		then:
		TestRoleGroupRoles.remove(rg1, r1)
	}

	void 'UserRoleGroup removeAllByRoleGroup'() {
		when:
		TestUser u1 = save(new TestUser(loginName: 'u1', passwrrd: 'u1'))
		TestUser u2 = save(new TestUser(loginName: 'u2', passwrrd: 'u2'))
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRoleGroup rg2 = save(new TestRoleGroup(name: 'rg2'))

		TestUserRoleGroup.create u1, rg1
		TestUserRoleGroup.create u2, rg1
		TestUserRoleGroup.create u1, rg2
		flushAndClear()

		then:
		3 == TestUserRoleGroup.count()

		when:
		TestUserRoleGroup.removeAll rg1
		flushAndClear()

		then:
		1 == TestUserRoleGroup.count()
	}

	void 'RoleGroupRole removeAllByRoleGroup'() {
		when:
		TestRole r1 = save(new TestRole(auth: 'r1', description: 'r1'))
		TestRole r2 = save(new TestRole(auth: 'r2', description: 'r2'))
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRoleGroup rg2 = save(new TestRoleGroup(name: 'rg2'))

		TestRoleGroupRoles.create rg1, r1
		TestRoleGroupRoles.create rg1, r2
		TestRoleGroupRoles.create rg2, r1
		flushAndClear()

		then:
		3 == TestRoleGroupRoles.count()

		when:
		TestRoleGroupRoles.removeAll rg1
		flushAndClear()

		then:
		1 == TestRoleGroupRoles.count()
	}

	void 'UserRoleGroup removeAllByUser'() {
		when:
		TestUser u1 = save(new TestUser(loginName: 'u1', passwrrd: 'u1'))
		TestUser u2 = save(new TestUser(loginName: 'u2', passwrrd: 'u2'))
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRoleGroup rg2 = save(new TestRoleGroup(name: 'rg2'))

		TestUserRoleGroup.create u1, rg1
		TestUserRoleGroup.create u2, rg1
		TestUserRoleGroup.create u1, rg2
		flushAndClear()

		then:
		3 == TestUserRoleGroup.count()

		when:
		TestUserRoleGroup.removeAll u2
		flushAndClear()

		then:
		2 == TestUserRoleGroup.count()
	}

	void 'RoleGroupRole removeAllByRole'() {
		when:
		TestRole r1 = save(new TestRole(auth: 'r1', description: 'r1'))
		TestRole r2 = save(new TestRole(auth: 'r2', description: 'r2'))
		TestRoleGroup rg1 = save(new TestRoleGroup(name: 'rg1'))
		TestRoleGroup rg2 = save(new TestRoleGroup(name: 'rg2'))

		TestRoleGroupRoles.create rg1, r1
		TestRoleGroupRoles.create rg1, r2
		TestRoleGroupRoles.create rg2, r1
		flushAndClear()

		then:
		3 == TestRoleGroupRoles.count()

		when:
		TestRoleGroupRoles.removeAll r2
		flushAndClear()

		then:
		2 == TestRoleGroupRoles.count()
	}

	private String getMessage(fieldError) {
		messageSource.getMessage fieldError, Locale.default
	}
}
