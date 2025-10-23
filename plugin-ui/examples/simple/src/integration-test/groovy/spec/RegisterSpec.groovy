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
import page.register.ForgotPasswordPage
import page.register.RegisterPage
import page.register.ResetPasswordPage
import page.user.UserEditPage
import page.user.UserSearchPage

import com.dumbster.smtp.SimpleSmtpServer
import com.dumbster.smtp.SmtpMessage

@Integration
class RegisterSpec extends AbstractSecuritySpec {

	private SimpleSmtpServer server

	void setup() {
		startMailServer()
	}

	void cleanup() {
		server.stop()
	}

	void testRegisterValidation() {
		when:
		to(RegisterPage).with {
			submit()
		}
		def registerPage = at(RegisterPage)

		then:
		pageSource.contains('Username is required')
		pageSource.contains('Email is required')
		pageSource.contains('Password is required')

		when:
		registerPage.with {
			username = 'admin'
			email = 'foo'
			password = 'abcdefghijk'
			password2 = 'mnopqrstuwzy'
			submit()
		}
		registerPage = at(RegisterPage)

		then:
		pageSource.contains('The username is taken')
		pageSource.contains('Please provide a valid email address')
		pageSource.contains('Password must have at least one letter, number, and special character: !@#$%^&')
		pageSource.contains('Passwords do not match')

		when:
		registerPage.with {
			username = 'abcdef123'
			email = 'abcdef@abcdef.com'
			password = 'aaaaaaaa'
			password2 = 'aaaaaaaa'
			submit()
		}

		then:
		at(RegisterPage)
		pageSource.contains('Password must have at least one letter, number, and special character: !@#$%^&')
	}

	void testForgotPasswordValidation() {
		when:
		to(ForgotPasswordPage).with {
			submit()
		}
		def forgotPasswordPage = at(ForgotPasswordPage)

		then:
		pageSource.contains('Please enter your username')

		when:
		forgotPasswordPage.username = '1111'
		forgotPasswordPage.submit()

		then:
		at(ForgotPasswordPage)
		pageSource.contains('No user was found with that username')
	}

	void testRegisterAndForgotPassword() {
		given:
		String un = "test_user_abcdef${System.currentTimeMillis()}"

		when:
		def registerPage = to(RegisterPage)

		and:
		registerPage.with {
			username = un
			email = "$un@abcdef.com"
			password = 'aaaaaa1#'
			password2 = 'aaaaaa1#'
			submit()
		}

		then:
		pageSource.contains('Your account registration email was sent - check your mail!')
		1 == server.receivedEmailSize

		when:
		def email = currentEmail

		then:
		'New Account' == email.getHeaderValue('Subject')

		when:
		String body = email.body

		then:
		body.contains("Hi $un")

		when:
		String code = findCode(body, 'verifyRegistration')

		then:
		code ==~ /^[a-f0-9]{32}$/

		when:
		go("register/verifyRegistration?t=$code")

		then:
		pageSource.contains('Your registration is complete')

		when:
		logout()
		go('')

		then:
		pageSource.contains('Log in')

		when:
		to(ForgotPasswordPage).with {
			username = un
			submit()
		}

		then:
		pageSource.contains('Your password reset email was sent - check your mail!')
		2 == server.receivedEmailSize

		when:
		email = currentEmail

		then:
		'Password Reset' == email.getHeaderValue('Subject')

		when:
		body = email.body

		then:
		body.contains("Hi $un")

		when:
		code = findCode(body, 'resetPassword')
		go('register/resetPassword?t=123')

		then:
		pageSource.contains('Sorry, we have no record of that request, or it has expired')
		code ==~ /^[a-f0-9]{32}$/

		when:
		go("register/resetPassword?t=$code")

		then:
		def resetPasswordPage = at(ResetPasswordPage)

		when:
		resetPasswordPage.submit()
		resetPasswordPage = at(ResetPasswordPage)

		then:
		pageSource.contains('Password is required')

		when:
		go("register/resetPassword?t=$code")
		resetPasswordPage.enterNewPassword('abcdefghijk', 'mnopqrstuwzy')
		resetPasswordPage = at(ResetPasswordPage)

		then:
		pageSource.contains('Password must have at least one letter, number, and special character: !@#$%^&')
		pageSource.contains('Passwords do not match')

		when:
		go("register/resetPassword?t=$code")
		resetPasswordPage.enterNewPassword('aaaaaaaa', 'aaaaaaaa')
		resetPasswordPage = at(ResetPasswordPage)

		then:
		pageSource.contains('Password must have at least one letter, number, and special character: !@#$%^&')

		when:
		go("register/resetPassword?t=$code")
		resetPasswordPage.enterNewPassword('aaaaaa1#', 'aaaaaa1#')

		then:
		waitFor { pageSource.contains('Your password was successfully changed') }

		when:
		logout()
		go('')

		then:
		pageSource.contains('Log in')

		// delete the user so it doesn't affect other tests
		when:
		go("user/edit?username=$un")
		def userEditPage = at(UserEditPage)

		then:
		userEditPage.username.text == un

		when:
		userEditPage.delete()

		then:
		at(UserSearchPage)

		when:
		go("user/edit?username=$un")

		then:
		pageSource.contains('User not found')
	}

	private SmtpMessage getCurrentEmail() {
		def received = server.receivedEmail
		def email = null
		while (received.hasNext()) {
			email = received.next()
		}
		return email as SmtpMessage
	}

	private String findCode(String body, String action) {
		def matcher = body =~ /(?s).*$action\?t=(.+)".*/
		assert matcher.hasGroup()
		assert matcher.count == 1
		matcher[0][1]
	}

	private void startMailServer() {
		int port = 1025
		while (true) {
			try {
				new ServerSocket(port).close()
				break
			}
			catch (IOException ignored) {
				port++
				assert port < 2000, 'cannot find open port'
			}
		}
		server = SimpleSmtpServer.start(port)
		go("testData/updateMailSenderPort?port=$port")
		pageSource.contains("OK: $port")
	}
}
