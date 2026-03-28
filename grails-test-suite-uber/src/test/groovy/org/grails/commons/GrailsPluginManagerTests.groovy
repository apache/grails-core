/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.commons

import spock.lang.Shared
import spock.lang.Specification

import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.beans.propertyeditors.ClassEditor
import org.springframework.context.ApplicationContext
import org.springframework.web.servlet.i18n.CookieLocaleResolver

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import org.grails.support.MockApplicationContext

class GrailsPluginManagerTests extends Specification {

    private static final String TEST_PLUGIN_RESOURCE_PATH = 'classpath:org/grails/plugins/ClassEditorGrailsPlugin.groovy'

    @Shared ApplicationContext ctx = new MockApplicationContext()
    @Shared GrailsApplication grailsApplication = new DefaultGrailsApplication().tap {
        applicationContext = ctx
        initialise()
    }

    def 'registers observers and informs them of plugin events'() {
        given: 'a plugin manager with an observed plugin and an observer plugin'
        def pluginManager = new DefaultGrailsPluginManager([
                MyGrailsPlugin,
                AnotherGrailsPlugin,
                ObservingGrailsPlugin
        ] as Class[], grailsApplication).tap {
            applicationContext = ctx
        }

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'the observed plugin is registered'
        pluginManager.hasGrailsPlugin('another')

        when: 'the observers for the observed plugin are requested'
        def plugin = pluginManager.getGrailsPlugin('another')
        def observers = pluginManager.getPluginObservers(plugin)

        then: 'the expected observer is returned'
        observers*.name.contains('observing')
        observers.size() == 1

        when: 'the observers for an unobserved plugin are requested'
        observers = pluginManager.getPluginObservers(pluginManager.getGrailsPlugin('my'))

        then: 'no observers are returned'
        observers.empty

        when: 'notifying the "another" plugin of an event'
        def event = [source: 'foo']
        pluginManager.informObservers('another', event)

        then: 'the "another" plugin should have been notified and modified the event object'
        event.source == 'bar'
    }

    def 'wildcard observers do not observe themselves'() {
        given: 'a wildcard observer and an observed plugin'
        def pluginManager = new DefaultGrailsPluginManager([
                AnotherGrailsPlugin,
                ObservingAllGrailsPlugin
        ] as Class[], grailsApplication)

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        and: 'observers are requested for the observed plugin'
        def plugin = pluginManager.getGrailsPlugin('another')
        def observers = pluginManager.getPluginObservers(plugin)

        then: 'the wildcard observer is returned'
        observers*.name.contains('observingAll')
        observers.size() == 1

        when: 'the wildcard observer asks for its own observers'
        observers = pluginManager.getPluginObservers(pluginManager.getGrailsPlugin('observingAll'))

        then: 'it does not observe itself'
        observers.empty
    }

    def 'skips disabled plugins during loading'() {
        given: 'a plugin manager with one disabled plugin'
        def pluginManager = new DefaultGrailsPluginManager([
                AnotherGrailsPlugin,
                DisabledGrailsPlugin
        ] as Class[], grailsApplication)

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'enabled plugins are loaded and disabled plugins are skipped'
        with(pluginManager) {
            hasGrailsPlugin('another')
            getGrailsPlugin('another').instance
            !hasGrailsPlugin('disabled')
        }
    }

    def 'initializes the plugin manager from an explicit plugin resource path'() {
        given: 'a plugin manager constructed with an explicit test plugin resource path'
        def pluginManager = new DefaultGrailsPluginManager(TEST_PLUGIN_RESOURCE_PATH, grailsApplication)
        
        expect: 'the plugin resource path is resolved into a single plugin resource'
        pluginManager.pluginResources.length == 1
    }

    def 'loads discovered plugins and resolves them by name and version'() {
        given: 'the plugin manager has been initialized from the resource path'
        def pluginManager = new DefaultGrailsPluginManager(TEST_PLUGIN_RESOURCE_PATH, grailsApplication)

        when: 'plugins are loaded and looked up by name'
        pluginManager.loadPlugins()

        and: 'the classEditor plugin is requested'
        def plugin = pluginManager.getGrailsPlugin('classEditor')

        then: 'the plugin is available with the expected version'
        plugin
        plugin.name == 'classEditor'
        plugin.version == '1.1'

        when: 'the plugin is requested with the matching version'
        plugin = pluginManager.getGrailsPlugin('classEditor', '1.1')

        then: 'the plugin is returned'
        plugin

        when: 'the plugin is requested with a non-matching version'
        plugin = pluginManager.getGrailsPlugin('classEditor', '1.2')

        then: 'no plugin is returned'
        !plugin
    }

    def 'loads plugins successfully when a plugin declares loadAfter dependencies'() {
        given: 'a plugin manager with a plugin that declares loadAfter dependencies'
        def pluginManager = new DefaultGrailsPluginManager([
                ShouldLoadLastGrailsPlugin,
                MyGrailsPlugin,
                AnotherGrailsPlugin
            ] as Class[], grailsApplication
        )

        when: 'plugins are loaded'
        pluginManager.loadPlugins()
        def plugins = pluginManager.getAllPlugins()*.name

        then: 'the plugin with loadAfter dependencies is loaded last'
        plugins.indexOf('shouldLoadLast') > plugins.indexOf('my')
        plugins.indexOf('shouldLoadLast') > plugins.indexOf('someOther')
    }

    def 'does not load a plugin when required dependencies are missing'() {
        given: 'a plugin manager missing a required dependency'
        def pluginManager = new DefaultGrailsPluginManager([
                MyGrailsPlugin // depends on 'another' plugin which is not included here
        ] as Class[], grailsApplication)

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'the dependent plugin is not loaded'
        !pluginManager.hasGrailsPlugin('my')
    }

    def 'loads plugins when required dependencies are satisfied'() {
        given: 'a plugin manager with all required dependencies present'
        def pluginManager = new DefaultGrailsPluginManager([
                MyGrailsPlugin,
                AnotherGrailsPlugin,
                SomeOtherGrailsPlugin
        ] as Class[], grailsApplication)

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'the dependent plugin is loaded'
        pluginManager.hasGrailsPlugin('my')
    }

    def 'evicts superseded plugins during loading'() {
        given: 'a plugin manager with an evicting plugin'
        def pluginManager = new DefaultGrailsPluginManager([
                MyGrailsPlugin,
                AnotherGrailsPlugin,
                SomeOtherGrailsPlugin,
                ShouldEvictSomeOtherGrailsPlugin
        ] as Class[], grailsApplication)

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'the evicted plugin is removed while the others remain loaded'
        with(pluginManager) {
            !hasGrailsPlugin('someOther')
            hasGrailsPlugin('my')
            hasGrailsPlugin('another')
            hasGrailsPlugin('shouldEvictSomeOther')
        }
    }

    def 'invokes plugin shutdown callbacks when the manager shuts down'() {
        given: 'a plugin manager with a plugin that defines an onShutdown callback'
        def pluginManager = new DefaultGrailsPluginManager([
                MyGrailsPlugin,
                AnotherGrailsPlugin, // needed to satisfy the dependency of MyGrailsPlugin
        ] as Class[], grailsApplication).tap {
             applicationContext = ctx
        }

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'the plugin is loaded'
        pluginManager.hasGrailsPlugin('my')

        and: 'its shutdown marker is unchanged'
        MyGrailsPlugin.SHUTDOWN_FIELD == 'not-updated'

        when: 'the plugin manager is shut down'
        pluginManager.shutdown()

        then: 'the shutdown callback updates the marker'
        MyGrailsPlugin.SHUTDOWN_FIELD == 'updated'
    }
}

class MyGrailsPlugin {

    static SHUTDOWN_FIELD = 'not-updated'

    def dependsOn = [another: 1.2]
    def version = 1.1
    def doWithSpring = {
        classEditor(ClassEditor, grailsApplication.classLoader)
    }
    def onShutdown = {
        SHUTDOWN_FIELD = 'updated'
    }
}

class AnotherGrailsPlugin {

    def version = 1.2
    def watchedResources = ['classpath:org/codehaus/groovy/grails/plugins/*.xml']
    def doWithApplicationContext = { ctx ->
        ctx.registerBeanDefinition('localeResolver', new RootBeanDefinition(CookieLocaleResolver))
    }
}

class SomeOtherGrailsPlugin {

    def version = 1.4
    def dependsOn = [my: 1.1, another: 1.2]
}

class ShouldLoadLastGrailsPlugin {

    def loadAfter = ['my', 'someOther']
    def version = 1.5
}

class ShouldEvictSomeOtherGrailsPlugin {

    def evict = ['someOther']
    def version = 1.1
}

class ObservingGrailsPlugin {

    def version = '1.0-RC1'
    def observe = ['another']

    def onChange = { event ->
        assert event.source != null
        event.source = 'bar'
    }
}

class ObservingAllGrailsPlugin {

    def version = '1.0'
    def observe = ['*']
}

/**
 * This plugin should be the last one defined here because because a grails-plugin.xml file will be generated
 * in build/classes/groovy/test/META-INF for the last plugin defined and the plugin manager will always
 * load it via finding that file. This fixes problem described in commit 3aaeaa95
 */
class DisabledGrailsPlugin {

    def version = 1.0
    def status = 'disabled'
}
