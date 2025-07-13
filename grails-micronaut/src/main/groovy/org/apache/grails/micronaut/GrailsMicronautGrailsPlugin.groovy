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

import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import groovy.util.logging.Slf4j
import io.micronaut.context.ConfigurableApplicationContext
import io.micronaut.context.env.AbstractPropertySourceLoader
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource

@Slf4j
class GrailsMicronautGrailsPlugin extends Plugin {

    def grailsVersion = '7.0.0-SNAPSHOT > *'
    def title = 'Grails Micronaut Plugin'

    @Override
    void doWithApplicationContext() {
        String[] beanNames = applicationContext.getBeanNamesForType(GrailsPluginManager)
        GrailsPluginManager pluginManagerFromContext = beanNames.length ?
                applicationContext.getBean(GrailsPluginManager) :
                null

        if (!pluginManagerFromContext && !pluginManager) {
            // No plugin managers to search for plugin configurations
            return
        }

        if (!applicationContext.containsBean('micronautApplicationContext')) {
            throw new IllegalStateException("A Micronaut Application Context should exist prior to the loading of the Grails Micronaut plugin.")
        }

        ConfigurableApplicationContext micronautContext = applicationContext.getBean('micronautApplicationContext', ConfigurableApplicationContext)
        Environment micronautEnv = micronautContext.getEnvironment()

        log.debug("Loading configurations from the plugins to the parent Micronaut context")
        final GrailsPlugin[] plugins = pluginManager.allPlugins
        final GrailsPlugin[] pluginsFromContext = pluginManagerFromContext ? pluginManagerFromContext.allPlugins : new GrailsPlugin[]{}
        Integer priority = AbstractPropertySourceLoader.DEFAULT_POSITION
        [plugins, pluginsFromContext].each { GrailsPlugin[] pluginsToProcess ->
            Arrays.stream(pluginsToProcess)
                    .filter({ GrailsPlugin plugin -> plugin.propertySource != null })
                    .forEach({ GrailsPlugin plugin ->
                        if (log.isDebugEnabled()) {
                            log.debug("Loading configurations from {} plugin to the parent Micronaut context", plugin.name)
                        }
                        // If invoking the source as `.source`, the NavigableMapPropertySource will return null, while invoking the getter, it will return the correct value
                        micronautEnv.addPropertySource(PropertySource.of("grails.plugins.$plugin.name", (Map) plugin.propertySource.getSource(), --priority))
                    })
        }
        micronautEnv.refresh()
//            applicationContext.setParent(parentApplicationContext)
    }
}
