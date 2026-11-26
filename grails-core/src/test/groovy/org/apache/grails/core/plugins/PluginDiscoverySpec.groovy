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

import ch.qos.logback.classic.Level
import spock.lang.Specification
import spock.lang.Unroll

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import grails.plugins.exceptions.PluginException
import grails.plugins.metadata.PluginDefinition
import org.apache.grails.core.plugins.filters.ExcludingPluginFilter
import org.apache.grails.core.plugins.filters.IncludingPluginFilter
import org.apache.grails.core.testing.support.LogCapture

class PluginDiscoverySpec extends Specification {

    private static File writePluginDescriptor(File root, String name, String propertiesContent) {
        File imports = new File(root, PluginUtils.PLUGINS_IMPORTS_PATH)
        imports.parentFile.mkdirs()
        imports.append("${name}\n")

        File descriptor = new File(root, PluginUtils.getPluginDescriptorPath(name))
        descriptor.parentFile.mkdirs()
        descriptor.setText(propertiesContent.stripIndent(), 'UTF-8')
        descriptor
    }

    def 'finds plugin descriptors on the classpath and includes their resources'() {
        when: 'plugin descriptor resources are scanned from the current context class loader'
        def descriptors = PluginUtils.scanPluginDescriptorResources(
                Thread.currentThread().contextClassLoader
        )

        then: 'the core plugin descriptor is among them'
        descriptors.values().any { it.pluginName == 'core' && it.pluginClass == 'org.grails.plugins.CoreGrailsPlugin' }

        and: 'each descriptor is a resource that exists'
        descriptors.keySet().every { it.exists() }
    }

    def 'returns no plugin descriptors when the class loader has no plugins.imports resources'() {
        given: 'a class loader with no Grails plugin descriptors'
        def emptyClassLoader = new URLClassLoader([] as URL[], (ClassLoader) null)

        when: 'plugin descriptor resources are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(emptyClassLoader)

        then: 'no descriptors are found'
        descriptors.isEmpty()
    }

    def 'captures the plugin class and provided class names grouped by artefact type from plugin.properties'() {
        given: 'a plugin.properties descriptor with class and grouped artefact entries'
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'test', '''
            format.version=1
            name=test
            version=1.0.0
            class=com.example.TestGrailsPlugin
            artefacts.Domain=com.example.MyDomainClass
            artefacts.Service=com.example.MyService
        ''')
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptor resources are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'the descriptor captures the plugin class and artefact classes without including the plugin class as an artefact'
        descriptors.size() == 1
        with(descriptors.values().first()) {
            pluginName == 'test'
            pluginVersion == '1.0.0'
            pluginClass == 'com.example.TestGrailsPlugin'
            providedClasses == ['com.example.MyDomainClass', 'com.example.MyService']
            artefactsByType == [
                    Domain : ['com.example.MyDomainClass'],
                    Service: ['com.example.MyService']
            ]
        }
        descriptors.keySet().first().exists()

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'fails on a plugin descriptor with an unsupported format.version'() {
        given: 'a descriptor with an unsupported format version'
        def badDir = File.createTempDir()
        writePluginDescriptor(badDir, 'future', '''
            format.version=99
            name=future
            version=1.0.0
            class=com.example.FutureGrailsPlugin
        ''')
        def classLoader = new URLClassLoader([badDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptor resources are scanned'
        PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'the broken descriptor fails discovery, naming the format'
        def e = thrown(PluginException)
        e.message.contains('Unsupported plugin descriptor format 99')

        cleanup:
        classLoader?.close()
        badDir.deleteDir()
    }

    def 'fails on a plugin descriptor that names no plugin class'() {
        given: 'a classless descriptor, which a starter plugin used to be'
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'starterMetrics', '''
            format.version=1
            name=starterMetrics
            version=2.1.0
        ''')
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when:
        new ClasspathPluginFinder().findClasspathPlugins(classLoader, '9.0.0')

        then:
        def e = thrown(PluginException)
        e.message.contains('starterMetrics')
        e.message.contains('names no plugin class')

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'fails on a plugin descriptor whose plugin class is not on the class path'() {
        given:
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'absent', 'format.version=1\nname=absent\nversion=1.0\nclass=com.example.AbsentGrailsPlugin\n')
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when:
        new ClasspathPluginFinder().findClasspathPlugins(classLoader, '9.0.0')

        then:
        def e = thrown(PluginException)
        e.message.contains('com.example.AbsentGrailsPlugin')
        e.message.contains('cannot be loaded')

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'fails on a plugin descriptor whose class is not a grails.plugins.Plugin'() {
        given: 'a descriptor naming a class that does not extend Plugin'
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'notAPlugin', 'format.version=1\nname=notAPlugin\nversion=1.0\nclass=java.lang.String\n')
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], PluginDiscoverySpec.classLoader)

        when:
        new ClasspathPluginFinder().findClasspathPlugins(classLoader, '9.0.0')

        then:
        def e = thrown(PluginException)
        e.message.contains('notAPlugin')
        e.message.contains('java.lang.String')

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'fails on a plugins.imports entry #entry that is not a valid plugin name'() {
        given: 'a plugins.imports file containing an invalid entry'
        def tempDir = File.createTempDir()
        def imports = new File(tempDir, PluginUtils.PLUGINS_IMPORTS_PATH)
        imports.parentFile.mkdirs()
        imports.text = "validPlugin\n${entry}\n"
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptors are scanned'
        PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'the entry is rejected rather than resolved against the class path'
        def e = thrown(PluginException)
        e.message.contains("Invalid Grails plugin name [${entry}]")

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()

        where:
        entry << ['../evil', 'foo/bar', 'a..b']
    }

    def 'fails on a plugins.imports entry that has no descriptor'() {
        given:
        def tempDir = File.createTempDir()
        def imports = new File(tempDir, PluginUtils.PLUGINS_IMPORTS_PATH)
        imports.parentFile.mkdirs()
        imports.text = 'listedOnly\n'
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when:
        PluginUtils.scanPluginDescriptorResources(classLoader)

        then:
        def e = thrown(PluginException)
        e.message.contains('[listedOnly]')
        e.message.contains(PluginUtils.getPluginDescriptorPath('listedOnly'))

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'path helper methods reject invalid plugin names and config file names'() {
        expect:
        !PluginUtils.isValidPluginName('../evil')
        !PluginUtils.isValidPluginName('foo/bar')
        !PluginUtils.isValidPluginName('a..b')
        !PluginUtils.isValidPluginName('   ')
        PluginUtils.isValidPluginName('spring-security-core')

        when:
        PluginUtils.getPluginDescriptorPath('../evil')
        then:
        thrown(IllegalArgumentException)

        when:
        PluginUtils.getPluginConfigPath('validPlugin', '../secret.yml')
        then:
        thrown(IllegalArgumentException)

        when:
        PluginUtils.getPluginViewsPath('foo/bar')
        then:
        thrown(IllegalArgumentException)
    }

    def 'defaults empty dependsOn version to wildcard and normalizes observe entries of a classpath descriptor'() {
        given: 'a descriptor with an empty dependsOn version and hyphenated observe entries'
        def gcl = new GroovyClassLoader()
        gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class EdgeMetaGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'edgeMeta', '''
            format.version=1
            name=edgeMeta
            version=1.0.0
            class=EdgeMetaGrailsPlugin
            grailsVersion=9.0.0 > *
            dependsOn.core=
            observe=spring-security-core, *,  
        ''')
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], gcl)

        when: 'classpath plugins are discovered'
        def plugin = new ClasspathPluginFinder().findClasspathPlugins(classLoader, '9.0.0').find { it.name == 'edgeMeta' }

        then: 'empty dependsOn defaults to * and observe normalizes names while preserving *'
        plugin != null
        plugin.getDependentVersion('core') == '*'
        plugin.observedPluginNames as List == ['springSecurityCore', '*']

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'sanitizes blank dependsOn names and normalizes observe entries declared by the annotation'() {
        given: 'a plugin class registered directly whose annotation has edge-case entries'
        def pluginClass = new GroovyClassLoader().parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(
                    name = 'edge-annotated',
                    dependsOn = [':1.0', ' :2.0', 'i18n'],
                    observe = ['spring-security-core', '*', ' ']
            )
            class EdgeAnnotatedDescriptor extends grails.plugins.Plugin { }
        ''')

        when: 'metadata is extracted from the annotation'
        def metadata = PluginUtils.createPluginInfo(pluginClass)

        then: 'blank dependsOn names are ignored, a bare dependency defaults to *, and observe is normalized'
        metadata.name == 'edgeAnnotated'
        metadata.dependsOnNames as List == ['i18n']
        metadata.getDependentVersion('i18n') == '*'
        metadata.observedPluginNames as List == ['springSecurityCore', '*']
    }

    def 'a classpath plugin is described by its descriptor, not by its annotation'() {
        given: 'a plugin class whose annotation and descriptor disagree'
        def gcl = new GroovyClassLoader()
        def pluginClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(loadAfter = ['fromAnnotation'])
            class DescribedGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def props = new Properties()
        props.load(new StringReader('''
            format.version=1
            name=described
            version=1.0.0
            grailsVersion=9.0.0 > *
            class=DescribedGrailsPlugin
            loadAfter=fromDescriptor
        '''.stripIndent()))
        def definition = PluginDefinition.fromProperties('described', props)

        when:
        def metadata = PluginUtils.createPluginInfo(pluginClass, definition, null)

        then: 'the compiled descriptor is the single source of the metadata'
        metadata.pluginVersion == '1.0.0'
        metadata.grailsVersionRange == '9.0.0 > *'
        metadata.loadAfterNames as List == ['fromDescriptor']
    }

    def 'a plugin with a descriptor for another class is rejected'() {
        given:
        def pluginClass = new GroovyClassLoader().parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class MismatchedGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def definition = new PluginDefinition.Builder(pluginName: 'mismatched', pluginClass: 'com.example.OtherGrailsPlugin').build()

        when:
        PluginUtils.createPluginInfo(pluginClass, definition, null)

        then:
        def e = thrown(PluginException)
        e.message.contains('com.example.OtherGrailsPlugin')
        e.message.contains('MismatchedGrailsPlugin')
    }

    def 'loads namespaced plugin.groovy and fails when both plugin.yml and plugin.groovy exist'() {
        given: 'a plugin with namespaced plugin.groovy, in the class path root of its class'
        def groovyDir = File.createTempDir()
        writePluginDescriptor(groovyDir, 'groovyConfigured',
                'format.version=1\nname=groovyConfigured\nversion=1.0.0\nclass=GroovyConfiguredGrailsPlugin\n')
        new File(groovyDir, PluginUtils.getPluginConfigPath('groovyConfigured', PluginUtils.PLUGIN_GROOVY_CONFIG)).text = 'starter.flag = true\n'
        def groovyLoader = new GroovyClassLoader(new URLClassLoader([groovyDir.toURI().toURL()] as URL[], PluginDiscoverySpec.classLoader))
        groovyLoader.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class GroovyConfiguredGrailsPlugin extends grails.plugins.Plugin { }
        ''')

        and: 'a plugin with both plugin.yml and plugin.groovy'
        def bothDir = File.createTempDir()
        writePluginDescriptor(bothDir, 'clashConfigured',
                'format.version=1\nname=clashConfigured\nversion=1.0.0\nclass=ClashConfiguredGrailsPlugin\n')
        new File(bothDir, PluginUtils.getPluginConfigPath('clashConfigured', PluginUtils.PLUGIN_YML_CONFIG)).text = 'starter:\n  flag: true\n'
        new File(bothDir, PluginUtils.getPluginConfigPath('clashConfigured', PluginUtils.PLUGIN_GROOVY_CONFIG)).text = 'starter.flag = true\n'
        def bothLoader = new GroovyClassLoader(new URLClassLoader([bothDir.toURI().toURL()] as URL[], PluginDiscoverySpec.classLoader))
        bothLoader.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class ClashConfiguredGrailsPlugin extends grails.plugins.Plugin { }
        ''')

        when: 'the plugin.groovy plugin is discovered'
        def plugin = new ClasspathPluginFinder().findClasspathPlugins(groovyLoader, '9.0.0').find { it.name == 'groovyConfigured' }

        then: 'its namespaced plugin.groovy config resource is returned'
        plugin.configResource != null
        plugin.configResource.filename == 'plugin.groovy'
        plugin.configResource.inputStream.text == 'starter.flag = true\n'

        when: 'configuration is read directly for a plugin with both config files'
        PluginUtils.readPluginConfiguration('clashConfigured', bothLoader)

        then: 'reading it fails, naming the plugin'
        def e = thrown(PluginException)
        e.message.contains('clashConfigured')

        when: 'the misconfigured plugin is found on the class path'
        new ClasspathPluginFinder().findClasspathPlugins(bothLoader, '9.0.0')

        then: 'classpath discovery fails too, rather than skipping it'
        thrown(PluginException)

        cleanup:
        groovyLoader?.close()
        bothLoader?.close()
        groovyDir.deleteDir()
        bothDir.deleteDir()
    }

    def 'registers a classpath plugin under its descriptor name and resolves config under that name'() {
        given: 'a plugin class whose annotation declares the logical name bar, with its descriptor and plugin.yml'
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'bar', '''
            format.version=1
            name=bar
            version=1.0.0
            class=com.example.RenamedSourceGrailsPlugin
        ''')
        new File(tempDir, PluginUtils.getPluginConfigPath('bar', PluginUtils.PLUGIN_YML_CONFIG)).text = 'bar:\n  enabled: true\n'
        def classLoader = new GroovyClassLoader(new URLClassLoader([tempDir.toURI().toURL()] as URL[], PluginDiscoverySpec.classLoader))
        classLoader.parseClass('''
            package com.example
            @grails.plugins.metadata.GrailsPluginDescriptor(name = 'bar')
            class RenamedSourceGrailsPlugin extends grails.plugins.Plugin { }
        ''')

        when: 'classpath plugins are discovered'
        def plugins = new ClasspathPluginFinder().findClasspathPlugins(classLoader, '9.0.0')

        then: 'the plugin registers under the declared name and finds its namespaced plugin.yml'
        def barPlugin = plugins.find { it.pluginClass?.name == 'com.example.RenamedSourceGrailsPlugin' }
        barPlugin != null
        barPlugin.name == 'bar'
        plugins.every { it.name != 'renamedSource' }
        barPlugin.configResource != null
        barPlugin.configResource.inputStream.text.contains('enabled: true')

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'ignores comments and blank lines in plugins.imports'() {
        given: 'a plugins.imports index containing comments and blank lines'
        def tempDir = File.createTempDir()
        def imports = new File(tempDir, PluginUtils.PLUGINS_IMPORTS_PATH)
        imports.parentFile.mkdirs()
        imports.text = '''
            # Generated by Grails
            alpha # inline comment

            beta-starter
        '''.stripIndent()
        def alphaDesc = new File(tempDir, PluginUtils.getPluginDescriptorPath('alpha'))
        alphaDesc.parentFile.mkdirs()
        alphaDesc.text = 'format.version=1\nname=alpha\nversion=1.0\nclass=com.example.AlphaGrailsPlugin\n'
        def betaDesc = new File(tempDir, PluginUtils.getPluginDescriptorPath('betaStarter'))
        betaDesc.parentFile.mkdirs()
        betaDesc.text = 'format.version=1\nname=betaStarter\nversion=1.0\nclass=com.example.BetaStarterGrailsPlugin\n'
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptors are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'both descriptors are discovered with normalized logical names'
        descriptors.values()*.pluginName == ['alpha', 'betaStarter']
        descriptors.values()*.pluginClass == ['com.example.AlphaGrailsPlugin', 'com.example.BetaStarterGrailsPlugin']

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'extracts metadata directly from @GrailsPluginDescriptor without instantiating the class'() {
        given: 'an annotated plugin class whose constructor throws'
        def pluginClass = new GroovyClassLoader().parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(
                    name = 'annotated-plugin',
                    grailsVersion = '9.0.0 > *',
                    loadAfter = ['core', 'spring-security-core'],
                    loadBefore = ['controllers'],
                    dependsOn = ['i18n:9.0.0 > *'],
                    evict = ['legacy-plugin'],
                    observe = ['services']
            )
            class AnnotatedNonInstantiableDescriptor extends grails.plugins.Plugin {
                AnnotatedNonInstantiableDescriptor() {
                    throw new AssertionError('Constructor must not be invoked when reading @GrailsPluginDescriptor metadata')
                }
            }
        ''')

        when: 'metadata is extracted'
        def metadata = PluginUtils.createPluginInfo(pluginClass)

        then: 'all metadata is extracted from the annotation without calling the constructor'
        metadata.name == 'annotatedPlugin'
        metadata.grailsVersionRange == '9.0.0 > *'
        metadata.loadAfterNames as List == ['core', 'springSecurityCore']
        metadata.loadBeforeNames as List == ['controllers']
        metadata.dependsOnNames as List == ['i18n']
        metadata.getDependentVersion('i18n') == '9.0.0 > *'
        metadata.evictions as List == ['legacyPlugin']
        metadata.observedPluginNames as List == ['services']

        and: 'a class registered directly without a descriptor on its class path has no version'
        metadata.pluginVersion == null
    }

    def 'a plugin class registered directly takes its version from its descriptor on the class path'() {
        given:
        def root = File.createTempDir()
        def gcl = PluginClassFixtures.versionedPluginLoader(root.toPath(), [VersionedProbeGrailsPlugin: '4.5.6'])
        def pluginClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['other:${plugin.version}'])
            class VersionedProbeGrailsPlugin extends grails.plugins.Plugin { }
        ''')

        when:
        def definition = PluginUtils.definitionOf(pluginClass)

        then: 'the version is the descriptor\'s, and ${plugin.version} resolves to it'
        definition.pluginVersion == '4.5.6'
        definition.dependsOn == [other: '4.5.6']

        cleanup:
        gcl?.close()
        root.deleteDir()
    }

    def 'a ${plugin.version} dependency of a plugin without a known version accepts any version'() {
        given:
        def pluginClass = new GroovyClassLoader().parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['other:${plugin.version}'])
            class UnversionedProbeGrailsPlugin extends grails.plugins.Plugin { }
        ''')

        expect:
        PluginUtils.definitionOf(pluginClass).dependsOn == [other: '*']
    }

    def 'a class that is not a plugin is rejected: #description'() {
        when:
        PluginUtils.definitionOf(new GroovyClassLoader().parseClass(source))

        then:
        def e = thrown(PluginException)
        e.message.contains('is not a Grails plugin')

        where:
        description                                  | source
        'a *GrailsPlugin class that is not a Plugin' | 'class NamedOnlyGrailsPlugin { }'
        'a Plugin without the annotation'            | 'class UnannotatedGrailsPlugin extends grails.plugins.Plugin { }'
        'an abstract annotated Plugin'               | '@grails.plugins.metadata.GrailsPluginDescriptor abstract class AbstractGrailsPlugin extends grails.plugins.Plugin { }'
    }

    @Unroll
    def "derives logical plugin name '#expectedName' from plugin class #className"() {
        given:
        def pluginClass = new GroovyClassLoader().parseClass(
                "@grails.plugins.metadata.GrailsPluginDescriptor${annotation} class ${className} extends grails.plugins.Plugin { }")

        expect: 'the logical plugin name follows the class name, or the name @GrailsPluginDescriptor declares'
        PluginUtils.getLogicalPluginName(pluginClass) == expectedName
        PluginUtils.definitionOf(pluginClass).pluginName == expectedName

        where:
        className                            | annotation                   || expectedName
        'DiscoveryTestCoreGrailsPlugin'      | ''                           || 'discoveryTestCore'
        'DiscoveryTestSimpleGrailsPlugin'    | ''                           || 'discoveryTestSimple'
        'DiscoveryTestABCGrailsPlugin'       | ''                           || 'discoveryTestABC'
        'DiscoveryNoSuffix'                  | ''                           || 'discoveryNoSuffix'
        'AnnotatedNameDescriptor'            | "(name = 'annotated-plugin')" || 'annotatedPlugin'
    }

    def 'treats discovered plugins with the same name as equal'() {
        given: 'two plugins that differ in every field except the name'
        def loader = new GroovyClassLoader()
        def first = loader.parseClass('package a; class TestGrailsPlugin extends grails.plugins.Plugin { }')
        def second = loader.parseClass('package b; class TestGrailsPlugin extends grails.plugins.Plugin { }')
        def info1 = new PluginInfo(new PluginDefinition.Builder(pluginName: 'test', pluginClass: 'a.TestGrailsPlugin', pluginVersion: '1.0', loadAfter: ['a']).build(), first, null, null)
        def info2 = new PluginInfo(new PluginDefinition.Builder(pluginName: 'test', pluginClass: 'b.TestGrailsPlugin', pluginVersion: '2.0', dependsOn: [dep: '1.0'], observe: ['other']).build(), second, null, null)

        expect: 'equality is based on name only, not other attributes'
        info1 == info2
        info1.hashCode() == info2.hashCode()
    }

    def 'treats discovered plugins with different names as distinct, and names them in their string form'() {
        given:
        def loader = new GroovyClassLoader()
        def alpha = loader.parseClass('class AlphaGrailsPlugin extends grails.plugins.Plugin { }')
        def beta = loader.parseClass('class BetaGrailsPlugin extends grails.plugins.Plugin { }')
        def info1 = new PluginInfo(new PluginDefinition.Builder(pluginName: 'alpha', pluginClass: 'AlphaGrailsPlugin', pluginVersion: '1.0').build(), alpha, null, null)
        def info2 = new PluginInfo(new PluginDefinition.Builder(pluginName: 'beta', pluginClass: 'BetaGrailsPlugin', pluginVersion: '1.0').build(), beta, null, null)

        expect:
        info1 != info2
        info1.toString() == 'PluginInfo[name=alpha, class=AlphaGrailsPlugin]'
    }

    def 'exposes the expected plugin utility constants'() {
        expect: 'the plugin utility constants keep their documented values'
        with(PluginUtils) {
            PLUGINS_IMPORTS_PATH == 'META-INF/grails/plugins.imports'
            PLUGIN_DESCRIPTORS_PREFIX == 'META-INF/grails/plugins/'
            PLUGIN_PROPERTIES_FILE == 'plugin.properties'
            PLUGIN_VIEWS_FILE == 'views.properties'
            PLUGIN_YML_CONFIG == 'plugin.yml'
            PLUGIN_GROOVY_CONFIG == 'plugin.groovy'
            DEFAULT_CONFIG_IGNORE_LIST == ['dataSource', 'hibernate']
            getPluginDescriptorPath('spring-security-core') == 'META-INF/grails/plugins/springSecurityCore/plugin.properties'
            getPluginConfigPath('spring-security-core', 'plugin.yml') == 'META-INF/grails/plugins/springSecurityCore/plugin.yml'
            getPluginViewsPath('spring-security-core') == 'META-INF/grails/plugins/springSecurityCore/views.properties'
        }
    }

    def 'two different plugin classes with one name report both origins'() {
        given:
        def gcl = new GroovyClassLoader()
        ['First', 'Second'].each {
            gcl.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor(name = 'conflictingId') class ${it}ConflictGrailsPlugin extends grails.plugins.Plugin { }")
        }
        def roots = ['First', 'Second'].collect {
            def dir = File.createTempDir()
            writePluginDescriptor(dir, 'conflictingId', "name=conflictingId\nversion=1\nclass=${it}ConflictGrailsPlugin\n")
            dir
        }
        def loader = new URLClassLoader(roots*.toURI()*.toURL() as URL[], gcl)

        when:
        new ClasspathPluginFinder().findClasspathPlugins(loader, '9.0.0')

        then:
        def error = thrown(PluginException)
        roots.every { error.message.contains(it.name) }
        error.message.contains('conflictingId')

        cleanup:
        loader?.close()
        roots*.deleteDir()
    }

    def 'a plugin class declared in multiple classpath descriptors is registered only once'() {
        given: 'a plugin class compiled by a Groovy class loader'
        def gcl = new GroovyClassLoader()
        gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class DuplicateDescriptorProbeGrailsPlugin extends grails.plugins.Plugin { }
        ''')

        and: 'two classpath roots each declaring the same plugin class'
        def tempDirs = (1..2).collect { index ->
            def tempDir = File.createTempDir()
            writePluginDescriptor(tempDir, 'duplicateDescriptorProbe', '''
                format.version=1
                name=duplicateDescriptorProbe
                version=1.0.0
                class=DuplicateDescriptorProbeGrailsPlugin
            ''')
            tempDir
        }
        def classLoader = new URLClassLoader(tempDirs*.toURI()*.toURL() as URL[], gcl)

        and: 'the discovery resolves plugins from the thread context class loader'
        def originalContextClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoader
        def discovery = new DefaultPluginDiscovery()

        when:
        discovery.init(new StandardEnvironment())

        then: 'the plugin is registered under its name'
        discovery.hasPlugin('duplicateDescriptorProbe')

        and: 'only one registration appears in the load order'
        discovery.pluginsInLoadOrder.count { it.name == 'duplicateDescriptorProbe' } == 1

        cleanup:
        Thread.currentThread().contextClassLoader = originalContextClassLoader
        classLoader?.close()
        tempDirs*.deleteDir()
    }

    def 'a plugin class supplied more than once is registered only once and skipping is logged at WARN'() {
        given: 'a discovery bean supplied the same plugin class twice'
        def pluginClass = new GroovyClassLoader().parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class RepeatedProbeGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def discovery = new DefaultPluginDiscovery([pluginClass, pluginClass] as Class<?>[]).tap {
            loadPluginsFromClasspath = false
        }

        and: 'configure a logback appender to capture log messages'
        def logCapture = new LogCapture(DefaultPluginDiscovery)

        when:
        discovery.init(new StandardEnvironment())

        then: 'only one registration appears in the load order'
        with(discovery) {
            hasPlugin('repeatedProbe')
            pluginsInLoadOrder.count { it.name == 'repeatedProbe' } == 1
        }

        and: 'the skipped duplicate is reported at WARN'
        def duplicateWarnings = logCapture.events.findAll {
            it.level == Level.WARN &&
            it.formattedMessage.contains('repeatedProbe') &&
            it.formattedMessage.contains('already registered')
        }
        duplicateWarnings.size() == 1

        cleanup:
        logCapture.close()
    }

    def 'a class supplied to discovery that is not a plugin fails discovery'() {
        given:
        def notAPlugin = new GroovyClassLoader().parseClass('class LegacyShapedGrailsPlugin { def version = "1.0" }')
        def discovery = new DefaultPluginDiscovery([notAPlugin] as Class<?>[]).tap {
            loadPluginsFromClasspath = false
        }

        when:
        discovery.init(new StandardEnvironment())

        then:
        def e = thrown(PluginException)
        e.message.contains('LegacyShapedGrailsPlugin')
        e.message.contains('is not a Grails plugin')
    }

    def 'a dependency of unknown version satisfies any required version'() {
        given: 'two plugins registered directly, without descriptors, so neither has a version'
        def gcl = new GroovyClassLoader()
        def dependencyClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class UnknownVersionDependencyGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def dependentClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['unknownVersionDependency:2.0.0 > *'])
            class NeedsVersionedDependencyGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def discovery = new DefaultPluginDiscovery([dependentClass, dependencyClass] as Class<?>[]).tap {
            loadPluginsFromClasspath = false
        }

        when:
        discovery.init(new StandardEnvironment())

        then: 'the dependency is satisfied and loaded first'
        discovery.pluginsInLoadOrder*.name == ['unknownVersionDependency', 'needsVersionedDependency']
    }

    def 'stops discovery with one exception naming every missing or incompatible plugin dependency'() {
        given: 'a discovery bean with one available and one unresolved plugin, versioned by their descriptors'
        def root = File.createTempDir()
        def gcl = PluginClassFixtures.versionedPluginLoader(root.toPath(), [AvailableDependencyGrailsPlugin: '1.0.0', UnresolvedDependenciesGrailsPlugin: '4.0.0'])
        def availableDependencyClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor
            class AvailableDependencyGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def unresolvedDependenciesClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['missingDependency:1.0.0', 'availableDependency:2.0.0'])
            class UnresolvedDependenciesGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def discovery = directDiscovery(availableDependencyClass, unresolvedDependenciesClass)

        when: 'the public discovery API initializes the plugin set'
        discovery.init(new StandardEnvironment())

        then: 'the application cannot start: one exception identifies the plugin and every unresolved dependency'
        def e = thrown(PluginException)
        e.message.contains('Grails plug-in [unresolvedDependencies] with version [4.0.0] cannot be loaded')
        e.message.contains('dependency [missingDependency] with required version [1.0.0] is missing')
        e.message.contains('dependency [availableDependency] has version [1.0.0] but requires [2.0.0]')

        when: 'the discovery is asked for its plugins afterwards'
        discovery.pluginsInLoadOrder

        then: 'it is not left initialized with a partial plugin set'
        thrown(IllegalStateException)

        cleanup:
        gcl?.close()
        root.deleteDir()
    }

    def 'names every plugin that cannot be loaded, reporting a dependency that failed to load as failed rather than missing'() {
        given: 'a plugin whose only dependency itself fails to load due to a genuinely missing dependency'
        def gcl = new GroovyClassLoader()
        def failingDependencyClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['reallyMissing:1.0.0'])
            class FailingDependencyGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def dependentClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['failingDependency:1.0.0'])
            class DependentOnFailedGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def discovery = directDiscovery(failingDependencyClass, dependentClass)

        when:
        discovery.init(new StandardEnvironment())

        then: 'one exception names both plugins, one line each'
        def e = thrown(PluginException)
        def lines = e.message.readLines()
        lines.size() == 3

        and: 'the missing root cause is reported as missing'
        def rootCause = lines.find { it.contains('[failingDependency] cannot be loaded') }
        rootCause.contains('dependency [reallyMissing] with required version [1.0.0] is missing')

        and: 'the plugin depending on the failed plugin reports it as failed, not missing'
        def dependent = lines.find { it.contains('[dependentOnFailed]') }
        dependent.contains('dependency [failingDependency] with required version [1.0.0] failed to load')
        !dependent.contains('is missing')
    }

    def 'reports a dependency that is still pending load together with the version that was found'() {
        given: 'a plugin that requires a newer version of a dependency which is itself still waiting on a missing plugin'
        def root = File.createTempDir()
        def gcl = PluginClassFixtures.versionedPluginLoader(root.toPath(), [NeedsNewerPendingGrailsPlugin: '1.0.0', PendingDependencyGrailsPlugin: '1.0.0'])
        def dependentClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['pendingDependency:2.0.0'])
            class NeedsNewerPendingGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def pendingDependencyClass = gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['reallyMissing:1.0.0'])
            class PendingDependencyGrailsPlugin extends grails.plugins.Plugin { }
        ''')

        and: 'the dependent plugin is processed before the pending dependency gives up'
        def discovery = directDiscovery(dependentClass, pendingDependencyClass)

        when:
        discovery.init(new StandardEnvironment())

        then: 'both plugins are named'
        def e = thrown(PluginException)
        e.message.contains('Grails plug-in [pendingDependency] with version [1.0.0] cannot be loaded: ' +
                'dependency [reallyMissing] with required version [1.0.0] is missing')

        and: 'the dependent plugin reports the pending dependency with the version it found'
        def dependent = e.message.readLines().find { it.contains('[needsNewerPending]') }
        dependent.contains(
                'dependency [pendingDependency] with required version [2.0.0] is still pending load ' +
                        'and only version [1.0.0] was found'
        )
        !dependent.contains('is missing')

        cleanup:
        gcl?.close()
        root.deleteDir()
    }

    def 'a dependency excluded by grails.plugin.excludes stops discovery, naming the dependent to exclude too'() {
        given: 'a dependency and its dependent'
        def classes = dependencyAndDependent('ExcludedDependency', 'KeptDependent', '')
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', ['grails.plugin.excludes': 'excludedDependency']))

        when: 'only the dependency is excluded'
        directDiscovery(classes).init(environment)

        then: 'the dependent is not dropped silently: the application cannot start'
        def e = thrown(PluginException)
        e.message.contains('Grails plug-in [keptDependent] cannot be loaded: dependency ' +
                '[excludedDependency] is left out by the plugin filter (grails.plugin.excludes or grails.plugin.includes); ' +
                'leave out [keptDependent] too')
    }

    def 'excluding a dependency together with its dependents leaves both out'() {
        given:
        def classes = dependencyAndDependent('ExcludedTogether', 'ExcludedWithIt', '')
        def discovery = directDiscovery(classes).tap {
            pluginFilter = new ExcludingPluginFilter('excludedTogether', 'excludedWithIt')
        }

        when:
        discovery.init(new StandardEnvironment())

        then:
        discovery.pluginsInLoadOrder.empty
    }

    def 'including a plugin includes its dependencies, so a test application that names a few plugins starts'() {
        given: 'a dependency, a dependent of it, and an unrelated plugin'
        def gcl = new GroovyClassLoader()
        def classes = dependencyAndDependent('IncludedDependency', 'IncludedDependent', '', gcl) +
                gcl.parseClass('@grails.plugins.metadata.GrailsPluginDescriptor class UnrelatedGrailsPlugin extends grails.plugins.Plugin { }')
        def discovery = directDiscovery(classes).tap {
            pluginFilter = new IncludingPluginFilter('includedDependent')
        }

        when: 'only the dependent is named'
        discovery.init(new StandardEnvironment())

        then: 'its dependency is included with it, and loaded first'
        discovery.pluginsInLoadOrder*.name == ['includedDependency', 'includedDependent']
    }

    def 'including a plugin whose dependency is not on the class path reports the dependency as missing'() {
        given:
        def dependent = new GroovyClassLoader().parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['absent'])
            class IncludedWithoutDependencyGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def discovery = directDiscovery(dependent).tap {
            pluginFilter = new IncludingPluginFilter('includedWithoutDependency')
        }

        when:
        discovery.init(new StandardEnvironment())

        then:
        def e = thrown(PluginException)
        e.message.contains('dependency [absent] with required version [*] is missing')
    }

    def 'a dependency its environments leave out of the current environment stops discovery'() {
        given: 'a dependency that does not support the current environment'
        def current = grails.util.Environment.current.name
        def classes = dependencyAndDependent('OtherEnvironmentDependency', 'EnvironmentDependent',
                "environmentsExcludes = ['${current}']")

        when:
        directDiscovery(classes).init(new StandardEnvironment())

        then: 'the dependent cannot be loaded, and the message says why and what to do'
        def e = thrown(PluginException)
        e.message.contains("Grails plug-in [environmentDependent] cannot be loaded: dependency " +
                "[otherEnvironmentDependency] does not support the [${current}] environment; leave " +
                '[environmentDependent] out of that environment too, with its environmentsIncludes or environmentsExcludes')
    }

    def 'a plugin left out of the current environment with its dependency loads neither, and is not reported'() {
        given: 'a dependency and a dependent that both exclude the current environment'
        def current = grails.util.Environment.current.name
        def gcl = new GroovyClassLoader()
        def dependency = gcl.parseClass("""
            @grails.plugins.metadata.GrailsPluginDescriptor(environmentsExcludes = ['${current}'])
            class SkippedDependencyGrailsPlugin extends grails.plugins.Plugin { }
        """)
        def dependent = gcl.parseClass("""
            @grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['skippedDependency'], environmentsExcludes = ['${current}'])
            class SkippedDependentGrailsPlugin extends grails.plugins.Plugin { }
        """)
        def discovery = directDiscovery(dependent, dependency)

        when:
        discovery.init(new StandardEnvironment())

        then:
        discovery.pluginsInLoadOrder.empty
    }

    def 'a plugin depending on an evicted plugin still loads, the evicting plugin standing in for it'() {
        given: 'a plugin, a dependent of it, and a plugin that evicts it'
        def gcl = new GroovyClassLoader()
        def classes = dependencyAndDependent('Replaced', 'NeedsReplaced', '', gcl) + gcl.parseClass('''
            @grails.plugins.metadata.GrailsPluginDescriptor(evict = ['replaced'])
            class ReplacementGrailsPlugin extends grails.plugins.Plugin { }
        ''')
        def discovery = directDiscovery(classes)

        when:
        discovery.init(new StandardEnvironment())

        then: 'the evicted plugin is gone, and the plugin that needed it is loaded with its replacement'
        discovery.pluginsInLoadOrder*.name as Set == ['needsReplaced', 'replacement'] as Set
        !discovery.hasPlugin('replaced')
    }

    private static DefaultPluginDiscovery directDiscovery(Class<?>... pluginClasses) {
        new DefaultPluginDiscovery(pluginClasses).tap {
            loadPluginsFromClasspath = false
        }
    }

    private static DefaultPluginDiscovery directDiscovery(List<Class<?>> pluginClasses) {
        directDiscovery(pluginClasses as Class<?>[])
    }

    /**
     * A plugin named after {@code dependency} and one named after {@code dependent} that depends on it, the
     * dependency declaring the given extra descriptor attributes.
     */
    private static List<Class<?>> dependencyAndDependent(String dependency, String dependent, String dependencyAttributes,
                                                     GroovyClassLoader gcl = new GroovyClassLoader()) {
        String dependencyName = dependency.uncapitalize()
        [
                gcl.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor(${dependencyAttributes}) " +
                        "class ${dependency}GrailsPlugin extends grails.plugins.Plugin { }"),
                gcl.parseClass("@grails.plugins.metadata.GrailsPluginDescriptor(dependsOn = ['${dependencyName}']) " +
                        "class ${dependent}GrailsPlugin extends grails.plugins.Plugin { }")
        ] as List<Class<?>>
    }
}
