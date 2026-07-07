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
package simple.spec

import com.dumbster.smtp.SimpleSmtpServer
import com.dumbster.smtp.SmtpMessage
import page.HomePage
import page.register.ForgotPasswordPage
import page.register.RegisterPage
import page.register.ResetPasswordPage
import page.register.VerifyRegistrationPage
import page.user.UserEditPage
import page.user.UserSearchPage
import spec.SecurityUISpec

import grails.testing.mixin.integration.Integration

@Integration
class RegisterSpec extends SecurityUISpec {

	void testRegisterValidation() {
		when:
		def page = to(RegisterPage)
				.submitRegister(RegisterPage)

		then:
		with(pageSource) {
			contains('Username is required')
			contains('Email is required')
			contains('Password is required')
		}

		when:
		page = page.submitRegister(
				new RegisterPage.Form(
					'admin',
					'foo',
					'abcdefghijk',
					'mnopqrstuwzy'
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
					'abcdef123',
					'abcdef@abcdef.com',
					'aaaaaaaa',
					'aaaaaaaa'
				),
				RegisterPage
		)

		then:
		pageSource.contains('Password must have at least one letter, number, and special character: !@#$%^&')
	}

	void testForgotPasswordValidation() {
		when:
		def page = to(ForgotPasswordPage)
				.submitForgotPassword('', ForgotPasswordPage)

		then:
		pageSource.contains('Please enter your username')

		when:
		page.submitForgotPassword('1111', ForgotPasswordPage)

		then:
		pageSource.contains('No user was found with that username')
	}

	void testRegisterAndForgotPassword() {
		setup:
		def smtpServer = startSmtpServer()

		when:
		def un = "test_user_abcdef${System.currentTimeMillis()}"
		to(RegisterPage).submitRegister(
				new RegisterPage.Form(
						un,
						"$un@abcdef.com",
						'aaaaaa1#',
						'aaaaaa1#'
				),
				RegisterPage
		)

		then:
		pageSource.contains('Your account registration email was sent - check your mail!')
		smtpServer.receivedEmailSize == 1

		when:
		def smtpMessage = fetchCurrentEmail(smtpServer)

		then:
		with(smtpMessage) {
			getHeaderValue('Subject') == 'New Account'
			body.contains("Hi $un")
		}

		when:
		def verificationCodePattern = ~/^[a-f0-9]{32}$/
		def code = findCode(smtpMessage.body, 'verifyRegistration')

		then:
		code ==~ verificationCodePattern

		when:
		via(VerifyRegistrationPage, code)

		then:
		at(HomePage)
		pageSource.contains('Your registration is complete')

		when:
		logout()

		then:
		pageSource.contains('Log in')

		when:
		to(ForgotPasswordPage)
				.submitForgotPassword(un, ForgotPasswordPage)

		then:
		pageSource.contains('Your password reset email was sent - check your mail!')
		smtpServer.receivedEmailSize == 2

		when:
		smtpMessage = fetchCurrentEmail(smtpServer)

		then:
		smtpMessage.with {
			getHeaderValue('Subject') == 'Password Reset'
			body.contains("Hi $un")
		}

		when:
		code = findCode(smtpMessage.body, 'resetPassword')
		via(ResetPasswordPage, '123')

		then:
		at(HomePage)
		pageSource.contains('Sorry, we have no record of that request, or it has expired')
		code ==~ /^[a-f0-9]{32}$/

		when:
		to(ResetPasswordPage, code)
				.submitResetPassword(ResetPasswordPage)

		then:
		pageSource.contains('Password is required')

		when:
		to(ResetPasswordPage, code).enterNewPassword(
				new ResetPasswordPage.Form(
						'abcdefghijk',
						'mnopqrstuwzy'
				),
				ResetPasswordPage
		)

		then:
		with(pageSource) {
			contains('Password must have at least one letter, number, and special character: !@#$%^&')
			contains('Passwords do not match')
		}

		when:
		to(ResetPasswordPage, code).enterNewPassword(
				new ResetPasswordPage.Form(
						'aaaaaaaa',
						'aaaaaaaa'
				),
				ResetPasswordPage
		)

		then:
		pageSource.contains('Password must have at least one letter, number, and special character: !@#$%^&')

		when:
		to(ResetPasswordPage, code).enterNewPassword(
				new ResetPasswordPage.Form(
						'aaaaaa1#',
						'aaaaaa1#'
				),
				HomePage
		)

		then:
		pageSource.contains('Your password was successfully changed')

		when:
		logout()

		then:
		pageSource.contains('Log in')

		// delete the user so it doesn't affect other tests
		when:
		def page = to(UserEditPage, username: un)

		then:
		page.username.text == un

		when:
		page.submitDelete(UserSearchPage)

		and:
		via(UserEditPage, un)

		then:
		at(UserSearchPage)
		pageSource.contains('User not found')

		cleanup:
		smtpServer.stop()
	}

	private static SmtpMessage fetchCurrentEmail(SimpleSmtpServer smtpServer) {
		def received = smtpServer.receivedEmail
		def email = null
		while (received.hasNext()) {
			email = received.next()
		}
		return email as SmtpMessage
	}

	private static String findCode(String body, String action) {
		def matcher = body =~ /(?s).*$action\?t=(.+)".*/
		assert matcher.find()
		assert matcher.groupCount() == 1
		matcher.group(1)
	}

	private SimpleSmtpServer startSmtpServer() {
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
		def smtpServer = SimpleSmtpServer.start(port)
		go("testData/updateMailSenderPort?port=$port")
		assert pageSource.contains("OK: $port")
		smtpServer
	}
}
