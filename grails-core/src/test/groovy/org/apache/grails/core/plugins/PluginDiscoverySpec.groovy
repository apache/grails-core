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

import org.springframework.core.env.StandardEnvironment

import grails.plugins.metadata.GrailsPluginDescriptor
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

        then: 'at least one descriptor is found'
        !descriptors.empty

        and: 'each descriptor has a resource'
        descriptors.every { it.resource }
    }

    def 'returns no plugin descriptors when the class loader has no plugins.imports resources'() {
        given: 'a class loader with no Grails plugin descriptors'
        def emptyClassLoader = new URLClassLoader([] as URL[], (ClassLoader) null)

        when: 'plugin descriptor resources are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(emptyClassLoader)

        then: 'no descriptors are found'
        descriptors.empty
    }

    def 'captures provided class names grouped by artefact type from plugin.properties'() {
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
        with(descriptors[0]) {
            pluginName == 'test'
            pluginVersion == '1.0.0'
            providedPlugins == ['com.example.TestGrailsPlugin']
            providedClasses == ['com.example.MyDomainClass', 'com.example.MyService']
            providedArtefactsByType == [
                    Domain : ['com.example.MyDomainClass'],
                    Service: ['com.example.MyService']
            ]
            resource != null
            resource.exists()
        }

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'skips a plugin descriptor with an unsupported format.version while loading valid descriptors'() {
        given: 'one descriptor with an unsupported format version and one valid descriptor'
        def badDir = File.createTempDir()
        writePluginDescriptor(badDir, 'future', '''
            format.version=99
            name=future
            version=1.0.0
            class=com.example.FutureGrailsPlugin
        ''')
        def goodDir = File.createTempDir()
        writePluginDescriptor(goodDir, 'plain', '''
            format.version=1
            name=plain
            version=1.0.0
            class=com.example.PlainGrailsPlugin
        ''')
        def classLoader = new URLClassLoader(
                [badDir.toURI().toURL(), goodDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptor resources are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'only the supported descriptor is returned'
        descriptors*.providedPlugins == [['com.example.PlainGrailsPlugin']]

        cleanup:
        classLoader?.close()
        badDir.deleteDir()
        goodDir.deleteDir()
    }

    def 'falls back to legacy META-INF/grails-plugin.xml when a plugin has not migrated yet and avoids duplicates'() {
        given: 'one legacy XML-only plugin and one plugin that has both modern and legacy descriptors'
        def legacyDir = File.createTempDir()
        def legacyXml = new File(legacyDir, 'META-INF/grails-plugin.xml')
        legacyXml.parentFile.mkdirs()
        legacyXml.text = '''
            <plugin name='legacy-asset' version='5.2.3'>
                <type>com.example.LegacyAssetGrailsPlugin</type>
                <resources>
                    <resource>com.example.AssetTagLib</resource>
                </resources>
            </plugin>
        '''.stripIndent()

        def dualDir = File.createTempDir()
        writePluginDescriptor(dualDir, 'modern', '''
            format.version=1
            name=modern
            version=2.0.0
            class=com.example.ModernGrailsPlugin
            artefacts.Service=com.example.ModernService
        ''')
        def dualXml = new File(dualDir, 'META-INF/grails-plugin.xml')
        dualXml.parentFile.mkdirs()
        dualXml.text = '''
            <plugin name='modern' version='1.0.0'>
                <type>com.example.ModernGrailsPlugin</type>
            </plugin>
        '''.stripIndent()

        def classLoader = new URLClassLoader(
                [legacyDir.toURI().toURL(), dualDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptor resources are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'the modern plugin is loaded once from plugin.properties and the legacy XML-only plugin is discovered via fallback'
        descriptors.size() == 2
        with(descriptors.find { it.pluginName == 'modern' }) {
            pluginVersion == '2.0.0'
            providedPlugins == ['com.example.ModernGrailsPlugin']
            providedClasses == ['com.example.ModernService']
        }
        with(descriptors.find { it.pluginName == 'legacyAsset' }) {
            pluginVersion == '5.2.3'
            providedPlugins == ['com.example.LegacyAssetGrailsPlugin']
            providedClasses == ['com.example.AssetTagLib']
        }

        cleanup:
        classLoader?.close()
        legacyDir.deleteDir()
        dualDir.deleteDir()
    }

    def 'skips only already-discovered plugin classes when a legacy XML descriptor shares a name with a modern descriptor'() {
        given: 'a modern descriptor for alpha and a legacy XML descriptor named alpha that lists beta'
        def modernDir = File.createTempDir()
        writePluginDescriptor(modernDir, 'alpha', '''
            format.version=1
            name=alpha
            version=2.0.0
            class=com.example.AlphaGrailsPlugin
        ''')

        def legacyDir = File.createTempDir()
        def legacyXml = new File(legacyDir, 'META-INF/grails-plugin.xml')
        legacyXml.parentFile.mkdirs()
        legacyXml.text = """
            <plugin name='alpha' version='1.0.0'>
                ${alphaType}
                <type>com.example.BetaGrailsPlugin</type>
            </plugin>
        """.stripIndent()

        def classLoader = new URLClassLoader(
                [modernDir.toURI().toURL(), legacyDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptor resources are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'alpha is loaded from the modern descriptor and beta is still discovered from the legacy descriptor'
        descriptors.size() == 2
        descriptors[0].providedPlugins == ['com.example.AlphaGrailsPlugin']
        descriptors[0].pluginVersion == '2.0.0'
        descriptors[1].providedPlugins == ['com.example.BetaGrailsPlugin']
        descriptors[1].pluginVersion == '1.0.0'
        descriptors[1].pluginName == 'beta'

        cleanup:
        classLoader?.close()
        modernDir.deleteDir()
        legacyDir.deleteDir()

        where:
        alphaType << ['', '<type>com.example.AlphaGrailsPlugin</type>']
    }

    def 'rejects invalid plugin names and skips path traversal entries in plugins.imports'() {
        given: 'a plugins.imports file containing invalid path-traversal entries alongside a valid entry'
        def tempDir = File.createTempDir()
        def imports = new File(tempDir, PluginUtils.PLUGINS_IMPORTS_PATH)
        imports.parentFile.mkdirs()
        imports.text = '''
            ../evil
            foo/bar
            a..b
            validPlugin
        '''.stripIndent()
        def validDesc = new File(tempDir, PluginUtils.getPluginDescriptorPath('validPlugin'))
        validDesc.parentFile.mkdirs()
        validDesc.text = 'format.version=1\nname=validPlugin\nversion=1.0.0\n'
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptors are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'only the valid entry is discovered'
        descriptors*.pluginName == ['validPlugin']

        and: 'path helper methods reject invalid plugin names and config file names'
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

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'rejects DOCTYPE declarations and skips malformed legacy XML descriptors without blocking valid ones'() {
        given: 'a legacy XML with DOCTYPE, a malformed legacy XML, and a valid legacy XML'
        def xxeDir = File.createTempDir()
        def xxeXml = new File(xxeDir, 'META-INF/grails-plugin.xml')
        xxeXml.parentFile.mkdirs()
        xxeXml.text = '''<?xml version="1.0"?>
            <!DOCTYPE plugin [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <plugin name="xxe" version="1.0.0"><type>com.example.XxeGrailsPlugin</type></plugin>
        '''.stripIndent()

        def brokenDir = File.createTempDir()
        def brokenXml = new File(brokenDir, 'META-INF/grails-plugin.xml')
        brokenXml.parentFile.mkdirs()
        brokenXml.text = '<plugin name="broken"><type>unclosed'

        def validDir = File.createTempDir()
        def validXml = new File(validDir, 'META-INF/grails-plugin.xml')
        validXml.parentFile.mkdirs()
        validXml.text = '''
            <plugin name='good-legacy' version='1.2.3'>
                <type>com.example.GoodLegacyGrailsPlugin</type>
            </plugin>
        '''.stripIndent()

        def classLoader = new URLClassLoader(
                [xxeDir.toURI().toURL(), brokenDir.toURI().toURL(), validDir.toURI().toURL()] as URL[],
                (ClassLoader) null
        )

        when: 'plugin descriptors are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'only the valid legacy descriptor is returned'
        descriptors.size() == 1
        descriptors[0].pluginName == 'goodLegacy'
        descriptors[0].providedPlugins == ['com.example.GoodLegacyGrailsPlugin']

        cleanup:
        classLoader?.close()
        xxeDir.deleteDir()
        brokenDir.deleteDir()
        validDir.deleteDir()
    }

    def 'defaults empty dependsOn version to wildcard and normalizes observe entries'() {
        given: 'a descriptor with an empty dependsOn version and hyphenated observe entries'
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'edgeMeta', '''
            format.version=1
            name=edgeMeta
            version=1.0.0
            grailsVersion=9.0.0 > *
            dependsOn.core=
            observe=spring-security-core, *,  
            metadata.complete=true
        ''')
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'classpath plugins are discovered'
        def plugins = new ClasspathPluginFinder().findClasspathPlugins(classLoader, '9.0.0')

        then: 'empty dependsOn defaults to * and observe normalizes names while preserving *'
        plugins.size() == 1
        plugins[0].metadata.getDependentVersion('core') == '*'
        plugins[0].observedPluginNames as List == ['springSecurityCore', '*']

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'sanitizes blank dependsOn names, normalizes observe, and uses descriptor grailsVersion fallback for annotations'() {
        given: 'a descriptor with a grailsVersion range and an annotated class that omits grailsVersion'
        def props = new Properties()
        props.load(new StringReader('''
            format.version=1
            name=edgeAnnotated
            version=1.0.0
            grailsVersion=9.0.0 > *
            class=org.apache.grails.core.plugins.EdgeAnnotatedDescriptor
        '''.stripIndent()))
        def descriptor = PluginDescriptor.fromProperties('edgeAnnotated', null, props)

        when: 'metadata is extracted with the descriptor'
        def metadata = PluginUtils.extractPluginMetadata(descriptor, EdgeAnnotatedDescriptor)

        then: 'blank dependsOn names are ignored, bare dependency defaults to *, observe is normalized, and grailsVersion falls back'
        metadata != null
        metadata.grailsVersionRange == '9.0.0 > *'
        metadata.dependsOnNames as List == ['i18n']
        metadata.getDependentVersion('i18n') == '*'
        metadata.observedPluginNames as List == ['springSecurityCore', '*']
    }

    def 'handles null elements safely in Groovy plugin loadAfter arrays'() {
        given: 'a Groovy plugin class whose loadAfter list contains a null element'
        def gcl = new GroovyClassLoader()
        def pluginClass = gcl.parseClass('''
            class NullSafeLoadAfterGrailsPlugin {
                def version = '1.0.0'
                def loadAfter = [null, 'core', 'spring-security-core']
            }
        ''')

        when: 'metadata is extracted'
        def metadata = PluginUtils.extractPluginMetadata(pluginClass)

        then: 'null elements are converted safely without throwing'
        metadata != null
        metadata.loadAfterNames as List == ['', 'core', 'spring-security-core']
    }

    def 'loads namespaced plugin.groovy and fails when both plugin.yml and plugin.groovy exist'() {
        given: 'a starter plugin with namespaced plugin.groovy'
        def groovyDir = File.createTempDir()
        writePluginDescriptor(groovyDir, 'groovyStarter', '''
            format.version=1
            name=groovyStarter
            version=1.0.0
            metadata.complete=true
        ''')
        def groovyCfg = new File(groovyDir, PluginUtils.getPluginConfigPath('groovyStarter', PluginUtils.PLUGIN_GROOVY_CONFIG))
        groovyCfg.parentFile.mkdirs()
        groovyCfg.text = 'starter.flag = true\n'
        def groovyLoader = new URLClassLoader([groovyDir.toURI().toURL()] as URL[], (ClassLoader) null)

        and: 'a starter plugin with both plugin.yml and plugin.groovy'
        def bothDir = File.createTempDir()
        writePluginDescriptor(bothDir, 'clashStarter', '''
            format.version=1
            name=clashStarter
            version=1.0.0
            metadata.complete=true
        ''')
        def ymlCfg = new File(bothDir, PluginUtils.getPluginConfigPath('clashStarter', PluginUtils.PLUGIN_YML_CONFIG))
        ymlCfg.parentFile.mkdirs()
        ymlCfg.text = 'starter:\n  flag: true\n'
        def secondGroovyCfg = new File(bothDir, PluginUtils.getPluginConfigPath('clashStarter', PluginUtils.PLUGIN_GROOVY_CONFIG))
        secondGroovyCfg.text = 'starter.flag = true\n'
        def bothLoader = new URLClassLoader([bothDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'the plugin.groovy starter is discovered'
        def plugins = new ClasspathPluginFinder().findClasspathPlugins(groovyLoader, '9.0.0')

        then: 'its namespaced plugin.groovy config resource is returned'
        plugins.size() == 1
        plugins[0].configResource != null
        plugins[0].configResource.filename == 'plugin.groovy'

        when: 'configuration is read directly for a plugin with both config files'
        PluginUtils.readPluginConfiguration('clashStarter', null, bothLoader)

        then: 'a RuntimeException is thrown'
        def e = thrown(RuntimeException)
        e.message.contains('clashStarter')

        and: 'tolerant classpath discovery skips the misconfigured plugin'
        new ClasspathPluginFinder().findClasspathPlugins(bothLoader, '9.0.0').isEmpty()

        cleanup:
        groovyLoader?.close()
        bothLoader?.close()
        groovyDir.deleteDir()
        bothDir.deleteDir()
    }

    def 'honors descriptor plugin name override for class-backed plugins and resolves config under the overridden name'() {
        given: 'a *GrailsPlugin class packaged under a custom logical name via grails { plugin { name = ... } }'
        def gcl = new GroovyClassLoader()
        gcl.parseClass('''
            package com.example
            class RenamedSourceGrailsPlugin {
                def version = '1.0.0'
            }
        ''')
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'bar', '''
            format.version=1
            name=bar
            version=1.0.0
            class=com.example.RenamedSourceGrailsPlugin
            metadata.complete=false
        ''')
        def ymlCfg = new File(tempDir, PluginUtils.getPluginConfigPath('bar', PluginUtils.PLUGIN_YML_CONFIG))
        ymlCfg.parentFile.mkdirs()
        ymlCfg.text = 'bar:\n  enabled: true\n'
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], gcl)

        when: 'classpath plugins are discovered'
        def plugins = new ClasspathPluginFinder().findClasspathPlugins(classLoader, '9.0.0')

        then: 'the plugin registers under the overridden descriptor name and finds its namespaced plugin.yml'
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
        betaDesc.text = 'format.version=1\nname=betaStarter\nversion=1.0\n'
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptors are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'both descriptors are discovered with normalized logical names'
        descriptors*.pluginName == ['alpha', 'betaStarter']
        descriptors[0].providedPlugins == ['com.example.AlphaGrailsPlugin']
        descriptors[1].descriptorLess

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'discovers descriptor-less starter plugins with metadata and namespaced plugin.yml'() {
        given: 'a descriptor-less starter plugin on the classpath with a namespaced plugin.yml'
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'starterMetrics', '''
            format.version=1
            name=starterMetrics
            version=2.1.0
            grailsVersion=9.0.0 > *
            loadAfter=core
            metadata.complete=true
            artefacts.Service=com.example.MetricsService
        ''')
        def configFile = new File(tempDir, PluginUtils.getPluginConfigPath('starterMetrics', PluginUtils.PLUGIN_YML_CONFIG))
        configFile.parentFile.mkdirs()
        configFile.text = 'metrics:\n  enabled: true\n'
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'classpath plugins are discovered'
        def plugins = new ClasspathPluginFinder().findClasspathPlugins(classLoader, '9.0.0')

        then: 'the descriptor-less plugin is discovered with full metadata and its namespaced config'
        plugins.size() == 1
        with(plugins[0]) {
            name == 'starterMetrics'
            pluginVersion == '2.1.0'
            pluginClass == null
            grailsVersionRange == '9.0.0 > *'
            loadAfterNames as List == ['core']
            pluginDescriptor.providedClasses == ['com.example.MetricsService']
            configResource != null
            configResource.inputStream.text.contains('enabled: true')
        }

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'extracts metadata directly from @GrailsPluginDescriptor without instantiating the class'() {
        when: 'metadata is extracted from an annotated plugin descriptor class whose constructor throws'
        def metadata = PluginUtils.extractPluginMetadata(AnnotatedNonInstantiableDescriptor)

        then: 'all metadata is extracted from the annotation without calling the constructor'
        metadata != null
        metadata.name == 'annotatedPlugin'
        metadata.pluginVersion == '3.2.1'
        metadata.grailsVersionRange == '9.0.0 > *'
        metadata.loadAfterNames as List == ['core', 'springSecurityCore']
        metadata.loadBeforeNames as List == ['controllers']
        metadata.dependsOnNames as List == ['i18n']
        metadata.getDependentVersion('i18n') == '9.0.0 > *'
        metadata.evictions as List == ['legacyPlugin']
        metadata.observedPluginNames as List == ['services']
        metadata.enabled
    }

    def 'extracts plugin class names from descriptors'() {
        given: 'two plugin descriptors with declared plugin classes'
        def tempDir = File.createTempDir()
        writePluginDescriptor(tempDir, 'alpha', 'format.version=1\nname=alpha\nversion=1.0\nclass=com.example.AlphaGrailsPlugin\n')
        writePluginDescriptor(tempDir, 'beta', 'format.version=1\nname=beta\nversion=1.0\nclass=com.example.BetaGrailsPlugin\n')
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptors are scanned for class names'
        def classNames = PluginUtils.scanPluginDescriptors(classLoader)

        then: 'returns class names from plugin.properties descriptors'
        classNames == ['com.example.AlphaGrailsPlugin', 'com.example.BetaGrailsPlugin']

        cleanup:
        classLoader?.close()
        tempDir.deleteDir()
    }

    def 'returns no plugin class names when the class loader has no plugin descriptors'() {
        given: 'a class loader with no Grails plugin descriptors'
        def emptyClassLoader = new URLClassLoader([] as URL[], (ClassLoader) null)

        when: 'plugin descriptors are scanned for class names'
        def classNames = PluginUtils.scanPluginDescriptors(emptyClassLoader)

        then: 'no plugin class names are found'
        classNames.empty
    }

    @Unroll
    def "derives logical plugin name '#expectedName' from plugin class #pluginClass.simpleName"() {
        expect: 'the logical plugin name matches the Grails naming convention or @GrailsPluginDescriptor'
        PluginUtils.getLogicalPluginName(pluginClass) == expectedName

        where:
        pluginClass                        || expectedName
        DiscoveryTestCoreGrailsPlugin      || 'discoveryTestCore'
        DiscoveryTestSimpleGrailsPlugin    || 'discoveryTestSimple'
        DiscoveryTestABCGrailsPlugin       || 'discoveryTestABC'
        AnnotatedNonInstantiableDescriptor || 'annotatedPlugin'
    }

    def 'treats plugin metadata with the same name as equal'() {
        given: 'two metadata instances that differ in every field except the name'
        def meta1 = new PluginMetadata(
                'test',
                '1.0',
                '9.0.0',
                String,
                ['a'] as String[],
                [] as String[],
                [:],
                [] as String[],
                [] as String[],
                [] as String[],
                [:],
                true
        )
        def meta2 = new PluginMetadata(
                'test',
                '2.0',
                '9.1.0',
                Integer,
                ['b'] as String[],
                ['c'] as String[],
                ['dep': '1.0'],
                ['d'] as String[],
                [] as String[],
                [] as String[],
                [:],
                false
        )

        expect: 'metadata equality is based on name only, not other attributes'
        meta1 == meta2
        meta1.hashCode() == meta2.hashCode()
    }

    def 'treats plugin metadata with different names as distinct'() {
        given: 'two metadata instances with different names'
        def meta1 = new PluginMetadata(
                'alpha', '1.0', '9.0.0', String,
                [] as String[], [] as String[], [:], [] as String[],
                [] as String[], [] as String[], [:], true
        )
        def meta2 = new PluginMetadata(
                'beta', '1.0', '9.0.0', String,
                [] as String[], [] as String[], [:], [] as String[],
                [] as String[], [] as String[], [:], true
        )

        expect: 'metadata with different names are not equal'
        meta1 != meta2
    }

    def 'includes the plugin name in plugin metadata string output'() {
        given: 'plugin metadata for a named plugin'
        def meta = new PluginMetadata(
                'myPlugin', '1.0', '9.0.0', String,
                [] as String[], [] as String[], [:], [] as String[],
                [] as String[], [] as String[], [:], true
        )

        expect: 'the string form includes the logical plugin name'
        meta.toString() == 'GrailsPluginClassMetadata[myPlugin]'
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

    def 'two independent starter definitions with one id report both origins'() {
        given:
        def roots = (1..2).collect {
            def dir = File.createTempDir()
            writePluginDescriptor(dir, 'conflictingId', "name=conflictingId\nversion=${it}\n")
            dir
        }
        def loader = new URLClassLoader(roots*.toURI()*.toURL() as URL[], (ClassLoader) null)

        when:
        new ClasspathPluginFinder().findClasspathPlugins(loader, '9.0.0')

        then:
        def error = thrown(grails.plugins.exceptions.PluginException)
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
            class DuplicateDescriptorProbeGrailsPlugin {
                def version = "1.0.0"
            }
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
            class RepeatedProbeGrailsPlugin {
                def version = '1.0.0'
            }
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

    def 'reports every missing or incompatible plugin dependency at ERROR'() {
        given: 'a discovery bean with one available and one unresolved plugin'
        def gcl = new GroovyClassLoader()
        def availableDependencyClass = gcl.parseClass('''
            class AvailableDependencyGrailsPlugin {
                def version = "1.0.0"
            }
        ''')
        def unresolvedDependenciesClass = gcl.parseClass('''
            class UnresolvedDependenciesGrailsPlugin {
                def version = "4.0.0"
                def dependsOn = [missingDependency: "1.0.0", availableDependency: "2.0.0"]
            }
        ''')
        def discovery = new DefaultPluginDiscovery(
                [availableDependencyClass, unresolvedDependenciesClass] as Class<?>[]
        ).tap {
            loadPluginsFromClasspath = false
        }

        and: 'configure a logback appender to capture log messages'
        def logCapture = new LogCapture(DefaultPluginDiscovery)

        when: 'the public discovery API initializes the plugin set'
        discovery.init(new StandardEnvironment())

        then: 'the unresolved plugin remains failed and is not loaded'
        with(discovery) {
            hasFailedPlugin('unresolvedDependencies')
            !hasPlugin('unresolvedDependencies')
            pluginsInLoadOrder*.name == ['availableDependency']
        }

        and: 'one message at ERROR identifies the failed plugin and every unresolved dependency'
        def errors = logCapture.events.findAll { it.formattedMessage.contains('unresolvedDependencies') }
        errors.size() == 1
        with(errors[0]) {
            level == Level.ERROR
            formattedMessage.contains('Grails plug-in [unresolvedDependencies] with version [4.0.0] cannot be loaded')
            formattedMessage.contains('dependency [missingDependency] with required version [1.0.0] is missing')
            formattedMessage.contains('dependency [availableDependency] has version [1.0.0] but requires [2.0.0]')
            throwableProxy == null
        }

        cleanup:
        logCapture.close()
    }

    def 'reports a dependency that failed to load as failed rather than missing'() {
        given: 'a plugin whose only dependency itself fails to load due to a genuinely missing dependency'
        def gcl = new GroovyClassLoader()
        def failingDependencyClass = gcl.parseClass('''
            class FailingDependencyGrailsPlugin {
                def version = "1.0.0"
                def dependsOn = [reallyMissing: "1.0.0"]
            }
        ''')
        def dependentClass = gcl.parseClass('''
            class DependentOnFailedGrailsPlugin {
                def version = "1.0.0"
                def dependsOn = [failingDependency: "1.0.0"]
            }
        ''')
        def discovery = new DefaultPluginDiscovery(
                [failingDependencyClass, dependentClass] as Class<?>[]
        ).tap {
            loadPluginsFromClasspath = false
        }

        and: 'configure a logback appender to capture log messages'
        def logCapture = new LogCapture(DefaultPluginDiscovery)

        when:
        discovery.init(new StandardEnvironment())

        then: 'both plugins end up failed'
        with(discovery) {
            hasFailedPlugin('failingDependency')
            hasFailedPlugin('dependentOnFailed')
        }

        and: 'the missing root cause is reported as missing'
        def rootCause = logCapture.events.find { it.formattedMessage.contains('[failingDependency]') }
        rootCause.level == Level.ERROR
        rootCause.formattedMessage.contains('dependency [reallyMissing] with required version [1.0.0] is missing')

        and: 'the plugin depending on the failed plugin reports it as failed, not missing'
        def dependent = logCapture.events.find { it.formattedMessage.contains('[dependentOnFailed]') }
        dependent.level == Level.ERROR
        dependent.formattedMessage.contains(
                'dependency [failingDependency] with required version [1.0.0] failed to load'
        )
        !dependent.formattedMessage.contains('is missing')

        cleanup:
        logCapture.close()
    }

    def 'reports a dependency that is still pending load together with the version that was found'() {
        given: 'a plugin that requires a newer version of a dependency which is itself still waiting on a missing plugin'
        def gcl = new GroovyClassLoader()
        def dependentClass = gcl.parseClass('''
            class NeedsNewerPendingGrailsPlugin {
                def version = "1.0.0"
                def dependsOn = [pendingDependency: "2.0.0"]
            }
        ''')
        def pendingDependencyClass = gcl.parseClass('''
            class PendingDependencyGrailsPlugin {
                def version = "1.0.0"
                def dependsOn = [reallyMissing: "1.0.0"]
            }
        ''')

        and: 'the dependent plugin is processed before the pending dependency gives up'
        def discovery = new DefaultPluginDiscovery(
                [dependentClass, pendingDependencyClass] as Class<?>[]
        ).tap {
            loadPluginsFromClasspath = false
        }

        and: 'configure a logback appender to capture log messages'
        def logCapture = new LogCapture(DefaultPluginDiscovery)

        when:
        discovery.init(new StandardEnvironment())

        then: 'both plugins end up failed'
        with(discovery) {
            hasFailedPlugin('needsNewerPending')
            hasFailedPlugin('pendingDependency')
        }

        and: 'the dependent plugin reports the pending dependency with the version it found'
        def dependent = logCapture.events.find { it.formattedMessage.contains('[needsNewerPending]') }
        dependent.level == Level.ERROR
        dependent.formattedMessage.contains(
                'dependency [pendingDependency] with required version [2.0.0] is still pending load ' +
                        'and only version [1.0.0] was found'
        )
        !dependent.formattedMessage.contains('is missing')

        cleanup:
        logCapture.close()
    }
}

class DiscoveryTestCoreGrailsPlugin {
    def version = '1.0'
}

class DiscoveryTestSimpleGrailsPlugin {
    def version = '1.0'
}

class DiscoveryTestABCGrailsPlugin {
    def version = '1.0'
}

@GrailsPluginDescriptor(
        name = 'annotated-plugin',
        version = '3.2.1',
        grailsVersion = '9.0.0 > *',
        loadAfter = ['core', 'spring-security-core'],
        loadBefore = ['controllers'],
        dependsOn = ['i18n:9.0.0 > *'],
        evict = ['legacy-plugin'],
        observe = ['services']
)
class AnnotatedNonInstantiableDescriptor {
    AnnotatedNonInstantiableDescriptor() {
        throw new AssertionError('Constructor must not be invoked when reading @GrailsPluginDescriptor metadata')
    }
}

@GrailsPluginDescriptor(
        name = 'edge-annotated',
        version = '1.0.0',
        dependsOn = [':1.0', ' :2.0', 'i18n'],
        observe = ['spring-security-core', '*', ' ']
)
class EdgeAnnotatedDescriptor {
}
