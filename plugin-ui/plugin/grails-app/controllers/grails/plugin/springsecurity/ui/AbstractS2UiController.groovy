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
package grails.plugin.springsecurity.ui

import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityUtils
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
abstract class AbstractS2UiController implements InitializingBean {

	static scope = 'singleton'

	// needed for afterPropertiesSet since accessing the Trait's
	// grailsApplication looks for a current thread-bound request
	private @Autowired GrailsApplication application

	protected findUserByUsername(String username) {
		if (username) {
			return User.findWhere((usernamePropertyName): username)
		}
	}

	protected Class<?> getDomainClassClass(String name) {
		if (name) {
			return application.getDomainClass(name)?.clazz
		}
	}

	protected static getConf() {
		SpringSecurityUtils.securityConfig
	}

	protected String authorityNameField
	protected String usernamePropertyName

	protected Class<?> Role
	protected Class<?> User
	protected Class<?> UserRole

	void afterPropertiesSet() {
		authorityNameField = conf.authority.nameField ?: ''
		usernamePropertyName = conf.userLookup.usernamePropertyName ?: ''

		Role = getDomainClassClass(conf.authority.className ?: '')
		User = getDomainClassClass(conf.userLookup.userDomainClassName ?: '')
		UserRole = getDomainClassClass(conf.userLookup.authorityJoinClassName ?: '')
	}
}
