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
			authority.className = 'com.test.Role'
         cas {
            loginUri         = '/login'
            serverUrlPrefix  = 'http://localhost:9090/cas'
            proxyReceptorUrl = '/secure/receptor'
            serviceUrl       = 'http://localhost:${server.port}/login/cas'
            proxyCallbackUrl = 'http://localhost:${server.port}/secure/receptor'
         }
			controllerAnnotations.staticRules = [
				[pattern: '/',               access: 'permitAll'],
				[pattern: '/error',          access: 'permitAll'],
				[pattern: '/index',          access: 'permitAll'],
				[pattern: '/index.gsp',      access: 'permitAll'],
				[pattern: '/shutdown',       access: 'permitAll'],
				[pattern: '/assets/**',      access: 'permitAll'],
				[pattern: '/**/js/**',       access: 'permitAll'],
				[pattern: '/**/css/**',      access: 'permitAll'],
				[pattern: '/**/images/**',   access: 'permitAll'],
				[pattern: '/**/favicon.ico', access: 'permitAll']
			]
         fii.rejectPublicInvocations = false
			filterChain.chainMap = [
				[pattern: '/assets/**',      filters: 'none'],
				[pattern: '/**/js/**',       filters: 'none'],
				[pattern: '/**/css/**',      filters: 'none'],
				[pattern: '/**/images/**',   filters: 'none'],
				[pattern: '/**/favicon.ico', filters: 'none'],
				[pattern: '/**',             filters: 'JOINED_FILTERS']
			]
         logout.postOnly = false
         rejectIfNoRule = false
			userLookup {
				userDomainClassName = 'com.test.User'
				authorityJoinClassName = 'com.test.UserRole'
			}
		}
	}
}
