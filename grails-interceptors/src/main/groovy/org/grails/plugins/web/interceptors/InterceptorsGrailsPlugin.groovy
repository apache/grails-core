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

import grails.artefact.Interceptor
import grails.config.Settings
import grails.core.GrailsClass
import grails.plugins.Plugin
import grails.util.GrailsUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.web.servlet.handler.MappedInterceptor

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
    def final watchedResources = "file:./grails-app/controllers/**/*Interceptor.groovy"
    def final loadAfter = ['domainClass', 'hibernate']

    GrailsInterceptorHandlerInterceptorAdapter interceptorAdapter

    @Override
    @CompileDynamic
    Closure doWithSpring() {
        { ->
            GrailsClass[] interceptors = grailsApplication.getArtefacts(InterceptorArtefactHandler.TYPE)
            if (interceptors.length == 0) return

            grailsInterceptorMappedInterceptor(MappedInterceptor, ['/**'] as String[], bean(GrailsInterceptorHandlerInterceptorAdapter))

            def enableJsessionId = config.getProperty(Settings.GRAILS_VIEWS_ENABLE_JSESSIONID, Boolean, false)
            for (GrailsClass i in interceptors) {
                "${i.propertyName}"(i.clazz) { bean ->
                    bean.autowire = 'byName'
                    if (enableJsessionId) {
                        useJessionId = enableJsessionId
                    }
                }
            }
        }
    }

    @Override
    void doWithApplicationContext() {
        if (applicationContext.containsBeanDefinition("grailsInterceptorMappedInterceptor")) {
            interceptorAdapter = (GrailsInterceptorHandlerInterceptorAdapter) applicationContext.getBean("grailsInterceptorMappedInterceptor", MappedInterceptor).getInterceptor()
        }
    }

    @Override
    void onChange(Map<String, Object> event) {

        def source = event.source
        if (source instanceof Class) {
            def enableJsessionId = config.getProperty(Settings.GRAILS_VIEWS_ENABLE_JSESSIONID, Boolean, false)

            def interceptorClass = (Class) source
            def grailsClass = grailsApplication.addArtefact(InterceptorArtefactHandler.TYPE, interceptorClass)

            def interceptorAdapter = this.interceptorAdapter ?: (GrailsInterceptorHandlerInterceptorAdapter) applicationContext.getBean("grailsInterceptorMappedInterceptor", MappedInterceptor).getInterceptor()
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
