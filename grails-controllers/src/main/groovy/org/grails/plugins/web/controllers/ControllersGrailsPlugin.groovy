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
package org.grails.plugins.web.controllers

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment

import grails.config.Settings
import grails.core.GrailsControllerClass
import grails.plugins.Plugin
import grails.util.GrailsUtil
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.plugins.web.servlet.context.BootStrapClassRunner
import org.grails.web.servlet.mvc.TokenResponseActionResultTransformer
import org.grails.web.servlet.view.CompositeViewResolver

/**
 * Handles the configuration of controllers for Grails.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@Slf4j
@CompileStatic
class ControllersGrailsPlugin extends Plugin {

    def watchedResources = [
            'file:./grails-app/controllers/**/*Controller.groovy',
            'file:./plugins/*/grails-app/controllers/**/*Controller.groovy']

    def version = GrailsUtil.getGrailsVersion()
    def observe = ['domainClass']
    def dependsOn = [core: version, i18n: version, urlMappings: version]

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            boolean useJsessionId = environment.getProperty(Settings.GRAILS_VIEWS_ENABLE_JSESSIONID, Boolean, false)

            if (!Boolean.parseBoolean(System.getProperty(Settings.SETTING_SKIP_BOOTSTRAP))) {
                registry.registerBean('bootStrapClassRunner', BootStrapClassRunner)
            }

            registry.registerBean('tokenResponseActionResultTransformer', TokenResponseActionResultTransformer)

            registry.registerBean(CompositeViewResolver.BEAN_NAME, CompositeViewResolver)

            // Controller beans autowire by name and use per-controller scopes, which the
            // BeanRegistry API cannot express — their definitions are contributed by a
            // dedicated post-processor instead
            registry.registerBean('controllerBeanDefinitionsPostProcessor', ControllerBeanDefinitionsPostProcessor) {
                it.infrastructure().supplier {
                    new ControllerBeanDefinitionsPostProcessor(grailsApplication, useJsessionId)
                }
            }

            if (environment.getProperty(Settings.SETTING_LEGACY_JSON_BUILDER, Boolean, false)) {
                log.warn("'grails.json.legacy.builder' is set to TRUE but is NOT supported in this version of Grails.")
            }
        }
    }

    @Override
    @CompileDynamic
    void onChange(Map<String, Object> event) {
        if (!(event.source instanceof Class)) {
            return
        }
        def application = grailsApplication
        if (application.isArtefactOfType(ControllerArtefactHandler.TYPE, (Class) event.source)) {
            ApplicationContext context = applicationContext
            if (!context) {
                log.debug("Application context not found. Can't reload")
                return
            }

            GrailsControllerClass controllerClass = (GrailsControllerClass) application.addArtefact(ControllerArtefactHandler.TYPE, (Class)event.source)
            beans {
                "${controllerClass.fullName}"(controllerClass.clazz) { bean ->
                    def beanScope = controllerClass.getScope()
                    bean.scope = beanScope
                    bean.autowire = 'byName'
                    if (beanScope == 'prototype') {
                        bean.beanDefinition.dependencyCheck = AbstractBeanDefinition.DEPENDENCY_CHECK_NONE
                    }
                }
            }
        }
    }
}
