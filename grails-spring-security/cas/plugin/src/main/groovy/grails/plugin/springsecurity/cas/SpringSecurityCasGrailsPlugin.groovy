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
package grails.plugin.springsecurity.cas

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.apereo.cas.client.proxy.Cas20ProxyRetriever
import org.apereo.cas.client.proxy.ProxyGrantingTicketStorageImpl
import org.apereo.cas.client.session.SingleSignOutFilter
import org.apereo.cas.client.session.SingleSignOutHttpSessionListener
import org.apereo.cas.client.validation.Cas20ServiceTicketValidator

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean
import org.springframework.core.Ordered
import org.springframework.security.cas.ServiceProperties
import org.springframework.security.cas.authentication.CasAuthenticationProvider
import org.springframework.security.cas.authentication.NullStatelessTicketCache
import org.springframework.security.cas.web.CasAuthenticationEntryPoint
import org.springframework.security.cas.web.CasAuthenticationFilter
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy

import grails.plugin.springsecurity.BeanTypeResolver
import grails.plugin.springsecurity.SecurityFilterPosition
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugins.Plugin

@Slf4j
@CompileStatic
class SpringSecurityCasGrailsPlugin extends Plugin {

    String grailsVersion = '8.0.0-SNAPSHOT > *'
    String title = 'Apereo CAS support for the Spring Security plugin.'
    String description = 'Apereo CAS support for the Spring Security plugin.'
    String documentation = 'https://apache.github.io/grails-spring-security'
    String license = 'APACHE'
    List loadAfter = ['springSecurityCore']
    def organization = [name: 'Grails', url: 'https://grails.apache.org/']
    def issueManagement = [system: 'Github', url: 'https://github.com/apache/grails-core/issues']
    def scm = [url: 'https://github.com/apache/grails-core']
    def profiles = ['web']

    @CompileDynamic
    Closure doWithSpring() {
        { ->

            def conf = SpringSecurityUtils.securityConfig
            if (!conf || !conf.active) {
                return
            }

            SpringSecurityUtils.loadSecondaryConfig 'DefaultCasSecurityConfig'
            // have to get again after overlaying DefaultCasSecurityConfig
            conf = SpringSecurityUtils.securityConfig

            if (!conf.cas.active) {
                return
            }

            boolean printStatusMessages = (conf.printStatusMessages instanceof Boolean) ? conf.printStatusMessages : true

            if (printStatusMessages) {
                println '\nConfiguring Spring Security CAS ...'
            }

            ['serverUrlPrefix', 'loginUri', 'serviceUrl'].each { requiredConfigKey ->
                if (!conf.cas[requiredConfigKey]) {
                    throw new IllegalStateException("cas.${requiredConfigKey} is not set: this is a required configuration value when cas.active is true")
                }
            }

            if (conf.cas.useSingleSignout) {

                // Session fixation prevention breaks single signout because the service ticket is
                // mapped to the session id, which changes when the session is replaced on login.
                // Disabling it is a security trade-off the application has opted into, so say so.
                String message = '''
    WARNING: cas.useSingleSignout is enabled, so session fixation prevention has been disabled.
    CAS maps the service ticket to the HTTP session id, and a logout request cannot be matched to a
    session that was replaced when the user authenticated. Set cas.useSingleSignout to false to keep
    session fixation prevention and handle logout in the application instead.
    '''
                println message
                log.warn message

                // Setting the config value is not enough on its own: this plugin loads after
                // springSecurityCore, which has already defined sessionAuthenticationStrategy from
                // the original value. The bean is therefore redefined here as well, the same way
                // the core plugin defines it when the setting is off. The value is still updated so
                // that anything reading the config later sees what is actually in effect.
                conf.useSessionFixationPrevention = false

                Class beanTypeResolverClass = (conf.beanTypeResolverClass ?: BeanTypeResolver) as Class
                def casBeanTypeResolver = beanTypeResolverClass.newInstance(conf, grailsApplication)
                sessionAuthenticationStrategy(casBeanTypeResolver.resolveType(
                        'sessionAuthenticationStrategy', NullAuthenticatedSessionStrategy))

                singleSignOutFilter(SingleSignOutFilter) {
                    ignoreInitConfiguration = true
                }

                singleSignOutFilterRegistrationBean(FilterRegistrationBean) {
                    name = 'CAS Single Sign Out Filter'
                    filter = ref('singleSignOutFilter')
                    order = Ordered.HIGHEST_PRECEDENCE
                }

                singleSignOutHttpSessionListener(ServletListenerRegistrationBean, new SingleSignOutHttpSessionListener())
            }

            SpringSecurityUtils.registerProvider 'casAuthenticationProvider'
            SpringSecurityUtils.registerFilter 'casAuthenticationFilter', SecurityFilterPosition.CAS_FILTER

            // TODO  document NullProxyGrantingTicketStorage
            casProxyGrantingTicketStorage(ProxyGrantingTicketStorageImpl)

            authenticationEntryPoint(CasAuthenticationEntryPoint) {
                serviceProperties = ref('casServiceProperties')
                loginUrl = conf.cas.serverUrlPrefix + conf.cas.loginUri
            }

            casServiceProperties(ServiceProperties) {
                service = conf.cas.serviceUrl
                sendRenew = conf.cas.sendRenew // false
                artifactParameter = conf.cas.artifactParameter // 'ticket'
                serviceParameter = conf.cas.serviceParameter // 'service'
            }

            casAuthenticationFilter(CasAuthenticationFilter) {
                authenticationManager = ref('authenticationManager')
                sessionAuthenticationStrategy = ref('sessionAuthenticationStrategy')
                authenticationSuccessHandler = ref('authenticationSuccessHandler')
                authenticationFailureHandler = ref('authenticationFailureHandler')
                rememberMeServices = ref('rememberMeServices')
                authenticationDetailsSource = ref('authenticationDetailsSource')
                serviceProperties = ref('casServiceProperties')
                proxyGrantingTicketStorage = ref('casProxyGrantingTicketStorage')
                filterProcessesUrl = conf.cas.filterProcessesUrl // '/login/cas'
                continueChainBeforeSuccessfulAuthentication = conf.apf.continueChainBeforeSuccessfulAuthentication
                // false
                allowSessionCreation = conf.apf.allowSessionCreation // true
                // Only set when configured: CasAuthenticationFilter rejects a null pattern, and an
                // unset receptor is how the filter expresses that proxy support is disabled.
                if (conf.cas.proxyReceptorUrl) {
                    proxyReceptorUrl = conf.cas.proxyReceptorUrl
                }
            }

            casProxyRetriever(Cas20ProxyRetriever, conf.cas.serverUrlPrefix, conf.cas.serverUrlEncoding /*'UTF-8'*/)

            casTicketValidator(Cas20ServiceTicketValidator, conf.cas.serverUrlPrefix) {
                proxyRetriever = ref('casProxyRetriever')
                proxyGrantingTicketStorage = ref('casProxyGrantingTicketStorage')
                proxyCallbackUrl = conf.cas.proxyCallbackUrl
                renew = conf.cas.sendRenew // false
            }

            casStatelessTicketCache(NullStatelessTicketCache)

            casAuthenticationProvider(CasAuthenticationProvider) {
                authenticationUserDetailsService = ref('authenticationUserDetailsService')
                serviceProperties = ref('casServiceProperties')
                ticketValidator = ref('casTicketValidator')
                statelessTicketCache = ref('casStatelessTicketCache')
                key = conf.cas.key // 'grails-spring-security-cas'
            }

            if (printStatusMessages) {
                println '... finished configuring Spring Security CAS\n'
            }
        }
    }
}
