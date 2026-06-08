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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.LoadingCache
import grails.plugin.springsecurity.rest.authentication.RestAuthenticationEventPublisher
import grails.plugin.springsecurity.rest.oauth.OauthUser
import grails.plugin.springsecurity.rest.oauth.OauthUserDetailsService
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.TokenGenerator
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import grails.core.GrailsApplication
import grails.util.Holders
import grails.web.mapping.LinkGenerator
import groovy.util.logging.Slf4j
import org.pac4j.core.client.IndirectClient
import org.pac4j.core.context.CallContext
import org.pac4j.core.credentials.Credentials
import org.pac4j.core.profile.UserProfile
import org.springframework.beans.BeanWrapperImpl
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

import java.beans.PropertyDescriptor

/**
 * Deals with pac4j library to fetch a user profile from the selected OAuth provider, and stores it on the security context
 */
@Slf4j
class RestOauthService {

    static transactional = false

    TokenGenerator tokenGenerator
    TokenStorageService tokenStorageService
    GrailsApplication grailsApplication
    LinkGenerator grailsLinkGenerator
    OauthUserDetailsService oauthUserDetailsService
    RestAuthenticationEventPublisher authenticationEventPublisher

    private transient LoadingCache<String, IndirectClient> clientCache = CacheBuilder.newBuilder().<String, IndirectClient>build { String provider ->
        log.debug "Creating OAuth client for provider: ${provider}"

        def clientClass = grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.client"]
        if (clientClass instanceof CharSequence) clientClass = Class.forName(clientClass as String, true, Holders.grailsApplication.classLoader)
        IndirectClient client = (clientClass as Class<? extends IndirectClient>).getDeclaredConstructor().newInstance()

        String callbackUrl = grailsLinkGenerator.link controller: 'restOauth', action: 'callback', params: [provider: provider], mapping: 'oauth', absolute: true
        log.debug "Callback URL is: ${callbackUrl}"
        client.callbackUrl = callbackUrl

        BeanWrapperImpl clientInvokerHelper = new BeanWrapperImpl(client)
        for (PropertyDescriptor propertyDescriptor : clientInvokerHelper.getPropertyDescriptors()) {
            if(propertyDescriptor.writeMethod) {
                String propertyName = propertyDescriptor.name
                if(propertyName != "client" && grailsApplication.config.containsKey("grails.plugin.springsecurity.rest.oauth.${provider}.${propertyName}")) {
                    clientInvokerHelper.setPropertyValue(propertyName, grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.${propertyName}"])
                }
            }
        }

        client
    }

    IndirectClient getClient(String provider) {
        clientCache.get provider
    }

    UserProfile getProfile(String provider, CallContext context) {
        IndirectClient client = getClient(provider)
        Credentials credentials = client.getCredentials(context).orElse(null)
        client.validateCredentials(context, credentials)

        log.debug "Querying provider to fetch User ID"
        client.getUserProfile(context, credentials).orElse(null)
    }

    OauthUser getOauthUser(String provider, UserProfile profile) {
        def configuredDefaultRoles = grailsApplication.config["grails.plugin.springsecurity.rest.oauth.${provider}.defaultRoles"]
        List defaultRoles = configuredDefaultRoles?.collect { new SimpleGrantedAuthority(it as String) }
        oauthUserDetailsService.loadUserByUserProfile(profile, defaultRoles)
    }

    String storeAuthentication(String provider, CallContext context) {
        UserProfile profile = getProfile(provider, context)
        log.debug "User's ID: ${profile.id}"

        OauthUser userDetails = getOauthUser(provider, profile)
        AccessToken accessToken = tokenGenerator.generateAccessToken(userDetails)
        log.debug "Generated REST authentication token: ${accessToken}"

        log.debug "Storing token on the token storage"
        tokenStorageService.storeToken(accessToken)

        authenticationEventPublisher.publishTokenCreation(accessToken)

        SecurityContextHolder.context.setAuthentication(accessToken)

        return accessToken.accessToken
    }
}
