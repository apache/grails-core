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
class TestRoleGroupRoles implements Serializable {

	private static final long serialVersionUID = 1

	TestRoleGroup roleGroup
	TestRole role

	@Override
	boolean equals(other) {
		if (other instanceof TestRoleGroupRoles) {
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

	static TestRoleGroupRoles get(long roleGroupId, long roleId) {
		criteriaFor(roleGroupId, roleId).get()
	}

	static boolean exists(long roleGroupId, long roleId) {
		criteriaFor(roleGroupId, roleId).count()
	}

	private static DetachedCriteria criteriaFor(long roleGroupId, long roleId) {
		TestRoleGroupRoles.where {
			roleGroup == TestRoleGroup.load(roleGroupId) &&
			role == TestRole.load(roleId)
		}
	}

	static TestRoleGroupRoles create(TestRoleGroup roleGroup, TestRole role) {
		def instance = new TestRoleGroupRoles(roleGroup: roleGroup, role: role)
		instance.save()
		instance
	}

	static boolean remove(TestRoleGroup rg, TestRole r) {
		if (rg && r) {
			TestRoleGroupRoles.where { roleGroup == rg && role == r }.deleteAll()
		}
	}

	static int removeAll(TestRole r) {
		r ? TestRoleGroupRoles.where { role == r }.deleteAll() : 0
	}

	static int removeAll(TestRoleGroup rg) {
		rg ? TestRoleGroupRoles.where { roleGroup == rg }.deleteAll() : 0
	}

	static constraints = {
		roleGroup nullable: false
		role nullable: false, validator: { TestRole r, TestRoleGroupRoles rg ->
			if (rg.roleGroup?.id) {
				if (TestRoleGroupRoles.exists(rg.roleGroup.id, r.id)) {
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
