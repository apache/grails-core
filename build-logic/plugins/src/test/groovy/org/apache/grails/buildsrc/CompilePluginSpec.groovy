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

import org.gradle.api.Project
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class CompilePluginSpec extends Specification {

    @TempDir
    File projectDir

    @TempDir
    Path testProjectDir

    void 'the hand-authored auto-configuration imports file is a compiler input'() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        GroovyCompile compileGroovy = project.tasks.register('compileGroovy', GroovyCompile).get()
        File importsFile = new File(projectDir, CompilePlugin.AUTO_CONFIGURATION_IMPORTS_PATH)
        importsFile.parentFile.mkdirs()
        importsFile.text = 'example.ExampleAutoConfiguration\n'

        when:
        CompilePlugin.registerAutoConfigurationImportsInput(project, compileGroovy)
        CompilePlugin.registerAutoConfigurationImportsInput(project, compileGroovy)

        then:
        compileGroovy.inputs.files.files*.canonicalFile.contains(importsFile.canonicalFile)
        compileGroovy.inputs.files.files.count { it.canonicalFile == importsFile.canonicalFile } == 1
    }

    void 'the hand-authored factories files are compiler inputs, each registered once'() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        GroovyCompile compileGroovy = project.tasks.register('compileGroovy', GroovyCompile).get()
        List<File> factoriesFiles = ([CompilePlugin.GRAILS_FACTORIES_PATH] + CompilePlugin.CLI_FACTORIES_PATHS).collect { String path ->
            File file = new File(projectDir, path)
            file.parentFile.mkdirs()
            file.text = 'example.Type=example.Implementation\n'
            file.canonicalFile
        }

        when:
        CompilePlugin.registerFactoriesInputs(project, compileGroovy)
        CompilePlugin.registerFactoriesInputs(project, compileGroovy)

        then:
        compileGroovy.inputs.files.files*.canonicalFile.findAll { it in factoriesFiles }.sort() == factoriesFiles.sort()
    }

    def setup() {
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('.asf.yaml').toFile().text = ''
        def configScript = testProjectDir.resolve('gradle/groovy-compile-configscript.groovy').toFile()
        configScript.parentFile.mkdirs()
        configScript.text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.buildsrc.compile'
            }

            ext {
                javaVersion = 21
                grailsVersion = '8.0.0-SNAPSHOT'
                formattedBuildDate = '2026-01-01'
            }

            repositories {
                mavenCentral()
            }

            tasks.register('printCallSiteModule') {
                def declared = provider {
                    sourceSets.collect { ss ->
                        "\${ss.name}=" + configurations[ss.implementationConfigurationName].dependencies
                                .findAll { it.group == 'org.apache.groovy' && it.name == 'groovy-callsite' }
                                .collect { "\${it.group}:\${it.name}:\${it.version}" }
                    }
                }
                doLast {
                    println "CALL_SITE_MODULE=\${declared.get()}"
                }
            }

            tasks.register('printIndy') {
                def compileTask = tasks.named('compileGroovy', org.gradle.api.tasks.compile.GroovyCompile)
                def testCompileTask = tasks.named('compileTestGroovy', org.gradle.api.tasks.compile.GroovyCompile)
                doLast {
                    println "MAIN_INDY=\${compileTask.get().groovyOptions.optimizationOptions.indy}"
                    println "TEST_INDY=\${testCompileTask.get().groovyOptions.optimizationOptions.indy}"
                    println "MAIN_JOINT_JAVAC_ARGS=\${compileTask.get().options.compilerArgs}"
                    println "MAIN_GROOVY_PARAMETERS=\${compileTask.get().groovyOptions.parameters}"
                }
            }
        """
    }

    def "enables invokedynamic on GroovyCompile tasks by default"() {
        when:
        def result = runPrintIndy()

        then:
        result.task(':printIndy').outcome == TaskOutcome.SUCCESS
        result.output.contains('MAIN_INDY=true')
        result.output.contains('TEST_INDY=true')
    }

    def "preserves parameter names for Groovy and joint-compiled Java sources"() {
        when:
        def result = runPrintIndy()

        then:
        result.output.contains('MAIN_GROOVY_PARAMETERS=true')
        result.output.contains('MAIN_JOINT_JAVAC_ARGS=[-parameters]')
    }

    def "declares groovy-callsite on every source set only when grailsIndy is false"() {
        given: 'the version the BOM projects publish, as dependencies.gradle sets it on the root'
        testProjectDir.resolve('build.gradle').toFile().append('''
            ext.bomDependencyVersions = ['groovy.version': '6.0.0']
        ''')

        when:
        def result = run(['printCallSiteModule'] + arguments)

        then:
        result.task(':printCallSiteModule').outcome == TaskOutcome.SUCCESS
        result.output.contains("CALL_SITE_MODULE=[main=${expected}, test=${expected}]")

        where:
        arguments               | expected
        []                      | '[]'
        ['-PgrailsIndy=true']   | '[]'
        ['-PgrailsIndy=false']  | '[org.apache.groovy:groovy-callsite:6.0.0]'
    }

    def "declares no groovy-callsite in the #build build, whose code runs inside Gradle"() {
        given:
        testProjectDir.resolve('settings.gradle').toFile().text = "rootProject.name = '${build}'"
        testProjectDir.resolve('build.gradle').toFile().append('''
            ext.bomDependencyVersions = ['groovy.version': '6.0.0']
        ''')

        when:
        def result = run(['printCallSiteModule', '-PgrailsIndy=false'])

        then:
        result.output.contains('CALL_SITE_MODULE=[main=[], test=[]]')

        where:
        build << ['build-logic-root', 'grails-gradle']
    }

    def "keeps invokedynamic on when grailsIndy is false"() {
        when:
        def result = runPrintIndy('-PgrailsIndy=false')

        then:
        result.task(':printIndy').outcome == TaskOutcome.SUCCESS
        result.output.contains('MAIN_INDY=true')
        result.output.contains('TEST_INDY=true')
    }

    private def run(List<String> arguments) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(arguments + ['--stacktrace'])
                .withPluginClasspath()
                .build()
    }

    private def runPrintIndy(String... extraArgs) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(['printIndy', '--stacktrace'] + (extraArgs as List))
                .withPluginClasspath()
                .build()
    }
}
