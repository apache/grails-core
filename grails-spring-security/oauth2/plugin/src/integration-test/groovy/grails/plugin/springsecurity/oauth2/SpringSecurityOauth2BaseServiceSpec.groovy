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

import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.model.OAuth2AccessToken
import spock.lang.Specification

import org.springframework.transaction.PlatformTransactionManager

import grails.plugin.springsecurity.oauth2.service.OAuth2AbstractProviderService
import grails.plugin.springsecurity.oauth2.token.OAuth2SpringToken
import grails.testing.services.ServiceUnitTest

class SpringSecurityOauth2BaseServiceSpec extends Specification implements ServiceUnitTest<SpringSecurityOauth2BaseService> {

    void setup() {
        // The service is @Transactional; no GORM implementation is set up in a unit test
        service.transactionManager = Stub(PlatformTransactionManager)
    }

    void 'a newly registered user gets ROLE_USER when no role names are configured'() {
        expect:
        service.roleNames == ['ROLE_USER']
    }

    void 'a newly registered user gets the configured role names'() {
        given:
        config.merge([grails: [plugin: [springsecurity: [oauth2: [registration: [roleNames: ['ROLE_A', 'ROLE_B']]]]]]])

        expect:
        service.roleNames == ['ROLE_A', 'ROLE_B']
    }

    void 'authorization URL preserves caller state through the base service and Scribe builder'() {
        given:
        def api = Spy(DefaultApi20) {
            getAuthorizationBaseUrl() >> 'https://provider.example/authorize'
        }
        def authService = new ServiceBuilder('client-key').build(api)
        def provider = Spy(OAuth2AbstractProviderService) {
            getAuthService() >> authService
        }
        service.providerServiceMap.test = provider

        expect:
        service.getAuthorizationUrl('test', 'session-bound-state').contains('state=session-bound-state')
        service.sessionKeyForState('test') != service.sessionKeyForState('other')
        service.sessionKeyForState('test') != service.sessionKeyForAccessToken('test')
    }

    void 'provider refuses to build an authorization URL without caller state'() {
        given:
        def provider = Spy(OAuth2AbstractProviderService)

        when:
        provider.getAuthUrl(state, [:])

        then:
        thrown(IllegalArgumentException)

        where:
        state << [null, '', '   ']
    }

    void 'authorization URL dispatches to a provider override accepting the supplied state'() {
        given:
        service.providerServiceMap.test = new OverridingProvider()

        expect:
        service.getAuthorizationUrl('test', 'session-bound-state') ==
                'https://provider.example/custom?state=session-bound-state&prompt=login'
    }

    void 'provider failure URL defaults to the failure action'() {
        given:
        service.grailsApplication = grailsApplication
        config.merge([grails: [serverURL: 'https://application.example']])
        service.providerServiceMap.test = Stub(OAuth2AbstractProviderService)

        expect:
        service.getFailureUrl('test') == 'https://application.example/oauth2/test/failure'
    }

    static class OverridingProvider extends OAuth2AbstractProviderService {
        @Override
        String getAuthUrl(String state, Map<String, String> params) {
            "https://provider.example/custom?state=${state}&prompt=login"
        }

        String getProviderID() { 'test' }
        Class<? extends DefaultApi20> getApiClass() { DefaultApi20 }
        String getProfileScope() { 'profile' }
        String getScopes() { 'profile' }
        String getScopeSeparator() { ' ' }
        OAuth2SpringToken createSpringAuthToken(OAuth2AccessToken accessToken) {
            throw new UnsupportedOperationException('Token exchange is outside this URL test')
        }
    }
}
