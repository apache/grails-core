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

grails.plugin.springsecurity.authority.className = 'com.testapp.TestRole'
grails.plugin.springsecurity.debug.useFilter = true
grails.plugin.springsecurity.logout.afterLogoutUrl = '/hack/blankPage'
grails.plugin.springsecurity.password.algorithm = 'SHA-256'
grails.plugin.springsecurity.requestMap.className = 'com.testapp.TestRequestmap'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'com.testapp.TestUserTestRole'
grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.testapp.TestUser'
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
				[pattern: '/login/impersonate',  access: 'ROLE_ADMIN'],
				[pattern: '/logout/impersonate', access: 'permitAll'],
				[pattern: '/',                   access: 'permitAll'],
				[pattern: '/error',              access: 'permitAll'],
				[pattern: '/index',              access: 'permitAll'],
				[pattern: '/index.gsp',          access: 'permitAll'],
				[pattern: '/shutdown',           access: 'permitAll'],
				[pattern: '/**/js/**',           access: 'permitAll'],
				[pattern: '/**/css/**',          access: 'permitAll'],
				[pattern: '/**/images/**',       access: 'permitAll'],
				[pattern: '/**/favicon.ico',     access: 'permitAll'],
				[pattern: '/dbconsole/**',       access: 'permitAll'],
				[pattern: '/dbconsole',          access: 'permitAll'],
				[pattern: '/assets/**',          access: 'permitAll'],
				[pattern: '/securityinfo',       access: 'permitAll'],
				[pattern: '/securityinfo/**',    access: 'permitAll']
			]
grails.plugin.springsecurity.filterChain.chainMap = [
				[pattern: '/assets/**',      filters: 'none'],
				[pattern: '/**/js/**',       filters: 'none'],
				[pattern: '/**/css/**',      filters: 'none'],
				[pattern: '/**/images/**',   filters: 'none'],
				[pattern: '/**/favicon.ico', filters: 'none'],
				[pattern: '/**', 			 filters: 'JOINED_FILTERS']
			]


String testconfig = System.getProperty('TESTCONFIG')
switch (testconfig) {
	case 'annotation':
		grails.plugin.springsecurity.securityConfigType = 'Annotation'
		break

	case 'basic':
		grails.plugin.springsecurity.securityConfigType = 'Annotation'
		grails.plugin.springsecurity.useBasicAuth = true
		grails.plugin.springsecurity.basic.realmName = 'Grails Spring Security Basic Test Realm'
		grails.plugin.springsecurity.filterChain.chainMap = [
			[pattern: '/secureclassannotated/**', filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
			[pattern: '/**',                      filters: 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter']
		]
		break

	case 'basicCacheUsers':
		grails.plugin.springsecurity.securityConfigType = 'Annotation'
		grails.plugin.springsecurity.useBasicAuth = true
		grails.plugin.springsecurity.basic.realmName = 'Grails Spring Security Basic Test Realm'
		grails.plugin.springsecurity.filterChain.chainMap = [
			[pattern: '/secureclassannotated/**', filters: 'JOINED_FILTERS,-exceptionTranslationFilter'],
			[pattern: '/**',                      filters: 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter']
		]
		grails.plugin.springsecurity.cacheUsers = true
		grails.plugin.springsecurity.providerManager.eraseCredentialsAfterAuthentication = false
		break

	case 'bcrypt':
		grails.plugin.springsecurity.securityConfigType = 'Annotation'
		grails.plugin.springsecurity.password.algorithm = 'bcrypt'
		break

	case 'misc':
		grails.plugin.springsecurity.securityConfigType = 'Annotation'
		grails.plugin.springsecurity.dao.reflectionSaltSourceProperty = 'username'
		grails.plugin.springsecurity.roleHierarchy = 'ROLE_ADMIN > ROLE_USER'
		grails.plugin.springsecurity.useSwitchUserFilter = true
		grails.plugin.springsecurity.failureHandler.exceptionMappings = [
			[exception: 'org.springframework.security.authentication.LockedException',             url: '/test-user/account-locked'],
			[exception: 'org.springframework.security.authentication.DisabledException',           url: '/test-user/account-disabled'],
			[exception: 'org.springframework.security.authentication.AccountExpiredException',     url: '/test-user/account-expired'],
			[exception: 'org.springframework.security.authentication.CredentialsExpiredException', url: '/test-user/password-expired']
		]
		grails.web.url.converter = 'hyphenated'
		break

	case 'requestmap':
		grails.plugin.springsecurity.securityConfigType = 'Requestmap'
		break

	case 'static':
		grails.plugin.springsecurity.securityConfigType = 'InterceptUrlMap'
		grails.plugin.springsecurity.interceptUrlMap = [
			[pattern: '/secureannotated/admineither', access: ['ROLE_ADMIN', 'ROLE_ADMIN2']],
			[pattern: '/secureannotated/expression',  access: ["authentication.name == 'admin1'"]],
			[pattern: '/secureannotated/**',          access: 'ROLE_ADMIN'],
			[pattern: '/**',                          access: 'permitAll']
		]
		break
}
