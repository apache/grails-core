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
package org.apache.grails.security.cli

import java.util.jar.JarFile

import groovy.json.JsonSlurper
import groovy.xml.XmlSlurper

import spock.lang.Specification
import spock.lang.Unroll

import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * Where the tools of the Spring Security plugins are, as the build publishes them. {@code s2-quickstart} is the one
 * tool the build runs, without starting the application, and is in {@code grails-spring-security-cli}; every other
 * tool works on the files of the project only, and is a standalone tool in the {@code -cli-standalone} companion of
 * its plugin, which has the templates it renders and depends on the tool API and not on the plugin. Each {@code -cli}
 * brings its plugin and the standalone tools of the plugin, and has no tool classes when its plugin has no other tool.
 *
 * <p>The build hands over the jar, the POM and the module metadata of each companion of the four plugins as it
 * publishes them.</p>
 */
class SecurityToolsLocationSpec extends Specification {

    private static final String GROUP = 'org.apache.grails'

    @Unroll
    def "#plugin-cli-standalone has the standalone tools #tools of #plugin"() {
        given:
        File jar = companion(plugin, 'cliStandalone', 'jar')

        when:
        Map<String, ToolDescriptor.Described> registered = registeredTools(jar)

        then:
        registered.keySet().sort() == tools
        registered.values().every { ToolDescriptor.Described tool -> tool.target == ToolDescriptor.TARGET_STANDALONE }

        and: 'each class it registers is in the jar, and is a StandaloneTool'
        withClassesOf(jar) { ClassLoader loader, List<String> classes ->
            classes.every { String className -> StandaloneTool.isAssignableFrom(loader.loadClass(className)) }
        }

        where:
        plugin                          | tools
        'grails-spring-security'        | ['s2-create-persistent-token', 's2-create-role-hierarchy-entry']
        'grails-spring-security-acl'    | ['s2-create-acl-domains']
        'grails-spring-security-oauth2' | ['init-oauth2']
        'grails-spring-security-ui'     | ['s2ui-create-challenge-questions', 's2ui-override']
    }

    def "grails-spring-security-cli has s2-quickstart, an application tool the build runs without starting the application"() {
        given:
        File jar = companion('grails-spring-security', 'cli', 'jar')

        when:
        Map<String, ToolDescriptor.Described> registered = registeredTools(jar)

        then:
        registered.keySet() == ['s2-quickstart'] as Set
        registered['s2-quickstart'].target == ToolDescriptor.TARGET_APPLICATION
        !registered['s2-quickstart'].startsApplication

        and:
        withClassesOf(jar) { ClassLoader loader, List<String> classes ->
            classes.size() == 1 && ApplicationTool.isAssignableFrom(loader.loadClass(classes[0])) &&
                    !StandaloneTool.isAssignableFrom(loader.loadClass(classes[0]))
        }
    }

    @Unroll
    def "#plugin-cli, of a plugin whose tools are all standalone tools, has no classes"() {
        expect:
        registeredTools(companion(plugin, 'cli', 'jar')).isEmpty()
        entries(companion(plugin, 'cli', 'jar')).every { String entry -> !entry.endsWith('.class') }

        where:
        plugin << ['grails-spring-security-acl', 'grails-spring-security-oauth2', 'grails-spring-security-ui']
    }

    @Unroll
    def "#plugin-cli-standalone has every template of #plugin"() {
        given:
        File templates = new File(directory, 'src/main/templates')
        List<String> expected = []
        templates.eachFileRecurse { File file ->
            if (file.isFile()) {
                expected << 'META-INF/templates/' + templates.toPath().relativize(file.toPath()).toString().replace(File.separator, '/')
            }
        }

        expect:
        !expected.isEmpty()
        entries(companion(plugin, 'cliStandalone', 'jar')).containsAll(expected)

        where:
        plugin                          | directory
        'grails-spring-security'        | '.'
        'grails-spring-security-acl'    | '../acl/plugin'
        'grails-spring-security-oauth2' | '../oauth2/plugin'
        'grails-spring-security-ui'     | '../ui/plugin'
    }

    @Unroll
    def "#plugin-cli-standalone depends on the tool API and not on #plugin, in its POM and in its module metadata"() {
        given:
        String api = "${GROUP}:grails-core-cli"
        String itsPlugin = "${GROUP}:${plugin}"

        expect:
        pomDependencies(companion(plugin, 'cliStandalone', 'pom')).contains(api)
        !pomDependencies(companion(plugin, 'cliStandalone', 'pom')).contains(itsPlugin)
        moduleDependencies(companion(plugin, 'cliStandalone', 'module')).contains(api)
        !moduleDependencies(companion(plugin, 'cliStandalone', 'module')).contains(itsPlugin)

        where:
        plugin << ['grails-spring-security', 'grails-spring-security-acl', 'grails-spring-security-oauth2', 'grails-spring-security-ui']
    }

    @Unroll
    def "#plugin-cli depends on #plugin, the tool API and #plugin-cli-standalone, in its POM and in its module metadata"() {
        given:
        List<String> expected = [plugin, 'grails-core-cli', "${plugin}-cli-standalone"].collect { "${GROUP}:${it}".toString() }

        expect:
        pomDependencies(companion(plugin, 'cli', 'pom')).containsAll(expected)
        moduleDependencies(companion(plugin, 'cli', 'module')).containsAll(expected)

        where:
        plugin << ['grails-spring-security', 'grails-spring-security-acl', 'grails-spring-security-oauth2', 'grails-spring-security-ui']
    }

    /**
     * @param plugin the project of the plugin
     * @param publication the publication of the companion, {@code cli} or {@code cliStandalone}
     * @param kind {@code jar}, {@code pom} or {@code module}
     * @return the file the build made for it
     */
    private static File companion(String plugin, String publication, String kind) {
        String path = System.getProperty("tools.${plugin}.${publication}.${kind}")
        assert path: "the build hands over the ${kind} of the publication ${publication} of ${plugin}"
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
     * Gives the check the classes a jar registers as tools, each of which is in the jar, with a class loader that
     * loads them from the jar and the tool API from the class path of the spec.
     */
    private static boolean withClassesOf(File jar, Closure<Boolean> check) {
        List<String> classes = new JarFile(jar).withCloseable { JarFile archive ->
            List<String> registered = classesRegisteredIn(archive)
            registered.each { String className -> assert archive.getEntry(className.replace('.', '/') + '.class') != null }
            registered
        }
        URLClassLoader loader = new URLClassLoader([jar.toURI().toURL()] as URL[], SecurityToolsLocationSpec.classLoader)
        try {
            return check.call(loader, classes)
        } finally {
            loader.close()
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
