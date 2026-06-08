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
security {
	ldap {
		active = true
		search {
			searchSubtree = true
			base = '' // e.g. 'dc=example,dc=com', 'ou=users,dc=example,dc=com'
			filter = '(uid={0})' //, '(mailNickname={0})'
			derefLink = false
			timeLimit = 0 // unlimited
			attributesToReturn = null // all
		}
		authenticator {
			useBind = true
			attributesToReturn = null // all
			dnPatterns = null // e.g. ["cn={0},ou=people"]
			passwordAttributeName = 'userPassword' // when not using bind
		}
		mapper {
			convertToUpperCase = true
			passwordAttributeName = 'userPassword'
			userDetailsClass = null // can be 'person' or 'inetOrgPerson'
			roleAttributes = null
		}
		auth {
			hideUserNotFoundExceptions = true
			useAuthPassword = true
		}
		context {
			managerDn = 'cn=admin,dc=example,dc=com'
			managerPassword = 'secret'
			server = 'ldap://localhost:389' // 'ldap://ad.example.com', 'ldap://monkeymachine:389/dc=acegisecurity,dc=org'
			contextFactoryClassName = 'com.sun.jndi.ldap.LdapCtxFactory'
			dirObjectFactoryClassName = 'org.springframework.ldap.core.support.DefaultDirObjectFactory'
			baseEnvironmentProperties = [:]
			cacheEnvironmentProperties = true
			anonymousReadOnly = false
			referral = null
		}
		authorities {
			retrieveGroupRoles = true
			retrieveDatabaseRoles = false
			groupRoleAttribute = 'cn'
			groupSearchFilter = 'uniquemember={0}'
			searchSubtree = true
			groupSearchBase = 'ou=groups,dc=example,dc=com'
			ignorePartialResultException = false
			defaultRole = null
			prefix = 'ROLE_'
			clean {
				prefix = null
				suffix = null
				dashes = false
				uppercase = false
			}
		}
		useRememberMe = false
		rememberMe {
			detailsManager {
				passwordAttributeName = 'userPassword'
				groupSearchBase = 'ou=groups,dc=example,dc=com'
				groupRoleAttributeName = 'cn'
				attributesToRetrieve = null // all
				groupMemberAttributeName = 'uniquemember'
			}
			usernameMapper {
				userDnBase = null // must be set, e.g. 'dc=example,dc=com', 'ou=users,dc=example,dc=com'
				usernameAttribute = null // must be set, e.g. 'cn'
			}
		}
	}
}
