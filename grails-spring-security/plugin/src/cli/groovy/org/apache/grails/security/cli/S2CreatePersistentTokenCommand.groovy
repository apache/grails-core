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

package org.apache.grails.security.cli

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.StandaloneCommand

/**
 * Creates a persistent token domain class for the Spring Security Core plugin.
 * Usage: <code>s2-create-persistent-token DOMAIN_CLASS_NAME</code>
 *
 * For example: <code>s2-create-persistent-token com.yourapp.PersistentLogin</code>
 *
 * @since 6.0.0
 */
@CompileStatic
class S2CreatePersistentTokenCommand implements GrailsApplicationCommand, StandaloneCommand, CommandLineHelper, SkipBootstrap {

    private static final String USAGE_MESSAGE = '''
s2-create-persistent-token <domain-class-name>

Example: s2-create-persistent-token com.yourapp.PersistentLogin
'''

    String description = 'Creates a persistent token domain class for the Spring Security Core plugin'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        final Model domainModel = domainModel(USAGE_MESSAGE)
        if (domainModel == null || !canAppendConfig()) {
            return FAILURE
        }
        final String domainClass = "${domainModel.packageName}.${domainModel.simpleName}"

        addStatus("\nCreating persistent token class ${domainClass}")
        render(template: template('PersistentLogin.groovy.template'),
                destination: file("grails-app/domain/${domainModel.packagePath}/${domainModel.simpleName}.groovy"),
                model: domainModel,
                overwrite: false)

        String path = appendConfig([
                'grails.plugin.springsecurity.rememberMe.persistent': true,
                'grails.plugin.springsecurity.rememberMe.persistentToken.domainClassName': domainClass
        ] as Map<String, Object>)
        addStatus("\nYour ${path} has been updated with the name of the class; please verify that the values are correct.")
        return SUCCESS
    }
}
