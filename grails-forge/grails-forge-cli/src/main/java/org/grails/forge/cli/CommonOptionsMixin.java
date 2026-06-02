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
package org.grails.forge.cli;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.grails.forge.cli.util.GrailsVersionProvider;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 * Mixin that adds help, version and other common options to a command. Example usage:
 * <pre>
 * &#064;Command(name = "command")
 * class App {
 *     &#064;Mixin
 *     CommonOptionsMixin commonOptions // adds common options to the command
 *
 *     // ...
 * }
 * </pre>
 *
 * @author Remko Popma
 * @version 1.0
 */
@CommandLine.Command(mixinStandardHelpOptions = true, versionProvider = GrailsVersionProvider.class)
@SuppressWarnings("checkstyle:VisibilityModifier")
public class CommonOptionsMixin {

    @Option(names = {"-x", "--stacktrace"}, defaultValue = "false", description = "Show full stack trace when exceptions occur.")
    @ReflectiveAccess
    public boolean showStacktrace;

    @Option(names = {"-v", "--verbose"}, defaultValue = "false", description = "Create verbose output.")
    @ReflectiveAccess
    public boolean verbose;
}
