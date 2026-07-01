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

import org.springframework.security.authentication.AccountExpiredException
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException

grails {
	plugin {
		springsecurity {
			userLookup {
				userDomainClassName = 'test.TestUser'
				usernamePropertyName = 'loginName'
				enabledPropertyName = 'enabld'
				passwordPropertyName = 'passwrrd'
				authoritiesPropertyName = 'roles'
				authorityJoinClassName = 'test.TestUserRole'
			}

			requestMap {
				className = 'test.TestRequestmap'
				urlField = 'urlPattern'
				configAttributeField = 'rolePattern'
				httpMethodField = 'httpMethod'
			}

			authority {
				className = 'test.TestRole'
				nameField = 'auth'
			}

			rememberMe {
				persistent = true
				persistentToken {
					domainClassName = 'test.TestPersistentLogin'
				}
			}

			failureHandler {
				exceptionMappings = [
					[exception: LockedException.name,             url: '/testUser/accountLocked'],
					[exception: DisabledException.name,           url: '/testUser/accountDisabled'],
					[exception: AccountExpiredException.name,     url: '/testUser/accountExpired'],
					[exception: CredentialsExpiredException.name, url: '/testUser/passwordExpired']
				]
			}
			interceptUrlMap = [
				[pattern: '/testController/**', access: ['roleInMap']]
			]

			logout {
				additionalHandlerNames = ['additionalLogoutHandler']
			}
		}
	}
}



