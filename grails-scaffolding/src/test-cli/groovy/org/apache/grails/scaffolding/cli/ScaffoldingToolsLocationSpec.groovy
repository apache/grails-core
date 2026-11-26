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
package org.apache.grails.scaffolding.cli

import java.util.jar.JarFile

import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper

import spock.lang.Specification

import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * Where the tools of the scaffolding plugin are, as the build publishes them: every one is a standalone tool in
 * {@code grails-scaffolding-cli-standalone}, which has the templates they render and depends on the tool API and
 * not on the plugin; {@code grails-scaffolding-cli} has no tools of its own and brings the plugin and that companion.
 *
 * <p>The build hands over the jar, the POM and the module metadata of each companion as it publishes them.</p>
 */
class ScaffoldingToolsLocationSpec extends Specification {

    private static final String GROUP = 'org.apache.grails'

    private static final List<String> TOOLS = ['create-scaffold-controller', 'create-scaffold-service', 'generate-all',
                                               'generate-async-controller', 'generate-controller', 'generate-scaffold-all',
                                               'generate-service', 'generate-views', 'install-templates']

    def "every tool of the plugin is a standalone tool in grails-scaffolding-cli-standalone"() {
        when:
        Map<String, ToolDescriptor.Described> tools = registeredTools(companion('cliStandalone', 'jar'))

        then:
        tools.keySet().sort() == TOOLS
        tools.values().every { ToolDescriptor.Described tool -> tool.target == ToolDescriptor.TARGET_STANDALONE }

        and: 'each class it registers is in the jar, and is a StandaloneTool'
        classesOf(companion('cliStandalone', 'jar')).every { String className ->
            StandaloneTool.isAssignableFrom(Class.forName(className, false, getClass().classLoader))
        }
    }

    def "grails-scaffolding-cli has no tool of its own"() {
        expect:
        registeredTools(companion('cli', 'jar')).isEmpty()
        entries(companion('cli', 'jar')).every { String entry -> !entry.endsWith('.class') }
    }

    def "grails-scaffolding-cli-standalone has every template the tools render"() {
        given:
        List<String> templates = new File('src/main/templates/scaffolding').list().toList()

        expect:
        templates.size() > 10
        entries(companion('cliStandalone', 'jar')).containsAll(templates.collect { "META-INF/templates/scaffolding/${it}".toString() })
    }

    def "grails-scaffolding-cli-standalone depends on the tool API and not on the plugin, in its POM and in its module metadata"() {
        expect:
        pomDependencies(companion('cliStandalone', 'pom')).contains("${GROUP}:grails-core-cli".toString())
        !pomDependencies(companion('cliStandalone', 'pom')).contains("${GROUP}:grails-scaffolding".toString())
        moduleDependencies(companion('cliStandalone', 'module')).contains("${GROUP}:grails-core-cli".toString())
        !moduleDependencies(companion('cliStandalone', 'module')).contains("${GROUP}:grails-scaffolding".toString())
    }

    def "grails-scaffolding-cli depends on the plugin, the tool API and grails-scaffolding-cli-standalone, in its POM and in its module metadata"() {
        given:
        List<String> expected = ['grails-scaffolding', 'grails-core-cli', 'grails-scaffolding-cli-standalone'].collect { "${GROUP}:${it}".toString() }

        expect:
        pomDependencies(companion('cli', 'pom')).containsAll(expected)
        moduleDependencies(companion('cli', 'module')).containsAll(expected)
    }

    /**
     * @param publication the publication of the companion, {@code cli} or {@code cliStandalone}
     * @param kind {@code jar}, {@code pom} or {@code module}
     * @return the file the build made for it
     */
    private static File companion(String publication, String kind) {
        String path = System.getProperty("tools.${publication}.${kind}")
        assert path: "the build hands over the ${kind} of the publication ${publication}"
        File file = new File(path)
        assert file.isFile()
        file
    }

    /**
     * @return the tools a jar registers, by name, as their descriptors in the jar describe them
     */
    private static Map<String, ToolDescriptor.Described> registeredTools(File jar) {
        new JarFile(jar).withCloseable { JarFile archive ->
            classesRegisteredIn(archive).collectEntries { String className ->
                def descriptor = archive.getEntry(ToolDescriptor.location(className))
                assert descriptor != null: "${jar.name} describes ${className}"
                ToolDescriptor.Described described = ToolDescriptor.read(className, archive.getInputStream(descriptor).bytes)
                [(described.name): described]
            }
        }
    }

    /**
     * @return the classes a jar registers as tools, each of which is in the jar
     */
    private static List<String> classesOf(File jar) {
        new JarFile(jar).withCloseable { JarFile archive ->
            List<String> classes = classesRegisteredIn(archive)
            classes.each { String className -> assert archive.getEntry(className.replace('.', '/') + '.class') != null }
            classes
        }
    }

    private static List<String> classesRegisteredIn(JarFile archive) {
        def factories = archive.getEntry('META-INF/grails-cli.factories')
        if (factories == null) {
            return []
        }
        Properties registered = new Properties()
        archive.getInputStream(factories).withCloseable { InputStream input -> registered.load(input) }
        ToolDescriptor.FACTORIES_KEYS.collectMany { String key ->
            (registered.getProperty(key) ?: '').tokenize(',')*.trim().findAll()
        }
    }

    private static List<String> entries(File jar) {
        new JarFile(jar).withCloseable { JarFile archive -> archive.entries()*.name }
    }

    private static List<String> pomDependencies(File pom) {
        def project = new XmlSlurper().parse(pom)
        project.dependencies.dependency.collect { "${it.groupId.text()}:${it.artifactId.text()}".toString() }
    }

    private static List<String> moduleDependencies(File module) {
        def metadata = new JsonSlurper().parse(module)
        metadata.variants.collectMany { variant -> (variant.dependencies ?: []).collect { "${it.group}:${it.module}".toString() } }.unique()
    }
}
