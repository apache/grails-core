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
package grails.plugin.springsecurity.rest

import java.nio.charset.StandardCharsets

import groovy.transform.InheritConstructors

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import org.grails.config.yaml.YamlPropertySourceLoader
import org.pac4j.oauth.client.GitHubClient
import spock.lang.Issue
import spock.lang.Specification

import org.springframework.core.io.ByteArrayResource
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

import grails.plugin.springsecurity.rest.authentication.RestAuthenticationEventPublisher
import grails.plugin.springsecurity.rest.error.DefaultCallbackErrorHandler
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.jwt.AbstractJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.DefaultRSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.EncryptedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.rendering.AccessTokenJsonRenderer
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import grails.plugin.springsecurity.rest.token.storage.jwt.JwtTokenStorageService
import grails.testing.web.controllers.ControllerUnitTest

import static org.springframework.http.HttpStatus.*

@Issue("https://github.com/grails/grails-spring-security-rest/issues/237")
class RestOauthControllerSpec extends Specification implements TokenGeneratorSupport, ControllerUnitTest<RestOauthController> {

    private static final String ALLOWED_CALLBACK_ORIGINS = 'grails.plugin.springsecurity.rest.oauth.allowedCallbackOrigins'
    private static final String TOKEN_VALUE = 'token-value'

    /**
     * The frontend callback URL stored in config.groovy
     */
    private frontendCallbackBaseUrl = "http://config.com/welcome#token="

    def setup() {
        grailsApplication.config.merge(['grails.plugin.springsecurity.rest.oauth.frontendCallbackUrl': { String tokenValue ->
            frontendCallbackBaseUrl + tokenValue
        }])

        params.provider = "google"
    }

    def cleanup() {
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): null])
    }

    private void stubOauthFlow() {
        final client = new GitHubClient('key', 'secret').tap {
            callbackUrl = 'http://localhost/oauth/callback/google'
        }
        controller.restOauthService = Stub(RestOauthService) {
            getClient('google') >> client
            storeAuthentication('google', _) >> TOKEN_VALUE
        }
    }

    /**
     * Starts the OAuth flow with the given callback parameter, completes it at the provider callback and
     * returns the URL the browser is finally redirected to.
     */
    private String runOauthFlow(String callback) {
        params.callback = callback
        controller.authenticate()
        assert response.redirectedUrl.startsWith('https://github.com/login/oauth/authorize?')

        response.reset()
        controller.callback()
        response.redirectedUrl
    }

    private static String base64(String value) {
        value.getBytes(StandardCharsets.UTF_8).encodeBase64().toString()
    }

    void 'a callback URL of an allowed origin receives the token: #callback'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): ['https://frontend.example.com', 'http://localhost:8080']])
        stubOauthFlow()

        expect:
        runOauthFlow(callback) == expectedUrl

        where:
        callback                                                  || expectedUrl
        'https://frontend.example.com/welcome?token='             || 'https://frontend.example.com/welcome?token=token-value'
        'https://frontend.example.com/'                           || 'https://frontend.example.com/token-value'
        'https://frontend.example.com?token='                     || 'https://frontend.example.com?token=token-value'
        'https://frontend.example.com:443/welcome#token='         || 'https://frontend.example.com:443/welcome#token=token-value'
        'HTTPS://Frontend.Example.COM/welcome?token='             || 'HTTPS://Frontend.Example.COM/welcome?token=token-value'
        'http://localhost:8080/app/welcome?token='                || 'http://localhost:8080/app/welcome?token=token-value'
        base64('https://frontend.example.com/welcome?token=')     || 'https://frontend.example.com/welcome?token=token-value'
    }

    void 'a single allowed origin can be configured as a String'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): 'https://frontend.example.com'])
        stubOauthFlow()

        expect:
        runOauthFlow('https://frontend.example.com/welcome?token=') == 'https://frontend.example.com/welcome?token=token-value'
    }

    void 'characters outside US-ASCII in a callback URL are percent-encoded: #callback'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): ['https://frontend.example.com']])
        stubOauthFlow()

        expect:
        runOauthFlow(callback) == expectedUrl

        where:
        callback                                                     || expectedUrl
        'https://other.exampleį@frontend.example.com/?token='   || 'https://other.example%C4%AF@frontend.example.com/?token=token-value'
        'https://frontend.example.com/你好?token='           || 'https://frontend.example.com/%E4%BD%A0%E5%A5%BD?token=token-value'
        'https://frontend.example.com/welcome?name=é&token='    || 'https://frontend.example.com/welcome?name=%C3%A9&token=token-value'
        base64('https://frontend.example.com/你好?token=')   || 'https://frontend.example.com/%E4%BD%A0%E5%A5%BD?token=token-value'
    }

    void 'characters outside US-ASCII in a callback URL from the session are percent-encoded'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): ['https://frontend.example.com']])
        stubOauthFlow()
        session[controller.CALLBACK_ATTR] = 'https://other.exampleį@frontend.example.com/?token='

        when:
        controller.callback()

        then:
        response.redirectedUrl == 'https://other.example%C4%AF@frontend.example.com/?token=token-value'
    }

    void 'a host outside US-ASCII matches only in its ASCII form: #allowedOrigin and #callback'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): [allowedOrigin]])
        stubOauthFlow()

        expect:
        runOauthFlow(callback) == expectedUrl

        where:
        allowedOrigin                   | callback                                       || expectedUrl
        'https://xn--bcher-kva.example' | 'https://xn--bcher-kva.example/welcome?token=' || 'https://xn--bcher-kva.example/welcome?token=token-value'
        'https://xn--bcher-kva.example' | 'https://bücher.example/welcome?token='        || 'http://config.com/welcome#token=token-value'
        'https://bücher.example'        | 'https://xn--bcher-kva.example/welcome?token=' || 'http://config.com/welcome#token=token-value'
        'https://bücher.example'        | 'https://bücher.example/welcome?token='        || 'http://config.com/welcome#token=token-value'
    }

    void 'an allowed IPv6 origin is compared by address: #callback'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): ['http://[::1]:8080', 'http://127.0.0.1:8080']])
        stubOauthFlow()

        expect:
        runOauthFlow(callback) == expectedUrl

        where:
        callback                                          || expectedUrl
        'http://[::1]:8080/welcome?token='                || 'http://[::1]:8080/welcome?token=token-value'
        'http://[0:0:0:0:0:0:0:1]:8080/welcome?token='    || 'http://[0:0:0:0:0:0:0:1]:8080/welcome?token=token-value'
        'http://[0000::0001]:8080/welcome?token='         || 'http://[0000::0001]:8080/welcome?token=token-value'
        'http://[::2]:8080/welcome?token='                || 'http://config.com/welcome#token=token-value'
        'http://[::1]:8443/welcome?token='                || 'http://config.com/welcome#token=token-value'
        'http://[::ffff:127.0.0.1]:8080/welcome?token='   || 'http://config.com/welcome#token=token-value'
        'http://[::1%25lo]:8080/welcome?token='           || 'http://config.com/welcome#token=token-value'
    }

    void 'a callback URL of another origin is ignored: #callback'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): ['https://frontend.example.com']])
        stubOauthFlow()

        expect: 'the configured frontend callback URL receives the token'
        runOauthFlow(callback) == 'http://config.com/welcome#token=token-value'

        where:
        callback << [
                'https://other.example/?token=',
                'https://frontend.example.com.other.example/?token=',
                'https://frontend.example.com@other.example/?token=',
                'https://other.example\\@frontend.example.com/?token=',
                'https://other.example#@frontend.example.com/?token=',
                'https://frontend.example.com',
                'https://frontend.example.com:443',
                'http://frontend.example.com/?token=',
                'https://frontend.example.com:8443/?token=',
                '//frontend.example.com/?token=',
                'frontend.example.com/?token=',
                '/welcome?token=',
                'ftp://frontend.example.com/',
                'javascript:alert(1)//',
                'Zm9v',
                'a-b_',
                base64('https://other.example/?token='),
        ]
    }

    void 'a new login forgets the callback URL of an earlier one: #secondCallback'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): ['https://frontend.example.com']])
        stubOauthFlow()

        and: 'a login that was started with an allowed callback URL, but not completed'
        params.callback = 'https://frontend.example.com/welcome?token='
        controller.authenticate()
        response.reset()

        expect:
        runOauthFlow(secondCallback) == 'http://config.com/welcome#token=token-value'

        where:
        secondCallback << [null, 'https://other.example/?token=']
    }

    void 'a callback URL is not used when its origin is no longer allowed at the end of the login'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): ['https://frontend.example.com']])
        stubOauthFlow()
        params.callback = 'https://frontend.example.com/welcome?token='
        controller.authenticate()
        response.reset()

        when:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): ['https://other.example']])
        controller.callback()

        then:
        response.redirectedUrl == 'http://config.com/welcome#token=token-value'
    }

    void 'an entry of the allowed origins that is no origin is ignored: #allowedOrigins'() {
        given:
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): allowedOrigins])
        stubOauthFlow()

        expect:
        runOauthFlow('https://frontend.example.com/welcome?token=') == expectedUrl

        where:
        allowedOrigins                                          || expectedUrl
        ['frontend.example.com']                                || 'http://config.com/welcome#token=token-value'
        ['frontend.example.com', 'https://frontend.example.com'] || 'https://frontend.example.com/welcome?token=token-value'
        ['https://frontend.example.com/welcome']                || 'http://config.com/welcome#token=token-value'
        ['https://frontend.example.com/?token=']                || 'http://config.com/welcome#token=token-value'
        ['https://frontend.example.com#token=']                 || 'http://config.com/welcome#token=token-value'
        ['https://user@frontend.example.com']                   || 'http://config.com/welcome#token=token-value'
        ['https://frontend.example.com/']                       || 'https://frontend.example.com/welcome?token=token-value'
    }

    void 'allowed origins can be listed in application.yml'() {
        given:
        final yaml = '''\
            grails:
                plugin:
                    springsecurity:
                        rest:
                            oauth:
                                allowedCallbackOrigins:
                                    - https://other.example
                                    - https://frontend.example.com
            '''.stripIndent()
        final propertySource = new YamlPropertySourceLoader().load('application.yml', new ByteArrayResource(yaml.bytes)).first()
        // getSource(), since the property syntax would look up a configuration property named 'source'
        grailsApplication.config.merge(propertySource.getSource() as Map)
        stubOauthFlow()

        expect:
        runOauthFlow('https://frontend.example.com/welcome?token=') == 'https://frontend.example.com/welcome?token=token-value'
    }

    void 'the callback parameter is ignored when no origin is allowed'() {
        given:
        stubOauthFlow()

        expect: 'the configured frontend callback URL receives the token'
        runOauthFlow('https://frontend.example.com/welcome?token=') == 'http://config.com/welcome#token=token-value'
    }

    private Exception injectDependencies(Exception caughtException) {
        def stubRestOauthService = Stub(RestOauthService)
        stubRestOauthService.storeAuthentication(*_) >> { throw caughtException }
        controller.restOauthService = stubRestOauthService
        controller.callbackErrorHandler = new DefaultCallbackErrorHandler()
        caughtException
    }

    private String getExpectedUrl(Exception exception, expectedErrorValue, String baseUrl = frontendCallbackBaseUrl) {
        def message = exception.message
        "${baseUrl}&error=${expectedErrorValue}&message=${message}&error_description=${message}&error_code=${exception.class.simpleName}"
    }

    def 'UsernameNotFoundException caught within callback action with frontend closure URL in Config.groovy'() {

        def caughtException = injectDependencies(new UsernameNotFoundException('message'))

        when:
        controller.callback()

        then:
        String expectedUrl = getExpectedUrl(caughtException, FORBIDDEN.value())
        response.redirectedUrl == expectedUrl
    }

    def 'UsernameNotFoundException caught within callback action with frontend string URL in Config.groovy'() {

        def caughtException = injectDependencies(new UsernameNotFoundException('message'))
        grailsApplication.config.merge(['grails.plugin.springsecurity.rest.oauth.frontendCallbackUrl': frontendCallbackBaseUrl])

        when:
        controller.callback()

        then:
        String expectedUrl = getExpectedUrl(caughtException, FORBIDDEN.value())
        response.redirectedUrl == expectedUrl
    }

    def 'UsernameNotFoundException caught within callback action with frontend URL in session'() {

        def caughtException = injectDependencies(new UsernameNotFoundException('message'))
        def frontendCallbackBaseUrlSession = "http://session.com/welcome#token="
        grailsApplication.config.merge([(ALLOWED_CALLBACK_ORIGINS): ['http://session.com']])
        request.session[controller.CALLBACK_ATTR] = frontendCallbackBaseUrlSession

        when:
        controller.callback()

        then:
        String expectedUrl = getExpectedUrl(caughtException, FORBIDDEN.value(), frontendCallbackBaseUrlSession)
        response.redirectedUrl == expectedUrl
    }

    def 'Non-UsernameNotFoundException with cause that has code caught within callback action'() {

        def caughtException = injectDependencies(new ExceptionWithCodedCause('message'))

        when:
        controller.callback()

        then:
        String expectedUrl = getExpectedUrl(caughtException, 'cause.code')
        response.redirectedUrl == expectedUrl
    }

    def 'Non-UsernameNotFoundException without cause caught within callback action'() {

        def caughtException = injectDependencies(new Exception('message'))

        when:
        controller.callback()

        then:
        String expectedUrl = getExpectedUrl(caughtException, INTERNAL_SERVER_ERROR.value())
        response.redirectedUrl == expectedUrl
    }

    void "invalid jwt receives forbidden"() {
        given:
        TokenStorageService stubbedTokenStorageService = Stub(TokenStorageService)
        stubbedTokenStorageService.loadUserByToken(_) >> new User('foo', '', [])
        controller.tokenStorageService = stubbedTokenStorageService

        def stubbedTokenGenerator = Stub(AbstractJwtTokenGenerator) {
            generateAccessToken() >> { u, b -> new AccessToken('accessToken') }
        }
        controller.tokenGenerator = stubbedTokenGenerator

        controller.authenticationEventPublisher = Mock(RestAuthenticationEventPublisher)
        controller.jwtService = Mock(JwtService)

        when:
        params.grant_type = 'refresh_token'
        params.refresh_token = 'refresh_token_1'
        request.method = 'POST'
        controller.accessToken()

        then:
        1 * controller.jwtService.parse('refresh_token_1') >> { token ->
            null
        }
        0 * controller.authenticationEventPublisher.publishTokenCreation(_)
        response.status == FORBIDDEN.value()
    }

    void "it publishes RestTokenCreationEvent's"() {
        given:
        TokenStorageService stubbedTokenStorageService = Stub(TokenStorageService)
        stubbedTokenStorageService.loadUserByToken(_) >> new User('foo', '', [])
        controller.tokenStorageService = stubbedTokenStorageService

        def stubbedTokenGenerator = Stub(AbstractJwtTokenGenerator) {
            generateAccessToken() >> { u, b -> new AccessToken('accessToken') }
        }
        controller.tokenGenerator = stubbedTokenGenerator

        controller.authenticationEventPublisher = Mock(RestAuthenticationEventPublisher)
        controller.jwtService = Mock(JwtService)
        controller.accessTokenJsonRenderer = Mock(AccessTokenJsonRenderer)

        JWT returnedToken = new PlainJWT(new JWTClaimsSet.Builder().claim(AbstractJwtTokenGenerator.REFRESH_ONLY_CLAIM, true).build())

        when:
        params.grant_type = 'refresh_token'
        params.refresh_token = 'refresh_token_1'
        request.method = 'POST'
        controller.accessToken()

        then:
        1 * controller.jwtService.parse('refresh_token_1') >> { token ->
            returnedToken
        }
        1 * controller.authenticationEventPublisher.publishTokenCreation(_)
        1 * controller.accessTokenJsonRenderer.generateJson(_) >> { token ->
            assert !token.is(returnedToken)
            'rendered json'
        }
        response.status == OK.value()
        response.text == "rendered json"
    }

    void "an encrypted refresh token gets a new access token"() {
        given:
        final tokenGenerator = useEncryptedJwt()
        final refreshToken = tokenGenerator.generateAccessToken(new User('foo', 'bar', [])).refreshToken

        when:
        params.grant_type = 'refresh_token'
        params.refresh_token = refreshToken
        request.method = 'POST'
        controller.accessToken()

        then:
        1 * controller.tokenStorageService.userDetailsService.loadUserByUsername('foo') >> new User('foo', 'bar', [])
        1 * controller.authenticationEventPublisher.publishTokenCreation(_)
        1 * controller.accessTokenJsonRenderer.generateJson({ AccessToken it ->
            it.principal.username == 'foo' && it.refreshToken == refreshToken && it.accessTokenJwt.JWTClaimsSet.subject == 'foo'
        }) >> 'rendered json'
        response.status == OK.value()
        response.text == 'rendered json'
    }

    void "an encrypted refresh token whose claims are not signed is forbidden"() {
        given:
        final tokenGenerator = useEncryptedJwt()
        final claims = new JWTClaimsSet.Builder()
                .subject('foo')
                .claim(AbstractJwtTokenGenerator.REFRESH_ONLY_CLAIM, true)
                .build()
        final refreshToken = encryptClaims(claims, tokenGenerator.keyProvider.publicKey)

        when:
        params.grant_type = 'refresh_token'
        params.refresh_token = refreshToken
        request.method = 'POST'
        controller.accessToken()

        then:
        0 * controller.tokenStorageService.userDetailsService.loadUserByUsername(_)
        0 * controller.authenticationEventPublisher.publishTokenCreation(_)
        0 * controller.accessTokenJsonRenderer.generateJson(_)
        response.status == FORBIDDEN.value()
    }

    private EncryptedJwtTokenGenerator useEncryptedJwt() {
        final tokenGenerator = new EncryptedJwtTokenGenerator(
                keyProvider: new DefaultRSAKeyProvider(),
                jweAlgorithm: JWEAlgorithm.RSA_OAEP,
                encryptionMethod: EncryptionMethod.A128GCM,
                defaultExpiration: 3600,
                customClaimProviders: []
        )
        final jwtService = new JwtService(keyProvider: tokenGenerator.keyProvider)
        tokenGenerator.jwtTokenStorageService = new JwtTokenStorageService(jwtService: jwtService, userDetailsService: Mock(UserDetailsService))
        controller.tokenGenerator = tokenGenerator
        controller.jwtService = jwtService
        controller.tokenStorageService = tokenGenerator.jwtTokenStorageService
        controller.authenticationEventPublisher = Mock(RestAuthenticationEventPublisher)
        controller.accessTokenJsonRenderer = Mock(AccessTokenJsonRenderer)
        tokenGenerator
    }
}

@InheritConstructors
class ExceptionWithCodedCause extends Exception {

    String getCode() {
        'cause.code'
    }

    @Override
    synchronized Throwable getCause() {
        return this
    }
}
