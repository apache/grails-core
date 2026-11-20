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

import java.util.concurrent.Callable
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.io.FileSystemOutputHandler
import picocli.CommandLine

/**
 * Stands where a command that writes into a Grails project would be, in a directory where the record
 * of no project was read: the command is known by its name everywhere, and says in one line, as
 * every command of a project does, that it is one and that there is no project. It writes nothing,
 * reads none of its arguments and is not listed.
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'project-command', hidden = true, preprocessor = ProjectOnlyCommand.NothingIsRead)
@CompileStatic
class ProjectOnlyCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    @CommandLine.Parameters(arity = '0..*', hidden = true)
    List<String> arguments = []

    /**
     * @param name the name of a command of a project
     * @param factory the factory of the command line the command is added to
     * @return what stands for the command, under its name
     */
    static CommandLine.Model.CommandSpec specOf(String name, CommandLine.IFactory factory) {
        CommandLine.Model.CommandSpec spec = CommandLine.Model.CommandSpec.forAnnotatedObject(new ProjectOnlyCommand(), factory)
        for (CommandLine.Model.OptionSpec option : new ArrayList<CommandLine.Model.OptionSpec>(spec.options())) {
            spec.remove(option)
        }
        spec.name(name)
        String description = "${name} is a command of a Grails project: its options are shown in the directory of one whose record is read"
        spec.usageMessage().description(description)
        return spec
    }

    @Override
    Integer call() throws Exception {
        ConsoleProject.Search search = ConsoleProject.search(FileSystemOutputHandler.getDefaultBaseDirectory().toPath())
        if (search.project == null) {
            err(search.none(spec.name()))
            return 1
        }
        err("${spec.name()} is a command of a Grails project whose record is read; the project in ${GradleRunner.line(search.project.directory)} " +
                "has no ${ProjectState.MANIFEST} or ${ProjectState.EARLIER_RECORD} that is")
        return 1
    }

    /** Takes every argument: what the command would have been given is not looked at. */
    static class NothingIsRead implements CommandLine.IParameterPreprocessor {

        @Override
        boolean preprocess(Stack<String> args, CommandLine.Model.CommandSpec commandSpec, CommandLine.Model.ArgSpec argSpec, Map<String, Object> info) {
            args.clear()
            return true
        }
    }
}
