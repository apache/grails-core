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

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.session.HttpSessionEventPublisher
import org.springframework.security.web.util.matcher.AnyRequestMatcher
import spock.lang.Specification

import grails.plugin.springsecurity.access.NullAfterInvocationProvider
import grails.plugin.springsecurity.authentication.DigestDaoAuthenticationProvider
import grails.testing.web.GrailsWebUnitTest
import grails.web.CamelCaseUrlConverter

/**
 * What the beans the plugin declares are once created, however they are declared.
 */
abstract class AbstractPluginBeansSpec extends Specification implements GrailsWebUnitTest {

    Set<String> getIncludePlugins() {
        ['core', 'springSecurityCore'] as Set
    }

    Closure doWithSpring() {
        { ->
            grailsUrlConverter(CamelCaseUrlConverter)
        }
    }

    Closure doWithConfig() {
        { config ->
            config.grails.plugin.springsecurity.securityConfigType = 'InterceptUrlMap'
            configure(config.grails.plugin.springsecurity)
        }
    }

    protected abstract void configure(Object securityConfig)

    void cleanupSpec() {
        SpringSecurityUtils.resetSecurityConfig()
        ReflectionUtils.application = null
        SpringSecurityUtils.application = null
    }
}

class DefaultPluginBeansSpec extends AbstractPluginBeansSpec {

    protected void configure(Object securityConfig) {
        securityConfig.password = [algorithm: 'SHA-256', encodeHashAsBase64: true, hash: [iterations: 2]]
        securityConfig.providerManager = [eraseCredentialsAfterAuthentication: false]
        securityConfig.useHttpSessionEventPublisher = true
        securityConfig.afterInvocationManagerProviderNames = ['nullAfterInvocationProvider']
    }

    void 'the password encoder encodes as the application configured it'() {
        given: 'what the digest encoder makes of the values, which it only accepts if they all arrived'
            def digest = new MessageDigestPasswordEncoder('SHA-256')
            digest.encodeHashAsBase64 = true
            digest.iterations = 2

        when:
            String encoded = applicationContext.getBean('passwordEncoder', PasswordEncoder).encode('secret')

        then:
            applicationContext.getBean('passwordEncoder') instanceof DelegatingPasswordEncoder
            encoded.startsWith('{SHA-256}')
            digest.matches('secret', encoded - '{SHA-256}')
    }

    void 'the authentication manager is told what the application configured'() {
        given:
            ProviderManager manager = applicationContext.getBean('authenticationManager', ProviderManager)

        expect:
            !manager.eraseCredentialsAfterAuthentication
    }

    void 'the filter chain proxy reads the list the plugin goes on to fill'() {
        given:
            List<SecurityFilterChain> chains = applicationContext.getBean('securityFilterChains', List)
            FilterChainProxy proxy = applicationContext.getBean('springSecurityFilterChainProxy', FilterChainProxy)
            def chain = new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE)

        when:
            chains << chain

        then:
            proxy.filterChains.contains(chain)
    }

    void 'the session event publisher is registered as a listener'() {
        expect:
            applicationContext.getBean('httpSessionEventPublisher', ServletListenerRegistrationBean)
                    .listener instanceof HttpSessionEventPublisher
    }

    void 'the after-invocation manager starts with a provider'() {
        expect:
            applicationContext.getBean('afterInvocationManager').providers*.getClass() == [NullAfterInvocationProvider]
    }
}

class BcryptPluginBeansSpec extends AbstractPluginBeansSpec {

    protected void configure(Object securityConfig) {
        securityConfig.password = [algorithm: 'bcrypt', bcrypt: [logrounds: 5]]
    }

    void 'the password encoder uses the rounds the application configured'() {
        expect:
            applicationContext.getBean('passwordEncoder', PasswordEncoder).encode('secret').startsWith('{bcrypt}$2a$05$')
    }
}

class CustomClassesPluginBeansSpec extends AbstractPluginBeansSpec {

    protected void configure(Object securityConfig) {
        securityConfig.password = [algorithm: 'bcrypt']
        securityConfig.passwordEncoderBeanClass = CustomPasswordEncoder
        securityConfig.authenticationManagerBeanClass = CustomProviderManager
        securityConfig.springSecurityFilterChainProxyBeanClass = CustomFilterChainProxy
    }

    void 'a class configured in place of a default one is created with what the default is given'() {
        given:
            CustomPasswordEncoder encoder = applicationContext.getBean('passwordEncoder', CustomPasswordEncoder)

        expect:
            encoder.algorithm == 'bcrypt'
            encoder.encoders.bcrypt
            applicationContext.getBean('authenticationManager') instanceof CustomProviderManager
            applicationContext.getBean('springSecurityFilterChainProxy') instanceof CustomFilterChainProxy
    }

    static class CustomPasswordEncoder extends DelegatingPasswordEncoder {

        final String algorithm
        final Map<String, PasswordEncoder> encoders

        CustomPasswordEncoder(String algorithm, Map<String, PasswordEncoder> encoders) {
            super(algorithm, encoders)
            this.algorithm = algorithm
            this.encoders = encoders
        }
    }

    static class CustomProviderManager extends ProviderManager {

        /** The plugin sets the providers on whatever class is configured, and this one's are private to its parent. */
        List<AuthenticationProvider> providers

        CustomProviderManager(List<AuthenticationProvider> providers) {
            super(providers)
        }
    }

    static class CustomFilterChainProxy extends FilterChainProxy {

        CustomFilterChainProxy(List<SecurityFilterChain> chains) {
            super(chains)
        }
    }
}

class DigestPluginBeansSpec extends AbstractPluginBeansSpec {

    protected void configure(Object securityConfig) {
        securityConfig.useDigestAuth = true
        securityConfig.digest = [realmName: 'Test Realm']
    }

    void 'with Digest authentication the provider is told the realm and that what is stored is the hash'() {
        given:
            DigestDaoAuthenticationProvider provider =
                    applicationContext.getBean('daoAuthenticationProvider', DigestDaoAuthenticationProvider)

        expect:
            provider.realmName == 'Test Realm'
            provider.passwordAlreadyEncoded
            applicationContext.getBean('passwordEncoder') instanceof DelegatingPasswordEncoder
    }
}

class CleartextDigestPluginBeansSpec extends AbstractPluginBeansSpec {

    protected void configure(Object securityConfig) {
        securityConfig.useDigestAuth = true
        securityConfig.digest = [realmName: 'Test Realm', useCleartextPasswords: true]
        securityConfig.daoAuthenticationProviderBeanClass = CustomDigestDaoAuthenticationProvider
    }

    void 'a subclass of the Digest provider configured in its place is told what it is'() {
        given:
            CustomDigestDaoAuthenticationProvider provider =
                    applicationContext.getBean('daoAuthenticationProvider', CustomDigestDaoAuthenticationProvider)

        expect:
            provider.realmName == 'Test Realm'
            !provider.passwordAlreadyEncoded
    }

    static class CustomDigestDaoAuthenticationProvider extends DigestDaoAuthenticationProvider {

        CustomDigestDaoAuthenticationProvider(UserDetailsService userDetailsService) {
            super(userDetailsService)
        }
    }
}

class CustomProviderDigestPluginBeansSpec extends AbstractPluginBeansSpec {

    protected void configure(Object securityConfig) {
        securityConfig.useDigestAuth = true
        securityConfig.daoAuthenticationProviderBeanClass = CustomDaoAuthenticationProvider
    }

    void 'a provider class of the application is given the password encoder with Digest authentication too'() {
        expect:
            applicationContext.getBean('daoAuthenticationProvider', CustomDaoAuthenticationProvider).encoder
                    .is(applicationContext.getBean('passwordEncoder'))
    }

    static class CustomDaoAuthenticationProvider extends DaoAuthenticationProvider {

        CustomDaoAuthenticationProvider(UserDetailsService userDetailsService) {
            super(userDetailsService)
        }

        PasswordEncoder getEncoder() {
            getPasswordEncoder()
        }
    }
}
