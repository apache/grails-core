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

package grails.plugin.springsecurity.rest

import grails.plugin.springsecurity.SecurityFilterPosition
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.rest.token.generation.SecureRandomTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.GormTokenStorageService
import grails.plugins.Plugin

class SpringSecurityRestGormGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    String grailsVersion = '7.0.0 > *'
    List loadAfter = ['springSecurityRest']
    List pluginExcludes = [
            'grails-app/views/**'
    ]

    String title = 'Spring Security REST Plugin - GORM support'
    String author = 'Alvaro Sanchez-Mariscal'
    String authorEmail = ''
    String description = 'Implements authentication for REST APIs based on Spring Security. It uses a token-based workflow'

    def profiles = ['web']

    // URL to the plugin's documentation
    String documentation = 'https://apache.github.io/grails-spring-security'

    // Extra (optional) plugin metadata
    String license = 'APACHE'
    def organization = [name: 'Grails', url: 'https://www.grails.org/']

    def issueManagement = [system: 'GitHub', url: 'https://github.com/apache/grails-spring-security/issues']
    def scm = [ url: 'https://github.com/apache/grails-spring-security']

    Closure doWithSpring() { {->
        def conf = SpringSecurityUtils.securityConfig
        if (!conf || !conf.active || !conf.rest.active) {
            return
        }

        boolean printStatusMessages = (conf.printStatusMessages instanceof Boolean) ? conf.printStatusMessages : true

        if (printStatusMessages) {
            println '\t... with GORM support'
        }

        SpringSecurityUtils.loadSecondaryConfig 'DefaultRestGormSecurityConfig'
        conf = SpringSecurityUtils.securityConfig

        SpringSecurityUtils.registerFilter 'restLogoutFilter', SecurityFilterPosition.LOGOUT_FILTER.order - 1

        tokenStorageService(GormTokenStorageService) {
            userDetailsService = ref('userDetailsService')
        }

        tokenGenerator(SecureRandomTokenGenerator)
    }}

    void doWithApplicationContext() {
        def conf = SpringSecurityUtils.securityConfig
        if (!conf || !conf.active || !conf.rest.active) {
            return
        }

        applicationContext.getBean(RestAuthenticationProvider).useJwt = false
    }
}
