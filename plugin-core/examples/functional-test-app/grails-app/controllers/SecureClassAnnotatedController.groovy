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

@Secured('ROLE_ADMIN')
class SecureClassAnnotatedController {

	def index() {
		render 'index: you have ROLE_ADMIN'
	}

	def otherAction() {
		render 'otherAction: you have ROLE_ADMIN'
	}

	@Secured('ROLE_ADMIN2')
	def admin2() {
		render 'admin2: you have ROLE_ADMIN2'
	}
}
