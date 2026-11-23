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

/**
 * The options of {@code GRAILS_OPTS}, or of {@code GRAILS_FORK_OPTS} in its place, reach the JVM of the application
 * that {@code bootRun} starts and that of the tests, separated by white space.
 */
class GrailsOptsForkArgumentsSpec extends GradleSpecification {

    // the arguments of the rows below, told from those the plugin and Spring Boot give the JVM themselves
    private static final List<String> PROBES = ['-Dprobe.', '-XX:MaxMetaspaceSize=']

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

    private static Map<String, String> environment(Map<String, String> options) {
        Map<String, String> environment = new LinkedHashMap<>(System.getenv())
        environment.remove('GRAILS_OPTS')
        environment.remove('GRAILS_FORK_OPTS')
        environment.putAll(options)
        environment
    }

    private static BuildResult run(GradleRunner runner, String task) {
        runner.withArguments(task, '--stacktrace').forwardOutput().build()
    }

    // what the build printed after the marker, one argument a line, between brackets
    private static List<String> arguments(BuildResult result, String marker) {
        String prefix = "${marker} [".toString()
        result.output.readLines()
                .findAll { String line -> line.startsWith(prefix) && line.endsWith(']') }
                .collect { String line -> line.substring(prefix.length(), line.length() - 1) }
    }

    private static List<String> probes(List<String> arguments) {
        arguments.findAll { String argument -> PROBES.any { argument.startsWith(it) } }.sort()
    }
}
