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

import grails.plugin.springsecurity.acl.model.ObjectIdentityRetrievalStrategyAndGenerator
import groovy.util.logging.Slf4j
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.acls.domain.GrantedAuthoritySid
import org.springframework.security.acls.domain.PrincipalSid
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.MutableAcl
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.security.acls.model.Permission
import org.springframework.security.acls.model.Sid
import org.springframework.security.acls.model.SidRetrievalStrategy
import org.springframework.security.core.Authentication

/**
 * Utility service that hides a lot of the implementation details for working with ACLs.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Slf4j
class AclUtilService {

	/** Dependency injection for aclService. */
	def aclService

	/** Dependency injection for permissionEvaluator. */
	PermissionEvaluator permissionEvaluator

	/** Dependency injection for sidRetrievalStrategy. */
	SidRetrievalStrategy sidRetrievalStrategy

	/** Dependency injection for objectIdentityRetrievalStrategy. */
	ObjectIdentityRetrievalStrategyAndGenerator objectIdentityRetrievalStrategy

	/**
	 * Grant a permission. Used when you don't have the instance available.
	 *
	 * @param domainClass  the domain class
	 * @param id  the instance id
	 * @param recipient  the grantee; can be a username, role name, Sid, or Authentication
	 * @param permission  the permission to grant
	 */
	void addPermission(Class<?> domainClass, Serializable id, recipient, Permission permission) {
		ObjectIdentity oid = objectIdentityRetrievalStrategy.createObjectIdentity(id, domainClass.name)
		addPermission oid, recipient, permission
	}

	/**
	 * Grant a permission. Used when you have the instance available.
	 *
	 * @param domainObject  the domain class instance
	 * @param recipient  the grantee; can be a username, role name, Sid, or Authentication
	 * @param permission  the permission to grant
	 */
	void addPermission(domainObject, recipient, Permission permission) {
		ObjectIdentity oid = objectIdentityRetrievalStrategy.getObjectIdentity(domainObject)
		addPermission oid, recipient, permission
	}

	/**
	 * Grant a permission.
	 *
	 * @param oid  represents the domain object
	 * @param recipient  the grantee; can be a username, role name, Sid, or Authentication
	 * @param permission  the permission to grant
	 */
	void addPermission(ObjectIdentity oid, recipient, Permission permission) {

		Sid sid = createSid(recipient)

		MutableAcl acl
		try {
			acl = aclService.readAclById(oid)
		}
		catch (NotFoundException e) {
			acl = aclService.createAcl(oid)
		}

		acl.insertAce acl.entries.size(), permission, sid, true
		aclService.updateAcl acl

		log.debug "Added permission $permission for Sid $sid for $oid.type with id $oid.identifier"
	}

	/**
	 * Update the owner of the domain class instance.
	 *
	 * @param domainObject  the domain class instance
	 * @param newOwnerUsername  the new username
	 */
	void changeOwner(domainObject, String newUsername) {
		MutableAcl acl = readAcl(domainObject)
		acl.owner = new PrincipalSid(newUsername)
		aclService.updateAcl acl
	}

	/**
	 * Removes a granted permission. Used when you have the instance available.
	 *
	 * @param domainObject  the domain class instance
	 * @param recipient  the grantee; can be a username, role name, Sid, or Authentication
	 * @param permission  the permission to remove
	 */
	void deletePermission(domainObject, recipient, Permission permission) {
		deletePermission domainObject.getClass(), domainObject.id, recipient, permission
	}

	/**
	 * Removes a granted permission. Used when you don't have the instance available.
	 *
	 * @param domainClass  the domain class
	 * @param id  the instance id
	 * @param recipient  the grantee; can be a username, role name, Sid, or Authentication
	 * @param permission  the permission to remove
	 */
	void deletePermission(Class<?> domainClass, long id, recipient, Permission permission) {
		Sid sid = createSid(recipient)
		MutableAcl acl = readAcl(domainClass, id)

		acl.entries.eachWithIndex { entry, i ->
			if (entry.sid.equals(sid) && entry.permission.equals(permission)) {
				acl.deleteAce i
			}
		}

		aclService.updateAcl acl

		log.debug "Deleted ${domainClass.name}($id) ACL permissions for recipient $recipient"
	}

	/**
	 * Check if the authentication has grants for the specified permission(s) on the domain class instance.
	 *
	 * @param authentication  an authentication representing a user and roles
	 * @param domainObject  the domain class instance
	 * @param permissions  one or more permissions to check
	 * @return  <code>true</code> if granted
	 */
	boolean hasPermission(Authentication authentication, domainObject, Permission... permissions) {
		permissionEvaluator.hasPermission authentication, domainObject, permissions
	}

	/**
	 * Check if the authentication has grants for the specified permission(s) on the domain class instance.
	 *
	 * @param authentication  an authentication representing a user and roles
	 * @param domainObject  the domain class instance
	 * @param permissions  one or more permissions to check
	 * @return  <code>true</code> if granted
	 */
	boolean hasPermission(Authentication authentication, domainObject, List<Permission> permissions) {
		hasPermission authentication, domainObject, permissions as Permission[]
	}

	/**
	 * Helper method to retrieve the ACL for a domain class instance.
	 *
	 * @param domainObject  the domain class instance
	 * @return the {@link Acl} (never <code>null</code>)
	 */
	Acl readAcl(domainObject) {
		aclService.readAclById objectIdentityRetrievalStrategy.getObjectIdentity(domainObject)
	}

	/**
	 * Helper method to retrieve the ACL for a domain class instance.
	 *
	 * @param domainClass  the domain class
	 * @param id  the instance id
	 * @return the {@link Acl} (never <code>null</code>)
	 */
	Acl readAcl(Class<?> domainClass, id) {
		aclService.readAclById objectIdentityRetrievalStrategy.createObjectIdentity(id, domainClass.name)
	}

	/**
	 * Helper method to delete an ACL for a domain class.
	 *
	 * @param domainObject  the domain class instance
	 */
	void deleteAcl(domainObject) {
		aclService.deleteAcl objectIdentityRetrievalStrategy.getObjectIdentity(domainObject), false
	}

	protected Sid createSid(recipient) {
		if (recipient instanceof String) {
			return recipient.startsWith('ROLE_') ?
					new GrantedAuthoritySid(recipient) :
					new PrincipalSid(recipient)
		}

		if (recipient instanceof Sid) {
			return recipient
		}

		if (recipient instanceof Authentication) {
			return new PrincipalSid(recipient)
		}

		throw new IllegalArgumentException('recipient must be a String, Sid, or Authentication')
	}
}
