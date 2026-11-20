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

    @CommandLine.Option(names = ['-D'], paramLabel = 'NAME=VALUE', mapFallbackValue = '',
            description = 'A system property for the build; can be given more than once. -Dgrails.env=NAME selects the Grails environment.')
    SystemProperties systemProperties = new SystemProperties()

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
     * The system properties given to a command. The type is the command's own on purpose: picocli asks
     * the factory of the CLI for the map of an option, and the factory would answer for a plain
     * {@code Map} with a bean of the application, which the properties would then be put into.
     */
    static class SystemProperties extends LinkedHashMap<String, String> {
    }
}
