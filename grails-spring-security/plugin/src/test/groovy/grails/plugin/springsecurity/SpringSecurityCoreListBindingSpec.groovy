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

import jakarta.servlet.Filter

import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.ByteArrayResource
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetailsService

import grails.core.GrailsApplication
import org.grails.config.yaml.YamlPropertySourceLoader

import spock.lang.Specification

class SpringSecurityCoreListBindingSpec extends Specification {

    void 'the plugin bridges roles from a YAML list with placeholders'() {
        given:
        def configuredFilters = new TreeMap(SpringSecurityUtils.configuredOrderedFilters)
        def yaml = '''\
            spring:
              security:
                user:
                  name: example
                  password: example-password
                  roles: [READER, "${EXAMPLE_USER_ROLE:EDITOR}"]
            '''.stripIndent()
        def context = new GenericApplicationContext()
        context.environment.propertySources.addFirst(new YamlPropertySourceLoader().load('test', new ByteArrayResource(yaml.bytes)).first())
        def provider = Stub(AuthenticationProvider)
        def authenticationManager = new ProviderManager([provider])
        [
                roleHierarchy: new Expando(hierarchy: ''),
                securityFilterChains: [],
                testFilter: Stub(Filter),
                testVoter: new Object(),
                accessDecisionManager: new Expando(decisionVoters: []),
                testProvider: provider,
                authenticationManager: authenticationManager,
                userDetailsService: Stub(UserDetailsService),
                testLogoutHandler: new Object(),
                logoutHandlers: [],
                testAfterInvocationProvider: new Object(),
                afterInvocationManager: new Expando(providers: [])
        ].each { name, bean -> context.beanFactory.registerSingleton(name, bean) }
        context.refresh()
        SpringSecurityUtils.securityConfig = new ConfigSlurper().parse('''
            active = true
            securityConfigType = 'InterceptUrlMap'
            roleHierarchy = ''
            filterChain.filterNames = ['testFilter']
            voterNames = ['testVoter']
            providerNames = ['testProvider']
            logout.handlerNames = ['testLogoutHandler']
            afterInvocationManagerProviderNames = ['testAfterInvocationProvider']
            componentBased {
                autoMergeSecurityFilterChain = false
                autoMergeAuthenticationProviders = false
                autoChainUserDetailsServices = false
            }
        ''')
        def plugin = new SpringSecurityCoreGrailsPlugin(grailsApplication: Stub(GrailsApplication), applicationContext: context)

        when:
        plugin.doWithApplicationContext()
        def authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated('example', 'example-password'))

        then:
        authentication.authenticated
        authentication.principal.authorities*.authority.toSet() == ['ROLE_READER', 'ROLE_EDITOR'] as Set

        cleanup:
        context?.close()
        SpringSecurityUtils.resetSecurityConfig()
        ReflectionUtils.application = null
        SpringSecurityUtils.configuredOrderedFilters.clear()
        SpringSecurityUtils.configuredOrderedFilters.putAll(configuredFilters)
    }
}
