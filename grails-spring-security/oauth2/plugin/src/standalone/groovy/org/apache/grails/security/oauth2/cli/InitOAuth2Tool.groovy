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
package org.apache.grails.security.oauth2.cli

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolParameter
import org.apache.grails.core.cli.ToolSupport

/**
 * Creates the domain class that records the OAuth identities of a user and names it in the configuration of
 * the Spring Security OAuth2 plugin.
 *
 * <p>Usage: {@code grails init-oauth2 <domain-class-package> <user-class-name> <oauthid-class-name>}, or
 * {@code ./gradlew runTool -Pargs="init-oauth2 <domain-class-package> <user-class-name> <oauthid-class-name>"}.
 * The user class is taken to be in the package of the domain class unless it is given with its package.</p>
 *
 * For Example:
 * 1. <code>grails init-oauth2 com.yourapp User OAuthID</code>
 * 2. <code>grails init-oauth2 com.yourapp com.yourapp.user.User OAuthID</code>
 *
 * @since 3.0.0
 */
@CompileStatic
@ToolParameter(index = 0, label = 'DOMAIN-CLASS-PACKAGE', arity = '1', description = 'The package the OAuthID class is created in')
@ToolParameter(index = 1, label = 'USER-CLASS-NAME', arity = '1',
        description = 'The user class: its name, in the package of the OAuthID class, or its full name')
@ToolParameter(index = 2, label = 'OAUTHID-CLASS-NAME', arity = '1', description = 'The name of the OAuthID class, without a package')
@ToolHints(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false)
class InitOAuth2Tool implements StandaloneTool, ToolSupport, CommandLineHelper {

    private final static String USAGE_MESSAGE = '''
   grails init-oauth2 <domain-class-package> <user-class-name> <oauthid-class-name>
or ./gradlew runTool -Pargs="init-oauth2 <domain-class-package> <user-class-name> <oauthid-class-name>"

   Creates an OAuthID class in the specified package

Example: grails init-oauth2 com.yourapp User OAuthID
or grails init-oauth2 com.yourapp com.yourapp.user.User OAuthID
'''
    private String packageName
    private Model userClassModel
    private Model oAuthIDClassModel
    private Map<String, String> templateAttributes

    String description = 'Creates the OAuthID domain class and updates the config settings for the Spring Security OAuth2 plugin'

    /**
     * The command writes files and reads nothing of the application, so its {@code BootStrap} classes are not run.
     */
    boolean skipBootstrap = true

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    /**
     * @return {@code init-oauth2}; the name the class gives by convention is {@code init-o-auth2}
     */
    @Override
    String getName() {
        'init-oauth2'
    }

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

    private void updateConfig() {
        File config = file('grails-app/conf/application.groovy')
        config.parentFile.mkdirs()
        config.withWriterAppend { BufferedWriter writer ->
            writer.newLine()
            writer.newLine()
            writer.writeLine('// Added by the Spring Security OAuth2 Google Plugin:')
            writer.writeLine("grails.plugin.springsecurity.oauth2.domainClass = '${packageName}.${oAuthIDClassModel.simpleName}'")
        }
    }

    private void initialize() {
        packageName = args[0]
        userClassModel = model(args[1].contains('.') ? args[1] : packageName + '.' + args[1])
        oAuthIDClassModel = model(packageName + '.' + args[2])
    }

    private void initializeTemplateAttributes() {
        templateAttributes = Collections.unmodifiableMap([
                userClassFullName: userClassModel.fullName,
                userClassName: userClassModel.simpleName,
                oAuthIDPackageName: packageName,
                oAuthIDClassName: oAuthIDClassModel.simpleName,
        ])
    }

    private void generateFile(String templateName, String packagePath, String className) {
        render template(templateName + '.groovy.template'),
                file("grails-app/domain/$packagePath/${className}.groovy"),
                templateAttributes, false
    }

    private void logStatus() {
        consoleLogger.addStatus '''
************************************************************
* Created domain class.                                    *
* Your grails-app/conf/application.groovy has been updated *
* with the class name of the configured domain class;      *
* please verify that the values are correct.               *
************************************************************
'''
    }
}
