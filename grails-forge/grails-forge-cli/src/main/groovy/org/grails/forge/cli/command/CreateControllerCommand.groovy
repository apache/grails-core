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
import org.apache.grails.forge.cli.artefact.ArtefactTemplate
import org.grails.forge.util.ThrowingSupplier
import jakarta.inject.Inject
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import picocli.CommandLine

/**
 * {@code create-controller}: a controller with an empty default action, and its test. In a REST API
 * application or REST API plugin the controller names JSON as the one format it responds in, as the
 * controller the command of that profile wrote did, and as {@code create-restful-controller} and
 * {@code create-domain-resource} do; without it {@code respond} negotiates every format the
 * application has a MIME type for.
 *
 * @since 9.0
 */
@CommandLine.Command(name = CreateControllerCommand.NAME, description = 'Creates a Grails Controller')
@CompileStatic
class CreateControllerCommand extends CodeGenCommand {

    public static final String NAME = 'create-controller'

    @CommandLine.Parameters(paramLabel = 'CONTROLLER-NAME', description = 'The name of the controller to create')
    String controllerName

    @Inject
    CreateControllerCommand(CodeGenConfig config) {
        super(config)
    }

    CreateControllerCommand(CodeGenConfig config,
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
        return [controller(), ArtefactTemplate.CONTROLLER_TEST]
    }

    // the template of the controller this project gets; a project has its own of either at the same place
    private ArtefactTemplate controller() {
        ApplicationType type = config?.getApplicationType()
        return type != null && type.isRestApi() ? ArtefactTemplate.REST_CONTROLLER : ArtefactTemplate.CONTROLLER
    }

    @Override
    Integer call() throws Exception {
        return generate('controller', getProject(controllerName, 'Controller'), [
                'grails-app/controllers/{packagePath}/{className}Controller.groovy': controller(),
                'src/test/groovy/{packagePath}/{className}ControllerSpec.groovy': ArtefactTemplate.CONTROLLER_TEST
        ])
    }
}
