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
package org.apache.grails.forge.cli.console

import java.nio.file.Files
import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * {@code test-app}: the Gradle tasks {@code test} and, in a project that has a
 * {@code src/integration-test} directory, {@code integrationTest}, for the test environment unless
 * another one is given. The names of tests are the filter of both tasks.
 *
 * <p>Gradle fails a test task that has no test for its filter, so a filter that is given to both
 * tasks is given with the project property {@value #LENIENT_FILTER}: the Grails Gradle plugin then
 * lets a task without a match pass and fails the build when neither task ran a test. A build without
 * that plugin does not know the property and fails as Gradle does.</p>
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'test-app',
        description = [
                'Runs the tests of the Grails project.',
                'Runs the Gradle task test and, where the project has a src/integration-test directory, integrationTest, ' +
                        'for the test environment unless -Dgrails.env names another one.',
                'What follows @|bold --|@ is given to Gradle as it is.'
        ],
        preprocessor = GradleTaskCommand.PassedOn)
@CompileStatic
class TestAppCommand extends GradleEnvironmentCommand {

    private static final String INTEGRATION_TESTS = 'src/integration-test'

    /** The project property of the Grails Gradle plugin for a filter that is given to several test tasks. */
    static final String LENIENT_FILTER = 'grails.test.filter.lenient'

    @CommandLine.Parameters(arity = '0..*', paramLabel = 'TEST',
            description = 'The tests to run, as the filter of Gradle: a class name or a pattern such as example.BookSpec, *BookSpec or "*BookSpec.saves a book".')
    List<String> names = []

    @CommandLine.Option(names = ['--tests'], paramLabel = 'TEST', description = 'A test to run, as TEST above; can be given more than once.')
    List<String> tests = []

    @CommandLine.Option(names = ['--unit', '-unit'], description = 'Run the unit tests: the task test.')
    boolean unit

    @CommandLine.Option(names = ['--integration', '-integration'], description = 'Run the integration tests: the task integrationTest.')
    boolean integration

    @CommandLine.Option(names = ['--clean', '-clean'], description = 'Run every test again, also where nothing changed: cleanTest and cleanIntegrationTest come first.')
    boolean clean

    @CommandLine.Option(names = ['--continuous', '-continuous'], description = 'Run the tests again whenever a file they depend on changes: the continuous build of Gradle.')
    boolean continuous

    @CommandLine.Option(names = ['--debug-jvm', '-debug-jvm'], description = 'Start the JVM of the tests suspended, waiting for a debugger on port 5005.')
    boolean debugJvm

    // whether the unit tests and the integration tests are both run
    private boolean both

    @Override
    protected String defaultEnvironment() {
        return 'test'
    }

    @Override
    protected List<String> gradleArguments(ConsoleProject project) {
        boolean hasIntegrationTests = Files.isDirectory(project.directory.resolve(INTEGRATION_TESTS))
        List<String> arguments = []
        if (unit || !integration) {
            arguments.addAll(phase(project, 'test'))
        }
        if (hasIntegrationTests && (integration || !unit)) {
            arguments.addAll(phase(project, 'integrationTest'))
            both = unit || !integration
        }
        if (arguments.isEmpty()) {
            err("The project in ${GradleRunner.line(project.directory)} has no integration tests: there is no ${INTEGRATION_TESTS} directory in it. Gradle was not run")
            return null
        }
        if (both && !(names + tests).isEmpty()) {
            arguments.add("-P${LENIENT_FILTER}=true".toString())
        }
        if (continuous) {
            arguments.add('--continuous')
        }
        arguments.addAll(systemPropertyArguments())
        return arguments
    }

    @Override
    protected void failed(ConsoleProject project) {
        // Which of the reasons a build fails for it was is in what Gradle wrote, and is not read from there
        if (both && !(names + tests).isEmpty()) {
            List<String> named = (names + tests).collect { String test -> GradleRunner.line(test) }
            spec.commandLine().getErr().println(CommandLine.Help.Ansi.AUTO.string("@|bold | Note|@ The tests were named (${named.join(', ')}) and looked for among " +
                    'the unit tests and the integration tests. If Gradle said that no tests were found for the filter, neither has a test of that name: it is the name ' +
                    'of a test class or a pattern for it, such as example.BookSpec or *BookSpec. If Gradle said "No tests found for given includes" for one of the two ' +
                    'tasks, the build of the project does not have the Grails Gradle plugin of Grails 9, which lets the task without such a test pass: say which tests ' +
                    'are meant with --unit or with --integration'))
        }
    }

    @Override
    protected void succeeded(ConsoleProject project) {
        out('| Tests PASSED')
    }

    private List<String> phase(ConsoleProject project, String task) {
        List<String> arguments = []
        if (clean) {
            arguments.add(project.task('clean' + task.capitalize()))
        }
        arguments.add(project.task(task))
        for (String test : names + tests) {
            arguments.add('--tests')
            arguments.add(test)
        }
        if (debugJvm) {
            arguments.add('--debug-jvm')
        }
        return arguments
    }
}
