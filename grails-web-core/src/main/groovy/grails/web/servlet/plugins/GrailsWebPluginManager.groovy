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
package grails.web.servlet.plugins

import groovy.transform.CompileStatic

import jakarta.servlet.ServletContext

import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.core.io.Resource

import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPlugin

/**
 * @author Graeme Rocher
 */
@CompileStatic
class GrailsWebPluginManager extends DefaultGrailsPluginManager{

    public static final String SERVLET_CONTEXT_INIT_METHOD = 'doWithServletContext'

    GrailsWebPluginManager(String resourcePath, GrailsApplication application) {
        super(resourcePath, application)
    }

    GrailsWebPluginManager(String[] pluginResources, GrailsApplication application) {
        super(pluginResources, application)
    }

    GrailsWebPluginManager(Class<?>[] plugins, GrailsApplication application) {
        super(plugins, application)
    }

    GrailsWebPluginManager(Resource[] pluginFiles, GrailsApplication application) {
        super(pluginFiles, application)
    }

    void doWithServletContext(ServletContext servletContext) {
        for(GrailsPlugin plugin in allPlugins) {
            def instance = plugin.instance
            if(instance instanceof ServletContextInitializer) {
                ((ServletContextInitializer)instance).onStartup(servletContext)
            }
        }
    }
}
