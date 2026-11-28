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

import org.gradle.testkit.runner.BuildResult

/**
 * The system properties a build is given with {@code -Dgrails.<name>=<value>} reach the JVMs the
 * build starts, also when the build reuses a configuration cache entry that was stored by a build
 * that was given other properties, or none. The JVMs are real ones: the application, a
 * {@code JavaExec} task of the build script, and stand-ins for the classes that run a command and a
 * script, each of which says what its system properties are.
 */
class ForkSystemPropertiesSpec extends CommandProjectSpecification {

    private static final List<String> TASKS = ['bootRun', 'probe', 'runCommand', '--arg=greet', 'runScript', 'testJvmArguments'].asImmutable()

    private static final String SAYS = '''
        static void main(String[] args) {
            ['server.port', 'probe.one', 'grails.server.port', 'grails.probe.one'].each { String name ->
                println "FORK ${LABEL} ${name}=${System.getProperty(name)}"
            }
        }
    '''

    void setup() {
        setupCommandProject('''
            tasks.register('probe', JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'example.Probe'
            }
            // a test task starts no JVM without tests: what its JVM would be started with, asked as the task would ask
            tasks.register('testJvmArguments') {
                def providers = tasks.named('test', Test).get().jvmArgumentProviders
                doLast {
                    println 'FORK test ' + providers.collectMany { it.asArguments().toList() }.findAll { it.contains('server.port') || it.contains('probe.one') }.sort()
                }
            }
        ''')
        projectFile('src/main/groovy/example/Application.groovy', """
            package example
            import grails.boot.config.GrailsAutoConfiguration
            class Application extends GrailsAutoConfiguration {
                static final String LABEL = 'bootRun'
                ${SAYS}
            }
        """)
        projectFile('src/main/groovy/example/Probe.groovy', """
            package example
            class Probe {
                static final String LABEL = 'probe'
                ${SAYS}
            }
        """)
        projectFile('src/main/groovy/grails/ui/command/GrailsApplicationContextCommandRunner.groovy', """
            package grails.ui.command
            class GrailsApplicationContextCommandRunner {
                static final String LABEL = 'runCommand'
                ${SAYS}
            }
        """)
        projectFile('src/main/groovy/grails/ui/script/GrailsApplicationScriptRunner.groovy', """
            package grails.ui.script
            class GrailsApplicationScriptRunner {
                static final String LABEL = 'runScript'
                ${SAYS}
            }
        """)
    }

    def "a property given to a later build reaches every JVM, and the configuration cache entry is reused"() {
        when: 'a build that is given no property stores the entry'
        BuildResult without = executeTask(TASKS[0], TASKS.tail())

        then:
        without.output.contains('Configuration cache entry stored')
        ['bootRun', 'probe', 'runCommand', 'runScript'].every { String task ->
            forked(without, task) == ['server.port': 'null', 'probe.one': 'null', 'grails.server.port': 'null', 'grails.probe.one': 'null']
        }
        testArguments(without) == '[]'

        when: 'a later build is given two'
        BuildResult given = executeTask(TASKS[0], TASKS.tail() + ['-Dgrails.server.port=9090', '-Dgrails.probe.one=a b'])

        then: 'the entry is reused, and the JVMs have them'
        given.output.contains('Configuration cache entry reused')
        forked(given, 'bootRun') == ['server.port': '9090', 'probe.one': 'a b', 'grails.server.port': 'null', 'grails.probe.one': 'null']
        forked(given, 'probe') == ['server.port': '9090', 'probe.one': 'a b', 'grails.server.port': 'null', 'grails.probe.one': 'null']

        and: 'the JVM of a command and of a script has them under their full names too'
        forked(given, 'runCommand') == ['server.port': '9090', 'probe.one': 'a b', 'grails.server.port': '9090', 'grails.probe.one': 'a b']
        forked(given, 'runScript') == ['server.port': '9090', 'probe.one': 'a b', 'grails.server.port': '9090', 'grails.probe.one': 'a b']
        testArguments(given) == '[-Dprobe.one=a b, -Dserver.port=9090]'

        when: 'the next one is given another value for one and not the other'
        BuildResult other = executeTask(TASKS[0], TASKS.tail() + ['-Dgrails.server.port=9191'])

        then: 'the entry is reused again, with the values of this build and nothing of the one before'
        other.output.contains('Configuration cache entry reused')
        forked(other, 'bootRun') == ['server.port': '9191', 'probe.one': 'null', 'grails.server.port': 'null', 'grails.probe.one': 'null']
        forked(other, 'probe') == ['server.port': '9191', 'probe.one': 'null', 'grails.server.port': 'null', 'grails.probe.one': 'null']
        forked(other, 'runCommand') == ['server.port': '9191', 'probe.one': 'null', 'grails.server.port': '9191', 'grails.probe.one': 'null']
        forked(other, 'runScript') == ['server.port': '9191', 'probe.one': 'null', 'grails.server.port': '9191', 'grails.probe.one': 'null']
        testArguments(other) == '[-Dserver.port=9191]'

        when: 'and one that is given none again'
        BuildResult none = executeTask(TASKS[0], TASKS.tail())

        then:
        none.output.contains('Configuration cache entry reused')
        forked(none, 'bootRun') == ['server.port': 'null', 'probe.one': 'null', 'grails.server.port': 'null', 'grails.probe.one': 'null']
        forked(none, 'runCommand') == ['server.port': 'null', 'probe.one': 'null', 'grails.server.port': 'null', 'grails.probe.one': 'null']
    }

    def "a system property the build script sets on a task is kept, and a property without a value is not passed"() {
        given:
        new File(projectDir, 'commands.gradle') << '''
            tasks.named('probe') {
                systemProperty 'server.port', '7070'
            }
        '''

        when:
        BuildResult result = executeTask(TASKS[0], TASKS.tail() + ['-Dgrails.server.port=9090', '-Dgrails.probe.one='])

        then:
        forked(result, 'probe') == ['server.port': '7070', 'probe.one': 'null', 'grails.server.port': 'null', 'grails.probe.one': 'null']
        forked(result, 'bootRun') == ['server.port': '9090', 'probe.one': 'null', 'grails.server.port': 'null', 'grails.probe.one': 'null']
        forked(result, 'runCommand') == ['server.port': '9090', 'probe.one': 'null', 'grails.server.port': '9090', 'grails.probe.one': 'null']
    }

    def "a system property a task sets when it runs is kept over the one the build is given, with the configuration cache disabled, stored and reused"() {
        given: 'tasks that set, as they start, a property the build is also given: under the name the JVM sees it by, which is the name without the prefix for every task and the full name too for a command and a script'
        new File(projectDir, 'commands.gradle') << '''
            tasks.named('probe') {
                doFirst { systemProperty 'server.port', '7070' }
            }
            tasks.named('bootRun') {
                doFirst { systemProperty 'probe.one', 'task' }
            }
            tasks.named('runCommand') {
                doFirst { systemProperty 'grails.probe.one', 'task' }
            }
            tasks.named('runScript') {
                doFirst {
                    systemProperty 'server.port', '7171'
                    systemProperty 'grails.server.port', '7272'
                }
            }
        '''
        List<String> arguments = TASKS.tail() + ['-Dgrails.server.port=9090', '-Dgrails.probe.one=given']

        when:
        BuildResult disabled = executeTask(TASKS[0], arguments + ['--no-configuration-cache'])
        BuildResult stored = executeTask(TASKS[0], arguments)
        BuildResult reused = executeTask(TASKS[0], arguments)

        then:
        !disabled.output.contains('Configuration cache entry')
        stored.output.contains('Configuration cache entry stored')
        reused.output.contains('Configuration cache entry reused')

        and: 'the value of the task, in every one of them'
        Map<String, Map<String, String>> kept = [
                probe: ['server.port': '7070', 'probe.one': 'given', 'grails.server.port': 'null', 'grails.probe.one': 'null'],
                bootRun: ['server.port': '9090', 'probe.one': 'task', 'grails.server.port': 'null', 'grails.probe.one': 'null'],
                runCommand: ['server.port': '9090', 'probe.one': 'given', 'grails.server.port': '9090', 'grails.probe.one': 'task'],
                runScript: ['server.port': '7171', 'probe.one': 'given', 'grails.server.port': '7272', 'grails.probe.one': 'given'],
        ]
        verifyAll {
            forkedByTask(disabled) == kept
            forkedByTask(stored) == kept
            forkedByTask(reused) == kept
        }
    }

    private static Map<String, Map<String, String>> forkedByTask(BuildResult result) {
        ['probe', 'bootRun', 'runCommand', 'runScript'].collectEntries { String task -> [(task): forked(result, task)] }
    }

    private static Map<String, String> forked(BuildResult result, String label) {
        result.output.readLines().findAll { it.startsWith("FORK ${label} ") }.collectEntries { String line ->
            String said = line.substring("FORK ${label} ".length())
            [(said.substring(0, said.indexOf('='))): said.substring(said.indexOf('=') + 1)]
        }
    }

    private static String testArguments(BuildResult result) {
        result.output.readLines().find { it.startsWith('FORK test ') }.substring('FORK test '.length())
    }
}
