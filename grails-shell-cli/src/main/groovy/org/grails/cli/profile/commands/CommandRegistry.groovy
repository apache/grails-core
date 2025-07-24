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

import groovy.transform.CompileStatic

import org.grails.cli.GrailsCli
import org.grails.cli.profile.Command
import org.grails.cli.profile.Profile
import org.grails.cli.profile.ProfileCommand
import org.grails.cli.profile.ProfileRepository
import org.grails.cli.profile.ProfileRepositoryAware
import org.grails.cli.profile.ProjectCommand
import org.grails.cli.profile.commands.factory.CommandFactory
import org.grails.config.CodeGenConfig

/**
 * Registry of available commands
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Singleton(strict = false)
class CommandRegistry {

    private Map<String, Command> registeredCommands = [:]
    private List<CommandFactory> registeredCommandFactories = []

    CommandRegistry() {
        Iterator<Command> commands = ServiceLoader.load(Command, Thread.currentThread().contextClassLoader).iterator()

        while(commands.hasNext()) {
            Command command = commands.next()
            registeredCommands[command.name] = command
        }

        Iterator<CommandFactory> commandFactories = ServiceLoader.load(CommandFactory, Thread.currentThread().contextClassLoader).iterator()
        while(commandFactories.hasNext()) {
            CommandFactory commandFactory = commandFactories.next()
            registeredCommandFactories << commandFactory
        }
    }

    /**
     * Returns a command for the given name and repository
     *
     * @param name The command name
     * @param repository The {@link ProfileRepository} instance
     * @return A command or null of non exists
     */
    Command getCommand(String name, ProfileRepository repository) {
        def command = registeredCommands[name]
        if(command instanceof ProfileRepositoryAware) {
            command.profileRepository = repository
        }
        return command
    }

    Collection<Command> findCommands(ProfileRepository repository) {
        registeredCommands.values().collect() { Command cmd ->
            if(cmd instanceof ProfileRepositoryAware) {
                ((ProfileRepositoryAware)cmd).profileRepository = repository
            }
            return cmd
        }
    }

    Collection<Command> findCommands(Profile profile, boolean inherited = false) {
        Collection<Command> commands = []

        for(CommandFactory cf in registeredCommandFactories) {
            def factoryCommands = cf.findCommands(profile, inherited)
            def condition = { Command c -> c.name == 'events' }
            def eventCommands = factoryCommands.findAll(condition)
            for(ec in eventCommands) {
                ec.handle(new GrailsCli.ExecutionContextImpl(new CodeGenConfig(profile.configuration)))
            }
            factoryCommands.removeAll(condition)
            commands.addAll factoryCommands
        }

        commands.addAll( registeredCommands.values()
                            .findAll { Command c -> (c instanceof ProjectCommand) || (c instanceof ProfileCommand) && ((ProfileCommand)c).profile == profile }
        )
        return commands
    }
}
