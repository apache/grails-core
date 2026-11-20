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
package org.grails.cli.gradle.commands

import groovy.transform.CompileStatic

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import org.gradle.tooling.BuildLauncher

import org.grails.cli.gradle.GradleUtil
import org.grails.cli.interactive.completers.ClosureCompleter
import org.grails.cli.profile.CommandDescription
import org.grails.cli.profile.ExecutionContext
import org.grails.cli.profile.ProjectCommand
import org.grails.cli.profile.ProjectContext
import org.grails.cli.profile.ProjectContextAware

/**
 * A command for invoking Gradle commands
 *
 * @author Graeme Rocher
 */
@CompileStatic
class GradleCommand implements ProjectCommand, Completer, ProjectContextAware {

    public static final String GRADLE = 'gradle'

    final String name = GRADLE
    final CommandDescription description = new CommandDescription(name, 'Allows running of Gradle tasks', 'gradle [task name]')
    ProjectContext projectContext

    private ReadGradleTasks readTasks
    private Completer completer

    void setProjectContext(ProjectContext projectContext) {
        this.projectContext = projectContext
        initializeCompleter()
    }

    @Override
    boolean handle(ExecutionContext context) {
        GradleUtil.runBuildWithConsoleOutput(context) { BuildLauncher buildLauncher ->
            def args = context.commandLine.remainingArgsString?.trim()
            if (args) {
                buildLauncher.withArguments(args)
            }
        }
        return true
    }

    @Override
    void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        initializeCompleter()

        if (completer) {
            completer.complete(reader, line, candidates)
        }
    }

    private void initializeCompleter() {
        if (completer == null && projectContext) {
            readTasks = new ReadGradleTasks(projectContext)
            completer = new ClosureCompleter({ -> readTasks.call() } as Closure<Collection<String>>)
        }
    }

}
