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
class RoleGroupRole implements Serializable {

	private static final long serialVersionUID = 1

	RoleGroup roleGroup
	Role role

	@Override
	boolean equals(other) {
		if (other instanceof RoleGroupRole) {
			other.roleId == role?.id && other.roleGroupId == roleGroup?.id
		}
	}

	@Override
	int hashCode() {
	    int hashCode = HashCodeHelper.initHash()
        if (roleGroup) {
            hashCode = HashCodeHelper.updateHash(hashCode, roleGroup.id)
		}
		if (role) {
		    hashCode = HashCodeHelper.updateHash(hashCode, role.id)
		}
		hashCode
	}

	static RoleGroupRole get(long roleGroupId, long roleId) {
		criteriaFor(roleGroupId, roleId).get()
	}

	static boolean exists(long roleGroupId, long roleId) {
		criteriaFor(roleGroupId, roleId).count()
	}

	private static DetachedCriteria<RoleGroupRole> criteriaFor(long roleGroupId, long roleId) {
		RoleGroupRole.where {
			roleGroup == RoleGroup.load(roleGroupId) &&
			role == Role.load(roleId)
		}
	}

	static RoleGroupRole create(RoleGroup roleGroup, Role role, boolean flush = false) {
		def instance = new RoleGroupRole(roleGroup: roleGroup, role: role)
		instance.save(flush: flush)
		instance
	}

	static boolean remove(RoleGroup rg, Role r) {
		if (rg != null && r != null) {
			RoleGroupRole.where { roleGroup == rg && role == r }.deleteAll()
		}
	}

	static int removeAll(Role r) {
		r == null ? 0 : RoleGroupRole.where { role == r }.deleteAll() as int
	}

	static int removeAll(RoleGroup rg) {
		rg == null ? 0 : RoleGroupRole.where { roleGroup == rg }.deleteAll() as int
	}

	static constraints = {
	    roleGroup nullable: false
		role nullable: false, validator: { Role r, RoleGroupRole rg ->
			if (rg.roleGroup?.id) {
				if (RoleGroupRole.exists(rg.roleGroup.id, r.id)) {
				    return ['roleGroup.exists']
				}
			}
		}
	}

	static mapping = {
		id composite: ['roleGroup', 'role']
		version false
	}
}
