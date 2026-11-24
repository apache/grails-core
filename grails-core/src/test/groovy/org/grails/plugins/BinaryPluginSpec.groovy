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

package org.grails.plugins

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplicationLifeCycle
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import grails.plugins.PluginManagerAware
import grails.plugins.metadata.GrailsPluginDescriptor
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginClassFixtures
import org.apache.grails.core.plugins.PluginDescriptor
import org.apache.grails.core.plugins.PluginUtils
import org.grails.spring.DefaultRuntimeSpringConfiguration

class BinaryPluginSpec extends Specification {

    @TempDir
    File tempDir

    @Shared
    String testBinary = '''
    format.version=1
    name=testBinary
    version=1.0
    class=org.grails.plugins.TestBinaryGrailsPlugin
    artefacts.Domain=org.grails.plugins.TestBinaryResource
    '''

    def "Test creation of a binary plugin"() {
        when:
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], ['org.grails.plugins.TestBinaryResource'])
            def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())

        then:
            binaryPlugin.version == "1.0"
            binaryPlugin.providedArtefacts.size() == 1
            binaryPlugin.providedArtefacts[0] == TestBinaryResource
    }


    def "Test load static resource from binary plugin"() {
        when:
            def resource = new MockBinaryPluginResource(testBinary.getBytes('UTF-8'))
        def descriptor = new PluginDescriptor(resource, ['org.grails.plugins.TestBinaryResource'], [])
            resource.relativesResources['static/css/main.css'] = new ByteArrayResource(''.bytes)
            def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())
            def cssResource = binaryPlugin.getResource("/css/main.css")

        then:
            cssResource != null
        when:
            cssResource = binaryPlugin.resolveView("/css/foo.css")

        then:
            cssResource == null
    }

    def "Test getPropertySource returns null when no mainContext is set on GrailsApplication"() {
        when:
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], [])
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())

        then:
        binaryPlugin.propertySource == null
    }

    def "Test getPropertySource looks up plugin.yml from environment"() {
        given:
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], [])
        def grailsApp = new DefaultGrailsApplication()
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, grailsApp)

        def appCtx = new GenericApplicationContext()
        def ymlPropertySource = new MapPropertySource('testBinary-plugin.yml', [foo: 'bar'])
        appCtx.environment.propertySources.addLast(ymlPropertySource)

        when:
        grailsApp.mainContext = appCtx

        then:
        binaryPlugin.propertySource != null
        binaryPlugin.propertySource.getProperty('foo') == 'bar'
    }

    def "Test getPropertySource looks up plugin.groovy from environment"() {
        given:
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], [])
        def grailsApp = new DefaultGrailsApplication()
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, grailsApp)

        def appCtx = new GenericApplicationContext()
        def groovyPropertySource = new MapPropertySource('testBinary-plugin.groovy', [bar: 'foo'])
        appCtx.environment.propertySources.addLast(groovyPropertySource)

        when:
        grailsApp.mainContext = appCtx

        then:
        binaryPlugin.propertySource != null
        binaryPlugin.propertySource.getProperty('bar') == 'foo'
    }

    def "Test mutual exclusion of plugin.yml and plugin.groovy is enforced by EPP"() {
        expect: "Constructor no longer throws when both config files exist - validation moved to GrailsEnvironmentPostProcessor"
        def descriptor = new PluginDescriptor(new ByteArrayResource(testBinary.getBytes('UTF-8')), ['org.grails.plugins.TestBinaryResource'], [])
        new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication()) != null
    }

    def "Test creation of a descriptor-less starter binary plugin"() {
        given:
        def props = new Properties()
        props.load(new StringReader('''
            format.version=1
            name=starterPlugin
            version=2.0.0
            loadAfter=core
            metadata.complete=true
            artefacts.Service=org.grails.plugins.TestBinaryResource
        '''.stripIndent()))
        def descriptor = PluginDescriptor.fromProperties('starterPlugin', new ByteArrayResource(new byte[0]), props)
        def metadata = org.apache.grails.core.plugins.PluginUtils.extractPluginMetadata(descriptor, null)
        def grailsApp = new DefaultGrailsApplication()

        when:
        def binaryPlugin = new BinaryGrailsPlugin(descriptor, metadata, grailsApp)

        then:
        binaryPlugin.name == 'starterPlugin'
        binaryPlugin.version == '2.0.0'
        binaryPlugin.pluginClass == null
        binaryPlugin.loadAfterNames as List == ['core']
        binaryPlugin.providedArtefacts as List == [TestBinaryResource]
        binaryPlugin.enabled
    }

    def "Test creation of a binary plugin annotated with @GrailsPluginDescriptor without GrailsPlugin suffix"() {
        given:
        def descriptor = new PluginDescriptor(new ByteArrayResource(new byte[0]), [AnnotatedStarterDescriptor.name], [TestBinaryResource.name])
        def grailsApp = new DefaultGrailsApplication()

        when:
        def binaryPlugin = new BinaryGrailsPlugin(AnnotatedStarterDescriptor, descriptor, grailsApp)

        then:
        binaryPlugin.name == 'annotatedStarter'
        binaryPlugin.version == '1.5.0'
        binaryPlugin.loadAfterNames as List == ['core']
        binaryPlugin.providedArtefacts as List == [TestBinaryResource]
    }

    def "Test getResource resolves META-INF/static assets for class-backed binary plugins"() {
        given: 'a compiled plugin class in a root containing META-INF/static/js/app.js'
        File root = new File(tempDir, 'class-static-root')
        PluginClassFixtures.compilePlugin(root.toPath(), 'fixture.staticres.StaticResGrailsPlugin')
        File jsFile = new File(root, 'META-INF/static/js/app.js')
        jsFile.parentFile.mkdirs()
        jsFile.text = 'console.log(1)'
        URLClassLoader loader = PluginClassFixtures.loaderOver(root.toPath())
        Class<?> pluginClass = loader.loadClass('fixture.staticres.StaticResGrailsPlugin')
        def descriptor = new PluginDescriptor(new ByteArrayResource(new byte[0]), [pluginClass.name], [])
        def binaryPlugin = new BinaryGrailsPlugin(pluginClass, descriptor, new DefaultGrailsApplication(loader))

        when:
        def jsResource = binaryPlugin.getResource('/js/app.js')

        then:
        jsResource != null
        jsResource.inputStream.text == 'console.log(1)'

        cleanup:
        loader?.close()
    }

    def "Test descriptor-less starter plugin resolves archive root static resources, namespaced views, and ignores refresh"() {
        given: 'an exploded starter plugin directory with descriptor, views.properties, and META-INF/static asset'
        File root = new File(tempDir, 'starter-root')
        File descriptorFile = new File(root, PluginUtils.getPluginDescriptorPath('starterRoot'))
        descriptorFile.parentFile.mkdirs()
        descriptorFile.text = '''
            format.version=1
            name=starterRoot
            version=1.0.0
            metadata.complete=true
        '''.stripIndent()
        File viewsFile = new File(root, PluginUtils.getPluginViewsPath('starterRoot'))
        viewsFile.text = "/starter/index.gsp=${MyView.name}\n"
        File staticCss = new File(root, 'META-INF/static/css/starter.css')
        staticCss.parentFile.mkdirs()
        staticCss.text = 'body { color: green; }'

        def props = new Properties()
        descriptorFile.withInputStream { props.load(it) }
        def descriptor = PluginDescriptor.fromProperties('starterRoot', new UrlResource(descriptorFile.toURI().toURL()), props)
        def metadata = PluginUtils.extractPluginMetadata(descriptor, null)
        def loader = new URLClassLoader([root.toURI().toURL()] as URL[], getClass().classLoader)
        def grailsApp = new DefaultGrailsApplication(loader)

        when:
        def binaryPlugin = new BinaryGrailsPlugin(descriptor, metadata, grailsApp)
        binaryPlugin.refresh()

        then: 'static assets and precompiled views resolve from the derived archive root'
        binaryPlugin.getResource('/css/starter.css')?.inputStream?.text == 'body { color: green; }'
        binaryPlugin.resolveView('/starter/index.gsp') == MyView

        cleanup:
        loader?.close()
    }

    def "Test Java @GrailsPluginDescriptor implementing GrailsApplicationLifeCycle receives all lifecycle hooks"() {
        given:
        def grailsApp = new DefaultGrailsApplication()
        def descriptor = new PluginDescriptor(new ByteArrayResource(new byte[0]), [LifecycleStarterPlugin.name], [])
        def plugin = new BinaryGrailsPlugin(LifecycleStarterPlugin, descriptor, grailsApp)
        def appCtx = new GenericApplicationContext()
        def springConfig = new DefaultRuntimeSpringConfiguration(appCtx)
        def discovery = new DefaultPluginDiscovery([LifecycleStarterPlugin] as Class<?>[]).tap {
            loadPluginsFromClasspath = false
        }
        discovery.init(new StandardEnvironment())
        def manager = new DefaultGrailsPluginManager(grailsApp, discovery)
        manager.applicationContext = appCtx

        when: 'lifecycle methods are dispatched through GrailsPlugin and GrailsPluginManager'
        plugin.manager = manager
        plugin.doWithRuntimeConfiguration(springConfig)
        plugin.doWithDynamicMethods(appCtx)
        plugin.doWithApplicationContext(appCtx)
        plugin.notifyOfEvent(DefaultGrailsPlugin.EVENT_ON_CONFIG_CHANGE, [key: 'val'])
        plugin.notifyOfEvent(DefaultGrailsPlugin.EVENT_ON_SHUTDOWN, [:])
        LifecycleStarterPlugin instance = (LifecycleStarterPlugin) plugin.pluginInstance
        manager.loadPlugins()
        manager.onStartup([started: true])

        then: 'every hook and aware callback was invoked'
        instance.pluginManager == manager
        instance.applicationContext == appCtx
        instance.events == ['doWithSpring', 'doWithDynamicMethods', 'doWithApplicationContext', 'onConfigChange', 'onShutdown']
        plugin.beanRegistrar != null
        ((LifecycleStarterPlugin) manager.getGrailsPlugin('lifecycleStarter').pluginInstance).events.contains('onStartup')
    }

    def "Test @GrailsPluginDescriptor enabled=false disables a Plugin subclass"() {
        given:
        def grailsApp = new DefaultGrailsApplication()
        def descriptor = new PluginDescriptor(new ByteArrayResource(new byte[0]), [DisabledAnnotatedPlugin.name], [])

        when:
        def plugin = new BinaryGrailsPlugin(DisabledAnnotatedPlugin, descriptor, grailsApp)

        then:
        !plugin.enabled
    }

    def "Test DefaultGrailsPluginManager loads descriptor-less starter plugins from classpath"() {
        given: 'a classpath root containing a descriptor-less starter plugin'
        File root = new File(tempDir, 'manager-starter')
        File imports = new File(root, PluginUtils.PLUGINS_IMPORTS_PATH)
        imports.parentFile.mkdirs()
        imports.text = 'managerStarter\n'
        File descFile = new File(root, PluginUtils.getPluginDescriptorPath('managerStarter'))
        descFile.parentFile.mkdirs()
        descFile.text = '''
            format.version=1
            name=managerStarter
            version=1.2.0
            metadata.complete=true
            artefacts.Service=org.grails.plugins.TestBinaryResource
        '''.stripIndent()

        def loader = new URLClassLoader([root.toURI().toURL()] as URL[], getClass().classLoader)
        def originalCl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader
        def grailsApp = new DefaultGrailsApplication(loader)
        def discovery = new DefaultPluginDiscovery()
        discovery.init(new StandardEnvironment())
        def manager = new DefaultGrailsPluginManager(grailsApp, discovery)
        manager.applicationContext = new GenericApplicationContext()

        when:
        manager.loadPlugins()

        then: 'the descriptor-less starter plugin is registered and loaded'
        def loaded = manager.getGrailsPlugin('managerStarter')
        loaded != null
        loaded.version == '1.2.0'
        loaded.pluginClass == null
        loaded.providedArtefacts as List == [TestBinaryResource]

        cleanup:
        Thread.currentThread().contextClassLoader = originalCl
        loader?.close()
    }

}

@GrailsPluginDescriptor(name = 'lifecycle-starter', version = '1.0.0')
class LifecycleStarterPlugin implements GrailsApplicationLifeCycle, PluginManagerAware, ApplicationContextAware {

    GrailsPluginManager pluginManager
    ApplicationContext applicationContext
    List<String> events = []

    @Override
    Closure doWithSpring() {
        events << 'doWithSpring'
        return { -> }
    }

    @Override
    BeanRegistrar beanRegistrar() {
        return { registry, env -> } as BeanRegistrar
    }

    @Override
    void doWithDynamicMethods() {
        events << 'doWithDynamicMethods'
    }

    @Override
    void doWithApplicationContext() {
        events << 'doWithApplicationContext'
    }

    @Override
    void onConfigChange(Map<String, Object> event) {
        events << 'onConfigChange'
    }

    @Override
    void onStartup(Map<String, Object> event) {
        events << 'onStartup'
    }

    @Override
    void onShutdown(Map<String, Object> event) {
        events << 'onShutdown'
    }
}

@GrailsPluginDescriptor(name = 'disabled-annotated', version = '1.0.0', enabled = false)
class DisabledAnnotatedPlugin extends Plugin {
}

@grails.plugins.metadata.GrailsPluginDescriptor(
        name = 'annotated-starter',
        version = '1.5.0',
        loadAfter = ['core']
)
class AnnotatedStarterDescriptor {
}

class TestBinaryGrailsPlugin {
    def version = 1.0
}

class TestBinaryResource {}

class MockBinaryPluginResource extends ByteArrayResource {

    Map<String, Resource> relativesResources = [:]

    MockBinaryPluginResource(byte[] byteArray) {
        super(byteArray)
    }

    @Override
    Resource createRelative(String relativePath) {
        return relativesResources[relativePath]
    }
}

class MyView extends Script {
    @Override
    Object run() {
        return "Good"
    }
}

