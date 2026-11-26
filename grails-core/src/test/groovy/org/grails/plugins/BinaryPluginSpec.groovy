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

import java.nio.file.Path

import grails.core.DefaultGrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.Plugin
import grails.plugins.exceptions.PluginException
import grails.plugins.metadata.PluginDefinition
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

    private static PluginDescriptor descriptorOf(String properties, Resource resource) {
        Properties props = new Properties()
        props.load(new StringReader(properties.stripIndent()))
        PluginDescriptor.fromProperties(props.getProperty('name'), resource, props)
    }

    private PluginDescriptor testBinaryDescriptor(Resource resource = new ByteArrayResource(testBinary.getBytes('UTF-8'))) {
        descriptorOf(testBinary, resource)
    }

    private final List<URLClassLoader> loaders = []

    void cleanup() {
        loaders*.close()
    }

    /**
     * Compiles a plugin class into a directory of its own, as a plugin jar holds it, so that the test class
     * path does not list it as a plugin of its own.
     */
    private Class<?> compilePlugin(String className, String source) {
        Path root = new File(tempDir, "compiled-${className}").toPath()
        PluginClassFixtures.compile(root, "${className}.groovy", source)
        URLClassLoader loader = PluginClassFixtures.loaderOver(root)
        loaders << loader
        loader.loadClass(className)
    }

    def "Test creation of a binary plugin"() {
        when:
            def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, testBinaryDescriptor(), new DefaultGrailsApplication())

        then:
            binaryPlugin.version == "1.0"
            binaryPlugin.providedArtefacts.size() == 1
            binaryPlugin.providedArtefacts[0] == TestBinaryResource
    }


    def "Test load static resource from binary plugin"() {
        when:
            def resource = new MockBinaryPluginResource(testBinary.getBytes('UTF-8'))
            def descriptor = testBinaryDescriptor(resource)
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
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, testBinaryDescriptor(), new DefaultGrailsApplication())

        then:
        binaryPlugin.propertySource == null
    }

    def "Test getPropertySource looks up plugin.yml from environment"() {
        given:
        def grailsApp = new DefaultGrailsApplication()
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, testBinaryDescriptor(), grailsApp)

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
        def grailsApp = new DefaultGrailsApplication()
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, testBinaryDescriptor(), grailsApp)

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
        new BinaryGrailsPlugin(TestBinaryGrailsPlugin, testBinaryDescriptor(), new DefaultGrailsApplication()) != null
    }

    def "Test a binary plugin whose descriptor names another class is rejected"() {
        given:
        def descriptor = new PluginDescriptor(null,
                new PluginDefinition.Builder(pluginName: 'other', pluginClass: 'com.example.OtherGrailsPlugin').build())

        when:
        new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())

        then:
        def e = thrown(PluginException)
        e.message.contains('com.example.OtherGrailsPlugin')
        e.message.contains(TestBinaryGrailsPlugin.name)
    }

    def "Test a binary plugin whose class is not a Plugin is rejected"() {
        given:
        def descriptor = new PluginDescriptor(null,
                new PluginDefinition.Builder(pluginName: 'resource', pluginClass: TestBinaryResource.name).build())

        when:
        new BinaryGrailsPlugin(TestBinaryResource, descriptor, new DefaultGrailsApplication())

        then:
        thrown(PluginException)
    }

    def "Test creation of a binary plugin annotated with @GrailsPluginDescriptor without GrailsPlugin suffix"() {
        given:
        def pluginClass = compilePlugin('AnnotatedStarterDescriptor', '''
            @grails.plugins.metadata.GrailsPluginDescriptor(
                    name = 'annotated-starter',
                    loadAfter = ['core'],
                    providedArtefacts = [org.grails.plugins.TestBinaryResource]
            )
            class AnnotatedStarterDescriptor extends grails.plugins.Plugin { }
        ''')
        def descriptor = new PluginDescriptor(new ByteArrayResource(new byte[0]), PluginUtils.definitionOf(pluginClass))

        when:
        def binaryPlugin = new BinaryGrailsPlugin(pluginClass, descriptor, new DefaultGrailsApplication())

        then:
        binaryPlugin.name == 'annotatedStarter'
        binaryPlugin.loadAfterNames as List == ['core']
        binaryPlugin.providedArtefacts as List == [TestBinaryResource]

        and: 'a class registered directly, without a descriptor of its own on the class path, has no version'
        binaryPlugin.version == null
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
        def descriptor = new PluginDescriptor(new ByteArrayResource(new byte[0]), PluginUtils.definitionOf(pluginClass))
        def binaryPlugin = new BinaryGrailsPlugin(pluginClass, descriptor, new DefaultGrailsApplication(loader))

        when:
        def jsResource = binaryPlugin.getResource('/js/app.js')

        then:
        jsResource != null
        jsResource.inputStream.text == 'console.log(1)'

        cleanup:
        loader?.close()
    }

    def "Test a compiled binary plugin resolves archive root static resources, namespaced views, and ignores refresh"() {
        given: 'a compiled plugin directory with its descriptor, views.properties, and META-INF/static asset'
        File root = new File(tempDir, 'starter-root')
        PluginClassFixtures.compilePlugin(root.toPath(), 'fixture.starter.StarterRootGrailsPlugin')
        File descriptorFile = new File(root, PluginUtils.getPluginDescriptorPath('starterRoot'))
        File viewsFile = new File(root, PluginUtils.getPluginViewsPath('starterRoot'))
        viewsFile.text = "/starter/index.gsp=${MyView.name}\n"
        File staticCss = new File(root, 'META-INF/static/css/starter.css')
        staticCss.parentFile.mkdirs()
        staticCss.text = 'body { color: green; }'

        def loader = PluginClassFixtures.loaderOver(root.toPath())
        def pluginClass = loader.loadClass('fixture.starter.StarterRootGrailsPlugin')
        def descriptor = descriptorOf(descriptorFile.text, new UrlResource(descriptorFile.toURI().toURL()))
        def grailsApp = new DefaultGrailsApplication(loader)

        when:
        def binaryPlugin = new BinaryGrailsPlugin(pluginClass, descriptor, grailsApp)
        binaryPlugin.refresh()

        then: 'the compiler wrote the descriptor naming the class'
        descriptor.pluginClassName == 'fixture.starter.StarterRootGrailsPlugin'

        and: 'static assets and precompiled views resolve from the archive root'
        binaryPlugin.getResource('/css/starter.css')?.inputStream?.text == 'body { color: green; }'
        binaryPlugin.resolveView('/starter/index.gsp') == MyView

        cleanup:
        loader?.close()
    }

    def "Test a Plugin subclass receives all lifecycle hooks"() {
        given:
        def pluginClass = compilePlugin('LifecycleStarterPlugin', '''
            import grails.plugins.Plugin
            import org.springframework.beans.factory.BeanRegistrar

            @grails.plugins.metadata.GrailsPluginDescriptor(name = 'lifecycle-starter')
            class LifecycleStarterPlugin extends Plugin {
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
        ''')
        def grailsApp = new DefaultGrailsApplication()
        def descriptor = new PluginDescriptor(new ByteArrayResource(new byte[0]), PluginUtils.definitionOf(pluginClass))
        def plugin = new BinaryGrailsPlugin(pluginClass, descriptor, grailsApp)
        def appCtx = new GenericApplicationContext()
        def springConfig = new DefaultRuntimeSpringConfiguration(appCtx)
        def discovery = new DefaultPluginDiscovery([pluginClass] as Class<?>[]).tap {
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
        Plugin instance = (Plugin) plugin.pluginInstance
        manager.loadPlugins()
        manager.onStartup([started: true])

        then: 'every hook and aware callback was invoked'
        instance.pluginManager == manager
        instance.applicationContext == appCtx
        instance.events == ['doWithSpring', 'doWithDynamicMethods', 'doWithApplicationContext', 'onConfigChange', 'onShutdown']
        plugin.beanRegistrar != null
        manager.getGrailsPlugin('lifecycleStarter').pluginInstance.events.contains('onStartup')
    }

    def "Test @GrailsPluginDescriptor enabled=false disables a Plugin subclass"() {
        given:
        def pluginClass = compilePlugin('DisabledAnnotatedPlugin', '''
            @grails.plugins.metadata.GrailsPluginDescriptor(name = 'disabled-annotated', enabled = false)
            class DisabledAnnotatedPlugin extends grails.plugins.Plugin { }
        ''')
        def descriptor = new PluginDescriptor(new ByteArrayResource(new byte[0]), PluginUtils.definitionOf(pluginClass))

        when:
        def plugin = new BinaryGrailsPlugin(pluginClass, descriptor, new DefaultGrailsApplication())

        then:
        !plugin.enabled
    }

    def "Test DefaultGrailsPluginManager loads a compiled plugin from its classpath descriptor"() {
        given: 'a classpath root holding a compiled plugin, whose descriptor records a version and an artefact'
        File root = new File(tempDir, 'manager-plugin')
        PluginClassFixtures.compilePlugin(root.toPath(), 'fixture.manager.ManagerProbeGrailsPlugin')
        File descFile = new File(root, PluginUtils.getPluginDescriptorPath('managerProbe'))
        descFile.text = '''
            format.version=1
            name=managerProbe
            version=1.2.0
            class=fixture.manager.ManagerProbeGrailsPlugin
            artefacts.Service=org.grails.plugins.TestBinaryResource
        '''.stripIndent()

        def loader = PluginClassFixtures.loaderOver(root.toPath())
        def originalCl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader
        def grailsApp = new DefaultGrailsApplication(loader)
        def discovery = new DefaultPluginDiscovery()
        discovery.init(new StandardEnvironment())
        def manager = new DefaultGrailsPluginManager(grailsApp, discovery)
        manager.applicationContext = new GenericApplicationContext()

        when:
        manager.loadPlugins()

        then: 'the plugin is registered and loaded as its descriptor describes it'
        def loaded = manager.getGrailsPlugin('managerProbe')
        loaded instanceof BinaryGrailsPlugin
        loaded.version == '1.2.0'
        loaded.pluginClass.name == 'fixture.manager.ManagerProbeGrailsPlugin'
        loaded.providedArtefacts as List == [TestBinaryResource]

        cleanup:
        Thread.currentThread().contextClassLoader = originalCl
        loader?.close()
    }

    def "activation uses the same complete definition as discovery"() {
        given:
        def descriptor = descriptorOf('''
            name=testBinary
            version=98.7
            class=org.grails.plugins.TestBinaryGrailsPlugin
            dependsOn.core=98.7
        ''', null)

        when:
        def plugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, new DefaultGrailsApplication())

        then:
        plugin.version == '98.7'
        plugin.getDependentVersion('core') == '98.7'
    }

    def "Test BinaryGrailsPlugin honors overridden descriptor name for views and propertySource"() {
        given: 'a plugin class whose descriptor declares the logical name bar'
        File root = new File(tempDir, 'renamed-binary-root')
        File descFile = new File(root, PluginUtils.getPluginDescriptorPath('bar'))
        descFile.parentFile.mkdirs()
        descFile.text = '''
            format.version=1
            name=bar
            version=1.0.0
            class=org.grails.plugins.TestBinaryGrailsPlugin
        '''.stripIndent()
        File viewsFile = new File(root, PluginUtils.getPluginViewsPath('bar'))
        viewsFile.text = "/bar/index.gsp=${MyView.name}\n"

        def descriptor = descriptorOf(descFile.text, new UrlResource(descFile.toURI().toURL()))
        def loader = new URLClassLoader([root.toURI().toURL()] as URL[], getClass().classLoader)
        def grailsApp = new DefaultGrailsApplication(loader)
        def appCtx = new GenericApplicationContext()
        appCtx.environment.propertySources.addLast(new MapPropertySource('bar-plugin.yml', [renamed: 'yes']))
        grailsApp.mainContext = appCtx

        when:
        def binaryPlugin = new BinaryGrailsPlugin(TestBinaryGrailsPlugin, descriptor, grailsApp)

        then: 'the binary plugin uses the overridden name for registration, views, and config lookup'
        binaryPlugin.name == 'bar'
        binaryPlugin.resolveView('/bar/index.gsp') == MyView
        binaryPlugin.propertySource?.getProperty('renamed') == 'yes'

        cleanup:
        loader?.close()
    }

}

/**
 * A plugin class described by the descriptors the tests hand it. It carries no annotation, so that the
 * test class path does not list it as a plugin of its own.
 */
class TestBinaryGrailsPlugin extends Plugin {
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
