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
package org.apache.grails.core.plugins

import spock.lang.Specification
import spock.lang.Unroll

import org.grails.plugins.DefaultGrailsPlugin

/**
 * Test suite for PluginUtils utility methods
 */
class PluginUtilsSpec extends Specification {

    @Unroll
    def "isPluginVersionCompatible checks that plugin with grailsVersion=#pluginGrailsVersion is compatible with grails #grailsVersion"() {
        expect:
        PluginUtils.isPluginVersionCompatible(
            '1.0.0',  // pluginVersion
            pluginGrailsVersion,  // pluginSupportedVersion
            grailsVersion,  // grailsVersion
            'test-plugin'  // pluginDescription
        ) == isCompatible

        where:
        grailsVersion | pluginGrailsVersion        || isCompatible
        '1.0'         | '3.3.1 > *'                || false
        '2.5'         | '3.0.1'                    || false
        '3.0.0'       | '3.3.10 > *'               || false
        '3.3.10'      | '4.0.0 > *'                || false
        '4.0.1'       | '3.0.0.BUILD-SNAPSHOT > *' || true
        '4.0.1'       | '4.0.1'                    || true
        '4.0.1'       | '3.0.1'                    || false
        '4.0.1'       | '3.3.1 > *'                || true
        '4.0.1'       | '3.3.10 > *'               || true
    }

    def "isPluginVersionCompatible handles null grailsVersion"() {
        when:
        def compatible = PluginUtils.isPluginVersionCompatible(
            '1.0.0',
            '3.3.10 > *',
            null,  // grailsVersion is null
            'test-plugin'
        )

        then:
        compatible
    }

    def "isPluginVersionCompatible handles null pluginSupportedVersion"() {
        when:
        def compatible = PluginUtils.isPluginVersionCompatible(
            '1.0.0',
            null,  // pluginSupportedVersion is null
            '4.0.1',
            'test-plugin'
        )

        then:
        compatible
    }

    def "isPluginVersionCompatible handles @ in pluginSupportedVersion"() {
        when:
        def compatible = PluginUtils.isPluginVersionCompatible(
            '1.0.0',
            '4.0.0@', // contains @
            '3.0.0',
            'test-plugin'
        )

        then:
        compatible
    }

    // Tests for extractPluginMetadata method
    
    def "extractPluginMetadata extracts loadAfter, loadBefore, and dependsOn"() {
        when:
        def metadata = PluginUtils.extractPluginMetadata(PluginWithAllOrderingGrailsPlugin)

        then:
        metadata
        metadata.name == 'pluginWithAllOrdering'
        metadata.loadAfterNames == ['alpha', 'beta'] as String[]
        metadata.loadBeforeNames == ['gamma'] as String[]
        metadata.dependsOnNames == ['delta'] as String[]
    }

    def "extractPluginMetadata returns empty arrays for plugin with no ordering declarations"() {
        when:
        def metadata = PluginUtils.extractPluginMetadata(UtilsTestSimpleGrailsPlugin)

        then:
        metadata
        metadata.name == 'utilsTestSimple'
        metadata.loadAfterNames.length == 0
        metadata.loadBeforeNames.length == 0
        metadata.dependsOnNames.length == 0
    }

    def "extractPluginMetadata returns null for class not ending in GrailsPlugin"() {
        expect:
        PluginUtils.extractPluginMetadata(String) == null
    }

    def "extractPluginMetadata returns null for null input"() {
        expect:
        PluginUtils.extractPluginMetadata(null) == null
    }

    def "extractPluginMetadata handles plugin that throws on instantiation"() {
        when:
        PluginUtils.extractPluginMetadata(FailingConstructorUtilsGrailsPlugin)

        then: 'throws IllegalStateException when plugin cannot be instantiated'
        thrown(IllegalStateException)
    }

    def "extractPluginMetadata extracts multiple dependsOn names"() {
        when:
        def metadata = PluginUtils.extractPluginMetadata(PluginWithMultipleDepsGrailsPlugin)

        then:
        metadata
        metadata.name == 'pluginWithMultipleDeps'
        metadata.dependsOnNames as Set == ['core', 'i18n'] as Set
    }

    def "readPluginConfiguration returns null when no plugin.yml or plugin.groovy exists"() {
        when:
        def resource = PluginUtils.readPluginConfiguration(UtilsTestSimpleGrailsPlugin)

        then: 'no config file exists for this test fixture class'
        !resource
    }

    def "getConfigurationResource returns resource that does not exist for non-existent path"() {
        when: 'a resource is requested for a non-existent path'
        def resource = PluginUtils.getConfigurationResource(
                UtilsTestSimpleGrailsPlugin,
                '/nonexistent.yml'
        )

        then: 'resource is returned'
        resource

        and: 'resource does not exist'
        !resource.exists()
    }

    // Tests for additional public methods

    @Unroll
    def "normalizePluginName converts name #input"(String input, String output) {
        expect:
        PluginUtils.normalizePluginName(input) == output

        where:
        input || output
        'my-plugin-name' || 'myPluginName'
        'simple'         || 'simple'
        'alreadyCamel'   || 'alreadyCamel'
    }

    @Unroll
    def "getLogicalPluginNameFromClassName extracts plugin name from class name #input"(String input, String output) {
        expect:
        PluginUtils.getLogicalPluginNameFromClassName(input) == output

        where:
        input                  || output
        'MyPluginGrailsPlugin' || 'myPlugin'
        'CoreGrailsPlugin'     || 'core'
        'SimpleGrailsPlugin'   || 'simple'
    }

    @Unroll
    def "isGrailsPluginClassNamedCorrectly validates plugin naming correctly for #clazz"(Class<?> clazz, boolean isValidPluginClassName) {
        expect:
        PluginUtils.isGrailsPluginClassNamedCorrectly(clazz) == isValidPluginClassName

        where:
        clazz                             || isValidPluginClassName
        PluginWithAllOrderingGrailsPlugin || true
        UtilsTestSimpleGrailsPlugin       || true
        String                            || false
        null                              || false

    }

    @Unroll
    def "isGrailsPluginLoadable returns #isLoadable for #clazz"(Class<?> clazz, boolean isLoadable) {
        expect:
        PluginUtils.isGrailsPluginLoadable(clazz) == isLoadable

        where:
        clazz                             || isLoadable
        PluginWithAllOrderingGrailsPlugin || true
        DefaultGrailsPlugin               || false // DefaultGrailsPlugin is not loadable since it's not a plugin class
        String                            || true // Method returns true for String since it's not abstract and not DefaultGrailsPlugin.class
        null                              || false
    }

    def "supportsValueInIncludeExcludeMap returns true for empty map"() {
        expect:
        PluginUtils.supportsValueInIncludeExcludeMap([:], 'anything')
    }

    def "supportsValueInIncludeExcludeMap checks includes/excludes"() {
        when:
        def mapWithIncludes = ['includes': ['dev', 'test'] as Set]

        then:
        with(PluginUtils) {
            supportsValueInIncludeExcludeMap(mapWithIncludes, 'dev')
            !supportsValueInIncludeExcludeMap(mapWithIncludes, 'prod')
        }
    }

    def "supportsValueInIncludeExcludeMap checks excludes"() {
        when:
        def mapWithExcludes = ['excludes': ['prod'] as Set]

        then:
        with(PluginUtils) {
            supportsValueInIncludeExcludeMap(mapWithExcludes, 'dev')
            !supportsValueInIncludeExcludeMap(mapWithExcludes, 'prod')
        }
    }

    def "scanPluginDescriptors returns empty list when no descriptors found"() {
        when:
        def classNames = PluginUtils.scanPluginDescriptors(
                new URLClassLoader([] as URL[], (ClassLoader) null)
        )

        then:
        classNames.empty
    }

    def "scanPluginDescriptorResources returns empty list when no descriptors found"() {
        when:
        def descriptors = PluginUtils.scanPluginDescriptorResources(
                new URLClassLoader([] as URL[], (ClassLoader) null)
        )

        then:
        descriptors.empty
    }

    @Unroll
    def "getLogicalPluginName derives plugin name #expectedName from plugin class #clazz"(Class<?> clazz, String expectedName) {
        expect:
        PluginUtils.getLogicalPluginName(clazz) == expectedName

        where:
        clazz                              || expectedName
        PluginWithAllOrderingGrailsPlugin  || 'pluginWithAllOrdering'
        UtilsTestSimpleGrailsPlugin        || 'utilsTestSimple'
        PluginWithMultipleDepsGrailsPlugin || 'pluginWithMultipleDeps'
    }

    def "evaluateIncludeExcludeProperty parses include/exclude map structure"() {
        given:
        def plugin = new GroovyObject() {
            def environments = [
                'includes': ['dev', 'test'],
                'excludes': ['prod']
            ]
        }

        when:
        def result = PluginUtils.evaluateIncludeExcludeProperty(
            plugin,
            'environments',
            { obj -> obj }  // identity converter
        )

        then:
        result.containsKey('includes')
        result.containsKey('excludes')
        result['includes'] == ['dev', 'test'] as Set
        result['excludes'] == ['prod'] as Set
    }

    def "evaluateIncludeExcludeProperty handles string values"() {
        given:
        def plugin = new GroovyObject() {
            def scope = 'development'
        }

        when:
        def result = PluginUtils.evaluateIncludeExcludeProperty(
            plugin,
            'scope',
            { obj -> obj }  // identity converter
        )

        then:
        result.containsKey('includes')
        result['includes'] == ['development'] as Set
    }

    def "evaluateIncludeExcludeProperty handles list values"() {
        given:
        def plugin = new GroovyObject() {
            def scopes = ['dev', 'test']
        }

        when:
        def result = PluginUtils.evaluateIncludeExcludeProperty(
            plugin,
            'scopes',
            { obj -> obj }  // identity converter
        )

        then:
        result.containsKey('includes')
        result['includes'] == ['dev', 'test'] as Set
    }
}

// Test fixture plugin classes for GrailsPluginUtilsSpec

class UtilsTestSimpleGrailsPlugin {
    def version = '1.0'
}

class PluginWithAllOrderingGrailsPlugin {
    def version = '1.0'
    def loadAfter = ['alpha', 'beta']
    def loadBefore = ['gamma']
    def dependsOn = [delta: '1.0']
}

class FailingConstructorUtilsGrailsPlugin {
    def version = '1.0'

    FailingConstructorUtilsGrailsPlugin() {
        throw new RuntimeException('Intentional failure for testing')
    }
}

class PluginWithMultipleDepsGrailsPlugin {
    def version = '1.0'
    def dependsOn = [core: '1.0', i18n: '1.0']
}
