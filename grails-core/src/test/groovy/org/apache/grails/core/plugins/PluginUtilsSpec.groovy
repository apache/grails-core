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

import java.nio.file.Path

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.grails.plugins.DefaultGrailsPlugin

/**
 * Test suite for PluginUtils utility methods
 */
class PluginUtilsSpec extends Specification {

    @TempDir
    Path temp

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
        '7.0.0-M2'    | '7.0.0-M1 > *'             || true
        '7.0.0-M1'    | '7.0.0-M2 > *'             || false
        '7.0.0-RC1'   | '7.0.0-M1 > *'             || true
        '7.0.0-M1'    | '7.0.0-RC1 > *'            || false
        '7.0.0'       | '7.0.0-RC1 > *'            || true
        '7.0.0-RC1'   | '7.0.0 > *'                || false
        '7.0.0-SNAPSHOT' | '7.0.0-SNAPSHOT > *'    || true
        '7.0.5-M1'    | '7.0.3 > *'                || true
        '7.0.0-M1'    | '7.0.0-M1'                 || true
        '7.0.0-M2'    | '7.0.0-M1'                 || false
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

    private static Class<?> parsePlugin(String source) {
        new GroovyClassLoader(PluginUtilsSpec.classLoader).parseClass(source)
    }

    def "createPluginInfo extracts after, before, and dependsOn from the annotation"() {
        given:
        def pluginClass = parsePlugin('''
            @grails.plugins.metadata.GrailsPluginDescriptor(
                    after = ['alpha', 'beta'], before = ['gamma'], dependsOn = ['delta:1.0'])
            class PluginWithAllOrderingGrailsPlugin extends grails.plugins.Plugin { }
        ''')

        when:
        def metadata = PluginUtils.createPluginInfo(pluginClass)

        then:
        metadata.name == 'pluginWithAllOrdering'
        metadata.pluginClass == pluginClass
        metadata.afterNames == ['alpha', 'beta'] as String[]
        metadata.beforeNames == ['gamma'] as String[]
        metadata.dependsOnNames == ['delta'] as String[]
        metadata.getDependentVersion('delta') == '1.0'
    }

    def "createPluginInfo returns empty arrays for plugin with no ordering declarations"() {
        when:
        def metadata = PluginUtils.createPluginInfo(parsePlugin('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class UtilsTestSimpleGrailsPlugin extends grails.plugins.Plugin { }
        '''))

        then:
        metadata.name == 'utilsTestSimple'
        metadata.afterNames.length == 0
        metadata.beforeNames.length == 0
        metadata.dependsOnNames.length == 0
        metadata.environments.isEmpty()
    }

    def "createPluginInfo reads the environments the annotation includes and excludes"() {
        when:
        def metadata = PluginUtils.createPluginInfo(parsePlugin('''
            @grails.plugins.metadata.GrailsPluginDescriptor(environmentsIncludes = ['dev', 'test'], environmentsExcludes = ['prod'])
            class EnvironmentsGrailsPlugin extends grails.plugins.Plugin { }
        '''))

        then: 'short environment names resolve to the environments they stand for'
        metadata.environments[DefaultGrailsPlugin.INCLUDES] == ['development', 'test'] as Set
        metadata.environments[DefaultGrailsPlugin.EXCLUDES] == ['production'] as Set
    }

    @Unroll
    def "createPluginInfo rejects #description"() {
        when:
        PluginUtils.createPluginInfo(pluginClass)

        then:
        def e = thrown(grails.plugins.exceptions.PluginException)
        e.message.contains('is not a Grails plugin')

        where:
        description                                    | pluginClass
        'a class that is not a Plugin'                 | String
        'a *GrailsPlugin class that is not a Plugin'   | parsePlugin('class LegacyGrailsPlugin { def version = "1.0" }')
        'a Plugin without @GrailsPluginDescriptor'     | parsePlugin('class BareGrailsPlugin extends grails.plugins.Plugin { }')
    }

    def "createPluginInfo does not create the plugin"() {
        when:
        def metadata = PluginUtils.createPluginInfo(parsePlugin('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class FailingConstructorUtilsGrailsPlugin extends grails.plugins.Plugin {
                FailingConstructorUtilsGrailsPlugin() {
                    throw new RuntimeException('Intentional failure for testing')
                }
            }
        '''))

        then: 'the constructor is never called'
        metadata.name == 'failingConstructorUtils'
    }

    def "createPluginInfo extracts multiple dependsOn names"() {
        when:
        def metadata = PluginUtils.createPluginInfo(parsePlugin('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['core:1.0', 'i18n:1.0'])
            class PluginWithMultipleDepsGrailsPlugin extends grails.plugins.Plugin { }
        '''))

        then:
        metadata.name == 'pluginWithMultipleDeps'
        metadata.dependsOnNames as Set == ['core', 'i18n'] as Set
    }

    def "readPluginConfiguration returns null when no plugin.yml or plugin.groovy exists"() {
        expect: 'no config file exists for this plugin name'
        PluginUtils.readPluginConfiguration('utilsTestSimple', PluginUtilsSpec.classLoader) == null
    }

    def "readPluginConfiguration returns null for an invalid plugin name"() {
        expect:
        PluginUtils.readPluginConfiguration('../escape', PluginUtilsSpec.classLoader) == null
    }

    @Unroll
    def "readPluginConfiguration finds a plugin's namespaced #fileName"(String fileName) {
        given: 'a plugin whose configuration was copied under its name by the compiler, and a stray one at the root'
        Path root = temp.resolve('plugin')
        PluginClassFixtures.compilePlugin(root, 'fixture.copy.CopiedGrailsPlugin')
        PluginClassFixtures.write(root, fileName, 'beside: the plugin')
        PluginClassFixtures.write(root, PluginUtils.getPluginConfigPath('copied', fileName), 'copied: by the build')
        URLClassLoader loader = PluginClassFixtures.loaderOver(root)

        when:
        def resource = PluginUtils.readPluginConfiguration('copied', loader)

        then: 'it keeps its name, which is how its format is chosen'
        resource.filename == fileName
        resource.inputStream.text == 'copied: by the build'

        cleanup:
        loader?.close()

        where:
        fileName << ['plugin.yml', 'plugin.groovy']
    }

    def "readPluginConfiguration finds each plugin's own configuration where every class is at one root"() {
        given: 'two plugins at one root, as every class is in a native image, each with its namespaced configuration'
        Path root = temp.resolve('image')
        PluginClassFixtures.compilePlugin(root, 'fixture.alpha.AlphaGrailsPlugin')
        PluginClassFixtures.compilePlugin(root, 'fixture.beta.BetaGrailsPlugin')
        PluginClassFixtures.write(root, PluginUtils.getPluginConfigPath('alpha', 'plugin.yml'), 'alpha: one')
        PluginClassFixtures.write(root, PluginUtils.getPluginConfigPath('beta', 'plugin.yml'), 'beta: two')
        URLClassLoader loader = PluginClassFixtures.loaderOver(root)

        expect:
        PluginUtils.readPluginConfiguration('alpha', loader).inputStream.text == 'alpha: one'
        PluginUtils.readPluginConfiguration('beta', loader).inputStream.text == 'beta: two'

        and: 'the compiler listed both plugins'
        PluginUtils.scanPluginImports(loader).containsAll(['alpha', 'beta'])

        cleanup:
        loader?.close()
    }

    def "readPluginConfiguration rejects a plugin with both plugin.yml and plugin.groovy"() {
        given:
        Path root = temp.resolve('both')
        PluginClassFixtures.write(root, PluginUtils.getPluginConfigPath('both', 'plugin.yml'), 'a: 1')
        PluginClassFixtures.write(root, PluginUtils.getPluginConfigPath('both', 'plugin.groovy'), 'a = 1')
        URLClassLoader loader = PluginClassFixtures.loaderOver(root)

        when:
        PluginUtils.readPluginConfiguration('both', loader)

        then:
        def e = thrown(grails.plugins.exceptions.PluginException)
        e.message.contains('[both]')

        cleanup:
        loader?.close()
    }

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
    def "isGrailsPlugin is #isPlugin for #description"() {
        expect:
        PluginUtils.isGrailsPlugin(clazz) == isPlugin

        where:
        description                                | clazz                                                                                                  || isPlugin
        'an annotated Plugin subclass'             | parsePlugin('@grails.plugins.metadata.GrailsPluginDescriptor class AGrailsPlugin extends grails.plugins.Plugin { }') || true
        'an annotated Plugin without the suffix'   | parsePlugin('@grails.plugins.metadata.GrailsPluginDescriptor class Starter extends grails.plugins.Plugin { }')       || true
        'a Plugin subclass without the annotation' | parsePlugin('class BGrailsPlugin extends grails.plugins.Plugin { }')                                                || false
        'an abstract annotated Plugin'             | parsePlugin('@grails.plugins.metadata.GrailsPluginDescriptor abstract class CGrailsPlugin extends grails.plugins.Plugin { }') || false
        'a *GrailsPlugin class that is no Plugin'  | parsePlugin('class DGrailsPlugin { }')                                                                             || false
        'DefaultGrailsPlugin'                      | DefaultGrailsPlugin                                                                                                || false
        'null'                                     | null                                                                                                               || false
    }

    def "supportsValueInIncludeExcludeMap returns true for empty map"() {
        expect:
        PluginUtils.supportsValueInIncludeExcludeMap([:], 'anything')
    }

    def "supportsValueInIncludeExcludeMap checks includes/excludes"() {
        when:
        def mapWithIncludes = ['includes': ['dev', 'test'] as Set]

        then:
        PluginUtils.supportsValueInIncludeExcludeMap(mapWithIncludes, 'dev')
        !PluginUtils.supportsValueInIncludeExcludeMap(mapWithIncludes, 'prod')
    }

    def "supportsValueInIncludeExcludeMap checks excludes"() {
        when:
        def mapWithExcludes = ['excludes': ['prod'] as Set]

        then:
        PluginUtils.supportsValueInIncludeExcludeMap(mapWithExcludes, 'dev')
        !PluginUtils.supportsValueInIncludeExcludeMap(mapWithExcludes, 'prod')
    }

    def "scanPluginDescriptorResources returns nothing when no descriptors are found"() {
        when:
        def descriptors = PluginUtils.scanPluginDescriptorResources(
                new URLClassLoader([] as URL[], (ClassLoader) null)
        )

        then:
        descriptors.isEmpty()
    }

    @Unroll
    def "getLogicalPluginName derives plugin name #expectedName from plugin class #className"() {
        expect:
        PluginUtils.getLogicalPluginName(parsePlugin(
                "@grails.plugins.metadata.GrailsPluginDescriptor${annotation} class ${className} extends grails.plugins.Plugin { }")) == expectedName

        where:
        className                            | annotation               || expectedName
        'PluginWithAllOrderingGrailsPlugin'  | ''                       || 'pluginWithAllOrdering'
        'CoreLikeGrailsPlugin'               | ''                       || 'coreLike'
        'RenamedGrailsPlugin'                | "(name = 'my-plugin')"   || 'myPlugin'
    }
}
