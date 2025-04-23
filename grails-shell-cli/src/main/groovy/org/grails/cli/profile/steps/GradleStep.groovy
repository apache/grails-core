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
package org.grails.cli.profile.steps

import groovy.transform.CompileStatic
import org.gradle.tooling.BuildException
import org.gradle.tooling.BuildLauncher
import org.grails.build.parsing.CommandLine
import org.grails.cli.gradle.GradleUtil
import org.grails.cli.profile.*
import org.grails.exceptions.ExceptionUtils

/**
 * A {@link org.grails.cli.profile.Step} that invokes Gradle
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 *
 * @since 3.0
 */
@CompileStatic
class GradleStep extends AbstractStep {
    protected static final Map<String, String> GRADLE_ARGUMENT_ADAPTER = [
            'plain-output' : '--console plain',
            'verbose' : '-d'
    ]
    protected List<String> tasks = []
    protected String baseArguments = ""
    protected boolean passArguments = true

    GradleStep(ProfileCommand command, Map<String, Object> parameters) {
        super(command, parameters)
        initialize()
    }


    @Override
    String getName() { "gradle" }

    @Override
    public boolean handle(ExecutionContext context) {
        try {
            GradleUtil.runBuildWithConsoleOutput(context) { BuildLauncher buildLauncher ->
                buildLauncher.forTasks(tasks as String[])
                fillArguments(context, buildLauncher)
            }
        } catch (BuildException e) {
            def cause = ExceptionUtils.getRootCause(e)
            context.console.error("Gradle build terminated with error: ${cause.message}", cause)
            return false
        }
        return true;
    }

    protected void initialize() {
        tasks = (List<String>)parameters.tasks
        baseArguments = parameters.baseArguments ?: ''
        passArguments = Boolean.valueOf(parameters.passArguments?.toString() ?: 'true' )
    }

    protected BuildLauncher fillArguments(ExecutionContext context, BuildLauncher buildLauncher) {
        def commandLine = context.commandLine

        List<String> argList = baseArguments ? [baseArguments] : new ArrayList<String>()

        for(Map.Entry<String, Object> entry in commandLine.undeclaredOptions) {
            def flagName = entry.key
            if(GRADLE_ARGUMENT_ADAPTER.containsKey(flagName)) {
                argList.addAll( GRADLE_ARGUMENT_ADAPTER[flagName].split(/\s/) )
                continue
            }

            def flag = command.description.getFlag(flagName)
            if(flag) {
                flagName = flag.target ?: flagName
            }
            argList << "-$flagName".toString()

        }

        if(passArguments) {
            argList.addAll(commandLine.remainingArgs.collect() { String arg -> "-${arg}".toString() } )
        }


        if(argList) {

            buildLauncher.withArguments(argList as String[])
        }
        buildLauncher
    }

}
