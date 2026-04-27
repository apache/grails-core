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
package scaffolding

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import grails.dev.commands.GrailsApplicationCommand
import grails.plugin.scaffolding.CommandLineHelper
import grails.plugin.scaffolding.SkipBootstrap
import org.grails.io.support.Resource

/**
 * Generates a controller that performs CRUD operations
 * Usage: <code>./gradlew runCommand "-Pargs=generate-controller [DOMAIN_CLASS_NAME]|*"</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
class GenerateControllerCommand implements GrailsApplicationCommand, CommandLineHelper, SkipBootstrap {

    String description = 'Generates a controller that performs CRUD operations'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (!args) {
            error('No domain-class specified')
            return false
        }

        List<String> domainClassNames
        if (args[0] == '*') {
            domainClassNames = resources('file:grails-app/domain/**/*.groovy')
                    .collect { className(it) }
        } else {
            domainClassNames = args
        }

        boolean overwrite = isFlagPresent('force')
        int failureCount = 0
        for (String domainClassName in domainClassNames) {
            final Resource sourceClass = source(domainClassName)
            if (sourceClass) {
                final Model model = model(sourceClass)
                // Call the explicit (Resource, File, Model, boolean) overload directly on
                // templateRenderer. Under Groovy 5 @CompileStatic, the named-argument shape
                // `render(template: ..., destination: ..., model: ..., overwrite: ...)` -
                // which compiles to a single-arg render(Map) - silently no-ops before the
                // method body is even entered. The failure is at the call site, not in the
                // @Delegate forwarder or the trait bridge: it reproduces equally with the
                // forwarder, with a direct call on the field, and with an explicit
                // Map<String,Object> literal. Only the typed positional overload survives.
                // Standalone reproducer: https://github.com/jamesfredley/groovy5-compiledynamic-trait-bug
                generateFile(sourceClass, model, 'scaffolding/Controller.groovy',
                        "grails-app/controllers/${model.packagePath}/${model.convention('Controller')}.groovy",
                        overwrite)
                generateFile(sourceClass, model, 'scaffolding/Service.groovy',
                        "grails-app/services/${model.packagePath}/${model.convention('Service')}.groovy",
                        overwrite)
                generateFile(sourceClass, model, 'scaffolding/Spec.groovy',
                        "src/test/groovy/${model.packagePath}/${model.convention('ControllerSpec')}.groovy",
                        overwrite)
                generateFile(sourceClass, model, 'scaffolding/ServiceSpec.groovy',
                        "src/test/groovy/${model.packagePath}/${model.convention('ServiceSpec')}.groovy",
                        overwrite)

                addStatus("Scaffolding complete for ${projectPath(sourceClass)}")
            } else {
                error("Domain class not found for name: $domainClassName")
                failureCount++
            }
        }
        return failureCount ? false : true

    }

    private void generateFile(Resource sourceClass, Model model, String templatePath,
                              String destinationPath, boolean overwrite) {
        Resource templateResource = templateRenderer.template(templatePath)
        if (templateResource == null) {
            throw new IllegalStateException(
                "Scaffolding template [${templatePath}] could not be resolved for ${sourceClass?.filename}")
        }
        File destination = file(destinationPath)
        if (destination == null) {
            throw new IllegalStateException(
                "Scaffolding destination [${destinationPath}] resolved to null File")
        }
        templateRenderer.render(templateResource, destination, model, overwrite)
    }
}
