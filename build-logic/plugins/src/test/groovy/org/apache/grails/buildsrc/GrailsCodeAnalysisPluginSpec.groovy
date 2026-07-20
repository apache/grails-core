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

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class GrailsCodeAnalysisPluginSpec extends Specification {

    @TempDir
    Path testProjectDir

    def "PMD project allowlist enables only listed projects"() {
        given:
        writeMultiProjectBuild('grails.code-analysis.enabled.pmd.projects=:selected')

        when:
        def result = run('tasks', '--all', '--configuration-cache')

        then:
        result.output.contains('selected:pmdMain')
        !result.output.contains('excluded:pmdMain')
    }

    def "global PMD opt-in remains compatible with project selection"() {
        given:
        writeMultiProjectBuild('grails.code-analysis.enabled.pmd=true')

        when:
        def result = run('tasks', '--all')

        then:
        result.output.contains('selected:pmdMain')
        result.output.contains('excluded:pmdMain')
    }

    def "PMD excludes generated build sources"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = 'grails.code-analysis.enabled.pmd=true\n'
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-analysis'
            }
            layout.buildDirectory.set(layout.projectDirectory.dir('generated-build'))
            sourceSets.main.java.srcDir('generated-build/generated/sources')
            tasks.register('assertPmdSources') {
                doLast {
                    assert !tasks.named('pmdMain').get().source.files.any { it.name == 'Generated.java' }
                }
            }
        """
        def generated = testProjectDir.resolve('generated-build/generated/sources/Generated.java').toFile()
        generated.parentFile.mkdirs()
        generated.text = 'class Generated {}'

        when:
        def result = run('assertPmdSources')

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    def "PMD includes normal sources when the checkout has a build ancestor"() {
        given:
        Path projectDirectory = testProjectDir.resolve('build/checkouts/project')
        writePmdBuild(projectDirectory, '''
            tasks.register('assertPmdSources') {
                doLast {
                    assert tasks.named('pmdMain').get().source.files.any { it.name == 'Source.java' }
                }
            }
        ''')
        def source = projectDirectory.resolve('src/main/java/Source.java').toFile()
        source.parentFile.mkdirs()
        source.text = 'class Source {}'

        when:
        def result = run(projectDirectory, 'assertPmdSources')

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    private void writePmdBuild(Path projectDirectory, String additionalConfiguration) {
        projectDirectory.toFile().mkdirs()
        projectDirectory.resolve('gradle.properties').toFile().text = 'grails.code-analysis.enabled.pmd=true\n'
        projectDirectory.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-analysis'
            }
            ${additionalConfiguration}
        """
    }

    private void writeMultiProjectBuild(String property) {
        testProjectDir.resolve('gradle.properties').toFile().text = "${property}\n"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'selected', 'excluded'"
        testProjectDir.resolve('selected').toFile().mkdirs()
        testProjectDir.resolve('excluded').toFile().mkdirs()
        ['selected', 'excluded'].each { projectName ->
            testProjectDir.resolve("${projectName}/build.gradle").toFile().text = """
                plugins {
                    id 'java'
                    id 'org.apache.grails.gradle.grails-code-analysis'
                }
            """
        }
    }

    private def run(String... arguments) {
        run(testProjectDir, arguments)
    }

    private def run(Path projectDirectory, String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withArguments(arguments + ['--stacktrace'])
                .withPluginClasspath()
                .build()
    }
}
