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

import grails.plugin.springsecurity.acl.AclEntry
import grails.plugin.springsecurity.acl.AclObjectIdentity
import grails.plugin.springsecurity.acl.AclSid
import grails.plugin.springsecurity.acl.model.StubAclParent
import org.springframework.security.acls.domain.AccessControlEntryImpl
import org.springframework.security.acls.domain.AclAuthorizationStrategy
import org.springframework.security.acls.domain.AclImpl
import org.springframework.security.acls.domain.GrantedAuthoritySid
import org.springframework.security.acls.domain.ObjectIdentityImpl
import org.springframework.security.acls.domain.PermissionFactory
import org.springframework.security.acls.domain.PrincipalSid
import org.springframework.security.acls.jdbc.LookupStrategy
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.AclCache
import org.springframework.security.acls.model.MutableAcl
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.security.acls.model.Permission
import org.springframework.security.acls.model.PermissionGrantingStrategy
import org.springframework.security.acls.model.Sid
import org.springframework.util.Assert
import org.springframework.util.ReflectionUtils

import java.lang.reflect.Field

/**
 * GORM implementation of {@link LookupStrategy}. Ported from <code>BasicLookupStrategy</code>.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class GormAclLookupStrategy implements LookupStrategy {

	protected Field aceAclField

	protected /*HibernateProxyHandler*/ hibernateProxyHandler

	/** Dependency injection for aclAuthorizationStrategy. */
	AclAuthorizationStrategy aclAuthorizationStrategy

	/** Dependency injection for aclCache. */
	AclCache aclCache

	/** Dependency injection for permissionFactory. */
	PermissionFactory permissionFactory

	/** Dependency injection for permissionGrantingStrategy. */
	PermissionGrantingStrategy permissionGrantingStrategy

	int batchSize = 50

	GormAclLookupStrategy() {
		findAceAclField()
		createHibernateProxyHandler()
	}

	Map<ObjectIdentity, Acl> readAclsById(List<ObjectIdentity> objects, List<Sid> sids) {
		Map<ObjectIdentity, Acl> result = [:]
		Set<ObjectIdentity> currentBatchToLoad = []

		for ( int i = 0; i < objects.size(); i++ ) {
			ObjectIdentity object = objects.get(i)
			// Check we don't already have this ACL in the results
			boolean aclFound = result.containsKey(object)

			// Check cache for the present ACL entry
			if (!aclFound) {
				Acl acl = aclCache.getFromCache(object)

				// Ensure any cached element supports all the requested SIDs
				// (they should always, as our base impl doesn't filter on SID)
				if (acl) {
					Assert.state(acl.isSidLoaded(sids),
						'Error: SID-filtered element detected when implementation does not perform SID filtering ' +
						'- have you added something to the cache manually?')

					result[acl.objectIdentity] = acl
					aclFound = true
				}
			}

			// Load the ACL from the database
			if (!aclFound) {
				currentBatchToLoad << object
			}

			// Is it time to load from JDBC the currentBatchToLoad?
			if (currentBatchToLoad.size() == batchSize || (i + 1) == objects.size()) {
				if (currentBatchToLoad) {
					Map<ObjectIdentity, Acl> loadedBatch = lookupObjectIdentities(currentBatchToLoad, sids)
					// Add loaded batch (all elements 100% initialized) to results
					result.putAll loadedBatch
					// Add the loaded batch to the cache
					loadedBatch.values().each { aclCache.putInCache it }
					currentBatchToLoad.clear()
				}
			}
		}

		result
	}

	protected Map<ObjectIdentity, Acl> lookupObjectIdentities(Collection<ObjectIdentity> objectIdentities, List<Sid> sids) {

		Assert.notEmpty objectIdentities, 'Must provide identities to lookup'

		Map<Serializable, Acl> acls = [:] // contains Acls with StubAclParents

		List<AclObjectIdentity> aclObjectIdentities = AclObjectIdentity.withCriteria {
			createAlias 'aclClass', 'ac'
			or {
				for (ObjectIdentity objectIdentity in objectIdentities) {
					and {
						eq 'objectId', objectIdentity.identifier
						eq 'ac.className', objectIdentity.type
					}
				}
			}
			order 'objectId', 'asc'
		}

		unwrapProxies aclObjectIdentities

		Map<AclObjectIdentity, List<AclEntry>> aclObjectIdentityMap = findAcls(aclObjectIdentities)

		List<AclObjectIdentity> parents = convertEntries(aclObjectIdentityMap, acls, sids)
		if (parents) {
			lookupParents acls, parents, sids
		}

		// Finally, convert our 'acls' containing StubAclParents into true Acls
		Map<ObjectIdentity, Acl> result = [:]
		for (Acl inputAcl in acls.values()) {
			Acl converted = convert(acls, inputAcl.id)
			result[converted.objectIdentity] = converted
		}

		result
	}

	protected void unwrapProxies(List<AclObjectIdentity> aclObjectIdentities) {
		if (hibernateProxyHandler) {
			for (ListIterator<AclObjectIdentity> iter = aclObjectIdentities.listIterator(); iter.hasNext(); ) {
				iter.set hibernateProxyHandler.unwrapIfProxy(iter.next())
			}
		}
	}

	protected Map<AclObjectIdentity, List<AclEntry>> findAcls(List<AclObjectIdentity> aclObjectIdentities) {

		List<AclEntry> entries
		if (aclObjectIdentities) {
			entries = AclEntry.withCriteria {
				'in'('aclObjectIdentity', aclObjectIdentities)
				order 'aceOrder', 'asc'
			}
		}

		def map = [:]
		for (AclObjectIdentity aclObjectIdentity in aclObjectIdentities) {
			map[aclObjectIdentity] = []
		}

		for (entry in entries) {
			map[entry.aclObjectIdentity] << entry
		}

		map
	}

	protected AclImpl convert(Map<Serializable, Acl> inputMap, Serializable currentIdentity) {
		Assert.notEmpty inputMap, 'InputMap required'
		Assert.notNull currentIdentity, 'CurrentIdentity required'

		// Retrieve this Acl from the InputMap
		Acl inputAcl = inputMap[currentIdentity]
		Assert.isInstanceOf AclImpl, inputAcl, 'The inputMap contained a non-AclImpl'

		Acl parent = inputAcl.parentAcl
		if (parent instanceof StubAclParent) {
			parent = convert(inputMap, parent.id)
		}

		// Now we have the parent (if there is one), create the true AclImpl
		AclImpl result = new AclImpl(inputAcl.objectIdentity, inputAcl.id,
				aclAuthorizationStrategy, permissionGrantingStrategy, parent, null /*List<Sid> loadedSids*/,
				inputAcl.isEntriesInheriting(), inputAcl.owner)

		List acesNew = []
		for (AccessControlEntryImpl ace in inputAcl.@aces) {
			ReflectionUtils.setField aceAclField, ace, result
			acesNew << ace
		}
		result.@aces.clear()
		result.@aces.addAll acesNew

		result
	}

	protected List<AclObjectIdentity> convertEntries(Map<AclObjectIdentity, List<AclEntry>> aclObjectIdentityMap,
			Map<Serializable, Acl> acls, List<Sid> sids) {

		List<AclObjectIdentity> parents = []

		aclObjectIdentityMap.each { aclObjectIdentity, aclEntries ->
			createAcl acls, aclObjectIdentity, aclEntries

			if (!aclObjectIdentity.parent) {
				return
			}

			Serializable parentId = aclObjectIdentity.parent.id
			if (acls.containsKey(parentId)) {
				return
			}

			// Now try to find it in the cache
			MutableAcl cached = aclCache.getFromCache(parentId)
			if (!cached || !cached.isSidLoaded(sids)) {
				parents << aclObjectIdentity.parent
			}
			else {
				// Pop into the acls map, so our convert method doesn't need to deal with an unsynchronized AclCache
				acls[cached.id] = cached
			}
		}

		parents
	}

	protected void createAcl(Map<Serializable, Acl> acls, AclObjectIdentity aclObjectIdentity,
			List<AclEntry> entries) {

		Serializable id = aclObjectIdentity.id

		// If we already have an ACL for this ID, just create the ACE
		AclImpl acl = acls[id]
		if (!acl) {
			// Make an AclImpl and pop it into the Map
			ObjectIdentity objectIdentity = new ObjectIdentityImpl(
					lookupClass(aclObjectIdentity.aclClass.className),
					aclObjectIdentity.objectId)
			Acl parentAcl
			if (aclObjectIdentity.parent) {
				parentAcl = new StubAclParent(aclObjectIdentity.parent.id)
			}

			AclSid ownerSid = aclObjectIdentity.owner
			Sid owner = ownerSid.principal ?
					new PrincipalSid(ownerSid.sid) :
					new GrantedAuthoritySid(ownerSid.sid)

			acl = new AclImpl(objectIdentity, id, aclAuthorizationStrategy, permissionGrantingStrategy,
					parentAcl, null /*List<Sid> loadedSids*/, aclObjectIdentity.entriesInheriting, owner)
			acls[id] = acl
		}

		List aces = acl.@aces
		for (AclEntry entry in entries) {
			// Add an extra ACE to the ACL (ORDER BY maintains the ACE list order)
			// It is permissable to have no ACEs in an ACL
			String aceSid = entry.sid?.sid
			if (aceSid) {
				Sid recipient = entry.sid.principal ? new PrincipalSid(aceSid) : new GrantedAuthoritySid(aceSid)

				Permission permission = permissionFactory.buildFromMask(entry.mask)
				AccessControlEntryImpl ace = new AccessControlEntryImpl(entry.id, acl, recipient, permission,
						entry.granting, entry.auditSuccess, entry.auditFailure)

				// Add the ACE if it doesn't already exist in the ACL.aces field
				if (!aces.contains(ace)) {
					aces << ace
				}
			}
		}
	}

	protected Class<?> lookupClass(String className) {
		// workaround for Class.forName() not working in tests
		Class.forName className, true, Thread.currentThread().contextClassLoader
	}

	protected void lookupParents(Map<Serializable, Acl> acls, Collection<AclObjectIdentity> findNow,
			List<Sid> sids) {

		Assert.notNull acls, 'ACLs are required'
		Assert.notEmpty findNow, 'Items to find now required'

		Map<AclObjectIdentity, List<AclEntry>> aclObjectIdentityMap = findAcls(findNow as List)
		List<AclObjectIdentity> parents = convertEntries(aclObjectIdentityMap, acls, sids)
		if (parents) {
			lookupParents acls, parents, sids
		}
	}

	protected void findAceAclField() {
		aceAclField = ReflectionUtils.findField(AccessControlEntryImpl, 'acl')
		aceAclField.accessible = true
	}

	protected void createHibernateProxyHandler() {
		try {
			Class<?> c = lookupClass('org.grails.orm.hibernate.proxy.HibernateProxyHandler')
			hibernateProxyHandler = c.newInstance()
		}
		catch (ignored) {}
	}
}
