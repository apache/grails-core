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
package extended.spec

import extended.page.profile.ProfileCreatePage
import extended.page.profile.ProfileListPage
import page.HomePage
import page.register.ForgotPasswordPage
import page.register.RegisterPage
import page.register.ResetPasswordPage
import page.register.SecurityQuestionsPage
import page.user.UserEditPage
import page.user.UserSearchPage
import spec.SecurityUISpec

import grails.testing.mixin.integration.Integration

@Integration
class RegisterSpec extends SecurityUISpec {

	void testRegisterValidation() {
		when:
		def page = to(RegisterPage).submitRegister(RegisterPage)

		then:
		with(pageSource) {
			contains('Username is required')
			contains('Email is required')
			contains('Password is required')
		}

		when:
		page = page.submitRegister(
				new RegisterPage.Form(
						username: 'admin',
						email: 'foo',
						password: 'abcdefghijk',
						password2: 'mnopqrstuwzy'
				),
				RegisterPage
		)

		then:
		with(pageSource) {
			contains('The username is taken')
			contains('Please provide a valid email address')
			contains('Password must have at least one letter, number, and special character: !@#$%^&')
			contains('Passwords do not match')
		}

		when:
		page.submitRegister(
				new RegisterPage.Form(
						username: 'abcdef123',
						email: 'abcdef@abcdef.com',
						password: 'aaaaaaaa',
						password2: 'aaaaaaaa'
				),
				RegisterPage
		)

		then:
		pageSource.contains('Password must have at least one letter, number, and special character: !@#$%^&')
	}

	void testForgotPasswordValidation() {
		when:
		def page = to(ForgotPasswordPage)
				.submitForgotPassword(ForgotPasswordPage)

		then:
		pageSource.contains('Please enter your username')

		when:
		page.submitForgotPassword('1111', ForgotPasswordPage)

		then:
		pageSource.contains('No user was found with that username')
	}

	void testRegisterAndForgotPassword() {
		given:
		def un = "test_user_abcdef${System.currentTimeMillis()}"

		when:
		via(ResetPasswordPage, '123')

		then: 'the user is redirected to the home page and a growl message is shown'
		at(HomePage)
		waitFor {
			pageSource.contains('Sorry, we have no record of that request, or it has expired')
		}

		when:
		to(RegisterPage).submitRegister(
				new RegisterPage.Form(
						username: un,
						email: "$un@abcdef.com",
						password: 'aaaaaa1#',
						password2: 'aaaaaa1#'
				),
				HomePage
		)

		then:
		pageSource.contains('Your registration is complete')

		when:
		def page = to(ProfileCreatePage).submitCreate(un, ProfileListPage)

		then:
		pageSource.contains('created')

		when:
		page = page.editProfile(un)

		and:
		page.submitUpdate(un, ProfileListPage)

		then:
		pageSource.contains('updated')

		when:
		logout()

		then:
		pageSource.contains('Log in')

		when:
		page = to(ForgotPasswordPage).submitForgotPassword(un, SecurityQuestionsPage)

		and:
		page = page.submitAnswer('1234', '12345', ResetPasswordPage)

		and:
		page = page.submitResetPassword(ResetPasswordPage)

		then:
		pageSource.contains('Password is required')

		when:
		page = page.enterNewPassword(
 				new ResetPasswordPage.Form(
						password: 'abcdefghijk',
						password2: 'mnopqrstuwzy'
				),
				ResetPasswordPage
		)

		then:
		with(pageSource) {
			contains('Password must have at least one letter, number, and special character: !@#$%^&')
			contains('Passwords do not match')
		}

		when:
		page.enterNewPassword(
				new ResetPasswordPage.Form(
						password: 'aaaaaaaa',
						password2: 'aaaaaaaa'
				),
				ResetPasswordPage
		)

		then:
		pageSource.contains('Password must have at least one letter, number, and special character: !@#$%^&')

		when:
		page.enterNewPassword(
				new ResetPasswordPage.Form(
						password: 'aaaaaa1#',
						password2: 'aaaaaa1#'
				),
				HomePage
		)

		then:
		pageSource.contains('Your password was successfully changed')

		when:
		logout()

		then:
		pageSource.contains('Log in')

		when:
		page = to(ProfileListPage)

		and:
		page = page.editProfile(un)

		and:
		page.deleteProfile()

		then:
		pageSource.contains('deleted')

		when:
		page = to(UserEditPage, username: un)

		then:
		page.username.text == un

		when:
		page.submitDelete(UserSearchPage)

		and:
		via(UserEditPage, username: un)

		then:
		at(UserSearchPage)
		pageSource.contains('User not found')
	}
}
