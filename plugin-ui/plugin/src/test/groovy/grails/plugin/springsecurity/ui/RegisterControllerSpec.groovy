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
package grails.plugin.springsecurity.ui

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class RegisterControllerSpec extends Specification implements ControllerUnitTest<RegisterController> {

	void setup() {
		SpringSecurityUtils.setSecurityConfig([:] as ConfigObject)
	}

	void cleanup() {
		SpringSecurityUtils.resetSecurityConfig()
	}

	void 'passwordValidator validation fails if the password is the same as the username'() {
		given:
		addRegisterEmailSubjectToMessageSource()

		expect:
		'command.password.error.username' == RegisterController.passwordValidator('username', [username: 'username'])
	}

	void 'passwordValidator validation strength check fails if the password is too short'() {
		given:
		addRegisterEmailSubjectToMessageSource()

		when:
		def command = [username: 'username']
		String password = 'h!Z7'
		updateFromConfig()

		then:
		!RegisterController.checkPasswordMinLength(password, command)
		RegisterController.checkPasswordMaxLength password, command
		RegisterController.checkPasswordRegex password, command

		'command.password.error.length' == RegisterController.passwordValidator(password, command)[0]
	}

	void 'passwordValidator validation fails if the password is too short'() {
		given:
		addRegisterEmailSubjectToMessageSource()

		when:
		def command = [username: 'username']
		String password = 'h!Z7'
		SpringSecurityUtils.securityConfig.ui.password.minLength = 3
		updateFromConfig()

		then:
		RegisterController.checkPasswordMinLength password, command
		RegisterController.checkPasswordMaxLength password, command
		RegisterController.checkPasswordRegex password, command

		!RegisterController.passwordValidator(password, command)
	}

	void 'passwordValidator validation strength check fails if the password is too long'() {
		given:
		addRegisterEmailSubjectToMessageSource()

		when:
		def command = [username: 'username']
		String password = 'h!Z7aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1'

		then:
		RegisterController.checkPasswordMinLength password, command
		!RegisterController.checkPasswordMaxLength(password, command)
		RegisterController.checkPasswordRegex password, command

		'command.password.error.length' == RegisterController.passwordValidator(password, command)[0]
	}

	void 'passwordValidator validation fails if the password is too long'() {
		given:
		addRegisterEmailSubjectToMessageSource()

		when:
		def command = [username: 'username']
		String password = 'h!Z7aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1'
		SpringSecurityUtils.securityConfig.ui.password.maxLength = 100
		updateFromConfig()

		then:
		RegisterController.checkPasswordMinLength password, command
		RegisterController.checkPasswordMaxLength password, command
		RegisterController.checkPasswordRegex password, command

		!RegisterController.passwordValidator(password, command)
	}

	void 'passwordValidator validation fails if the default regex does not match'() {
		when:
		addRegisterEmailSubjectToMessageSource()
		def command = [username: 'username']
		String password = 'password'

		then:
		RegisterController.checkPasswordMinLength password, command
		RegisterController.checkPasswordMaxLength password, command
		!RegisterController.checkPasswordRegex(password, command)

		'command.password.error.strength' == RegisterController.passwordValidator(password, command)

		when:
		password = 'h!Z7abcd'

		then:
		RegisterController.checkPasswordMinLength password, command
		RegisterController.checkPasswordMaxLength password, command
		RegisterController.checkPasswordRegex password, command

		!RegisterController.passwordValidator(password, command)
	}

	void 'passwordValidator validation fails if an updated regex does not match'() {
		when:
		addRegisterEmailSubjectToMessageSource()
		def command = [username: 'username']
		String password = 'h!Z7abcd'
		SpringSecurityUtils.securityConfig.ui.password.validationRegex = '^.*s3cr3t.*$'
		updateFromConfig()

		then:
		RegisterController.checkPasswordMinLength password, command
		RegisterController.checkPasswordMaxLength password, command
		!RegisterController.checkPasswordRegex(password, command)

		'command.password.error.strength' == RegisterController.passwordValidator(password, command)

		when:
		password = '123_s3cr3t_asd'

		then:
		RegisterController.checkPasswordMinLength password, command
		RegisterController.checkPasswordMaxLength password, command
		RegisterController.checkPasswordRegex password, command

		!RegisterController.passwordValidator(password, command)
	}

	void "verify generateLink for ('#action', #linkParams, '#shouldUseServerUrl') is #expectedUrl"() {
        given: "the grails.serverUrl is set"
        if (isConfigured) {
			controller.serverURL = 'https://grails.org'
        }

		and: "the email subject exist in i18 messages"
		addRegisterEmailSubjectToMessageSource()

        when: "the generateLink method is called"
        def results = controller.generateLink(action, linkParams, shouldUseServerUrl)

        then: "the configured grails.serverUrl is used if the absolute parameter is true"
        results == expectedUrl

        where:
        isConfigured | shouldUseServerUrl | action   | linkParams               | expectedUrl
        false        | false              | 'shipit' | [foo: 'foo', bar: 'bar'] | 'http://localhost:80/register/shipit?foo=foo&bar=bar'
        false        | true               | 'shipit' | [foo: 'foo', bar: 'bar'] | 'http://localhost:80/register/shipit?foo=foo&bar=bar'
        true         | true               | 'shipit' | [foo: 'foo', bar: 'bar'] | 'https://grails.org/register/shipit?foo=foo&bar=bar'
    }

	void "register email subject is loaded from config if config exists"() {
		given:
		addRegisterEmailSubjectToMessageSource()
		if(hasConfig) {
			SpringSecurityUtils.securityConfig.ui.register.emailSubject = 'This is from the config'
		}
		updateFromConfig()

		when:
		String results = controller.registerEmailSubject

		then:
		results == expectedResults

		where:
		hasConfig | expectedResults
		true      | 'This is from the config'
		false     | 'New Account'
	}

	private void updateFromConfig() {
		controller.messageSource = messageSource
		controller.afterPropertiesSet()
	}

	protected void addRegisterEmailSubjectToMessageSource() {
		messageSource.addMessage 'spring.security.ui.register.email.subject', Locale.US, 'New Account'
	}
}
