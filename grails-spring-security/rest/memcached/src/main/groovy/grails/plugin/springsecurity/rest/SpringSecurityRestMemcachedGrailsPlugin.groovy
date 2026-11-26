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

import net.spy.memcached.DefaultHashAlgorithm
import net.spy.memcached.spring.MemcachedClientFactoryBean

import org.springframework.beans.factory.BeanRegistrar

import grails.plugin.springsecurity.SecurityFilterPosition
import grails.plugin.springsecurity.SpringSecurityBeanDefinitions
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.rest.token.generation.SecureRandomTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.memcached.CustomSerializingTranscoder
import grails.plugin.springsecurity.rest.token.storage.memcached.MemcachedTokenStorageService
import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor

@GrailsPluginDescriptor(
        loadAfter = ['springSecurityRest'])
class SpringSecurityRestMemcachedGrailsPlugin extends Plugin {

    /**
     * Registers the beans of the plugin, declared in the bean builder DSL by {@link #beanDefinitions()},
     * through a post-processor that Spring runs before it reads the configuration classes, in plugin
     * load order. A plugin loaded after the core plugin replaces its beans of the same name.
     */
    @Override
    BeanRegistrar beanRegistrar() {
        SpringSecurityBeanDefinitions.registrar(this, 'springSecurityRestMemcachedBeanDefinitionsPostProcessor') { -> beanDefinitions() }
    }

    /**
     * The beans of the Spring Security REST Memcached token storage plugin, in the bean builder DSL.
     *
     * @return the bean builder closure
     */
    Closure beanDefinitions() {
        { ->
            def conf = SpringSecurityUtils.securityConfig
            if (!conf || !conf.active || !conf.rest.active) {
                return
            }

            boolean printStatusMessages = (conf.printStatusMessages instanceof Boolean) ? conf.printStatusMessages : true

            if (printStatusMessages) {
                println '\t... with Memcached support'
            }

            SpringSecurityUtils.loadSecondaryConfig 'DefaultRestMemcachedSecurityConfig'
            conf = SpringSecurityUtils.securityConfig

            Properties systemProperties = System.properties
            systemProperties.put('net.spy.log.LoggerImpl', 'net.spy.memcached.compat.log.SLF4JLogger')
            System.setProperties(systemProperties)

            SpringSecurityUtils.registerFilter 'restLogoutFilter', SecurityFilterPosition.LOGOUT_FILTER.order - 1

            memcachedClient(MemcachedClientFactoryBean) {
                servers = conf.rest.token.storage.memcached.hosts
                protocol = 'BINARY'
                transcoder = new CustomSerializingTranscoder()
                opTimeout = 1000
                timeoutExceptionThreshold = 1998
                hashAlg = DefaultHashAlgorithm.KETAMA_HASH
                locatorType = 'CONSISTENT'
                failureMode = 'Redistribute'
                useNagleAlgorithm = false
            }

            tokenStorageService(MemcachedTokenStorageService) {
                memcachedClient = ref('memcachedClient')
                expiration = conf.rest.token.storage.memcached.expiration
            }

            tokenGenerator(SecureRandomTokenGenerator)

        }
    }

    void doWithApplicationContext() {
        def conf = SpringSecurityUtils.securityConfig
        if (!conf || !conf.active || !conf.rest.active) {
            return
        }

        applicationContext.getBean(RestAuthenticationProvider).useJwt = false
    }
}
