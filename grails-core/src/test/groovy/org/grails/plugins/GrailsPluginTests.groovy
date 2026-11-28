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
import grails.util.Environment
import org.apache.grails.core.plugins.DefaultPluginDiscovery
import org.apache.grails.core.plugins.PluginClassFixtures
import org.apache.grails.core.plugins.PluginDiscovery
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.StandardEnvironment

import static org.junit.jupiter.api.Assertions.*

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GrailsPluginTests {

    @TempDir
    Path temp

    /** A plugin class whose descriptor on its class path records version 0.1, as a plugin jar's does. */
    private Class<?> versionedPlugin(String className, String annotation = '') {
        PluginClassFixtures.versionedPluginLoader(temp, [(className): '0.1']).parseClass(
                "@grails.plugins.metadata.GrailsPluginDescriptor${annotation} class ${className} extends grails.plugins.Plugin { }")
    }

    @Test
    void testPluginPath() {
        def plugin = new DefaultGrailsPlugin(versionedPlugin('TestOneGrailsPlugin'), new DefaultGrailsApplication())

        assertEquals "/plugins/test-one-0.1", plugin.pluginPath
    }

    @Test
    void testPluginPathLongName() {
        def plugin = new DefaultGrailsPlugin(versionedPlugin('TestOnetwoThreeFourfiveGrailsPlugin'), new DefaultGrailsApplication())

        assertEquals "/plugins/test-onetwo-three-fourfive-0.1", plugin.pluginPath
    }

    @Test
    void testPluginPathCamelCase() {
        def plugin = new DefaultGrailsPlugin(versionedPlugin('TestOneGrailsPlugin'), new DefaultGrailsApplication())

        assertEquals "/plugins/testOne-0.1", plugin.pluginPathCamelCase
    }

    @Test
    void testPluginPathCamelCaseLongName() {
        def plugin = new DefaultGrailsPlugin(versionedPlugin('TestOnetwoThreeFourfiveGrailsPlugin'), new DefaultGrailsApplication())

        assertEquals "/plugins/testOnetwoThreeFourfive-0.1", plugin.pluginPathCamelCase
    }

    @Test
    void testFileSystemName() {
        def plugin = new DefaultGrailsPlugin(versionedPlugin('TestOneGrailsPlugin'), new DefaultGrailsApplication())

        assertEquals "test-one-0.1", plugin.fileSystemName
    }

    @Test
    void testUnversionedPluginNamesAndPathsCarryNoVersion() {
        // a plugin compiled outside a build that sets the version has none
        Class<?> unversioned = new GroovyClassLoader(getClass().classLoader).parseClass(
                '@grails.plugins.metadata.GrailsPluginDescriptor class TestTwoGrailsPlugin extends grails.plugins.Plugin { }')
        def plugin = new DefaultGrailsPlugin(unversioned, new DefaultGrailsApplication())

        assertNull plugin.version
        assertEquals "/plugins/test-two", plugin.pluginPath
        assertEquals "/plugins/testTwo", plugin.pluginPathCamelCase
        assertEquals "test-two", plugin.fileSystemName
        assertEquals "testTwo", plugin.fullName
    }

    @Test
    void testSimpleEnvironmentEvaluation() {
        def test1 = versionedPlugin('TestGrailsPlugin', "(environmentsIncludes = ['dev'])")

        def plugin = new DefaultGrailsPlugin(test1, new DefaultGrailsApplication())

        assertTrue plugin.supportsEnvironment(Environment.DEVELOPMENT)
        assertFalse plugin.supportsEnvironment(Environment.PRODUCTION)
    }

    @Test
    void testListEnvironmentEvaluation() {
        def test1 = versionedPlugin('TestGrailsPlugin', "(environmentsIncludes = ['test', 'dev'])")

        def plugin = new DefaultGrailsPlugin(test1, new DefaultGrailsApplication())

        assertTrue plugin.supportsEnvironment(Environment.DEVELOPMENT)
        assertTrue plugin.supportsEnvironment(Environment.TEST)
        assertFalse plugin.supportsEnvironment(Environment.PRODUCTION)
    }

    @Test
    void testExcludedEnvironmentEvaluation() {
        def test1 = versionedPlugin('TestGrailsPlugin', "(environmentsExcludes = ['prod'])")

        def plugin = new DefaultGrailsPlugin(test1, new DefaultGrailsApplication())

        assertTrue plugin.supportsEnvironment(Environment.DEVELOPMENT)
        assertTrue plugin.supportsEnvironment(Environment.TEST)
        assertFalse plugin.supportsEnvironment(Environment.PRODUCTION)
    }

    @Test
    void testEnvironmentsAndLoadIntoPluginManager() {
        def test1 = versionedPlugin('TestGrailsPlugin', "(environmentsIncludes = ['test', 'dev'])")

        DefaultGrailsApplication application = new DefaultGrailsApplication()

        // Create and set up application context
        def appCtx = new GenericApplicationContext()
        application.mainContext = appCtx

        // Create discovery bean configured for unit tests
        PluginDiscovery discovery = new DefaultPluginDiscovery(new Class<?>[]{test1})

        // simulate the call in GrailsEnvironmentPostProcessor to populate the metadata
        discovery.init(new StandardEnvironment())

        def pluginManager = new DefaultGrailsPluginManager(application, discovery)
        pluginManager.loadPlugins()
        assertNotNull pluginManager.getGrailsPlugin("test")

        String originalEnv = System.getProperty(Environment.KEY)
        try {
            System.setProperty(Environment.KEY, Environment.PRODUCTION.name)

            // Create new application and context for production environment test
            application = new DefaultGrailsApplication()
            appCtx = new GenericApplicationContext()
            application.mainContext = appCtx

            // Create new discovery bean for production environment test
            discovery = new DefaultPluginDiscovery(new Class<?>[]{test1})
            discovery.loadPluginsFromClasspath = false
            // simulate the call in GrailsEnvironmentPostProcessor to populate the metadata
            discovery.init(new StandardEnvironment())

            pluginManager = new DefaultGrailsPluginManager(application, discovery)
            pluginManager.loadPlugins()
            assertNull pluginManager.getGrailsPlugin("test")
        } finally {
            if (originalEnv != null) {
                System.setProperty(Environment.KEY, originalEnv)
            } else {
                System.clearProperty(Environment.KEY)
            }
        }
    }
}
