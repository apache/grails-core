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

import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.cyclonedx.Version
import org.cyclonedx.parsers.JsonParser
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class SbomPluginSpec extends Specification {

    @TempDir
    File projectDir

    private static final def LOGGER = Logging.getLogger(SbomPluginSpec)
    private static final String HIBERNATE_COMMONS = 'pkg:maven/org.hibernate.common/hibernate-commons-annotations@5.1.2.Final?type=jar'

    // the license of Hibernate 5 as cyclonedx-core-java 13 reads it from the POM: a name without an SPDX id
    private static List lgplChoice() {
        [[license: [name: 'GNU Library General Public License v2.1 or later', url: 'http://www.opensource.org/licenses/LGPL-2.1']]]
    }

    static class FakeCliArtifactExtension {
        final Property<String> artifactId
        FakeCliArtifactExtension(Property<String> artifactId) { this.artifactId = artifactId }
    }

    private static Project projectWithVersion(String version) {
        Project project = ProjectBuilder.builder().withName('grails-core').build()
        project.extensions.extraProperties.set('projectVersion', version)
        project
    }

    private static void addCliArtifactExtension(Project project, String artifactId) {
        Property<String> prop = project.objects.property(String)
        prop.set(artifactId)
        project.extensions.add('cliArtifact', new FakeCliArtifactExtension(prop))
    }

    void "sbomOutputLocationFor names the file <artifactId>-<version>-sbom.json"() {
        given:
        Project project = projectWithVersion('8.0.0')

        expect:
        SbomPlugin.sbomOutputLocationFor(project, project.provider { 'grails-core' }).get().asFile.name ==
                'grails-core-8.0.0-sbom.json'
    }

    void "cliCompanionArtifactId honours a custom cli artifactId"() {
        given: "a cli-artifact extension configured with a customised companion coordinate"
        Project project = projectWithVersion('8.0.0')
        addCliArtifactExtension(project, 'grails-core-custom-cli')

        expect:
        SbomPlugin.cliCompanionArtifactId(project).get() == 'grails-core-custom-cli'
    }

    void "a custom cli artifactId flows through to the cli sbom file name"() {
        given: "a customised companion coordinate"
        Project project = projectWithVersion('8.0.0')
        addCliArtifactExtension(project, 'grails-core-custom-cli')

        when: "the cli sbom output location is derived from that coordinate"
        def location = SbomPlugin.sbomOutputLocationFor(project, SbomPlugin.cliCompanionArtifactId(project))

        then: "the sbom file name tracks the custom name, not the <project>-cli default"
        location.get().asFile.name == 'grails-core-custom-cli-8.0.0-sbom.json'
    }

    void "a category-X license is permitted when exempted for the sbom component (cli companion)"() {
        expect: "the hibernate 5 LGPL exemption keyed to the dbmigration cli companion is honoured"
        SbomPlugin.pickLicense(LOGGER, 'grails-data-hibernate5-dbmigration',
                'grails-data-hibernate5-dbmigration-cli', HIBERNATE_COMMONS, lgplChoice()) != null
    }

    void "an unexempted category-X license fails naming the sbom component, not just the project"() {
        when: "the same LGPL dependency appears in an sbom component with no matching exemption"
        SbomPlugin.pickLicense(LOGGER, 'grails-data-hibernate5-dbmigration',
                'grails-data-hibernate5-dbmigration', HIBERNATE_COMMONS, lgplChoice())

        then: "the failure identifies the offending sbom component"
        GradleException e = thrown(GradleException)
        e.message.contains('grails-data-hibernate5-dbmigration')
        e.message.contains('LGPL-2.1-or-later')
    }

    void "a license that comes with only its name is recorded under the SPDX id the name stands for"() {
        expect: 'the exempted LGPL dependency is recorded with the id in place of the name'
        SbomPlugin.pickLicense(LOGGER, 'grails-data-hibernate5-core', 'grails-data-hibernate5-core',
                HIBERNATE_COMMONS, lgplChoice()) ==
                [license: [id: 'LGPL-2.1-or-later', url: 'http://www.opensource.org/licenses/LGPL-2.1']]
    }

    void "a license name with no known SPDX id fails the check"() {
        when: 'a dependency comes with a license name that is not looked up'
        SbomPlugin.pickLicense(LOGGER, 'grails-core', 'grails-core',
                'pkg:maven/org.example/example@1.0?type=jar', [[license: [name: 'Example License']]])

        then: 'the failure names the license'
        final e = thrown(GradleException)
        e.message.contains('Could not determine License id')
        e.message.contains('Example License')
    }

    void "antlr 2.7.7 is recorded under the BSD-3-Clause license its POM links to"() {
        expect: 'the BSD-4-Clause that cyclonedx-core-java reads from its license name is replaced'
        SbomPlugin.pickLicense(LOGGER, 'grails-data-hibernate5-core', 'grails-data-hibernate5-core',
                'pkg:maven/antlr/antlr@2.7.7?type=jar', [[license: [id: 'BSD-4-Clause']]]) ==
                [license: [id: 'BSD-3-Clause', url: 'https://opensource.org/license/bsd-3-clause/']]
    }

    void "the license picked for #bomRef is valid in a CycloneDX 1.6 BOM"() {
        given: 'a BOM with one component that has the picked license'
        final bom = [
                bomFormat  : 'CycloneDX',
                specVersion: '1.6',
                version    : 1,
                components : [[
                        type     : 'library',
                        'bom-ref': bomRef,
                        name     : 'dependency',
                        licenses : [SbomPlugin.pickLicense(LOGGER, projectName, projectName, bomRef, choices)],
                ]],
        ]

        expect: 'the CycloneDX schema finds no error in it'
        new JsonParser().validate(JsonOutput.toJson(bom), Version.VERSION_16).empty

        where: 'the license comes from the mapping with an id or a name, is looked up by its name, or is preferred'
        projectName                   | bomRef                                           | choices
        'grails-data-hibernate5-core' | 'pkg:maven/antlr/antlr@2.7.7?type=jar'           | [[license: [id: 'BSD-4-Clause']]]
        'grails-layout'               | 'pkg:maven/opensymphony/sitemesh@2.6.0?type=jar' | [[license: [name: 'OpenSymphony']]]
        'grails-data-hibernate5-core' | HIBERNATE_COMMONS                                | lgplChoice()
        'grails-core'                 | 'pkg:maven/org.example/example@1.0?type=jar'     | [[license: [id: 'Apache-2.0']]]
    }

    void "the sbom is written and made reproducible with the configuration cache, also when it is reused"() {
        given: 'a library with no dependencies'
        new File(projectDir, 'settings.gradle').text = "rootProject.name = 'library'"
        new File(projectDir, 'gradle.properties').text = 'projectVersion=1.0.0'
        new File(projectDir, 'build.gradle').text = """
            plugins {
                id 'java-library'
                id 'org.apache.grails.buildsrc.sbom'
            }
        """

        when: 'the sbom is built from a clean build, storing and then reusing the configuration cache entry'
        BuildResult stored = buildSbomFromClean()
        BuildResult reused = buildSbomFromClean()

        then:
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')

        and: 'the reused build rewrote the sbom as the stored one did'
        String sbom = new File(projectDir, 'build/library-1.0.0-sbom.json').text
        sbom.contains('"timestamp": "1970-01-01T00:00:00Z"')
        sbom.contains('"serialNumber": "urn:uuid:')
    }

    void "the sbom is written again when an external dependency moves to another version"() {
        given: 'a library that depends on version 1.0 of a module in a local repository'
        publishExternalModule('1.0')
        publishExternalModule('2.0')
        new File(projectDir, 'settings.gradle').text = "rootProject.name = 'library'"
        new File(projectDir, 'gradle.properties').text = 'projectVersion=1.0.0'
        writeLibraryDependingOnExternal('1.0')
        build('cyclonedxDirectBom')

        when: 'the sbom is built again with nothing changed'
        final unchanged = build('cyclonedxDirectBom')

        then: 'the task is up to date'
        unchanged.task(':cyclonedxDirectBom').outcome == TaskOutcome.UP_TO_DATE
        sbomVersions('build/library-1.0.0-sbom.json', 'external') == ['1.0']

        when: 'the dependency moves to 2.0'
        writeLibraryDependingOnExternal('2.0')
        final moved = build('cyclonedxDirectBom')

        then: 'the task runs again and the sbom lists 2.0'
        moved.task(':cyclonedxDirectBom').outcome != TaskOutcome.UP_TO_DATE
        sbomVersions('build/library-1.0.0-sbom.json', 'external') == ['2.0']

        when: 'the dependency moves back to 1.0 and the sbom is built from a clean build'
        writeLibraryDependingOnExternal('1.0')
        build('clean', 'cyclonedxDirectBom')

        then: 'the build cache does not hand back the sbom of 2.0'
        sbomVersions('build/library-1.0.0-sbom.json', 'external') == ['1.0']
    }

    void "the sbom is written again when a project dependency moves to another version"() {
        given: 'a library that depends on version 1.0 of another project of the build'
        writeLibraryDependingOnProject()
        writeDependencyProject('1.0')
        build(':library:cyclonedxDirectBom')

        when: 'the sbom is built again with nothing changed'
        final unchanged = build(':library:cyclonedxDirectBom')

        then: 'the task is up to date'
        unchanged.task(':library:cyclonedxDirectBom').outcome == TaskOutcome.UP_TO_DATE
        sbomVersions('library/build/library-1.0.0-sbom.json', 'dependency') == ['1.0']

        when: 'the project moves to 2.0'
        writeDependencyProject('2.0')
        final moved = build(':library:cyclonedxDirectBom')

        then: 'the task runs again and the sbom lists 2.0'
        moved.task(':library:cyclonedxDirectBom').outcome != TaskOutcome.UP_TO_DATE
        sbomVersions('library/build/library-1.0.0-sbom.json', 'dependency') == ['2.0']

        when: 'the project moves back to 1.0 and the sbom is built from a clean build'
        writeDependencyProject('1.0')
        build(':library:clean', ':library:cyclonedxDirectBom')

        then: 'the build cache does not hand back the sbom of 2.0'
        sbomVersions('library/build/library-1.0.0-sbom.json', 'dependency') == ['1.0']
    }

    void "the sbom is written again when the bytes of an external dependency change under the same version"() {
        given: 'a library that depends on version 1.0 of a module in a local repository'
        publishExternalModule('1.0')
        new File(projectDir, 'settings.gradle').text = "rootProject.name = 'library'"
        new File(projectDir, 'gradle.properties').text = 'projectVersion=1.0.0'
        writeLibraryDependingOnExternal('1.0')
        build('cyclonedxDirectBom')

        when: 'the jar of 1.0 gets other bytes, as the jar of a snapshot does'
        final jar = new File(projectDir, 'repo/org/example/external/1.0/external-1.0.jar')
        writeJar(jar, 'other bytes')
        final changed = build('cyclonedxDirectBom')

        then: 'the task runs again and the sbom records the hash of the new bytes'
        changed.task(':cyclonedxDirectBom').outcome != TaskOutcome.UP_TO_DATE
        sbomSha256('build/library-1.0.0-sbom.json', 'external') == MessageDigest.getInstance('SHA-256').digest(jar.bytes).encodeHex().toString()
    }

    void "a project dependency is described without its jar being built"() {
        given: 'a library that depends on another project of the build'
        writeLibraryDependingOnProject()
        writeDependencyProject('1.0')

        when: 'the sbom of the library is built'
        final result = build(':library:cyclonedxDirectBom')

        then: 'the sbom lists the project, and the jar of the project is not built'
        sbomVersions('library/build/library-1.0.0-sbom.json', 'dependency') == ['1.0']
        result.task(':dependency:jar') == null
    }

    private BuildResult buildSbomFromClean() {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('clean', 'cyclonedxDirectBom', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()
    }

    private BuildResult build(String... tasks) {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(tasks.toList() + ['--build-cache', '--configuration-cache', '--stacktrace'])
                .withPluginClasspath()
                .build()
    }

    private void publishExternalModule(String version) {
        final moduleDir = new File(projectDir, "repo/org/example/external/$version")
        moduleDir.mkdirs()
        new File(moduleDir, "external-${version}.pom").text = """\
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>org.example</groupId>
                <artifactId>external</artifactId>
                <version>$version</version>
            </project>
        """.stripIndent()
        writeJar(new File(moduleDir, "external-${version}.jar"), version)
    }

    private static void writeJar(File file, String content) {
        new JarOutputStream(new FileOutputStream(file)).withCloseable { JarOutputStream jar ->
            jar.putNextEntry(new JarEntry('version.txt'))
            jar.write(content.bytes)
        }
    }

    private void writeLibraryDependingOnExternal(String version) {
        new File(projectDir, 'build.gradle').text = """
            plugins {
                id 'java-library'
                id 'org.apache.grails.buildsrc.sbom'
            }

            repositories {
                maven {
                    url = uri('repo')
                }
            }

            dependencies {
                implementation 'org.example:external:$version'
            }
        """
    }

    private void writeLibraryDependingOnProject() {
        new File(projectDir, 'settings.gradle').text = '''\
            rootProject.name = 'build'
            include 'library', 'dependency'
        '''.stripIndent()
        new File(projectDir, 'gradle.properties').text = 'projectVersion=1.0.0'
        new File(projectDir, 'library').mkdirs()
        new File(projectDir, 'library/build.gradle').text = '''
            plugins {
                id 'java-library'
                id 'org.apache.grails.buildsrc.sbom'
            }

            dependencies {
                implementation project(':dependency')
            }
        '''
    }

    private void writeDependencyProject(String version) {
        new File(projectDir, 'dependency').mkdirs()
        new File(projectDir, 'dependency/build.gradle').text = """
            plugins {
                id 'java-library'
            }

            group = 'org.example'
            version = '$version'
        """
    }

    private List<String> sbomVersions(String sbomPath, String componentName) {
        final sbom = new JsonSlurper().parse(new File(projectDir, sbomPath)) as Map
        final components = (sbom.components ?: []) as List<Map>
        components.findAll { it.name == componentName }*.version as List<String>
    }

    private String sbomSha256(String sbomPath, String componentName) {
        final sbom = new JsonSlurper().parse(new File(projectDir, sbomPath)) as Map
        final component = (sbom.components as List<Map>).find { it.name == componentName }
        (component.hashes as List<Map>).find { it.alg == 'SHA-256' }.content as String
    }
}
