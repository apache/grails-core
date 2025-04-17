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
import grails.dev.commands.GrailsApplicationCommand
import grails.plugin.scaffolding.CommandLineHelper
import grails.plugin.scaffolding.SkipBootstrap
import groovy.transform.CompileStatic

/**
 * Generates a controller that performs CRUD operations and the associated views
 * Usage: <code>./gradlew runCommand "-Pargs=generate-all [DOMAIN_CLASS_NAME]|*"</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
class GenerateAllCommand implements GrailsApplicationCommand, CommandLineHelper, SkipBootstrap {

    String description = 'Generates a controller that performs CRUD operations and the associated views'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (!args) {
            error("No domain-class specified")
            return FAILURE
        }
        return new GenerateControllerCommand().handle(executionContext) &&
        new GenerateViewsCommand().handle(executionContext)
    }
}
