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

import grails.codegen.model.Model
import org.apache.grails.core.cli.ExecutionContext
import org.grails.build.parsing.CommandLine
import org.grails.io.support.Resource

trait CommandLineHelper {

    static final boolean SUCCESS = true
    static final boolean FAILURE = false

    /** The option that says which kind of views is generated, as in {@code --views=json} */
    static final String VIEWS_OPTION = 'views'
    static final String GSP_VIEWS = 'gsp'
    static final String JSON_VIEWS = 'json'

    private static final String JSON_VIEWS_PLUGIN = 'grails/plugin/json/view/JsonViewGrailsPlugin.class'
    private static final String VIEWS_DIRECTORY = 'grails-app/views'
    private static final String GSP_EXTENSION = '.gsp'

    abstract ExecutionContext getExecutionContext()

    abstract Resource source(String className)

    abstract Model model(Resource resource)

    abstract Model model(String className)

    abstract File file(Object path)

    /**
     * The kind of views the project is generated for: GSP views with a controller for pages, or JSON views with
     * a REST controller, as the {@code rest-api} profile generated them.
     *
     * <p>The {@code --views} option decides it when it is given. Without it the project decides: JSON views where
     * the JSON views plugin is on the class path and {@code grails-app/views} has no GSP, which is a REST API
     * application or a REST API plugin, and GSP views everywhere else, so an application that has both kinds
     * keeps the GSP views it has always been given.</p>
     *
     * @return {@link #GSP_VIEWS} or {@link #JSON_VIEWS}, or {@code null} when the option names neither
     */
    String viewsKind() {
        final String asked = flag(VIEWS_OPTION)
        if (asked != null) {
            final String kind = asked.toLowerCase(Locale.ROOT)
            return kind in [GSP_VIEWS, JSON_VIEWS] ? kind : null
        }
        hasJsonViewsPlugin() && !hasGspViews() ? JSON_VIEWS : GSP_VIEWS
    }

    /**
     * @return what to say when {@link #viewsKind()} has no answer
     */
    String viewsKindProblem() {
        "The --${VIEWS_OPTION} option takes ${GSP_VIEWS} or ${JSON_VIEWS}, as in --${VIEWS_OPTION}=${JSON_VIEWS}, and not: ${flag(VIEWS_OPTION)}"
    }

    private boolean hasJsonViewsPlugin() {
        final ClassLoader classLoader = Thread.currentThread().contextClassLoader ?: getClass().classLoader
        classLoader.getResource(JSON_VIEWS_PLUGIN) != null
    }

    private boolean hasGspViews() {
        final File views = file(VIEWS_DIRECTORY)
        if (!views.isDirectory()) {
            return false
        }
        boolean found = false
        views.eachFileRecurse { File each ->
            found = found || (each.isFile() && each.name.endsWith(GSP_EXTENSION))
        }
        found
    }

    /**
     * The model of the domain class a scaffolded controller or service is created for.
     *
     * <p>A class with a source file in the project is found by its simple or its full name, and the model has
     * the package of that file. A class without one, which is a domain class of a plugin or of another project
     * of the build, is taken by the name that was given: its full name puts what is created into its package.</p>
     *
     * @param domainClassName the name the command was given
     * @return the model of the class
     */
    Model scaffoldModel(String domainClassName) {
        final Resource sourceClass = source(domainClassName)
        sourceClass ? model(sourceClass) : model(domainClassName)
    }

    boolean isFlagPresent(String name) {
        final CommandLine commandLine = executionContext.commandLine
        if (commandLine.hasOption(name)) {
            return commandLine.optionValue(name) ? true : false
        } else {
            def value = commandLine?.undeclaredOptions?.get(name)
            return value ? true : false
        }
    }

    String flag(String name) {
        final CommandLine commandLine = executionContext.commandLine
        if (commandLine.hasOption(name)) {
            return commandLine.optionValue(name)?.toString()
        } else {
            return commandLine?.undeclaredOptions?.get(name)?.toString()
        }
    }

}
