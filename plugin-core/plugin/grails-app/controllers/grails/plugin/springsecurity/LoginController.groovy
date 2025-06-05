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
package grails.plugin.springsecurity

import grails.converters.JSON
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.MessageSource
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.AccountExpiredException
import org.springframework.security.authentication.AuthenticationTrustResolver
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.WebAttributes
import org.springframework.security.web.authentication.session.SessionAuthenticationException

import jakarta.servlet.http.HttpServletResponse

@Secured('permitAll')
class LoginController {

	/** Dependency injection for the authenticationTrustResolver. */
	AuthenticationTrustResolver authenticationTrustResolver

	/** Dependency injection for the springSecurityService. */
	def springSecurityService

	/** Dependency injection for the messageSource. */
	MessageSource messageSource

	/** Default action; redirects to 'defaultTargetUrl' if logged in, /login/auth otherwise. */
	def index() {
		if (springSecurityService.isLoggedIn()) {
			redirect uri: conf.successHandler.defaultTargetUrl
		}
		else {
			redirect action: 'auth', params: params
		}
	}

	/** Show the login page. */
	def auth() {

		def conf = getConf()

		if (springSecurityService.isLoggedIn()) {
			redirect uri: conf.successHandler.defaultTargetUrl
			return
		}

		String postUrl = request.contextPath + conf.apf.filterProcessesUrl
		render view: 'auth', model: [postUrl: postUrl,
		                             rememberMeParameter: conf.rememberMe.parameter,
		                             usernameParameter: conf.apf.usernameParameter,
		                             passwordParameter: conf.apf.passwordParameter,
		                             gspLayout: conf.gsp.layoutAuth]
	}

	/** The redirect action for Ajax requests. */
	def authAjax() {
		response.setHeader 'Location', conf.auth.ajaxLoginFormUrl
		render(status: HttpServletResponse.SC_UNAUTHORIZED, text: 'Unauthorized')
	}

	/** Show denied page. */
	def denied() {
		if (springSecurityService.isLoggedIn() && authenticationTrustResolver.isRememberMe(authentication)) {
			// have cookie but the page is guarded with IS_AUTHENTICATED_FULLY (or the equivalent expression)
			redirect action: 'full', params: params
			return
		}

		[gspLayout: conf.gsp.layoutDenied]
	}

	/** Login page for users with a remember-me cookie but accessing a IS_AUTHENTICATED_FULLY page. */
	def full() {
		def conf = getConf()
		render view: 'auth', params: params,
		       model: [hasCookie: authenticationTrustResolver.isRememberMe(authentication),
		               postUrl: request.contextPath + conf.apf.filterProcessesUrl,
		               rememberMeParameter: conf.rememberMe.parameter,
		               usernameParameter: conf.apf.usernameParameter,
		               passwordParameter: conf.apf.passwordParameter,
		               gspLayout: conf.gsp.layoutAuth]
	}

    /** Callback after a failed login. Redirects to the auth page with a warning message. */
    def authfail() {

        String msg = ''
        def exception = session[WebAttributes.AUTHENTICATION_EXCEPTION]
        if (exception) {
            def locale = GrailsWebRequest.lookup().getLocale() ?: Locale.getDefault()
            if (exception instanceof AccountExpiredException) {
                msg = messageSource.getMessage('springSecurity.errors.login.expired', null, "Account Expired", locale)
            } else if (exception instanceof CredentialsExpiredException) {
                msg = messageSource.getMessage('springSecurity.errors.login.passwordExpired', null, "Password Expired", locale)
            } else if (exception instanceof DisabledException) {
                msg = messageSource.getMessage('springSecurity.errors.login.disabled', null, "Account Disabled", locale)
            } else if (exception instanceof LockedException) {
                msg = messageSource.getMessage('springSecurity.errors.login.locked', null, "Account Locked", locale)
            } else if (exception instanceof SessionAuthenticationException) {
                msg = messageSource.getMessage('springSecurity.errors.login.max.sessions.exceeded', null, "Sorry, you have exceeded your maximum number of open sessions.", locale)
            } else {
                msg = messageSource.getMessage('springSecurity.errors.login.fail', null, "Authentication Failure", locale)
            }
        }

        if (springSecurityService.isAjax(request)) {
            render([error: msg] as JSON)
        } else {
            flash.message = msg
            redirect action: 'auth', params: params
        }
    }

    /** The Ajax success redirect url. */
	def ajaxSuccess() {
		render([success: true, username: authentication.name] as JSON)
	}

	/** The Ajax denied redirect url. */
	def ajaxDenied() {
		render([error: 'access denied'] as JSON)
	}

	protected Authentication getAuthentication() {
		SecurityContextHolder.context?.authentication
	}

	protected ConfigObject getConf() {
		SpringSecurityUtils.securityConfig
	}
}
