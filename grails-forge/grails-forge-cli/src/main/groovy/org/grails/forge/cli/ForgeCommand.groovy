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
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.cli.command.CreateAppCommand
import org.grails.forge.cli.command.CreatePluginCommand
import org.grails.forge.cli.command.CreateRestApiCommand
import org.grails.forge.cli.command.CreateWebPluginCommand
import org.grails.forge.cli.command.CreateWebappCommand
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.util.concurrent.Callable

/**
 * The root command of the Forge CLI: {@code grails-forge-cli}, whose subcommands create applications
 * and plugins. The code generation commands that apply inside a generated project are added by
 * {@link ForgeCli} when the current directory holds one.
 */
@CommandLine.Command(name = 'grails-forge-cli', description = [
        'Grails Forge CLI command line interface for generating projects and services.',
        'Application generation commands are:',
        '',
        '*  @|bold create-app|@ @|yellow NAME|@',
        '*  @|bold create-webapp|@ @|yellow NAME|@',
        '*  @|bold create-restapi|@ @|yellow NAME|@',
        '*  @|bold create-plugin|@ @|yellow NAME|@',
        '*  @|bold create-web-plugin|@ @|yellow NAME|@'
],
        synopsisHeading = '@|bold,underline Usage:|@ ',
        optionListHeading = '%n@|bold,underline Options:|@%n',
        commandListHeading = '%n@|bold,underline Commands:|@%n',
        subcommands = [
                CreateAppCommand,
                CreateWebappCommand,
                CreatePluginCommand,
                CreateWebPluginCommand,
                CreateRestApiCommand
        ])
@Component
@Scope('prototype')
@CompileStatic
class ForgeCommand extends BaseCommand implements Callable<Integer> {

    @Override
    Integer call() throws Exception {
        throw new CommandLine.ParameterException(spec.commandLine(), 'No command specified')
    }
}
