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
package org.grails.core.cfg

import grails.util.BuildSettings
import grails.util.Environment
import grails.util.Metadata
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.config.NavigableMap
import org.grails.config.NavigableMapPropertySource
import org.grails.core.exceptions.GrailsConfigurationException
import org.springframework.boot.env.PropertySourceLoader
import org.springframework.core.env.PropertySource
import org.springframework.core.io.Resource

/**
 * Adds support for defining a 'application.groovy' file in ConfigSlurper format in order to configure Spring Boot within Grails
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Slf4j
class GroovyConfigPropertySourceLoader implements PropertySourceLoader {

    final String[] fileExtensions = ['groovy'] as String[]
    final Set<String> loadedFiles = new HashSet<>(1)

    @Override
    List<PropertySource<?>> load(String name, Resource resource) throws IOException {
        return load(name, resource, Collections.<String>emptyList())
    }

    List<PropertySource<?>> load(String name, Resource resource, List<String> filteredKeys) throws IOException {
        if (!loadedFiles.contains(name)) {
            def env = Environment.current.name

            if(resource.exists()) {
                ConfigSlurper configSlurper = env ? new ConfigSlurper(env) : new ConfigSlurper()

                configSlurper.setBinding(userHome: System.getProperty('user.home'),
                        grailsHome: BuildSettings.GRAILS_HOME?.absolutePath,
                        appName: Metadata.getCurrent().getApplicationName(),
                        appVersion: Metadata.getCurrent().getApplicationVersion() )
                try {
                    def configObject = configSlurper.parse(resource.URL)

                    for(key in filteredKeys) {
                        configObject.remove(key)
                    }

                    def propertySource = new NavigableMap()
                    propertySource.merge(configObject, false)

                    Resource runtimeResource = resource.createRelative( resource.filename.replace('application', 'runtime') )
                    if(runtimeResource.exists()) {
                        def runtimeConfig = configSlurper.parse( runtimeResource.getURL() )
                        propertySource.merge(runtimeConfig, false)
                    }
                    final NavigableMapPropertySource navigableMapPropertySource = new NavigableMapPropertySource(name, propertySource)
                    loadedFiles.add(name)
                    return Collections.<PropertySource<?>>singletonList(navigableMapPropertySource)
                } catch (Throwable e) {
                    log.error("Unable to load $resource.filename: $e.message", e)
                    throw new GrailsConfigurationException("Error loading $resource.filename due to [${e.getClass().name}]: $e.message", e)
                }
            }
        }
        return Collections.emptyList()
    }
}
