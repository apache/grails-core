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
package grails.plugin.springsecurity.oauth2.service

import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Response
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth20Service
import grails.plugin.springsecurity.oauth2.token.OAuth2SpringToken
import grails.plugin.springsecurity.oauth2.util.OAuth2ProviderConfiguration

/**
 * Always code as if the guy who ends up maintaining your code 
 * will be a violent psychopath who knows where you live. 
 * Code for readability.
 *
 * John F. Woods
 *
 * Created by Johannes on 06.04.2016.
 */
abstract class OAuth2AbstractProviderService implements OAuth2ProviderService {

    private OAuth20Service _authService

    private OAuth2ProviderConfiguration _providerConfiguration

    /**
     * @return The ProviderID
     */
    abstract String getProviderID()

    /**
     * A scribeJava API class to use for the oAuth Request or any other class that extends the @link{DefaultApi20}* @return The ApiClass that is to use
     */
    abstract Class<? extends DefaultApi20> getApiClass()

    /**
     * Path to the OAuthScope that is returning the UserIdentifier
     * i.e 'https://graph.facebook.com/me' for facebook
     */
    abstract String getProfileScope()

    /**
     * The scopes that are at least required by the oauth2 provider, to get an email-address
     * Additional scopes can be configured in the application.yml
     */
    abstract String getScopes()

    /**
     * Get separator string for concatenating the mandatory and the optional scopes
     */
    abstract String getScopeSeparator()

    /**
     * @param accessToken
     * @return
     */
    abstract OAuth2SpringToken createSpringAuthToken(OAuth2AccessToken accessToken)

    /**
     * Initialize the service with a configuration
     * @param oAuth2ProviderConfiguration
     */
    void init(OAuth2ProviderConfiguration oAuth2ProviderConfiguration) {
        _providerConfiguration = oAuth2ProviderConfiguration
        _authService = buildScribeService(oAuth2ProviderConfiguration)
    }

    /**
     * Get the access token from the oAuth2 Service
     * @param authCode
     * @return
     */
    OAuth2AccessToken getAccessToken(String authCode) {
        authService.getAccessToken(authCode)
    }

    /**
     * Get the authorization URL
     * @param params Additional params for the url call
     * @return
     */
    String getAuthUrl(Map<String, String> params) {
        final String secretState = getProviderID() + "-secret-" + new Random().nextInt(999_999)
        return authService.createAuthorizationUrlBuilder()
                .state(secretState)
                .additionalParams(params)
                .build()
    }

    /**
     * Create the scribe service to make oAuthCalls with
     * @param providerConfiguration
     * @return a scribejava service builder
     */
    OAuth20Service buildScribeService(OAuth2ProviderConfiguration providerConfiguration) {
        ServiceBuilder serviceBuilder = new ServiceBuilder(providerConfiguration.apiKey)
                .apiSecret(providerConfiguration.apiSecret)
        if (providerConfiguration.callbackUrl) {
            serviceBuilder.callback(providerConfiguration.callbackUrl)
        }
        if (providerConfiguration.scope) {
            serviceBuilder.defaultScope(providerConfiguration.scope)
        }
        if (providerConfiguration.debug) {
            serviceBuilder.debug()
        }

        serviceBuilder.build(getApiClass().instance())
    }

    /**
     * @return The configured successUrl
     */
    String getSuccessUrl() {
        providerConfiguration.successUrl
    }

    /**
     * @return The configure failureUrl
     */
    String getFailureUrl() {
        providerConfiguration.failureUrl
    }

    /**
     * Get the response from OAuthServer
     * @param profileUrl
     * @param accessToken
     * @return
     */
    Response getResponse(OAuth2AccessToken accessToken) {
        OAuthRequest oAuthRequest = new OAuthRequest(Verb.GET, getProfileScope())
        authService.signRequest(accessToken, oAuthRequest)
        authService.execute(oAuthRequest)
    }

    OAuth20Service getAuthService() {
        return _authService
    }

    OAuth2ProviderConfiguration getProviderConfiguration() {
        return _providerConfiguration
    }

}