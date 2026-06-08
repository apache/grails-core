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
package grails.plugin.springsecurity.ldap.userdetails

import grails.plugin.springsecurity.userdetails.GrailsUserDetailsService
import groovy.transform.CompileStatic

import org.springframework.beans.factory.InitializingBean
import org.springframework.ldap.core.ContextSource
import org.springframework.ldap.core.DirContextOperations
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator
import org.springframework.util.Assert

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class GrailsLdapAuthoritiesPopulator extends DefaultLdapAuthoritiesPopulator implements InitializingBean {

	/** Dependency injection for the user details service. */
	GrailsUserDetailsService userDetailsService

	/** Dependency injection for whether to retrieve roles from the database in addition to LDAP. */
	Boolean retrieveDatabaseRoles

	protected String roleStripPrefix
	protected String roleStripSuffix
	protected boolean roleConvertDashes = false
	protected boolean roleToUpperCase = false

	/**
	 * Constructor for group search scenarios. <tt>userRoleAttributes</tt> may still be
	 * set as a property.
	 *
	 * @param contextSource supplies the contexts used to search for user roles.
	 * @param groupSearchBase          if this is an empty string the search will be performed from the root DN of the
	 *                                 context factory.
	 */
	GrailsLdapAuthoritiesPopulator(ContextSource contextSource, String groupSearchBase) {
		super(contextSource, groupSearchBase)
	}

	/**
	 * This cleans a role based on configuration flags set.
	 * @param role the role to clean
	 * @return the cleaned role
	 */
	GrantedAuthority cleanRole(GrantedAuthority role) {
		if (!(role instanceof SimpleGrantedAuthority)) {
			return role
		}

		SimpleGrantedAuthority newRole = (SimpleGrantedAuthority) role

		if (roleConvertDashes && newRole.authority.contains('-')) {
			// replace dashes
			newRole = new SimpleGrantedAuthority(newRole.authority.replaceAll('-', '_'))
		}

		if (roleToUpperCase && newRole.authority.toUpperCase() != newRole.authority) {
			// convert to upper case
			newRole = new SimpleGrantedAuthority(newRole.authority.toUpperCase())
		}

		if (roleStripPrefix) {
			// strip prefix if found
			String tempPrefix = rolePrefix + roleStripPrefix
			if (tempPrefix && newRole.authority.startsWith(tempPrefix) && newRole.authority.length() > tempPrefix.length()) {
				// replace dashes
				newRole = new SimpleGrantedAuthority(newRole.authority.replace(tempPrefix, rolePrefix).trim())
			}
		}

		// strip suffix if found
		if (roleStripSuffix && newRole.authority.length() > roleStripSuffix.length() &&
				newRole.authority.endsWith(roleStripSuffix)) {
			int roleLength = newRole.authority.length()
			int suffixLength = roleStripSuffix.length()
			newRole = new SimpleGrantedAuthority(
					newRole.authority.substring(0, roleLength - suffixLength).trim())
		}

		if (newRole.authority.contains(' ')) {
			// replace spaces
			newRole = new SimpleGrantedAuthority(newRole.authority.replaceAll(' ', '_'))
		}

		while (newRole.authority.contains('__')) {
			// replace __
			newRole = new SimpleGrantedAuthority(newRole.authority.replaceAll('__', '_'))
		}

		return newRole
	}

	@Override
	Set<GrantedAuthority> getGroupMembershipRoles(String userDn, String username) {
		super.getGroupMembershipRoles(userDn, username).collect { cleanRole it } as Set
	}

	@Override
	protected Set<GrantedAuthority> getAdditionalRoles(DirContextOperations user, String username) {
		if (!retrieveDatabaseRoles) {
			return null
		}

		try {
			UserDetails dbDetails = userDetailsService.loadUserByUsername(username, true)
			if (dbDetails.authorities) {
				return new HashSet<GrantedAuthority>(dbDetails.authorities)
			}
		}
		catch (UsernameNotFoundException ignored) {
			// just looking for roles, so ignore the UsernameNotFoundException
		}
	}

	/**
	 * Hooks to adjust prefix string if other cleaning flags are set
	 */
	protected void updateRoleStripPrefix() {
		// convert dashes
		if (roleConvertDashes && roleStripPrefix?.contains('-')) {
			roleStripPrefix = roleStripPrefix.replaceAll('-', '_')
		}
		// To upper case
		if (roleToUpperCase && roleStripPrefix && roleStripPrefix.toUpperCase() != roleStripPrefix) {
			roleStripPrefix = roleStripPrefix.toUpperCase()
		}
	}

	/**
	 * Hooks to adjust suffix string if other cleaning flags are set
	 */
	protected void updateRoleStripSuffix() {
		// convert dashes
		if (roleConvertDashes && roleStripSuffix?.contains('-')) {
			roleStripSuffix = roleStripSuffix.replaceAll('-', '_')
		}
		// To upper case
		if (roleToUpperCase && roleStripSuffix && roleStripSuffix.toUpperCase() != roleStripSuffix) {
			roleStripSuffix = roleStripSuffix.toUpperCase()
		}
	}

	/**
	 * Dependency injection for whether or not to remove a prefix string from a LDAP
	 * group name if it matches the beginning of the group name, but not the full
	 * name of the group.
	 * @param prefix if not null, this is stripped from the group name before it is made into a role
	 */
	void setRoleStripPrefix(String prefix) {
		roleStripPrefix = prefix
		updateRoleStripPrefix()
	}

	/**
	 * Dependency injection for whether or not to remove a suffix string from a LDAP
	 * group name if it matches the end of the group name, but not the full
	 * name of the group.
	 * @param suffix if not null, this is stripped from the group name before it is made into a role
	 */
	void setRoleStripSuffix(String suffix) {
		roleStripSuffix = suffix
		updateRoleStripSuffix()
	}

	/**
	 * Dependency injection for whether or not to convert all dashes to underscores if found in a
	 * group name before it is made into a role.
	 * @param convertDashes if <code>true</code>, all dashes are converted to underscores
	 */
	void setRoleConvertDashes(boolean convertDashes) {
		roleConvertDashes = convertDashes
		updateRoleStripPrefix()
		updateRoleStripSuffix()
	}

	/**
	 * Dependency injection for whether or not to convert group names to uppercase before they
	 * are made into roles.
	 * @param toUpperCase if <code>true</code>, roles are converted to uppercase
	 */
	void setRoleToUpperCase(boolean toUpperCase) {
		roleToUpperCase = toUpperCase
		updateRoleStripPrefix()
		updateRoleStripSuffix()
	}

	void afterPropertiesSet() {
		Assert.notNull userDetailsService, 'userDetailsService must be specified'
		Assert.notNull retrieveDatabaseRoles, 'retrieveDatabaseRoles must be specified'
	}
}
