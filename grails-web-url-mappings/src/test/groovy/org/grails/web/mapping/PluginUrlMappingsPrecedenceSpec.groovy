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
package org.grails.web.mapping

import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.web.mapping.UrlMappingsHolder
import org.apache.grails.core.plugins.DefaultPluginDiscovery

/**
 * Where the URL mappings of two plugins match the same URL, the mapping of the later plugin in plugin order
 * wins, and the application's own mapping wins over both.
 */
class PluginUrlMappingsPrecedenceSpec extends Specification {

    void 'the later plugin\'s mapping wins over an earlier plugin\'s, and the application\'s over both'() {
        given: 'two plugins mapping the same URLs, the one whose name sorts first coming last'
        def loader = new GroovyClassLoader(getClass().classLoader)
        Class<?> earlier = loader.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(providedArtefacts = [org.grails.web.mapping.EarlierPluginUrlMappings])
            class ZzzEarlierGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        Class<?> later = loader.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(after = ['zzzEarlier'],
                    providedArtefacts = [org.grails.web.mapping.LaterPluginUrlMappings])
            class AaaLaterGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        UrlMappingsHolder holder = holder([earlier, later])

        expect:
        holder.match('/contested').controllerName == 'later'
        holder.match('/onlyEarlier').controllerName == 'earlier'
        holder.match('/application').controllerName == 'application'
    }

    private static UrlMappingsHolder holder(List<Class<?>> pluginClasses) {
        def context = new GenericApplicationContext()
        context.refresh()
        def app = new DefaultGrailsApplication(ApplicationPrecedenceUrlMappings)
        def discovery = new DefaultPluginDiscovery(pluginClasses as Class<?>[])
        discovery.loadPluginsFromClasspath = false
        discovery.init(context.environment)
        def manager = new DefaultGrailsPluginManager(app, discovery)
        context.beanFactory.registerSingleton(GrailsApplication.APPLICATION_ID, app)
        context.beanFactory.registerSingleton(GrailsPluginManager.BEAN_NAME, manager)
        app.mainContext = context
        manager.loadPlugins()
        manager.applicationContext = context
        app.initialise()
        manager.registerProvidedArtefacts(app)
        def factoryBean = new UrlMappingsHolderFactoryBean()
        factoryBean.applicationContext = context
        factoryBean.afterPropertiesSet()
        factoryBean.object
    }
}

class EarlierPluginUrlMappings {
    static mappings = {
        '/contested'(controller: 'earlier')
        '/onlyEarlier'(controller: 'earlier')
        '/application'(controller: 'earlier')
    }
}

class LaterPluginUrlMappings {
    static mappings = {
        '/contested'(controller: 'later')
        '/application'(controller: 'later')
    }
}

class ApplicationPrecedenceUrlMappings {
    static mappings = {
        '/application'(controller: 'application')
    }
}
