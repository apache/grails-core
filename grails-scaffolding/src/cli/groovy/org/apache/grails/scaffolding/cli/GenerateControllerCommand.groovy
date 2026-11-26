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
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.StandaloneCommand
import org.grails.io.support.Resource

/**
 * Generates a controller that performs CRUD operations
 * Usage: <code>./gradlew runCommand "-Pargs=generate-controller [DOMAIN_CLASS_NAME]|*"</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
class GenerateControllerCommand implements GrailsApplicationCommand, StandaloneCommand, CommandLineHelper, SkipBootstrap {

    String description = 'Generates a controller that performs CRUD operations'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (!args) {
            error('No domain-class specified')
            return FAILURE
        }

        List<String> domainClassNames
        if (args[0] == '*') {
            domainClassNames = resources('file:grails-app/domain/**/*.groovy')
                    .collect { className(it) }
        } else {
            domainClassNames = args
        }

        final String views = viewsKind()
        if (views == null) {
            error(viewsKindProblem())
            return FAILURE
        }
        // the controller and the service of a REST application, and their specs, are templates of their own
        final String prefix = views == JSON_VIEWS ? 'Rest' : ''
        boolean overwrite = isFlagPresent('force')
        int failureCount = 0
        for (String domainClassName in domainClassNames) {
            final Resource sourceClass = source(domainClassName)
            if (sourceClass) {
                final Model model = model(sourceClass)
                render(template: "scaffolding/${prefix}Controller.groovy",
                        destination: file("grails-app/controllers/${model.packagePath}/${model.convention('Controller')}.groovy"),
                        model: model,
                        overwrite: overwrite)

                render(template: "scaffolding/${prefix}Service.groovy",
                        destination: file("grails-app/services/${model.packagePath}/${model.convention('Service')}.groovy"),
                        model: model,
                        overwrite: overwrite)

                render(template: "scaffolding/${prefix}Spec.groovy",
                        destination: file("src/test/groovy/${model.packagePath}/${model.convention('ControllerSpec')}.groovy"),
                        model: model,
                        overwrite: overwrite)

                render(template: "scaffolding/${prefix}ServiceSpec.groovy",
                        destination: file("src/integration-test/groovy/${model.packagePath}/${model.convention('ServiceSpec')}.groovy"),
                        model: model,
                        overwrite: overwrite)

                addStatus("Scaffolding complete for ${projectPath(sourceClass)}")
            } else {
                error("Domain class not found for name: $domainClassName")
                failureCount++
            }
        }
        return failureCount ? FAILURE : SUCCESS

    }
}
