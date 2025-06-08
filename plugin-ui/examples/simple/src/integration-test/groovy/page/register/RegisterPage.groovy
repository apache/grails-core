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

package page.register

import geb.module.PasswordInput
import geb.module.TextInput
import page.AbstractSecurityPage

class RegisterPage extends AbstractSecurityPage {

	static url = 'register'
	static at = {
		waitFor {
			title == 'Register'
		}
	}
	static content = {
		form { $('registerForm') }
		username { $(name: 'username').module(TextInput) }
		email { $(name: 'email').module(TextInput) }
		password { $(name: 'password').module(PasswordInput) }
		password2 { $(name: 'password2').module(PasswordInput) }
		submitBtn { $('a', id: 'submit') }
	}
}
