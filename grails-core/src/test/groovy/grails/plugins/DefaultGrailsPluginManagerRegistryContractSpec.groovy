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
package grails.plugins

import grails.core.DefaultGrailsApplication
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginUtils
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

class DefaultGrailsPluginManagerRegistryContractSpec extends Specification {

    void 'discovery and the loaded registry share one order, whatever order the plugin classes come in'() {
        given:
        def pluginClasses = orderingPluginClasses()
        def expectedOrder = [
                'registryOrderingBefore',
                'registryOrderingRoot',
                'registryOrderingLeft',
                'registryOrderingRight',
                'registryOrderingJoin',
                'registryOrderingTail'
        ]

        when:
        def orders = [pluginClasses, pluginClasses.reverse(), pluginClasses.sort(false) { it.name }].collect { List<Class<?>> classes ->
            def discovery = createDiscovery(classes)
            def manager = createManager(discovery)
            [discovery.getPluginsInTopologicalOrder()*.name, manager.getAllPlugins()*.name]
        }

        then:
        orders.every { it == [expectedOrder, expectedOrder] }
    }

    void 'the loaded registry de-duplicates plugins and resolves each plugin consistently'() {
        given:
        def pluginClasses = orderingPluginClasses()
        def tailPluginClass = pluginClasses.find { it.name == 'RegistryOrderingTailGrailsPlugin' }
        def discovery = createDiscovery(pluginClasses + tailPluginClass)
        def manager = createManager(discovery)

        when:
        def allPlugins = manager.getAllPlugins()
        def expectedTailPlugin = allPlugins.find { it.name == 'registryOrderingTail' }
        def pluginByName = manager.getGrailsPlugin('registryOrderingTail')
        def pluginByClassName = manager.getGrailsPluginForClassName(tailPluginClass.name)
        def pluginByVersion = manager.getGrailsPlugin('registryOrderingTail', '1.0.0')

        then:
        allPlugins.count { it.name == 'registryOrderingTail' } == 1
        expectedTailPlugin != null
        manager.hasGrailsPlugin('registryOrderingTail')
        pluginByName != null
        pluginByClassName != null
        pluginByVersion != null
        pluginByName.is(expectedTailPlugin)
        pluginByClassName.is(expectedTailPlugin)
        pluginByVersion.is(expectedTailPlugin)
    }

    private static DefaultPluginDiscovery createDiscovery(List<Class<?>> pluginClasses) {
        def discovery = new DefaultPluginDiscovery(pluginClasses as Class<?>[])
        discovery.loadPluginsFromClasspath = false
        discovery.init(new StandardEnvironment())
        discovery
    }

    private static DefaultGrailsPluginManager createManager(DefaultPluginDiscovery discovery) {
        def manager = new DefaultGrailsPluginManager(new DefaultGrailsApplication(), discovery)
        manager.loadPlugins()
        manager
    }

    /**
     * The ordering plugins, parsed by a class loader whose class path holds their descriptors, as a plugin jar
     * does, so that each has the version its descriptor records.
     */
    private static List<Class<?>> orderingPluginClasses() {
        Map<String, String> annotations = [
                RegistryOrderingTailGrailsPlugin  : "after = ['registryOrderingJoin']",
                RegistryOrderingJoinGrailsPlugin  : "after = ['registryOrderingLeft', 'registryOrderingRight']",
                RegistryOrderingRightGrailsPlugin : "after = ['registryOrderingRoot']",
                RegistryOrderingLeftGrailsPlugin  : "after = ['registryOrderingRoot']",
                RegistryOrderingRootGrailsPlugin  : '',
                RegistryOrderingBeforeGrailsPlugin: "before = ['registryOrderingRoot']"
        ]
        File root = File.createTempDir()
        root.deleteOnExit()
        File imports = new File(root, PluginUtils.PLUGINS_IMPORTS_PATH)
        imports.parentFile.mkdirs()
        annotations.keySet().each { String className ->
            String name = PluginUtils.normalizePluginName(className[0].toLowerCase() + className[1..-1] - 'GrailsPlugin')
            imports << "${name}\n"
            File descriptor = new File(root, PluginUtils.getPluginDescriptorPath(name))
            descriptor.parentFile.mkdirs()
            descriptor.text = "format.version=1\nname=${name}\nclass=${className}\nversion=1.0.0\n"
        }
        def classLoader = new GroovyClassLoader(new URLClassLoader([root.toURI().toURL()] as URL[],
                DefaultGrailsPluginManagerRegistryContractSpec.classLoader))
        annotations.collect { String className, String attributes ->
            classLoader.parseClass(
                    "@grails.plugins.metadata.GrailsPluginDescriptor(${attributes}) class ${className} extends grails.plugins.Plugin { }")
        }
    }
}
