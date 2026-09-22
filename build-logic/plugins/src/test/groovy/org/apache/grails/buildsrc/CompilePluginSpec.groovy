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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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

    def "disables invokedynamic on GroovyCompile tasks by default"() {
        when:
        def result = runPrintIndy()

        then:
        result.task(':printIndy').outcome == TaskOutcome.SUCCESS
        result.output.contains('MAIN_INDY=false')
        result.output.contains('TEST_INDY=false')
    }

    def "preserves parameter names for Groovy and joint-compiled Java sources"() {
        when:
        def result = runPrintIndy()

        then:
        result.output.contains('MAIN_GROOVY_PARAMETERS=true')
        result.output.contains('MAIN_JOINT_JAVAC_ARGS=[-parameters]')
    }

    def "enables invokedynamic when grailsIndy is true"() {
        when:
        def result = runPrintIndy('-PgrailsIndy=true')

        then:
        result.task(':printIndy').outcome == TaskOutcome.SUCCESS
        result.output.contains('MAIN_INDY=true')
        result.output.contains('TEST_INDY=true')
    }

    def "trims whitespace when parsing grailsIndy"() {
        when:
        def result = runPrintIndy('-PgrailsIndy= true ')

        then:
        result.task(':printIndy').outcome == TaskOutcome.SUCCESS
        result.output.contains('MAIN_INDY=true')
        result.output.contains('TEST_INDY=true')
    }

    private def runPrintIndy(String... extraArgs) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(['printIndy', '--stacktrace'] + (extraArgs as List))
                .withPluginClasspath()
                .build()
    }

    def "a disabled javadoc task's stale output stays out of the javadoc jar"() {
        given: 'the javadoc jar is fed by another documentation directory, as grails-publish does with groovydoc'
            writeFile('docs-from-groovydoc/help-doc.html', 'groovydoc help')
            writeFile('docs-from-groovydoc/index.html', 'groovydoc index')
            testProjectDir.resolve('build.gradle').toFile() << '''
                tasks.named('javadoc') { enabled = false }
                tasks.named('javadocJar') { from('docs-from-groovydoc') }
            '''

        and: 'a build/docs/javadoc left behind by a build where javadoc still ran'
            writeFile('build/docs/javadoc/help-doc.html', 'stale javadoc help')
            writeFile('build/docs/javadoc/p/A.html', 'stale javadoc page')

        when:
            def result = runJavadocJar()

        then: 'the jar builds although both directories carry a help-doc.html'
            result.task(':javadocJar').outcome == TaskOutcome.SUCCESS

        and: 'it holds the other documentation only'
        def entries = jarEntries()
        entries['help-doc.html'] == 'groovydoc help'
        entries['index.html'] == 'groovydoc index'
        !entries.containsKey('p/A.html')
    }

    def "an enabled javadoc task's output is packaged in the javadoc jar"() {
        given:
            writeFile('src/main/java/p/A.java', 'package p;\n/** Documented. */\npublic class A {}\n')

        when:
            def result = runJavadocJar()

        then:
            result.task(':javadoc').outcome == TaskOutcome.SUCCESS
            result.task(':javadocJar').outcome == TaskOutcome.SUCCESS
            jarEntries().containsKey('p/A.html')
    }

    private def runJavadocJar() {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(['javadocJar', '--stacktrace'])
                .withPluginClasspath()
                .build()
    }

    private void writeFile(String path, String text) {
        testProjectDir.resolve(path).toFile().with {
            parentFile.mkdirs()
            it.text = text
        }
    }

    private Map<String, String> jarEntries() {
        def jar = testProjectDir
                .resolve('build/libs')
                .toFile()
                .listFiles()
                .find { it.name.endsWith('-javadoc.jar') }
        def entries = [:] as Map<String, String>
        new ZipFile(jar).withCloseable { ZipFile zip ->
            zip.entries().each { ZipEntry entry ->
                if (!entry.directory) {
                    entries[entry.name] = zip.getInputStream(entry).text
                }
            }
        }
        entries
    }
}
