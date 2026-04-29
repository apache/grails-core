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

import org.grails.io.support.SpringIOUtils

class PluginDiscoverySpec extends Specification {

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

    def 'returns no plugin descriptors when the class loader has no grails-plugin.xml resources'() {
        given: 'a class loader with no Grails plugin descriptors'
        def emptyClassLoader = new URLClassLoader([] as URL[], (ClassLoader) null)

        when: 'plugin descriptor resources are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(emptyClassLoader)

        then: 'no descriptors are found'
        descriptors.empty
    }

     def 'captures provided class names from plugin descriptor resource elements'() {
         given: 'a grails-plugin.xml with both type and resource elements'
         def pluginXml = '''
             <plugin name='test'>
                 <type>com.example.TestGrailsPlugin</type>
                 <resource>com.example.MyDomainClass</resource>
                 <resource>com.example.MyService</resource>
             </plugin>
         '''
         def tempDir = File.createTempDir()
         def metaInfDir = new File(tempDir, 'META-INF').tap { mkdirs() }
         new File(metaInfDir, 'grails-plugin.xml').text = pluginXml
         def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

         when: 'plugin descriptor resources are scanned'
         def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

         then: 'the descriptor only includes <resource> elements, not <type> elements'
         descriptors.size() == 1
         descriptors[0].providedClasses == ['com.example.MyDomainClass', 'com.example.MyService']
         descriptors[0].resource != null
         descriptors[0].resource.exists()

         cleanup:
         tempDir.deleteDir()
     }

    def 'ignores malformed plugin descriptor XML without failing discovery'() {
        given: 'a classloader that returns a grails-plugin.xml with invalid content'
        def badXml = '<plugin><type>valid.Class</type><broken'
        def tempDir = File.createTempDir()
        def metaInfDir = new File(tempDir, 'META-INF').tap { mkdirs() }
        new File(metaInfDir, 'grails-plugin.xml').text = badXml
        def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

        when: 'plugin descriptor resources are scanned'
        def descriptors = PluginUtils.scanPluginDescriptorResources(classLoader)

        then: 'discovery continues without throwing an exception'
        noExceptionThrown()

        and: 'a non-null result is returned even if parsing fails'
        descriptors != null

        cleanup:
        tempDir.deleteDir()
    }

     def 'extracts plugin class names from descriptor type elements'() {
         given: 'a grails-plugin.xml with known <type> elements'
         def xml = '''
             <plugin name='test'>
                 <type>com.example.AlphaGrailsPlugin</type>
                 <type>com.example.BetaGrailsPlugin</type>
             </plugin>
         '''
         def tempDir = File.createTempDir()
         def metaInfDir = new File(tempDir, 'META-INF').tap { mkdirs() }
         new File(metaInfDir, 'grails-plugin.xml').text = xml
         def classLoader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)

         when: 'plugin descriptors are scanned for class names'
         def classNames = PluginUtils.scanPluginDescriptors(classLoader)

         then: 'returns class names from <type> elements in grails-plugin.xml'
         classNames == ['com.example.AlphaGrailsPlugin', 'com.example.BetaGrailsPlugin']

         cleanup:
         tempDir.deleteDir()
     }

    def 'returns no plugin class names when the class loader has no grails-plugin.xml resources'() {
        given: 'a class loader with no Grails plugin descriptors'
        def emptyClassLoader = new URLClassLoader([] as URL[], (ClassLoader) null)

        when: 'plugin descriptors are scanned for class names'
        def classNames = PluginUtils.scanPluginDescriptors(emptyClassLoader)

        then: 'no plugin class names are found'
        classNames.empty
    }

    @Unroll
    def "derives logical plugin name '#expectedName' from plugin class #pluginClass.simpleName"() {
        expect: 'the logical plugin name matches the Grails naming convention'
        PluginUtils.getLogicalPluginName(pluginClass) == expectedName

        where:
        pluginClass                        || expectedName
        DiscoveryTestCoreGrailsPlugin      || 'discoveryTestCore'
        DiscoveryTestSimpleGrailsPlugin    || 'discoveryTestSimple'
        DiscoveryTestABCGrailsPlugin       || 'discoveryTestABC'
    }

    def 'treats plugin metadata with the same name as equal'() {
        given: 'two metadata instances that differ in every field except the name'
        def meta1 = new PluginMetadata(
                'test',  // name
                '1.0',  // pluginVersion
                '4.0.0',  // grailsVersion
                String,  // pluginClass
                ['a'] as String[],  // loadAfterNames
                [] as String[],  // loadBeforeNames
                [:],  // dependencies
                [] as String[],  // dependsOnNames
                [] as String[],  // evictions
                [] as String[],  // observedPluginNames
                [:],  // environments
                true
        )  // enabled
        def meta2 = new PluginMetadata(
                'test',  // name
                '2.0',  // pluginVersion (different)
                '5.0.0',  // grailsVersion (different)
                Integer,  // pluginClass (different)
                ['b'] as String[],  // loadAfterNames (different)
                ['c'] as String[],  // loadBeforeNames (different)
                ['dep': '1.0'],  // dependencies (different)
                ['d'] as String[],  // dependsOnNames (different)
                [] as String[],  // evictions
                [] as String[],  // observedPluginNames
                [:],  // environments
                false
        )  // enabled (different)

        expect: 'metadata equality is based on name only, not other attributes'
        meta1 == meta2
        meta1.hashCode() == meta2.hashCode()
    }

    def 'treats plugin metadata with different names as distinct'() {
        given: 'two metadata instances with different names'
        def meta1 = new PluginMetadata(
                'alpha',  // name
                '1.0',  // pluginVersion
                '4.0.0',  // grailsVersion
                String,  // pluginClass
                [] as String[],  // loadAfterNames
                [] as String[],  // loadBeforeNames
                [:],  // dependencies
                [] as String[],  // dependsOnNames
                [] as String[],  // evictions
                [] as String[],  // observedPluginNames
                [:],  // environments
                true
        )  // enabled
        def meta2 = new PluginMetadata(
                'beta',  // name (different)
                '1.0',  // pluginVersion
                '4.0.0',  // grailsVersion
                String,  // pluginClass
                [] as String[],  // loadAfterNames
                [] as String[],  // loadBeforeNames
                [:],  // dependencies
                [] as String[],  // dependsOnNames
                [] as String[],  // evictions
                [] as String[],  // observedPluginNames
                [:],  // environments
                true
        )  // enabled

        expect: 'metadata with different names are not equal'
        meta1 != meta2
    }

    def 'includes the plugin name in plugin metadata string output'() {
        given: 'plugin metadata for a named plugin'
        def meta = new PluginMetadata(
                'myPlugin',  // name
                '1.0',  // pluginVersion
                '4.0.0',  // grailsVersion
                String,  // pluginClass
                [] as String[],  // loadAfterNames
                [] as String[],  // loadBeforeNames
                [:],  // dependencies
                [] as String[],  // dependsOnNames
                [] as String[],  // evictions
                [] as String[],  // observedPluginNames
                [:],  // environments
                true
        )  // enabled

        expect: 'the string form includes the logical plugin name'
        meta.toString() == 'GrailsPluginClassMetadata[myPlugin]'
    }

    def 'parses plugin and provided class names from type and resource elements'() {
        given: 'plugin XML containing both type and resource elements'
        def xml = '''
            <plugin name='test'>
                <type>com.example.TestGrailsPlugin</type>
                <type>com.example.OtherGrailsPlugin</type>
                <resource>com.example.SomeClass</resource>
                <resource>com.example.AnotherClass</resource>
            </plugin>
        '''

        def handler = new PluginXmlHandler()
        def parser = SpringIOUtils.newSAXParser()

        when: 'the XML is parsed'
        parser.parse(new ByteArrayInputStream(xml.bytes), handler)

        then: 'the handler captures the declared plugin and provided class names'
        handler.pluginClassNames == ['com.example.TestGrailsPlugin', 'com.example.OtherGrailsPlugin']
        handler.providedClasses == ['com.example.SomeClass', 'com.example.AnotherClass']
    }

    def 'returns no plugin or provided classes when plugin XML has no matching elements'() {
        given: 'plugin XML without type or resource elements'
        def xml = '<plugin name="empty"></plugin>'
        def handler = new PluginXmlHandler()
        def parser = SpringIOUtils.newSAXParser()

        when: 'the XML is parsed'
        parser.parse(new ByteArrayInputStream(xml.bytes), handler)

        then: 'both collected lists remain empty'
        handler.pluginClassNames.empty
        handler.providedClasses.empty
    }

    def 'trims surrounding whitespace from plugin class names in XML elements'() {
        given: 'plugin XML with whitespace around the type element content'
        def xml = '''
            <plugin name='test'>
                <type>
                    com.example.TestGrailsPlugin
                </type>
            </plugin>
        '''

        def handler = new PluginXmlHandler()
        def parser = SpringIOUtils.newSAXParser()

        when: 'the XML is parsed'
        parser.parse(new ByteArrayInputStream(xml.bytes), handler)

        then: 'the extracted plugin class name is trimmed'
        handler.pluginClassNames == ['com.example.TestGrailsPlugin']
    }

    def 'exposes the expected plugin utility constants'() {
        expect: 'the plugin utility constants keep their documented values'
        with(PluginUtils) {
            PLUGIN_XML_PATTERN == 'META-INF/grails-plugin.xml'
            PLUGIN_YML_CONFIG == 'plugin.yml'
            PLUGIN_GROOVY_CONFIG == 'plugin.groovy'
            DEFAULT_CONFIG_IGNORE_LIST == ['dataSource', 'hibernate']
        }
    }
}

// Test fixture plugin classes for GrailsPluginDiscoverySpec

class DiscoveryTestCoreGrailsPlugin {
    def version = '1.0'
}

class DiscoveryTestSimpleGrailsPlugin {
    def version = '1.0'
}

class DiscoveryTestABCGrailsPlugin {
    def version = '1.0'
}
