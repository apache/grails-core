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

import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.acls.domain.PermissionFactory
import org.springframework.security.acls.model.Permission

/**
 * ACL-related security tags.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class SecurityAclTagLib {

	static namespace = 'sec'

	/** Dependency injection for aclPermissionFactory. */
	PermissionFactory aclPermissionFactory

	/** Dependency injection for permissionEvaluator. */
	PermissionEvaluator permissionEvaluator

	/** Dependency injection for springSecurityService. */
	def springSecurityService

	/**
	 * Renders the body if the user has grants for the specified permissions. Permissions are
	 * specified in the 'permission' attribute and can be a single Permission, an array of
	 * Permission, an int/Integer (which is assumed to be a mask), a String (which can be a
	 * single name, or a comma-delimited list of names, or a comma-delimited list of masks),
	 * or a List of any of these.
	 */
	def permitted = { attrs, body ->
		if (hasPermission(attrs, 'permitted')) {
			out << body()
		}
	}

	/**
	 * Renders the body if the user has grants for none of the specified permissions.
	 */
	def notPermitted = { attrs, body ->
		if (!hasPermission(attrs, 'notPermitted')) {
			out << body()
		}
	}

	protected boolean hasPermission(attrs, String tagName) {

		if (!springSecurityService.isLoggedIn()) {
			return false
		}

		def auth = springSecurityService.authentication
		def perm = assertAttribute('permission', attrs, tagName)
		def permissions = resolvePermissions(perm)

		def object = attrs.remove('object')
		if (object) {
			return permissionEvaluator.hasPermission(auth, object, permissions)
		}

		def id = assertAttribute('id', attrs, tagName)
		String className = assertAttribute('className', attrs, tagName)
		if (!id || !className) {
			throwTagError "Tag [$tagName] requires either an object or a class name and id"
		}

		return permissionEvaluator.hasPermission(auth, id, className, permissions)
	}

	/**
	 * @param permissions  can be a single Permission, an array of Permission, an int/Integer (which
	 * is assumed to be a mask), a String (which can be a single name, or a comma-delimited
	 * list of names, or a comma-delimited list of masks), or a List of any of these
	 */
	protected resolvePermissions(permissions) {

		Set<Permission> resolvedPermissions = []

		if (permissions instanceof String) {
			splitStringIntoPermissions permissions, resolvedPermissions
		}
		else if (permissions instanceof List) {
			for (item in permissions) {
				if (item instanceof String) {
					splitStringIntoPermissions item, resolvedPermissions
				}
				else if (item instanceof Integer) {
					resolvedPermissions << aclPermissionFactory.buildFromMask(item)
				}
				else if (item instanceof Permission) {
					resolvedPermissions << item
				}
			}
		}
		else {
			// let the permissionEvaluator handle it
			return permissions
		}

		if (resolvedPermissions.any {it instanceof Permission}) {
			return resolvedPermissions as Permission[]
		}

		return permissions
	}

	protected void splitStringIntoPermissions(String permission, Set permissions) {
		for (token in permission.split(',')) {
			token = token.trim()
			if (token) {
				try {
					permissions << aclPermissionFactory.buildFromMask(Integer.valueOf(token))
				}
				catch (NumberFormatException nfe) {
					try {
						permissions << aclPermissionFactory.buildFromName(token)
					}
					catch (IllegalArgumentException notfound) {
						try {
							permissions << aclPermissionFactory.buildFromName(token.toUpperCase())
						}
						catch (IllegalArgumentException notfoundAgain) {
							permissions << token
						}
					}
				}
			}
		}
	}

	protected assertAttribute(String name, attrs, String tag) {
		if (!attrs.containsKey(name)) {
			throwTagError "Tag [$tag] is missing required attribute [$name]"
		}
		attrs.remove name
	}
}
