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

import org.springframework.security.access.annotation.Secured

@Secured('permitAll')
class MiscTestController {

	def test() {}

	def testControllerMethods() {
		render """
		getPrincipal: ${getPrincipal()}<br/>
		principal: $principal<br/>

		isLoggedIn: ${isLoggedIn()}<br/>
		loggedIn: $loggedIn<br/>

		getAuthenticatedUser: ${getAuthenticatedUser()}<br/>
		authenticatedUser: $authenticatedUser<br/>
		"""
	}

	def testServletApiMethods() {
		render """
		request.getUserPrincipal(): ${request.getUserPrincipal()}<br/>
		request.userPrincipal: $request.userPrincipal<br/>

		request.isUserInRole('ROLE_ADMIN'): ${request.isUserInRole('ROLE_ADMIN')}<br/>
		request.isUserInRole('ROLE_FOO'): ${request.isUserInRole('ROLE_FOO')}<br/>

		request.getRemoteUser(): ${request.getRemoteUser()}<br/>
		request.remoteUser: $request.remoteUser<br/>
		"""
	}
}
