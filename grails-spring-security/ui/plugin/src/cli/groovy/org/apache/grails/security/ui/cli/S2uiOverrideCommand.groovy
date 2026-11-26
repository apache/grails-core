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
import grails.util.GrailsNameUtils
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.StandaloneCommand
import org.grails.io.support.Resource

/**
 * Copies a controller of the Spring Security UI plugin and its GSPs into the application, where they can be
 * changed: {@code s2ui-override <type> [controllerPackage]}.
 *
 * <p>The controller that is written is empty and extends the controller of the plugin. The GSPs are the views the
 * plugin ships among its templates under {@code views/}; a file the project has under
 * {@code src/main/templates} at the same path is taken in its place. A controller that is there is kept, a GSP
 * that is there is written again.</p>
 *
 * @since 9.0
 */
@CompileStatic
class S2uiOverrideCommand implements GrailsApplicationCommand, StandaloneCommand, CommandLineHelper {

    static final String USAGE_MESSAGE = '''
grails s2ui-override <type> [controllerPackage]

type can be one of aclclass, aclentry, aclobjectidentity, aclsid, persistentlogin,
register, registrationcode, requestmap, role, securityinfo, user, auth, or layout (not case-sensitive)

controllerPackage is required for any type that has a controller (i.e. all but 'auth' and 'layout')

Example: grails s2ui-override user com.yourcompany.yourapp
'''

    private static final String PLUGIN_CONTROLLER_PACKAGE = 'grails.plugin.springsecurity.ui'

    private static final Map<String, String> CONTROLLERS = [
            aclclass: 'AclClass',
            aclentry: 'AclEntry',
            aclobjectidentity: 'AclObjectIdentity',
            aclsid: 'AclSid',
            persistentlogin: 'PersistentLogin',
            register: 'Register',
            registrationcode: 'RegistrationCode',
            requestmap: 'Requestmap',
            role: 'Role',
            securityinfo: 'SecurityInfo',
            user: 'User'
    ].asImmutable()

    private static final List<String> CREATE_EDIT_SEARCH = ['create', 'edit', 'search'].asImmutable()

    private static final Map<String, List<String>> VIEWS = [
            aclClass: CREATE_EDIT_SEARCH,
            aclEntry: CREATE_EDIT_SEARCH,
            aclObjectIdentity: CREATE_EDIT_SEARCH,
            aclSid: CREATE_EDIT_SEARCH,
            persistentLogin: ['search', 'edit'],
            register: ['forgotPassword', 'register', 'resetPassword', '_forgotPasswordMail',
                       '_verifyRegistrationMail', 'securityQuestions'],
            registrationCode: ['search', 'edit'],
            requestmap: CREATE_EDIT_SEARCH,
            role: CREATE_EDIT_SEARCH,
            securityInfo: ['config', 'currentAuth', 'filterChains', 'logoutHandlers', 'mappings', 'providers',
                           'secureChannel', 'usercache', 'voters'],
            user: CREATE_EDIT_SEARCH
    ].asImmutable()

    final String description = 'Copies plugin controllers and GSPs to the application so they can be overridden'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (!args) {
            consoleLogger.error("The type is required\n\nUsage:\n$USAGE_MESSAGE")
            return FAILURE
        }
        if (args.size() > 2) {
            consoleLogger.error("Too many arguments: ${args.join(' ')}\n\nUsage:\n$USAGE_MESSAGE")
            return FAILURE
        }

        final String givenType = args[0]
        final String type = givenType.toLowerCase(Locale.ROOT)
        final String controllerPackage = args.size() > 1 ? args[1] : ''

        try {
            if (type == 'layout' || type == 'auth') {
                // the two types that are views alone: a package given with them is a mistake, which is said as one
                if (controllerPackage) {
                    consoleLogger.error("The type '${givenType}' has no controller and takes no controller package: ${controllerPackage}\n\nUsage:\n$USAGE_MESSAGE")
                    return FAILURE
                }
                if (type == 'layout') {
                    copyView('layouts/springSecurityUI.gsp')
                    copyView('includes/_ajaxLogin.gsp')
                } else {
                    copyView('login/auth.gsp')
                }
                return SUCCESS
            }

            final String controller = CONTROLLERS[type]
            if (!controller) {
                consoleLogger.error("Unknown type '${givenType}'\n\nUsage:\n$USAGE_MESSAGE")
                return FAILURE
            }
            if (!controllerPackage) {
                consoleLogger.error("The controller package is required for type '${givenType}'\n\nUsage:\n$USAGE_MESSAGE")
                return FAILURE
            }
            if (controllerPackage == PLUGIN_CONTROLLER_PACKAGE) {
                consoleLogger.error('The controller package cannot be the same as the plugin controller')
                return FAILURE
            }
            if (!isQualifiedName(controllerPackage)) {
                consoleLogger.error("The controller package '${controllerPackage}' is not the name of a package\n\nUsage:\n$USAGE_MESSAGE")
                return FAILURE
            }

            consoleLogger.addStatus("Copying ${givenType} resources")

            render(template("${controller}Controller.groovy.template"),
                    file("grails-app/controllers/${controllerPackage.replace('.', '/')}/${controller}Controller.groovy"),
                    [packageDeclaration: "package $controllerPackage"], false)

            final String directory = GrailsNameUtils.getPropertyName(controller)
            for (String view in VIEWS[directory]) {
                copyView("${directory}/${view}.gsp")
            }
            if (type == 'register') {
                copyView('layouts/register.gsp')
                copyView('layouts/email.gsp')
            }
            return SUCCESS
        } catch (e) {
            consoleLogger.error(e.message, e)
            return FAILURE
        }
    }

    /**
     * Copies a view of the plugin to the same path under {@code grails-app/views} of the application.
     *
     * @param path the path of the view under the views of the plugin
     */
    private void copyView(String path) {
        final Resource view = template("views/${path}")
        if (!view?.exists()) {
            throw new IllegalStateException("The view ${path} of the plugin was not found among its templates")
        }
        final File directory = file("grails-app/views/${path.substring(0, path.lastIndexOf('/'))}")
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("The directory ${projectPath(directory)} of the view ${path} could not be made")
        }
        copy(view, directory)
    }
}
