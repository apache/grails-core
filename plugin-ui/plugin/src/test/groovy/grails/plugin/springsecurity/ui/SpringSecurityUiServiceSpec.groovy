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
package grails.plugin.springsecurity.ui

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.ui.strategy.DefaultPropertiesStrategy
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class SpringSecurityUiServiceSpec extends Specification implements ServiceUnitTest<SpringSecurityUiService> {

    void cleanup() {
        SpringSecurityUtils.resetSecurityConfig()
    }

    void "forgot password email subject is loaded from config if config exists"() {
        given: "the subject text exists in messages.properties"
        addForgotPasswordEmailSubjectToMessageSource()

        and: "the legacy config file exists"
        if (hasConfig) {
            SpringSecurityUtils.securityConfig.ui.forgotPassword.emailSubject = 'This is from the config'
        }

        and: "the properties strategy is set"
        service.uiPropertiesStrategy = new DefaultPropertiesStrategy(springSecurityUiService: service)

        and: "the service is initialized"
        updateFromConfig()

        when: "the value of forgotPasswordEmailSubject is requested"
        String results = service.forgotPasswordEmailSubject

        then: "the value from config is used if it exists, else the value from messages.properties is used"
        results == expectedResults

        where:
        hasConfig | expectedResults
        true      | 'This is from the config'
        false     | 'Password Reset'
    }

    private void updateFromConfig() {
        service.messageSource = messageSource
        service.initialize()
    }

    protected void addForgotPasswordEmailSubjectToMessageSource() {
        messageSource.addMessage 'spring.security.ui.forgotPassword.email.subject', Locale.US, 'Password Reset'
    }
}