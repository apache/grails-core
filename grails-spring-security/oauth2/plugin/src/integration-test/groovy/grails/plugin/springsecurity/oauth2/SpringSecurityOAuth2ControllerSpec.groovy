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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import com.github.scribejava.core.model.OAuth2AccessToken
import spock.lang.Specification
import spock.lang.Unroll

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.context.request.RequestContextHolder

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.servlet.mvc.SynchronizerTokensHolder

import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.oauth2.exception.OAuth2Exception
import grails.plugin.springsecurity.oauth2.service.OAuth2AbstractProviderService
import grails.plugin.springsecurity.oauth2.token.OAuth2SpringToken
import grails.testing.mixin.integration.Integration
import grails.testing.web.controllers.ControllerUnitTest

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

    OAuth2AbstractProviderService providerService

    void setup() {
        providerService = Mock(OAuth2AbstractProviderService)
        providerService.getSuccessUrl() >> '/provider-success'
        providerService.getFailureUrl() >> '/provider-failure'
        controller.springSecurityOauth2BaseService = new SpringSecurityOauth2BaseService(
                transactionManager: Stub(PlatformTransactionManager),
                providerServiceMap: [test: providerService])
    }

    void cleanup() {
        SecurityContextHolder.clearContext()
    }

    void 'authenticate stores the same secure state sent to the provider in this session'() {
        given:
        params.provider = 'test'
        providerService.getAuthUrl(_, [:]) >> { String state, Map extra ->
            "https://provider.example.com/authorize?state=${state}"
        }

        when:
        controller.authenticate()

        then:
        String state = session[controller.springSecurityOauth2BaseService.sessionKeyForState('test')]
        state ==~ /[A-Za-z0-9_-]{43}/
        Base64.urlDecoder.decode(state).length == 32
        response.redirectedUrl == "https://provider.example.com/authorize?state=${state}"
        !session[controller.springSecurityOauth2BaseService.sessionKeyForState('other')]
    }

    void 'authenticate stores a session lock and state that survive Java serialization'() {
        given:
        params.provider = 'test'
        providerService.getAuthUrl(_, [:]) >> { String requestedState, Map extra ->
            "https://provider.example.com/authorize?state=${requestedState}"
        }

        when:
        controller.authenticate()
        def nativeSession = GrailsWebRequest.lookup().currentRequest.getSession(true)
        String state = nativeSession.getAttribute(controller.springSecurityOauth2BaseService.sessionKeyForState('test'))
        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { output ->
            output.writeObject(nativeSession.getAttribute('OAuth2:state-lock'))
            output.writeObject(state)
        }
        def restored = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable { input ->
            [input.readObject(), input.readObject()]
        }

        then:
        noExceptionThrown()
        restored[0] != null
        state != null
        restored[1] == state
    }

    void 'callback rejects state #submittedState when this session holds #storedState'() {
        given:
        params.provider = 'test'
        params.code = 'authorization-code'
        params.state = submittedState
        String stateKey = controller.springSecurityOauth2BaseService.sessionKeyForState('test')
        session[stateKey] = storedState

        when:
        controller.callback()

        then:
        0 * providerService.getAccessToken(_)
        response.redirectedUrl == '/provider-failure'
        !session[controller.springSecurityOauth2BaseService.sessionKeyForAccessToken('test')]
        session[stateKey] == storedState

        where:
        submittedState | storedState
        'unsolicited'  | null
        'wrong-state'  | 'expected-state'
        null           | 'expected-state'
        ''             | 'expected-state'
        '   '          | 'expected-state'
    }

    void 'callback consumes matching state and rejects replay before exchanging the code'() {
        given:
        params.provider = 'test'
        params.code = 'authorization-code'
        params.state = 'expected-state'
        String stateKey = controller.springSecurityOauth2BaseService.sessionKeyForState('test')
        String tokenKey = controller.springSecurityOauth2BaseService.sessionKeyForAccessToken('test')
        session[stateKey] = 'expected-state'
        def token = new OAuth2AccessToken('access-token')

        when:
        controller.callback()

        then:
        1 * providerService.getAccessToken('authorization-code') >> token
        session[tokenKey].is(token)
        session[stateKey] == null
        response.redirectedUrl == '/provider-success'

        when:
        response.reset()
        session.removeAttribute(tokenKey)
        controller.callback()

        then:
        0 * providerService.getAccessToken(_)
        session[tokenKey] == null
        response.redirectedUrl == '/provider-failure'
    }

    void 'concurrent #competingAction shares the state lock across distinct request contexts'() {
        given:
        def baseService = controller.springSecurityOauth2BaseService
        String stateKey = baseService.sessionKeyForState('test')
        def nativeSession = new PausingStateSession(stateKey: stateKey)
        nativeSession.setAttribute(stateKey, 'expected-state')
        def contexts = (1..2).collect {
            def servletRequest = new MockHttpServletRequest(servletContext)
            servletRequest.setSession(nativeSession)
            new GrailsWebRequest(servletRequest, new MockHttpServletResponse(), servletContext, applicationContext)
        }
        assert !contexts[0].session.is(contexts[1].session)
        assert contexts.every { it.currentRequest.getSession(true).is(nativeSession) }
        def errors = new ConcurrentLinkedQueue<Throwable>()
        def competitorFinished = new CountDownLatch(1)
        def competitorStarted = new CountDownLatch(1)
        Closure runAction = { GrailsWebRequest context, String action ->
            RequestContextHolder.setRequestAttributes(context)
            try {
                context.params.putAll(provider: 'test', code: 'authorization-code', state: 'expected-state')
                def requestController = new SpringSecurityOAuth2Controller(springSecurityOauth2BaseService: baseService)
                requestController."${action}"()
            } catch (Throwable failure) {
                errors.add(failure)
            } finally {
                RequestContextHolder.resetRequestAttributes()
                SecurityContextHolder.clearContext()
            }
        }
        def first = new Thread({ runAction(contexts[0], 'callback') } as Runnable)
        def second = new Thread({
            competitorStarted.countDown()
            try {
                runAction(contexts[1], competingAction)
            } finally {
                competitorFinished.countDown()
            }
        } as Runnable)
        providerService.getAuthUrl(_, [:]) >> { String state, Map extra ->
            "https://provider.example.com/authorize?state=${state}"
        }

        when:
        first.start()
        assert nativeSession.stateRead.await(10, TimeUnit.SECONDS)
        second.start()
        assert competitorStarted.await(10, TimeUnit.SECONDS)
        // Observe actual monitor contention or completion, without a scheduling delay.
        def threadBean = ManagementFactory.threadMXBean
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (second.alive && threadBean.getThreadInfo(second.id)?.lockOwnerId != first.id) {
            assert System.nanoTime() < deadline
            Thread.onSpinWait()
        }
        nativeSession.releaseRead.countDown()
        first.join(10000)
        second.join(10000)

        then:
        1 * providerService.getAccessToken('authorization-code') >> {
            if (competingAction == 'authenticate') {
                assert competitorFinished.await(10, TimeUnit.SECONDS)
            }
            new OAuth2AccessToken('access-token')
        }
        !first.alive
        !second.alive
        errors.empty
        URI.create(contexts[0].currentResponse.redirectedUrl).path == '/provider-success'
        if (competingAction == 'callback') {
            assert URI.create(contexts[1].currentResponse.redirectedUrl).path == '/provider-failure'
            assert nativeSession.getAttribute(stateKey) == null
        } else {
            String newState = nativeSession.getAttribute(stateKey)
            assert newState != 'expected-state'
            assert newState ==~ /[A-Za-z0-9_-]{43}/
            assert contexts[1].currentResponse.redirectedUrl == "https://provider.example.com/authorize?state=${newState}"
        }

        cleanup:
        nativeSession.releaseRead.countDown()
        first.join(10000)
        if (second.state != Thread.State.NEW) {
            second.join(10000)
        }

        where:
        competingAction << ['callback', 'authenticate']
    }

    void 'rejected callback follows the default failure route without logging out the current user'() {
        given:
        def authentication = UsernamePasswordAuthenticationToken.authenticated('existing-user', null, [])
        SecurityContextHolder.context.authentication = authentication
        controller.springSecurityOauth2BaseService.grailsApplication = grailsApplication
        config.merge([grails: [serverURL: 'https://application.example']])
        controller.springSecurityOauth2BaseService.providerServiceMap.test = Stub(OAuth2AbstractProviderService)
        params.provider = 'test'
        params.state = 'wrong-state'
        params.code = 'authorization-code'
        String stateKey = controller.springSecurityOauth2BaseService.sessionKeyForState('test')
        session[stateKey] = 'expected-state'

        when:
        controller.callback()

        then:
        response.redirectedUrl == 'https://application.example/oauth2/test/failure'

        when: 'the browser follows /oauth2/test/failure to onFailure'
        response.reset()
        controller.onFailure('test')

        then:
        response.redirectedUrl == '/'
        SecurityContextHolder.context.authentication.is(authentication)
        session[stateKey] == 'expected-state'
        0 * providerService.getAccessToken(_)
    }

    void 'ask GET offers explicit linking without modifying the logged-in user'() {
        given:
        request.method = 'GET'
        def user = Mock(LinkingUser)
        controller.springSecurityService = Stub(SpringSecurityService) {
            isLoggedIn() >> true
            getCurrentUser() >> user
        }
        session[SpringSecurityOAuth2Controller.SPRING_SECURITY_OAUTH_TOKEN] = Mock(OAuth2SpringToken)

        when:
        def result = controller.ask()

        then:
        result.viewName == '/springSecurityOAuth2/ask'
        result.model.linkCurrentAccount
        0 * user.addTooAuthIDs(_)
        0 * user.save()
    }

    void 'linkCurrentAccount rejects #method with #tokenKind CSRF token'() {
        given:
        request.method = method
        def user = Mock(LinkingUser)
        controller.springSecurityService = Stub(SpringSecurityService) {
            isLoggedIn() >> true
            getCurrentUser() >> user
        }
        session[SpringSecurityOAuth2Controller.SPRING_SECURITY_OAUTH_TOKEN] = Mock(OAuth2SpringToken)
        def holder = SynchronizerTokensHolder.store(session)
        String formUri = '/oauth2/ask'
        String token = holder.generateToken(formUri)
        params[SynchronizerTokensHolder.TOKEN_URI] = formUri
        params[SynchronizerTokensHolder.TOKEN_KEY] = tokenKind == 'valid' ? token : tokenKind == 'wrong' ? UUID.randomUUID().toString() : null

        when:
        controller.linkCurrentAccount()

        then:
        response.status == expectedStatus
        0 * user.addTooAuthIDs(_)
        0 * user.save()

        where:
        method | tokenKind | expectedStatus
        'GET'  | 'valid'   | 405
        'POST' | 'missing' | 403
        'POST' | 'wrong'   | 403
    }

    void 'linkCurrentAccount POST links only with a valid form token and logged-in user'() {
        given:
        request.method = 'POST'
        def user = Mock(LinkingUser)
        controller.springSecurityService = Stub(SpringSecurityService) {
            isLoggedIn() >> true
            getCurrentUser() >> user
        }
        def token = Mock(OAuth2SpringToken) {
            getProviderName() >> 'test'
            getSocialId() >> 'social-user'
        }
        session[SpringSecurityOAuth2Controller.SPRING_SECURITY_OAUTH_TOKEN] = token
        def baseService = Mock(SpringSecurityOauth2BaseService)
        controller.springSecurityOauth2BaseService = baseService
        String formUri = '/oauth2/ask'
        params[SynchronizerTokensHolder.TOKEN_URI] = formUri
        params[SynchronizerTokensHolder.TOKEN_KEY] = SynchronizerTokensHolder.store(session).generateToken(formUri)

        when:
        controller.linkCurrentAccount()

        then:
        1 * user.addTooAuthIDs([provider: 'test', accessToken: 'social-user', user: user])
        1 * user.validate() >> true
        1 * user.save() >> user
        1 * baseService.updateOAuthToken(token, user) >> token
        session[SpringSecurityOAuth2Controller.SPRING_SECURITY_OAUTH_TOKEN] == null
        SecurityContextHolder.context.authentication.is(token)
        response.redirectedUrl == '/'
    }

    void 'linkCurrentAccount POST cannot link an anonymous user even with a valid form token'() {
        given:
        request.method = 'POST'
        controller.springSecurityService = Mock(SpringSecurityService)
        session[SpringSecurityOAuth2Controller.SPRING_SECURITY_OAUTH_TOKEN] = Mock(OAuth2SpringToken)
        params[SynchronizerTokensHolder.TOKEN_URI] = '/oauth2/ask'
        params[SynchronizerTokensHolder.TOKEN_KEY] = SynchronizerTokensHolder.store(session).generateToken('/oauth2/ask')

        when:
        controller.linkCurrentAccount()

        then:
        response.status == 403
        0 * controller.springSecurityService.getCurrentUser()
    }

    void 'password linking POST still renders the form when authentication fails'() {
        given:
        request.method = 'POST'
        session[SpringSecurityOAuth2Controller.SPRING_SECURITY_OAUTH_TOKEN] = Mock(OAuth2SpringToken)
        def baseService = Mock(SpringSecurityOauth2BaseService)
        controller.springSecurityOauth2BaseService = baseService
        params.username = 'existing-user'
        params.password = 'wrong-password'

        when:
        controller.linkAccount()

        then:
        1 * baseService.authenticationIsValid('existing-user', 'wrong-password') >> false
        view == '/springSecurityOAuth2/ask'
        model.linkAccountCommand.errors.hasFieldErrors('username')
    }

    void 'createAccount rejects GET without creating an account'() {
        given:
        request.method = 'GET'

        when:
        controller.createAccount()

        then:
        response.status == 405
    }

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

    void "linkAccount rejects GET without linking"() {
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
        response.status == 405
    }

    static class LinkingUser {
        void addTooAuthIDs(Map identity) { }
        boolean validate() { true }
        Object save() { this }
    }

    static class PausingStateSession extends MockHttpSession {
        String stateKey
        final CountDownLatch stateRead = new CountDownLatch(1)
        final CountDownLatch releaseRead = new CountDownLatch(1)
        private final AtomicBoolean pauseRead = new AtomicBoolean(true)

        @Override
        Object getAttribute(String name) {
            Object value = super.getAttribute(name)
            if (name == stateKey && pauseRead.compareAndSet(true, false)) {
                stateRead.countDown()
                assert releaseRead.await(10, TimeUnit.SECONDS)
            }
            value
        }
    }
}


