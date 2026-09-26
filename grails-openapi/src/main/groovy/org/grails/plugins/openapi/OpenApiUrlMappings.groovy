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

import org.springframework.core.env.PropertyResolver
import org.springframework.util.ClassUtils

import grails.core.GrailsApplication
import grails.util.Holders

/**
 * Leaves the paths springdoc serves to springdoc, so a catch-all URL mapping of the application,
 * such as a single page application's, does not answer them: the API documents, and Swagger UI
 * where it is on the classpath, at the paths springdoc is configured to serve them at.
 */
@CompileStatic
class OpenApiUrlMappings {

    private static final String SPRINGDOC = 'org.springdoc.core.properties.SpringDocConfigProperties'
    private static final String SWAGGER_UI = 'org.springdoc.webmvc.ui.SwaggerConfig'
    private static final String SWAGGER_UI_RESOURCES = '/swagger-ui/**'
    private static final String ALL = '/**'

    static Closure mappings = { }

    /**
     * Read as the application's URL mappings are built, from its configuration.
     */
    static ArrayList<String> getExcludes() {
        GrailsApplication application = Holders.findApplication()
        application != null ? servedBySpringdoc(application.config) : new ArrayList<String>()
    }

    private static ArrayList<String> servedBySpringdoc(PropertyResolver config) {
        ArrayList<String> paths = new ArrayList<>()
        ClassLoader classLoader = OpenApiUrlMappings.classLoader
        if (!ClassUtils.isPresent(SPRINGDOC, classLoader)) {
            return paths
        }
        if (config.getProperty('springdoc.api-docs.enabled', Boolean, true)) {
            String docs = withoutTrailingSlash(config.getProperty('springdoc.api-docs.path', String, '/v3/api-docs'))
            paths.addAll([docs, docs + ALL, docs + '.yaml', docs + '.yaml' + ALL])
        }
        if (ClassUtils.isPresent(SWAGGER_UI, classLoader) && config.getProperty('springdoc.swagger-ui.enabled', Boolean, true)) {
            // Swagger UI's page, and its resources beside it and in its webjar.
            String page = config.getProperty('springdoc.swagger-ui.path', String, '/swagger-ui.html')
            String root = page.contains('/') ? page.substring(0, page.lastIndexOf('/')) : ''
            String webjars = config.getProperty('spring.mvc.webjars-path-pattern', String, '/webjars/**')
            paths.addAll([page, root + SWAGGER_UI_RESOURCES,
                          withoutTrailingSlash(webjars - ALL) + SWAGGER_UI_RESOURCES])
        }
        paths
    }

    private static String withoutTrailingSlash(String path) {
        path.length() > 1 && path.endsWith('/') ? path[0..-2] : path
    }
}
