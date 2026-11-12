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

package com.testapp

import grails.gorm.DetachedCriteria
import groovy.transform.ToString

import org.codehaus.groovy.util.HashCodeHelper
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@ToString(cache=true, includeNames=true, includePackage=false)
class TestUserTestRole implements Serializable {

	private static final long serialVersionUID = 1

	TestUser testUser
	TestRole testRole

	@Override
	boolean equals(other) {
		if (other instanceof TestUserTestRole) {
			other.testUserId == testUser?.id && other.testRoleId == testRole?.id
		}
	}

    @Override
	int hashCode() {
	    int hashCode = HashCodeHelper.initHash()
        if (testUser) {
            hashCode = HashCodeHelper.updateHash(hashCode, testUser.id)
		}
		if (testRole) {
		    hashCode = HashCodeHelper.updateHash(hashCode, testRole.id)
		}
		hashCode
	}

	static TestUserTestRole get(long testUserId, long testRoleId) {
		criteriaFor(testUserId, testRoleId).get()
	}

	static boolean exists(long testUserId, long testRoleId) {
		criteriaFor(testUserId, testRoleId).count()
	}

	private static DetachedCriteria<TestUserTestRole> criteriaFor(long testUserId, long testRoleId) {
		TestUserTestRole.where {
			testUser == TestUser.load(testUserId) &&
			testRole == TestRole.load(testRoleId)
		}
	}

	static TestUserTestRole create(TestUser testUser, TestRole testRole, boolean flush = false) {
		def instance = new TestUserTestRole(testUser: testUser, testRole: testRole)
		instance.save(flush: flush)
		instance
	}

	static boolean remove(TestUser u, TestRole r) {
		if (u != null && r != null) {
			TestUserTestRole.where { testUser == u && testRole == r }.deleteAll()
		}
	}

	static int removeAll(TestUser u) {
		u == null ? 0 : TestUserTestRole.where { testUser == u }.deleteAll() as int
	}

	static int removeAll(TestRole r) {
		r == null ? 0 : TestUserTestRole.where { testRole == r }.deleteAll() as int
	}

	static constraints = {
	    testUser nullable: false
		testRole nullable: false, validator: { TestRole r, TestUserTestRole ur ->
			if (ur.testUser?.id) {
				if (TestUserTestRole.exists(ur.testUser.id, r.id)) {
				    return ['userRole.exists']
				}
			}
		}
	}

	static mapping = {
		id composite: ['testUser', 'testRole']
		version false
	}
}
