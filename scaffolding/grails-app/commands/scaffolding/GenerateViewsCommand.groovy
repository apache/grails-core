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
 * Generates GSP views for the specified domain class
 * Usage: <code>./gradlew runCommand "-Pargs=generate-views [DOMAIN_CLASS_NAME]|*"</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
class GenerateViewsCommand implements GrailsApplicationCommand, CommandLineHelper, SkipBootstrap {

    @Delegate ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (!args) {
            error("No domain-class specified")
            return FAILURE
        }
        List<String> domainClassesNames
        if (args[0] == '*') {
            domainClassesNames = resources("file:grails-app/domain/**/*.groovy")
                    .collect { className(it) }
        } else {
            domainClassesNames = args
        }
        final List<String> viewNames = resolveViewNames()
        boolean overwrite = isFlagPresent('force')
        int failureCount = 0
        for (String domainClassName in domainClassesNames) {
            final Resource sourceClass = source(domainClassName)
            if (sourceClass) {
                final Model model = model(sourceClass)
                viewNames.each { String view ->
                    render(template: template("scaffolding/$view"),
                            destination: file("grails-app/views/$model.propertyName/$view"),
                            model: model,
                            overwrite: overwrite)
                }
                addStatus("Views generated for ${projectPath(sourceClass)}")
            } else {
                error("Domain-class not found for name $domainClassName")
                failureCount++
            }
        }
        return failureCount ? FAILURE : SUCCESS
    }

    private List<String> resolveViewNames() {
        List<String> viewNames = resources("file:src/main/templates/scaffolding/*.gsp")
                .collect { it.filename }
        if (!viewNames) {
            viewNames = resources("classpath*:META-INF/templates/scaffolding/*.gsp")
                    .collect { it.filename }
        }
        viewNames
    }
}
