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
package grails.plugin.springsecurity.acl.access

import groovy.transform.CompileStatic
import org.aopalliance.intercept.MethodInvocation
import org.springframework.security.access.AccessDecisionVoter
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.core.Authentication

import java.lang.reflect.Method

/**
 * Dummy voter that votes yes on Groovy methods like getMetaClass() since they wouldn't be
 * secured but if all voters abstain access is denied.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class GroovyAwareAclVoter implements AccessDecisionVoter<MethodInvocation> {

	protected static final List<String> NON_SECURABLE_METHODS = [
			'invokeMethod', 'getMetaClass', 'setMetaClass', 'getProperty', 'setProperty',
			'isTransactional', 'getTransactional', 'setTransactional']
	static {
		for (Method m in Object.methods) {
			NON_SECURABLE_METHODS << m.name
		}
	}

	boolean supports(ConfigAttribute attribute) {
		true
	}

	boolean supports(Class<?> clazz) {
		clazz.isAssignableFrom MethodInvocation
	}

	int vote(Authentication authentication, MethodInvocation object, Collection<ConfigAttribute> attributes) {
		if (NON_SECURABLE_METHODS.contains(object.method.name)) {
			return ACCESS_GRANTED
		}

		ACCESS_ABSTAIN
	}
}
