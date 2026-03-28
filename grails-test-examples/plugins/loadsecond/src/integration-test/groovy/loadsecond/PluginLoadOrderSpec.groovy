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

package loadsecond

import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Integration tests for plugin loading order.
 * Tests that plugins are loaded in correct order based on
 * loadAfter and loadBefore declarations.
 */
@Integration(applicationClass = Application)
class PluginLoadOrderSpec extends Specification {

    @Autowired
    GrailsApplication grailsApplication

    @Autowired
    GrailsPluginManager pluginManager

    // ========== Plugin Manager Tests ==========

    def "plugin manager is available"() {
        expect: "plugin manager is injected"
        pluginManager != null
    }

    def "grails application is available"() {
        expect: "grails application is injected"
        grailsApplication != null
    }

    // ========== Plugin Registration Tests ==========

    def "loadsecond plugin is loaded"() {
        when: "getting loadsecond plugin"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')

        then: "plugin exists"
        plugin != null
        plugin.name == 'loadsecond'
    }

    def "plugin has correct metadata"() {
        when: "getting plugin"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')

        then: "metadata is correct"
        plugin != null
        plugin.instance instanceof LoadsecondGrailsPlugin
    }

    def "loadAfter is declared correctly"() {
        when: "getting plugin instance"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')
        def instance = plugin?.instance

        then: "loadAfter contains loadfirst"
        instance != null
        instance.loadAfter?.contains('loadfirst')
    }

    // ========== All Plugins Tests ==========

    def "can list all loaded plugins"() {
        when: "getting all plugins"
        def allPlugins = pluginManager.allPlugins

        then: "plugins are loaded"
        allPlugins != null
        allPlugins.size() > 0

        and: "loadsecond is among them"
        allPlugins.any { it.name == 'loadsecond' }
    }

    def "plugin count is greater than zero"() {
        expect: "at least one plugin is loaded"
        pluginManager.allPlugins.size() > 0
    }

    // ========== Plugin Properties Tests ==========

    def "plugin version is set"() {
        when: "getting plugin"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')

        then: "version is available"
        plugin != null
        plugin.version != null
    }

    def "plugin grails version constraint is set"() {
        when: "getting plugin instance"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')
        def instance = plugin?.instance

        then: "grails version constraint exists"
        instance != null
        instance.grailsVersion != null
    }

    def "plugin profiles are configured"() {
        when: "getting plugin instance"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')
        def instance = plugin?.instance

        then: "profiles are set"
        instance != null
        instance.profiles?.contains('web')
    }

    // ========== Plugin State Tests ==========

    def "plugin is enabled"() {
        when: "getting plugin"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')

        then: "plugin is enabled"
        plugin != null
        plugin.enabled
    }

    // ========== Plugin Configuration Tests ==========

    def "plugin configuration is loaded from plugin.yml"() {
        when: "getting config value defined in plugin.yml"
        def config = grailsApplication.config
        def prop2 = config.getProperty('grails11951.prop2', String)

        then: "plugin config is accessible"
        // LoadSecond defines prop2, which should override LoadFirst's prop2
        // if both plugins are loaded in correct order
        prop2 != null
    }

    // ========== Plugin Exclusions Tests ==========

    def "plugin excludes are configured"() {
        when: "getting plugin instance"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')
        def instance = plugin?.instance

        then: "exclusions are defined"
        instance != null
        instance.pluginExcludes != null
        instance.pluginExcludes.contains('grails-app/views/error.gsp')
    }

    // ========== Plugin API Tests ==========

    def "can check if plugin is available"() {
        expect: "hasGrailsPlugin works"
        pluginManager.hasGrailsPlugin('loadsecond')
    }

    def "hasGrailsPlugin returns false for non-existent plugin"() {
        expect: "non-existent plugin returns false"
        !pluginManager.hasGrailsPlugin('nonExistentPlugin')
    }

    def "getGrailsPlugin returns null for non-existent plugin"() {
        expect: "null is returned"
        pluginManager.getGrailsPlugin('nonExistentPlugin') == null
    }

    // ========== Plugin Title and Description Tests ==========

    def "plugin title is set"() {
        when: "getting plugin instance"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')
        def instance = plugin?.instance

        then: "title is available"
        instance != null
        instance.title == 'Loadsecond'
    }

    def "plugin description is set"() {
        when: "getting plugin instance"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')
        def instance = plugin?.instance

        then: "description is available"
        instance != null
        instance.description != null
        instance.description.contains('loadfirst')
    }

    // ========== Plugin Documentation Tests ==========

    def "plugin documentation URL is set"() {
        when: "getting plugin instance"
        def plugin = pluginManager.getGrailsPlugin('loadsecond')
        def instance = plugin?.instance

        then: "documentation URL is available"
        instance != null
        instance.documentation != null
    }
}
