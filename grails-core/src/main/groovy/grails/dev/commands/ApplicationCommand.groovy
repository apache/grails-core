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
package grails.dev.commands

import groovy.transform.CompileStatic

import org.springframework.context.ConfigurableApplicationContext

import grails.util.Described
import grails.util.GrailsNameUtils
import grails.util.Named

/**
 * Represents a command that runs with access to the
 * {@link org.springframework.context.ApplicationContext}.
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
trait ApplicationCommand implements Named, Described {

    ConfigurableApplicationContext applicationContext

    /**
     * Calculates the command name as used on the command line.
     * <p>Example:
     * {@code UrlMappingsReportCommand} -> {@code url-mappings-report}
     * @return The command line name of the command
     */
    @Override
    String getName() {
        GrailsNameUtils.getScriptName(
                GrailsNameUtils.getLogicalName(
                        getClass().name,
                        'Command'
                )
        )
    }

    /**
     * The description of the command.
     * By default this returns the name of the command.
     * @return The description of the command
     */
    @Override
    String getDescription() {
        name
    }

    /**
     * Executes the command.
     *
     * @param executionContext The execution context
     * @return {@code true} if the command was successful; {@code false} otherwise
     */
    abstract boolean handle(ExecutionContext executionContext)

}
