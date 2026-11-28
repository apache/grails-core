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

import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import org.apache.grails.core.cli.ExecutionContext
import org.grails.build.parsing.CommandLine

trait CommandLineHelper {

    static final boolean SUCCESS = true
    static final boolean FAILURE = false

    abstract ExecutionContext getExecutionContext()

    abstract File file(Object path)

    abstract Model model(String className)

    boolean isFlagPresent(String name) {
        String value = flag(name)
        value && value != 'false'
    }

    String flag(String name) {
        final CommandLine commandLine = executionContext.commandLine
        if (commandLine.hasOption(name)) {
            return commandLine.optionValue(name)?.toString()
        }
        commandLine.undeclaredOptions?.get(name)?.toString()
    }

    /**
     * @param known the names of the flags the command has
     * @return the flags the command was given and does not have, as they are written on the command line
     */
    List<String> unknownFlags(List<String> known) {
        Collection<String> given = executionContext.commandLine.undeclaredOptions?.keySet() ?: []
        given.findAll { String name -> !known.contains(name) }.collect { String name -> "--${name}".toString() }
    }

    /**
     * @param name what was given as the name of a package or as the full name of a class
     * @return whether it is one: Java identifiers joined by dots
     */
    boolean isQualifiedName(String name) {
        name ==~ /[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*/
    }

    /**
     * @param name what was given as the name of a class
     * @return whether it is the name of a class without its package
     */
    boolean isSimpleName(String name) {
        isQualifiedName(name) && !name.contains('.')
    }

    /**
     * Reads the one argument of a command that creates a domain class: the full name of that class.
     *
     * @param usage the usage of the command, said with what is wrong
     * @return the model of the class, or {@code null} when the command was given something else, which was said
     */
    Model domainModel(String usage) {
        List<String> arguments = executionContext.commandLine.remainingArgs
        List<String> unknown = unknownFlags([])
        String refusal = null
        if (unknown) {
            refusal = "Unknown option ${unknown.join(', ')}."
        } else if (arguments.size() != 1) {
            refusal = arguments ? 'Too many arguments.' : 'The name of the domain class is required.'
        } else if (!isQualifiedName(arguments[0]) || !arguments[0].contains('.')) {
            refusal = "'${arguments[0]}' is no full name of a class: the name of the domain class is given with its package."
        }
        if (refusal != null) {
            GrailsConsole.getInstance().error("${refusal} Usage:${usage}")
            return null
        }
        model(arguments[0])
    }

    /**
     * Says why the settings of the command cannot be appended to the configuration of the project, when they
     * cannot. A command asks before it writes anything, so that it writes all of its files or none.
     *
     * @return whether the settings can be appended
     */
    boolean canAppendConfig() {
        String why = new ApplicationConfigAppender(file('')).whyNotAppendable()
        if (why != null) {
            GrailsConsole.getInstance().error("${why}, so the command wrote nothing.")
        }
        why == null
    }

    /**
     * Appends settings to the configuration of the project, see {@link ApplicationConfigAppender}.
     *
     * @param settings the settings by their full keys
     * @return the path in the project of the file they were appended to
     */
    String appendConfig(Map<String, Object> settings) {
        new ApplicationConfigAppender(file('')).append('Added by the Spring Security Core plugin:', settings)
    }

}
