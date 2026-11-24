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
