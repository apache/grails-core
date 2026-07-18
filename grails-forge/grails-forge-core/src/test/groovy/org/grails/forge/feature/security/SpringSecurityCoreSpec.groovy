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
import org.grails.forge.BuildBuilder
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.fixture.CommandOutputFixture

class SpringSecurityCoreSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test gradle spring-security feature adds the plugin dependency'() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['spring-security'])
                .render()

        then:
        template.contains('implementation "org.apache.grails:grails-spring-security"')
    }

    void 'test spring-security feature configures the domain class lookups'() {
        when:
        GeneratorContext commandContext = buildGeneratorContext(['spring-security'])

        then:
        commandContext.configuration.get('grails.plugin.springsecurity.userLookup.userDomainClassName') == 'example.grails.SecurityUser'
        commandContext.configuration.get('grails.plugin.springsecurity.userLookup.authorityJoinClassName') == 'example.grails.SecurityUserRole'
        commandContext.configuration.get('grails.plugin.springsecurity.authority.className') == 'example.grails.SecurityRole'

        and: 'the standard permit-all static rules are configured'
        List<Map<String, Object>> staticRules = (List<Map<String, Object>>) commandContext.configuration
                .get('grails.plugin.springsecurity.controllerAnnotations.staticRules')
        staticRules.find { it.pattern == '/' }.access == ['permitAll']
        staticRules.find { it.pattern == '/assets/**' }.access == ['permitAll']
    }

    void 'test spring-security feature generates the security domain classes'() {
        when:
        def output = generate(['spring-security'])
        def user = output['grails-app/domain/example/grails/SecurityUser.groovy']
        def role = output['grails-app/domain/example/grails/SecurityRole.groovy']
        def userRole = output['grails-app/domain/example/grails/SecurityUserRole.groovy']

        then:
        user
        user.contains('package example.grails')
        user.contains('class SecurityUser implements Serializable')
        user.contains('Set<SecurityRole> getAuthorities()')
        user.contains('protected void encodePassword()')

        and:
        role
        role.contains('class SecurityRole implements Serializable')
        role.contains('String authority')

        and:
        userRole
        userRole.contains('class SecurityUserRole implements Serializable')
        userRole.contains("id composite: ['securityUser', 'securityRole']")

        and: 'the password encoder listener is generated and registered as a bean'
        def listener = output['src/main/groovy/example/grails/SecurityUserPasswordEncoderListener.groovy']
        listener
        listener.contains('class SecurityUserPasswordEncoderListener')
        listener.contains('@Listener(SecurityUser)')
        def resources = output['grails-app/conf/spring/resources.groovy']
        resources.contains('import example.grails.SecurityUserPasswordEncoderListener')
        resources.contains('securityUserPasswordEncoderListener(SecurityUserPasswordEncoderListener)')
    }

    void 'test readme.md with feature spring-security contains links to documentation'() {
        when:
        def output = generate(['spring-security'])
        def readme = output['README.md']

        then:
        readme
        readme.contains('guide/security.html')
    }

}
