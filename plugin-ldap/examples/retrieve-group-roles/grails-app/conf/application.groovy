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

grails {
	plugin {
		springsecurity {
			authority {
				className = 'com.test.Role'
			}
			controllerAnnotations.staticRules = [
				[pattern: '/',               access: 'permitAll'],
				[pattern: '/logoff',         access: 'permitAll'],
				[pattern: '/error',          access: 'permitAll'],
				[pattern: '/index',          access: 'permitAll'],
				[pattern: '/index.gsp',      access: 'permitAll'],
				[pattern: '/shutdown',       access: 'permitAll'],
				[pattern: '/assets/**',      access: 'permitAll'],
				[pattern: '/secure/users',   access: 'permitAll'],
				[pattern: '/**/js/**',       access: 'permitAll'],
				[pattern: '/**/css/**',      access: 'permitAll'],
				[pattern: '/**/images/**',   access: 'permitAll'],
				[pattern: '/**/favicon.ico', access: 'permitAll']
			]
			password.algorithm = 'SHA-256'
			rememberMe {
				persistent = true
				persistentToken.domainClassName = 'com.test.PersistentLogin'
			}
			userLookup {
				userDomainClassName = 'com.test.User'
				authorityJoinClassName = 'com.test.UserRole'
			}

			// http://www.forumsys.com/tutorials/integration-how-to/ldap/online-ldap-test-server/
			ldap {
				context {
					managerDn = 'cn=read-only-admin,dc=example,dc=com'
					managerPassword = 'password'
					server = 'ldap://ldap.forumsys.com:389/' //'ldap://[ip]:[port]/'
				}
				authorities {
					ignorePartialResultException = true
					retrieveGroupRoles = true
					groupSearchBase='ou=mathematicians,dc=example,dc=com'
					retrieveDatabaseRoles = true
					defaultRole = 'ROLE_USER'
				}
				search {
					base = 'dc=example,dc=com'
				}
			}
		}
	}
}