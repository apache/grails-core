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
 * The tasks of Grails 7 and 8 that run scripts and commands are kept, and say that they are deprecated:
 * {@code runScript}, which runs a script against the application context, {@code runCommand}, the other
 * name of {@code runTool}, and {@code runScriptCommand}, which runs a script command of the project or of
 * a plugin. Each runs as it did, says so once in each run, and fails the build as before when what it
 * runs fails. The classes they start are stand-ins that say what they were given.
 */
class DeprecatedTasksSpec extends CommandProjectSpecification {

    // the stand-in for the class that runs a script command is a class of the test project, which the class path of the task does not have
    private static final String WITH_STAND_IN = '''
        tasks.named('runScriptCommand') {
            classpath += sourceSets.main.runtimeClasspath
        }
    '''

    def "#task is a task of the group of the tools and says in its description that it is deprecated"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = executeTask('tasks', ['--group=Grails'])

        then:
        result.output.readLines().any { String line -> line.startsWith("${task} - ") && line.contains(said) }

        where:
        task               | said
        'runScript'        | 'Deprecated: executes a Grails script against the application context; write a tool and run it with runTool'
        'runCommand'       | 'Deprecated: use runTool'
        'runScriptCommand' | 'Script commands are deprecated: write a tool and run it with runTool'
    }

    def "#task runs as it did, and says once that it is deprecated"() {
        given:
        setupCommandProject(WITH_STAND_IN)

        when:
        BuildResult result = executeTask(task, arguments)

        then: 'what it starts is given what it was given'
        result.output.contains(ran)

        and: 'the warning is said once'
        result.output.count(warning) == 1

        where:
        task               | arguments                           | ran                                              | warning
        'runScript'        | ['-Pargs=scripts/report.groovy']    | 'SCRIPT_RUNNER_ARGUMENTS=scripts/report.groovy example.Application' | GrailsCliGradlePlugin.RUN_SCRIPT_DEPRECATED
        'runCommand'       | ['--arg=report']                    | 'ARGUMENTS=2'                                    | GrailsCliGradlePlugin.RUN_COMMAND_DEPRECATED
        'runScriptCommand' | ['--arg=report']                    | 'ARGUMENTS=1'                                    | GrailsCliGradlePlugin.SCRIPT_COMMANDS_DEPRECATED
    }

    def "runTool, the task to use instead, says nothing of a deprecation"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = executeTask('runTool', ['--arg=report'])

        then:
        result.output.contains('ARGUMENTS=2')
        !result.output.contains('is deprecated')
        !result.output.contains('are deprecated')
    }

    def "#task fails the build as before when what it runs fails, after the warning"() {
        given:
        setupCommandProject(WITH_STAND_IN)

        when:
        BuildResult result = runner.withArguments([task] + arguments).buildAndFail()

        then: 'the build fails with the exit value of the process'
        result.output.contains("Process 'command ")
        result.output.contains("finished with non-zero exit value ${exit}")

        and: 'it said once that it is deprecated'
        result.output.count(warning) == 1

        where:
        task               | arguments              | exit | warning
        'runScript'        | ['-Pargs=fail']        | 3    | GrailsCliGradlePlugin.RUN_SCRIPT_DEPRECATED
        'runCommand'       | ['--arg=fail']         | 3    | GrailsCliGradlePlugin.RUN_COMMAND_DEPRECATED
        'runScriptCommand' | ['--arg=fail']         | 1    | GrailsCliGradlePlugin.SCRIPT_COMMANDS_DEPRECATED
    }

    def "the texts of the warnings name tools as what to use instead"() {
        expect:
        GrailsCliGradlePlugin.RUN_COMMAND_DEPRECATED == 'runCommand is deprecated: use runTool'
        GrailsCliGradlePlugin.RUN_SCRIPT_DEPRECATED == 'runScript is deprecated: write a tool and run it with runTool'
        GrailsCliGradlePlugin.SCRIPT_COMMANDS_DEPRECATED == 'Script commands are deprecated: write a tool and run it with runTool'
    }
}
