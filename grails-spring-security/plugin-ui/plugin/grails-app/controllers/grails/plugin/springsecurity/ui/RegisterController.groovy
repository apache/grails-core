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

import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import grails.gsp.PageRenderer
import grails.plugin.springsecurity.ui.strategy.MailStrategy
import grails.plugin.springsecurity.ui.strategy.PropertiesStrategy
import grails.plugin.springsecurity.ui.strategy.RegistrationCodeStrategy
import groovy.text.SimpleTemplateEngine
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class RegisterController extends AbstractS2UiController implements GrailsConfigurationAware {

	static defaultAction = 'register'


	/** Dependency injection for the 'uiMailStrategy' bean. */
	MailStrategy uiMailStrategy

	/** Dependency injection for the 'uiRegistrationCodeStrategy' bean. */
	RegistrationCodeStrategy uiRegistrationCodeStrategy

	/** Dependency injection for the 'uiPropertiesStrategy' bean. */
	PropertiesStrategy uiPropertiesStrategy

	String serverURL

	PageRenderer groovyPageRenderer
	MessageSource messageSource

	static final String EMAIL_LAYOUT = "/layouts/email"
	static final String FORGOT_PASSWORD_TEMPLATE = "/register/_forgotPasswordMail"
	static final String VERIFY_REGISTRATION_TEMPLATE = "/register/_verifyRegistrationMail"

	@Override
	void setConfiguration(Config co) {
		serverURL = co.getProperty('grails.serverURL', String)
	}

	def register(RegisterCommand registerCommand) {

		if (!request.post) {
			return [registerCommand: new RegisterCommand()]
		}

		if (registerCommand.hasErrors()) {
			return [registerCommand: registerCommand]
		}

		def user = uiRegistrationCodeStrategy.createUser(registerCommand)

		RegistrationCode registrationCode = uiRegistrationCodeStrategy.register(user, registerCommand.password)

		if (registrationCode == null || registrationCode.hasErrors()) {
			// null means problem creating the user
			flash.error = message(code: 'spring.security.ui.register.miscError')
			return [registerCommand: registerCommand]
		}



		if( requireEmailValidation  ) {
			sendVerifyRegistrationMail registrationCode, user, registerCommand.email
			[emailSent: true, registerCommand: registerCommand]
		} else {
			redirectVerifyRegistration(uiRegistrationCodeStrategy.verifyRegistration(registrationCode.token))
		}
	}

	protected void redirectVerifyRegistration(def rtn) {
		if (rtn?.flashmsg) {
			if(rtn?.flashType == 'error') {
				flash.error =  message(code: rtn?.flashmsg)
			} else {
				flash.message = message(code:rtn?.flashmsg)
			}
		}

		//this should always be set but if not have a backup!
		redirect uri: rtn?.redirectmsg ?: successHandlerDefaultTargetUrl
	}

    protected void sendVerifyRegistrationMail(RegistrationCode registrationCode, user, String email) {
        String url = generateLink('verifyRegistration', [t: registrationCode.token])
        String body = renderRegistrationMailBody(url, user)

        uiMailStrategy.sendVerifyRegistrationMail(
                to: email,
                from: registerEmailFrom,
                subject: registerEmailSubject,
                html: body
        )
    }

	/**
	 * Render the email body using text from DefaultUiSecurityConfig if it exists.  If not, render using gsps
	 * @param url
	 * @param user
	 * @return html mail goodness
	 */
	protected String renderRegistrationMailBody(String url, user) {
		if (registerEmailBody) {
			def body = registerEmailBody
			if (body.contains('$')) {
				body = evaluate(body, [user: user, url: url])
			}
			return body.toString()
		}

		renderEmail(VERIFY_REGISTRATION_TEMPLATE, EMAIL_LAYOUT, [
				url     : url,
				username: user.username
		])
	}

	def verifyRegistration() {
		redirectVerifyRegistration(uiRegistrationCodeStrategy.verifyRegistration(params.t))
	}

	def forgotPassword(ForgotPasswordCommand forgotPasswordCommand) {

			if (!request.post) {
				ForgotPasswordCommand fpc = new ForgotPasswordCommand()
				return [forgotPasswordCommand: fpc]
			}
		withForm {
			if (forgotPasswordCommand.hasErrors()) {

				return [forgotPasswordCommand: forgotPasswordCommand]
			}

			def user = findUserByUsername(forgotPasswordCommand.username)
			if (!user) {
				forgotPasswordCommand.errors.rejectValue 'username', 'spring.security.ui.forgotPassword.user.notFound'

				return [forgotPasswordCommand: forgotPasswordCommand]
			}

			if (forgotPasswordExtraValidation && forgotPasswordExtraValidation.size() > 0 && forgotPasswordExtraValidationDomainClassName ) {
				redirect uri: generateLink('securityQuestions', [username: forgotPasswordCommand.username])
			} else {
				if (requireForgotPassEmailValidation) {
					processForgotPasswordEmail(forgotPasswordCommand, user)
				} else {
					redirect uri: processForgotPasswordEmail(forgotPasswordCommand, user)
				}
			}
		}.invalidToken {
			flash.message = "Invalid Form Submission"
			redirect(controller: "login", action: "auth")
		}
	}


	def securityQuestions(){
		if (forgotPasswordExtraValidation && forgotPasswordExtraValidation.size() > 0) {
				def user = findUserByUsername(params.username)
				SecurityQuestionsCommand sc = new SecurityQuestionsCommand()
				try {
					sc.username = params.username
					if (!request.post) {
						render(view:"securityQuestions", model: [securityQuestionsCommand:sc,user:user,forgotPasswordExtraValidation:forgotPasswordExtraValidation,forgotPasswordExtraValidationDomainClassName:forgotPasswordExtraValidationDomainClassName,validationUserLookUpProperty:validationUserLookUpProperty])
					} else {
						withForm {
							def rtn = uiRegistrationCodeStrategy.validateForgotPasswordExtraSecurity(params, user,forgotPasswordExtraValidationDomainClassName, forgotPasswordExtraValidation, validationUserLookUpProperty)
							if (!rtn[0]) {
								sc.validations = rtn[1]
								render(view: "securityQuestions", model: [securityQuestionsCommand: sc, user: user, forgotPasswordExtraValidation: forgotPasswordExtraValidation,forgotPasswordExtraValidationDomainClassName:forgotPasswordExtraValidationDomainClassName, validationUserLookUpProperty: validationUserLookUpProperty])
							} else {
								ForgotPasswordCommand fc = new ForgotPasswordCommand()
								fc.username = sc.username
								if (requireForgotPassEmailValidation) {
									render(view: "forgotPassword", processForgotPasswordEmail(fc, user))
								} else {
									redirect uri: processForgotPasswordEmail(fc, user)
								}
							}
						}.invalidToken {
							flash.message = "Invalid Form Submission"
							redirect(controller: "login", action: "auth")
						}
					}
				} catch (Exception e) {
					flash.error = e.getMessage()
					render(view: "securityQuestions", model: [securityQuestionsCommand: sc,user: user, forgotPasswordExtraValidation: forgotPasswordExtraValidation,forgotPasswordExtraValidationDomainClassName:forgotPasswordExtraValidationDomainClassName, validationUserLookUpProperty: validationUserLookUpProperty])
				}
			} else {
				redirect uri: generateLink('register')
			}
		}


	protected def processForgotPasswordEmail(forgotPasswordCommand,user){

		if(requireForgotPassEmailValidation) {
            String email = uiPropertiesStrategy.getProperty(user, 'email')
			if (!email) {
				forgotPasswordCommand.errors.rejectValue 'username', 'spring.security.ui.forgotPassword.noEmail'
				return [forgotPasswordCommand: forgotPasswordCommand]
			}
			uiRegistrationCodeStrategy.sendForgotPasswordMail(
					forgotPasswordCommand.username, email) { String registrationCodeToken ->

				String url = generateLink('resetPassword', [t: registrationCodeToken])
				String body = forgotPasswordEmailBody

				if (!body) {
					body = renderEmail(
							FORGOT_PASSWORD_TEMPLATE, EMAIL_LAYOUT,
							[
									url     : url,
									username: user.username
							]
					)
				} else if (body.contains('$')) {
					body = evaluate(body, [user: user, url: url])
				}

				body
			}
			[emailSent: true, forgotPasswordCommand: forgotPasswordCommand]
		} else {
			return generateLink('resetPassword', [t: uiRegistrationCodeStrategy.sendForgotPasswordMail(forgotPasswordCommand.username)?.token ])
		}
	}


	private String renderEmail(String viewPath, String layoutPath, Map model) {
		String content = groovyPageRenderer.render(view: viewPath, model: model)
		return groovyPageRenderer.render(view: layoutPath, model: model << [content: content])
	}

	def resetPassword(ResetPasswordCommand resetPasswordCommand) {

		String token = params.t

		def registrationCode = token ? RegistrationCode.findByToken(token) : null
		if (!registrationCode) {
			flash.error = message(code: 'spring.security.ui.resetPassword.badCode')
			redirect uri: successHandlerDefaultTargetUrl
			return
		}

		if (!request.post) {
			return [token: token, resetPasswordCommand: new ResetPasswordCommand()]
		}

		resetPasswordCommand.username = registrationCode.username
		resetPasswordCommand.validate()
		if (resetPasswordCommand.hasErrors()) {
			return [token: token, resetPasswordCommand: resetPasswordCommand]
		}

		def user = uiRegistrationCodeStrategy.resetPassword(resetPasswordCommand, registrationCode)
		if (user.hasErrors()) {
			// expected to be handled already by ErrorsStrategy.handleValidationErrors
		}

		flash.message = message(code: 'spring.security.ui.resetPassword.success')

		redirect uri: registerPostResetUrl ?: successHandlerDefaultTargetUrl
	}

	/**
	 * Creates a grails application link from a set of attributes.
	 * @param action
	 * @param linkParams
	 * @param shouldUseServerUrl (optional) - If true, will utilize the configured grails.serverURL from application.yml if it exists otherwise the base url will be constructed the same as it always has been
	 * @return String representing the relative or absolute URL
	 */
	protected String generateLink(String action, Map linkParams, boolean shouldUseServerUrl = false) {
		String base = "$request.scheme://$request.serverName:$request.serverPort$request.contextPath"

		if (shouldUseServerUrl && serverURL) {
			base = serverURL
		}

		createLink(
				base: base,
				controller: 'register',
				action: action,
				params: linkParams)
	}

	protected String evaluate(s, binding) {
		new SimpleTemplateEngine().createTemplate(s).make(binding)
	}

	protected String forgotPasswordEmailBody
	protected Boolean requireForgotPassEmailValidation
	protected List<HashMap> forgotPasswordExtraValidation
    protected String forgotPasswordExtraValidationDomainClassName
	protected String registerEmailBody
	protected String registerEmailFrom
	protected String registerEmailSubject
	protected String registerPostRegisterUrl
	protected String registerPostResetUrl
	protected String successHandlerDefaultTargetUrl
	protected Boolean requireEmailValidation
	protected static int passwordMaxLength
	protected String validationUserLookUpProperty
	protected static int passwordMinLength
	protected static String passwordValidationRegex

	void afterPropertiesSet() {
		super.afterPropertiesSet()

		RegisterCommand.User = User
		RegisterCommand.usernamePropertyName = usernamePropertyName

		forgotPasswordEmailBody = conf.ui.forgotPassword.emailBody ?: ''
		requireForgotPassEmailValidation = conf.ui.forgotPassword.requireForgotPassEmailValidation instanceof groovy.util.ConfigObject ? true : Boolean.valueOf(conf.ui.forgotPassword.requireForgotPassEmailValidation)
		forgotPasswordExtraValidation = conf.ui.forgotPassword.forgotPasswordExtraValidation  ?: []
        forgotPasswordExtraValidationDomainClassName = (conf.ui.forgotPassword.forgotPasswordExtraValidationDomainClassName ?: '').toString().trim()
		registerEmailBody = conf.ui.register.emailBody ?: ''
		registerEmailFrom = conf.ui.register.emailFrom ?: ''
		validationUserLookUpProperty = conf.ui.forgotPassword.validationUserLookUpProperty ?: 'user'
		registerEmailSubject = conf.ui.register.emailSubject ?: messageSource ? messageSource.getMessage('spring.security.ui.register.email.subject', [].toArray(), 'New Account', LocaleContextHolder.locale) : '' ?: ''
		registerPostRegisterUrl = conf.ui.register.postRegisterUrl ?: ''
		registerPostResetUrl = conf.ui.forgotPassword.postResetUrl ?: ''
		successHandlerDefaultTargetUrl = conf.successHandler.defaultTargetUrl ?: '/'
		requireEmailValidation = conf.ui.register.requireEmailValidation instanceof groovy.util.ConfigObject ? true : Boolean.valueOf(conf.ui.register.requireEmailValidation)
		passwordMaxLength = conf.ui.password.maxLength instanceof Number ? conf.ui.password.maxLength : 64
		passwordMinLength = conf.ui.password.minLength instanceof Number ? conf.ui.password.minLength : 8
		passwordValidationRegex = conf.ui.password.validationRegex ?: '^.*(?=.*\\d)(?=.*[a-zA-Z])(?=.*[!@#$%^&]).*$'
	}

	static final passwordValidator = { String password, command ->
		if (command.username && command.username.equals(password)) {
			return 'command.password.error.username'
		}

		if (!checkPasswordMinLength(password, command) || !checkPasswordMaxLength(password, command)) {
			return ['command.password.error.length', passwordMinLength, passwordMaxLength]
		}
		if (!checkPasswordRegex(password, command)) {
			return 'command.password.error.strength'
		}
	}

	static boolean checkPasswordMinLength(String password, command) {
		password && password.length() >= passwordMinLength
	}

	static boolean checkPasswordMaxLength(String password, command) {
		password && password.length() <= passwordMaxLength
	}

	static boolean checkPasswordRegex(String password, command) {
		password && password.matches(passwordValidationRegex)
	}

	static final password2Validator = { value, command ->
		if (command.password != command.password2) {
			return 'command.password2.error.mismatch'
		}
	}
}