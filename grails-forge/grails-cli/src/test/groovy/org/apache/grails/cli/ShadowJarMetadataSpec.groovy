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
package org.apache.grails.cli

import java.util.jar.JarFile

import groovy.json.JsonSlurper
import spock.lang.Specification

class ShadowJarMetadataSpec extends Specification {

    // the components the MCP Java SDK brings into the jar, with the SPDX id of the license their POM declares
    static final Map<String, String> MCP_SDK_LICENSES = [
            'io.modelcontextprotocol.sdk:mcp'                  : 'MIT',
            'io.modelcontextprotocol.sdk:mcp-core'             : 'MIT',
            'io.modelcontextprotocol.sdk:mcp-json-jackson3'    : 'MIT',
            'org.reactivestreams:reactive-streams'             : 'MIT-0',
            'io.projectreactor:reactor-core'                   : 'Apache-2.0',
            'com.networknt:json-schema-validator'              : 'Apache-2.0',
            'com.ethlo.time:itu'                               : 'Apache-2.0',
            'org.snakeyaml:snakeyaml-engine'                   : 'Apache-2.0',
            'tools.jackson.core:jackson-core'                  : 'Apache-2.0',
            'tools.jackson.core:jackson-databind'              : 'Apache-2.0',
            'tools.jackson.dataformat:jackson-dataformat-yaml' : 'Apache-2.0',
    ]

    // the heading the LICENSE of the jar gives each license that a bundled jar does not carry itself
    static final String MIT_COMPONENTS = 'The following components are licensed under the MIT License:'
    static final String MIT_0_COMPONENTS = 'The following component is licensed under the MIT No Attribution License (MIT-0):'

    void "no third-party component of the published jar is a pre-release, and Reactor is the release the BOM manages"() {
        given:
        List<Map> components = sbomComponents('grails.cli.all.jar')

        expect: 'the BOM ranks reactor-core 3.9.0 above the 3.9.0-RC1 the MCP Java SDK asks for'
        components.find { Map component -> component.group == 'io.projectreactor' && component.name == 'reactor-core' }?.version == '3.9.0'

        and: 'Grails itself is a snapshot between releases; nothing else is'
        components.findAll { Map component -> !(component.group as String).startsWith('org.apache.grails') }
                .findAll { Map component -> (component.version as String) =~ /(?i)[.-](rc|m)\d+([.-]|$)|-SNAPSHOT$/ }
                .collect { Map component -> "${component.group}:${component.name}:${component.version}".toString() } == []
    }

    void "the components of the MCP Java SDK are in the SBOM of the jar, each with the SPDX id of its license"() {
        given:
        Map<String, Map> components = sbomComponents('grails.cli.all.jar').collectEntries { Map component ->
            ["${component.group}:${component.name}".toString(), component]
        }

        expect:
        MCP_SDK_LICENSES.findAll { String coordinates, String id ->
            components[coordinates] == null || (components[coordinates].licenses as List<Map>)*.license*.id != [id]
        } == [:]
    }

    void "the LICENSE of the jar has, once each, the MIT and MIT-0 licenses of the components whose jars have no LICENSE"() {
        given:
        String license = entryText('grails.cli.all.jar', 'META-INF/LICENSE')

        expect: 'the MCP Java SDK, under the MIT License with its own copyright'
        license.count(MIT_COMPONENTS) == 1
        license.substring(license.indexOf(MIT_COMPONENTS)).readLines().subList(0, 8) == [
                MIT_COMPONENTS,
                '',
                '    io.modelcontextprotocol.sdk:mcp',
                '    io.modelcontextprotocol.sdk:mcp-core',
                '    io.modelcontextprotocol.sdk:mcp-json-jackson3',
                '',
                'MIT License',
                '',
        ]
        license.contains('Copyright (c) 2025 the original author or authors.')

        and: 'reactive-streams, under the MIT No Attribution License'
        license.count(MIT_0_COMPONENTS) == 1
        license.substring(license.indexOf(MIT_0_COMPONENTS)).readLines().subList(0, 7) == [
                MIT_0_COMPONENTS,
                '',
                '    org.reactivestreams:reactive-streams',
                '',
                'MIT No Attribution',
                '',
                'Copyright 2014 Reactive Streams',
        ]
    }

    void "the NOTICE of the jar has the NOTICE that JSON Schema Validator has in its repository and not in its jar"() {
        given:
        String notice = entryText('grails.cli.all.jar', 'META-INF/NOTICE')

        expect:
        notice.count('JSON Schema Validator\nCopyright (c) 2016 and onwards Network New Technologies Inc.\n') == 1
        notice.contains('Apache Commons Validator\nCopyright 2001-2023 The Apache Software Foundation\n')

        and: 'it is merged with the others, and is no entry of its own'
        new JarFile(new File(System.getProperty('grails.cli.all.jar'))).withCloseable { JarFile jar ->
            jar.getJarEntry('META-INF/NOTICE-json-schema-validator') == null
        }
    }

    void "the #stage jar has usable merged Groovy extensions without a build-time comment"() {
        given:
        File file = new File(System.getProperty(property))
        String descriptor = new JarFile(file).withCloseable { JarFile jar ->
            assert jar.getJarEntry('META-INF/services/org.codehaus.groovy.runtime.ExtensionModule') == null
            jar.getInputStream(jar.getJarEntry('META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule')).getText('ISO-8859-1')
        }
        Properties extensions = new Properties()
        extensions.load(new StringReader(descriptor))

        expect:
        extensions.stringPropertyNames() == ['moduleName', 'moduleVersion', 'extensionClasses', 'staticExtensionClasses'] as Set
        extensions.getProperty('moduleName')
        extensions.getProperty('extensionClasses')
        extensions.getProperty('extensionClasses').split(',').contains('org.grails.spring.context.ApplicationContextExtension')
        extensions.getProperty('extensionClasses').split(',').contains('org.apache.groovy.xml.extensions.XmlExtensions')
        !descriptor.contains('\r')
        descriptor.readLines().every { String line -> !line.startsWith('#') }

        where:
        stage         | property
        'intermediate' | 'grails.cli.intermediate.jar'
        'published'    | 'grails.cli.all.jar'
    }

    void "shadow packaging preserves every bundled pack, compiled page and index byte"() {
        given:
        Map<String, byte[]> intermediate = packEntries('grails.cli.intermediate.jar')
        Map<String, byte[]> published = packEntries('grails.cli.all.jar')

        expect:
        intermediate.containsKey('META-INF/grails-forge-bundled/packs')
        intermediate.containsKey('META-INF/grails-forge-pages/index')
        intermediate.keySet().any { String name -> name.startsWith('org/apache/grails/forge/core/pack/pages/') && name.endsWith('.class') }
        intermediate.keySet() == published.keySet()
        intermediate.every { String name, byte[] bytes -> Arrays.equals(bytes, published[name]) }
    }

    private static List<Map> sbomComponents(String property) {
        Map sbom = new JsonSlurper().parseText(entryText(property, 'META-INF/sbom.json')) as Map
        return sbom.components as List<Map>
    }

    private static String entryText(String property, String entry) {
        new JarFile(new File(System.getProperty(property))).withCloseable { JarFile jar ->
            jar.getInputStream(jar.getJarEntry(entry)).getText('UTF-8')
        }
    }

    private static Map<String, byte[]> packEntries(String property) {
        new JarFile(new File(System.getProperty(property))).withCloseable { JarFile jar ->
            jar.entries().toList().findAll { entry ->
                !entry.directory && (entry.name.startsWith('META-INF/grails-forge-') || entry.name.startsWith('org/apache/grails/forge/core/pack/pages/'))
            }.collectEntries { entry -> [entry.name, jar.getInputStream(entry).bytes] }
        }
    }
}
