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

    abstract ExecutionContext getExecutionContext()

    abstract Resource source(String className)

    abstract Model model(Resource resource)

    abstract Model model(String className)

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
