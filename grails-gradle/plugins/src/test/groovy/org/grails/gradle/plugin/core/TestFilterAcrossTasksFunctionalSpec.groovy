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
 * One test filter for the unit tests and the integration tests of a project, as
 * {@code grails test-app BookSpec} gives it: a real build with one test task per phase, two unit
 * tests and two integration tests, run with the arguments the command line gives Gradle.
 */
class TestFilterAcrossTasksFunctionalSpec extends GradleSpecification {

    private static final String LENIENT = '-Pgrails.test.filter.lenient=true'

    private GradleRunner runner

    def setup() {
        runner = setupTestResourceProject('test-filter-across-tasks')
    }

    @Unroll
    def "the filter #filter given to both tasks runs #ran and the build passes"() {
        when:
        BuildResult result = run(both(filter) + [LENIENT])

        then:
        passed(result) == ran
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.task(':integrationTest').outcome == TaskOutcome.SUCCESS
        result.task(':checkTestFilterMatched').outcome == TaskOutcome.SUCCESS

        when: 'asked again, with nothing changed'
        result = run(both(filter) + [LENIENT])

        then: 'a task that is up to date has the count of the run its results are of: the configuration is the stored one, and the build passes all the same'
        // the task test is run again, because mergeTestReports writes its report over the one of that task
        result.task(':integrationTest').outcome == TaskOutcome.UP_TO_DATE
        result.task(':checkTestFilterMatched').outcome == TaskOutcome.SUCCESS
        result.output.contains('Reusing configuration cache')

        where:
        filter                 || ran
        '*UnitOnly*'           || ['UnitOnlyTest']
        'demo.UnitOnlyTest'    || ['UnitOnlyTest']
        '*IntegrationOnly*'    || ['IntegrationOnlyTest']
        '*Both*'               || ['BothIntegrationTest', 'BothUnitTest']
        '*OnlyTest.passes'     || ['IntegrationOnlyTest', 'UnitOnlyTest']
    }

    def "a filter that matches no test of either task fails the build and is named, also when the tasks are up to date"() {
        when:
        BuildResult result = runner.withArguments(both('*Nope*') + [LENIENT, '--stacktrace']).forwardOutput().buildAndFail()

        then: 'neither test task fails: the task that looks at both does'
        result.task(':test').outcome == TaskOutcome.SUCCESS
        result.task(':integrationTest').outcome == TaskOutcome.SUCCESS
        result.task(':checkTestFilterMatched').outcome == TaskOutcome.FAILED
        result.output.contains('No tests found for the given filter (*Nope*) in any of the test tasks :test, :integrationTest')
        passed(result) == []

        when:
        result = runner.withArguments(both('*Nope*') + [LENIENT]).forwardOutput().buildAndFail()

        then:
        result.task(':integrationTest').outcome == TaskOutcome.UP_TO_DATE
        result.task(':checkTestFilterMatched').outcome == TaskOutcome.FAILED
        result.output.contains('No tests found for the given filter (*Nope*) in any of the test tasks :test, :integrationTest')
    }

    def "a test that was changed is run again with the stored configuration, and counted"() {
        given:
        run(both('*UnitOnly*') + [LENIENT])
        File source = new File(runner.projectDir, 'src/test/java/demo/UnitOnlyTest.java')
        source.text = source.text.replace('void passes() {', 'void passes() {\n        System.out.println("changed");')

        when:
        BuildResult result = run(both('*UnitOnly*') + [LENIENT])

        then:
        result.output.contains('Reusing configuration cache')
        result.task(':test').outcome == TaskOutcome.SUCCESS
        passed(result) == ['UnitOnlyTest']
        result.task(':checkTestFilterMatched').outcome == TaskOutcome.SUCCESS
    }

    def "the count an earlier build left for a task that is not part of the build is not taken for a match"() {
        given: 'a build in which the integration tests had a match'
        run(both('*Both*') + [LENIENT])

        when: 'the unit tests alone are asked for a test they do not have'
        BuildResult result = runner.withArguments(['test', '--tests', '*IntegrationOnly*', LENIENT]).forwardOutput().buildAndFail()

        then:
        result.task(':integrationTest') == null
        result.task(':checkTestFilterMatched').outcome == TaskOutcome.FAILED
        result.output.contains('No tests found for the given filter (*IntegrationOnly*) in the test task :test')
    }

    def "without a filter every test is run, with the property and without it"() {
        when:
        BuildResult result = run(['test', 'integrationTest'] + arguments)

        then:
        passed(result) == ['BothIntegrationTest', 'BothUnitTest', 'IntegrationOnlyTest', 'UnitOnlyTest']
        (result.task(':checkTestFilterMatched') != null) == arguments.contains(LENIENT)

        where:
        arguments << [[], [LENIENT]]
    }

    @Unroll
    def "without the property #tasks fails as Gradle has it, and one task with a test for the filter passes"() {
        when: 'what a command line that does not give the property asks, and what one asks of a build without this plugin'
        BuildResult result = runner.withArguments(tasks).forwardOutput().buildAndFail()

        then:
        result.task(failed).outcome == TaskOutcome.FAILED
        result.output.contains('No tests found for given includes: [*UnitOnly*](--tests filter)')
        result.task(':checkTestFilterMatched') == null

        expect:
        passed(run(passes)) == ran

        where:
        tasks                                         | failed             | passes                                               | ran
        both('*UnitOnly*')                            | ':integrationTest' | ['test', '--tests', '*UnitOnly*']                    | ['UnitOnlyTest']
        ['integrationTest', '--tests', '*UnitOnly*']  | ':integrationTest' | ['integrationTest', '--tests', '*IntegrationOnly*']  | ['IntegrationOnlyTest']
    }

    def "the property with the value false changes nothing"() {
        when:
        BuildResult result = runner.withArguments(both('*UnitOnly*') + ['-Pgrails.test.filter.lenient=false']).forwardOutput().buildAndFail()

        then:
        result.task(':integrationTest').outcome == TaskOutcome.FAILED
        result.output.contains('No tests found for given includes')
    }

    // the tasks and their filter in the order the command line gives them
    private static List<String> both(String filter) {
        ['test', '--tests', filter, 'integrationTest', '--tests', filter]
    }

    private BuildResult run(List<String> arguments) {
        runner.withArguments(arguments + ['--stacktrace']).forwardOutput().build()
    }

    // the test classes Gradle logged a passed test of
    private static List<String> passed(BuildResult result) {
        result.output.readLines().findAll { it.endsWith(' PASSED') && it.contains(' > ') }.collect { it.split(' > ')[0].trim() }.unique().sort()
    }

    @Override
    protected GradleRunner setupTestResourceProject(String projectName) {
        GradleRunner runner = super.setupTestResourceProject(projectName)
        new File(runner.projectDir, 'gradle.properties').text = "junitClasspath=${junitClasspath().replace('\\', '/')}\n"
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
