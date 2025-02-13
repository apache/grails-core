/* Copyright 2011-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springsecurity.ldap.userdetails

import grails.plugin.springsecurity.userdetails.GrailsUserDetailsService
import groovy.transform.CompileStatic

import org.springframework.beans.factory.InitializingBean
import org.springframework.ldap.core.DirContextOperations
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator
import org.springframework.util.Assert

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class DatabaseOnlyLdapAuthoritiesPopulator implements LdapAuthoritiesPopulator, InitializingBean {

	protected GrantedAuthority defaultRole

	/** Dependency injection for the user details service. */
	GrailsUserDetailsService userDetailsService

	Collection<GrantedAuthority> getGrantedAuthorities(DirContextOperations userData, String username) {

		def roles = [] as Set<GrantedAuthority>
		if (defaultRole) {
			roles << defaultRole
		}

		UserDetails dbDetails
		try {
			dbDetails = userDetailsService.loadUserByUsername(username, true)
		}
		catch (UsernameNotFoundException ignored) {
			// just looking for roles, so ignore the UsernameNotFoundException
		}

		if (dbDetails?.authorities != null) {
			roles.addAll(dbDetails.authorities)
		}

		roles
	}

	/**
	 * The default role which will be assigned to all users.
	 *
	 * @param defaultRoleName the role name, including any desired prefix.
	 */
	void setDefaultRole(String defaultRoleName) {
		Assert.notNull defaultRoleName, 'The defaultRole property cannot be set to null'
		defaultRole = new SimpleGrantedAuthority(defaultRoleName)
	}

	void afterPropertiesSet() {
		Assert.notNull userDetailsService, 'userDetailsService must be specified'
	}
}
