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
package grails.plugin.springsecurity.ui.strategy


import grails.plugin.springsecurity.ui.RegisterCommand
import grails.plugin.springsecurity.ui.RegistrationCode
import grails.plugin.springsecurity.ui.ResetPasswordCommand

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
interface RegistrationCodeStrategy {

	void updateRegistrationCode(Map properties, RegistrationCode registrationCode)

	void deleteRegistrationCode(RegistrationCode registrationCode)

	RegistrationCode register(user, String password)

	def createUser(RegisterCommand command)

	def verifyRegistration(String token)



	def validateForgotPasswordExtraSecurity(params,user,forgotPasswordExtraValidationDomainClassName,forgotPasswordExtraValidation, String validationUserLookUpProperty)

	/**
	 * Called when the user clicks the link in the registration email. If the user is found, unlocks
	 * the user and assigns roles, deletes the RegistrationCode, and authenticates the user.
	 *
	 * @param registrationCode the instance created during registration
	 * @return the user, to check for validation errors
	 */
	def finishRegistration(RegistrationCode registrationCode)

	/**
	 * Sends a forgot-password email. Generates a RegistrationCode that will be checked when the user
	 * clicks the link in the email to verify that
	 *
	 * @param username the supplied username
	 * @param emailAddress the user's email (looked up in the database from the username)
	 * @param emailBodyGenerator will be passed the RegistrationCode token to build the email link and body
	 * @return the RegistrationCode, not null but may have validation errors
	 */
	RegistrationCode sendForgotPasswordMail(String username)

	RegistrationCode sendForgotPasswordMail(String username, String emailAddress,
	                                        Closure emailBodyGenerator)

	RegistrationCode sendForgotPasswordMail(String username,  String emailAddress,  Boolean sendMail)

	RegistrationCode sendForgotPasswordMail(String username, String emailAddress,
											Closure emailBodyGenerator,Boolean sendMail)

	/**
	 * If the user is found by the username in the RegistrationCode, updates the user's password from
	 * the ResetPasswordCommand and authenticates the user.
	 *
	 * @param command the command
	 * @param registrationCode the registration code retrieved using the token in the link from the email
	 * @return the user with the specified username if found
	 */
	def resetPassword(ResetPasswordCommand command, RegistrationCode registrationCode)
}
