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

package org.apache.grails.security.cli.standalone

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolSupport

/**
 * Creates a domain class for a persistent role hierarchy for the Spring Security Core plugin.
 * Usage: <code>s2-create-role-hierarchy-entry DOMAIN_CLASS_NAME</code>
 *
 * For example: <code>s2-create-role-hierarchy-entry com.yourapp.RoleHierarchyEntry</code>
 *
 * @since 6.0.0
 */
@CompileStatic
class S2CreateRoleHierarchyEntryTool implements StandaloneTool, ToolSupport, CommandLineHelper, SkipBootstrap {

    private static final String USAGE_MESSAGE = '''
s2-create-role-hierarchy-entry <domain-class-name>

Example: s2-create-role-hierarchy-entry com.yourapp.RoleHierarchyEntry
'''

    String description = 'Creates a domain class for a persistent role hierarchy for the Spring Security Core plugin'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        final Model domainModel = domainModel(USAGE_MESSAGE)
        if (domainModel == null || !canAppendConfig()) {
            return FAILURE
        }
        final String domainClass = "${domainModel.packageName}.${domainModel.simpleName}"

        addStatus("\nCreating role hierarchy entry class ${domainClass}")
        render(template: template('RoleHierarchyEntry.groovy.template'),
                destination: file("grails-app/domain/${domainModel.packagePath}/${domainModel.simpleName}.groovy"),
                model: domainModel,
                overwrite: false)

        String path = appendConfig([
                'grails.plugin.springsecurity.roleHierarchyEntryClassName': domainClass
        ] as Map<String, Object>)
        addStatus("\nYour ${path} has been updated with the name of the class; please verify that the values are correct.")
        return SUCCESS
    }
}
