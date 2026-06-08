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
package grails.plugin.springsecurity.acl.jdbc

import grails.gorm.transactions.Rollback
import grails.plugin.springsecurity.acl.AbstractIntegrationSpec
import grails.plugin.springsecurity.acl.AclClass
import grails.plugin.springsecurity.acl.AclEntry
import grails.plugin.springsecurity.acl.AclObjectIdentity
import grails.plugin.springsecurity.acl.AclSid
import org.springframework.cache.CacheManager
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.domain.ObjectIdentityImpl
import org.springframework.security.acls.domain.PrincipalSid
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.MutableAcl
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.security.acls.model.Permission
import org.springframework.security.acls.model.Sid
import test.Report
import grails.testing.mixin.integration.Integration

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Integration
@Rollback
class GormAclLookupStrategySpec extends AbstractIntegrationSpec {

	private final Sid principalSid = new PrincipalSid('ben')
	private final ObjectIdentity topParentOid = new ObjectIdentityImpl(Report, 100L)
	private final ObjectIdentity middleParentOid = new ObjectIdentityImpl(Report, 101L)
	private final ObjectIdentity childOid = new ObjectIdentityImpl(Report, 102L)

	private AclClass aclClass
	private AclSid sid
	private AclObjectIdentity aclObjectIdentity

	GormAclLookupStrategy aclLookupStrategy
	CacheManager aclCacheManager

	void buildData() {

		aclLookupStrategy.batchSize = 50

		sid = new AclSid(sid: 'ben', principal: true).save(failOnError: true)

		aclClass = new AclClass(className: Report.name).save(failOnError: true)

		aclObjectIdentity = new AclObjectIdentity(
				aclClass: aclClass,
				objectId: 100L,
				owner: sid,
				entriesInheriting: true).save(failOnError: true)

		new AclEntry(
				aclObjectIdentity: aclObjectIdentity,
				sid: sid,
				mask: 1,
				granting: true).save(failOnError: true)

		new AclEntry(
				aclObjectIdentity: aclObjectIdentity,
				aceOrder: 1,
				sid: sid,
				mask: 2).save(failOnError: true)

		def aclObjectIdentity2 = new AclObjectIdentity(
				aclClass: aclClass,
				objectId: 101L,
				parent: aclObjectIdentity,
				owner: sid,
				entriesInheriting: true).save(failOnError: true)

		new AclEntry(
				aclObjectIdentity: aclObjectIdentity2,
				sid: sid,
				mask: 8,
				granting: true).save(failOnError: true)

		def aclObjectIdentity3 = new AclObjectIdentity(
				aclClass: aclClass,
				objectId: 102L,
				parent: aclObjectIdentity2,
				owner: sid,
				entriesInheriting: true).save(failOnError: true)

		new AclEntry(
				aclObjectIdentity: aclObjectIdentity3,
				sid: sid,
				mask: 8).save(failOnError: true)

		flushAndClear()
	}

	void cleanup() {
		aclCacheManager.getCache("aclCache").clear()
	}

	void 'acls retrieval with default batch size'() {
		given:
		buildData()

		when:
		// Deliberately use an integer for the child, to reproduce bug report in SEC-819
		ObjectIdentity childOid = new ObjectIdentityImpl(Report, 102L)
		Map<ObjectIdentity, Acl> map = aclLookupStrategy.readAclsById(
				[topParentOid, middleParentOid, childOid], null)

		then:
		checkEntries topParentOid, middleParentOid, childOid, map
	}

	void 'acls retrieval from cache-only'() {
		given:
		buildData()

		when:
		// Objects were put in cache
		aclLookupStrategy.readAclsById([topParentOid, middleParentOid, childOid], null)

		// Let's empty the database to force acls retrieval from cache
		AclEntry.list()*.delete()
		AclObjectIdentity.list()*.delete()
		AclClass.list()*.delete()
		AclSid.list()*.delete()
		flushAndClear()

		Map<ObjectIdentity, Acl> map = aclLookupStrategy.readAclsById(
				[topParentOid, middleParentOid, childOid], null)

		then:
		checkEntries topParentOid, middleParentOid, childOid, map
	}

	void 'acls retrieval with custom batch size'() {
		given:
		buildData()

		when:
		// Set a batch size to allow multiple database queries in order to retrieve all acls
		aclLookupStrategy.batchSize = 1
		Map<ObjectIdentity, Acl> map = aclLookupStrategy.readAclsById(
				[topParentOid, middleParentOid, childOid], null)

		then:
		checkEntries topParentOid, middleParentOid, childOid, map
	}

	void 'all parents are retrieved when child is loaded'() {
		given:
		buildData()

		when:
		new AclObjectIdentity(
				aclClass: aclClass,
				objectId: 103L,
				parent: aclObjectIdentity,
				owner: sid,
				entriesInheriting: true).save(failOnError: true)
		flushAndClear()

		ObjectIdentity middleParent2Oid = new ObjectIdentityImpl(Report, 103L)

		// Retrieve the child
		Map map = aclLookupStrategy.readAclsById([childOid], null)

		then:

		// Check that the child and all its parents were retrieved
		map[childOid]
		childOid == map[childOid].objectIdentity
		map[middleParentOid]
		middleParentOid == map[middleParentOid].objectIdentity
		map.get(topParentOid)
		topParentOid == map[topParentOid].objectIdentity

		// The second parent shouldn't have been retrieved
		!map[middleParent2Oid]
	}

	/**
	 * Test created from SEC-590.
	 */
	void 'read all ObjectIdentities when last element is already cached'() {
		given:
		buildData()

		when:
		def aclObjectIdentity4 = new AclObjectIdentity(
				aclClass: aclClass,
				objectId: 104L,
				owner: sid,
				entriesInheriting: true).save(failOnError: true)

		def aclObjectIdentity5 = new AclObjectIdentity(
				aclClass: aclClass,
				objectId: 105L,
				parent: aclObjectIdentity4,
				owner: sid,
				entriesInheriting: true).save(failOnError: true)

		new AclObjectIdentity(
				aclClass: aclClass,
				objectId: 106L,
				parent: aclObjectIdentity4,
				owner: sid,
				entriesInheriting: true).save(failOnError: true)

		new AclObjectIdentity(
				aclClass: aclClass,
				objectId: 107L,
				parent: aclObjectIdentity5,
				owner: sid,
				entriesInheriting: true).save(failOnError: true)

		new AclEntry(
				aclObjectIdentity: aclObjectIdentity4,
				sid: sid,
				mask: 1,
				granting: true).save(failOnError: true)

		flushAndClear()

		ObjectIdentity grandParentOid = new ObjectIdentityImpl(Report, 104L)
		ObjectIdentity parent1Oid = new ObjectIdentityImpl(Report, 105L)
		ObjectIdentity parent2Oid = new ObjectIdentityImpl(Report, 106L)
		ObjectIdentity childOid = new ObjectIdentityImpl(Report, 107L)

		// First lookup only child, thus populating the cache with grandParent, parent1 and child
		List<Permission> checkPermission = [BasePermission.READ]
		List<Sid> sids = [principalSid]
		List<ObjectIdentity> childOids = [childOid]

		aclLookupStrategy.batchSize = 6
		Map foundAcls = aclLookupStrategy.readAclsById(childOids, sids)

		Acl foundChildAcl = foundAcls[childOid]

		then:
		foundChildAcl
		foundChildAcl.isGranted(checkPermission, sids, false)

		when:
		// Search for object identities has to be done in the following order: last element have to be one which
		// is already in cache and the element before it must not be stored in cache
		List<ObjectIdentity> allOids = [grandParentOid, parent1Oid, parent2Oid, childOid]
		foundAcls = aclLookupStrategy.readAclsById(allOids, sids)

		Acl foundParent2Acl = foundAcls[parent2Oid]

		then:
		foundParent2Acl
		foundParent2Acl.isGranted(checkPermission, sids, false)
	}

	private boolean checkEntries(ObjectIdentity topParentOid, ObjectIdentity middleParentOid,
	                             ObjectIdentity childOid, Map<ObjectIdentity, Acl> map) {

		assert 3 == map.size()

		MutableAcl topParent = map[topParentOid]
		MutableAcl middleParent = map[middleParentOid]
		MutableAcl child = map[childOid]

		// Check the retrieved versions has IDs
		assert null != topParent.id
		assert null != middleParent.id
		assert null != child.id

		// Check their parents were correctly retrieved
		assert null == topParent.parentAcl
		assert topParentOid == middleParent.parentAcl.objectIdentity
		assert middleParentOid == child.parentAcl.objectIdentity

		// Check their ACEs were correctly retrieved
		assert 2 == topParent.entries.size()
		assert 1 == middleParent.entries.size()
		assert 1 == child.entries.size()

		// Check object identities were correctly retrieved
		assert topParentOid == topParent.objectIdentity
		assert middleParentOid == middleParent.objectIdentity
		assert childOid == child.objectIdentity

		// Check each entry
		assert topParent.isEntriesInheriting()
		assert topParent.owner == principalSid
		assert topParent.entries[0].permission == BasePermission.READ
		assert topParent.entries[0].sid == principalSid
		assert !topParent.entries[0].isAuditFailure()
		assert !topParent.entries[0].isAuditSuccess()
		topParent.entries[0].isGranting()

		assert topParent.entries[1].permission == BasePermission.WRITE
		assert topParent.entries[1].sid == principalSid
		assert !topParent.entries[1].isAuditFailure()
		assert !topParent.entries[1].isAuditSuccess()
		assert !topParent.entries[1].isGranting()

		assert middleParent.isEntriesInheriting()
		assert middleParent.owner == principalSid
		assert middleParent.entries[0].permission == BasePermission.DELETE
		assert middleParent.entries[0].sid == principalSid
		assert !middleParent.entries[0].isAuditFailure()
		assert !middleParent.entries[0].isAuditSuccess()
		assert middleParent.entries[0].isGranting()

		assert child.isEntriesInheriting()
		assert child.owner == principalSid
		assert child.entries[0].permission == BasePermission.DELETE
		assert child.entries[0].sid == principalSid
		assert !child.entries[0].isAuditFailure()
		assert !child.entries[0].isAuditSuccess()
		assert !child.entries[0].isGranting()

		true
	}
}
