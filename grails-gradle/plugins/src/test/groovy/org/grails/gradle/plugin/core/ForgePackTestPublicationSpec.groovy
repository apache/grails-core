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
package org.grails.gradle.plugin.core

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

/**
 * What {@code forgePackTest} tests is the plugin this build has just built: the applications resolve
 * from a repository of the task that has the Maven publications of the project before any other, and
 * a change of the plugin is a change of what the task tests, also under the same version.
 *
 * <p>The stand-in for Forge says, of each repository it is given that is a directory, which jars it has
 * and what their {@code marker.txt} says: what an application generated there would be built with.</p>
 */
class ForgePackTestPublicationSpec extends GradleSpecification {

    private static final String JAR = 'org/example/test-plugin-forge-pack/1.0.0/test-plugin-forge-pack-1.0.0.jar'

    def "a plugin that changed under the same version is tested again, with what it is now"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile
        marker(project, 'one')

        when:
        BuildResult first = executeTask('forgePackTest', ['-Ppublished', '--info'])

        then: 'the jar of the plugin is built first, and the applications get it'
        first.task(':jar').outcome == TaskOutcome.SUCCESS
        first.task(':forgePackTest').outcome == TaskOutcome.SUCCESS
        first.output.contains("stand-in fixtures repository jar: ${JAR} marker=one")

        when: 'nothing changed'
        BuildResult same = executeTask('forgePackTest', ['-Ppublished'])

        then:
        same.task(':forgePackTest').outcome == TaskOutcome.UP_TO_DATE

        when: 'the plugin changes and keeps its version'
        marker(project, 'two')
        BuildResult changed = executeTask('forgePackTest', ['-Ppublished', '--info'])

        then: 'it is tested again, and the applications get the plugin as it is now'
        changed.task(':forgePackTest').outcome == TaskOutcome.SUCCESS
        changed.output.contains("stand-in fixtures repository jar: ${JAR} marker=two")
        !changed.output.contains('marker=one')
    }

    def "the repository of the publications is the first the applications resolve from, and has them as a Maven repository does"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile
        File repository = new File(project, 'build/forge-pack-repository')

        when:
        BuildResult result = executeTask('forgePackTest', ['-Ppublished', '-PwithRepository', '--info'])

        then:
        result.output.contains("stand-in fixtures repositories: ${repository.path};${new File(project, 'maven-repo').path}")
        files(repository) == ['org/example/test-plugin-forge-pack/1.0.0/test-plugin-forge-pack-1.0.0.jar',
                              'org/example/test-plugin-forge-pack/1.0.0/test-plugin-forge-pack-1.0.0.module',
                              'org/example/test-plugin-forge-pack/1.0.0/test-plugin-forge-pack-1.0.0.pom']
        new File(repository, 'org/example/test-plugin-forge-pack/1.0.0/test-plugin-forge-pack-1.0.0.pom').text.contains('<artifactId>test-plugin-forge-pack</artifactId>')
    }

    def "a publication of #version is resolved by Gradle from the repository of the publications"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile
        marker(project, 'resolved')
        new File(project, 'build.gradle') << "\nversion = '${version}'\n"
        executeTask('forgePackTest', ['-Ppublished'])
        File repository = new File(project, 'build/forge-pack-repository')

        and: 'a build that depends on the plugin by its coordinates, and has that repository alone'
        File consumer = new File(project.parentFile, 'consumer')
        consumer.mkdirs()
        new File(consumer, 'settings.gradle').text = "rootProject.name = 'consumer'\n"
        new File(consumer, 'build.gradle').text = """
            plugins {
                id 'java-library'
            }
            repositories {
                maven {
                    url = uri('${repository.toURI()}')
                }
            }
            dependencies {
                implementation('org.example:test-plugin-forge-pack:${version}') {
                    transitive = false
                }
            }
            def resolved = configurations.named('runtimeClasspath').map { it.incoming.files.files.collect { it.name } }
            tasks.register('resolved') {
                doLast {
                    println 'resolved=' + resolved.get().join(',')
                }
            }
        """.stripIndent()

        when:
        BuildResult result = runner.withProjectDir(consumer).withArguments('resolved').build()

        then:
        result.output.contains("resolved=test-plugin-forge-pack-${version}.jar")

        where:
        version << ['1.0.0', '1.0.0-SNAPSHOT']
    }

    def "a plugin without a Maven publication gives the applications no repository of its own"() {
        given:
        GradleRunner runner = setupTestResourceProject('plugin-forge-pack')
        File project = runner.projectDir.canonicalFile

        when:
        BuildResult result = executeTask('forgePackTest', ['--info'])

        then:
        result.output.contains('stand-in fixtures repositories: null')
        !result.output.contains('stand-in fixtures repository jar: ')
        !new File(project, 'build/forge-pack-repository').exists()
        result.task(':jar') == null
    }

    private static void marker(File project, String text) {
        File file = new File(project, 'src/main/resources/marker.txt')
        file.parentFile.mkdirs()
        file.text = text
    }

    private static List<String> files(File directory) {
        List<String> found = []
        directory.eachFileRecurse(groovy.io.FileType.FILES) { File file ->
            found << directory.toPath().relativize(file.toPath()).join('/')
        }
        return found.sort()
    }
}
