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
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.util.ThrowingSupplier
import picocli.CommandLine

/**
 * {@code create-restful-controller}: a controller that extends {@code RestfulController} for a domain class. It
 * is a command of a REST API application, as it was of its profile.
 *
 * @since 9.0
 */
@CommandLine.Command(name = CreateRestfulControllerCommand.NAME, description = 'Creates a REST controller for a domain class')
@CompileStatic
class CreateRestfulControllerCommand extends CodeGenCommand {

    public static final String NAME = 'create-restful-controller'

    @CommandLine.Parameters(paramLabel = 'DOMAIN-CLASS', description = 'The name of the domain class the controller is for')
    String domainClassName

    @Inject
    CreateRestfulControllerCommand(CodeGenConfig config) {
        super(config)
    }

    CreateRestfulControllerCommand(CodeGenConfig config,
            ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
            ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput)
    }

    @Override
    boolean applies() {
        ApplicationType type = config.getApplicationType()
        return type != null && type.isRestApi()
    }

    @Override
    List<ArtefactTemplate> getTemplates() {
        return [ArtefactTemplate.RESTFUL_CONTROLLER]
    }

    @Override
    Integer call() throws Exception {
        return generate('controller', newArtefact(domainClassName, 'Controller'), [
                'grails-app/controllers/{packagePath}/{className}Controller.groovy': ArtefactTemplate.RESTFUL_CONTROLLER
        ])
    }
}
