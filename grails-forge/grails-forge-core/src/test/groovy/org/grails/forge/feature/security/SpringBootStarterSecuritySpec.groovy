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

package org.grails.forge.feature.security

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Category
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options
import spock.lang.Unroll

class SpringBootStarterSecuritySpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'the feature secures the app with plain spring security, a user domain and a seeded admin'() {
        when:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['spring-boot-starter-security'])

        then: 'the starter dependency is added'
        output['build.gradle'].contains('implementation "org.springframework.boot:spring-boot-starter-security"')

        and: 'the User domain implements UserDetails with a roles association'
        def user = output['grails-app/domain/example/grails/User.groovy']
        user.contains('package example.grails')
        user.contains('class User implements UserDetails')
        user.contains('static hasMany = [roles: String]')
        user.contains('new SimpleGrantedAuthority(it)')

        and: 'the scaffolded controller and UserDetailsService-backed service are generated'
        output['grails-app/controllers/example/grails/UserController.groovy'].contains('@Scaffold(RestfulServiceController<User>)')
        def service = output['grails-app/services/example/grails/UserService.groovy']
        service.contains('@Scaffold(User)')
        service.contains('class UserService implements UserDetailsService')
        service.contains("User.findByUsername(username, [fetch: [roles: 'join']])")

        and: 'SecurityConfig enables form login with a delegating password encoder'
        def config = output['src/main/groovy/example/grails/SecurityConfig.groovy']
        config.contains('@EnableWebSecurity')
        config.contains('PasswordEncoderFactories.createDelegatingPasswordEncoder()')
        config.contains('.formLogin { }')
        config.contains(".logout { it.logoutSuccessUrl('/') }")

        and: 'Application imports the security configuration'
        def application = output['grails-app/init/example/grails/Application.groovy']
        application.contains('import org.springframework.context.annotation.Import')
        application.contains('@Import(SecurityConfig)')

        and: 'BootStrap seeds an admin user with a generated password'
        def bootStrap = output['grails-app/init/example/grails/BootStrap.groovy']
        bootStrap.contains('import org.springframework.security.crypto.factory.PasswordEncoderFactories')
        bootStrap.contains('PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password)')
        bootStrap.contains('Generated admin credentials: admin')

        and: 'a domain spec covering constraints and authorities is generated'
        def userSpec = output['src/test/groovy/example/grails/UserSpec.groovy']
        userSpec.contains('DomainUnitTest<User>')
        userSpec.contains("authorities*.authority == ['ROLE_ADMIN']")
    }

    void 'the feature appears in its own Spring Security category with the agreed title'() {
        given:
        def feature = beanContext.getBean(SpringBootStarterSecurity)

        expect:
        feature.name == 'spring-boot-starter-security'
        feature.title == 'Spring Boot Starter Security'
        feature.category == Category.SPRING_SECURITY
        feature.description.contains('does NOT use any Grails plugins')
    }

    void 'a default web app is untouched without the feature'() {
        when:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))

        then:
        !output['build.gradle'].contains('spring-boot-starter-security')
        !output['grails-app/init/example/grails/Application.groovy'].contains('@Import(SecurityConfig)')
        !output['grails-app/init/example/grails/BootStrap.groovy'].contains('Generated admin credentials')
        !output.containsKey('grails-app/domain/example/grails/User.groovy')
        !output.containsKey('src/main/groovy/example/grails/SecurityConfig.groovy')
    }

    @Unroll
    void 'the feature is not supported for #applicationType'(ApplicationType applicationType) {
        when:
        generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS), ['spring-boot-starter-security'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The requested feature does not exist: spring-boot-starter-security'

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.REST_API, ApplicationType.WEB_PLUGIN]
    }
}
