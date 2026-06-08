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

package grails.plugin.springsecurity.oauth2

import grails.plugin.springsecurity.ReflectionUtils
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugins.Plugin
import groovy.util.logging.Slf4j

@Slf4j
class SpringSecurityOauth2GrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = '7.0.0 > *'
    List loadAfter = ['spring-security-core']

    // TODO Fill in these fields
    def title = 'Spring Security Oauth2' // Headline display name of the plugin
    def author = 'Johannes Brunswicker'
    def authorEmail = ''
    def description = '''\
This plugin provides the capability to authenticate via oauth. Depends on grails-spring-security.
'''
    def profiles = ['web']
    def license = 'APACHE'

    @Override
    Closure doWithSpring() {
        { ->
            ReflectionUtils.application = grailsApplication
            if (grailsApplication.warDeployed) {
                SpringSecurityUtils.resetSecurityConfig()
            }
            SpringSecurityUtils.application = grailsApplication

            // Check if there is an SpringSecurity configuration
            def coreConf = SpringSecurityUtils.securityConfig
            boolean printStatusMessages = (coreConf.printStatusMessages instanceof Boolean) ? coreConf.printStatusMessages : true
            if (!coreConf || !coreConf.active) {
                if (printStatusMessages) {
                    println('ERROR: There is no SpringSecurity configuration or SpringSecurity is disabled')
                    println('ERROR: Stopping configuration of SpringSecurity Oauth2')
                }
                return
            }

            if (printStatusMessages) {
                println('Configuring Spring Security OAuth2 plugin...')
            }

            SpringSecurityUtils.loadSecondaryConfig('DefaultSpringSecurityOAuth2Config')
            SpringSecurityUtils.securityConfig.controllerAnnotations.staticRules.add([pattern:'/oauth2/**', access:['permitAll']])

            grailsApplication.getArtefact('Domain','User')

            if (printStatusMessages) {
                println('... finished configuring Spring Security OAuth2\n')
            }
        }
    }

}
