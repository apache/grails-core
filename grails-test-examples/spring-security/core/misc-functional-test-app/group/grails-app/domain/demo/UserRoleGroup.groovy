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
class UserRoleGroup implements Serializable {

	private static final long serialVersionUID = 1

	User user
	RoleGroup roleGroup

	@Override
	boolean equals(other) {
		if (other instanceof UserRoleGroup) {
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
	
	static UserRoleGroup get(long userId, long roleGroupId) {
		criteriaFor(userId, roleGroupId).get()
	}

	static boolean exists(long userId, long roleGroupId) {
		criteriaFor(userId, roleGroupId).count()
	}

	private static DetachedCriteria<UserRoleGroup> criteriaFor(long userId, long roleGroupId) {
		UserRoleGroup.where {
			user == User.load(userId) &&
			roleGroup == RoleGroup.load(roleGroupId)
		}
	}

	static UserRoleGroup create(User user, RoleGroup roleGroup, boolean flush = false) {
		def instance = new UserRoleGroup(user: user, roleGroup: roleGroup)
		instance.save(flush: flush)
		instance
	}

	static boolean remove(User u, RoleGroup rg) {
		if (u != null && rg != null) {
			UserRoleGroup.where { user == u && roleGroup == rg }.deleteAll()
		}
	}

	static int removeAll(User u) {
		u == null ? 0 : UserRoleGroup.where { user == u }.deleteAll() as int
	}

	static int removeAll(RoleGroup rg) {
		rg == null ? 0 : UserRoleGroup.where { roleGroup == rg }.deleteAll() as int
	}

	static constraints = {
	    roleGroup nullable: false
		user nullable: false, validator: { User u, UserRoleGroup ug ->
			if (ug.roleGroup?.id) {
				if (UserRoleGroup.exists(u.id, ug.roleGroup.id)) {
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
