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

class GrailsSpringSecuritySpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'the feature secures the app with the plugin over the classic domain model'() {
        when:
        def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS), ['grails-spring-security'])

        then: 'the plugin dependency is added, not the plain starter or the UI plugin'
        output['build.gradle'].contains('implementation "org.apache.grails:grails-spring-security"')
        !output['build.gradle'].contains('spring-boot-starter-security')
        !output['build.gradle'].contains('grails-spring-security-ui')

        and: 'the classic User/Role/UserRole triple is generated, matching the UI flavor'
        output['grails-app/domain/example/grails/User.groovy'].contains('Set<Role> getAuthorities()')
        !output['grails-app/domain/example/grails/User.groovy'].contains('springSecurityService')
        output['grails-app/domain/example/grails/Role.groovy'].contains('String authority')
        output['grails-app/domain/example/grails/UserRole.groovy'].contains('static UserRole create(User user, Role role')

        and: 'the user admin stays scaffolded with a plain data service, authentication served by the plugin GORM UserDetailsService'
        output['grails-app/controllers/example/grails/UserController.groovy'].contains('@Scaffold(RestfulServiceController<User>)')
        output['grails-app/services/example/grails/UserService.groovy'].contains('@Scaffold(User)')
        !output['grails-app/services/example/grails/UserService.groovy'].contains('UserDetailsService')
        output['grails-app/conf/spring/resources.groovy'].contains('// Place your Spring DSL code here')

        and: 'the config wires the classic model and guards the user admin'
        def config = output['grails-app/conf/application.groovy']
        config.contains("grails.plugin.springsecurity.userLookup.userDomainClassName = 'example.grails.User'")
        config.contains("grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'example.grails.UserRole'")
        config.contains("grails.plugin.springsecurity.authority.className = 'example.grails.Role'")
        config.contains("[pattern: '/',                    access: ['permitAll']]")
        config.contains("[pattern: '/user/**',             access: ['ROLE_ADMIN']]")
        !config.contains('/register/**')
        !config.contains('/role/**')

        and: 'BootStrap seeds the admin through the role join inside a transaction, encoding explicitly'
        def bootStrap = output['grails-app/init/example/grails/BootStrap.groovy']
        bootStrap.contains('User.withTransaction')
        bootStrap.contains('springSecurityService.encodePassword(password)')
        bootStrap.contains("new Role('ROLE_ADMIN').save(failOnError: true)")
        bootStrap.contains('UserRole.create(admin, adminRole, true)')
        bootStrap.contains('Generated admin credentials: admin')

        and: 'no plain-security artifacts leak in'
        !output.containsKey('src/main/groovy/example/grails/SecurityConfig.groovy')
        !output['grails-app/init/example/grails/Application.groovy'].contains('@Import(SecurityConfig)')

        and: 'a data spec covers the classic model'
        output['src/test/groovy/example/grails/UserSpec.groovy'].contains('[User, Role, UserRole]')
    }

    @Unroll
    void 'the classic User domain maps through the #gorm mapping directive'() {
        when:
        def options = new Options(DevelopmentReloading.DEVTOOLS, gorm, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION)
        def user = generate(ApplicationType.WEB, options, ['grails-spring-security'])['grails-app/domain/example/grails/User.groovy']

        then: 'the directive matches the data implementation'
        user.contains(directive)

        and: 'no directive from another data implementation leaks in'
        !user.contains(foreign)

        where:
        gorm                | directive              | foreign
        GormImpl.HIBERNATE5 | "table name: '`user`'" | "collection 'user'"
        GormImpl.HIBERNATE7 | "table name: '`user`'" | "collection 'user'"
        GormImpl.MONGODB    | "collection 'user'"    | "table name: '`user`'"
    }

    void 'the classic User domain carries no mapping block when neither Hibernate nor MongoDB is used'() {
        when:
        def options = new Options(DevelopmentReloading.DEVTOOLS, GormImpl.NEO4J, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION)
        def user = generate(ApplicationType.WEB, options, ['grails-spring-security'])['grails-app/domain/example/grails/User.groovy']

        then: 'no persistence-unit directive is emitted for a store that uses neither'
        !user.contains('static mapping')
        !user.contains("table name: '`user`'")
        !user.contains("collection 'user'")

        and: 'the rest of the domain is still generated'
        user.contains('class User implements Serializable')
        user.contains('username unique: true, nullable: false')
    }

    void 'the feature shares the Spring Security category and advertises the plugin'() {
        given:
        def feature = beanContext.getBean(GrailsSpringSecurity)

        expect:
        feature.name == 'grails-spring-security'
        feature.title == 'Grails Spring Security Plugin'
        feature.category == Category.SPRING_SECURITY
        feature.description.contains('Grails Spring Security Core plugin')
    }

    void 'the two security features are mutually exclusive'() {
        when:
        generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS),
                ['grails-spring-security', 'spring-boot-starter-security'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith('There can only be one of the following features selected:')
        e.message.contains('grails-spring-security')
        e.message.contains('spring-boot-starter-security')
    }

    @Unroll
    void 'the feature is not supported for #applicationType'(ApplicationType applicationType) {
        when:
        generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS), ['grails-spring-security'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'The requested feature does not exist: grails-spring-security'

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.REST_API, ApplicationType.WEB_PLUGIN]
    }
}
