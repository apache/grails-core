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
 * {@code create-integration-test}: a specification under {@code src/integration-test/groovy} that runs in the
 * application. A plugin without the web layer has no such tests, as it had none with its profile.
 *
 * @since 9.0
 */
@CommandLine.Command(name = CreateIntegrationTestCommand.NAME, description = 'Creates an integration test')
@CompileStatic
class CreateIntegrationTestCommand extends CodeGenCommand {

    public static final String NAME = 'create-integration-test'

    @CommandLine.Parameters(paramLabel = 'TEST-NAME', description = 'The name of the integration test to create')
    String testName

    @Inject
    CreateIntegrationTestCommand(CodeGenConfig config) {
        super(config)
    }

    CreateIntegrationTestCommand(CodeGenConfig config,
            ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
            ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput)
    }

    @Override
    boolean applies() {
        return config.getApplicationType() != ApplicationType.PLUGIN
    }

    @Override
    List<ArtefactTemplate> getTemplates() {
        return [ArtefactTemplate.INTEGRATION_TEST]
    }

    @Override
    Integer call() throws Exception {
        return generate('integration test', newArtefact(testName, 'Spec'), [
                'src/integration-test/groovy/{packagePath}/{className}Spec.groovy': ArtefactTemplate.INTEGRATION_TEST
        ])
    }
}
