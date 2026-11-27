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
package org.grails.forge.cli

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.mcp.McpCommand
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.cli.command.CreateAppCommand
import org.grails.forge.cli.command.CreatePluginCommand
import org.grails.forge.cli.command.CreateRestApiCommand
import org.grails.forge.cli.command.CreateRestApiPluginCommand
import org.grails.forge.cli.command.CreateWebPluginCommand
import org.grails.forge.cli.command.CreateWebappCommand
import org.grails.forge.cli.command.FeatureCommand
import org.grails.forge.cli.command.GradleCommand
import org.grails.forge.cli.command.HelpCommand
import org.grails.forge.cli.command.ProjectGroupCommand
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.util.concurrent.Callable

/**
 * The root command of the Forge CLI: {@code grails-forge-cli}, whose subcommands create applications
 * and plugins, or run Gradle in a project. The code generation commands that apply inside a generated
 * project are added by {@link ForgeCli} when the current directory holds one.
 */
@CommandLine.Command(name = 'grails-forge-cli', description = [
        'Grails CLI: generates projects and runs commands in them.',
        'Application generation commands are:',
        '',
        '*  @|bold create-app|@ @|yellow NAME|@',
        '*  @|bold create-webapp|@ @|yellow NAME|@',
        '*  @|bold create-restapi|@ @|yellow NAME|@',
        '*  @|bold create-plugin|@ @|yellow NAME|@',
        '*  @|bold create-web-plugin|@ @|yellow NAME|@',
        '*  @|bold create-rest-api-plugin|@ @|yellow NAME|@',
        '',
        'In the directory of an application, a feature is added to it with:',
        '',
        '*  @|bold feature add|@ @|yellow FEATURE|@',
        '',
        'and how it was generated is said, and done again, with:',
        '',
        '*  @|bold project show|@',
        '*  @|bold project regenerate|@ @|yellow --output DIRECTORY|@',
        '',
        'An application that has no record of how it was generated, as one of an earlier version, is given one, and the record an earlier version of ' +
                'Grails 9 wrote is moved to this one, with:',
        '',
        '*  @|bold project migrate|@',
        '',
        'What the files of a project show of its state, and what the upgrade notes still ask of it, is said with:',
        '',
        '*  @|bold project health|@'
],
        synopsisHeading = '@|bold,underline Usage:|@ ',
        optionListHeading = '%n@|bold,underline Options:|@%n',
        commandListHeading = '%n@|bold,underline Commands:|@%n',
        subcommands = [
                CreateAppCommand,
                CreateWebappCommand,
                CreatePluginCommand,
                CreateWebPluginCommand,
                CreateRestApiPluginCommand,
                CreateRestApiCommand,
                FeatureCommand,
                ProjectGroupCommand,
                GradleCommand,
                HelpCommand,
                McpCommand
        ])
@Component
@Scope('prototype')
@CompileStatic
class ForgeCommand extends BaseCommand implements Callable<Integer> {

    /** The option that rules out everything that waits for what a user types. */
    public static final String NON_INTERACTIVE = '--non-interactive'

    // read before the command line is: see PlainOutput. Declared so that it is accepted, here and after the name of a command, and shown
    @CommandLine.Option(names = ['--plain-output'], scope = CommandLine.ScopeType.INHERIT,
            description = 'Write plain text for a program to read: no colour or other terminal markup, and no progress display of Gradle.')
    boolean plainOutput

    @CommandLine.Option(names = ['--non-interactive'], scope = CommandLine.ScopeType.INHERIT,
            description = 'Never wait for what a user types: without a command the help is shown and the interactive console is not started, ' +
                    'and a command that reads what is typed is refused.')
    boolean nonInteractive

    @Override
    Integer call() throws Exception {
        throw new CommandLine.ParameterException(spec.commandLine(), 'No command specified')
    }
}
