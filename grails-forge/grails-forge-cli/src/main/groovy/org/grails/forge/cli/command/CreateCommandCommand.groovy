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

import groovy.transform.CompileStatic
import jakarta.inject.Inject
import org.apache.grails.forge.cli.artefact.ArtefactTemplate
import org.apache.grails.forge.cli.console.UsageHelp
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.util.ThrowingSupplier
import picocli.CommandLine

/**
 * {@code create-command}: a command of the application under {@code grails-app/commands}.
 *
 * @since 9.0
 */
@CommandLine.Command(name = CreateCommandCommand.NAME, description = 'Creates an application command')
@CompileStatic
class CreateCommandCommand extends CodeGenCommand {

    public static final String NAME = 'create-command'

    @CommandLine.Parameters(paramLabel = 'COMMAND-NAME', description = 'The name of the command to create')
    String commandName

    @Inject
    CreateCommandCommand(CodeGenConfig config) {
        super(config)
    }

    CreateCommandCommand(CodeGenConfig config,
            ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
            ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput)
    }

    @Override
    boolean applies() {
        return true
    }

    @Override
    List<ArtefactTemplate> getTemplates() {
        return [ArtefactTemplate.COMMAND]
    }

    @Override
    Integer call() throws Exception {
        Integer exit = generate('command', newArtefact(commandName, 'Command'), [
                'grails-app/commands/{packagePath}/{className}Command.groovy': ArtefactTemplate.COMMAND
        ])
        if (exit == 0) {
            // the build lists the commands of the application, and is not run for it from here
            out("@|blue ||@ To make it a command of ${spec.root().name()}, list the commands of the application again: ${UsageHelp.toType(spec, 'gradle grailsCommandCatalog')}")
        }
        return exit
    }
}
