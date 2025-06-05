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
import grails.plugin.springsecurity.ui.SpringSecurityUiService
import groovy.transform.CompileStatic

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class DefaultRegistrationCodeStrategy implements RegistrationCodeStrategy {

	SpringSecurityUiService springSecurityUiService

	void updateRegistrationCode(Map properties, RegistrationCode registrationCode) {
		springSecurityUiService.updateRegistrationCode properties, registrationCode
	}

	void deleteRegistrationCode(RegistrationCode registrationCode) {
		springSecurityUiService.deleteRegistrationCode registrationCode
	}

	RegistrationCode register(user, String password) {
		springSecurityUiService.register user, password
	}

	def verifyRegistration(String token) {
		springSecurityUiService.verifyRegistration(token)
	}

	def validateForgotPasswordExtraSecurity(params,user,forgotPasswordExtraValidationDomainClassName, forgotPasswordExtraValidation, String validationUserLookUpProperty){
		springSecurityUiService.validateForgotPasswordExtraSecurity(params,user,forgotPasswordExtraValidationDomainClassName,forgotPasswordExtraValidation,validationUserLookUpProperty)
	}

	def createUser(RegisterCommand command) {
		springSecurityUiService.createUser command
	}

	def finishRegistration(RegistrationCode registrationCode) {
		springSecurityUiService.finishRegistration registrationCode
	}

	RegistrationCode sendForgotPasswordMail(String username) {
        springSecurityUiService.sendForgotPasswordMail(username)
	}

	RegistrationCode sendForgotPasswordMail(String username, String emailAddress, Closure emailBodyGenerator) {
        springSecurityUiService.sendForgotPasswordMail(username, emailAddress, emailBodyGenerator,true)
	}

	RegistrationCode sendForgotPasswordMail(String username,  String emailAddress,  Boolean sendMail) {
		springSecurityUiService.sendForgotPasswordMail username, emailAddress, sendMail
	}

	RegistrationCode sendForgotPasswordMail(String username, String emailAddress, Closure emailBodyGenerator, Boolean sendMail) {
		springSecurityUiService.sendForgotPasswordMail username, emailAddress, emailBodyGenerator, sendMail
	}

	def resetPassword(ResetPasswordCommand command, RegistrationCode registrationCode) {
		springSecurityUiService.resetPassword command, registrationCode
	}
}
