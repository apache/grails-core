/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.cli.profile.commands

import jline.console.completer.Completer

import org.grails.build.parsing.CommandLine
import org.grails.build.parsing.CommandLineParser
import org.grails.cli.profile.Command
import org.grails.cli.profile.CommandDescription
import org.grails.cli.profile.ExecutionContext
import org.grails.cli.profile.Profile
import org.grails.cli.profile.ProfileCommand
import org.grails.cli.profile.ProfileRepository
import org.grails.cli.profile.ProfileRepositoryAware
import org.grails.cli.profile.ProjectCommand
import org.grails.cli.profile.ProjectContext
import org.grails.cli.profile.ProjectContextAware

/**
 * @author Graeme Rocher
 */
class HelpCommand implements ProfileCommand, Completer, ProjectContextAware, ProfileRepositoryAware{

    public static final String NAME = "help"

    final CommandDescription description = new CommandDescription(NAME, "Prints help information for a specific command", "help [COMMAND NAME]")

    Profile profile
    ProfileRepository profileRepository
    ProjectContext projectContext

    CommandLineParser cliParser = new CommandLineParser()

    @Override
    String getName() {
        return NAME
    }


    @Override
    boolean handle(ExecutionContext executionContext) {
        def console = executionContext.console
        def commandLine = executionContext.commandLine
        Collection<CommandDescription> allCommands=findAllCommands()
        String remainingArgs = commandLine.getRemainingArgsString()
        if(remainingArgs?.trim()) {
            CommandLine remainingArgsCommand = cliParser.parseString(remainingArgs)
            String helpCommandName = remainingArgsCommand.getCommandName()
            for (CommandDescription desc : allCommands) {
                if(desc.name == helpCommandName) {
                    console.addStatus("Command: $desc.name")
                    console.addStatus("Description:")
                    console.println "${desc.description?:''}"
                    if(desc.usage) {
                        console.println()
                        console.addStatus("Usage:")
                        console.println "${desc.usage}"
                    }
                    if(desc.arguments) {
                        console.println()
                        console.addStatus("Arguments:")
                        for(arg in desc.arguments) {
                            console.println "* ${arg.name} - ${arg.description?:''} (${arg.required ? 'REQUIRED' : 'OPTIONAL'})"
                        }
                    }
                    if(desc.flags) {
                        console.println()
                        console.addStatus("Flags:")
                        for(arg in desc.flags) {
                            console.println "* ${arg.name} - ${arg.description ?: ''}"
                        }
                    }
                    return true
                }
            }
            console.error "Help for command $helpCommandName not found"
            return false
        } else {
            console.log '''
Usage (optionals marked with *):'
grails [environment]* [target] [arguments]*'

'''
            console.addStatus("Examples:")
            console.log('$ grails dev run-app')
            console.log('$ grails create-app books')
            console.log ''
            console.addStatus('Available Commands (type grails help \'command-name\' for more info):')
            console.addStatus("${'Command Name'.padRight(37)} Command Description")
            console.println('-' * 100)
            for (CommandDescription desc : allCommands) {
                console.println "${desc.name.padRight(40)}${desc.description}"
            }
            console.println()
            console.addStatus("Detailed usage with help [command]")
            return true
        }

    }

    @Override
    int complete(String buffer, int cursor, List<CharSequence> candidates) {
        def allCommands = findAllCommands().collect() { CommandDescription desc -> desc.name }

        for(cmd in allCommands) {
            if(buffer) {
                if(cmd.startsWith(buffer)) {
                    candidates << cmd.substring(buffer.size())
                }
            }
            else {
                candidates << cmd
            }
        }
        return cursor
    }


    protected Collection<CommandDescription> findAllCommands() {
        Iterable<Command> commands
        if(profile) {
            commands = profile.getCommands(projectContext)
        }
        else {
            commands = CommandRegistry.instance.findCommands(profileRepository).findAll() { Command cmd ->
                !(cmd instanceof ProjectCommand)
            }
        }
        return commands
                    .collect() { Command cmd -> cmd.description }
                    .unique() { CommandDescription cmd -> cmd.name }
                    .sort(false) { CommandDescription itDesc ->  itDesc.name }
    }


}
