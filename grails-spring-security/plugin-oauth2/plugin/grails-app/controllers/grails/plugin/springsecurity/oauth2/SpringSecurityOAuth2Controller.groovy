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
package grails.plugin.springsecurity.oauth2

import com.github.scribejava.core.model.OAuth2AccessToken
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.oauth2.exception.OAuth2Exception
import grails.plugin.springsecurity.oauth2.token.OAuth2SpringToken
import grails.plugin.springsecurity.userdetails.GrailsUser
import groovy.util.logging.Slf4j
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang.exception.ExceptionUtils
import org.apache.commons.validator.routines.UrlValidator
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.ModelAndView

/**
 * Controller for handling OAuth authentication request and
 * integrating it into SpringSecurity
 *
 * Based on SpringSecurityOAuthController:2.1.0.RC4
 */
@Secured('permitAll')
@Slf4j
class SpringSecurityOAuth2Controller {

    public static final String SPRING_SECURITY_OAUTH_TOKEN = 'springSecurityOAuthToken'

    SpringSecurityOauth2BaseService springSecurityOauth2BaseService
    SpringSecurityService springSecurityService

    /**
     * Authenticate
     */
    def authenticate() {
        String providerName = params.provider
        if (StringUtils.isBlank(providerName)) {
            throw new OAuth2Exception("No provider defined")
        }
        log.debug "authenticate ${providerName}"
        String url = springSecurityOauth2BaseService.getAuthorizationUrl(providerName)
        log.debug "redirect url from s2oauthservice=${url}"
        if (!UrlValidator.instance.isValid(url)) {
            flash.message = "Authorization url for provider '${providerName}' is invalid."
            redirect(controller: 'login', action: 'index')
        }
        redirect(url: url)
    }

    /**
     * Default callback function for first OAuth2 Step
     */
    def callback() {
        String providerName = params.provider
        log.debug("Callback for " + providerName)

        // Check if we got an AuthCode from the server query
        String authCode = params.code
        log.debug("AuthCode: " + authCode)
        if (!authCode || authCode.isEmpty()) {
            throw new OAuth2Exception("No AuthCode in callback for provider '${providerName}'")
        }

        def providerService = springSecurityOauth2BaseService.getProviderService(providerName)
        OAuth2AccessToken accessToken
        try {
            accessToken = providerService.getAccessToken(authCode)
        } catch (Exception exception) {
            log.error("Could not authenticate with oAuth2. " + ExceptionUtils.getMessage(exception), exception)
            log.debug(ExceptionUtils.getStackTrace(exception))
            redirect(uri: springSecurityOauth2BaseService.getFailureUrl(providerName))
            return
        }
        session[springSecurityOauth2BaseService.sessionKeyForAccessToken(providerName)] = accessToken
        redirect(uri: springSecurityOauth2BaseService.getSuccessUrl(providerName))
    }

    def onFailure(String provider) {
        flash.error = "Error authenticating with ${provider}"
        log.warn("Error authentication with OAuth2Provider ${provider}")
        authenticateAndRedirect(null, getDefaultTargetUrl())
    }

    def onSuccess(String provider) {
        if (!provider) {
            log.warn "The Spring Security OAuth callback URL must include the 'provider' URL parameter"
            throw new OAuth2Exception("The Spring Security OAuth callback URL must include the 'provider' URL parameter")
        }
        def sessionKey = springSecurityOauth2BaseService.sessionKeyForAccessToken(provider)
        if (!session[sessionKey]) {
            log.warn "No OAuth token in the session for provider '${provider}'"
            throw new OAuth2Exception("Authentication error for provider '${provider}'")
        }
        // Create the relevant authentication token and attempt to log in.
        OAuth2SpringToken oAuthToken = springSecurityOauth2BaseService.createAuthToken(provider, session[sessionKey])

        if (oAuthToken.principal instanceof GrailsUser) {
            authenticateAndRedirect(oAuthToken, getDefaultTargetUrl())
        } else {
            // This OAuth account hasn't been registered against an internal
            // account yet. Give the oAuthID the opportunity to create a new
            // internal account or link to an existing one.
            session[SPRING_SECURITY_OAUTH_TOKEN] = oAuthToken

            def redirectUrl = springSecurityOauth2BaseService.getAskToLinkOrCreateAccountUri()
            if (!redirectUrl) {
                log.warn "grails.plugin.springsecurity.oauth.registration.askToLinkOrCreateAccountUri configuration option must be set"
                throw new OAuth2Exception('Internal error')
            }
            log.debug "Redirecting to askToLinkOrCreateAccountUri: ${redirectUrl}"
            redirect(redirectUrl instanceof Map ? redirectUrl : [uri: redirectUrl])
        }
    }

    def ask() {
        if (springSecurityService.isLoggedIn()) {
            def currentUser = springSecurityService.currentUser
            OAuth2SpringToken oAuth2SpringToken = session[SPRING_SECURITY_OAUTH_TOKEN] as OAuth2SpringToken
            // Check for token in session
            if (!oAuth2SpringToken) {
                log.warn("ask: OAuthToken not found in session")
                throw new OAuth2Exception('Authentication error')
            }
            // Try to add the token to the OAuthID's
            currentUser.addTooAuthIDs(
                    provider: oAuth2SpringToken.providerName,
                    accessToken: oAuth2SpringToken.socialId,
                    user: currentUser
            )
            if (currentUser.validate() && currentUser.save()) {
                // Could assign the token to the OAuthIDs. Login and redirect
                oAuth2SpringToken = springSecurityOauth2BaseService.updateOAuthToken(oAuth2SpringToken, currentUser)
                authenticateAndRedirect(oAuth2SpringToken, getDefaultTargetUrl())
                return
            }
        }
        // There seems to be a new one in the town aka 'There is no one logged in'
        // Ask to create a new account or link an existing user to it
        return new ModelAndView("/springSecurityOAuth2/ask", [:])
    }

    /**
     * Associates an OAuthID with an existing account. Needs the user's password to ensure
     * that the user owns that account, and authenticates to verify before linking.
     */
    def linkAccount(OAuth2LinkAccountCommand command) {
        OAuth2SpringToken oAuth2SpringToken = session[SPRING_SECURITY_OAUTH_TOKEN] as OAuth2SpringToken
        if (!oAuth2SpringToken) {
            log.warn "linkAccount: OAuthToken not found in session"
            throw new OAuth2Exception('Authentication error')
        }
        if (request.post) {
            if (!springSecurityOauth2BaseService.authenticationIsValid(command.username, command.password)) {
                log.info "Authentication error for use ${command.username}"
                command.errors.rejectValue("username", "OAuthLinkAccountCommand.authentication.error")
                render view: 'ask', model: [linkAccountCommand: command]
                return
            }
            def commandValid = command.validate()
            def User = springSecurityOauth2BaseService.lookupUserClass()
            boolean linked = commandValid && User.withTransaction { status ->
                def user = User.findByUsername(command.username)
                if (user) {
                    user.addTooAuthIDs(provider: oAuth2SpringToken.providerName, accessToken: oAuth2SpringToken.socialId, user: user)
                    if (user.validate() && user.save()) {
                        oAuth2SpringToken = springSecurityOauth2BaseService.updateOAuthToken(oAuth2SpringToken, user)
                        return true
                    } else {
                        return false
                    }
                } else {
                    command.errors.rejectValue("username", "OAuthLinkAccountCommand.username.not.exists")
                }
                status.setRollbackOnly()
                return false
            }
            if (linked) {
                authenticateAndRedirect(oAuth2SpringToken, getDefaultTargetUrl())
                return
            }
        }
        render view: 'ask', model: [linkAccountCommand: command]
    }

    /**
     * Create ne account and associate it with the oAuthID
     */
    def createAccount(OAuth2CreateAccountCommand command) {
        OAuth2SpringToken oAuth2SpringToken = session[SPRING_SECURITY_OAUTH_TOKEN] as OAuth2SpringToken
        if (!oAuth2SpringToken) {
            log.warn "createAccount: OAuthToken not found in session"
            throw new OAuth2Exception('Authentication error')
        }
        if (request.post) {
            if (!springSecurityService.loggedIn) {
                def commandValid = command.validate()
                def User = springSecurityOauth2BaseService.lookupUserClass()
                boolean created = commandValid && User.withTransaction { status ->
                    def user = springSecurityOauth2BaseService.lookupUserClass().newInstance()
                    user.username = command.username
                    user.password = command.password1
                    user.enabled = true
                    user.addTooAuthIDs(provider: oAuth2SpringToken.providerName, accessToken: oAuth2SpringToken.socialId, user: user)
                    if (!user.validate() || !user.save()) {
                        status.setRollbackOnly()
                        false
                    }
                    def UserRole = springSecurityOauth2BaseService.lookupUserRoleClass()
                    def Role = springSecurityOauth2BaseService.lookupRoleClass()
                    def roles = springSecurityOauth2BaseService.roleNames
                    for (roleName in roles) {
                        log.debug("Creating role " + roleName + " for user " + user.username)
                        // Make sure that the role exists.
                        UserRole.create user, Role.findOrSaveByAuthority(roleName)
                    }
                    // make sure that the new roles are effective immediately
                    springSecurityService.reauthenticate(user.username)
                    oAuth2SpringToken = springSecurityOauth2BaseService.updateOAuthToken(oAuth2SpringToken, user)
                    true
                }
                if (created) {
                    authenticateAndRedirect(oAuth2SpringToken, getDefaultTargetUrl())
                    return
                }
            }
        }
        render view: 'ask', model: [createAccountCommand: command]
    }

    /**
     * Set authentication token and redirect to the page we came from
     * @param oAuthToken
     * @param redirectUrl
     */
    protected void authenticateAndRedirect(OAuth2SpringToken oAuthToken, redirectUrl) {
        session.removeAttribute SPRING_SECURITY_OAUTH_TOKEN
        SecurityContextHolder.context.authentication = oAuthToken
        redirect(redirectUrl instanceof Map ? redirectUrl : [uri: redirectUrl])
    }

    /**
     * Get default Url
     * @return
     */
    protected Map getDefaultTargetUrl() {
        def config = SpringSecurityUtils.securityConfig
        def savedRequest = SpringSecurityUtils.getSavedRequest(session)
        def defaultUrlOnNull = '/'
        if (savedRequest && !config.successHandler.alwaysUseDefault) {
            return [url: (savedRequest.redirectUrl ?: defaultUrlOnNull)]
        }
        return [uri: (config.successHandler.defaultTargetUrl ?: defaultUrlOnNull)]
    }
}

/**
 * A bean for storing createAccount values
 */
class OAuth2CreateAccountCommand {

    def springSecurityOauth2BaseService

    String username
    String password1
    String password2

    static constraints = {
        username blank: false, minSize: 3, validator: { String username, command ->
            if (command.springSecurityOauth2BaseService.usernameTaken(username)) {
                return 'OAuthCreateAccountCommand.username.error.unique'
            }
        }
        password1 blank: false, minSize: 8, maxSize: 64, validator: { password1, command ->
            if (command.username && command.username == password1) {
                return 'OAuthCreateAccountCommand.password.error.username'
            }

            if (password1 && password1.length() >= 8 && password1.length() <= 64 &&
                    (!password1.matches('^.*\\p{Alpha}.*$') ||
                            !password1.matches('^.*\\p{Digit}.*$') ||
                            !password1.matches('^.*[!@#$%^&].*$'))) {
                return 'OAuthCreateAccountCommand.password.error.strength'
            }
        }
        password2 nullable: true, blank: true, validator: { password2, command ->
            if (command.password1 != password2) {
                return 'OAuthCreateAccountCommand.password.error.mismatch'
            }
        }
    }
}

/**
 * A bean for storing link account commands
 */
class OAuth2LinkAccountCommand {
    String username
    String password

    static constraints = {
        username blank: false
        password blank: false
    }
}
