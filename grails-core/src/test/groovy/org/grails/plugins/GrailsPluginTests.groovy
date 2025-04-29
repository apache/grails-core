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
import grails.plugins.DefaultGrailsPluginManager
import grails.util.Environment
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GrailsPluginTests {

    @Test
    void testPluginPath() {

        def gcl = new GroovyClassLoader()
        def test1 = gcl.parseClass('''
class TestOneGrailsPlugin {
    def version = 0.1
    def scopes = 'test'
}
''')

        DefaultGrailsApplication application = new DefaultGrailsApplication()
        def plugin = new DefaultGrailsPlugin(test1, application)

        assertEquals "/plugins/test-one-0.1", plugin.pluginPath
    }

    @Test
    void testPluginPathLongName() {

        def gcl = new GroovyClassLoader()
        def test1 = gcl.parseClass('''
class TestOnetwoThreeFourfiveGrailsPlugin {
    def version = 0.1
    def scopes = 'test'
}
''')

        DefaultGrailsApplication application = new DefaultGrailsApplication()
        def plugin = new DefaultGrailsPlugin(test1, application)

        assertEquals "/plugins/test-onetwo-three-fourfive-0.1", plugin.pluginPath
    }

    @Test
    void testPluginPathCamelCase() {

        def gcl = new GroovyClassLoader()
        def test1 = gcl.parseClass('''
class TestOneGrailsPlugin {
    def version = 0.1
    def scopes = 'test'
}
''')

        DefaultGrailsApplication application = new DefaultGrailsApplication()
        def plugin = new DefaultGrailsPlugin(test1, application)

        assertEquals "/plugins/testOne-0.1", plugin.pluginPathCamelCase
    }

    @Test
    void testPluginPathCamelCaseLongName() {

        def gcl = new GroovyClassLoader()
        def test1 = gcl.parseClass('''
class TestOnetwoThreeFourfiveGrailsPlugin {
    def version = 0.1
    def scopes = 'test'
}
''')

        DefaultGrailsApplication application = new DefaultGrailsApplication()
        def plugin = new DefaultGrailsPlugin(test1, application)

        assertEquals "/plugins/testOnetwoThreeFourfive-0.1", plugin.pluginPathCamelCase
    }

    @Test
    void testFileSystemName() {

        def gcl = new GroovyClassLoader()
        def test1 = gcl.parseClass('''
class TestOneGrailsPlugin {
    def version = 0.1
    def scopes = 'test'
}
''')

        DefaultGrailsApplication application = new DefaultGrailsApplication()
        def plugin = new DefaultGrailsPlugin(test1, application)

        assertEquals "test-one-0.1", plugin.fileSystemName
    }

    @Test
    void testSimpleEnvironmentEvaluation() {
        def gcl = new GroovyClassLoader()
        def test1 = gcl.parseClass('''
class TestGrailsPlugin {
    def version = 0.1
    def environments = 'dev'
}
''')

        DefaultGrailsApplication application = new DefaultGrailsApplication()
        def plugin = new DefaultGrailsPlugin(test1, application)

        assertTrue plugin.supportsEnvironment(Environment.DEVELOPMENT)
        assertFalse plugin.supportsEnvironment(Environment.PRODUCTION)
    }

    @Test
    void testListEnvironmentEvaluation() {
        def gcl = new GroovyClassLoader()
        def test1 = gcl.parseClass('''
class TestGrailsPlugin {
    def version = 0.1
    def environments = ['test','dev']
}
''')

        DefaultGrailsApplication application = new DefaultGrailsApplication()
        def plugin = new DefaultGrailsPlugin(test1, application)

        assertTrue plugin.supportsEnvironment(Environment.DEVELOPMENT)
        assertTrue plugin.supportsEnvironment(Environment.TEST)
        assertFalse plugin.supportsEnvironment(Environment.PRODUCTION)
    }

    @Test
    void testEnvironmentsAndLoadIntoPluginManager() {
        def gcl = new GroovyClassLoader()
        def test1 = gcl.parseClass('''
class TestGrailsPlugin {
    def version = 0.1
    def environments = ['test','dev']
}
''')

        DefaultGrailsApplication application = new DefaultGrailsApplication()
        def pluginManager = new DefaultGrailsPluginManager([test1] as Class[], application)

        pluginManager.loadPlugins()
        assertNotNull pluginManager.getGrailsPlugin("test")

        try {
            System.setProperty(Environment.KEY, Environment.PRODUCTION.getName())

            pluginManager = new DefaultGrailsPluginManager([test1] as Class[], application)
            pluginManager.loadPlugins()
            assertNull pluginManager.getGrailsPlugin("test")
        } finally {
            System.setProperty(Environment.KEY, Environment.TEST.getName())
        }
    }
}
