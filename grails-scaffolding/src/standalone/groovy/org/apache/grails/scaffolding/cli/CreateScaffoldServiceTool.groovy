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
import grails.codegen.model.Model
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolOption
import org.apache.grails.core.cli.ToolParameter
import org.apache.grails.core.cli.ToolSupport

/**
 * Creates a scaffolded service.
 * Usage: <code>grails create-scaffold-service [DOMAIN_CLASS_NAME]</code>
 *
 * @author Scott Murphy Heiberg
 * @since 7.1.0
 */
@CompileStatic
@ToolParameter(index = 0, label = 'DOMAIN-CLASS', arity = '1',
        description = 'The domain class, by its simple or its full name; one the project has no source of, as a domain class of a plugin, by its full name')
@ToolOption(names = ['--extends'], arity = '1', label = 'CLASS', description = 'The full name of the class the service extends')
@ToolOption(names = ['--force'], arity = '0', description = 'Replaces a file that is there already')
@ToolHints(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false)
class CreateScaffoldServiceTool implements StandaloneTool, ToolSupport, CommandLineHelper, SkipBootstrap {

    String description = 'Creates a scaffolded service'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    boolean handle() {
        final String domainClassName = args[0]
        if (!domainClassName) {
            error('No domain-class specified')
            return FAILURE
        }
        boolean overwrite = isFlagPresent('force')
        final Model model = scaffoldModel(domainClassName)

        String extendsClass = flag('extends')

        Map<String, Object> templateModel = model.asMap()
        templateModel.put('extendsClass', extendsClass ?: '')
        templateModel.put('extendsClassName', extendsClass ? extendsClass.substring(extendsClass.lastIndexOf('.') + 1) : '')

        render(template: template('scaffolding/ScaffoldedService.groovy'),
                destination: file("grails-app/services/${model.packagePath}/${model.convention('Service')}.groovy"),
                model: templateModel,
                overwrite: overwrite)
        verbose('Scaffold service created for domain class')

        return SUCCESS
    }
}
