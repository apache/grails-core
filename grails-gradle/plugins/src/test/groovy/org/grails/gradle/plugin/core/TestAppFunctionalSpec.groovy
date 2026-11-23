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
import spock.lang.Unroll

/**
 * The task {@code testApp}, which {@code grails test-app} runs: a real build with the unit tests,
 * the integration tests, a phase the project adds ({@code functionalTest}) and a {@code Test} task
 * that is no phase of the Grails Gradle plugin ({@code smoke}, which runs the functional tests
 * again), two test classes each.
 */
class TestAppFunctionalSpec extends GradleSpecification {

    private static final List<String> EVERY_TASK = [':test', ':integrationTest', ':functionalTest', ':smoke']
    private static final List<String> EVERY_TEST = ['BothFunctionalTest', 'BothIntegrationTest', 'BothUnitTest', 'FunctionalOnlyTest', 'IntegrationOnlyTest', 'UnitOnlyTest']

    private GradleRunner runner

    def setup() {
        runner = setupTestResourceProject('test-app-phases')
    }

    def "testApp runs every Test task of the project, the unit tests first and the integration tests before the others"() {
        when:
        BuildResult result = run(['testApp'])
        List<String> order = result.tasks*.path.findAll { it in EVERY_TASK }

        then:
        EVERY_TASK.every { result.task(it).outcome == TaskOutcome.SUCCESS }
        result.task(':testApp').outcome == TaskOutcome.SUCCESS
        passed(result) == EVERY_TEST
        order.take(2) == [':test', ':integrationTest']
        order.drop(2).sort() == [':functionalTest', ':smoke']

        and: 'one after the other, also where Gradle runs the tasks of a project in parallel'
        List<String> lines = result.output.readLines()
        lines.indexOf('> Task :test') < lines.indexOf('> Task :integrationTest')
        lines.indexOf('> Task :integrationTest') < [lines.indexOf('> Task :functionalTest'), lines.indexOf('> Task :smoke')].min()

        when: 'asked again, with nothing changed'
        result = run(['testApp'])

        then: 'the configuration is the stored one, and what is up to date is not run again'
        result.output.contains('Reusing configuration cache')
        // test and smoke are run again, because mergeTestReports writes its report over the ones of those tasks
        [':integrationTest', ':functionalTest'].every { result.task(it).outcome == TaskOutcome.UP_TO_DATE }
        result.task(':testApp').outcome == TaskOutcome.SUCCESS
    }

    @Unroll
    def "testApp with the phases #phases runs #tasks and no other test task"() {
        when:
        BuildResult result = run(['testApp'] + phases.collectMany { ['--phase', it] })

        then:
        result.tasks*.path.findAll { it in EVERY_TASK }.sort() == tasks.sort()
        tasks.every { result.task(it).outcome == TaskOutcome.SUCCESS }
        passed(result) == ran

        where:
        phases                     || tasks                          | ran
        ['unit']                   || [':test']                      | ['BothUnitTest', 'UnitOnlyTest']
        ['integration']            || [':integrationTest']           | ['BothIntegrationTest', 'IntegrationOnlyTest']
        ['functional']             || [':functionalTest']            | ['BothFunctionalTest', 'FunctionalOnlyTest']
        ['smoke']                  || [':smoke']                     | ['BothFunctionalTest', 'FunctionalOnlyTest']
        ['unit', 'functional']     || [':test', ':functionalTest']   | ['BothFunctionalTest', 'BothUnitTest', 'FunctionalOnlyTest', 'UnitOnlyTest']
        ['functional', 'unit']     || [':test', ':functionalTest']   | ['BothFunctionalTest', 'BothUnitTest', 'FunctionalOnlyTest', 'UnitOnlyTest']
    }

    def "a phase the project does not have fails the build before a test is run, and the phases it has are named"() {
        when:
        BuildResult result = runner.withArguments(['testApp', '--phase', 'unit', '--phase', 'acceptance']).forwardOutput().buildAndFail()

        then:
        result.output.contains('This project has no test phase acceptance; its test phases are: unit, integration, functional, smoke.')
        result.tasks.empty
    }

    @Unroll
    def "the tests #filter are looked for in every phase and run where they are (#ran): a phase without them passes"() {
        when:
        BuildResult result = run(['testApp', '--tests', filter])

        then:
        passed(result) == ran
        EVERY_TASK.every { result.task(it).outcome == TaskOutcome.SUCCESS }
        result.task(':testApp').outcome == TaskOutcome.SUCCESS

        when: 'asked again, with nothing changed'
        result = run(['testApp', '--tests', filter])

        then: 'a task that is up to date has the count of the run its results are of'
        result.output.contains('Reusing configuration cache')
        result.task(':functionalTest').outcome == TaskOutcome.UP_TO_DATE
        result.task(':testApp').outcome == TaskOutcome.SUCCESS

        where:
        filter                || ran
        '*UnitOnly*'          || ['UnitOnlyTest']
        'demo.UnitOnlyTest'   || ['UnitOnlyTest']
        '*FunctionalOnly*'    || ['FunctionalOnlyTest']
        '*Both*'              || ['BothFunctionalTest', 'BothIntegrationTest', 'BothUnitTest']
        '*OnlyTest.passes'    || ['FunctionalOnlyTest', 'IntegrationOnlyTest', 'UnitOnlyTest']
    }

    def "several filters are one filter, and with phases it is looked for in those alone"() {
        when:
        BuildResult result = run(['testApp', '--tests', '*UnitOnly*', '--tests', '*IntegrationOnly*', '--phase', 'unit', '--phase', 'functional'])

        then:
        passed(result) == ['UnitOnlyTest']
        result.task(':integrationTest') == null
        result.task(':functionalTest').outcome == TaskOutcome.SUCCESS
    }

    def "tests that no phase has fail the build in testApp, which names the filter and the tasks, also when the tasks are up to date"() {
        when:
        BuildResult result = runner.withArguments(['testApp', '--tests', '*Nope*']).forwardOutput().buildAndFail()

        then: 'no test task fails: the task that looks at all of them does'
        EVERY_TASK.every { result.task(it).outcome == TaskOutcome.SUCCESS }
        result.task(':testApp').outcome == TaskOutcome.FAILED
        result.output.contains('No tests found for the given filter (*Nope*) in any of the test tasks :test, :integrationTest, :functionalTest, :smoke')
        passed(result) == []

        when:
        result = runner.withArguments(['testApp', '--tests', '*Nope*']).forwardOutput().buildAndFail()

        then:
        result.task(':functionalTest').outcome == TaskOutcome.UP_TO_DATE
        result.task(':testApp').outcome == TaskOutcome.FAILED
    }

    def "tests that the phases that are run do not have are not found, whatever another phase ran in an earlier build"() {
        given:
        run(['testApp', '--tests', '*IntegrationOnly*'])

        when:
        BuildResult result = runner.withArguments(['testApp', '--phase', 'unit', '--tests', '*IntegrationOnly*']).forwardOutput().buildAndFail()

        then:
        result.task(':integrationTest') == null
        result.task(':testApp').outcome == TaskOutcome.FAILED
        result.output.contains('No tests found for the given filter (*IntegrationOnly*) in the test task :test:')
    }

    @Unroll
    def "after a filter the unit tests matched, a filter that matches nothing fails where the unit tests are #how, #cache"() {
        given:
        run(['testApp', '--tests', '*UnitOnly*'] + cacheArguments)

        when: 'what the unit tests counted in the build before is no match of this one'
        BuildResult result = fail(['testApp', '--tests', '*Nope*'] + phaseArguments + cacheArguments)

        then:
        result.task(':testApp').outcome == TaskOutcome.FAILED
        result.output.contains("No tests found for the given filter (*Nope*) in any of the test tasks ${searched}:")
        passed(result) == []

        when: 'asked again'
        result = fail(['testApp', '--tests', '*Nope*'] + phaseArguments + cacheArguments)

        then:
        result.output.contains('Reusing configuration cache') == cacheArguments.isEmpty()
        result.task(':testApp').outcome == TaskOutcome.FAILED
        result.output.contains("No tests found for the given filter (*Nope*) in any of the test tasks ${searched}:")

        where:
        how                                   | phaseArguments           | cacheArguments                 || searched
        'run again'                           | []                       | []                             || ':test, :integrationTest, :functionalTest, :smoke'
        'left out of the build (-x)'          | ['-x', 'test']           | []                             || ':integrationTest, :functionalTest, :smoke'
        'skipped by an onlyIf'                | ['-PskipPhase=test']     | []                             || ':integrationTest, :functionalTest, :smoke; :test was skipped'
        'turned off (enabled = false)'        | ['-PdisablePhase=test']  | []                             || ':integrationTest, :functionalTest, :smoke; :test was skipped'
        'run again'                           | []                       | ['--no-configuration-cache']   || ':test, :integrationTest, :functionalTest, :smoke'
        'left out of the build (-x)'          | ['-x', 'test']           | ['--no-configuration-cache']   || ':integrationTest, :functionalTest, :smoke'
        'skipped by an onlyIf'                | ['-PskipPhase=test']     | ['--no-configuration-cache']   || ':integrationTest, :functionalTest, :smoke; :test was skipped'
        'turned off (enabled = false)'        | ['-PdisablePhase=test']  | ['--no-configuration-cache']   || ':integrationTest, :functionalTest, :smoke; :test was skipped'

        cache = cacheArguments ? 'without the configuration cache' : 'with the configuration cache stored and reused'
    }

    @Unroll
    def "where no phase that is asked for runs, a filter that matched in an earlier build fails: the unit tests #how, #cache"() {
        given:
        run(['testApp', '--phase', 'unit', '--tests', '*UnitOnly*'] + cacheArguments)

        when:
        BuildResult result = fail(['testApp', '--phase', 'unit', '--tests', '*UnitOnly*'] + phaseArguments + cacheArguments)

        then:
        result.task(':testApp').outcome == TaskOutcome.FAILED
        result.output.contains("No test task ran for the given filter (*UnitOnly*): ${why}")
        passed(result) == []

        where:
        how                                   | phaseArguments           | cacheArguments                 || why
        'left out of the build (-x)'          | ['-x', 'test']           | []                             || 'none of the test tasks is part of the build'
        'skipped by an onlyIf'                | ['-PskipPhase=test']     | []                             || ':test was skipped'
        'left out of the build (-x)'          | ['-x', 'test']           | ['--no-configuration-cache']   || 'none of the test tasks is part of the build'
        'skipped by an onlyIf'                | ['-PskipPhase=test']     | ['--no-configuration-cache']   || ':test was skipped'

        cache = cacheArguments ? 'without the configuration cache' : 'with the configuration cache'
    }

    @Unroll
    def "a phase that is up to date or taken from the build cache counts the tests it ran, where another phase is skipped, #cache"() {
        given:
        run(['testApp', '--tests', '*IntegrationOnly*', '--build-cache'] + cacheArguments)

        when: 'nothing changed, and the unit tests are skipped'
        BuildResult result = run(['testApp', '--tests', '*IntegrationOnly*', '--build-cache', '-PskipPhase=test'] + cacheArguments)

        then:
        result.task(':test').outcome == TaskOutcome.SKIPPED
        result.task(':integrationTest').outcome == TaskOutcome.UP_TO_DATE
        result.task(':testApp').outcome == TaskOutcome.SUCCESS

        when: 'the build directory is gone, and the results of the tests are taken from the build cache'
        new File(runner.projectDir, 'build').deleteDir()
        result = run(['testApp', '--tests', '*IntegrationOnly*', '--build-cache', '-PskipPhase=test'] + cacheArguments)

        then:
        result.output.contains('Reusing configuration cache') == cacheArguments.isEmpty()
        result.task(':test').outcome == TaskOutcome.SKIPPED
        result.task(':integrationTest').outcome == TaskOutcome.FROM_CACHE
        result.task(':testApp').outcome == TaskOutcome.SUCCESS
        passed(result) == []

        where:
        cacheArguments << [[], ['--no-configuration-cache']]
        cache = cacheArguments ? 'without the configuration cache' : 'with the configuration cache stored and reused'
    }

    def "a test task that is asked for by its own name is as Gradle has it: without a test for its filter it fails"() {
        when:
        BuildResult result = runner.withArguments(['integrationTest', '--tests', '*UnitOnly*']).forwardOutput().buildAndFail()

        then:
        result.task(':integrationTest').outcome == TaskOutcome.FAILED
        result.output.contains('No tests found for given includes')
    }

    def "--rerun-tests runs the tests again where nothing changed, also where the build cache has their results"() {
        given:
        run(['testApp', '--build-cache'])

        expect: 'without it nothing is run again'
        run(['testApp', '--build-cache']).task(':functionalTest').outcome == TaskOutcome.UP_TO_DATE

        when:
        BuildResult result = run(['testApp', '--rerun-tests', '--build-cache'])

        then:
        EVERY_TASK.every { result.task(it).outcome == TaskOutcome.SUCCESS }
        passed(result) == EVERY_TEST

        when: 'with the phases that are named, those alone'
        result = run(['testApp', '--rerun-tests', '--phase', 'integration', '--build-cache'])

        then:
        result.task(':integrationTest').outcome == TaskOutcome.SUCCESS
        result.task(':functionalTest') == null
        passed(result) == ['BothIntegrationTest', 'IntegrationOnlyTest']
    }

    def "--debug-jvm starts the JVM of every phase that is run for a debugger, and of no test task without it"() {
        given:
        ServerSocket free = new ServerSocket(0)
        int port = free.localPort
        free.close()

        when:
        BuildResult result = run(['testApp', '--debug-jvm', "-PdebugPort=${port}".toString()])

        then: 'one after the other on the one port, or the second could not listen on it'
        ['test', 'integrationTest', 'functionalTest', 'smoke'].every { result.output.contains("debug of ${it}: true") }
        passed(result) == EVERY_TEST

        when:
        result = run(['testApp', '--phase', 'unit', '--debug-jvm', "-PdebugPort=${port}".toString()])

        then:
        result.output.contains('debug of test: true')
        !result.output.contains('debug of smoke')

        when:
        result = run(['testApp', '--rerun-tests'])

        then:
        ['test', 'integrationTest', 'functionalTest', 'smoke'].every { result.output.contains("debug of ${it}: false") }
    }

    def "a plugin project has the task as an application has"() {
        given:
        setupTestResourceProject('plugin-forge-pack')

        when:
        BuildResult result = executeTask('testApp', ['--dry-run', '--tests', 'BookSpec'])

        then:
        result.output.contains(':test SKIPPED')
        result.output.contains(':integrationTest SKIPPED')
        result.output.contains(':testApp SKIPPED')
    }

    private BuildResult run(List<String> arguments) {
        runner.withArguments(arguments + ['--stacktrace']).forwardOutput().build()
    }

    private BuildResult fail(List<String> arguments) {
        runner.withArguments(arguments).forwardOutput().buildAndFail()
    }

    // the test classes Gradle logged a passed test of
    private static List<String> passed(BuildResult result) {
        result.output.readLines().findAll { it.endsWith(' PASSED') && it.contains(' > ') }.collect { it.split(' > ')[0].trim() }.unique().sort()
    }

    @Override
    protected GradleRunner setupTestResourceProject(String projectName) {
        GradleRunner runner = super.setupTestResourceProject(projectName)
        if (projectName == 'test-app-phases') {
            new File(runner.projectDir, 'gradle.properties').text = "junitClasspath=${junitClasspath().replace('\\', '/')}\n"
        }
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
}
