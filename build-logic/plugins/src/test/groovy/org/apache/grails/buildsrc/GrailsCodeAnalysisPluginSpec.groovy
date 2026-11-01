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

    def "PMD enablement (global: #global, projects: '#projects', enablePmd() on :selected: #extension)"() {
        given:
        writeMultiProjectBuild('pmd', global, projects, extension)

        when:
        def result = run('tasks', '--all', '--configuration-cache')

        then:
        result.output.contains('selected:pmdMain') == selectedEnabled
        result.output.contains('excluded:pmdMain') == excludedEnabled

        where:
        global | projects    | extension || selectedEnabled | excludedEnabled
        null   | ''          | false     || false           | false
        true   | ''          | false     || true            | true
        null   | ':selected' | false     || true            | false
        null   | ''          | true      || true            | false
        null   | ':excluded' | true      || true            | true
        true   | ':selected' | true      || true            | true
        false  | ''          | true      || false           | false
        false  | ':selected' | false     || false           | false
        false  | ':selected' | true      || false           | false
    }

    def "SpotBugs enablement (global: #global, projects: '#projects', enableSpotbugs() on :selected: #extension)"() {
        given:
        writeMultiProjectBuild('spotbugs', global, projects, extension)

        when:
        def result = run('tasks', '--all', '--configuration-cache')

        then:
        result.output.contains('selected:spotbugsMain') == selectedEnabled
        result.output.contains('excluded:spotbugsMain') == excludedEnabled

        where:
        global | projects    | extension || selectedEnabled | excludedEnabled
        null   | ''          | false     || false           | false
        null   | ':selected' | false     || true            | false
        null   | ''          | true      || true            | false
        true   | ''          | true      || true            | true
        false  | ':selected' | true      || false           | false
    }

    def "PITest enablement (global: #global, projects: '#projects', enablePitest() on :selected: #extension)"() {
        given:
        writeMultiProjectBuild('pitest', global, projects, extension)

        when:
        def result = run('tasks', '--all', '--configuration-cache')

        then:
        result.output.contains('selected:pitest') == selectedEnabled
        result.output.contains('excluded:pitest') == excludedEnabled

        where:
        global | projects    | extension || selectedEnabled | excludedEnabled
        null   | ''          | false     || false           | false
        null   | ':selected' | false     || true            | false
        null   | ''          | true      || true            | false
        true   | ''          | true      || true            | true
        false  | ':selected' | true      || false           | false
    }

    def "enablePitest configures Groovy synthetic exclusions and targetClasses/targetTests properties"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = '''
            grails.code-analysis.pitest.targetClasses=org.grails.example.Foo,org.grails.example.Bar
            grails.code-analysis.pitest.targetTests=org.grails.example.FooSpec
        '''
        testProjectDir.resolve('build.gradle').toFile().text = '''
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-analysis'
            }
            grailsCodeAnalysis {
                enablePitest()
            }
            tasks.register('assertPitestConfigured') {
                Set<String> targetClasses = project.extensions.getByName('pitest').targetClasses.get()
                Set<String> targetTests = project.extensions.getByName('pitest').targetTests.get()
                Set<String> excludedMethods = project.extensions.getByName('pitest').excludedMethods.get()
                Set<String> excludedClasses = project.extensions.getByName('pitest').excludedClasses.get()
                doLast {
                    assert targetClasses == ['org.grails.example.Foo', 'org.grails.example.Bar'] as Set
                    assert targetTests == ['org.grails.example.FooSpec'] as Set
                    assert excludedMethods.contains('$getStaticMetaClass')
                    assert !excludedMethods.contains('invokeMethod')
                    assert !excludedMethods.contains('getProperty')
                    assert excludedClasses.contains('*$_closure*')
                }
            }
        '''

        when:
        def result = run('assertPitestConfigured', '--configuration-cache')

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    def "PITest auto-enables on requested task, evaluates late group in targetClasses, defaults targetTests to *, and kills mutants in Spock with configuration cache"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = '''
            pitestVersion=1.19.6
            pitestJunit5PluginVersion=1.2.2
        '''
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'selected'"
        def selectedDir = testProjectDir.resolve('selected')
        selectedDir.toFile().mkdirs()
        selectedDir.resolve('build.gradle').toFile().text = '''
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-code-analysis'
            }
            group = 'custom.group'
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation localGroovy()
                testImplementation platform('org.spockframework:spock-bom:2.4-M6-groovy-4.0')
                testImplementation 'org.spockframework:spock-core'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            tasks.withType(Test).configureEach {
                useJUnitPlatform()
            }
            tasks.register('assertLazyDefaults') {
                def targetClassesProvider = project.extensions.getByName('pitest').targetClasses
                def targetTestsProvider = project.extensions.getByName('pitest').targetTests
                doLast {
                    Set<String> classes = targetClassesProvider.get()
                    assert classes.containsAll(['org.apache.grails.*', 'org.grails.*', 'grails.*', 'custom.group.*'])
                    assert targetTestsProvider.get() == ['*'] as Set
                }
            }
        '''
        def javaFile = selectedDir.resolve('src/main/java/org/grails/example/Calculator.java').toFile()
        javaFile.parentFile.mkdirs()
        javaFile.text = '''
            package org.grails.example;

            public class Calculator {
                public static int add(int a, int b) {
                    return a + b;
                }

                public Object getProperty(String name) {
                    return "val:" + name;
                }
            }
        '''
        def mainFile = selectedDir.resolve('src/main/groovy/org/grails/example/GroovyCalculator.groovy').toFile()
        mainFile.parentFile.mkdirs()
        mainFile.text = '''
            package org.grails.example

            import groovy.transform.CompileStatic

            @CompileStatic
            class GroovyCalculator {
                static int multiply(int a, int b) {
                    return a * b
                }
            }
        '''
        def specFile = selectedDir.resolve('src/test/groovy/org/grails/example/CalculatorSpec.groovy').toFile()
        specFile.parentFile.mkdirs()
        specFile.text = '''
            package org.grails.example

            import spock.lang.Specification

            class CalculatorSpec extends Specification {
                def "kills mutants in add, multiply, and handwritten getProperty"() {
                    expect:
                    Calculator.add(2, 3) == 5
                    new Calculator().getProperty('foo') == 'val:foo'
                    GroovyCalculator.multiply(2, 3) == 6
                }
            }
        '''

        when:
        def defaultsRun = run(
                ':selected:assertLazyDefaults',
                '-Pgrails.code-analysis.enabled.pitest.projects=:selected',
                '--configuration-cache'
        )
        def firstRun = run(
                ':selected:pitest',
                '-Pgrails.code-analysis.pitest.targetClasses=org.grails.example.*',
                '--configuration-cache'
        )
        File mutationsXml = testProjectDir.resolve('build/reports/code-analysis/pitest/selected/mutations.xml').toFile()

        then:
        defaultsRun.output.contains('BUILD SUCCESSFUL')
        firstRun.output.contains('BUILD SUCCESSFUL')
        mutationsXml.exists()
        mutationsXml.text.contains("status='KILLED'")
        mutationsXml.text.contains('<mutatedMethod>add</mutatedMethod>')
        mutationsXml.text.contains('<mutatedMethod>getProperty</mutatedMethod>')
        mutationsXml.text.contains('<mutatedMethod>multiply</mutatedMethod>')

        when:
        def secondRun = run(
                ':selected:pitest',
                '-Pgrails.code-analysis.pitest.targetClasses=org.grails.example.*',
                '--configuration-cache'
        )

        then:
        secondRun.output.contains('Configuration cache entry reused')
    }

    def "enablePmd configures PMD immediately, so the build script customizes pmdMain and the reports directory afterwards"() {
        given:
        testProjectDir.resolve('build.gradle').toFile().text = '''
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-analysis'
            }
            grailsCodeAnalysis {
                enablePmd()
                enablePmd()
                reportsDirectory.set(layout.buildDirectory.dir('custom-analysis'))
            }
            tasks.named('pmdMain') {
                reports.xml.outputLocation.set(layout.buildDirectory.file('custom/pmd.xml'))
            }
            tasks.register('assertPmdCustomized') {
                File mainLocation = tasks.named('pmdMain').get().reports.xml.outputLocation.get().asFile
                File testLocation = tasks.named('pmdTest').get().reports.xml.outputLocation.get().asFile
                File expectedMain = file('build/custom/pmd.xml')
                File expectedTestDirectory = file('build/custom-analysis/pmd')
                doLast {
                    assert mainLocation == expectedMain
                    assert testLocation.parentFile == expectedTestDirectory
                }
            }
        '''

        when:
        def result = run('assertPmdCustomized', '--configuration-cache')

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    def "the plugin can be applied and opted into after every project is evaluated"() {
        given:
        testProjectDir.resolve('build.gradle').toFile().text = '''
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-analysis' apply false
            }
            gradle.projectsEvaluated {
                pluginManager.apply('org.apache.grails.gradle.grails-code-analysis')
                grailsCodeAnalysis.enablePmd()
            }
        '''

        when:
        def result = run('tasks', '--all')

        then:
        result.output.contains('pmdMain')
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

    private void writeMultiProjectBuild(String tool, Boolean global, String projects, boolean extension) {
        String optIn = tool == 'pmd' ? 'enablePmd()' : (tool == 'spotbugs' ? 'enableSpotbugs()' : 'enablePitest()')
        testProjectDir.resolve('gradle.properties').toFile().text = """${global == null ? '' : "grails.code-analysis.enabled.${tool}=${global}"}
${projects ? "grails.code-analysis.enabled.${tool}.projects=${projects}" : ''}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'selected', 'excluded'"
        testProjectDir.resolve('selected').toFile().mkdirs()
        testProjectDir.resolve('excluded').toFile().mkdirs()
        ['selected', 'excluded'].each { projectName ->
            testProjectDir.resolve("${projectName}/build.gradle").toFile().text = """
                plugins {
                    id 'java'
                    id 'org.apache.grails.gradle.grails-code-analysis'
                }
                ${extension && projectName == 'selected' ? "grailsCodeAnalysis { ${optIn} }" : ''}
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
