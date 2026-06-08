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
package grails.plugin.springsecurity.oauth2

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import grails.dev.commands.GrailsApplicationCommand
import groovy.transform.CompileStatic

/**
 * Creates domain classes and updates config settings for the Spring Security OAuth2 plugin.
 * Usage: <code>./gradlew runCommand "-Pargs=init-oauth2 [DOMAIN-CLASS-PACKAGE] [USER-CLASS-NAME] [OAUTH-ID-CLASS-NAME]"</code>
 *
 * For Example:
 * 1. <code>./gradlew runCommand "-Pargs=init-oauth2 com.yourapp User OAuthID"</code>
 * 2. <code>./gradlew runCommand "-Pargs=init-oauth2 com.yourapp com.yourapp.user.User OAuthID"</code>
 *
 * @author Puneet Behl
 * @since 3.0.0
 */
@CompileStatic
class InitOAuth2Command implements GrailsApplicationCommand, CommandLineHelper {

    private final static String USAGE_MESSAGE = '''
   ./gradlew runCommand "-Pargs=init-oauth2 [DOMAIN-CLASS-PACKAGE] [USER-CLASS-NAME] [OAUTH-ID-CLASS-NAME]"

Example: ./gradlew runCommand "-Pargs=init-oauth2 com.yourapp User OAuthID"
'''
    private String packageName
    private Model userClassModel
    private Model oAuthIDClassModel
    private Map<String, String> templateAttributes

    String description = "Creates domain class and update the config settings fpr the Grails Spring Security OAuth2 plugin"

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (args.size() < 3) {
            consoleLogger.error 'Usage:' + USAGE_MESSAGE
            return FAILURE
        }

        initialize()
        initializeTemplateAttributes()

        consoleLogger.addStatus "Creating OAuthID class '${oAuthIDClassModel.simpleName}'"
        generateFile 'OAuthID', oAuthIDClassModel.packagePath, oAuthIDClassModel.simpleName
        updateConfig()
        logStatus()
        return SUCCESS
    }

    private Object updateConfig() {
        file('grails-app/conf/application.groovy')
                .withWriterAppend { BufferedWriter writer ->
                    writer.newLine()
                    writer.newLine()
                    writer.writeLine("// Added by the Spring Security OAuth2 Google Plugin:")
                    writer.writeLine("grails.plugin.springsecurity.oauth2.domainClass = '${packageName}.${oAuthIDClassModel.simpleName}'")
                }
    }

    private Model initialize() {
        packageName = args[0]
        userClassModel = model(args[1].contains('.') ? args[1] : packageName + '.' + args[1])
        oAuthIDClassModel = model(packageName + '.' + args[2])
    }

    private void initializeTemplateAttributes() {
        templateAttributes = Collections.unmodifiableMap([
                userClassFullName : userClassModel.fullName,
                userClassName     : userClassModel.simpleName,
                oAuthIDPackageName: packageName,
                oAuthIDClassName  : oAuthIDClassModel.simpleName,
        ])
    }

    private void generateFile(String templateName, String packagePath, String className, String fileName = null, String folder = 'grails-app/domain') {
        render template(templateName + '.groovy.template'),
                file("${folder}/$packagePath/${fileName ?: className}.groovy"),
                templateAttributes, false
    }

    private void logStatus() {
        consoleLogger.addStatus '''
************************************************************
* Created  domain class.                                  *
* Your grails-app/conf/application.groovy has been updated *
* with the class name of the configured domain class;      *
* please verify that the values are correct.               *
************************************************************
'''
    }
}
