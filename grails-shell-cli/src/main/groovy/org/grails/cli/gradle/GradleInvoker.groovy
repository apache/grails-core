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
package org.grails.cli.gradle

import grails.util.Environment
import groovy.transform.CompileStatic
import org.gradle.tooling.BuildLauncher
import org.grails.build.parsing.CommandLine
import org.grails.cli.profile.ExecutionContext

/**
 * Allow dynamic invocation of Gradle tasks
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GradleInvoker {

    ExecutionContext executionContext

    GradleInvoker(ExecutionContext executionContext) {
        this.executionContext = executionContext
    }

    @Override
    Object invokeMethod(String name, Object args) {
        Object[] argArray = (Object[]) args


        GradleUtil.runBuildWithConsoleOutput(executionContext) { BuildLauncher buildLauncher ->
            buildLauncher.forTasks(name.split(' '))
            List<String> arguments = []
            arguments << "-Dgrails.env=${Environment.current.name}".toString()


            def commandLine = executionContext.commandLine
            if(commandLine.hasOption(CommandLine.STACKTRACE_ARGUMENT)) {
                arguments << '--stacktrace'
                arguments << '-Dgrails.full.stacktrace=true'
            }

            arguments.addAll argArray.collect() { it.toString() }
            buildLauncher.withArguments( arguments as String[])
        }
    }

    GradleAsyncInvoker getAsync() {
        return new GradleAsyncInvoker(this)
    }


}
