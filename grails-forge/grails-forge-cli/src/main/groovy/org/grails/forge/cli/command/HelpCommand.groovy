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
package org.grails.forge.cli.command

import java.nio.file.Path
import java.util.concurrent.Callable
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ProjectTools
import org.apache.grails.forge.cli.console.CommandCatalog
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.OptionRelation
import org.grails.forge.io.FileSystemOutputHandler
import picocli.CommandLine

/**
 * Shows how a command is used, as the command does when it is given {@code --help}, lists the
 * commands there are in the current directory when none is named, and with {@code --json} writes
 * them as a document for a tool.
 *
 * @since 9.0
 */
@CommandLine.Command(name = HelpCommand.NAME, description = [
        'Shows how a command is used, or without a command which commands there are here.',
        'For a command of a command give both names, as in @|bold help feature add|@.'])
@CompileStatic
class HelpCommand extends BaseCommand implements Callable<Integer>, OptionRelation.Declared {

    public static final String NAME = 'help'

    @CommandLine.Parameters(arity = '0..*', paramLabel = 'COMMAND', description = 'The command to show the help of.')
    List<String> command = []

    @CommandLine.Option(names = ['--json'], description = 'Write the commands there are here, with their options, as one JSON document for a tool to read.')
    boolean json

    /**
     * What kept commands of the project from being found when the command line was put together,
     * which the JSON document reports; set by what puts the command line together.
     */
    List<String> diagnostics = []

    /**
     * Whether the record of the project could be used when the command line was put together, so
     * that the commands of the project are all there; set by what puts the command line together.
     */
    boolean complete = true

    @Override
    List<OptionRelation> getOptionRelations() {
        return [OptionRelation.exclusive(false, '--json', 'COMMAND')]
    }

    @Override
    Integer call() throws Exception {
        CommandCatalog catalog = new CommandCatalog(spec.root().commandLine())
        if (json) {
            if (!command.isEmpty()) {
                throw new CommandLine.ParameterException(spec.commandLine(), '--json describes every command there is here: name no command with it')
            }
            ConsoleProject.Search search = ConsoleProject.search(FileSystemOutputHandler.getDefaultBaseDirectory().toPath())
            Path directory = search.project == null ? null : search.project.getDirectory()
            List<String> said = new ArrayList<String>(diagnostics)
            if (search.untrusted != null) {
                // the commands of a project above that is not used are not there, and a tool is told why
                said.add(search.untrusted)
            }
            // what the build of the project lists of the tools of the application, and what is not there of it
            ProjectTools application = ProjectTools.of(catalog.getRoot())
            if (application != null) {
                said.addAll(application.diagnostics(catalog.getRoot()))
            }
            // written as it is: a description is not markup of the command line
            spec.commandLine().getOut().println(JsonOutput.toJson(catalog.describe(directory, said,
                    complete && search.untrusted == null && (application == null || application.isComplete()))))
            return 0
        }
        CommandLine shown = catalog.getRoot()
        for (String name : command) {
            CommandLine next = catalog.find(shown, name)
            if (next == null) {
                throw CommandCatalog.unknown(shown, name)
            }
            shown = next
        }
        shown.usage(spec.commandLine().getOut(), spec.commandLine().getColorScheme())
        return 0
    }
}
