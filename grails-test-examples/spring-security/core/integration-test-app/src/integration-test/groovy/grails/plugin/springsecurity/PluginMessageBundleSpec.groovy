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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource

/**
 * Proves a plugin's message bundle resolves through Spring Boot's message source.
 *
 * <p>{@code spring-security-core} is the multi-word case: its build-time descriptor records the
 * hyphenated {@code spring-security-core}, while the plugin the application discovers reports the
 * logical {@code springSecurityCore}. If those two are compared without normalising, every multi-word
 * plugin's bundles are dropped silently — messages simply stop resolving, with no error anywhere. A
 * single-word plugin cannot catch that, because both derivations agree.</p>
 */
class PluginMessageBundleSpec extends AbstractIntegrationSpec {

    @Autowired
    MessageSource messageSource

    void 'a plugin message code resolves from the plugin bundle'() {
        expect:
        messageSource.getMessage('springSecurity.errors.login.expired', null, Locale.ENGLISH) ==
                'Sorry, your account has expired.'
    }

    void 'a plugin bundle is resolved in a translated locale'() {
        expect: 'spring-security-core_de.properties participates, not just the base bundle'
        messageSource.getMessage('springSecurity.errors.login.expired', null, Locale.GERMAN) !=
                messageSource.getMessage('springSecurity.errors.login.expired', null, Locale.ENGLISH)
    }

    void 'the application bundle resolves alongside the plugin bundle'() {
        expect: 'both base names are in the composed spring.messages.basename list'
        messageSource.getMessage('AbstractUserDetailsAuthenticationProvider.disabled', null, Locale.ENGLISH) ==
                'Custom user account is disabled.'
    }
}
