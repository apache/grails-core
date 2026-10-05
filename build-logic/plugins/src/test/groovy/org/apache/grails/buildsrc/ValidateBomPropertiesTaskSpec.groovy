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
package org.apache.grails.buildsrc

import java.nio.file.Path

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Runs {@code validateBomProperties} against a BOM whose parent lives in a local Maven repository:
 *
 * <pre>
 * test:parent-bom:1.0
 *   test:lib-a       ${lib-a.version}  = 1.0
 *   test:lib-b       ${lib-b.version}  = 2.0
 *   test:lib-literal 5.0
 *   ${project.groupId}:family-bom  ${family-bom.version} = 3.0, imported
 *     test:family-core ${family.version} - declared by its parent POM, test:family-parent:3.0
 *     test:family-extra ${project.version}
 *     ${project.groupId}:family-api ${family.version}
 *     test:lib-a       ${family.version} - loses to the parent BOM's own entry
 * </pre>
 */
class ValidateBomPropertiesTaskSpec extends Specification {

    @TempDir
    Path testProjectDir

    def setup() {
        writePom('test', 'parent-bom', '1.0', '''
            <properties>
                <lib-a.version>1.0</lib-a.version>
                <lib-b.version>2.0</lib-b.version>
                <family-bom.version>3.0</family-bom.version>
            </properties>
            <dependencyManagement>
                <dependencies>
                    <dependency><groupId>test</groupId><artifactId>lib-a</artifactId><version>${lib-a.version}</version></dependency>
                    <dependency><groupId>test</groupId><artifactId>lib-b</artifactId><version>${lib-b.version}</version></dependency>
                    <dependency><groupId>test</groupId><artifactId>lib-literal</artifactId><version>5.0</version></dependency>
                    <dependency>
                        <groupId>${project.groupId}</groupId><artifactId>family-bom</artifactId><version>${family-bom.version}</version>
                        <type>pom</type><scope>import</scope>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        ''')
        writePom('test', 'family-parent', '3.0', '''
            <properties>
                <family.version>3.0</family.version>
            </properties>
        ''')
        writePom('test', 'family-bom', '3.0', '''
            <dependencyManagement>
                <dependencies>
                    <dependency><groupId>test</groupId><artifactId>family-core</artifactId><version>${family.version}</version></dependency>
                    <dependency><groupId>test</groupId><artifactId>family-extra</artifactId><version>${project.version}</version></dependency>
                    <dependency><groupId>${project.groupId}</groupId><artifactId>family-api</artifactId><version>${family.version}</version></dependency>
                    <dependency><groupId>test</groupId><artifactId>lib-a</artifactId><version>${family.version}</version></dependency>
                </dependencies>
            </dependencyManagement>
        ''', '<parent><groupId>test</groupId><artifactId>family-parent</artifactId><version>3.0</version></parent>')
        testProjectDir.resolve('settings.gradle').toFile().text = "rootProject.name = 'test-bom'"
    }

    def "passes when every override reuses the parent's property name with a different version"() {
        given:
        writeBom(['test:lib-a:1.1': 'lib-a.version', 'test:family-core:3.1': 'family-bom.version'])

        when:
        BuildResult result = run()

        then:
        result.task(':validateBomProperties').outcome == TaskOutcome.SUCCESS
    }

    def "fails when an override is declared under a different property name"() {
        given:
        writeBom(['test:lib-a:1.1': 'my-lib-a.version'])

        when:
        BuildResult result = runAndFail()

        then:
        result.task(':validateBomProperties').outcome == TaskOutcome.FAILED
        result.output.contains("BOM property validation failed for project 'test-bom'.")
        result.output.contains('Parent BOMs: test:parent-bom:1.0')
        result.output.contains('${my-lib-a.version} should be ${lib-a.version}')
        result.output.contains('      test:lib-a')
    }

    def "a module managed through an imported BOM takes the property the parent imports that BOM with"() {
        given: "an override named after the property the imported BOM uses internally"
        writeBom(['test:family-core:3.1': 'family.version'])

        when:
        BuildResult result = runAndFail()

        then:
        result.output.contains('${family.version} should be ${family-bom.version}')
        result.output.contains('      test:family-core')
    }

    def "an import of a BOM the parent also imports is checked like any other override"() {
        given:
        writeBom([:], ['test:family-bom:3.1': 'family.version'])

        when:
        BuildResult result = runAndFail()

        then:
        result.output.contains('${family.version} should be ${family-bom.version}')
        result.output.contains('      test:family-bom')
    }

    def "fails when a pin repeats the version the parent manages"() {
        given:
        writeBom(['test:lib-b:2.0': 'lib-b.version'])

        when:
        BuildResult result = runAndFail()

        then:
        result.output.contains('These versions repeat the version the parent BOM already manages')
        result.output.contains('lib-b.version = 2.0')
        !result.output.contains('should be')
    }

    def "resolves an imported BOM's version through the properties its own parent POM declares"() {
        given: "family.version, which sets family-core's version, only exists on test:family-parent"
        writeBom(['test:family-core:3.0': 'family-bom.version'])

        when:
        BuildResult result = runAndFail()

        then:
        result.output.contains('family-bom.version = 3.0')
        result.output.contains('      test:family-core')
    }

    def "resolves a version written as a Maven built-in such as project.version"() {
        given:
        writeBom(['test:family-extra:3.0': 'family-bom.version'])

        when:
        BuildResult result = runAndFail()

        then:
        result.output.contains('family-bom.version = 3.0')
        result.output.contains('      test:family-extra')
    }

    def "resolves coordinates written as a Maven built-in such as project.groupId"() {
        given: "test:family-bom writes family-api's group, and test:parent-bom family-bom's, as project.groupId"
        writeBom(['test:family-api:3.0': 'family.version'])

        when:
        BuildResult result = runAndFail()

        then:
        result.output.contains('${family.version} should be ${family-bom.version}')
        result.output.contains('family.version = 3.0')
        result.output.contains('      test:family-api')
    }

    def "resolves the published POM's own coordinates written as a Maven built-in"() {
        given:
        writeBom(['test:lib-a:1.1': 'my-lib-a.version'], [:], '''
            publishing.publications.maven.pom.withXml { xml ->
                xml.asNode().dependencyManagement.dependencies.dependency.each { Node dependency ->
                    dependency.groupId[0].value = '${project.groupId}'
                }
            }
        ''')

        when:
        BuildResult result = runAndFail()

        then:
        result.output.contains('${my-lib-a.version} should be ${lib-a.version}')
        result.output.contains('      test:lib-a')
    }

    def "fails when a parent BOM manages a module whose coordinates or version cannot be resolved"() {
        given:
        writePom('test', 'broken-bom', '1.0', '''
            <dependencyManagement>
                <dependencies>
                    <dependency><groupId>test</groupId><artifactId>lib-a</artifactId><version>1.0</version></dependency>
                    <dependency><groupId>${unknown.group}</groupId><artifactId>lib-b</artifactId><version>2.0</version></dependency>
                </dependencies>
            </dependencyManagement>
        ''')
        writeBom(['test:lib-a:1.1': 'lib-a.version'], [:], "ext.parentBoms = ['test:broken-bom:1.0']")

        when:
        BuildResult result = runAndFail()

        then: "the entry is reported rather than silently left unchecked"
        result.output.contains('Cannot resolve ${unknown.group}:lib-b:2.0, managed by test:broken-bom:1.0')
    }

    def "the parent's own entry for a module wins over the entry of a BOM it imports"() {
        given: "test:family-bom also manages test:lib-a, at 3.0 under family.version"
        writeBom(['test:lib-a:3.0': 'lib-a.version'])

        when:
        BuildResult result = run()

        then: "lib-a is compared with the parent's 1.0 under lib-a.version, so nothing is reported"
        result.task(':validateBomProperties').outcome == TaskOutcome.SUCCESS
    }

    def "fails when a module the parent manages is overridden with a literal version"() {
        given:
        writeBom(['test:lib-a:1.1': null])

        when:
        BuildResult result = runAndFail()

        then:
        result.output.contains('the literal version 1.1 should be ${lib-a.version}')
    }

    def "a module the parent writes literally is moved by the property the parent is imported with"() {
        given:
        writeBom(['test:lib-literal:5.1': 'lib-literal.version'])

        when:
        BuildResult result = runAndFail()

        then: "renaming the pin to that property would only repeat the parent's version, so no rename is suggested"
        result.output.contains('These versions override a version the parent BOM writes literally')
        result.output.contains('${lib-literal.version}, where only ${parent-bom.version} moves the parent\'s version')
        result.output.contains('      test:lib-literal')
        result.output.contains('Remove the pin from dependencies.gradle')
        !result.output.contains('should be')
        !result.output.contains('Rename the version key')
    }

    def "documented exemptions suppress the matching failures, by property or by module"() {
        given:
        writeBom(['test:lib-a:1.1': 'my-lib-a.version', 'test:lib-b:2.0': 'lib-b.version'], [:], '''
            ext.bomPropertyNameExemptions = ['my-lib-a.version']
            ext.bomRedundantVersionExemptions = ['test:lib-b']
        ''')

        when:
        BuildResult result = run()

        then:
        result.task(':validateBomProperties').outcome == TaskOutcome.SUCCESS
    }

    def "fails when a version property the BOM owns is used by no entry it publishes"() {
        given:
        writeBom(['test:lib-a:1.1': 'lib-a.version'], [:], """
            ext.bomVersionProperties = ['lib-a.version': '1.1', 'orphan.version': '9.9']
        """)

        when:
        BuildResult result = runAndFail()

        then:
        result.output.contains('These version properties are defined for this BOM, but no entry it publishes uses them')
        result.output.contains('orphan.version = 9.9')
        !result.output.contains('lib-a.version = 1.1')
        !result.output.contains('Parent BOMs:')
    }

    def "checks the version properties the BOM owns even when it names no parent BOM"() {
        given:
        writeBom(['test:lib-a:1.1': 'my-lib-a.version'], [:], """
            ext.parentBoms = []
            ext.bomVersionProperties = ['orphan.version': '9.9']
        """)

        when:
        BuildResult result = runAndFail()

        then: "the unused property is reported, and the parent rules are not applied"
        result.output.contains('orphan.version = 9.9')
        !result.output.contains('should be')
    }

    def "a documented exemption lets an owned version property go unpublished"() {
        given:
        writeBom(['test:lib-a:1.1': 'lib-a.version'], [:], """
            ext.bomVersionProperties = ['lib-a.version': '1.1', 'orphan.version': '9.9']
            ext.bomUnusedVersionExemptions = ['orphan.version']
        """)

        when:
        BuildResult result = run()

        then:
        result.task(':validateBomProperties').outcome == TaskOutcome.SUCCESS
    }

    def "finds nothing to check when the BOM names neither parent BOMs nor the version properties it owns"() {
        given:
        writeBom(['test:lib-a:1.1': 'my-lib-a.version'], [:], 'ext.parentBoms = []')

        when:
        BuildResult result = run()

        then:
        result.task(':validateBomProperties').outcome == TaskOutcome.SUCCESS
    }

    def "skipBomPropertyValidation opts the project out: #optOut"() {
        given: "a BOM that fails every rule"
        writeBom(['test:lib-a:1.1': 'my-lib-a.version', 'test:lib-b:2.0': 'lib-b.version'], [:], """
            ext.bomVersionProperties = ['orphan.version': '9.9']
            ${buildScript}
        """)

        when:
        BuildResult result = runner(arguments).build()

        then:
        result.task(':validateBomProperties').outcome == TaskOutcome.SUCCESS

        where:
        optOut                                  | arguments                             | buildScript
        '-PskipBomPropertyValidation'           | ['-PskipBomPropertyValidation']       | ''
        '-PskipBomPropertyValidation=true'      | ['-PskipBomPropertyValidation=true']  | ''
        'ext.skipBomPropertyValidation = true'  | []                                    | 'ext.skipBomPropertyValidation = true'
    }

    def "still validates with #arguments"() {
        given:
        writeBom(['test:lib-a:1.1': 'my-lib-a.version'])

        when:
        BuildResult result = runner(arguments).buildAndFail()

        then:
        result.output.contains('${my-lib-a.version} should be ${lib-a.version}')

        where: "an explicit false, or the opt-out that belongs to validateDependencyVersions"
        arguments << [['-PskipBomPropertyValidation=false'], ['-PskipDependencyValidation']]
    }

    private BuildResult run() {
        runner().build()
    }

    private BuildResult runAndFail() {
        runner().buildAndFail()
    }

    private GradleRunner runner(List<String> arguments = []) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(['validateBomProperties', '--stacktrace'] + arguments)
                .withPluginClasspath()
    }

    /**
     * Writes a BOM that imports test:parent-bom:1.0 and declares the given constraints and platform
     * imports. It publishes each version as the given property, the way the Grails BOMs do, or
     * literally when the property is {@code null}.
     */
    private void writeBom(Map<String, String> constraints, Map<String, String> platforms = [:], String extra = '') {
        Map<String, String> versionProperties = ['test:parent-bom': 'parent-bom.version']
        (constraints + platforms).each { String coordinates, String property ->
            if (property) {
                versionProperties.put(coordinates.substring(0, coordinates.lastIndexOf(':')), property)
            }
        }
        String constraintLines = constraints.keySet().collect { "        api '${it}'" }.join('\n')
        String platformLines = platforms.keySet().collect { "    api platform('${it}')" }.join('\n')
        String propertyLines = versionProperties.collect { String key, String property -> "    '${key}': '${property}'," }.join('\n')

        testProjectDir.resolve('build.gradle').toFile().text = """
plugins {
    id 'java-platform'
    id 'maven-publish'
    id 'org.apache.grails.buildsrc.bom-property-validator'
}

group = 'test'
version = '1.0'

repositories {
    maven { url = uri('repo') }
}

javaPlatform {
    allowDependencies()
}

ext.parentBoms = ['test:parent-bom:1.0']

dependencies {
    api platform('test:parent-bom:1.0')
${platformLines}
    constraints {
${constraintLines}
    }
}

Map<String, String> versionProperties = [
${propertyLines}
]

publishing {
    publications {
        maven(MavenPublication) {
            from components.javaPlatform
            pom.withXml { xml ->
                Node root = xml.asNode()
                Node propertiesNode = root.appendNode('properties')
                root.dependencyManagement.dependencies.dependency.each { Node dependency ->
                    String property = versionProperties[dependency.groupId.text() + ':' + dependency.artifactId.text()]
                    if (property) {
                        propertiesNode.appendNode(property, dependency.version.text())
                        dependency.version[0].value = '\${' + property + '}'
                    }
                }
            }
        }
    }
}

${extra}
"""
    }

    private void writePom(String groupId, String artifactId, String version, String body, String parent = '') {
        File pom = testProjectDir.resolve("repo/${groupId}/${artifactId}/${version}/${artifactId}-${version}.pom").toFile()
        pom.parentFile.mkdirs()
        pom.text = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    ${parent}
    <groupId>${groupId}</groupId>
    <artifactId>${artifactId}</artifactId>
    <version>${version}</version>
    <packaging>pom</packaging>
    ${body}
</project>
"""
    }
}
