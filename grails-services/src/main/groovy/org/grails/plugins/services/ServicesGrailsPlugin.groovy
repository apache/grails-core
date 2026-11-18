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
package org.grails.plugins.services

import java.lang.reflect.Modifier

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.config.Settings
import grails.core.GrailsServiceClass
import grails.plugins.Plugin
import grails.util.GrailsUtil
import org.grails.core.artefact.ServiceArtefactHandler
import org.grails.core.exceptions.GrailsConfigurationException

/**
 * Configures services in the Spring context.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@CompileStatic
class ServicesGrailsPlugin extends Plugin  {

    def version = GrailsUtil.getGrailsVersion()
    def loadAfter = ['hibernate']

    def watchedResources = ['file:./grails-app/services/**/*Service.groovy',
                            'file:./plugins/*/grails-app/services/**/*Service.groovy']

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            final boolean springTransactionManagement = environment.getProperty(Settings.SPRING_TRANSACTION_MANAGEMENT, Boolean, false)
            if (springTransactionManagement) {
                throw new GrailsConfigurationException('Spring proxy-based transaction management no longer supported. Yes the @grails.gorm.transactions.Transactional annotation instead')
            }

            // Service beans autowire by name and use per-service scopes, which the BeanRegistry
            // API cannot express — their definitions are contributed by a dedicated post-processor
            registry.registerBean('serviceBeanDefinitionsPostProcessor', ServiceBeanDefinitionsPostProcessor) {
                it.infrastructure().supplier {
                    new ServiceBeanDefinitionsPostProcessor(grailsApplication, manager)
                }
            }

            registry.registerBean('serviceBeanAliasPostProcessor', ServiceBeanAliasPostProcessor)
        }
    }

    @CompileDynamic
    void onChange(Map<String,Object> event) {
        if (!event.source || !applicationContext) {
            return
        }

        if (event.source instanceof Class) {
            def application = grailsApplication
            Class javaClass = event.source
            // do nothing for abstract classes
            if (Modifier.isAbstract(javaClass.modifiers)) return
            def serviceClass = (GrailsServiceClass) application.addArtefact(ServiceArtefactHandler.TYPE, (Class) event.source)
            def serviceName = "${serviceClass.propertyName}"
            def scope = serviceClass.getPropertyValue('scope')

            beans {
                "$serviceName"(serviceClass.getClazz()) { bean ->
                    bean.autowire = true
                    if (scope) {
                        bean.scope = scope
                    }
                }
            }
        }
    }

}
