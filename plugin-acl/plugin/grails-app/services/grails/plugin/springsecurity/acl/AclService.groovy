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

import grails.gorm.transactions.ReadOnly
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.context.MessageSource
import org.springframework.security.acls.domain.AccessControlEntryImpl
import org.springframework.security.acls.domain.GrantedAuthoritySid
import org.springframework.security.acls.domain.ObjectIdentityImpl
import org.springframework.security.acls.domain.PrincipalSid
import org.springframework.security.acls.jdbc.LookupStrategy
import org.springframework.security.acls.model.AccessControlEntry
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.AclCache
import org.springframework.security.acls.model.AlreadyExistsException
import org.springframework.security.acls.model.AuditableAccessControlEntry
import org.springframework.security.acls.model.ChildrenExistException
import org.springframework.security.acls.model.MutableAcl
import org.springframework.security.acls.model.MutableAclService
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.security.acls.model.Sid
import org.springframework.security.core.context.SecurityContextHolder
import grails.gorm.transactions.Transactional
import org.springframework.util.Assert

/**
 * GORM implementation of {@link org.springframework.security.acls.model.AclService} and {@link MutableAclService}.
 * Ported from <code>JdbcAclService</code> and <code>JdbcMutableAclService</code>.
 *
 * Individual methods are @Transactional since NotFoundException
 * is a runtime exception and will cause an unwanted transaction rollback
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
@Slf4j
class AclService implements MutableAclService, WarnErros {

	AclSidGormService aclSidGormService
	AclEntryGormService aclEntryGormService
	AclClassGormService aclClassGormService
	AclObjectIdentityGormService aclObjectIdentityGormService

	/** Dependency injection for aclLookupStrategy. */
	LookupStrategy aclLookupStrategy

	/** Dependency injection for aclCache. */
	AclCache aclCache

	/** Dependency injection for messageSource. */
	MessageSource messageSource

	@Transactional
	MutableAcl createAcl(ObjectIdentity objectIdentity) throws AlreadyExistsException {
		Assert.notNull objectIdentity, 'Object Identity required'

		// Check this object identity hasn't already been persisted
		if ( aclObjectIdentityGormService.findByObjectIdentity(objectIdentity) ) {
			throw new AlreadyExistsException("Object identity '$objectIdentity' already exists")
		}

		// Need to retrieve the current principal, in order to know who "owns" this ACL (can be changed later on)
		PrincipalSid sid = new PrincipalSid(SecurityContextHolder.context.authentication)

		// Create the acl_object_identity row
		createObjectIdentity objectIdentity, sid

		readAclById(objectIdentity) as MutableAcl
	}

	@Transactional
	protected AclObjectIdentity createObjectIdentity(ObjectIdentity object, Sid owner) {
		AclSid ownerSid = createOrRetrieveSid(owner, true)
		AclClass aclClass = createOrRetrieveClass(object.type, true)
		AclObjectIdentity aclObjectIdentity = new AclObjectIdentity(
				aclClass: aclClass,
				objectId: object.identifier as Long,
				owner: ownerSid,
				entriesInheriting: true)
		if ( !aclObjectIdentity.save() ) {
			log.error '{}', errorsBeanBeingSaved(messageSource, aclObjectIdentity)
		}
		aclObjectIdentity
	}

	@Transactional
	protected AclSid createOrRetrieveSid(Sid sid, boolean allowCreate) {
		Assert.notNull sid, 'Sid required'

		String sidName
		boolean principal
		if (sid instanceof PrincipalSid) {
			sidName = sid.principal
			principal = true
		}
		else if (sid instanceof GrantedAuthoritySid) {
			sidName = sid.grantedAuthority
			principal = false
		}
		else {
			throw new IllegalArgumentException('Unsupported implementation of Sid')
		}

		AclSid aclSid = aclSidGormService.findBySidAndPrincipal(sidName, principal)
		if (!aclSid && allowCreate) {
			aclSid = new AclSid(sid: sidName, principal: principal)
			if ( !aclSid.save() ) {
				log.error '{}', errorsBeanBeingSaved(messageSource, aclSid)
			}
		}
		aclSid
	}

	@Transactional
	protected AclClass createOrRetrieveClass(String className, boolean allowCreate) {
		AclClass aclClass = aclClassGormService.findByClassName(className)
		if (!aclClass && allowCreate) {
			aclClass = new AclClass(className: className)
			if ( !aclClass.save() ) {
				log.error '{}', errorsBeanBeingSaved(messageSource, aclClass)
			}
		}
		aclClass
	}

	@CompileDynamic
	@Transactional
	void deleteAcl(ObjectIdentity objectIdentity, boolean deleteChildren) throws ChildrenExistException {

		Assert.notNull objectIdentity, 'Object Identity required'
		Assert.notNull objectIdentity.identifier, "Object Identity doesn't provide an identifier"

		if ( deleteChildren ) {
			List<ObjectIdentity> children = findChildren(objectIdentity)
			if ( children !=  null ) {
				for ( ObjectIdentity child : children ) {
					deleteAcl(child, true)
				}
			}
		}

		AclObjectIdentity oid = aclObjectIdentityGormService.findByObjectIdentity(objectIdentity)
		if ( oid ) {
			// Delete this ACL's ACEs in the acl_entry table
			deleteEntries oid

			// Delete this ACL's acl_object_identity row
			oid.delete(failOnError:true)

			AclEntry.withSession { it.flush() }
		}

		// Clear the cache
		aclCache.evictFromCache objectIdentity
	}

	@Transactional
	protected void deleteEntries(AclObjectIdentity oid) {
		if ( oid ) {
			List<Serializable> aclEntryIdList = aclEntryGormService.findAllIdByAclObjectIdentity(oid)
			List<AclEntry> entries = aclEntryIdList.collect { Serializable id -> AclEntry.load(id) }
			deleteEntries(entries)
		}
	}

	@CompileDynamic
	@Transactional
	protected void deleteEntries(List<AclEntry> entries) {
		log.debug 'Deleting entries: {}', entries
		if ( entries ) {
			for ( AclEntry entry : entries ) {

				entry.delete(failOnError:true)

			}
			AclEntry.withSession { it.flush() }
		}
	}

	@Transactional
	MutableAcl updateAcl(MutableAcl acl) throws NotFoundException {
		Assert.notNull acl.id, "Object Identity doesn't provide an identifier"

		AclObjectIdentity aclObjectIdentity = aclObjectIdentityGormService.findByObjectIdentity(acl.objectIdentity)

		List<AclEntry> existingAces = aclEntryGormService.findAllByAclObjectIdentity(aclObjectIdentity)

		List<AclEntry> toDelete = existingAces.findAll { AclEntry ace ->
			log.trace 'Checking ace for delete: {}', ace
			!acl.entries.find { AccessControlEntry entry ->
				log.trace 'Checking entry for delete: {}', entry
				entry.permission.mask == ace.mask && entry.sid == ace.sid.sid
			}
		}

		List<AccessControlEntry> toCreate = acl.entries.findAll { AccessControlEntry entry ->
			log.trace 'Checking entry for create: {}', entry
			!existingAces.find { AclEntry ace ->
				log.trace 'Checking ace for create: {}', ace
				entry.permission.mask == ace.mask && entry.sid == ace.sid.sid
			}
		}

		// Delete this ACL's ACEs in the acl_entry table
		deleteEntries toDelete

		// Create this ACL's ACEs in the acl_entry table
		createEntries(acl, toCreate as List<AuditableAccessControlEntry>)

		// Change the mutable columns in acl_object_identity
		updateObjectIdentity acl

		// Clear the cache, including children
		clearCacheIncludingChildren acl.objectIdentity

		readAclById(acl.objectIdentity) as MutableAcl
	}

	@Transactional
	protected void createEntries(MutableAcl acl, List<AuditableAccessControlEntry> entries = null) {
		List<AuditableAccessControlEntry> entryList = entries ?: acl.entries as List<AuditableAccessControlEntry>
		int i = 0
		for (AuditableAccessControlEntry entry in entryList) {
			Assert.isInstanceOf AccessControlEntryImpl, entry, 'Unknown ACE class'
			AclEntry aclEntryInstance = new AclEntry(
					aclObjectIdentity: AclObjectIdentity.load(acl.id),
					aceOrder: i++,
					sid: createOrRetrieveSid(entry.sid, true),
					mask: entry.permission.mask,
					granting: entry.isGranting(),
					auditSuccess: entry.isAuditSuccess(),
					auditFailure: entry.isAuditFailure())
			if ( !aclEntryInstance.save() ) {
				log.error "{}", errorsBeanBeingSaved(messageSource, aclEntryInstance)
			}
		}
	}

	@CompileDynamic
	@Transactional
	protected void updateObjectIdentity(MutableAcl acl) {
		Assert.notNull acl.owner, "Owner is required in this implementation"

		AclObjectIdentity aclObjectIdentity = aclObjectIdentityGormService.findById(acl.id)

		AclObjectIdentity parent = null
		if (acl.parentAcl) {
			ObjectIdentity oii = acl.parentAcl.objectIdentity
			Assert.isInstanceOf ObjectIdentityImpl, oii, 'Implementation only supports ObjectIdentityImpl'
			parent = aclObjectIdentityGormService.findByObjectIdentity(oii)
		}
		aclObjectIdentity.parent = parent
		aclObjectIdentity.owner = createOrRetrieveSid(acl.owner, true)
		aclObjectIdentity.entriesInheriting = acl.isEntriesInheriting()
		if ( !aclObjectIdentity.save() ) {
			log.error "{}", errorsBeanBeingSaved(messageSource, aclObjectIdentity)
		}
		AclObjectIdentity.withSession { it.flush() }
	}

	protected void clearCacheIncludingChildren(ObjectIdentity objectIdentity) {
		Assert.notNull objectIdentity, 'ObjectIdentity required'

		List<ObjectIdentity> children = findChildren(objectIdentity)
		for (ObjectIdentity child in children) {
			clearCacheIncludingChildren child
		}
		aclCache.evictFromCache objectIdentity
	}

	@ReadOnly
	List<ObjectIdentity> findChildren(ObjectIdentity parentOid) {
		List<AclObjectIdentity> children = aclObjectIdentityGormService.findAllByParentObjectIdAndParentAclClassName((parentOid?.identifier as Long), parentOid.type)

		if ( !children ) {
			return []
		}

		children.collect { AclObjectIdentity aoi ->
			new ObjectIdentityImpl(lookupClass(aoi.aclClass.className), aoi.objectId)
		}
	}

	protected Class<?> lookupClass(String className) {
		// workaround for Class.forName() not working in tests
		Class.forName className, true, Thread.currentThread().contextClassLoader
	}

	@ReadOnly(noRollbackFor = [NotFoundException])
	Acl readAclById(ObjectIdentity object) throws NotFoundException {
		readAclById object, null
	}

	@ReadOnly(noRollbackFor = [NotFoundException])
	Acl readAclById(ObjectIdentity object, List<Sid> sids) throws NotFoundException {
		Map<ObjectIdentity, Acl> map = readAclsById([object], sids)
		Assert.isTrue map.containsKey(object),
				"There should have been an Acl entry for ObjectIdentity $object"
		map[object]
	}

	@ReadOnly(noRollbackFor = [NotFoundException])
	Map<ObjectIdentity, Acl> readAclsById(List<ObjectIdentity> objects) throws NotFoundException {
		readAclsById objects, null
	}

	@ReadOnly(noRollbackFor = [NotFoundException])
	Map<ObjectIdentity, Acl> readAclsById(List<ObjectIdentity> objects, List<Sid> sids) throws NotFoundException {
		Map<ObjectIdentity, Acl> result = aclLookupStrategy.readAclsById(objects, sids)
		// Check every requested object identity was found (throw NotFoundException if needed)
		for (ObjectIdentity object in objects) {
			if (!result.containsKey(object)) {
				throw new NotFoundException("Unable to find ACL information for object identity '$object'")
			}
		}
		return result
	}
}
