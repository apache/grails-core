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
package grails.plugin.springsecurity.acl

import grails.gorm.transactions.Rollback
import org.springframework.cache.CacheManager
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.domain.CumulativePermission
import org.springframework.security.acls.domain.GrantedAuthoritySid
import org.springframework.security.acls.domain.ObjectIdentityImpl
import org.springframework.security.acls.domain.PrincipalSid
import org.springframework.security.acls.model.AccessControlEntry
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.AclCache
import org.springframework.security.acls.model.AlreadyExistsException
import org.springframework.security.acls.model.MutableAcl
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.security.acls.model.Permission
import org.springframework.security.acls.model.Sid
import test.Report
import grails.testing.mixin.integration.Integration


/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Rollback
@Integration
class AclServiceSpec extends AbstractIntegrationSpec {

	private final ObjectIdentity topParentOid =    new ObjectIdentityImpl(Report, 100L)
	private final ObjectIdentity middleParentOid = new ObjectIdentityImpl(Report, 101L)
	private final ObjectIdentity childOid =        new ObjectIdentityImpl(Report, 102L)

	private final List<Permission> read =           [BasePermission.READ]
	private final List<Permission> write =          [BasePermission.WRITE]
	private final List<Permission> delete =         [BasePermission.DELETE]
	private final List<Permission> administration = [BasePermission.ADMINISTRATION]
	private final List<Permission> create =         [BasePermission.CREATE]

	private PrincipalSid principalSid

	AclCache aclCache
	AclService aclService
	CacheManager aclCacheManager

	void setup() {
		principalSid = new PrincipalSid(authenticate('ben', 'ROLE_ADMIN'))
	}

	void cleanup() {
		aclCacheManager.getCache("aclCache").clear()
	}

	void 'test lifecycle'() {

		given:
		MutableAcl topParent = aclService.createAcl(topParentOid)
		MutableAcl middleParent = aclService.createAcl(middleParentOid)
		MutableAcl child = aclService.createAcl(childOid)

		// Specify the inheritance hierarchy
		middleParent.parent = topParent
		child.parent = middleParent

		// Now let's add a couple of permissions
		topParent.insertAce 0, BasePermission.READ, principalSid, true
		topParent.insertAce 1, BasePermission.WRITE, principalSid, false
		middleParent.insertAce 0, BasePermission.DELETE, principalSid, true
		child.insertAce 0, BasePermission.DELETE, principalSid, false

		// Explicitly save the changed ACL
		aclService.updateAcl topParent
		aclService.updateAcl middleParent
		aclService.updateAcl child

		when:
		// Let's check if we can read them back correctly
		Map<ObjectIdentity, Acl> map = aclService.readAclsById([topParentOid, middleParentOid, childOid])

		then:
		3 == map.size()

		when:
		// Replace our current objects with their retrieved versions
		topParent = map[topParentOid]
		middleParent = map[middleParentOid]
		child = map[childOid]

		then:
		// Check the retrieved versions has IDs
		topParent.id
		middleParent.id
		child.id

		// Check their parents were correctly persisted
		!topParent.parentAcl
		topParentOid == middleParent.parentAcl.objectIdentity
		middleParentOid == child.parentAcl.objectIdentity

		// Check their ACEs were correctly persisted
		2 == topParent.entries.size()
		1 == middleParent.entries.size()
		1 == child.entries.size()

		when:
		// Check the retrieved rights are correct
		List<Sid> pSid = [principalSid]

		then:
		topParent.isGranted read, pSid, false
		!topParent.isGranted(write, pSid, false)
		middleParent.isGranted delete, pSid, false
		!child.isGranted(delete, pSid, false)

		when:
		child.isGranted administration, pSid, false

		then:
		thrown NotFoundException

		// Now check the inherited rights (when not explicitly overridden) also look OK
		child.isGranted read, pSid, false
		!child.isGranted(write, pSid, false)
		!child.isGranted(delete, pSid, false)

		when:
		// Next change the child so it doesn't inherit permissions from above
		child.entriesInheriting = false
		aclService.updateAcl child
		child = aclService.readAclById(childOid)

		then:
		!child.isEntriesInheriting()

		// Check the child permissions no longer inherit
		!child.isGranted(delete, pSid, true)

		when:
		child.isGranted(read, pSid, true)

		then:
		thrown NotFoundException

		when:
		child.isGranted(write, pSid, true)

		then:
		thrown NotFoundException

		when:
		// Let's add an identical permission to the child, but it'll appear AFTER the current permission, so has no impact
		child.insertAce 1, BasePermission.DELETE, principalSid, true

		// Let's also add another permission to the child
		child.insertAce 2, BasePermission.CREATE, principalSid, true

		// Save the changed child
		aclService.updateAcl child
		child = aclService.readAclById(childOid)

		then:
		3 == child.entries.size()

		// Check the permissions are as they should be
		!child.isGranted(delete, pSid, true) // as earlier permission overrode
		child.isGranted create, pSid, true

		when:
		// Now check the first ACE (index 0) really is DELETE for our Sid and is non-granting
		AccessControlEntry entry = child.entries[0]

		then:
		BasePermission.DELETE.mask == entry.permission.mask
		principalSid == entry.sid
		!entry.isGranting()
		entry.id

		when:
		// Now delete that first ACE
		child.deleteAce 0

		// Save and check it worked
		child = aclService.updateAcl(child)

		then:
		2 == child.entries.size()
		child.isGranted delete, pSid, false
	}

	/**
	 * Demonstrates eviction failure from cache - SEC-676.
	 */
	void 'delete Acl also deletes children'() {

		given:
		aclService.createAcl topParentOid
		MutableAcl middleParent = aclService.createAcl(middleParentOid)
		MutableAcl child = aclService.createAcl(childOid)
		child.parent = middleParent
		aclService.updateAcl middleParent
		aclService.updateAcl child

		when:
		// Check the childOid really is a child of middleParentOid
		Acl childAcl = aclService.readAclById(childOid)

		then:
		middleParentOid == childAcl.parentAcl.objectIdentity

		when:
		// Delete the mid-parent and test if the child was deleted, as well
		aclService.deleteAcl middleParentOid, true
		aclService.readAclById(middleParentOid)

		then:
		thrown NotFoundException

		when:
		aclService.readAclById(childOid)

		then:
		thrown NotFoundException

		when:
		Acl acl = aclService.readAclById(topParentOid)

		then:
		acl
		acl.objectIdentity == topParentOid
	}

	void 'create Acl rejects null parameter'() {
		when:
		aclService.createAcl null

		then:
		thrown IllegalArgumentException
	}

	void 'create Acl for a duplicate domain object'() {
		given:
		ObjectIdentity duplicateOid = new ObjectIdentityImpl(Report, 100L)
		aclService.createAcl duplicateOid

		when:
		// Try to add the same object second time
		aclService.createAcl(duplicateOid)

		then:
		thrown AlreadyExistsException
	}

	void 'delete Acl rejects null parameters'() {
		when:
		aclService.deleteAcl null, true

		then:
		thrown IllegalArgumentException
	}

	void 'delete Acl removes rows from database'() {

		given:
		MutableAcl child = aclService.createAcl(childOid)
		child.insertAce 0, BasePermission.DELETE, principalSid, false
		aclService.updateAcl child

		when:
		// Remove the child and check all related database rows were removed accordingly
		aclService.deleteAcl childOid, false
		Report.withSession { it.flush(); it.clear(); }

		then:
		1 == AclClass.countByClassName(Report.name)
		0 == AclObjectIdentity.count()
		0 == AclEntry.count()

		// Check the cache
		!aclCache.getFromCache(childOid)
		!aclCache.getFromCache(102L)
	}

	/**
	 * SEC-655
	 */
	void 'children are cleared from cache when parent is updated'() {

		given:
		ObjectIdentity parentOid = new ObjectIdentityImpl(Report, 104L)
		ObjectIdentity childOid = new ObjectIdentityImpl(Report, 105L)

		MutableAcl parent = aclService.createAcl(parentOid)
		MutableAcl child = aclService.createAcl(childOid)

		child.parent = parent
		aclService.updateAcl child

		parent = aclService.readAclById(parentOid)
		parent.insertAce 0, BasePermission.READ, new PrincipalSid('ben'), true
		aclService.updateAcl parent

		parent = aclService.readAclById(parentOid)
		parent.insertAce 1, BasePermission.READ, new PrincipalSid('scott'), true
		aclService.updateAcl parent

		when:
		child = aclService.readAclById(childOid)
		parent = child.parentAcl

		then:
		assert 2 == parent.entries.size() : 'Fails because child has a stale reference to its parent'
		1 == parent.entries[0].permission.mask
		new PrincipalSid('ben') == parent.entries[0].sid
		1 == parent.entries[1].permission.mask
		new PrincipalSid('scott') == parent.entries[1].sid
	}

	/**
	 * SEC-655
	 */
	void 'children are cleared from cache when parent is updated2'() {

		given:
		ObjectIdentityImpl rootObject = new ObjectIdentityImpl(Report, 1L)

		MutableAcl parent = aclService.createAcl(rootObject)
		MutableAcl child = aclService.createAcl(new ObjectIdentityImpl(Report, 2L))
		child.parent = parent
		aclService.updateAcl child

		parent.insertAce 0, BasePermission.ADMINISTRATION,
				new GrantedAuthoritySid('ROLE_ADMINISTRATOR'), true
		aclService.updateAcl parent

		parent.insertAce 1, BasePermission.DELETE, new PrincipalSid('terry'), true
		aclService.updateAcl parent

		when:
		child = aclService.readAclById(new ObjectIdentityImpl(Report, 2L))
		parent = child.parentAcl

		then:
		2 == parent.entries.size()
		16 == parent.entries[0].permission.mask
		new GrantedAuthoritySid('ROLE_ADMINISTRATOR') == parent.entries[0].sid
		8 == parent.entries[1].permission.mask
		new PrincipalSid('terry') == parent.entries[1].sid
	}

	void 'cumulative permissions'() {

		when:
		ObjectIdentity topParentOid = new ObjectIdentityImpl(Report, 110L)
		MutableAcl topParent = aclService.createAcl(topParentOid)

		// Add an ACE permission entry
		Permission cm = new CumulativePermission()
				.set(BasePermission.READ)
				.set(BasePermission.ADMINISTRATION)

		then:
		17 == cm.mask

		when:
		Sid benSid = principalSid
		topParent.insertAce 0, cm, benSid, true

		then:
		1 == topParent.entries.size()

		when:
		// Explicitly save the changed ACL
		topParent = aclService.updateAcl(topParent)

		then:
		// Check the mask was retrieved correctly
		17 == topParent.entries[0].permission.mask
		topParent.isGranted([cm], [benSid], true)
	}
}
