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

/**
 * @author Burt Beckwith
 */
@ToString(cache=true, includeNames=true, includePackage=false)
class TestUserRole implements Serializable {

	private static final long serialVersionUID = 1

	TestUser user
	TestRole role

	@Override
	boolean equals(other) {
		if (other instanceof TestUserRole) {
			other.userId == user?.id && other.roleId == role?.id
		}
	}

	@Override
	int hashCode() {
		int hashCode = HashCodeHelper.initHash()
		if (user) {
			hashCode = HashCodeHelper.updateHash(hashCode, user.id)
		}
		if (role) {
			hashCode = HashCodeHelper.updateHash(hashCode, role.id)
		}
		hashCode
	}

	static TestUserRole get(long userId, long roleId) {
		criteriaFor(userId, roleId).get()
	}

	static boolean exists(long userId, long roleId) {
		criteriaFor(userId, roleId).count()
	}

	private static DetachedCriteria criteriaFor(long userId, long roleId) {
		TestUserRole.where {
			user == TestUser.load(userId) &&
			role == TestRole.load(roleId)
		}
	}

	static TestUserRole create(TestUser user, TestRole role) {
		def instance = new TestUserRole(user: user, role: role)
		instance.save()
		instance
	}

	static boolean remove(TestUser u, TestRole r) {
		if (u && r) {
			TestUserRole.where { user == u && role == r }.deleteAll()
		}
	}

	static int removeAll(TestUser u) {
		u ? TestUserRole.where { user == u }.deleteAll() : 0
	}

	static int removeAll(TestRole r) {
		r ? TestUserRole.where { role == r }.deleteAll() : 0
	}

	static constraints = {
		role validator: { TestRole r, TestUserRole ur ->
			if (ur.user?.id) {
				if (TestUserRole.exists(ur.user.id, r.id)) {
					return ['userRole.exists']
				}
			}
		}
	}

	static mapping = {
		id composite: ['user', 'role']
		version false
	}
}
