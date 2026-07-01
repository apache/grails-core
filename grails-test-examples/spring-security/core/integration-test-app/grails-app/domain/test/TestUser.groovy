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

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * @author Burt Beckwith
 */
@EqualsAndHashCode(includes='loginName')
@ToString(includes='loginName', includeNames=true, includePackage=false)
class TestUser implements Serializable {

	private static final long serialVersionUID = 1

	transient springSecurityService

	String loginName
	String passwrrd
	boolean enabld = true
	boolean accountExpired
	boolean accountLocked
	boolean passwordExpired

	Set<TestRole> getRoles() { TestUserRole.findAllByUser(this)*.role }
	Set<TestRoleGroup> getGroups() { TestUserRoleGroup.findAllByUser(this)*.roleGroup }
	Collection<String> getRoleNames() { roles*.auth }

	def beforeInsert() {
		encodePassword()
	}

	def beforeUpdate() {
		if (isDirty('passwrrd')) {
			encodePassword()
		}
	}

	protected void encodePassword() {
		passwrrd = springSecurityService?.passwordEncoder ? springSecurityService.encodePassword(passwrrd) : passwrrd
	}

	static transients = ['springSecurityService']

	static constraints = {
		loginName blank: false, unique: true
		passwrrd blank: false, password: true
	}

	static mapping = {
		autowire true
	}
}
