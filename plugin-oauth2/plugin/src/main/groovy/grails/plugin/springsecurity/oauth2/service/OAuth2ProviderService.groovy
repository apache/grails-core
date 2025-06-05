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

import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.Response
import com.github.scribejava.core.oauth.OAuth20Service
import grails.plugin.springsecurity.oauth2.token.OAuth2SpringToken
import grails.plugin.springsecurity.oauth2.util.OAuth2ProviderConfiguration

/**
 * Always code as if the guy who ends up maintaining your code
 * will be a violent psychopath that knows where you live.
 *
 * - John Woods
 *
 * Created on 07.04.2016.
 * @author J.Brunswicker
 */
interface OAuth2ProviderService {

    /**
     * @return The ProviderID
     */
    String getProviderID()

    /**
     * A scribeJava API class to use for the oAuth Request or any other class that extends the @link{DefaultApi20}* @return The ApiClass that is to use
     */
    Class<? extends DefaultApi20> getApiClass()

    /**
     * Path to the OAuthScope that is returning the UserIdentifier
     * i.e 'https://graph.facebook.com/me' for facebook
     */
    String getProfileScope()

    /**
     * Path to the OAuthScope(s) that are needed to get all the data you want.
     * Must at least contain the scope to retrieve the UserIdentifier
     * i.e 'https://graph.facebook.com/me' for facebook
     */
    String getScopes()

    /**
     * Get separator string for concatenating the mandatory and the optional scopes
     */
    String getScopeSeparator()

    /**
     * @param accessToken
     * @return
     */
    OAuth2SpringToken createSpringAuthToken(OAuth2AccessToken accessToken)

    /**
     * Initialize the service with a configuration
     * @param oAuth2ProviderConfiguration
     */
    void init(OAuth2ProviderConfiguration oAuth2ProviderConfiguration)

    /**
     * Get the access token from the oAuth2 Service
     * @param authCode
     * @return
     */
    OAuth2AccessToken getAccessToken(String authCode)

    /**
     * Get the authorization URL
     * @param params Additional params for the url call
     * @return
     */
    String getAuthUrl(Map<String, String> params)

    /**
     * Create the scribe service to make oAuthCalls with
     * @param providerConfiguration
     */
    OAuth20Service buildScribeService(OAuth2ProviderConfiguration providerConfiguration)

    /**
     * @return The configured successUrl
     */
    String getSuccessUrl()

    /**
     * @return The configure failureUrl
     */
    String getFailureUrl()

    /**
     * Get the response from OAuthServer
     * @param profileUrl
     * @param accessToken
     * @return
     */
    Response getResponse(OAuth2AccessToken accessToken)

}