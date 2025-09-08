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

import grails.core.GrailsApplication
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import org.grails.commons.test.AbstractGrailsMockTests
import grails.plugins.DefaultGrailsPluginManager
import org.grails.web.servlet.context.support.WebRuntimeSpringConfiguration
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.beans.propertyeditors.ClassEditor
import org.springframework.context.ApplicationContext
import org.springframework.core.env.StandardEnvironment
import org.springframework.web.servlet.i18n.CookieLocaleResolver

class GrailsPluginManagerTests extends AbstractGrailsMockTests {

    private static final String RESOURCE_PATH = "classpath:org/grails/plugins/ClassEditorGrailsPlugin.groovy"

    protected void onSetUp() {
        super.onSetUp()

        gcl.parseClass '''
dataSource {
    pooled = false
    driverClassName = "org.h2.Driver"
    username = "sa"
    password = ""
    dbCreate = "create-drop" // one of 'create', 'create-drop','update'
    url = "jdbc:h2:mem:devDB"
}
hibernate {
    cache.use_second_level_cache=true
    cache.use_query_cache=true
    cache.provider_class='org.hibernate.cache.OSCacheProvider'
}'''
    }

//    void testObservablePlugin() {
//        def manager = new DefaultGrailsPluginManager(
//            [MyGrailsPlugin, AnotherGrailsPlugin, ObservingGrailsPlugin] as Class[], ga)
//
//        manager.loadPlugins()
//
//        assertTrue manager.hasGrailsPlugin("another")
//
//        // Get the "another" plugin and all the plugins that are observing it.
//        def plugin = manager.getGrailsPlugin("another")
//        def observers = manager.getPluginObservers(plugin)
//
//        // Check that the observers are what we expect.
//        def expectedObservers = ["observing"]
//        assert observers*.name.containsAll(expectedObservers)
//        assertEquals expectedObservers.size(), observers.size()
//
//        // the "my" plugin (is not observed by any other plugins).
//        observers = manager.getPluginObservers(manager.getGrailsPlugin("my"))
//        expectedObservers = []
//
//        assertTrue observers*.name.containsAll(expectedObservers)
//        assertEquals expectedObservers.size(), observers.size()
//
//        // Make sure the observers are being notified of changes to the observed plugin.
//        def event = [source:"foo"]
//        manager.informObservers("another", event)
//
//        assertEquals "bar", event.source
//    }

    void testNoSelfObserving() {
        def manager = new DefaultGrailsPluginManager([AnotherGrailsPlugin,ObservingAllGrailsPlugin] as Class[], ga)

        manager.loadPlugins()

        // Get the "another" plugin and all the plugins that are observing it.
        def plugin = manager.getGrailsPlugin("another")
        def observers = manager.getPluginObservers(plugin)

        // Check that the observers are what we expect.
        def expectedObservers = ["observingAll"]

        assertTrue observers*.name.containsAll(expectedObservers)
        assertEquals expectedObservers.size(), observers.size()

        // Now check that the "observingAll" plugin is *not* observing itself.
        observers = manager.getPluginObservers(manager.getGrailsPlugin("observingAll"))
        expectedObservers = []

        assertTrue observers*.name.containsAll(expectedObservers)
        assertEquals expectedObservers.size(), observers.size()
    }

    void testDisabledPlugin() {
        def manager = new DefaultGrailsPluginManager([MyGrailsPlugin,AnotherGrailsPlugin,DisabledGrailsPlugin] as Class[], ga)

        manager.loadPlugins()

        assertTrue manager.hasGrailsPlugin("my")
        assertNotNull manager.getGrailsPlugin("my").instance
        assertFalse manager.hasGrailsPlugin("disabled")
    }

    void testDefaultGrailsPluginManager() {
        DefaultGrailsPluginManager manager = new DefaultGrailsPluginManager(RESOURCE_PATH,ga)
        assertEquals(1, manager.getPluginResources().length)
    }

    void testLoadPlugins() {
        GrailsPluginManager manager = new DefaultGrailsPluginManager(RESOURCE_PATH,ga)
        manager.loadPlugins()

        GrailsPlugin plugin = manager.getGrailsPlugin("classEditor")
        assertNotNull(plugin)
        assertEquals("classEditor",plugin.getName())
        assertEquals("1.1", plugin.getVersion())

        plugin = manager.getGrailsPlugin("classEditor", "1.1")
        assertNotNull(plugin)

        plugin = manager.getGrailsPlugin("classEditor", "1.2")
        assertNull(plugin)
    }

    void testWithLoadLastPlugin() {
        def manager = new DefaultGrailsPluginManager([MyGrailsPlugin,AnotherGrailsPlugin,ShouldLoadLastGrailsPlugin] as Class[], ga)
        manager.loadPlugins()
    }

    void testDependencyResolutionFailure() {
        def manager = new DefaultGrailsPluginManager([MyGrailsPlugin] as Class[], ga)

        manager.loadPlugins()
        assert !manager.hasGrailsPlugin("my")
    }

    void testDependencyResolutionSuccess() {
        def manager = new DefaultGrailsPluginManager([MyGrailsPlugin,AnotherGrailsPlugin, SomeOtherGrailsPlugin] as Class[], ga)

        manager.loadPlugins()
    }

    void testEviction() {
        def manager = new DefaultGrailsPluginManager([MyGrailsPlugin,AnotherGrailsPlugin,SomeOtherGrailsPlugin,ShouldEvictSomeOtherGrailsPlugin] as Class[], ga)

        manager.loadPlugins()

        assertFalse manager.hasGrailsPlugin("someOther")
        assertTrue manager.hasGrailsPlugin("my")
        assertTrue manager.hasGrailsPlugin("another")
        assertTrue manager.hasGrailsPlugin("shouldEvictSomeOther")
    }

    void testShutdownCalled() {
        def manager = new DefaultGrailsPluginManager([MyGrailsPlugin,AnotherGrailsPlugin] as Class[], ga)
        manager.applicationContext = [getBeansOfType: { Class c -> [:] }, getEnvironment: {-> new StandardEnvironment() } ] as ApplicationContext

        manager.loadPlugins()

        assertEquals "nullme",MyGrailsPlugin.SHUTDOWN_FIELD
        manager.shutdown()
        assertNull MyGrailsPlugin.SHUTDOWN_FIELD
    }
}

class MyGrailsPlugin {

    static SHUTDOWN_FIELD = "nullme"
    def dependsOn = [another:1.2]
    def version = 1.1
    def doWithSpring = {
        classEditor(ClassEditor,application.classLoader)
    }
    def onShutdown = {
        SHUTDOWN_FIELD = null
    }
}

class AnotherGrailsPlugin {
    def version = 1.2
    def watchedResources = ['classpath:org/codehaus/groovy/grails/plugins/*.xml']
    def doWithApplicationContext = { ctx ->
        RootBeanDefinition bd = new RootBeanDefinition(CookieLocaleResolver)
        ctx.registerBeanDefinition("localeResolver", bd)
    }
}

class SomeOtherGrailsPlugin {
    def version = 1.4
    def dependsOn = [my:1.1, another:1.2]
}

class ShouldLoadLastGrailsPlugin {
    def loadAfter = ["my", "someOther"]
    def version = 1.5
}

class ShouldEvictSomeOtherGrailsPlugin {
    def evict = ['someOther']
    def version = 1.1
}

class DisabledGrailsPlugin {
    def version = 1.0
    def status = "disabled"
}

class ObservingGrailsPlugin {
    def version = "1.0-RC1"
    def observe = ['another']

    def onChange = { event ->
        assert event.source != null
        event.source = "bar"
    }
}

class ObservingAllGrailsPlugin {
    def version = "1.0"
    def observe = ['*']
}
