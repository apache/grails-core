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

package demo

import grails.gorm.DetachedCriteria
import groovy.transform.ToString

import org.codehaus.groovy.util.HashCodeHelper
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@ToString(cache=true, includeNames=true, includePackage=false)
class UserRole implements Serializable {

	private static final long serialVersionUID = 1

	User user
	Role role

	@Override
	boolean equals(other) {
		if (other instanceof UserRole) {
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

	static UserRole get(long userId, long roleId) {
		criteriaFor(userId, roleId).get()
	}

	static boolean exists(long userId, long roleId) {
		criteriaFor(userId, roleId).count()
	}

	private static DetachedCriteria<UserRole> criteriaFor(long userId, long roleId) {
		UserRole.where {
			user == User.load(userId) &&
			role == Role.load(roleId)
		}
	}

	static UserRole create(User user, Role role, boolean flush = false) {
		def instance = new UserRole(user: user, role: role)
		instance.save(flush: flush)
		instance
	}

	static boolean remove(User u, Role r) {
		if (u != null && r != null) {
			UserRole.where { user == u && role == r }.deleteAll()
		}
	}

	static int removeAll(User u) {
		u == null ? 0 : UserRole.where { user == u }.deleteAll() as int
	}

	static int removeAll(Role r) {
		r == null ? 0 : UserRole.where { role == r }.deleteAll() as int
	}

	static constraints = {
	    user nullable: false
		role nullable: false, validator: { Role r, UserRole ur ->
			if (ur.user?.id) {
				if (UserRole.exists(ur.user.id, r.id)) {
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
