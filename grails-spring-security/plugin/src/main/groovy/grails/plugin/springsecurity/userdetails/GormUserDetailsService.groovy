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
package grails.plugin.springsecurity.userdetails

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException

import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

/**
 * Default implementation of <code>GrailsUserDetailsService</code> that uses
 * domain classes to load users and roles.
 *
 * @author Burt Beckwith
 */
@Slf4j
class GormUserDetailsService implements GrailsUserDetailsService {

	/**
	 * Some Spring Security classes (e.g. RoleHierarchyVoter) expect at least one role, so
	 * we give a user with no granted roles this one which gets past that restriction but
	 * doesn't grant anything.
	 */
	static final GrantedAuthority NO_ROLE = new SimpleGrantedAuthority(SpringSecurityUtils.NO_ROLE)

	/** Dependency injection for the application. */
	GrailsApplication grailsApplication

	@Transactional(readOnly=true, noRollbackFor=[IllegalArgumentException, UsernameNotFoundException])
	UserDetails loadUserByUsername(String username, boolean loadRoles) throws UsernameNotFoundException {

		def conf = SpringSecurityUtils.securityConfig
		String userClassName = conf.userLookup.userDomainClassName
		def dc = grailsApplication.getArtefact 'Domain', userClassName
		if (!dc) {
			throw new IllegalArgumentException("The specified user domain class '$userClassName' is not a domain class")
		}

		Class<?> User = dc.clazz

		def user = User.createCriteria().get {
			if(conf.userLookup.usernameIgnoreCase) {
				eq((conf.userLookup.usernamePropertyName), username, [ignoreCase: true])
			} else {
				eq((conf.userLookup.usernamePropertyName), username)
			}
		}

		if (!user) {
			log.warn 'User not found: {}', username
			throw new NoStackUsernameNotFoundException()
		}

		Collection<GrantedAuthority> authorities = loadAuthorities(user, username, loadRoles)
		createUserDetails user, authorities
	}

	UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		loadUserByUsername username, true
	}

	protected Collection<GrantedAuthority> loadAuthorities(user, String username, boolean loadRoles) {
		if (!loadRoles) {
			return []
		}

		def conf = SpringSecurityUtils.securityConfig

		String authoritiesPropertyName = conf.userLookup.authoritiesPropertyName
		String authorityPropertyName = conf.authority.nameField

		boolean useGroups = conf.useRoleGroups
		String authorityGroupPropertyName = conf.authority.groupAuthorityNameField

		Collection<?> userAuthorities = user."$authoritiesPropertyName"
		def authorities

		if (useGroups) {
			if (authorityGroupPropertyName) {
				authorities = userAuthorities.collect { it."$authorityGroupPropertyName" }.flatten().unique().collect { new SimpleGrantedAuthority(it."$authorityPropertyName") }
			}
			else {
				log.warn 'Attempted to use group authorities, but the authority name field for the group class has not been defined.'
			}
		}
		else {
			authorities = userAuthorities.collect { new SimpleGrantedAuthority(it."$authorityPropertyName") }
		}
		authorities ?: [NO_ROLE]
	}

	protected UserDetails createUserDetails(user, Collection<GrantedAuthority> authorities) {

		def conf = SpringSecurityUtils.securityConfig

		String usernamePropertyName = conf.userLookup.usernamePropertyName
		String passwordPropertyName = conf.userLookup.passwordPropertyName
		String enabledPropertyName = conf.userLookup.enabledPropertyName
		String accountExpiredPropertyName = conf.userLookup.accountExpiredPropertyName
		String accountLockedPropertyName = conf.userLookup.accountLockedPropertyName
		String passwordExpiredPropertyName = conf.userLookup.passwordExpiredPropertyName

		String username = user."$usernamePropertyName"
		String password = user."$passwordPropertyName"
		boolean enabled = enabledPropertyName ? user."$enabledPropertyName" : true
		boolean accountExpired = accountExpiredPropertyName ? user."$accountExpiredPropertyName" : false
		boolean accountLocked = accountLockedPropertyName ? user."$accountLockedPropertyName" : false
		boolean passwordExpired = passwordExpiredPropertyName ? user."$passwordExpiredPropertyName" : false

		new GrailsUser(username, password, enabled, !accountExpired, !passwordExpired,
				!accountLocked, authorities, user.id)
	}
}
