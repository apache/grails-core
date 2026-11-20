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
package org.apache.grails.forge.cli.console

import groovy.transform.CompileStatic
import org.grails.forge.feature.FeatureSelectionException
import picocli.CommandLine

/**
 * A Gradle command that runs the build for a Grails environment and passes system properties on:
 * {@code -Dname=value} is given to Gradle as it is, and {@code -Dgrails.env=NAME} selects the
 * environment, which is otherwise the one the command has by default.
 *
 * @since 9.0
 */
@CompileStatic
abstract class GradleEnvironmentCommand extends GradleTaskCommand {

    private static final String ENVIRONMENT = 'grails.env'
    private static final String SYSTEM_PROPERTY = '-D'

    /**
     * @return the environment the build runs for when none is given
     */
    protected abstract String defaultEnvironment()

    /**
     * @return the environment the build runs for
     */
    protected String environment() {
        String given = systemProperties.get(ENVIRONMENT)
        return given ? given : defaultEnvironment()
    }

    /**
     * @return the arguments that select the environment and pass the system properties on
     */
    protected List<String> systemPropertyArguments() {
        List<String> arguments = ["-D${ENVIRONMENT}=${environment()}".toString()]
        systemProperties.each { String name, String value ->
            if (name != ENVIRONMENT) {
                arguments.add("-D${name}=${value}".toString())
            }
        }
        return arguments
    }

    /**
     * Refuses a {@code -D} that names no system property, before the command line is read: picocli
     * would take the argument after a {@code -D} that stands alone for the name of a property without
     * a value, so that {@code -D BookSpec} would set a property and run every test.
     *
     * @param args the arguments of the command, the next one on top
     * @param commandSpec the command
     */
    static void checkSystemProperties(List<String> args, CommandLine.Model.CommandSpec commandSpec) {
        if (commandSpec.findOption(SYSTEM_PROPERTY) == null) {
            return
        }
        for (int i = args.size() - 1; i >= 0; i--) {
            String arg = args.get(i)
            if (arg == '--') {
                return
            }
            if (!arg.startsWith(SYSTEM_PROPERTY)) {
                continue
            }
            boolean attached = arg.length() > SYSTEM_PROPERTY.length()
            String given = attached ? arg.substring(SYSTEM_PROPERTY.length()) : (i > 0 ? args.get(i - 1) : null)
            // -Dname is the property without a value; after a -D that stands alone only name=value is one
            if (given == null || given.startsWith('=') || (!attached && given.indexOf('=') < 1)) {
                throw new CommandLine.ParameterException(commandSpec.commandLine(),
                        "${SYSTEM_PROPERTY} is followed by the name of a system property and its value, as in -Dgrails.env=test: " +
                                "${FeatureSelectionException.shown(attached || given == null ? arg : arg + ' ' + given)} names none")
            }
        }
    }
}
