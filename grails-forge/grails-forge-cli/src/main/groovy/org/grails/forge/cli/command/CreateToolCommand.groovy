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
import org.grails.forge.application.Project
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.util.NameUtils
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
        Project names = newArtefact(commandName, 'Command')
        Integer exit = generate('command', names, [
                'grails-app/commands/{packagePath}/{className}Command.groovy': ArtefactTemplate.COMMAND
        ])
        if (exit == 0) {
            // the name the command has by its class: the build lists it when it compiles the project, and
            // the command line has the build list it when the name is typed before that
            out("@|blue ||@ Run it with: ${UsageHelp.toType(spec, NameUtils.getScriptName(names.getClassName()))}")
        }
        return exit
    }
}
