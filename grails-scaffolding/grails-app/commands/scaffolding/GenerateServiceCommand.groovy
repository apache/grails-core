/*
 * Copyright 2023 Puneet Behl.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scaffolding

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import grails.dev.commands.GrailsApplicationCommand
import grails.plugin.scaffolding.CommandLineHelper
import grails.plugin.scaffolding.SkipBootstrap
import groovy.transform.CompileStatic
import org.grails.io.support.Resource

/**
 * Generates a Grails data service for the specified domain-class.
 * Usage: <code>./gradlew runCommand "-Pargs=generate-service [DOMAIN_CLASS_NAME]"</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
class GenerateServiceCommand implements GrailsApplicationCommand, CommandLineHelper, SkipBootstrap {

    String description = 'Generates a Grails data service for the specified domain-class.'

    @Delegate ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (!args) {
            error "No domain-class specified"
        }
        final String domainClassName = args[0]
        final Resource sourceClass = source(domainClassName)
        if (!sourceClass) {
            error "No domain-class found for name: ${domainClassName}"
        }

        boolean overwrite = isFlagPresent('force')
        final Model model = model(sourceClass)

        render(template: 'scaffolding/Service.groovy',
                destination: file("grails-app/services/${model.packagePath}/${model.convention('Service')}.groovy"),
                model: model,
                overwrite: overwrite)

        render(template: 'scaffolding/ServiceSpec.groovy',
                destination: file("src/test/groovy/${model.packagePath}/${model.convention('ServiceSpec')}.groovy"),
                model: model,
                overwrite: overwrite)

        verbose("Service created for domain-class ${projectPath(sourceClass)}")
        return SUCCESS
    }
}
