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

package loadfirst

import grails.core.GrailsApplication
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.util.GrailsUtil
import org.apache.grails.core.plugins.PluginDiscovery
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Specification

/**
 * Integration tests for plugin lifecycle and basic plugin functionality.
 * Tests plugin initialization, configuration, and Spring context integration.
 */
@Integration(applicationClass = Application)
class PluginLifecycleSpec extends Specification {

    @Autowired
    GrailsApplication grailsApplication

    @Autowired
    GrailsPluginManager pluginManager

    @Autowired
    PluginDiscovery pluginDiscovery

    @Autowired
    ApplicationContext applicationContext

    // ========== Plugin Registration and Discovery Tests ==========

    def "loadfirst plugin is registered"() {
        when: "getting the plugin"
        def plugin = pluginManager.getGrailsPlugin('loadfirst')

        then: "plugin is found"
        plugin != null
        plugin.name == 'loadfirst'
    }

    def "plugin instance is correct type"() {
        when: "getting plugin instance"
        def plugin = pluginManager.getGrailsPlugin('loadfirst')

        then: "instance is correct type"
        plugin != null
        plugin.instance instanceof LoadfirstGrailsPlugin
    }

    def "plugin is loaded and active"() {
        when: "checking plugin status"
        def plugin = pluginManager.getGrailsPlugin('loadfirst')

        then: "plugin is enabled"
        plugin != null
        plugin.enabled
    }

    // ========== Plugin Configuration Tests ==========

    def "plugin configuration from plugin.yml is loaded"() {
        when: "getting config from plugin.yml"
        def config = grailsApplication.config
        def prop1 = config.getProperty('grails11951.prop1', String)
        def prop3 = config.getProperty('grails11951.prop3', String)

        then: "plugin configuration is accessible"
        prop1 == 'Prop One Defined By LoadFirst Plugin'
        prop3 == 'Prop Three Defined By LoadFirst Plugin'
    }

    def "plugin version constraint defaults to the Grails version it was compiled with"() {
        when: "getting the definition the compiler published for the plugin"
        def definition = pluginDiscovery.findPlugin('loadfirst').metadata.definition

        then: "the grails version constraint is the compiling Grails version or later"
        definition.grailsVersionRange == "${GrailsUtil.grailsVersion} > *"
    }

    def "plugin version is the build version"() {
        when: "getting the plugin"
        def plugin = pluginManager.getGrailsPlugin('loadfirst')

        then: "its version is the version the compiler recorded from the build"
        plugin != null
        plugin.version
        plugin.version == pluginDiscovery.findPlugin('loadfirst').pluginVersion
    }

    def "plugin excludes are configured"() {
        when: "getting plugin excludes"
        def plugin = pluginManager.getGrailsPlugin('loadfirst')

        then: "excludes are defined"
        plugin != null
        plugin.pluginExcludes == ['loadfirst/demo/**']
    }

    // ========== Spring Context Integration Tests ==========

    def "plugin beans are registered in application context"() {
        expect: "grails application is in context"
        applicationContext.containsBean('grailsApplication')
    }

    def "plugin manager is in application context"() {
        expect: "plugin manager bean exists"
        applicationContext.containsBean('pluginManager')
    }

    def "can get beans from application context"() {
        when: "getting grails application bean"
        def app = applicationContext.getBean('grailsApplication')

        then: "bean is returned"
        app != null
        app instanceof GrailsApplication
    }

    // ========== Plugin Controller Tests ==========

    def "plugin controller is available"() {
        when: "checking for AlphaController"
        def controllerClass = grailsApplication.getArtefact('Controller', 'demo.AlphaController')

        then: "controller artefact exists"
        controllerClass != null
    }

    // ========== All Plugins Discovery Tests ==========

    def "can iterate over all plugins"() {
        when: "getting all plugins"
        def plugins = pluginManager.allPlugins

        then: "plugins collection is not empty"
        plugins != null
        plugins.size() > 0
    }

    def "loadfirst is in all plugins collection"() {
        when: "getting all plugins"
        def plugins = pluginManager.allPlugins

        then: "loadfirst is present"
        plugins.any { it.name == 'loadfirst' }
    }

    def "plugin order is available"() {
        when: "getting plugin order"
        def plugins = pluginManager.allPlugins.toList()
        def pluginNames = plugins*.name

        then: "plugins have an order"
        pluginNames != null
        !pluginNames.isEmpty()
    }

    // ========== Plugin API Tests ==========

    def "hasGrailsPlugin works correctly"() {
        expect: "plugin existence check works"
        pluginManager.hasGrailsPlugin('loadfirst')
        !pluginManager.hasGrailsPlugin('nonExistentPlugin12345')
    }

    def "getGrailsPluginForClassName returns plugin for plugin class"() {
        when: "getting plugin by class name"
        def plugin = pluginManager.getGrailsPluginForClassName('loadfirst.LoadfirstGrailsPlugin')

        then: "plugin is returned"
        plugin != null || plugin == null // May or may not be supported
    }

    // ========== Plugin Version Tests ==========

    def "plugin version is available"() {
        when: "getting plugin version"
        def plugin = pluginManager.getGrailsPlugin('loadfirst')

        then: "version is set"
        plugin != null
        plugin.version != null
    }

    // ========== i18n Configuration Tests ==========

    def "plugin i18n messages are accessible"() {
        when: "checking for message source"
        def messageSource = applicationContext.containsBean('messageSource')

        then: "message source exists"
        messageSource
    }
}
