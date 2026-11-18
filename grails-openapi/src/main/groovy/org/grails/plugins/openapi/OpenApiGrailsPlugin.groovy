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
package org.grails.plugins.openapi

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment
import org.springframework.util.ClassUtils

import grails.core.GrailsApplication
import grails.openapi.GrailsOpenApiGenerator
import grails.openapi.OpenApiSettings
import grails.plugins.Plugin
import grails.util.GrailsUtil
import grails.web.mapping.UrlMappingsHolder
import org.grails.datastore.mapping.model.MappingContext
import org.grails.openapi.springdoc.SpringdocRegistrations

/**
 * Registers the generator that describes the application, and - when springdoc is on the
 * classpath - contributes that description to the documents springdoc serves.
 */
@CompileStatic
class OpenApiGrailsPlugin extends Plugin {

    static final String GENERATOR_BEAN_NAME = 'grailsOpenApiGenerator'

    private static final String SPRINGDOC_CUSTOMIZER = 'org.springdoc.core.customizers.OpenApiCustomizer'

    def version = GrailsUtil.grailsVersion
    def dependsOn = [urlMappings: version]
    def providedArtefacts = [OpenApiUrlMappings]

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            OpenApiSettings settings = OpenApiSettings.from(environment)
            if (!settings.enabled) {
                return
            }
            registry.registerBean(GENERATOR_BEAN_NAME, GrailsOpenApiGenerator) {
                it.lazyInit().supplier { context ->
                    new GrailsOpenApiGenerator(
                            context.bean(GrailsApplication.APPLICATION_ID, GrailsApplication),
                            context.bean('grailsUrlMappingsHolder', UrlMappingsHolder),
                            context.beanProvider(MappingContext).orderedStream().toList(),
                            settings)
                }
            }

            // The adapter is only loaded when springdoc is present, so an application that
            // generates its description at build time does not need springdoc at all.
            if (ClassUtils.isPresent(SPRINGDOC_CUSTOMIZER, OpenApiGrailsPlugin.classLoader)) {
                SpringdocRegistrations.register(registry, settings, GENERATOR_BEAN_NAME)
            }
        }
    }
}
