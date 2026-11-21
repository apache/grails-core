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

package rest

import java.net.CookieManager
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets

import groovy.json.JsonOutput

import io.github.cjstehno.ersatz.GroovyErsatzServer
import io.github.cjstehno.ersatz.cfg.ContentType

import org.apache.grails.testing.http.client.TestHttpResponse

/**
 * The OAuth 2.0 authorization code flow of the plugin ({@code /oauth/authenticate/<provider>} and
 * {@code /oauth/callback/<provider>}) against the {@link MockOauthProvider}.
 *
 * <p>The spec plays the browser: it follows each redirect by hand, with a client that keeps the session cookie of the
 * application, where pac4j keeps the {@code state} parameter between the two requests. The provider is Ersatz, which
 * answers the authorization request of the browser and the token and user-info requests of the application, and
 * verifies that each was made.</p>
 */
abstract class AbstractRestOauthSpec extends AbstractRestSpec {

    static final String CLIENT_ID = 'rest-test-client'
    static final String CLIENT_SECRET = 'rest-test-secret'
    static final String FRONTEND_CALLBACK_URL = 'http://example.org#token='

    /** The user the provider reports; the application has a user of this name. */
    static final String PROFILE_ID = '115537660854424164575'

    static final String AUTHORIZATION_CODE = 'the-authorization-code'
    static final String PROVIDER_ACCESS_TOKEN = 'the-access-token-of-the-provider'

    GroovyErsatzServer provider
    HttpClient browser

    void setup() {
        provider = MockOauthProvider.reset()
        browser = newHttpClientWith {
            followRedirects(HttpClient.Redirect.NEVER)
            cookieHandler(new CookieManager())
        }
    }

    void cleanup() {
        MockOauthProvider.reset()
    }

    void "the user is sent to the authorization endpoint of the provider"() {
        when:
        TestHttpResponse response = http("/oauth/authenticate/${MockOauthProvider.NAME}", browser)
        URI location = URI.create(response.headerValue('Location'))
        Map<String, String> query = queryOf(location)

        then:
        response.statusCode() == 302
        location.toString().startsWith(provider.httpUrl(MockOauthProvider.AUTHORIZATION_PATH))
        query.response_type == 'code'
        query.client_id == CLIENT_ID
        query.redirect_uri == callbackUrl
        query.state
    }

    void "a user who logs in at the provider gets a token of the application at the frontend callback URL"() {
        given: 'the authorization request the application redirects to'
        URI authorization = URI.create(http("/oauth/authenticate/${MockOauthProvider.NAME}", browser).headerValue('Location'))
        String state = queryOf(authorization).state

        and: 'a provider that authorizes the user, exchanges the code for a token and reports who the user is'
        String redirectUri = callbackUrl
        String authorizationCode = AUTHORIZATION_CODE
        String providerToken = PROVIDER_ACCESS_TOKEN
        String clientCredentials = basic(CLIENT_ID, CLIENT_SECRET)
        provider.expectations {
            GET(MockOauthProvider.AUTHORIZATION_PATH) {
                called(1)
                query('response_type', 'code')
                query('client_id', CLIENT_ID)
                query('redirect_uri', redirectUri)
                query('state', state)
                responder {
                    code(302)
                    header('Location', "${redirectUri}&code=${authorizationCode}&state=${state}")
                }
            }
            POST(MockOauthProvider.TOKEN_PATH) {
                called(1)
                param('grant_type', 'authorization_code')
                param('code', authorizationCode)
                param('redirect_uri', redirectUri)
                header('Authorization', clientCredentials)
                responder {
                    code(200)
                    body(JsonOutput.toJson([access_token: providerToken, token_type: 'Bearer', expires_in: 3600]), ContentType.APPLICATION_JSON)
                }
            }
            GET(MockOauthProvider.PROFILE_PATH) {
                called(1)
                header('Authorization', "Bearer ${providerToken}")
                responder {
                    code(200)
                    body(JsonOutput.toJson([id: PROFILE_ID, email: 'jimi@example.org']), ContentType.APPLICATION_JSON)
                }
            }
        }

        when: 'the browser goes to the provider, which sends it back to the application'
        TestHttpResponse authorized = http(authorization.toString(), browser)

        then:
        authorized.statusCode() == 302

        when:
        TestHttpResponse callback = http(authorized.headerValue('Location'), browser)
        String frontendUrl = callback.headerValue('Location')

        then: 'the application sends the browser to the frontend with a token'
        callback.statusCode() == 302
        frontendUrl.startsWith(FRONTEND_CALLBACK_URL)
        frontendUrl.length() > FRONTEND_CALLBACK_URL.length()

        and: 'the provider was asked for the token and for the profile, once each'
        provider.verify()

        when: 'the token is used'
        String token = frontendUrl - FRONTEND_CALLBACK_URL
        TestHttpResponse validation = get('/api/validate', ['X-Auth-Token': token])

        then: 'it is the token of the user the provider reported'
        validation.statusCode() == 200
        validation.json().username == PROFILE_ID
        validation.json().access_token == token
        'ROLE_USER' in validation.json().roles

        and:
        get('/secured', ['X-Auth-Token': token]).body() == PROFILE_ID
    }

    void "a provider that refuses the authorization code is reported at the frontend callback URL without a token"() {
        given:
        URI authorization = URI.create(http("/oauth/authenticate/${MockOauthProvider.NAME}", browser).headerValue('Location'))
        String state = queryOf(authorization).state
        provider.expectations {
            POST(MockOauthProvider.TOKEN_PATH) {
                called(1)
                param('code', 'a-code-the-provider-does-not-know')
                responder {
                    code(400)
                    body(JsonOutput.toJson([error: 'invalid_grant']), ContentType.APPLICATION_JSON)
                }
            }
        }

        when:
        TestHttpResponse callback = http("${callbackUrl}&code=a-code-the-provider-does-not-know&state=${state}", browser)
        String frontendUrl = callback.headerValue('Location')

        then:
        callback.statusCode() == 302
        frontendUrl.startsWith("${FRONTEND_CALLBACK_URL}&error=")
        frontendUrl.contains('invalid_grant')

        and: 'the token was asked for once, and the profile was not'
        provider.verify()
    }

    /**
     * The redirect URI the application registers with the provider; pac4j adds the name of its client to it.
     */
    private String getCallbackUrl() {
        "${httpBaseUrl}/oauth/callback/${MockOauthProvider.NAME}?client_name=GenericOAuth20Client"
    }

    private static Map<String, String> queryOf(URI uri) {
        uri.rawQuery.split('&').collectEntries { String parameter ->
            List<String> pair = parameter.split('=', 2).toList()
            [(URLDecoder.decode(pair[0], StandardCharsets.UTF_8)): URLDecoder.decode(pair[1] ?: '', StandardCharsets.UTF_8)]
        }
    }

    private static String basic(String username, String password) {
        'Basic ' + "${username}:${password}".getBytes(StandardCharsets.UTF_8).encodeBase64()
    }
}
