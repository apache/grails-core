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
package org.apache.grails.micronaut

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import io.micronaut.context.ApplicationContext as MicronautApplicationContext
import io.micronaut.context.env.AbstractPropertySourceLoader
import io.micronaut.context.env.PropertySource

import org.springframework.core.env.EnumerablePropertySource

import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin

@Slf4j
@CompileStatic
class GrailsMicronautGrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0-SNAPSHOT > *'
    def title = 'Grails Micronaut Plugin'

    private static final String PLUGIN_YML_SUFFIX = '-plugin.yml'
    private static final String PLUGIN_GROOVY_SUFFIX = '-plugin.groovy'

    @Override
    void doWithApplicationContext() {
        def beanNames = applicationContext.getBeanNamesForType(GrailsPluginManager)
        def pluginManagerFromContext = beanNames.length ?
                applicationContext.getBean(GrailsPluginManager) :
                null

        if (!pluginManagerFromContext && !pluginManager) {
            // No plugin managers to search for plugin configurations
            return
        }

        if (!applicationContext.containsBean('micronautApplicationContext')) {
            throw new IllegalStateException(
                    'A Micronaut Application Context should exist prior to the loading ' +
                    'of the Grails Micronaut plugin.'
            )
        }

        def micronautContext = applicationContext.getBean(
                'micronautApplicationContext',
                MicronautApplicationContext
        )
        def micronautEnv = micronautContext.environment

        log.debug('Loading configurations from the plugins to the parent Micronaut context')

        def springEnv = applicationContext.environment
        def springPropertySources = springEnv.propertySources
        def plugins = pluginManager.allPlugins
        def pluginsFromContext = pluginManagerFromContext ?
                pluginManagerFromContext.allPlugins :
                new GrailsPlugin[0]
        int priority = AbstractPropertySourceLoader.DEFAULT_POSITION
        [plugins, pluginsFromContext].each { pluginsToProcess ->
            pluginsToProcess
                    .findAll { it.propertySource != null }
                    .each { plugin ->
                        // Look up the plugin's property source from the Spring environment,
                        // where it was loaded by GrailsEnvironmentPostProcessor
                        def pluginName = plugin.name
                        def ymlSourceName = pluginName + PLUGIN_YML_SUFFIX
                        def groovySourceName = pluginName + PLUGIN_GROOVY_SUFFIX
                        def springPs = springPropertySources.get(ymlSourceName) ?: springPropertySources.get(groovySourceName)
                        if (springPs instanceof EnumerablePropertySource) {
                            log.debug('Loading configurations from {} plugin to the parent Micronaut context', pluginName)
                            micronautEnv.addPropertySource(PropertySource.of("grails.plugins.${pluginName}", (Map) springPs.source, --priority))
                        }
                    }
        }
        micronautEnv.refresh()
    }
}
