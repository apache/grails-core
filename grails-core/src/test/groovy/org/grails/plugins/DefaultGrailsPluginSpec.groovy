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

import org.codehaus.groovy.control.MultipleCompilationErrorsException

import grails.core.DefaultGrailsApplication
import grails.plugins.DefaultGrailsPluginManager
import grails.plugins.Plugin
import grails.plugins.exceptions.PluginException
import grails.plugins.metadata.PluginDefinition
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import grails.util.BuildSettings
import org.apache.grails.core.plugins.ClasspathPluginFinder
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginClassFixtures
import org.apache.grails.core.plugins.PluginInfo
import org.apache.grails.core.plugins.PluginUtils

class DefaultGrailsPluginSpec extends Specification {

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

    /** The plugin a descriptor on the class path describes, as discovery finds it. */
    private static PluginInfo descriptorOf(String properties, Resource resource, Class<?> pluginClass = TestBinaryGrailsPlugin) {
        Properties props = new Properties()
        props.load(new StringReader(properties.stripIndent()))
        new PluginInfo(PluginDefinition.fromProperties(props.getProperty('name'), props), pluginClass, resource, null)
    }

    private PluginInfo testBinaryDescriptor(Resource resource = new ByteArrayResource(testBinary.getBytes('UTF-8'))) {
        descriptorOf(testBinary, resource)
    }

    /** A plugin class registered directly, described by its annotation. */
    private static PluginInfo registered(Class<?> pluginClass, Resource descriptor = null) {
        new PluginInfo(PluginUtils.definitionOf(pluginClass), pluginClass, descriptor, null)
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
            def binaryPlugin = new DefaultGrailsPlugin(testBinaryDescriptor(), new DefaultGrailsApplication())

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
            def binaryPlugin = new DefaultGrailsPlugin(descriptor, new DefaultGrailsApplication())
            def cssResource = binaryPlugin.getResource("/css/main.css")

        then:
            cssResource != null
        when:
            cssResource = binaryPlugin.resolveView("/css/foo.css")

        then:
            cssResource == null
    }

    def "Test mutual exclusion of plugin.yml and plugin.groovy is enforced by EPP"() {
        expect: "Constructor no longer throws when both config files exist - validation moved to GrailsEnvironmentPostProcessor"
        new DefaultGrailsPlugin(testBinaryDescriptor(), new DefaultGrailsApplication()) != null
    }

    def "Test a plugin whose descriptor names another class is rejected"() {
        given:
        def definition = new PluginDefinition.Builder(pluginName: 'other', pluginClass: 'com.example.OtherGrailsPlugin').build()

        when:
        new PluginInfo(definition, TestBinaryGrailsPlugin, null, null)

        then:
        def e = thrown(PluginException)
        e.message.contains('com.example.OtherGrailsPlugin')
        e.message.contains(TestBinaryGrailsPlugin.name)
    }

    def "Test a plugin whose class is not a Plugin is rejected"() {
        given:
        def definition = new PluginDefinition.Builder(pluginName: 'resource', pluginClass: TestBinaryResource.name).build()

        when:
        new PluginInfo(definition, TestBinaryResource, null, null)

        then:
        thrown(PluginException)
    }

    def "Test creation of a binary plugin annotated with @GrailsPluginDescriptor without GrailsPlugin suffix"() {
        given:
        def pluginClass = compilePlugin('AnnotatedStarterDescriptor', '''
            @grails.plugins.metadata.GrailsPluginDescriptor(
                    name = 'annotated-starter',
                    after = ['core'],
                    providedArtefacts = [org.grails.plugins.TestBinaryResource]
            )
            class AnnotatedStarterDescriptor extends grails.plugins.Plugin { }
        ''')
        def descriptor = registered(pluginClass, new ByteArrayResource(new byte[0]))

        when:
        def binaryPlugin = new DefaultGrailsPlugin(descriptor, new DefaultGrailsApplication())

        then:
        binaryPlugin.name == 'annotatedStarter'
        binaryPlugin.afterNames as List == ['core']
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
        def descriptor = registered(pluginClass, new ByteArrayResource(new byte[0]))
        def binaryPlugin = new DefaultGrailsPlugin(descriptor, new DefaultGrailsApplication(loader))

        when:
        def jsResource = binaryPlugin.getResource('/js/app.js')

        then:
        jsResource != null
        jsResource.inputStream.text == 'console.log(1)'

        cleanup:
        loader?.close()
    }

    def "Test a compiled plugin resolves archive root static resources and namespaced views"() {
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
        def descriptor = descriptorOf(descriptorFile.text, new UrlResource(descriptorFile.toURI().toURL()), pluginClass)
        def grailsApp = new DefaultGrailsApplication(loader)

        when:
        def binaryPlugin = new DefaultGrailsPlugin(descriptor, grailsApp)

        then: 'the compiler wrote the descriptor naming the class'
        descriptor.definition.pluginClass == 'fixture.starter.StarterRootGrailsPlugin'

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
                BeanRegistrar beanRegistrar() {
                    events << 'beanRegistrar'
                    return { registry, env -> } as BeanRegistrar
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
        def descriptor = registered(pluginClass, new ByteArrayResource(new byte[0]))
        def plugin = new DefaultGrailsPlugin(descriptor, grailsApp)
        def appCtx = new GenericApplicationContext()
        def discovery = new DefaultPluginDiscovery([pluginClass] as Class<?>[]).tap {
            loadPluginsFromClasspath = false
        }
        discovery.init(new StandardEnvironment())
        def manager = new DefaultGrailsPluginManager(grailsApp, discovery)
        manager.applicationContext = appCtx

        when: 'lifecycle methods are dispatched through GrailsPlugin and GrailsPluginManager'
        plugin.manager = manager
        def registrar = plugin.beanRegistrar
        plugin.doWithApplicationContext(appCtx)
        plugin.notifyOfEvent(DefaultGrailsPlugin.EVENT_ON_CONFIG_CHANGE, [key: 'val'])
        plugin.notifyOfEvent(DefaultGrailsPlugin.EVENT_ON_SHUTDOWN, [:])
        Plugin instance = plugin.pluginInstance
        manager.loadPlugins()
        manager.onStartup([started: true])

        then: 'every hook and aware callback was invoked'
        instance.pluginManager == manager
        instance.applicationContext == appCtx
        instance.events == ['beanRegistrar', 'doWithApplicationContext', 'onConfigChange', 'onShutdown']
        registrar != null
        manager.getGrailsPlugin('lifecycleStarter').pluginInstance.events.contains('onStartup')
    }

    def "a plugin can neither override doWithDynamicMethods nor declare an enabled attribute"() {
        when: 'a plugin overrides the removed doWithDynamicMethods hook, or declares an enabled attribute'
        new GroovyClassLoader().parseClass(source)

        then: 'it does not compile'
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains(expected)

        where:
        source | expected
        '''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class DynamicMethodsGrailsPlugin extends grails.plugins.Plugin {
                @Override
                void doWithDynamicMethods() { }
            }
        ''' | 'doWithDynamicMethods'
        '''
            @grails.plugins.metadata.GrailsPluginDescriptor(enabled = false)
            class DisabledGrailsPlugin extends grails.plugins.Plugin { }
        ''' | 'enabled'
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
        loaded instanceof DefaultGrailsPlugin
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
        def plugin = new DefaultGrailsPlugin(descriptor, new DefaultGrailsApplication())

        then:
        plugin.version == '98.7'
        plugin.getDependentVersion('core') == '98.7'
    }

    def "Test a plugin honors overridden descriptor name for views"() {
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

        when:
        def binaryPlugin = new DefaultGrailsPlugin(descriptor, grailsApp)

        then: 'the binary plugin uses the overridden name for registration and views'
        binaryPlugin.name == 'bar'
        binaryPlugin.resolveView('/bar/index.gsp') == MyView

        cleanup:
        loader?.close()
    }


    def "a plugin found on the class path and the same class registered directly offer the same capabilities"() {
        given: 'a plugin compiled into a root of its own with an artefact, precompiled views and a static asset'
        File root = new File(tempDir, 'capabilities-root')
        PluginClassFixtures.compile(root.toPath(), 'CapableGrailsPlugin.groovy', '''
            package fixture.capable
            @grails.plugins.metadata.GrailsPluginDescriptor(providedArtefacts = [org.grails.plugins.TestBinaryResource])
            class CapableGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        new File(root, PluginUtils.getPluginViewsPath('capable')).tap { parentFile.mkdirs() }.text = "/capable/index.gsp=${MyView.name}\n"
        new File(root, 'META-INF/static/js/capable.js').tap { parentFile.mkdirs() }.text = 'capable()'
        URLClassLoader loader = PluginClassFixtures.loaderOver(root.toPath())
        loaders << loader
        def application = new DefaultGrailsApplication(loader)

        and: 'the plugin as discovery finds it on the class path, and its class registered directly'
        PluginInfo found = new ClasspathPluginFinder().findClasspathPlugins(loader, null).find { it.name == 'capable' }
        def fromClasspath = new DefaultGrailsPlugin(found, application)
        def registeredDirectly = new DefaultGrailsPlugin(found.pluginClass, application)

        expect: 'only the plugin found on the class path has a descriptor'
        found.descriptorResource != null
        registeredDirectly.pluginInfo.descriptorResource == null

        and: 'both offer the artefacts, views, static resources and project directory the plugin has'
        [fromClasspath, registeredDirectly].every { DefaultGrailsPlugin plugin ->
            plugin.providedArtefacts as List == [TestBinaryResource] &&
                    plugin.resolveView('/capable/index.gsp') == MyView &&
                    plugin.getResource('/js/capable.js')?.inputStream?.text == 'capable()' &&
                    plugin.projectDirectory == null &&
                    !plugin.basePlugin
        }
    }

    def "a plugin class compiled in memory and registered directly has no resources of its own"() {
        given:
        Class<?> pluginClass = new GroovyClassLoader(getClass().classLoader).parseClass('''
            package fixture.memory
            @grails.plugins.metadata.GrailsPluginDescriptor(providedArtefacts = [org.grails.plugins.TestBinaryResource])
            class InMemoryGrailsPlugin extends grails.plugins.Plugin { }
        ''')

        when:
        def plugin = new DefaultGrailsPlugin(pluginClass, new DefaultGrailsApplication())

        then: 'it has the artefacts its annotation names'
        plugin.name == 'inMemory'
        plugin.pluginInstance.class == pluginClass
        plugin.providedArtefacts as List == [TestBinaryResource]

        and: 'no views, static resources, message bundles or project directory'
        plugin.resolveView('/inMemory/index.gsp') == null
        plugin.getResource('/js/app.js') == null
        plugin.getProperties(Locale.ENGLISH) == null
        plugin.projectDirectory == null
        !plugin.basePlugin
    }

    def "a plugin reads the message bundles beside its classes for a locale"() {
        given:
        File root = new File(tempDir, 'bundles-root')
        PluginClassFixtures.compilePlugin(root.toPath(), 'fixture.bundles.BundlesGrailsPlugin')
        new File(root, 'messages.properties').text = 'greeting=hello\nfarewell=bye\n'
        new File(root, 'messages_de.properties').text = 'greeting=hallo\n'
        new File(root, 'messages_fr.properties').text = 'greeting=bonjour\n'
        URLClassLoader loader = PluginClassFixtures.loaderOver(root.toPath())
        loaders << loader

        when:
        def plugin = new DefaultGrailsPlugin(loader.loadClass('fixture.bundles.BundlesGrailsPlugin'), new DefaultGrailsApplication(loader))
        Properties german = plugin.getProperties(Locale.GERMAN)

        then: 'the locale\'s bundle overrides the default one, and other locales are left out'
        german.getProperty('greeting') == 'hallo'
        german.getProperty('farewell') == 'bye'
    }

    def "a plugin loaded from the class directory of the project being run is the base plugin"() {
        given: 'a plugin compiled into the class directory of a project inside the base directory'
        File project = new File(BuildSettings.BASE_DIR, "build/tmp/base-plugin-${UUID.randomUUID()}")
        File classes = new File(project, BuildSettings.BUILD_CLASSES_PATH)
        PluginClassFixtures.compilePlugin(classes.toPath(), 'fixture.base.BaseProjectGrailsPlugin')
        URLClassLoader loader = PluginClassFixtures.loaderOver(classes.toPath())
        loaders << loader
        def application = new DefaultGrailsApplication(loader)
        Class<?> pluginClass = loader.loadClass('fixture.base.BaseProjectGrailsPlugin')
        def discovery = new DefaultPluginDiscovery([pluginClass] as Class<?>[]).tap {
            loadPluginsFromClasspath = false
        }
        discovery.init(new StandardEnvironment())
        def manager = new DefaultGrailsPluginManager(application, discovery)

        when:
        manager.loadPlugins()
        def plugin = (DefaultGrailsPlugin) manager.getGrailsPlugin('baseProject')

        then: 'its project directory is the project, and its views are the application\'s own'
        plugin.projectDirectory.canonicalFile == project.canonicalFile
        plugin.basePlugin
        manager.getPluginPath('baseProject') == ''

        cleanup:
        project.deleteDir()
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
