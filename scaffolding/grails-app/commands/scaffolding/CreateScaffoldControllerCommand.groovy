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
 * Creates a scaffolded controller.
 * Usage: <code>./gradlew runCommand "-Pargs=create-scaffold-controller [DOMAIN_CLASS_NAME]"</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
class CreateScaffoldControllerCommand implements GrailsApplicationCommand, CommandLineHelper, SkipBootstrap {

    String description = 'Creates a scaffolded controller'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    boolean handle() {
        final String domainClassName = args[0]
        if (!domainClassName) {
            error 'No domain-class specified'
            return FAILURE
        }
        final Resource sourceClass = source(domainClassName)
        if (!sourceClass) {
            error "No domain-class found for name: ${domainClassName}"
            return FAILURE
        }
        boolean overwrite = isFlagPresent('force')
        final Model model = model(sourceClass)
        render(template: template('scaffolding/ScaffoldedController.groovy'),
                destination: file("grails-app/controllers/${model.packagePath}/${model.convention("Controller")}.groovy"),
                model: model,
                overwrite: overwrite)
        verbose('Scaffold controller created for class domain-class')

        return SUCCESS
    }
}
