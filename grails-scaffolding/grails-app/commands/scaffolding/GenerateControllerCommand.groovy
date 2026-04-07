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
                // Use explicit overload calls instead of named arguments to work around
                // Groovy 5 @CompileStatic dispatch regression with @Delegate in traits
                templateRenderer.render(
                        templateRenderer.template('scaffolding/Controller.groovy'),
                        file("grails-app/controllers/${model.packagePath}/${model.convention('Controller')}.groovy") as File,
                        model, overwrite)

                templateRenderer.render(
                        templateRenderer.template('scaffolding/Service.groovy'),
                        file("grails-app/services/${model.packagePath}/${model.convention('Service')}.groovy") as File,
                        model, overwrite)

                templateRenderer.render(
                        templateRenderer.template('scaffolding/Spec.groovy'),
                        file("src/test/groovy/${model.packagePath}/${model.convention('ControllerSpec')}.groovy") as File,
                        model, overwrite)

                templateRenderer.render(
                        templateRenderer.template('scaffolding/ServiceSpec.groovy'),
                        file("src/test/groovy/${model.packagePath}/${model.convention('ServiceSpec')}.groovy") as File,
                        model, overwrite)

                addStatus("Scaffolding complete for ${projectPath(sourceClass)}")
            } else {
                error("Domain class not found for name: $domainClassName")
                failureCount++
            }
        }
        return failureCount ? false : true

    }
}
