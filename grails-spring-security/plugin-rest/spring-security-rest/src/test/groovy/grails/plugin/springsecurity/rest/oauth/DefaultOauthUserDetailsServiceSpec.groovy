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
package grails.plugin.springsecurity.rest.oauth

import grails.core.DefaultGrailsApplication
import grails.plugin.springsecurity.ReflectionUtils
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.userdetails.DefaultPreAuthenticationChecks
import org.grails.spring.GrailsApplicationContext
import org.pac4j.core.profile.CommonProfile
import org.springframework.security.authentication.AccountStatusException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification

class DefaultOauthUserDetailsServiceSpec extends Specification {

    @Shared
    def application
    def setupSpec() {
        application = new DefaultGrailsApplication()
        application.mainContext = new GrailsApplicationContext()
        ReflectionUtils.application = SpringSecurityUtils.application = application
    }

    def setup() {
        SpringSecurityUtils.resetSecurityConfig()
    }

    void "it load users either existing or not"() {
        given:
        application.config.merge(['grails.plugin.springsecurity.userLookup.userDomainClassName':'demo.User'])
        
        OauthUserDetailsService service = new DefaultOauthUserDetailsService()
        service.preAuthenticationChecks = new DefaultPreAuthenticationChecks()
        service.userDetailsService = new InMemoryUserDetailsManager([])
        UserDetails jimi = new User('jimi', 'jimispassword', [new SimpleGrantedAuthority('ROLE_USER'), new SimpleGrantedAuthority('ROLE_ADMIN')])
        service.userDetailsService.createUser(jimi)


        when:
        OauthUser oauthUser = service.loadUserByUserProfile([id: username] as CommonProfile, [])

        then:
        oauthUser.authorities.size() == authorities

        where:
        username    | authorities
        'test'      | 0
        'jimi'      | 2
    }

    void "when a user exists but it's disabled, it throws an exception"() {
        given:
        application.config.merge(['grails.plugin.springsecurity.userLookup.userDomainClassName': 'demo.User'])

        OauthUserDetailsService service = new DefaultOauthUserDetailsService()
        service.preAuthenticationChecks = new DefaultPreAuthenticationChecks()
        service.userDetailsService = new InMemoryUserDetailsManager([])
        UserDetails jimi = new User('jimi', 'jimispassword', false, false, false, false, [new SimpleGrantedAuthority('ROLE_USER'), new SimpleGrantedAuthority('ROLE_ADMIN')])
        service.userDetailsService.createUser(jimi)

        when:
        OauthUser oauthUser = service.loadUserByUserProfile([id: 'jimi'] as CommonProfile, [])

        then:
        thrown(AccountStatusException)
    }

    void "if userLookUp.userDomainClassName not set, don't look for the user with userDetailService; return basic OauthUser instead"() {
        given:
        application.config.merge(['grails.plugin.springsecurity.userLookup.userDomainClassName': null])

        OauthUserDetailsService service = new DefaultOauthUserDetailsService()
        Collection<GrantedAuthority> expectedRoles = [new MockGrantedAuthority(authority: 'ROLE_USER')]

        when:
        OauthUser oauthUser = service.loadUserByUserProfile([id: 'jimi'] as CommonProfile, expectedRoles)

        then:
        oauthUser
        oauthUser.username == 'jimi'
        oauthUser.authorities
        oauthUser.authorities.size() == expectedRoles.size()
        oauthUser.authorities.first() == expectedRoles.first()
    }

}

class MockGrantedAuthority implements GrantedAuthority {
    String authority
}