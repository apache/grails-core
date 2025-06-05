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

import org.springframework.ldap.core.ContextSource
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.ldap.userdetails.LdapUserDetailsManager

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class GrailsLdapUserDetailsManager extends LdapUserDetailsManager implements GrailsUserDetailsService {

	GrailsLdapUserDetailsManager(ContextSource contextSource) {
		super(contextSource)
	}

	UserDetails loadUserByUsername(String username, boolean loadRoles) throws UsernameNotFoundException {
		super.loadUserByUsername username
	}
}
