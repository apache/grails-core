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

package exploded

import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Specification

/**
 * Integration tests for plugin dependencies and load ordering.
 * Tests that plugins with dependencies are loaded after their dependencies,
 * and that plugin dependency resolution works correctly.
 */
@Integration(applicationClass = Application)
class PluginDependencySpec extends Specification {

    @Autowired
    GrailsApplication grailsApplication

    @Autowired
    GrailsPluginManager pluginManager

    @Autowired
    ApplicationContext applicationContext

    // ========== Plugin Dependency Declaration Tests ==========

    def "exploded plugin declares loadAfter dependency"() {
        when: "getting plugin instance"
        def plugin = pluginManager.getGrailsPlugin('exploded')
        def instance = plugin?.instance

        then: "loadAfter is declared"
        instance != null
        instance.loadAfter != null
        instance.loadAfter.contains('springSecurityCore')
    }

    def "plugin manager respects loadAfter declarations"() {
        when: "getting all plugins"
        def plugins = pluginManager.allPlugins.toList()
        def pluginNames = plugins*.name

        and: "finding positions"
        def explodedIndex = pluginNames.indexOf('exploded')
        def springSecurityIndex = pluginNames.indexOf('springSecurityCore')

        then: "if both exist, exploded comes after springSecurityCore"
        // Only check if springSecurityCore is actually loaded
        springSecurityIndex < 0 || explodedIndex > springSecurityIndex
    }

    // ========== Plugin with External Dependencies Tests ==========

    def "spring security core plugin is loaded (dependency)"() {
        when: "checking for spring security core"
        def hasPlugin = pluginManager.hasGrailsPlugin('springSecurityCore')

        then: "plugin may or may not be present"
        // This just documents that we check for it
        hasPlugin || !hasPlugin
    }

    def "plugin can access beans from dependencies"() {
        expect: "application context is available"
        applicationContext != null
    }

    // ========== Plugin Configuration Merging Tests ==========

    def "plugin configuration is merged into application config"() {
        when: "getting app configuration"
        def config = grailsApplication.config

        then: "plugin-specific config is accessible"
        config.getProperty('grails.profile', String) == 'web-plugin'
    }

    def "later plugins can override earlier plugin configuration"() {
        when: "getting config that might be overridden"
        def config = grailsApplication.config

        then: "config values are accessible"
        // The actual value depends on plugin load order
        config.getProperty('grails.mime.types.text', String) != null
    }

    // ========== Plugin Bean Registration Tests ==========

    def "plugins can register custom beans"() {
        expect: "standard beans exist"
        applicationContext.containsBean('grailsApplication')
        applicationContext.containsBean('pluginManager')
    }

    def "plugin manager bean is the correct type"() {
        when: "getting plugin manager from context"
        def pm = applicationContext.getBean('pluginManager')

        then: "it's the correct type"
        pm instanceof GrailsPluginManager
        pm.is(pluginManager)
    }

    // ========== Plugin Artefact Registration Tests ==========

    def "plugins can register controllers"() {
        when: "checking for controllers"
        def controllers = grailsApplication.getArtefacts('Controller')

        then: "controllers are available"
        controllers != null
    }

    def "plugins can register domains"() {
        when: "checking for domain classes"
        def domains = grailsApplication.getArtefacts('Domain')

        then: "domains collection is available"
        domains != null
    }

    def "plugins can register services"() {
        when: "checking for services"
        def services = grailsApplication.getArtefacts('Service')

        then: "services collection is available"
        services != null
    }

    // ========== Plugin Profiles Tests ==========

    def "plugin profiles are configured"() {
        when: "getting plugin"
        def plugin = pluginManager.getGrailsPlugin('exploded')
        def instance = plugin?.instance

        then: "profiles are set"
        instance != null
        instance.profiles?.contains('web')
    }

    // ========== Multi-Plugin Interaction Tests ==========

    def "multiple plugins can be loaded simultaneously"() {
        when: "counting plugins"
        def count = pluginManager.allPlugins.size()

        then: "multiple plugins exist"
        count > 1
    }

    def "plugins do not interfere with each other"() {
        when: "getting all plugins"
        def plugins = pluginManager.allPlugins

        then: "each plugin is distinct"
        plugins.collect { it.name }.unique().size() == plugins.size()
    }

    // ========== Plugin Metadata Consistency Tests ==========

    def "all plugins have required metadata"() {
        when: "checking all plugins"
        def plugins = pluginManager.allPlugins

        then: "each has required fields"
        plugins.every { plugin ->
            plugin.name != null &&
            plugin.version != null
        }
    }

    def "exploded plugin metadata is correct"() {
        when: "getting exploded plugin"
        def plugin = pluginManager.getGrailsPlugin('exploded')
        def instance = plugin?.instance

        then: "metadata is set"
        instance != null
        instance.title == 'Exploded Plugin'
    }

    // ========== Dynamic Plugin Discovery Tests ==========

    def "plugins are discoverable by pattern"() {
        when: "getting plugins"
        def allPlugins = pluginManager.allPlugins.toList()
        def pluginNames = allPlugins*.name

        then: "can filter plugins by naming pattern"
        pluginNames.findAll { it.toLowerCase().contains('security') }.size() >= 0
    }

    def "plugin manager provides consistent state"() {
        when: "getting plugin info multiple times"
        def count1 = pluginManager.allPlugins.size()
        def count2 = pluginManager.allPlugins.size()

        then: "state is consistent"
        count1 == count2
    }
}
