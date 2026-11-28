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
 * The options of {@code GRAILS_OPTS}, or of {@code GRAILS_FORK_OPTS} in its place, reach the JVM of the application
 * that {@code bootRun} starts and that of the tests, separated by white space. Those JVMs are given the bounds of the
 * plugin's heap that their options do not name.
 */
class GrailsOptsForkArgumentsSpec extends GradleSpecification {

    // the arguments of the rows below, told from those the plugin and Spring Boot give the JVM themselves
    private static final List<String> PROBES = ['-Dprobe.', '-XX:MaxMetaspaceSize=']

    // the options that name a bound of the heap of a JVM
    private static final List<String> HEAP_OPTIONS = ['-Xms', '-Xmx', '-XX:InitialHeapSize=', '-XX:MinHeapSize=', '-XX:MaxHeapSize=']

    // the heap the plugin gives a JVM whose options name none
    private static final List<String> DEFAULT_HEAP = ['-Xms768m', '-Xmx768m']

    def "#name in #variable reach the application and the tests as #expected"() {
        given:
        GradleRunner runner = setupTestResourceProject('fork-grails-opts')
                .withEnvironment(environment([(variable): value] + others))

        when: 'the application is run, storing and then reusing the configuration cache entry'
        BuildResult stored = run(runner, 'bootRun')
        BuildResult reused = run(runner, 'bootRun')

        then:
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')

        and: 'its JVM is given each option as one argument, and no empty argument'
        [stored, reused].each { BuildResult result ->
            assert result.output.contains('APPLICATION_STARTED')
            assert probes(arguments(result, 'FORKED_JVM_ARG')) == expected.sort()
            assert !arguments(result, 'FORKED_JVM_ARG').contains('')
        }

        when: 'the arguments of the test task are read, storing and then reusing the configuration cache entry'
        BuildResult testStored = run(runner, 'inspectTestJvmArgs')
        BuildResult testReused = run(runner, 'inspectTestJvmArgs')

        then:
        testStored.output.contains('Configuration cache entry stored')
        testReused.output.contains('Configuration cache entry reused')
        [testStored, testReused].each { BuildResult result ->
            assert probes(arguments(result, 'TEST_JVM_ARG')) == expected.sort()
            assert !arguments(result, 'TEST_JVM_ARG').contains('')
        }

        cleanup: 'the runner is shared by the features of the spec'
        runner?.withEnvironment(null)

        where:
        name                                 | variable           | value                                                  | others                             || expected
        'options separated by one space'     | 'GRAILS_OPTS'      | '-Dprobe.one=1 -Dprobe.two=2'                          | [:]                                || ['-Dprobe.one=1', '-Dprobe.two=2']
        'options separated by two spaces'    | 'GRAILS_OPTS'      | '-Dprobe.one=1  -Dprobe.two=2'                         | [:]                                || ['-Dprobe.one=1', '-Dprobe.two=2']
        'options between spaces'             | 'GRAILS_OPTS'      | '  -Dprobe.one=1 -XX:MaxMetaspaceSize=512m   '         | [:]                                || ['-Dprobe.one=1', '-XX:MaxMetaspaceSize=512m']
        'options separated by a tab'         | 'GRAILS_OPTS'      | '-Dprobe.one=1\t-Dprobe.two=2'                         | [:]                                || ['-Dprobe.one=1', '-Dprobe.two=2']
        'options on lines of their own'      | 'GRAILS_OPTS'      | '\n-Dprobe.one=1\n\t-Dprobe.two=2\n'                   | [:]                                || ['-Dprobe.one=1', '-Dprobe.two=2']
        'spaces only'                        | 'GRAILS_OPTS'      | '   '                                                  | [:]                                || []
        'options in place of GRAILS_OPTS'    | 'GRAILS_FORK_OPTS' | ' -Dprobe.fork=1  -XX:MaxMetaspaceSize=256m '          | [GRAILS_OPTS: '-Dprobe.cli=1']     || ['-Dprobe.fork=1', '-XX:MaxMetaspaceSize=256m']
        'an empty value'                     | 'GRAILS_FORK_OPTS' | ''                                                     | [GRAILS_OPTS: ' -Dprobe.cli=1  ']  || ['-Dprobe.cli=1']
    }

    def "a changed GRAILS_OPTS is given to the application and not the configuration of the build before"() {
        given:
        GradleRunner runner = setupTestResourceProject('fork-grails-opts')
        run(runner.withEnvironment(environment(GRAILS_OPTS: '-Dprobe.first=1  -Dprobe.both=1')), 'bootRun')

        when:
        BuildResult changed = run(runner.withEnvironment(environment(GRAILS_OPTS: ' -Dprobe.second=2  -Dprobe.both=1')), 'bootRun')

        then: 'the configuration cache entry of the first value is not reused'
        !changed.output.contains('Configuration cache entry reused')
        changed.output.contains("environment variable 'GRAILS_OPTS' has changed")

        and:
        probes(arguments(changed, 'FORKED_JVM_ARG')) == ['-Dprobe.both=1', '-Dprobe.second=2']
        !arguments(changed, 'FORKED_JVM_ARG').contains('')

        cleanup:
        runner?.withEnvironment(null)
    }

    def "the options of GRAILS_OPTS win over the heap and the system properties build.gradle gives bootRun, and come before its jvmArgs"() {
        given:
        GradleRunner runner = setupTestResourceProject('fork-grails-opts')
                .withEnvironment(environment(GRAILS_OPTS: '-Dprobe.one=variable  -Xms600m -Xmx1500m -XX:MaxMetaspaceSize=300m'))
        new File(runner.projectDir, 'build.gradle') << '''
            bootRun {
                systemProperty 'probe.one', 'script'
                systemProperty 'probe.two', 'script'
                minHeapSize = '512m'
                maxHeapSize = '1g'
                jvmArgs('-XX:MaxMetaspaceSize=200m')
            }
        '''.stripIndent()

        when:
        BuildResult result = run(runner, 'bootRun')
        List<String> forked = arguments(result, 'FORKED_JVM_ARG')

        then: 'the variable sets the heap, and the system property it names'
        result.output.contains('APPLICATION_STARTED')
        probes(forked).findAll { it.startsWith('-D') } == ['-Dprobe.one=variable', '-Dprobe.two=script']
        forked.findAll { it.startsWith('-Xms') || it.startsWith('-Xmx') } == ['-Xms600m', '-Xmx1500m']

        and: 'another option of the variable comes before the jvmArgs of the task, and the JVM takes the last one'
        forked.findAll { it.startsWith('-XX:MaxMetaspaceSize=') } == ['-XX:MaxMetaspaceSize=300m', '-XX:MaxMetaspaceSize=200m']

        cleanup:
        runner?.withEnvironment(null)
    }

    def "the application and the tests are given the bounds of the plugin's heap that their options do not name: #name"() {
        given:
        GradleRunner runner = heapProject(variables, configuration)

        when: 'the application is run, storing and then reusing the configuration cache entry'
        BuildResult stored = run(runner, 'bootRun')
        BuildResult reused = run(runner, 'bootRun')

        then:
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')

        and: 'it starts with the heap its options name and the bounds of the plugin they do not name'
        [stored, reused].each { BuildResult result ->
            assert result.output.contains('APPLICATION_STARTED')
            assert heap(arguments(result, 'FORKED_JVM_ARG')) == expected.sort()
        }

        when: 'the tests are run, storing and then reusing the configuration cache entry'
        BuildResult testStored = run(runner, 'test')
        BuildResult testReused = run(runner, 'test')

        then:
        testStored.output.contains('Configuration cache entry stored')
        testReused.output.contains('Configuration cache entry reused')

        and: 'their JVM starts with the same heap, and the -Xmx512m Gradle gives a test JVM whose task has no maxHeapSize'
        [testStored, testReused].each { BuildResult result ->
            assert result.task(':test').outcome == TaskOutcome.SUCCESS
            assert heap(arguments(result, 'RUNNING_TEST_JVM_ARG')) == (expected + gradleAdds).sort()
        }

        cleanup:
        runner?.withEnvironment(null)

        where:
        name                                                       | variables                                                  | configuration                         || expected                                                    | gradleAdds
        'nothing names the heap'                                   | [:]                                                        | ''                                    || DEFAULT_HEAP                                                | []
        'GRAILS_OPTS names no heap'                                | [GRAILS_OPTS: '-Dprobe.one=1']                             | ''                                    || DEFAULT_HEAP                                                | []
        'GRAILS_OPTS names a maximum below the default'            | [GRAILS_OPTS: '-Xmx512m']                                  | ''                                    || ['-Xmx512m']                                                | []
        'GRAILS_OPTS names a maximum above the default'            | [GRAILS_OPTS: '-Xmx1g']                                    | ''                                    || ['-Xmx1g']                                                  | []
        'GRAILS_OPTS names an initial heap below the default'      | [GRAILS_OPTS: '-Xms256m']                                  | ''                                    || ['-Xms256m', '-Xmx768m']                                    | []
        'GRAILS_OPTS names an initial heap above the default'      | [GRAILS_OPTS: '-Xms1g']                                    | ''                                    || ['-Xms1g', '-Xmx1g']                                        | []
        'GRAILS_OPTS names both'                                   | [GRAILS_OPTS: '-Xms256m  -Xmx512m']                        | ''                                    || ['-Xms256m', '-Xmx512m']                                    | []
        'GRAILS_FORK_OPTS names a maximum'                         | [GRAILS_FORK_OPTS: '-Xmx512m', GRAILS_OPTS: '-Dprobe.c=1'] | ''                                    || ['-Xmx512m']                                                | []
        'GRAILS_FORK_OPTS replaces a GRAILS_OPTS with a heap'      | [GRAILS_FORK_OPTS: '-Dprobe.f=1', GRAILS_OPTS: '-Xmx512m'] | ''                                    || DEFAULT_HEAP                                                | []
        'GRAILS_OPTS names -XX:MaxHeapSize'                        | [GRAILS_OPTS: '-XX:MaxHeapSize=600m']                      | ''                                    || ['-XX:MaxHeapSize=600m']                                    | ['-Xmx512m']
        'GRAILS_OPTS names -XX:InitialHeapSize above the default'  | [GRAILS_OPTS: '-XX:InitialHeapSize=1g']                    | ''                                    || ['-XX:InitialHeapSize=1g', '-Xmx1g']                        | []
        'GRAILS_OPTS names -XX:MinHeapSize below the default'      | [GRAILS_OPTS: '-XX:MinHeapSize=256m']                      | ''                                    || ['-XX:MinHeapSize=256m', '-Xmx768m']                        | []
        'GRAILS_OPTS names two initial heaps in two units'         | [GRAILS_OPTS: '-Xms800m -XX:MinHeapSize=921600k']          | ''                                    || ['-XX:MinHeapSize=921600k', '-Xms800m', '-Xmx921600k']      | []
        'build.gradle sets maxHeapSize below the default'          | [:]                                                        | "maxHeapSize = '512m'"                || ['-Xmx512m']                                                | []
        'build.gradle sets minHeapSize above the default'          | [:]                                                        | "minHeapSize = '1g'"                  || ['-Xms1g', '-Xmx1g']                                        | []
        'build.gradle gives -Xmx in jvmArgs'                       | [:]                                                        | "jvmArgs('-Xmx512m')"                 || ['-Xmx512m']                                                | []
        'build.gradle gives -XX:MaxHeapSize in jvmArgs'            | [:]                                                        | "jvmArgs('-XX:MaxHeapSize=600m')"     || ['-XX:MaxHeapSize=600m']                                    | ['-Xmx512m']
        'build.gradle sets the maximum, GRAILS_OPTS the initial'   | [GRAILS_OPTS: '-Xms1g']                                    | "maxHeapSize = '2g'"                  || ['-Xms1g', '-Xmx2g']                                        | []
        'a doFirst of the task sets maxHeapSize'                   | [:]                                                        | "doFirst { it.maxHeapSize = '512m' }" || ['-Xmx512m']                                                | []
    }

    def "the application and the tests are given the bounds of the plugin's heap that their options do not name without the configuration cache: #name"() {
        given:
        GradleRunner runner = heapProject(variables, configuration)

        when:
        BuildResult application = run(runner, 'bootRun', '--no-configuration-cache')
        BuildResult tests = run(runner, 'test', '--no-configuration-cache')

        then:
        !application.output.contains('Configuration cache entry')
        application.output.contains('APPLICATION_STARTED')
        heap(arguments(application, 'FORKED_JVM_ARG')) == expected.sort()

        and:
        !tests.output.contains('Configuration cache entry')
        tests.task(':test').outcome == TaskOutcome.SUCCESS
        heap(arguments(tests, 'RUNNING_TEST_JVM_ARG')) == expected.sort()

        cleanup:
        runner?.withEnvironment(null)

        where:
        name                                              | variables                 | configuration                         || expected
        'nothing names the heap'                          | [:]                       | ''                                    || DEFAULT_HEAP
        'GRAILS_OPTS names a maximum below the default'   | [GRAILS_OPTS: '-Xmx512m'] | ''                                    || ['-Xmx512m']
        'a doFirst of the task sets maxHeapSize'          | [:]                       | "doFirst { it.maxHeapSize = '512m' }" || ['-Xmx512m']
        'a doFirst of the task sets a large minHeapSize'  | [:]                       | "doFirst { it.minHeapSize = '1g' }"   || ['-Xms1g', '-Xmx1g']
    }

    // the project, run with the environment variables given and what build.gradle sets on bootRun and on test
    private GradleRunner heapProject(Map<String, String> variables, String configuration) {
        GradleRunner runner = setupTestResourceProject('fork-grails-opts').withEnvironment(environment(variables))
        if (configuration) {
            new File(runner.projectDir, 'build.gradle') << """
                tasks.named('bootRun') { ${configuration} }
                tasks.named('test') { ${configuration} }
            """.stripIndent()
        }
        runner
    }

    @Override
    protected GradleRunner setupTestResourceProject(String projectName) {
        GradleRunner runner = super.setupTestResourceProject(projectName)
        new File(runner.projectDir, 'gradle.properties') << "\njunitClasspath=${junitClasspath().replace('\\', '/')}\n"
        runner
    }

    // the jars of JUnit this specification runs with, for a build that has no repository to take them from
    private static String junitClasspath() {
        ['org.junit.jupiter.api.Test', 'org.junit.jupiter.engine.JupiterTestEngine', 'org.junit.platform.engine.TestEngine',
         'org.junit.platform.launcher.Launcher', 'org.junit.platform.commons.JUnitException',
         'org.opentest4j.AssertionFailedError'].collect { String name ->
            new File(Class.forName(name).protectionDomain.codeSource.location.toURI()).path
        }.unique().join(File.pathSeparator)
    }

    private static Map<String, String> environment(Map<String, String> options) {
        Map<String, String> environment = new LinkedHashMap<>(System.getenv())
        environment.remove('GRAILS_OPTS')
        environment.remove('GRAILS_FORK_OPTS')
        environment.putAll(options)
        environment
    }

    private static BuildResult run(GradleRunner runner, String task, String... options) {
        runner.withArguments([task, '--stacktrace'] + options.toList()).forwardOutput().build()
    }

    // what the build printed after the marker, one argument a line, between brackets; Gradle indents what a test prints
    private static List<String> arguments(BuildResult result, String marker) {
        String prefix = "${marker} [".toString()
        result.output.readLines()
                .collect { String line -> line.trim() }
                .findAll { String line -> line.startsWith(prefix) && line.endsWith(']') }
                .collect { String line -> line.substring(prefix.length(), line.length() - 1) }
    }

    private static List<String> probes(List<String> arguments) {
        arguments.findAll { String argument -> PROBES.any { argument.startsWith(it) } }.sort()
    }

    private static List<String> heap(List<String> arguments) {
        arguments.findAll { String argument -> HEAP_OPTIONS.any { argument.startsWith(it) } }.sort()
    }
}
