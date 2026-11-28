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
package org.apache.grails.scaffolding.cli

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolOption
import org.apache.grails.core.cli.ToolParameter
import org.apache.grails.core.cli.ToolSupport

/**
 * Generates a controller that performs CRUD operations and the associated views
 * Usage: <code>grails generate-all [DOMAIN_CLASS_NAME]|*</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
@ToolParameter(index = 0, label = 'DOMAIN-CLASS', arity = '1..*',
        description = 'Domain classes of the project, each by its simple or its full name, or * for every domain class under grails-app/domain')
@ToolOption(names = ['--force'], arity = '0', description = 'Replaces files that are there already')
@ToolOption(names = ['--views'], arity = '1', label = 'VIEWS', choices = ['gsp', 'json'], caseInsensitive = true,
        description = 'gsp or json: the kind of views, and of controller with them, in place of the kind the project is given by itself')
@ToolHints(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false)
class GenerateAllTool implements StandaloneTool, ToolSupport, CommandLineHelper, SkipBootstrap {

    String description = 'Generates a controller that performs CRUD operations and the associated views'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (!args) {
            error('No domain-class specified')
            return FAILURE
        }
        if (viewsKind() == null) {
            error(viewsKindProblem())
            return FAILURE
        }
        boolean controllers = run(new GenerateControllerTool())
        boolean views = run(new GenerateViewsTool())
        return controllers && views
    }

    /**
     * Runs a command of the plugin on the project and with the arguments of this command. Both commands run:
     * a domain class that is not found fails the command and does not keep the views of the others from being
     * generated.
     */
    private boolean run(ToolSupport command) {
        command.executionContext = executionContext
        command.templateRenderer = templateRenderer
        command.fileSystemInteraction = fileSystemInteraction
        return command.handle()
    }
}
