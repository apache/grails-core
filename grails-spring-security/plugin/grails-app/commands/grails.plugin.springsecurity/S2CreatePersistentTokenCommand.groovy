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
 * Creates a persistent token domain class for the Spring Security Core plugin.
 * Usage: <code>./grailsw s2-create-persistent-token [DOMAIN CLASS NAME]</code>
*
 * For example: <code>./grailsw s2-create-persistent-token com.yourapp.PersistentLogin</code>
 *
 * @author Puneet Behl
 * @since 6.0.0
 */
@CompileStatic
class S2CreatePersistentTokenCommand implements GrailsApplicationCommand, CommandLineHelper, SkipBootstrap {

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    private final static USAGE_MESSAGE = '''
./grailsw s2-create-persistent-token [DOMAIN CLASS NAME]

For example: ./grailsw s2-create-persistent-token com.yourapp.PersistentLogin
'''

    @Override
    boolean handle() {

        if (args.size() == 0) {
            consoleLogger.error('Usage: ' + USAGE_MESSAGE)
            return FAILURE
        }

        final String domainClass = args[0]
        final Model domainModel = model(domainClass)
        consoleLogger.addStatus("\nCreating persistent token class $domainClass")
        render(template: template('PersistentLogin.groovy.template'),
                destination: file("grails-app/domain/$domainModel.packagePath/${domainModel.simpleName}.groovy"),
                model: domainModel,
                overrite: false
        )
        file('grails-app/conf/application.groovy').withWriterAppend { BufferedWriter writer ->
            writer.newLine()
            writer.writeLine 'grails.plugin.springsecurity.rememberMe.persistent = true'
            writer.writeLine "grails.plugin.springsecurity.rememberMe.persistentToken.domainClassName = '$domainClass'"
            writer.newLine()
        }
        return SUCCESS
    }
}
