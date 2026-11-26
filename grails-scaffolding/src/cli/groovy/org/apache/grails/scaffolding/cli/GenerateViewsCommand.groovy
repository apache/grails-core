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
package org.apache.grails.scaffolding.cli

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolSupport
import org.grails.io.support.Resource

/**
 * Generates GSP views for the specified domain class
 * Usage: <code>./gradlew runCommand "-Pargs=generate-views [DOMAIN_CLASS_NAME]|*"</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
class GenerateViewsCommand implements StandaloneTool, ToolSupport, CommandLineHelper, SkipBootstrap {

    private static final String DOMAIN_TEMPLATE = '_domain.gson'
    private static final List<String> JSON_VIEW_NAMES = ['index.gson', 'show.gson', DOMAIN_TEMPLATE].asImmutable()

    String description = 'Generates the views for the specified domain class: GSP views, or JSON views in a REST application'

    @Delegate ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    @Override
    boolean handle() {
        if (!args) {
            error('No domain-class specified')
            return FAILURE
        }
        List<String> domainClassesNames
        if (args[0] == '*') {
            domainClassesNames = resources('file:grails-app/domain/**/*.groovy')
                    .collect { className(it) }
        } else {
            domainClassesNames = args
        }
        final String views = viewsKind()
        if (views == null) {
            error(viewsKindProblem())
            return FAILURE
        }
        final boolean json = views == JSON_VIEWS
        final List<String> viewNames = json ? JSON_VIEW_NAMES : resolveViewNames()
        boolean overwrite = isFlagPresent('force')
        int failureCount = 0
        for (String domainClassName in domainClassesNames) {
            final Resource sourceClass = source(domainClassName)
            if (sourceClass) {
                final Model model = model(sourceClass)
                viewNames.each { String view ->
                    // the template of the JSON views of a class is named after the class in the project
                    final String written = view == DOMAIN_TEMPLATE ? "_${model.propertyName}.gson" : view
                    render(template: template("scaffolding/$view"),
                            destination: file("grails-app/views/$model.propertyName/$written"),
                            model: model,
                            overwrite: overwrite)
                }
                addStatus("Views generated for ${projectPath(sourceClass)}")
            } else {
                error("Domain class not found for name: $domainClassName")
                failureCount++
            }
        }
        return failureCount ? FAILURE : SUCCESS
    }

    private List<String> resolveViewNames() {
        List<String> viewNames = resources('file:src/main/templates/scaffolding/*.gsp')
                .collect { it.filename }
        if (!viewNames) {
            viewNames = resources('classpath*:META-INF/templates/scaffolding/*.gsp')
                    .collect { it.filename }
        }
        viewNames
    }
}
