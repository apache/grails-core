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
import org.grails.forge.util.ThrowingSupplier
import jakarta.inject.Inject
import org.grails.forge.application.Project
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.OutputHandler
import org.grails.forge.template.RenderResult
import org.grails.forge.template.TemplateRenderer
import org.grails.forge.template.GspTemplate
import org.grails.forge.template.GspView
import picocli.CommandLine

@CommandLine.Command(name = CreateServiceCommand.NAME, description = 'Creates a Service Class')
@CompileStatic
class CreateServiceCommand extends CodeGenCommand {

    public static final String NAME = 'create-service'

    @CommandLine.Parameters(paramLabel = 'SERVICE-NAME', description = 'The name of the service to create')
    String serviceName

    @Inject
    CreateServiceCommand(CodeGenConfig config) {
        super(config)
    }

    CreateServiceCommand(CodeGenConfig config,
                                ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
                                ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput)
    }

    @Override
    boolean applies() {
        return true
    }

    @Override
    Integer call() throws Exception {
        final Project project = getProject(serviceName)
        final TemplateRenderer templateRenderer = getTemplateRenderer(project)
        final RenderResult renderResult = templateRenderer.render(new GspTemplate('grails-app/services/{packagePath}/{className}Service.groovy', GspView.of('/forge/cli/command/templates/service.gsp', [project: project])), overwrite)
        final RenderResult specRenderResult = templateRenderer.render(new GspTemplate('src/test/groovy/{packagePath}/{className}ServiceSpec.groovy', GspView.of('/forge/cli/command/templates/serviceSpec.gsp', [project: project])), overwrite)
        if (renderResult != null && specRenderResult != null) {
            logRenderResult(renderResult)
            logRenderResult(specRenderResult)
        }

        return 0
    }

    private void logRenderResult(RenderResult result) throws Exception {
        if (result != null) {
            if (result.isSuccess()) {
                out('@|blue ||@ Rendered service class to ' + result.getPath())
            } else if (result.isSkipped()) {
                warning('Rendering skipped for ' + result.getPath() + ' because it already exists. Run again with -f to overwrite.')
            } else if (result.getError() != null) {
                throw result.getError()
            }
        }
    }
}
