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
package org.grails.gradle.plugin.commands

import java.util.jar.JarFile

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * Every Grails plugin publishes the companions of its tools without opting in, and only when it has
 * sources of tools: {@code -cli-standalone} for {@code src/standalone}, and {@code -cli} for any of
 * {@code src/cli}, {@code grails-app/commands}, {@code src/main/scripts} and {@code src/standalone}, so
 * that a build that names {@code -cli} still resolves once the tools of a plugin are all standalone
 * tools. A companion that is not made has no software component, no publication, no manifest attribute
 * and no extra property. Whether a folder has a source is decided when the build is configured, and a
 * configuration cache entry is not reused once that changes.
 */
class CompanionOnlyWithSourcesSpec extends GradleSpecification {

    private static final String BUILD = '''
        plugins {
            id 'org.apache.grails.gradle.grails-plugin'
            id 'org.apache.grails.gradle.grails-publish'
            __EXPLICIT__
        }

        grails {
            bom = null
            cliAutoProvision = false
        }

        cliArtifact {
            defaultDependencies = false
        }

        dependencies {
            // the tools in Groovy are compiled with the Groovy of Gradle: this build resolves nothing
            cliCompileOnly localGroovy()
        }

        grailsPublish {
            githubSlug = 'apache/grails-core'
            license {
                name = 'Apache-2.0'
            }
            title = 'Plugin Companions'
            desc = 'Test plugin whose companions depend on its sources'
            developers = [test: 'Test Developer']
            testRepositoryPath = layout.buildDirectory.dir('test-repo').get()
        }

        tasks.register('inspectCompanions') {
            dependsOn 'jar'
            def components = providers.provider { project.components.names.toList().sort() }
            def publications = providers.provider { project.publishing.publications.names.toList().sort() }
            def extra = providers.provider { ['cliArtifactId', 'cliStandaloneArtifactId'].collect { "${it}=${project.findProperty(it)}".toString() } }
            def sourceSets = providers.provider { project.sourceSets.names.findAll { it in ['cli', 'standalone'] }.toList().sort() }
            def jar = tasks.named('jar', Jar).flatMap { it.archiveFile }
            doLast {
                println "COMPONENTS=${components.get().join(',')}"
                println "PUBLICATIONS=${publications.get().join(',')}"
                extra.get().each { println it }
                println "SOURCE_SETS=${sourceSets.get().join(',')}"
                new java.util.jar.JarFile(jar.get().asFile).withCloseable { java.util.jar.JarFile file ->
                    def attributes = file.manifest.mainAttributes
                    println "MANIFEST=${attributes.keySet()*.toString().findAll { it.startsWith('Grails-Cli') }.sort().collect { it + ':' + attributes.getValue(it) }.join(',')}"
                }
            }
        }
    '''

    def "a Grails plugin #has makes #made"() {
        given:
        GradleRunner runner = plugin(false)
        sources.each { String path -> file(runner, path, path.endsWith('.java') ? "package example;\npublic class ${new File(path).name - '.java'} {\n}\n" : 'content\n') }

        when:
        Map<String, String> said = said(executeTask('inspectCompanions'))

        then: 'the source sets are there, with or without sources'
        said.SOURCE_SETS == 'cli,standalone'

        and: 'each companion that is made has its component, its publication, its extra property and its manifest attribute, and no other'
        said.COMPONENTS == (['java'] + (cli ? ['cli'] : []) + (standalone ? ['cliStandalone'] : [])).sort().join(',')
        said.PUBLICATIONS == (['maven'] + (cli ? ['cli'] : []) + (standalone ? ['cliStandalone'] : [])).sort().join(',')
        said.cliArtifactId == (cli ? 'plugin-companions-cli' : 'null')
        said.cliStandaloneArtifactId == (standalone ? 'plugin-companions-cli-standalone' : 'null')
        said.MANIFEST == ((cli ? ['Grails-Cli-Artifact:org.example.test:plugin-companions-cli'] : []) +
                (standalone ? ['Grails-Cli-Standalone-Artifact:org.example.test:plugin-companions-cli-standalone'] : [])).join(',')

        where:
        has                                                    | sources                                                                       || cli   | standalone
        'without a source of a tool'                           | []                                                                            || false | false
        'with resources alone in src/cli and src/standalone'   | ['src/cli/resources/notes.txt', 'src/standalone/resources/templates/a.txt']   || false | false
        'with nothing but a .gitkeep in grails-app/commands'   | ['grails-app/commands/.gitkeep']                                              || false | false
        'with a source in src/cli'                             | ['src/cli/java/example/ReportTool.java']                                      || true  | false
        'with a source in grails-app/commands'                 | ['grails-app/commands/example/OldCommand.groovy']                             || true  | false
        'with a script in src/main/scripts'                    | ['src/main/scripts/example-script.groovy']                                    || true  | false
        // -cli without a tool, which depends on -cli-standalone, so that a build that names -cli has the standalone tools
        'with a source in src/standalone alone'                | ['src/standalone/java/example/CopyTool.java']                                 || true  | true
        'with a source in src/cli and one in src/standalone'   | ['src/cli/java/example/ReportTool.java', 'src/standalone/java/example/CopyTool.java'] || true | true

        made = cli && standalone ? 'both companions' : cli ? 'the companion -cli alone' : 'neither companion'
    }

    def "applying the id of the publishing side to a Grails plugin as well changes nothing"() {
        given:
        GradleRunner implicit = plugin(false)
        file(implicit, 'src/cli/java/example/ReportTool.java', 'package example;\npublic class ReportTool {\n}\n')
        file(implicit, 'src/standalone/java/example/CopyTool.java', 'package example;\npublic class CopyTool {\n}\n')
        Map<String, String> without = said(executeTask('inspectCompanions'))

        when:
        GradleRunner explicit = plugin(true)
        file(explicit, 'src/cli/java/example/ReportTool.java', 'package example;\npublic class ReportTool {\n}\n')
        file(explicit, 'src/standalone/java/example/CopyTool.java', 'package example;\npublic class CopyTool {\n}\n')
        Map<String, String> with = said(executeTask('inspectCompanions'))

        then:
        with == without
        with.COMPONENTS == 'cli,cliStandalone,java'
    }

    def "the configuration cache is reused while nothing changed, and not when the first tool is added or the last one deleted"() {
        given:
        GradleRunner runner = plugin(false)
        executeTask('inspectCompanions')

        when:
        BuildResult reused = executeTask('inspectCompanions')

        then:
        reused.output.contains('Configuration cache entry reused')
        said(reused).COMPONENTS == 'java'

        when: 'the first tool is added'
        File tool = file(runner, 'src/standalone/java/example/CopyTool.java', 'package example;\npublic class CopyTool {\n}\n')
        BuildResult added = executeTask('inspectCompanions')

        then:
        !added.output.contains('Configuration cache entry reused')
        said(added).COMPONENTS == 'cli,cliStandalone,java'

        when: 'nothing changes'
        BuildResult again = executeTask('inspectCompanions')

        then:
        again.output.contains('Configuration cache entry reused')
        said(again).COMPONENTS == 'cli,cliStandalone,java'

        when: 'the last tool is deleted'
        assert tool.delete()
        BuildResult deleted = executeTask('inspectCompanions')

        then:
        !deleted.output.contains('Configuration cache entry reused')
        said(deleted).COMPONENTS == 'java'
    }

    private GradleRunner plugin(boolean explicit) {
        GradleRunner runner = setupTestResourceProject('plugin-companions')
        file(runner, 'build.gradle', BUILD.replace('__EXPLICIT__', explicit ? "id 'org.apache.grails.gradle.grails-plugin-cli'" : ''))
        runner
    }

    private static File file(GradleRunner runner, String path, String content) {
        File file = new File(runner.projectDir, path)
        file.parentFile.mkdirs()
        file.text = content
        file
    }

    private static Map<String, String> said(BuildResult result) {
        result.output.readLines().findAll { it ==~ /^[A-Za-z_]+=.*$/ }.collectEntries { String line ->
            int equals = line.indexOf('=')
            [(line.substring(0, equals)): line.substring(equals + 1)]
        }
    }
}
