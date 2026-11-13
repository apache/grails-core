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

import spock.lang.Issue
import spock.lang.Specification

import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint

import grails.plugin.springsecurity.web.authentication.AjaxAwareAuthenticationEntryPoint
import grails.testing.web.GrailsWebUnitTest

@Issue(['https://github.com/apache/grails-core/issues/15863', 'https://github.com/apache/grails-core/issues/15864'])
class X509EntryPointSpec extends Specification implements GrailsWebUnitTest {

    void setup() {
        ReflectionUtils.application = SpringSecurityUtils.application = grailsApplication
    }

    void cleanup() {
        SpringSecurityUtils.resetSecurityConfig()
        ReflectionUtils.application = null
        SpringSecurityUtils.application = null
    }

    private Object entryPoint(boolean continueFilterChain) {
        ConfigObject conf = new ConfigObject()
        conf.active = true
        conf.useX509 = true
        conf.x509.continueFilterChainOnUnsuccessfulAuthentication = continueFilterChain
        ReflectionUtils.securityConfig = conf
        defineBeans(new SpringSecurityCoreGrailsPlugin(grailsApplication: grailsApplication).doWithSpring())
        assert applicationContext.containsBean('x509ProcessingFilter'), 'the X.509 beans are defined'
        applicationContext.getBean('authenticationEntryPoint')
    }

    void 'a request without a usable certificate goes to the login form when other mechanisms may handle it'() {
        expect: 'with x509.continueFilterChainOnUnsuccessfulAuthentication true, the default, the entry point is the one that redirects to the login form, not a bare 403'
        entryPoint(true) instanceof AjaxAwareAuthenticationEntryPoint
    }

    void 'a request without a usable certificate gets a 403 when certificates are the only mechanism'() {
        expect:
        entryPoint(false) instanceof Http403ForbiddenEntryPoint
    }
}
