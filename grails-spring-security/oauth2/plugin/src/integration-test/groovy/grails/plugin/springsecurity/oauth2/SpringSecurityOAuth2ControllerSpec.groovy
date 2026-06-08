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
import grails.plugin.springsecurity.oauth2.exception.OAuth2Exception
import grails.plugin.springsecurity.oauth2.token.OAuth2SpringToken
import grails.testing.mixin.integration.Integration
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Always code as if the guy who ends up maintaining your code
 * will be a violent psychopath that knows where you live.
 *
 * - John Woods
 *
 * Created on 21.06.2016
 * @author MatrixCrawler
 */
@Integration
class SpringSecurityOAuth2ControllerSpec extends Specification implements ControllerUnitTest<SpringSecurityOAuth2Controller> {

	@Unroll
	void "Registration command objects for #loginId validating correctly"() {

		given: "a command object"
		OAuth2CreateAccountCommand createAccountCommand = new OAuth2CreateAccountCommand()
		createAccountCommand.springSecurityOauth2BaseService = [usernameTaken: { u -> false }]

		and: "a set of initial values from the spock test"
		createAccountCommand.username = loginId
		createAccountCommand.password1 = password
		createAccountCommand.password2 = passwordRepeat

		when: "the validator is invoked"
		def isValidRegistration = createAccountCommand.validate()

		then: "the appropriate fields are flagged as errors"
		isValidRegistration == anticipatedValid
		createAccountCommand.errors.getFieldError(fieldInError)?.code == errorCode

		where:
		loginId | password   | passwordRepeat | anticipatedValid | fieldInError | errorCode
		"glen"  | "password" | "no-match"     | false            | "password2"  | "OAuthCreateAccountCommand.password.error.mismatch"
		"glen"  | "!QAZxsw"  | "!QAZxsw"      | false            | "password1"  | "minSize.notmet"
		"glen"  | "password" | "password"     | false            | "password1"  | "OAuthCreateAccountCommand.password.error.strength"
		"peter" | "!QAZxsw2" | "!QAZxsw2"     | true             | null         | null
		"a"     | "password" | "password"     | false            | "username"   | "minSize.notmet"

	}

	void "authenticate should throw exception if request is missing provider"() {
		given:
		params.provider = provider
		def springSecurityOauth2BaseService = Mock(SpringSecurityOauth2BaseService)
		controller.springSecurityOauth2BaseService = springSecurityOauth2BaseService

		when:
		controller.authenticate()

		then:
		thrown OAuth2Exception

		where:
		provider | _
		''       | _
		'  '     | _
		null     | _
	}

	void "onSuccess should throw exception if request is missing data"() {
		given:
		params.provider = provider
		def springSecurityOauth2BaseService = Mock(SpringSecurityOauth2BaseService)
		controller.springSecurityOauth2BaseService = springSecurityOauth2BaseService

		when:
		controller.onSuccess()

		then:
		thrown OAuth2Exception

		where:
		provider | _
		''       | _
		null     | _
	}

	void "onSuccess should throw exception if session is missing data"() {
		given:
		params.provider = provider
		def springSecurityOauth2BaseService = Mock(SpringSecurityOauth2BaseService)
		controller.springSecurityOauth2BaseService = springSecurityOauth2BaseService

		when:
		controller.onSuccess()

		then:
		1 * springSecurityOauth2BaseService.sessionKeyForAccessToken(provider) >> { 'OAuth2: access - t:' + provider }
		thrown OAuth2Exception

		where:
		provider   | _
		'facebook' | _
		'google'   | _
		'somewhat' | _
	}

	// now askToLinkOrCreateAccountUri is hardcoded, so this test is redundant
	void "onSuccess should throw exception if askToLinkOrCreateAccountUri is not set"() {
		given:
		OAuth2SpringToken authToken = Mock(OAuth2SpringToken)
		params.provider = provider
		def springSecurityOauth2BaseService = Mock(SpringSecurityOauth2BaseService)
		controller.springSecurityOauth2BaseService = springSecurityOauth2BaseService

		def sessionKey = 'OAuth2: access - t:' + provider

		def oAuth2AccessToken = Mock(OAuth2AccessToken)
		session[sessionKey] = oAuth2AccessToken

		when:
		controller.onSuccess()

		then:
		1 * springSecurityOauth2BaseService.sessionKeyForAccessToken(provider) >> { sessionKey }
		1 * springSecurityOauth2BaseService.createAuthToken(provider, session[sessionKey]) >> { authToken }
		1 * springSecurityOauth2BaseService.getAskToLinkOrCreateAccountUri() >> { null }
		thrown OAuth2Exception

		where:
		provider   | _
		'facebook' | _
	}

	void "onSuccess should redirect to askToLinkOrCreateAccountUri if the user is not logged in"() {
		given:
		OAuth2SpringToken authToken = Mock(OAuth2SpringToken)
		params.provider = provider
		def springSecurityOauth2BaseService = Mock(SpringSecurityOauth2BaseService)
		controller.springSecurityOauth2BaseService = springSecurityOauth2BaseService
		def sessionKey = 'OAuth2: access - t:' + provider
		springSecurityOauth2BaseService.sessionKeyForAccessToken(provider) >> { sessionKey }
		def oAuth2AccessToken = Mock(OAuth2AccessToken)
		session[sessionKey] = oAuth2AccessToken
		springSecurityOauth2BaseService.createAuthToken(provider, session[sessionKey]) >> { authToken }
		springSecurityOauth2BaseService.getAskToLinkOrCreateAccountUri() >> { "/askToLinkOrCreateAccountUri" }
		controller.springSecurityOauth2BaseService = springSecurityOauth2BaseService
		and:
		controller.onSuccess()
		expect:
		response.status == responseCode
		response.redirectedUrl == "/askToLinkOrCreateAccountUri"
		where:
		provider   | responseCode
		'facebook' | 302
	}

	void "onSuccess should redirect to defaultTargeturl if user is logged in"() {
		given:
		def token = Stub(OAuth2AccessToken) {
			getRawResponse() >> "a=1&b=2"
		}
		OAuth2SpringToken authToken = new TestOAuth2SpringToken(token, false)
		params.provider = provider
		def springSecurityOauth2BaseService = Mock(SpringSecurityOauth2BaseService)
		controller.springSecurityOauth2BaseService = springSecurityOauth2BaseService
		def sessionKey = 'OAuth2: access - t:' + provider
		springSecurityOauth2BaseService.sessionKeyForAccessToken(provider) >> { sessionKey }
		def oAuth2AccessToken = Mock(OAuth2AccessToken)
		session[sessionKey] = oAuth2AccessToken
		springSecurityOauth2BaseService.createAuthToken(provider, session[sessionKey]) >> { authToken }
		springSecurityOauth2BaseService.getAskToLinkOrCreateAccountUri() >> { "/ask" }
		controller.springSecurityOauth2BaseService = springSecurityOauth2BaseService
		controller.springSecurityOauth2BaseService = springSecurityOauth2BaseService
		and:
		controller.onSuccess()
		expect:
		response.status == responseCode
		response.redirectedUrl == controller.getDefaultTargetUrl().uri
		where:
		provider   | responseCode
		'facebook' | 302
	}

	void "linkAccount should return view if user is not logged in"() {
		given:
		def token = Stub(OAuth2AccessToken) {
			getRawResponse() >> "a=1&b=2"
		}
		OAuth2SpringToken authToken = new TestOAuth2SpringToken(token, false)
		session["springSecurityOAuthToken"] = authToken
		def springSecurityService = Mock(SpringSecurityService)
		springSecurityService.isLoggedIn() >> { false }
		controller.springSecurityService = springSecurityService
		when:
		controller.linkAccount()
		then:
		response
		view == '/springSecurityOAuth2/ask'
	}
}


