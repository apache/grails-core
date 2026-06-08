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

import spock.lang.Specification

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.SecurityFilterChain

import grails.testing.mixin.integration.Integration

@Integration
class SecurityAutoConfigurationExcluderIntegrationSpec extends Specification {

    @Autowired
    ApplicationContext applicationContext

    void "SecurityAutoConfigurationExcluder class is on the classpath"() {
        expect:
        Class.forName(
                'grails.plugin.springsecurity.SecurityAutoConfigurationExcluder'
        )
    }

    void "SecurityAutoConfiguration bean is not registered"() {
        given:
        def secAutoConfig = Class.forName(
                'org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration'
        )

        expect:
        applicationContext
                .getBeanNamesForType(secAutoConfig).length == 0
    }

    void "SecurityFilterAutoConfiguration bean is not registered"() {
        given:
        def secFilterAutoConfig = Class.forName(
                'org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration'
        )

        expect:
        applicationContext
                .getBeanNamesForType(secFilterAutoConfig).length == 0
    }

    void "no duplicate SecurityFilterChain beans from auto-configuration"() {
        given:
        def filterChainBeans = applicationContext
                .getBeanNamesForType(SecurityFilterChain)

        expect:
        filterChainBeans.length <= 1
    }

    void "only the plugin UserDetailsService is registered"() {
        given:
        def udsBeans = applicationContext
                .getBeanNamesForType(UserDetailsService)

        expect:
        udsBeans.length >= 1
        udsBeans.any { it.contains('userDetailsService') }
    }
}
