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
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

class DefaultGrailsPluginManagerRegistryContractSpec extends Specification {

    void 'the discovery load order consumed by the manager is deterministic for a combined ordering graph'() {
        given:
        def pluginClasses = orderingPluginClasses()
        def expectedLoadOrder = [
                'registryOrderingRoot',
                'registryOrderingBefore',
                'registryOrderingRight',
                'registryOrderingLeft',
                'registryOrderingJoin',
                'registryOrderingTail'
        ]

        when:
        def loadOrders = (1..3).collect {
            def discovery = createDiscovery(pluginClasses)
            createManager(discovery)
            discovery.getPluginsInLoadOrder()*.name
        }

        then:
        loadOrders == [expectedLoadOrder, expectedLoadOrder, expectedLoadOrder]
    }

    void 'the loaded registry exposes a deterministic topological order for a combined ordering graph'() {
        given:
        def pluginClasses = orderingPluginClasses()
        def expectedTopologicalOrder = [
                'registryOrderingBefore',
                'registryOrderingRoot',
                'registryOrderingRight',
                'registryOrderingLeft',
                'registryOrderingJoin',
                'registryOrderingTail'
        ]

        when:
        def topologicalOrders = (1..3).collect {
            def discovery = createDiscovery(pluginClasses)
            createManager(discovery).getAllPlugins()*.name
        }

        then:
        topologicalOrders == [expectedTopologicalOrder, expectedTopologicalOrder, expectedTopologicalOrder]
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

    private static List<Class<?>> orderingPluginClasses() {
        def classLoader = new GroovyClassLoader()
        [
                classLoader.parseClass('''
class RegistryOrderingTailGrailsPlugin {
    def version = '1.0.0'
    def loadAfter = ['registryOrderingJoin']
}
'''),
                classLoader.parseClass('''
class RegistryOrderingJoinGrailsPlugin {
    def version = '1.0.0'
    def loadAfter = ['registryOrderingLeft', 'registryOrderingRight']
}
'''),
                classLoader.parseClass('''
class RegistryOrderingRightGrailsPlugin {
    def version = '1.0.0'
    def loadAfter = ['registryOrderingRoot']
}
'''),
                classLoader.parseClass('''
class RegistryOrderingLeftGrailsPlugin {
    def version = '1.0.0'
    def loadAfter = ['registryOrderingRoot']
}
'''),
                classLoader.parseClass('''
class RegistryOrderingRootGrailsPlugin {
    def version = '1.0.0'
}
'''),
                classLoader.parseClass('''
class RegistryOrderingBeforeGrailsPlugin {
    def version = '1.0.0'
    def loadBefore = ['registryOrderingRoot']
}
''')
        ]
    }
}
