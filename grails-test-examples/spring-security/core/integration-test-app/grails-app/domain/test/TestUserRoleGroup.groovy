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

import grails.gorm.DetachedCriteria
import groovy.transform.ToString
import org.codehaus.groovy.util.HashCodeHelper

@ToString(cache=true, includeNames=true, includePackage=false)
class TestUserRoleGroup implements Serializable {

	private static final long serialVersionUID = 1

	TestUser user
	TestRoleGroup roleGroup

	@Override
	boolean equals(other) {
		if (other instanceof TestUserRoleGroup) {
			other.userId == user?.id && other.roleGroupId == roleGroup?.id
		}
	}

	@Override
	int hashCode() {
		int hashCode = HashCodeHelper.initHash()
		if (user) {
			hashCode = HashCodeHelper.updateHash(hashCode, user.id)
		}
		if (roleGroup) {
			hashCode = HashCodeHelper.updateHash(hashCode, roleGroup.id)
		}
		hashCode
	}

	static TestUserRoleGroup get(long userId, long roleGroupId) {
		criteriaFor(userId, roleGroupId).get()
	}

	static boolean exists(long userId, long roleGroupId) {
		criteriaFor(userId, roleGroupId).count()
	}

	private static DetachedCriteria criteriaFor(long userId, long roleGroupId) {
		TestUserRoleGroup.where {
			user == TestUser.load(userId) &&
			roleGroup == TestRoleGroup.load(roleGroupId)
		}
	}

	static TestUserRoleGroup create(TestUser user, TestRoleGroup roleGroup) {
		def instance = new TestUserRoleGroup(user: user, roleGroup: roleGroup)
		instance.save()
		instance
	}

	static boolean remove(TestUser u, TestRoleGroup rg) {
		if (u && rg) {
			TestUserRoleGroup.where { user == u && roleGroup == rg }.deleteAll()
		}
	}

	static int removeAll(TestUser u) {
		u ? TestUserRoleGroup.where { user == u }.deleteAll() : 0
	}

	static int removeAll(TestRoleGroup rg) {
		rg ? TestUserRoleGroup.where { roleGroup == rg }.deleteAll() : 0
	}

	static constraints = {
		roleGroup nullable: false
		user nullable: false, validator: { TestUser u, TestUserRoleGroup ug ->
			if (ug.roleGroup?.id ) {
				if (TestUserRoleGroup.exists(u.id, ug.roleGroup.id)) {
					return ['userGroup.exists']
				}
			}
		}
	}

	static mapping = {
		id composite: ['roleGroup', 'user']
		version false
	}
}
