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
package grails.plugin.springsecurity.web.authentication

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.NullRememberMeServices
import org.springframework.security.web.authentication.logout.LogoutHandler

import groovy.transform.CompileStatic

/**
 * @author Burt Beckwith
 */
@CompileStatic
class NullLogoutHandlerRememberMeServices extends NullRememberMeServices implements LogoutHandler {
	void logout(HttpServletRequest req, HttpServletResponse res, Authentication a) {
		// no-op
	}
}
