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

import grails.plugin.springsecurity.annotation.Secured

class SecureAnnotatedController {

	@Secured('ROLE_ADMIN')
	def index() {
		render 'you have ROLE_ADMIN'
	}

	@Secured(['ROLE_ADMIN', 'ROLE_ADMIN2'])
	def adminEither() {
		render 'you have ROLE_ADMIN or ROLE_ADMIN2'
	}

	@Secured('ROLE_USER')
	def userAction() {
		render 'you have ROLE_USER'
	}

	@Secured("authentication.name == 'admin1'")
	def expression() {
		render 'expression: OK'
	}

	@Secured('ROLE_ADMIN')
	def indexMethod() {
		render 'you have ROLE_ADMIN - method'
	}

	@Secured(['ROLE_ADMIN', 'ROLE_ADMIN2'])
	def adminEitherMethod() {
		render 'you have ROLE_ADMIN or ROLE_ADMIN2 - method'
	}

	@Secured('ROLE_USER')
	def userActionMethod() {
		render 'you have ROLE_USER - method'
	}

	@Secured("authentication.name == 'admin1'")
	def expressionMethod() {
		render 'OK - method'
	}

	@Secured(closure = {
		assert request
		assert ctx
		authentication.name == 'admin1'
	})
	def closureMethod() {
		render 'OK - closureMethod'
	}
}
