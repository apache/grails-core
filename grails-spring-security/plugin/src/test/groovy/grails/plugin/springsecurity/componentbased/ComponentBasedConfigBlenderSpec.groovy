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
package grails.plugin.springsecurity.componentbased

import spock.lang.Specification
import spock.lang.Subject

import org.springframework.context.ApplicationContext
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

class ComponentBasedConfigBlenderSpec extends Specification {

    @Subject
    ComponentBasedConfigBlender blender = new ComponentBasedConfigBlender()

    def "mergeUserSecurityFilterChains prepends user beans (higher precedence)"() {
        given:
        SecurityFilterChain pluginChain = Stub(SecurityFilterChain)
        SecurityFilterChain userChain1 = Stub(SecurityFilterChain)
        SecurityFilterChain userChain2 = Stub(SecurityFilterChain)
        ApplicationContext ctx = Mock(ApplicationContext)
        ctx.getBeansOfType(SecurityFilterChain) >> [user1: userChain1, user2: userChain2]

        List<SecurityFilterChain> pluginChains = [pluginChain]

        when:
        int merged = ComponentBasedConfigBlender.mergeUserSecurityFilterChains(ctx, pluginChains)

        then:
        merged == 2
        pluginChains.size() == 3
        pluginChains[0].is(userChain1)
        pluginChains[1].is(userChain2)
        pluginChains[2].is(pluginChain)
    }

    def "mergeUserSecurityFilterChains with no user beans is a no-op"() {
        given:
        SecurityFilterChain pluginChain = Stub(SecurityFilterChain)
        ApplicationContext ctx = Mock(ApplicationContext)
        ctx.getBeansOfType(SecurityFilterChain) >> [:]

        List<SecurityFilterChain> pluginChains = [pluginChain]

        when:
        int merged = ComponentBasedConfigBlender.mergeUserSecurityFilterChains(ctx, pluginChains)

        then:
        merged == 0
        pluginChains.size() == 1
        pluginChains[0].is(pluginChain)
    }

    def "mergeUserAuthenticationProviders appends user beans (plugin providers run first)"() {
        given:
        AuthenticationProvider pluginProvider = Stub(AuthenticationProvider)
        AuthenticationProvider userProvider1 = Stub(AuthenticationProvider)
        AuthenticationProvider userProvider2 = Stub(AuthenticationProvider)
        ApplicationContext ctx = Mock(ApplicationContext)
        ctx.getBeansOfType(AuthenticationProvider) >> [
                pluginP: pluginProvider, // simulates plugin's provider also being a named bean
                userP1: userProvider1,
                userP2: userProvider2,
        ]

        ProviderManager mgr = new ProviderManager([pluginProvider])

        when:
        int merged = ComponentBasedConfigBlender.mergeUserAuthenticationProviders(ctx, mgr)

        then: 'only beans NOT already in the manager are merged'
        merged == 2
        mgr.providers.size() == 3
        mgr.providers[0].is(pluginProvider)
        mgr.providers[1].is(userProvider1)
        mgr.providers[2].is(userProvider2)
    }

    def "mergeUserAuthenticationProviders skips providers already in manager (idempotent)"() {
        given:
        AuthenticationProvider pluginProvider = Stub(AuthenticationProvider)
        AuthenticationProvider userProvider = Stub(AuthenticationProvider)
        ApplicationContext ctx = Mock(ApplicationContext)
        ctx.getBeansOfType(AuthenticationProvider) >> [pluginP: pluginProvider, userP: userProvider]

        ProviderManager mgr = new ProviderManager([pluginProvider, userProvider])

        when:
        int merged = ComponentBasedConfigBlender.mergeUserAuthenticationProviders(ctx, mgr)

        then:
        merged == 0
        mgr.providers.size() == 2
    }

    def "chainUserDetailsServices returns primary unchanged when no other UDS beans"() {
        given:
        UserDetailsService primary = Stub(UserDetailsService)
        ApplicationContext ctx = Mock(ApplicationContext)
        ctx.getBeansOfType(UserDetailsService) >> [primary: primary]

        when:
        def result = ComponentBasedConfigBlender.chainUserDetailsServices(ctx, primary)

        then:
        result.is(primary)
    }

    def "chainUserDetailsServices wraps primary + additional in a chain"() {
        given:
        UserDetailsService primary = Stub(UserDetailsService) {
            loadUserByUsername('alice') >> { throw new UsernameNotFoundException('not in primary') }
            loadUserByUsername('bob') >> User.builder().username('bob').password('{noop}b').roles('USER').build()
        }
        UserDetailsService secondary = Stub(UserDetailsService) {
            loadUserByUsername('alice') >> User.builder().username('alice').password('{noop}a').roles('USER').build()
            loadUserByUsername('bob') >> { throw new UsernameNotFoundException('not in secondary') }
        }
        ApplicationContext ctx = Mock(ApplicationContext)
        ctx.getBeansOfType(UserDetailsService) >> [primary: primary, secondary: secondary]

        when:
        UserDetailsService chained = ComponentBasedConfigBlender.chainUserDetailsServices(ctx, primary)

        then: 'a chained service is returned'
        chained instanceof ChainedUserDetailsService

        and: 'primary is queried first (returns its own user without consulting secondary)'
        chained.loadUserByUsername('bob').username == 'bob'

        and: 'secondary is queried when primary throws UsernameNotFoundException'
        chained.loadUserByUsername('alice').username == 'alice'
    }

    def "ChainedUserDetailsService throws UsernameNotFoundException when no delegate finds the user"() {
        given:
        UserDetailsService primary = Stub(UserDetailsService) {
            loadUserByUsername(_) >> { throw new UsernameNotFoundException('not in primary') }
        }
        UserDetailsService secondary = Stub(UserDetailsService) {
            loadUserByUsername(_) >> { throw new UsernameNotFoundException('not in secondary') }
        }
        ChainedUserDetailsService chained = new ChainedUserDetailsService([primary, secondary])

        when:
        chained.loadUserByUsername('carol')

        then:
        thrown(UsernameNotFoundException)
    }

    def "bridgeSpringSecurityUserProperties returns null when name not set"() {
        expect:
        ComponentBasedConfigBlender.bridgeSpringSecurityUserProperties(null, null, null) == null
        ComponentBasedConfigBlender.bridgeSpringSecurityUserProperties('', null, null) == null
    }

    def "bridgeSpringSecurityUserProperties uses Spring Boot defaults when only name is set"() {
        when:
        InMemoryUserDetailsManager mgr = ComponentBasedConfigBlender
                .bridgeSpringSecurityUserProperties('alice', null, null)

        then:
        mgr != null

        when:
        UserDetails alice = mgr.loadUserByUsername('alice')

        then:
        alice.username == 'alice'
        alice.password == '{noop}user'
        alice.authorities*.authority.toSet() == ['ROLE_USER'] as Set
    }

    def "bridgeSpringSecurityUserProperties applies password and roles"() {
        when:
        InMemoryUserDetailsManager mgr = ComponentBasedConfigBlender
                .bridgeSpringSecurityUserProperties('admin', 'secret', ['ADMIN', 'USER'])

        then:
        UserDetails admin = mgr.loadUserByUsername('admin')
        admin.username == 'admin'
        admin.password == '{noop}secret'
        admin.authorities*.authority.toSet() == ['ROLE_ADMIN', 'ROLE_USER'] as Set
    }
}
