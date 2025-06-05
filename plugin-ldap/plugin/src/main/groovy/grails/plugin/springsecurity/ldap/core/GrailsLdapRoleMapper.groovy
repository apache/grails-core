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
package grails.plugin.springsecurity.ldap.core

import groovy.transform.CompileStatic

import javax.naming.NamingException
import javax.naming.directory.Attributes

import org.springframework.ldap.core.AttributesMapper
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Based on the private implementation in <code>LdapUserDetailsManager</code> to allow overriding.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class GrailsLdapRoleMapper implements AttributesMapper {

	/**
	 * Dependency injection for <code>groupRoleAttributeName</code>.
	 */
	String groupRoleAttributeName

	/**
	 * Dependency injection for <code>rolePrefix</code>.
	 */
	String rolePrefix = 'ROLE_'

	def mapFromAttributes(Attributes attributes) throws NamingException {
		new SimpleGrantedAuthority(rolePrefix + attributes.get(groupRoleAttributeName).all.next().toString().toUpperCase())
	}
}
