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
package grails.plugin.springsecurity.access.intercept

import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.intercept.AfterInvocationManager
import org.springframework.security.core.Authentication

import groovy.transform.CompileStatic

/**
 * No-op implementation.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class NullAfterInvocationManager implements AfterInvocationManager {

	def decide(Authentication a, o, Collection<ConfigAttribute> attrs, returnedObject) {
		returnedObject
	}

	boolean supports(ConfigAttribute attribute) {
		false
	}

	boolean supports(Class<?> clazz) {
		true
	}
}
