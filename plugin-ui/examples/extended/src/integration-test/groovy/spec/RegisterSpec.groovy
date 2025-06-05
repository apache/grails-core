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

package spec

import grails.testing.mixin.integration.Integration
import page.profile.ProfileCreatePage
import page.profile.ProfileEditPage
import page.profile.ProfileListPage
import page.register.ForgotPasswordPage
import page.register.RegisterPage
import page.register.ResetPasswordPage
import page.register.SecurityQuestionsPage
import page.user.UserEditPage
import page.user.UserSearchPage

@Integration
class RegisterSpec extends AbstractSecuritySpec {

	void testRegisterValidation() {
		when:
		def registerPage = browser.to(RegisterPage).tap {
			submit()
		}

		then:
		assertContentContains('Username is required')
		assertContentContains('Email is required')
		assertContentContains('Password is required')

		when:
		registerPage.with {
			username = 'admin'
			email = 'foo'
			password = 'abcdefghijk'
			password2 = 'mnopqrstuwzy'
			submit()
		}

		then:
		assertContentContains('The username is taken')
		assertContentContains('Please provide a valid email address')
		assertContentContains('Password must have at least one letter, number, and special character: !@#$%^&')
		assertContentContains('Passwords do not match')

		when:
		registerPage.with {
			username = 'abcdef123'
			email = 'abcdef@abcdef.com'
			password = 'aaaaaaaa'
			password2 = 'aaaaaaaa'
			submit()
		}

		then:
		assertContentContains('Password must have at least one letter, number, and special character: !@#$%^&')
	}

	void testForgotPasswordValidation() {
		when:
		def forgotPasswordPage = browser.to(ForgotPasswordPage).tap {
			submit()
		}

		then:
		assertContentContains('Please enter your username')

		when:
		forgotPasswordPage.with {
			username = '1111'
			submit()
		}

		then:
		assertContentContains('No user was found with that username')
	}

	void testRegisterAndForgotPassword() {
		given:
		String un = "test_user_abcdef${System.currentTimeMillis()}"

		when:
		browser.go('register/resetPassword?t=123')

		then:
		assertHtmlContains('Sorry, we have no record of that request, or it has expired')

		when:
		def registerPage = browser.to(RegisterPage)

		then:
		browser.at(RegisterPage)

		when:
		registerPage.with {
			username = un
			email = "$un@abcdef.com"
			password = 'aaaaaa1#'
			password2 = 'aaaaaa1#'
			submit()
		}

		then:
		assertHtmlContains('Your registration is complete')

		when:
		browser.to(ProfileCreatePage).with {
			create(un)
		}

		then:
		def profileListPage = browser.at(ProfileListPage)
		assertHtmlContains('created')

		when:
		profileListPage.editProfile(un)

		then:
		def profileEditPage = browser.at(ProfileEditPage)

		when:
		profileEditPage.updateProfile(un)

		then:
		browser.at(ProfileListPage)
		assertHtmlContains('updated')

		when:
		logout()
		browser.go('')

		then:
		assertContentContains('Log in')

		when:
		browser.to(ForgotPasswordPage).with {
			username = un
			submit()
		}

		then:
		def securityQuestionPage = browser.at(SecurityQuestionsPage)

		when:
		securityQuestionPage.with {
			question1 = '1234'
			question2 = '12345'
			submit()
		}

		then:
		def resetPasswordPage = browser.at(ResetPasswordPage)

		when:
		resetPasswordPage.submit()

		then:
		assertContentContains('Password is required')

		when:
		resetPasswordPage.enterNewPassword('abcdefghijk','mnopqrstuwzy')

		then:
		assertContentContains('Password must have at least one letter, number, and special character: !@#$%^&')
		assertContentContains('Passwords do not match')

		when:
		resetPasswordPage.enterNewPassword('aaaaaaaa', 'aaaaaaaa')

		then:
		assertContentContains('Password must have at least one letter, number, and special character: !@#$%^&')

		when:
		resetPasswordPage.enterNewPassword('aaaaaa1#', 'aaaaaa1#')

		then:
		assertHtmlContains('Your password was successfully changed')

		when:
		logout()
		browser.go('')

		then:
		assertContentContains('Log in')

		when:
		browser.to(ProfileListPage)

		then:
		browser.at(ProfileListPage)

		when:
		profileListPage.editProfile(un)

		then:
		def profileEditPage2 = browser.at(ProfileEditPage)

		when:
		profileEditPage2.deleteProfile()

		then:
		assertHtmlContains('deleted')

		when:
		browser.go("user/edit?username=$un")

		then:
		def userEditPage = browser.at(UserEditPage)
		userEditPage.username.text == un

		when:
		userEditPage.delete()

		then:
		browser.at(UserSearchPage)

		when:
		browser.go("user/edit?username=$un")

		then:
		assertHtmlContains('User not found')
	}
}