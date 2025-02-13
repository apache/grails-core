/* Copyright 2006-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springsecurity.ldap.core

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.ldap.core.support.AbstractContextSource
import org.springframework.ldap.core.support.SimpleDirContextAuthenticationStrategy

/**
 * Based on the anonymous inner class in DefaultSpringSecurityContextSource.
 *
 * @author Luke Taylor
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
@Slf4j
class GrailsSimpleDirContextAuthenticationStrategy extends SimpleDirContextAuthenticationStrategy {

	/** Dependency injection for the userDn. */
	String userDn

	@Override
	void setupEnvironment(Hashtable<String, Object> env, String dn, String password) {
		super.setupEnvironment env, dn, password

		// Remove the pooling flag unless we are authenticating as the 'manager' user.
		if (userDn != dn && env.remove(AbstractContextSource.SUN_LDAP_POOLING_FLAG)) {
			log.debug 'Removed pooling flag for user {}', dn
		}
	}
}
