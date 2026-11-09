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
package org.grails.plugins.web.interceptors

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment
import org.springframework.web.servlet.handler.MappedInterceptor

import grails.artefact.Interceptor
import grails.config.Settings
import grails.core.GrailsClass
import grails.plugins.Plugin
import grails.util.GrailsUtil

/**
 * A plugin for interceptors
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class InterceptorsGrailsPlugin extends Plugin {

    def final version = GrailsUtil.getGrailsVersion()
    def final dependsOn = [controllers: version, urlMappings: version]
    def final watchedResources = 'file:./grails-app/controllers/**/*Interceptor.groovy'
    def final loadAfter = ['domainClass', 'hibernate']

    GrailsInterceptorHandlerInterceptorAdapter interceptorAdapter

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            // The mapped interceptor (wrapping the handler adapter as an inner bean) and the
            // per-interceptor beans autowire by name and must not expose the adapter as a
            // top-level HandlerInterceptor bean — WebUtils.lookupHandlerInterceptors collects
            // every HandlerInterceptor bean, so a top-level adapter would run twice. None of
            // that is expressible through the BeanRegistry API, so the definitions are
            // contributed by a dedicated post-processor instead.
            boolean enableJsessionId = environment.getProperty(Settings.GRAILS_VIEWS_ENABLE_JSESSIONID, Boolean, false)
            registry.registerBean('interceptorBeanDefinitionsPostProcessor', InterceptorBeanDefinitionsPostProcessor) {
                it.infrastructure().supplier {
                    new InterceptorBeanDefinitionsPostProcessor(grailsApplication, enableJsessionId)
                }
            }
        }
    }

    @Override
    void doWithApplicationContext() {
        if (applicationContext.containsBeanDefinition('grailsInterceptorMappedInterceptor')) {
            interceptorAdapter = (GrailsInterceptorHandlerInterceptorAdapter) applicationContext.getBean('grailsInterceptorMappedInterceptor', MappedInterceptor).getInterceptor()
        }
    }

    @Override
    void onChange(Map<String, Object> event) {

        def source = event.source
        if (source instanceof Class) {
            def enableJsessionId = config.getProperty(Settings.GRAILS_VIEWS_ENABLE_JSESSIONID, Boolean, false)

            def interceptorClass = (Class) source
            def grailsClass = grailsApplication.addArtefact(InterceptorArtefactHandler.TYPE, interceptorClass)

            def interceptorAdapter = this.interceptorAdapter ?: (GrailsInterceptorHandlerInterceptorAdapter) applicationContext.getBean('grailsInterceptorMappedInterceptor', MappedInterceptor).getInterceptor()
            defineInterceptorBean(grailsClass, interceptorClass, enableJsessionId)
            interceptorAdapter.setInterceptors(
                    applicationContext.getBeansOfType(Interceptor).values() as Interceptor[]
            )
        }
    }

    @CompileDynamic
    private defineInterceptorBean(GrailsClass grailsClass, interceptorClass, enableJsessionId) {
        beans {
            "${grailsClass.propertyName}"(interceptorClass) { bean ->
                bean.autowire = 'byName'
                if (enableJsessionId) {
                    useJessionId = enableJsessionId
                }
            }
        }
    }
}
