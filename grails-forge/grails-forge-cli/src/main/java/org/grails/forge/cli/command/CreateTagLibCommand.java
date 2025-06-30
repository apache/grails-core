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
import org.grails.forge.cli.command.templates.taglib;
import org.grails.forge.cli.command.templates.taglibSpec;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.io.OutputHandler;
import org.grails.forge.template.RenderResult;
import org.grails.forge.template.RockerTemplate;
import org.grails.forge.template.TemplateRenderer;
import picocli.CommandLine;

import java.io.IOException;

@CommandLine.Command(name = CreateTagLibCommand.NAME, description = "Creates a Grails TagLib")
public class CreateTagLibCommand extends CodeGenCommand {

    public static final String NAME = "create-taglib";

    @ReflectiveAccess
    @CommandLine.Parameters(paramLabel = "TAG-LIB-NAME", description = "The name of the taglib to create")
    String tagLibName;

    @Inject
    public CreateTagLibCommand(@Parameter CodeGenConfig config) {
        super(config);
    }

    public CreateTagLibCommand(CodeGenConfig config,
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
        final Project project = getProject(tagLibName);
        TemplateRenderer templateRenderer = getTemplateRenderer(project);
        final RenderResult controllerRenderResult = templateRenderer.render(new RockerTemplate("grails-app/taglib/{packagePath}/{className}TagLib.groovy", taglib.template(project)), overwrite);
        final RenderResult controllerSpecRenderResult = templateRenderer.render(new RockerTemplate("src/test/groovy/{packagePath}/{className}TagLibSpec.groovy", taglibSpec.template(project)), overwrite);
        if (controllerRenderResult != null && controllerSpecRenderResult != null) {
            logRenderResult(controllerRenderResult);
            logRenderResult(controllerSpecRenderResult);
        }

        return 0;
    }

    private void logRenderResult(RenderResult result) throws Exception {
        if (result != null) {
            if (result.isSuccess()) {
                out("@|blue ||@ Rendered taglib to " + result.getPath());
            } else if (result.isSkipped()) {
                warning("Rendering skipped for " + result.getPath() + " because it already exists. Run again with -f to overwrite.");
            } else if (result.getError() != null) {
                throw result.getError();
            }
        }
    }
}
