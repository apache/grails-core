/*
 * Copyright 2023 Puneet Behl.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springsecurity

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import grails.dev.commands.GrailsApplicationCommand
import groovy.transform.CompileStatic

/**
 * Creates a domain class for a persistent role hierarchy for the Spring Security Core plugin
 * Usage: <code>./grailsw s2-create-role-hierarchy-entry [DOMAIN CLASS NAME]
 * For example: <code>./grailsw s2-create-role-hierarchy-entry com.yourapp.RoleHierarchyEntry</code>
 *
 * @author Puneet Behl
 * @since 6.0.0
 */
@CompileStatic
class S2CreateRoleHierarchyEntryCommand implements GrailsApplicationCommand, CommandLineHelper, SkipBootstrap {

    private static final String USAGE_MESSAGE = '''
./grailsw s2-create-role-hierarchy-entry [DOMAIN CLASS NAME] 
 
 For example: ./grailsw s2-create-role-hierarchy-entry com.yourapp.RoleHierarchyEntry
'''

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {

        if (args.size() == 0) {
            consoleLogger.error('Usage: ' + USAGE_MESSAGE)
            return FAILURE
        }

        final String domainClass = args[0]
        final Model domainModel = model(domainClass)

        consoleLogger.addStatus("\nCreating role hierarchy entry class $domainClass")
        render(template: template('RoleHierarchyEntry.groovy.template'),
                destination: file("grails-app/domain/$domainModel.packagePath/${domainModel.simpleName}.groovy"),
                model: domainModel, overwrite: false)

        file('grails-app/conf/application.groovy').withWriterAppend { BufferedWriter writer ->
            writer.newLine()
            writer.writeLine "grails.plugin.springsecurity.roleHierarchyEntryClassName = '$domainClass'"
            writer.newLine()
        }

        return SUCCESS
    }
}
