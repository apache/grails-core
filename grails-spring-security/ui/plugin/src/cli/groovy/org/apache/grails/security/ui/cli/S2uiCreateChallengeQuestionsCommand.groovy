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
package org.apache.grails.security.ui.cli

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolSupport

/**
 * Generates what the challenge questions of the forgotten password workflow need:
 * {@code s2ui-create-challenge-questions <domain-class-package> <challenge-qa-class-name> <user-domain-class-name> [number-of-questions]}.
 *
 * <p>It writes the domain class, its two services, its controller and three views, and adds the configuration of
 * the workflow to {@code grails-app/conf/application.groovy} and the caption of the menu to
 * {@code grails-app/i18n/messages.properties}. A file that is there is kept; the two files that are added to are
 * added to each time the command runs.</p>
 *
 * @since 9.0
 */
@CompileStatic
class S2uiCreateChallengeQuestionsCommand implements StandaloneTool, ToolSupport, CommandLineHelper {

    static final String USAGE_MESSAGE = '''
grails s2ui-create-challenge-questions <domain-class-package> <challenge-qa-class-name> <user-domain-class-name> [number-of-questions]

domain-class-package is required and is the package where the challenge questions class will live
challenge-qa-class-name is required and is the name of the domain class used to store this information
user-domain-class-name is required and is the package and name of the User class. In most circumstances it is in the same package but it will work if they are different.
number-of-questions is optional but if not given will default to 2.

Example: grails s2ui-create-challenge-questions com.mycompany ChallengeQuestion com.mycompany.User 2
'''

    private static final int DEFAULT_NUMBER_OF_QUESTIONS = 2

    final String description = 'Creates the domain class, services, controller and views of the challenge questions, and their configuration and menu caption'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (args.size() < 3 || args.size() > 4) {
            consoleLogger.error("Three or four arguments are required\n\nUsage:\n$USAGE_MESSAGE")
            return FAILURE
        }

        final String domainClassName = "${args[0].toLowerCase(Locale.ROOT)}.${args[1]}"
        if (!isQualifiedName(domainClassName)) {
            consoleLogger.error("'${args[0]}' and '${args[1]}' are not the names of a package and of a class\n\nUsage:\n$USAGE_MESSAGE")
            return FAILURE
        }
        if (!isQualifiedName(args[2])) {
            consoleLogger.error("'${args[2]}' is not the name of a class\n\nUsage:\n$USAGE_MESSAGE")
            return FAILURE
        }

        int numberOfQuestions = DEFAULT_NUMBER_OF_QUESTIONS
        if (args.size() > 3) {
            if (!args[3].isInteger() || args[3].toInteger() < 1) {
                consoleLogger.error("The number of questions '${args[3]}' is not a number of 1 or more\n\nUsage:\n$USAGE_MESSAGE")
                return FAILURE
            }
            numberOfQuestions = args[3].toInteger()
        }

        // both classes are named in full here: the default package of the application is for neither
        final Model saModel = model(domainClassName)
        final Model userModel = model(args[2].contains('.') ? args[2] : "${saModel.packageName}.${args[2]}")
        final String userPackage = userModel.packageName.toLowerCase(Locale.ROOT)
        final String camelCaseSaName = uncapitalized(saModel.simpleName)

        final Map<String, Object> templateAttributes = [
                packageName: saModel.packageName,
                saClassName: saModel.simpleName,
                camelCaseSaName: camelCaseSaName,
                saClassProperty: saModel.modelName,
                numberOfQuestions: numberOfQuestions,
                userPropName: uncapitalized(userModel.modelName),
                userDomainName: (userPackage == saModel.packageName ? '' : userPackage + '.') + userModel.simpleName
        ] as Map<String, Object>

        try {
            final String packagePath = saModel.packageName.replace('.', '/')
            final String services = "grails-app/services/${packagePath}"
            final String views = "grails-app/views/${camelCaseSaName}"

            renderTemplate('ChallengeQuestionsService.groovy.template', "${services}/${saModel.simpleName}Service.groovy", templateAttributes)
            renderTemplate('ChallengeQuestionsListenerService.groovy.template', "${services}/${saModel.simpleName}ListenerService.groovy", templateAttributes)
            renderTemplate('ChallengeQuestions.groovy.template', "grails-app/domain/${packagePath}/${saModel.simpleName}.groovy", templateAttributes)
            renderTemplate('ChallengeQuestionsController.groovy.template', "grails-app/controllers/${packagePath}/${saModel.simpleName}Controller.groovy", templateAttributes)
            renderTemplate('ChallengeQuestionsEdit.gsp.template', "${views}/edit.gsp", templateAttributes)
            renderTemplate('ChallengeQuestionsCreate.gsp.template', "${views}/create.gsp", templateAttributes)
            renderTemplate('ChallengeQuestionsIndex.gsp.template', "${views}/index.gsp", templateAttributes)

            appendTo('grails-app/conf/application.groovy') { BufferedWriter writer ->
                writer.newLine()
                writer.writeLine('// Added by the Spring Security UI plugin:')
                writer.writeLine("grails.plugin.springsecurity.ui.forgotPassword.forgotPasswordExtraValidationDomainClassName = '${saModel.packageName}.${saModel.simpleName}'")
                writer.writeLine('grails.plugin.springsecurity.ui.forgotPassword.forgotPasswordExtraValidation = [')
                for (int i = 1; i <= numberOfQuestions; i++) {
                    writer.writeLine("\t[labelDomain: 'myQuestion$i', prop:'myAnswer$i'],")
                }
                writer.writeLine(']')
                writer.newLine()
            }

            appendTo('grails-app/i18n/messages.properties') { BufferedWriter writer ->
                writer.newLine()
                writer.writeLine("spring.security.ui.menu.${saModel.simpleName}=${saModel.simpleName} Questions")
                writer.newLine()
            }
        } catch (e) {
            consoleLogger.error(e.message, e)
            return FAILURE
        }

        consoleLogger.addStatus('Finished s2ui-create-challenge-questions!')
        return SUCCESS
    }

    private void renderTemplate(String name, String destination, Map<String, Object> attributes) {
        render(template(name), file(destination), attributes, false)
    }

    private void appendTo(String path, Closure<?> lines) {
        final File destination = file(path)
        destination.parentFile.mkdirs()
        destination.withWriterAppend(lines)
    }

    private static String uncapitalized(String name) {
        name ? name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1) : name
    }
}
