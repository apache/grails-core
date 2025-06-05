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

import org.springframework.dao.DataAccessException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

/**
 * Extension of the standard interface that allows specifying whether or not to load roles
 * from the database, e.g. for LDAP where role information is inferred from LDAP group membership.
 *
 * @author Burt Beckwith
 */
interface GrailsUserDetailsService extends UserDetailsService {

	/**
	 * Locates the user based on the username.
	 *
	 * @param username the username identifying the user whose data is required.
	 * @param loadRoles whether to load roles at the same time as loading the user
	 *
	 * @return a fully populated user record (never <code>null</code>)
	 *
	 * @throws UsernameNotFoundException if the user could not be found
	 * @throws DataAccessException if user could not be found for a repository-specific reason
	 */
	UserDetails loadUserByUsername(String username, boolean loadRoles) throws UsernameNotFoundException, DataAccessException
}
