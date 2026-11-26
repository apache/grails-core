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

import groovy.xml.XmlSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class GrailsJacocoPluginSpec extends Specification {

    @TempDir
    Path testProjectDir

    def setup() {
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories {
                mavenCentral()
            }
        """
        def src = testProjectDir.resolve('src/main/groovy/com/example/Foo.groovy').toFile()
        src.parentFile.mkdirs()
        src.text = 'package com.example\nclass Foo {}'
    }

    def "jacoco plugin applies JaCoCo and registers jacocoTestReport task"() {
        when: "listing verification tasks"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks', '--group=verification')
                .withPluginClasspath()
                .build()

        then: "jacocoTestReport is present"
        result.output.contains('jacocoTestReport')
    }

    def "jacocoTestReport generates xml html and csv reports"() {
        when: "listing all tasks"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks', '--all')
                .withPluginClasspath()
                .build()

        then: "aggregateJacocoCoverage is not registered (that task belongs to grails-violation-aggregation)"
        !result.output.contains('aggregateJacocoCoverage')
    }

    def "jacocoAggregateReport is registered on the root project in a multi-project build"() {
        given: "a multi-project build where a subproject applies grails-jacoco"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
        """

        when: "listing verification tasks on the root"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks', '--group=verification')
                .withPluginClasspath()
                .build()

        then: "jacocoAggregateReport appears on the root"
        result.output.contains('jacocoAggregateReport')
    }

    def "jacocoAggregateReport includes the subproject test task as a dependency"() {
        given: "a multi-project build"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
        """

        when: "doing a dry run"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('jacocoAggregateReport', '--dry-run')
                .withPluginClasspath()
                .build()

        then: "the subproject test task is in the execution plan"
        result.output.contains(':app-module:test')
    }

    def "jacocoAggregateReport is skipped when no exec files exist"() {
        given: "a multi-project build with tests excluded so no exec files are produced"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
        """

        when: "running jacocoAggregateReport with tests excluded"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('jacocoAggregateReport', '-x', 'test', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "the task is skipped because executionData is empty"
        result.task(':jacocoAggregateReport').outcome == TaskOutcome.SKIPPED
    }

    def "each additional subproject with grails-jacoco wires itself into the same aggregate task"() {
        given: "two subprojects both applying grails-jacoco"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'module-a', 'module-b'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        ['module-a', 'module-b'].each { name ->
            def dir = testProjectDir.resolve(name)
            dir.toFile().mkdirs()
            dir.resolve('build.gradle').toFile().text = """
                plugins {
                    id 'groovy'
                    id 'org.apache.grails.gradle.grails-jacoco'
                }
                repositories { mavenCentral() }
            """
        }

        when: "doing a dry run"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('jacocoAggregateReport', '--dry-run')
                .withPluginClasspath()
                .build()

        then: "both subproject test tasks appear as dependencies"
        result.output.contains(':module-a:test')
        result.output.contains(':module-b:test')

        and: "only one aggregate task is registered on the root"
        result.output.count('jacocoAggregateReport') == 1
    }

    def "jacocoAggregateReport includes a subproject's cli source set next to its main one"() {
        given: "a subproject whose cli companion source set, as the grails-plugin-cli plugin adds, gains a directory after the aggregate is realized"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            tasks.register('printAggregateDirs') {
                doLast {
                    def aggregate = tasks.getByName('jacocoAggregateReport')
                    aggregate.classDirectories.files.each { println "CLASS_DIR \${it}" }
                    aggregate.sourceDirectories.files.each { println "SOURCE_DIR \${it}" }
                }
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
            sourceSets { cli }
            // realize the aggregate before the source set gains a directory, as an eager build script or an IDE sync does
            rootProject.tasks.getByName('jacocoAggregateReport')
            sourceSets.cli.groovy.srcDir('src/extra-cli')
        """

        when: "listing the directories the aggregate reports on"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('printAggregateDirs')
                .withPluginClasspath()
                .build()

        then: "the cli classes and sources are reported alongside main's"
        reported(result.output, 'CLASS_DIR', 'app-module/build/classes/groovy/cli')
        reported(result.output, 'CLASS_DIR', 'app-module/build/classes/groovy/main')
        reported(result.output, 'SOURCE_DIR', 'app-module/src/cli/groovy')
        reported(result.output, 'SOURCE_DIR', 'app-module/src/main/groovy')

        and: "so is the directory added after the aggregate was realized, which a snapshot of the source set would miss"
        reported(result.output, 'SOURCE_DIR', 'app-module/src/extra-cli')
    }

    def "jacocoAggregateReport includes a subproject's standalone source set, the tools that run on their own"() {
        given: "a subproject with both source sets of tools, as the grails-plugin-cli plugin adds them, and a third one that is not production code"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            tasks.register('printAggregateDirs') {
                doLast {
                    def aggregate = tasks.getByName('jacocoAggregateReport')
                    aggregate.classDirectories.files.each { println "CLASS_DIR \${it}" }
                    aggregate.sourceDirectories.files.each { println "SOURCE_DIR \${it}" }
                }
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
            sourceSets {
                cli
                standalone
                fixtures
            }
        """

        when: "listing the directories the aggregate reports on"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('printAggregateDirs')
                .withPluginClasspath()
                .build()

        then: "the classes and sources of both kinds of tool are reported beside main's"
        reported(result.output, 'CLASS_DIR', 'app-module/build/classes/groovy/standalone')
        reported(result.output, 'SOURCE_DIR', 'app-module/src/standalone/groovy')
        reported(result.output, 'CLASS_DIR', 'app-module/build/classes/groovy/cli')
        reported(result.output, 'CLASS_DIR', 'app-module/build/classes/groovy/main')

        and: "a source set of another name is not production code"
        !reported(result.output, 'CLASS_DIR', 'app-module/build/classes/groovy/fixtures')
        !reported(result.output, 'SOURCE_DIR', 'app-module/src/fixtures/groovy')
    }

    def "a JVM that a test starts itself is counted, in a file of its own that the reports read"() {
        given: "a class that only a process of its own runs, and a test that starts that process with the agent the test task hands it"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = 'repositories { mavenCentral() }'
        testProjectDir.resolve('gradle.properties').toFile().text = 'jacocoVersion=0.8.13\n'
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-jacoco'
            }
            repositories { mavenCentral() }
            dependencies {
                testImplementation platform('org.junit:junit-bom:5.11.4')
                testImplementation 'org.junit.jupiter:junit-jupiter'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            tasks.named('test') { useJUnitPlatform() }
        """
        def started = moduleDir.resolve('src/main/java/com/example/Started.java').toFile()
        started.parentFile.mkdirs()
        started.text = '''
            package com.example;
            public class Started {
                public static void main(String[] args) {
                    System.out.println("started");
                }
                public static void neverCalled() {
                    System.out.println("never");
                }
            }
        '''
        def test = moduleDir.resolve('src/test/java/com/example/StartedTest.java').toFile()
        test.parentFile.mkdirs()
        test.text = '''
            package com.example;
            import java.io.File;
            import java.util.ArrayList;
            import java.util.List;
            import java.util.UUID;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertNotNull;
            class StartedTest {
                @Test
                void startsAProcess() throws Exception {
                    String agent = System.getProperty("grails.test.fork.jacoco");
                    assertNotNull(agent);
                    List<String> command = new ArrayList<>();
                    command.add(new File(System.getProperty("java.home"), "bin/java").getPath());
                    command.add(agent.replace("@fork@", UUID.randomUUID().toString()));
                    command.add("-cp");
                    command.add(System.getProperty("java.class.path"));
                    command.add("com.example.Started");
                    // in another directory than the project, as the specs start theirs
                    Process process = new ProcessBuilder(command).directory(new File(System.getProperty("java.io.tmpdir"))).inheritIO().start();
                    assertEquals(0, process.waitFor());
                }
            }
        '''

        and: "the execution data of a JVM an earlier run started"
        def stale = moduleDir.resolve('build/jacoco/test-forks/stale.exec').toFile()
        stale.parentFile.mkdirs()
        stale.text = ''

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('jacocoAggregateReport', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "the started JVM wrote one file, beside the file of the test task, and the one of the earlier run is gone"
        result.task(':app-module:test').outcome == TaskOutcome.SUCCESS
        def written = moduleDir.resolve('build/jacoco/test-forks').toFile().listFiles().toList()
        written.size() == 1
        written[0].name ==~ /[0-9a-f-]{36}\.exec/
        written[0].length() > 0
        moduleDir.resolve('build/jacoco/test.exec').toFile().isFile()

        and: "the aggregate report has what it ran as covered, and what nothing ran as missed"
        methodCovered(testProjectDir.resolve('build/reports/jacoco/aggregate/jacocoAggregateReport.xml').toFile(), 'main')
        !methodCovered(testProjectDir.resolve('build/reports/jacoco/aggregate/jacocoAggregateReport.xml').toFile(), 'neverCalled')

        and: "so has the report of the module"
        methodCovered(moduleDir.resolve('build/reports/jacoco/test/jacocoTestReport.xml').toFile(), 'main')
    }

    def "the agent argument for a started JVM is the one of the test JVM with a destination of its own"() {
        given:
        File directory = new File(testProjectDir.toFile(), 'build/jacoco/test-forks')

        expect: "the agent and its options are kept, and the destination is absolute: the started JVM has another working directory"
        ForkedJvmCoverageArgumentProvider.forForkedJvm(
                '-javaagent:/tmp/jacocoagent.jar=destfile=build/jacoco/test.exec,append=true,excludes=a.*:b.*,output=file', directory) ==
                "-javaagent:/tmp/jacocoagent.jar=destfile=${new File(directory, '@fork@.exec').absolutePath},append=true,excludes=a.*:b.*,output=file"

        and: "an argument that is not understood gives none, and the started JVMs are not counted rather than started wrongly"
        ForkedJvmCoverageArgumentProvider.forForkedJvm(argument, directory) == null

        where:
        argument << [null, '', '-javaagent:/tmp/other.jar', '-javaagent:/tmp/jacocoagent.jar=append=true']
    }

    /** Whether a JaCoCo XML report has the method of the fixture's Started class as run. */
    private static boolean methodCovered(File report, String method) {
        def parser = new XmlSlurper(false, false, true)
        parser.setFeature('http://apache.org/xml/features/nonvalidating/load-external-dtd', false)
        def node = parser.parse(report).depthFirst().find { it.name() == 'method' && it.@name == method }
        assert node != null
        node.counter.find { it.@type == 'METHOD' }.@covered == '1'
    }

    /** Whether the output lists a directory under the label, by its path relative to the test project. */
    private static boolean reported(String output, String label, String relativePath) {
        String suffix = new File(relativePath).path
        output.readLines().any { it.startsWith("${label} ") && it.endsWith(suffix) }
    }
}
