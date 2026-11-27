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

import org.springframework.beans.BeansException
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

    private Object entryPoint(Object continueFilterChain) {
        ConfigObject conf = new ConfigObject()
        conf.active = true
        conf.useX509 = true
        conf.x509.continueFilterChainOnUnsuccessfulAuthentication = continueFilterChain
        ReflectionUtils.securityConfig = conf
        defineBeans(new SpringSecurityCoreGrailsPlugin(grailsApplication: grailsApplication).doWithSpring())
        assert applicationContext.containsBean('x509ProcessingFilter'), 'the X.509 beans are defined'
        applicationContext.getBean('authenticationEntryPoint')
    }

    void 'a request without a usable certificate goes to the form or gets a 403 by the setting #value'() {
        expect: 'true, the default, keeps the entry point that redirects to the login form; false makes it a bare 403'
        entryPoint(value).class == type

        where:
        value     || type
        true      || AjaxAwareAuthenticationEntryPoint
        'true'    || AjaxAwareAuthenticationEntryPoint
        'TRUE'    || AjaxAwareAuthenticationEntryPoint
        ' true '  || AjaxAwareAuthenticationEntryPoint
        'yes'     || AjaxAwareAuthenticationEntryPoint
        'on'      || AjaxAwareAuthenticationEntryPoint
        '1'       || AjaxAwareAuthenticationEntryPoint
        false     || Http403ForbiddenEntryPoint
        'false'   || Http403ForbiddenEntryPoint
        'FALSE'   || Http403ForbiddenEntryPoint
        ' false ' || Http403ForbiddenEntryPoint
        'no'      || Http403ForbiddenEntryPoint
        'off'     || Http403ForbiddenEntryPoint
        '0'       || Http403ForbiddenEntryPoint
    }

    void 'the setting not set at all keeps the default, which continues the chain'() {
        expect:
        entryPoint(null).class == AjaxAwareAuthenticationEntryPoint
    }

    void 'a value that is no boolean fails at startup, as it did when the filter converted it'() {
        when:
        entryPoint('maybe')

        then: 'the conversion error names the value, so the setting that is wrong can be found'
        BeansException e = thrown()
        e.message.contains('maybe')
    }

    void 'the filter is told the setting as one boolean, also when it is the string #value'() {
        when:
        entryPoint(value)

        then: 'a request without a certificate is refused rather than passed down the chain when it is false'
        applicationContext.getBeanDefinition('x509ProcessingFilter').propertyValues
                .get('continueFilterChainOnUnsuccessfulAuthentication') == expected

        where:
        value     || expected
        'true'    || true
        'false'   || false
        'yes'     || true
        'on'      || true
        '1'       || true
        ' true '  || true
        'no'      || false
        'off'     || false
        '0'       || false
    }
}
