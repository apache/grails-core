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
security {

	ui {

		encodePassword = false

		forgotPassword {
			emailFrom = 'Do Not Reply <do.not.reply@localhost>'
			postResetUrl = null // use defaultTargetUrl if not set
			validationUserLookUpProperty = 'user'
			requireForgotPassEmailValidation = 'true'
		}

		gsp {
			layoutRegister = 'register'
			layoutUi       = 'springSecurityUI'
		}

		register {
			defaultRoleNames = ['ROLE_USER']
			emailFrom = 'Do Not Reply <do.not.reply@localhost>'
			postRegisterUrl = null // use defaultTargetUrl if not set
			requireEmailValidation = 'true' //set this to false if you don't want an e-mail validation
		}

		switchUserRoleName = 'ROLE_SWITCH_USER'
	}
}
