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

import groovy.util.logging.Slf4j
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceAware
import org.springframework.context.support.MessageSourceAccessor
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.core.SpringSecurityMessageSource
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsChecker

import groovy.transform.CompileStatic

/**
 * Copy of the private class in AbstractUserDetailsAuthenticationProvider
 * to make subclassing or replacement easier.
 *
 * @author Burt Beckwith
 */
@Slf4j
@CompileStatic
class DefaultPostAuthenticationChecks implements UserDetailsChecker, MessageSourceAware {

	protected MessageSourceAccessor messages = SpringSecurityMessageSource.accessor

	@Override
	void setMessageSource(MessageSource messageSource) {
		this.messages = new MessageSourceAccessor(messageSource)
	}

	void check(UserDetails user) {
		if (!user.credentialsNonExpired) {
			log.debug 'User account credentials have expired'

			throw new CredentialsExpiredException(messages.getMessage(
					'AbstractUserDetailsAuthenticationProvider.credentialsExpired',
					'User credentials have expired'))
		}
	}
}
