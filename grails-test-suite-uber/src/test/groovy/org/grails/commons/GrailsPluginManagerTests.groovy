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
package org.grails.commons

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import org.springframework.context.ApplicationContext
import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.exceptions.PluginException
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginUtils
import org.grails.support.MockApplicationContext

/**
 * The plugin classes are compiled at run time, so that no other test discovers them from the class path.
 */
class GrailsPluginManagerTests extends Specification {

    @Shared ApplicationContext ctx = new MockApplicationContext()
    @Shared GrailsApplication grailsApplication = new DefaultGrailsApplication().tap {
        applicationContext = ctx
        initialise()
    }

    @Shared GroovyClassLoader fixtures = new GroovyClassLoader(GrailsPluginManagerTests.classLoader).tap {
        parseClass('''
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.beans.propertyeditors.ClassEditor
import org.springframework.core.env.Environment
import org.springframework.web.servlet.i18n.CookieLocaleResolver

import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor

@GrailsPluginDescriptor(dependsOn = ['another'])
class MyGrailsPlugin extends Plugin {

    static String SHUTDOWN_FIELD = 'not-updated'

    BeanRegistrar beanRegistrar() {
        { BeanRegistry registry, Environment environment ->
            registry.registerBean('classEditor', ClassEditor) { it.supplier { new ClassEditor(grailsApplication.classLoader) } }
        } as BeanRegistrar
    }

    void onShutdown(Map<String, Object> event) {
        SHUTDOWN_FIELD = 'updated'
    }
}

@GrailsPluginDescriptor(watchedResources = ['classpath:org/codehaus/groovy/grails/plugins/*.xml'])
class AnotherGrailsPlugin extends Plugin {

    void doWithApplicationContext() {
        applicationContext.registerBeanDefinition('localeResolver', new RootBeanDefinition(CookieLocaleResolver))
    }
}

@GrailsPluginDescriptor(dependsOn = ['my', 'another'])
class SomeOtherGrailsPlugin extends Plugin {
}

@GrailsPluginDescriptor(after = ['my', 'someOther'])
class ShouldLoadLastGrailsPlugin extends Plugin {
}

@GrailsPluginDescriptor(evict = ['someOther'])
class ShouldEvictSomeOtherGrailsPlugin extends Plugin {
}

@GrailsPluginDescriptor(observe = ['another'])
class ObservingGrailsPlugin extends Plugin {

    void onChange(Map<String, Object> event) {
        assert event.source != null
        event.source = 'bar'
    }
}

@GrailsPluginDescriptor(observe = ['*'])
class ObservingAllGrailsPlugin extends Plugin {
}

@GrailsPluginDescriptor(environmentsIncludes = ['neverCurrent'])
class OtherEnvironmentGrailsPlugin extends Plugin {
}
''')
    }

    @TempDir Path temp

    private Class plugin(String name) {
        fixtures.loadClass("${name}GrailsPlugin")
    }

    private Class[] plugins(String... names) {
        names.collect { plugin(it) } as Class[]
    }

    def 'registers observers and informs them of plugin events'() {
        given: 'a plugin manager with an observed plugin and an observer plugin'
        def pluginDiscovery = new DefaultPluginDiscovery().tap {
            pluginClasses = plugins('My', 'Another', 'Observing')
            init(new StandardEnvironment())
        }
        def pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery).tap {
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
        def pluginDiscovery = new DefaultPluginDiscovery().tap {
            pluginClasses = plugins('Another', 'ObservingAll')
            init(new StandardEnvironment())
        }
        def pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery)

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

    def 'skips plugins that do not support the current environment during loading'() {
        given: 'a plugin manager with one plugin limited to another environment'
        def pluginDiscovery = new DefaultPluginDiscovery().tap {
            pluginClasses = plugins('Another', 'OtherEnvironment')
            init(new StandardEnvironment())
        }
        def pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery)

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'the plugins of the current environment are loaded and the other one is skipped'
        with(pluginManager) {
            hasGrailsPlugin('another')
            getGrailsPlugin('another').pluginInstance
            !hasGrailsPlugin('otherEnvironment')
        }
    }

    def 'loads a plugin class registered directly, which has no version and so meets no version range'() {
        given: 'a plugin class compiled outside a build that sets a version, registered directly'
        Class classEditor = new GroovyClassLoader(GrailsPluginManagerTests.classLoader).parseClass('''
package org.grails.plugins

@grails.plugins.metadata.GrailsPluginDescriptor
class ClassEditorGrailsPlugin extends grails.plugins.Plugin {
}
''')
        def pluginDiscovery = new DefaultPluginDiscovery([classEditor] as Class[]).tap {
            init(new StandardEnvironment())
        }
        def pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery)

        when: 'plugins are loaded and the classEditor plugin is requested'
        pluginManager.loadPlugins()
        def plugin = pluginManager.getGrailsPlugin('classEditor')

        then: 'the plugin is available, without a version'
        plugin
        plugin.name == 'classEditor'
        plugin.pluginClass.name == 'org.grails.plugins.ClassEditorGrailsPlugin'
        plugin.version == null

        and: 'an unknown version cannot be checked against a range, so only a request for any version finds it'
        pluginManager.getGrailsPlugin('classEditor', '*')
        !pluginManager.getGrailsPlugin('classEditor', '1.1')
        !pluginManager.getGrailsPlugin('classEditor', '1.1 > *')
    }

    def 'resolves a plugin by the version its descriptor on the class path records'() {
        given: 'a plugin class whose class path holds a descriptor recording version 1.1, as a plugin jar does'
        Path descriptor = temp.resolve(PluginUtils.getPluginDescriptorPath('versioned'))
        Files.createDirectories(descriptor.parent)
        Files.writeString(descriptor, 'format.version=1\nname=versioned\nclass=VersionedGrailsPlugin\nversion=1.1\n')
        Path imports = temp.resolve(PluginUtils.PLUGINS_IMPORTS_PATH)
        Files.writeString(imports, 'versioned\n')
        def loader = new GroovyClassLoader(new URLClassLoader([temp.toUri().toURL()] as URL[], GrailsPluginManagerTests.classLoader))
        Class versioned = loader.parseClass('''
@grails.plugins.metadata.GrailsPluginDescriptor
class VersionedGrailsPlugin extends grails.plugins.Plugin {
}
''')
        def pluginDiscovery = new DefaultPluginDiscovery().tap {
            pluginClasses = [versioned] as Class[]
            init(new StandardEnvironment())
        }
        def pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery)

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'the plugin has the recorded version'
        pluginManager.getGrailsPlugin('versioned').version == '1.1'

        and: 'it is returned for the matching version and not for another'
        pluginManager.getGrailsPlugin('versioned', '1.1')
        !pluginManager.getGrailsPlugin('versioned', '1.2')
    }

    def 'loads plugins successfully when a plugin declares after'() {
        given: 'a plugin manager with a plugin that declares loadAfter dependencies'
        def pluginDiscovery = new DefaultPluginDiscovery().tap {
            pluginClasses = plugins('ShouldLoadLast', 'My', 'Another')
            init(new StandardEnvironment())
        }
        def pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery)

        when: 'plugins are loaded'
        pluginManager.loadPlugins()
        def plugins = pluginManager.getAllPlugins()*.name

        then: 'the plugin with loadAfter dependencies is loaded last'
        plugins.indexOf('shouldLoadLast') > plugins.indexOf('my')
        plugins.indexOf('shouldLoadLast') > plugins.indexOf('someOther')
    }

    def 'an application whose plugin misses a required dependency does not start'() {
        given: 'a plugin discovery missing a required dependency'
        def pluginDiscovery = new DefaultPluginDiscovery().tap {
            pluginClasses = plugins('My') // depends on 'another' plugin which is not included here
        }

        when: 'the plugins are discovered'
        pluginDiscovery.init(new StandardEnvironment())

        then: 'discovery fails, naming the plugin and its missing dependency'
        def e = thrown(PluginException)
        e.message.contains('Grails plug-in [my]')
        e.message.contains('dependency [another] with required version [*] is missing')
    }

    def 'loads plugins when required dependencies are satisfied'() {
        given: 'a plugin manager with all required dependencies present'
        def pluginDiscovery = new DefaultPluginDiscovery().tap {
            pluginClasses = plugins('My', 'Another', 'SomeOther')
            init(new StandardEnvironment())
        }
        def pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery)

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'the dependent plugin is loaded'
        pluginManager.hasGrailsPlugin('my')
    }

    def 'evicts superseded plugins during loading'() {
        given: 'a plugin manager with an evicting plugin'
        def pluginDiscovery = new DefaultPluginDiscovery().tap {
            pluginClasses = plugins('My', 'Another', 'SomeOther', 'ShouldEvictSomeOther')
            init(new StandardEnvironment())
        }
        def pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery)

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
        Class my = plugin('My')
        def pluginDiscovery = new DefaultPluginDiscovery().tap {
            pluginClasses = [my, plugin('Another')] as Class[] // Another satisfies the dependency of My
            init(new StandardEnvironment())
        }
        def pluginManager = new DefaultGrailsPluginManager(grailsApplication, pluginDiscovery).tap {
             applicationContext = ctx
        }

        when: 'plugins are loaded'
        pluginManager.loadPlugins()

        then: 'the plugin is loaded'
        pluginManager.hasGrailsPlugin('my')

        and: 'its shutdown marker is unchanged'
        my.SHUTDOWN_FIELD == 'not-updated'

        when: 'the plugin manager is shut down'
        pluginManager.shutdown()

        then: 'the shutdown callback updates the marker'
        my.SHUTDOWN_FIELD == 'updated'
    }
}
