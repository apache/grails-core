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

import java.util.jar.JarFile

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

class SbomPluginSpec extends Specification {

    @TempDir
    File projectDir

    /** A library with both companions, shared by the iterations so that its plugin stub is compiled once */
    @Shared
    @TempDir
    File companionsDir

    private static final def LOGGER = Logging.getLogger(SbomPluginSpec)
    private static final String HIBERNATE_COMMONS = 'pkg:maven/org.hibernate.common/hibernate-commons-annotations@5.1.2.Final?type=jar'

    private static List lgplChoice() {
        [[license: [id: 'LGPL-2.1-only']]]
    }

    private static List bsd4Choice() {
        [[license: [id: 'BSD-4-Clause']]]
    }

    private static Project projectWithVersion(String version) {
        Project project = ProjectBuilder.builder().withName('grails-core').build()
        project.extensions.extraProperties.set('projectVersion', version)
        project
    }

    void "sbomOutputLocationFor names the file <artifactId>-<version>-sbom.json"() {
        given:
        Project project = projectWithVersion('8.0.0')

        expect:
        SbomPlugin.sbomOutputLocationFor(project, project.provider { 'grails-core' }).get().asFile.name ==
                'grails-core-8.0.0-sbom.json'
    }

    @Unroll
    void "the #companion.sourceSetName companion honours a custom artifactId, which the project exports as #companion.artifactIdProperty"() {
        given: "a project that publishes the companion under a customised coordinate"
        Project project = projectWithVersion('8.0.0')
        project.extensions.extraProperties.set(companion.artifactIdProperty, custom)

        expect:
        SbomPlugin.companionPublished(project, companion).get()
        SbomPlugin.companionArtifactId(project, companion).get() == custom

        where:
        companion                       | custom
        SbomPlugin.CLI_COMPANION        | 'grails-core-custom-cli'
        SbomPlugin.STANDALONE_COMPANION | 'grails-core-custom-cli-standalone'
    }

    @Unroll
    void "a custom #companion.sourceSetName artifactId flows through to the name of the companion's sbom file"() {
        given: "a customised companion coordinate"
        Project project = projectWithVersion('8.0.0')
        project.extensions.extraProperties.set(companion.artifactIdProperty, custom)

        when: "the sbom output location is derived from that coordinate"
        def location = SbomPlugin.sbomOutputLocationFor(project, SbomPlugin.companionArtifactId(project, companion))

        then: "the sbom file name tracks the custom name, not the default of the project's name and the companion's suffix"
        location.get().asFile.name == "${custom}-8.0.0-sbom.json"

        where:
        companion                       | custom
        SbomPlugin.CLI_COMPANION        | 'grails-core-custom-cli'
        SbomPlugin.STANDALONE_COMPANION | 'grails-core-custom-cli-standalone'
    }

    @Unroll
    void "a project that does not say it publishes the #companion.sourceSetName companion has none, even where another project of the build has"() {
        given: "a build whose root has the property, and a project that has it not"
        Project root = projectWithVersion('8.0.0')
        root.extensions.extraProperties.set(companion.artifactIdProperty, 'of-the-root')
        Project project = ProjectBuilder.builder().withName('grails-scaffolding').withParent(root).build()

        expect: "the project has no companion, and its coordinate would be its name with the companion's suffix"
        !SbomPlugin.companionPublished(project, companion).get()
        SbomPlugin.companionArtifactId(project, companion).get() == expected

        where:
        companion                       | expected
        SbomPlugin.CLI_COMPANION        | 'grails-scaffolding-cli'
        SbomPlugin.STANDALONE_COMPANION | 'grails-scaffolding-cli-standalone'
    }

    @Unroll
    void "a library that publishes #published has an sbom built and packed into the jar of each companion it publishes, and of no other"() {
        given: "a library with the source sets and jars of both companions, as the plugin that publishes them makes them"
        writeCompanionsLibrary()

        when: "it is built twice from clean with the configuration cache, which the second build reuses"
        BuildResult stored = buildCompanions(companions)
        BuildResult reused = buildCompanions(companions)

        then:
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')

        and: "the sbom of a companion is built when the library publishes the companion, and its task is not even run when it does not"
        reused.task(':cyclonedxCliBom')?.outcome == (cli ? TaskOutcome.SUCCESS : null)
        reused.task(':cyclonedxCliStandaloneBom')?.outcome == (standalone ? TaskOutcome.SUCCESS : null)

        and: "the jar of the companion carries its sbom, which names the companion's coordinate, when the companion is published"
        packedSbom('build/libs/library-cli-1.0.0.jar') == (cli ? 'library-cli' : null)
        packedSbom('build/libs/library-cli-standalone-1.0.0.jar') == (standalone ? 'library-cli-standalone' : null)

        and: "the jar of the library carries its own"
        packedSbom('build/libs/library-1.0.0.jar') == 'library'

        where:
        companions       || cli   | standalone
        ''               || false | false
        'cli'            || true  | false
        'standalone'     || false | true
        'cli,standalone' || true  | true

        published = companions ? "the companions ${companions}" : 'no companion'
    }

    /**
     * A library with a stub of the plugin that publishes the companions with the tools of a plugin, which makes
     * both source sets features with a jar of their own named after the companion, as the plugin does, and sets the
     * extra property of each companion that the property {@code companions} names, once the project is evaluated.
     */
    private void writeCompanionsLibrary() {
        new File(companionsDir, 'settings.gradle').text = "rootProject.name = 'library'"
        new File(companionsDir, 'gradle.properties').text = 'projectVersion=1.0.0'
        new File(companionsDir, 'build.gradle').text = '''
            plugins {
                id 'java-library'
                id 'org.apache.grails.buildsrc.sbom'
                id 'org.apache.grails.gradle.grails-plugin-cli'
            }
            version = projectVersion
        '''
        new File(companionsDir, 'buildSrc').mkdirs()
        new File(companionsDir, 'buildSrc/build.gradle').text = '''
            plugins {
                id 'groovy-gradle-plugin'
            }
            gradlePlugin {
                plugins {
                    register('companions') {
                        id = 'org.apache.grails.gradle.grails-plugin-cli'
                        implementationClass = 'CompanionsStubPlugin'
                    }
                }
            }
        '''
        File plugin = new File(companionsDir, 'buildSrc/src/main/groovy/CompanionsStubPlugin.groovy')
        plugin.parentFile.mkdirs()
        plugin.text = '''
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.plugins.JavaPluginExtension
            import org.gradle.api.tasks.SourceSetContainer
            import org.gradle.api.tasks.bundling.Jar

            class CompanionsStubPlugin implements Plugin<Project> {
                void apply(Project project) {
                    List<String> published = (project.findProperty('companions') ?: '').toString().tokenize(',')
                    Map<String, List<String>> companions = [
                            cli       : ['-cli', 'cliArtifactId'],
                            standalone: ['-cli-standalone', 'cliStandaloneArtifactId'],
                    ]
                    companions.each { String name, List<String> companion ->
                        def sourceSet = project.extensions.getByType(SourceSetContainer).create(name)
                        project.extensions.getByType(JavaPluginExtension).registerFeature(name) { it.usingSourceSet(sourceSet) }
                        String artifactId = project.name + companion[0]
                        project.tasks.named(sourceSet.jarTaskName, Jar).configure { Jar jar ->
                            jar.archiveBaseName.set(artifactId)
                            jar.archiveClassifier.set('')
                        }
                        project.afterEvaluate {
                            if (name in published) {
                                project.extensions.extraProperties.set(companion[1], artifactId)
                            }
                        }
                    }
                }
            }
        '''
    }

    private BuildResult buildCompanions(String companions) {
        GradleRunner.create()
                .withProjectDir(companionsDir)
                .withArguments('clean', 'build', 'cliJar', 'standaloneJar', "-Pcompanions=${companions}".toString(),
                        '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()
    }

    /**
     * @return the name of the component the sbom packed into the jar describes, or {@code null} when the jar has none
     */
    private String packedSbom(String path) {
        new JarFile(new File(companionsDir, path)).withCloseable { JarFile jar ->
            def entry = jar.getEntry('META-INF/sbom.json')
            String location = jar.manifest.mainAttributes.getValue('Sbom-Location')
            assert (entry == null) == (location == null): "${path} has the sbom and its manifest attributes together or neither"
            entry == null ? null : new JsonSlurper().parse(jar.getInputStream(entry)).metadata.component.name
        }
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
        e.message.contains('LGPL-2.1-only')
    }

    void "JLine 4 Maven jars use the BSD-3-Clause correction for new versions"() {
        expect:
        SbomPlugin.pickLicense(LOGGER, 'grails-console', 'grails-console', bomRef, bsd4Choice()).id == 'BSD-3-Clause'

        where:
        bomRef << [
                'pkg:maven/org.jline/jansi@4.3.1?type=jar',
                'pkg:maven/org.jline/jline@4.4.5?type=jar',
                'pkg:maven/org.jline/jline-shell@4.4.5?type=jar'
        ]
    }

    void "org.json resolved through Jedis uses the public domain license approved in LEGAL-666"() {
        given:
        List publicDomainChoice = [[license: [name: 'Public Domain', url: 'https://github.com/stleary/JSON-java/blob/master/LICENSE']]]

        expect:
        SbomPlugin.pickLicense(LOGGER, 'grails-redis', 'grails-redis',
                'pkg:maven/org.json/json@20260719?type=jar', publicDomainChoice).name == 'Public Domain'
    }

    void "JLine 4 license correction is limited to JLine 4 Maven jars"() {
        when:
        SbomPlugin.pickLicense(LOGGER, 'grails-console', 'grails-console', bomRef, bsd4Choice())

        then:
        GradleException e = thrown(GradleException)
        e.message.contains('BSD-4-Clause')

        where:
        bomRef << [
                'pkg:maven/com.example/jansi@4.3.1?type=jar',
                'pkg:maven/org.jline/jansi@3.3.1?type=jar',
                'pkg:maven/org.jline/jline@3.30.16?type=jar'
        ]
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

    private BuildResult buildSbomFromClean() {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('clean', 'cyclonedxDirectBom', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()
    }
}
