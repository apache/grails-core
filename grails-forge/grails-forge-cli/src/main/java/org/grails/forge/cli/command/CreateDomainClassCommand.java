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
package org.grails.forge.cli.command;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.functional.ThrowingSupplier;
import jakarta.inject.Inject;
import org.grails.forge.application.Project;
import org.grails.forge.cli.CodeGenConfig;
import org.grails.forge.cli.command.templates.domain;
import org.grails.forge.cli.command.templates.domainSpec;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.io.OutputHandler;
import org.grails.forge.template.RenderResult;
import org.grails.forge.template.RockerTemplate;
import org.grails.forge.template.TemplateRenderer;
import picocli.CommandLine;

import java.io.IOException;

@CommandLine.Command(name = CreateDomainClassCommand.NAME, description = "Creates a Domain Class")
public class CreateDomainClassCommand extends CodeGenCommand {

    public static final String NAME = "create-domain-class";

    @ReflectiveAccess
    @CommandLine.Parameters(paramLabel = "DOMAIN-CLASS-NAME", description = "The name of the domain class to create")
    String domainClassName;

    @Inject
    public CreateDomainClassCommand(@Parameter CodeGenConfig config) {
        super(config);
    }

    public CreateDomainClassCommand(CodeGenConfig config,
                                    ThrowingSupplier<OutputHandler, IOException> outputHandlerSupplier,
                                    ConsoleOutput consoleOutput) {
        super(config, outputHandlerSupplier, consoleOutput);
    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public Integer call() throws Exception {
        final Project project = getProject(domainClassName);
        final TemplateRenderer templateRenderer = getTemplateRenderer(project);
        final RenderResult domainRenderResult = templateRenderer.render(new RockerTemplate("grails-app/domain/{packagePath}/{className}.groovy", domain.template(project)), overwrite);
        final RenderResult domainSpecRenderResult = templateRenderer.render(new RockerTemplate("src/test/groovy/{packagePath}/{className}Spec.groovy", domainSpec.template(project)), overwrite);
        if (domainRenderResult != null && domainSpecRenderResult != null) {
            logRenderResult(domainRenderResult);
            logRenderResult(domainSpecRenderResult);
        }

        return 0;
    }

    private void logRenderResult(RenderResult result) throws Exception {
        if (result != null) {
            if (result.isSuccess()) {
                out("@|blue ||@ Rendered domain class to " + result.getPath());
            } else if (result.isSkipped()) {
                warning("Rendering skipped for " + result.getPath() + " because it already exists. Run again with -f to overwrite.");
            } else if (result.getError() != null) {
                throw result.getError();
            }
        }
    }
}
