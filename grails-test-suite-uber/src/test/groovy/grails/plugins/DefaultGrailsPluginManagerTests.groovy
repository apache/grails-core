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
import grails.core.GrailsApplication
import groovy.lang.GroovyClassLoader

import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.filters.IncludingPluginFilter
import org.apache.grails.core.plugins.PluginDiscovery
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull

class DefaultGrailsPluginManagerTests {

    private Class<?> first
    private Class<?> second
    private Class<?> third
    private Class<?> fourth

    @Test
    @SuppressWarnings('rawtypes')
    void testLoadPlugins() {

        var gcl = new GroovyClassLoader()

        first = gcl.parseClass('''class FirstGrailsPlugin {
def version = 1.0
}''')
        second = gcl.parseClass('''class SecondGrailsPlugin {
def version = 1.0
def dependsOn = [first:version]
}''')
        third = gcl.parseClass('''import grails.util.GrailsUtil
class ThirdGrailsPlugin {
def version = GrailsUtil.getGrailsVersion()
def dependsOn = [i18n:version]
}''')
        fourth = gcl.parseClass('''class FourthGrailsPlugin {
def version = 1.0
def dependsOn = [second:version, third:version]
}''')

        GrailsApplication app = new DefaultGrailsApplication(new Class[0], gcl)
        var parent = new GenericApplicationContext()
        parent.getDefaultListableBeanFactory().registerSingleton(GrailsApplication.APPLICATION_ID, app)

        var discovery = new DefaultPluginDiscovery([first, second, third, fourth] as Class[])
        discovery.setPluginFilter(new IncludingPluginFilter('dataSource', 'first', 'third'))
        discovery.init(new StandardEnvironment())
        parent.getDefaultListableBeanFactory().registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
        var manager = new DefaultGrailsPluginManager(app, discovery)
        manager.setParentApplicationContext(parent)

        manager.loadPlugins()

        var plugins = manager.getAllPlugins()
        var pluginList = Arrays.asList(plugins)

        assertNotNull(manager.getGrailsPlugin('dataSource'))
        assertNotNull(manager.getGrailsPlugin('first'))
        assertNotNull(manager.getGrailsPlugin('third'))
        //dataSource depends on core
        assertNotNull(manager.getGrailsPlugin('core'))
        //third depends on i18n
        assertNotNull(manager.getGrailsPlugin('third'))

        assertEquals(5, pluginList.size(), 'Expected plugins not loaded. Expected ' + 5 + ' but got ' + pluginList)
    }

    /**
     * Test the known 1.0.2 failure where:
     *
     * mail 0.3 = has no deps
     * quartz 0.3-SNAPSHOT: loadAfter = ['core', 'hibernate']
     * emailconfirmation 0.4: dependsOn = [quartz:'0.3 > *', mail: '0.2 > *']
     *
     * ...and emailconfirmation is NOT loaded first.
     */
    @Test
    @SuppressWarnings('rawtypes')
    void testDependenciesWithDelayedLoadingWithVersionRangeStrings() {
        var gcl = new GroovyClassLoader()

        // These are defined in a specific order so that the one with the range dependencies
        // is the first in the list, and its dependencies load after
        first = gcl.parseClass('''class FirstGrailsPlugin {
def version = "0.4"
def dependsOn = [second:'0.3 > *', third:'0.2 > *']
}''')
        second = gcl.parseClass('''class SecondGrailsPlugin {
def version = "0.3"
def dependsOn = [:]
}''')
        third = gcl.parseClass('''class ThirdGrailsPlugin {
def version = "0.3-SNAPSHOT"
def loadAfter = ['core', 'hibernate']
}''')

        GrailsApplication app = new DefaultGrailsApplication(new Class[0], gcl)
        var parent = new GenericApplicationContext()
        parent.getDefaultListableBeanFactory().registerSingleton(GrailsApplication.APPLICATION_ID, app)

        // Set plugin filter on discovery before loading plugins
        var discovery = new DefaultPluginDiscovery([first, second, third] as Class[])
        discovery.setPluginFilter(new IncludingPluginFilter('dataSource', 'first', 'second', 'third'))
        discovery.init(new StandardEnvironment())
        parent.getDefaultListableBeanFactory().registerSingleton(PluginDiscovery.BEAN_NAME, discovery)
        var manager = new DefaultGrailsPluginManager(app, discovery)
        manager.setParentApplicationContext(parent)

        manager.loadPlugins()

        var plugins = manager.getAllPlugins()
        var pluginList = Arrays.asList(plugins)

        assertNotNull(manager.getGrailsPlugin('first'))
        assertNotNull(manager.getGrailsPlugin('second'))
        //dataSource depends on core
        assertNotNull(manager.getGrailsPlugin('core'))
        //third depends on i18n
        assertNotNull(manager.getGrailsPlugin('third'))

        assertEquals(5, pluginList.size(), 'Expected plugins not loaded. Expected ' + 5 + ' but got ' + pluginList)
    }

    @Test
    void testLoadingOrderGRAILS9426() {
        // GRAILS-9426
        var manager = loadPlugins('''class FirstGrailsPlugin {
def version = '1.0'
}''', '''class SecondGrailsPlugin {
def version = '1.0'
}''', '''import grails.util.GrailsUtil
class ThirdGrailsPlugin {
def version = '1.0'
}''', '''class FourthGrailsPlugin {
def version = '1.0'
def loadBefore = ['first', 'second']
}''')

        var plugins = manager.getAllPlugins()
        var pluginList = Arrays.asList(plugins)

        assertNotNull(manager.getGrailsPlugin('first'))
        assertNotNull(manager.getGrailsPlugin('second'))
        assertNotNull(manager.getGrailsPlugin('third'))
        assertNotNull(manager.getGrailsPlugin('fourth'))

        List<GrailsPlugin> expectedOrder = new ArrayList<GrailsPlugin>()
        expectedOrder.add(manager.getGrailsPlugin('fourth'))
        expectedOrder.add(manager.getGrailsPlugin('first'))
        expectedOrder.add(manager.getGrailsPlugin('second'))
        expectedOrder.add(manager.getGrailsPlugin('third'))

        assertEquals(expectedOrder, pluginList, 'Expected plugin order')

        assertEquals(4, pluginList.size(), 'Expected plugins not loaded. Expected ' + 4 + ' but got ' + pluginList)
    }

    DefaultGrailsPluginManager loadPlugins(String firstClassString, String secondClassString, String thirdClassString, String fourthClassString) {
        var gcl = new GroovyClassLoader()

        first = gcl.parseClass(firstClassString)
        second = gcl.parseClass(secondClassString)
        third = gcl.parseClass(thirdClassString)
        fourth = gcl.parseClass(fourthClassString)

        GrailsApplication app = new DefaultGrailsApplication(new Class[0], gcl)
        var parent = new GenericApplicationContext()
        parent.getDefaultListableBeanFactory().registerSingleton(GrailsApplication.APPLICATION_ID, app)

        // Set plugin filter on discovery before loading plugins
        var discovery = new DefaultPluginDiscovery([first, second, third, fourth] as Class[])
        discovery.setPluginFilter(new IncludingPluginFilter('first', 'second', 'third', 'fourth'))
        discovery.init(new StandardEnvironment())
        var manager = new DefaultGrailsPluginManager(app, discovery)
        manager.setParentApplicationContext(parent)

        manager.loadPlugins()
        return manager
    }

    @Test
    void testLoadingOrderLoadBeforeAndLoadAfter() {
        var manager = loadPlugins('''class FirstGrailsPlugin {
def version = '1.0'
def loadAfter = ['second', 'third']
}''', '''class SecondGrailsPlugin {
def version = '1.0'
}''', '''import grails.util.GrailsUtil
class ThirdGrailsPlugin {
def version = '1.0'
def loadBefore = ['fourth']
}''', '''class FourthGrailsPlugin {
def version = '1.0'
def loadBefore = ['first', 'second']
}''')

        var plugins = manager.getAllPlugins()
        var pluginList = Arrays.asList(plugins)

        List<GrailsPlugin> expectedOrder = new ArrayList<GrailsPlugin>()
        expectedOrder.add(manager.getGrailsPlugin('third'))
        expectedOrder.add(manager.getGrailsPlugin('fourth'))
        expectedOrder.add(manager.getGrailsPlugin('second'))
        expectedOrder.add(manager.getGrailsPlugin('first'))

        assertEquals(expectedOrder, pluginList, 'Expected plugin order')
    }
}
