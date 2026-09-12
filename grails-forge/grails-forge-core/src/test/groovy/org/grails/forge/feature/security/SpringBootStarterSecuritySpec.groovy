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
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
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

        and: 'the credentials are required, so an account cannot be saved without them'
        user.contains('username nullable: false, blank: false, unique: true')

        and: 'the default Hibernate app maps to a table, since user is a reserved word in several databases'
        user.contains('''    static mapping = {
        table 'users'
    }''')

        and: 'the password field is marked as a password so scaffolding masks it'
        user.contains('password nullable: false, blank: false, password: true')

        and: 'the scaffolded controller and UserDetailsService-backed service are generated'
        output['grails-app/controllers/example/grails/UserController.groovy'].contains('@Scaffold(RestfulServiceController<User>)')
        def service = output['grails-app/services/example/grails/UserService.groovy']
        service.contains('@Scaffold(User)')
        service.contains('class UserService implements UserDetailsService')
        service.contains("User.findByUsername(username, [fetch: [roles: 'join']])")

        and: 'no separate configuration class is generated'
        !output.containsKey('src/main/groovy/example/grails/SecurityConfig.groovy')

        and: 'Application declares the security beans through the beans DSL'
        def application = output['grails-app/init/example/grails/Application.groovy']
        application.contains('import org.springframework.security.web.SecurityFilterChain')
        application.contains('@EnableWebSecurity')
        application.contains('def beans = {')
        application.contains('bean(PasswordEncoder) {')
        application.contains('PasswordEncoderFactories.createDelegatingPasswordEncoder()')
        application.contains("bean('filterChain', SecurityFilterChain) { HttpSecurity http ->")
        application.contains('.formLogin { }')
        application.contains(".logout { it.logoutSuccessUrl('/') }")
        !application.contains('@Import(SecurityConfig)')

        and: 'the home page and static assets are public, the user admin is ROLE_ADMIN, the rest requires login'
        application.contains("it.requestMatchers('/', '/index', '/index.gsp', '/error', '/assets/**').permitAll()")
        application.contains(".requestMatchers('/user/**').hasRole('ADMIN')")
        application.contains('.anyRequest().authenticated()')
        !application.contains('anyRequest().permitAll()')

        and: 'BootStrap seeds an admin user with a generated password'
        def bootStrap = output['grails-app/init/example/grails/BootStrap.groovy']
        bootStrap.contains('import org.springframework.security.crypto.factory.PasswordEncoderFactories')
        bootStrap.contains('PasswordEncoderFactories.createDelegatingPasswordEncoder()')
        bootStrap.contains('passwordEncoder.encode(password)')
        bootStrap.contains('Generated admin credentials: admin')

        and: 'BootStrap also seeds a non-admin account so the ROLE_ADMIN rules are exercised'
        bootStrap.contains("roles: ['ROLE_USER']")
        bootStrap.contains('Generated user credentials: user')

        and: 'a domain spec covering constraints and authorities is generated'
        def userSpec = output['src/test/groovy/example/grails/UserSpec.groovy']
        userSpec.contains('DomainUnitTest<User>')
        userSpec.contains("authorities*.authority == ['ROLE_ADMIN']")

        and: 'that spec asserts the nullable and blank constraints separately'
        userSpec.contains("getFieldError('username').code == 'nullable'")
        userSpec.contains("getFieldError('username').code == 'blank'")
        userSpec.contains("getFieldError('password').code == 'nullable'")
        userSpec.contains("getFieldError('password').code == 'blank'")
    }

    @Unroll
    void 'the User domain maps through the #gorm mapping directive'() {
        when:
        def options = new Options(DevelopmentReloading.DEVTOOLS, gorm, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION)
        def user = generate(ApplicationType.WEB, options, ['spring-boot-starter-security'])['grails-app/domain/example/grails/User.groovy']

        then: 'the directive matches the data implementation'
        user.contains(directive)

        and: 'no directive from another data implementation leaks in'
        !user.contains(foreign)

        where:
        gorm                | directive             | foreign
        GormImpl.HIBERNATE5 | "table 'users'"       | "collection 'users'"
        GormImpl.HIBERNATE7 | "table 'users'"       | "collection 'users'"
        GormImpl.MONGODB    | "collection 'users'"  | "table 'users'"
    }

    void 'the User domain carries no mapping block when neither Hibernate nor MongoDB is used'() {
        when:
        def options = new Options(DevelopmentReloading.DEVTOOLS, GormImpl.NEO4J, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION)
        def user = generate(ApplicationType.WEB, options, ['spring-boot-starter-security'])['grails-app/domain/example/grails/User.groovy']

        then: 'no persistence-unit directive is emitted for a store that uses neither'
        !user.contains('static mapping')
        !user.contains("table 'users'")
        !user.contains("collection 'users'")

        and: 'the rest of the domain is still generated'
        user.contains('class User implements UserDetails')
        user.contains('username nullable: false, blank: false, unique: true')
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
        !output['grails-app/init/example/grails/Application.groovy'].contains('def beans = {')
        !output['grails-app/init/example/grails/Application.groovy'].contains('@EnableWebSecurity')
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
