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
package org.apache.grails.core.cli

/**
 * Marks an {@link ApplicationCommand} that is run as a process of its own, without Gradle and
 * without the application: a command that only works on the files of the project, such as one that
 * renders templates into it, and needs neither the application context nor anything the build does
 * for a command.
 *
 * <p>A command is one of three kinds, and its class says which:</p>
 * <ul>
 *   <li>a command that implements neither marker is run with the application started around it,
 *       by the build of the project;</li>
 *   <li>a command that implements {@link GradleCommand} is run by the build of the project, without
 *       the application: the build compiles the project first and resolves the class path;</li>
 *   <li>a command that implements this interface is run by the {@code grails} command line as a
 *       plain JVM, which {@link StandaloneCommandRunner} is the main class of: no build is started
 *       for it while nothing it is made from has changed.</li>
 * </ul>
 *
 * <p>Such a command is written, registered, listed and typed as any other command. What holds for it:</p>
 * <ul>
 *   <li>The application is not started: {@link ApplicationCommand#getApplicationContext()} is
 *       {@code null}, nothing is injected into the command, and the configuration of the application
 *       is not loaded.</li>
 *   <li>{@link ApplicationCommand#handle(ExecutionContext)} is given the command line as every command
 *       is, with the directories of the project. What {@link GrailsApplicationCommand} adds,
 *       templates, files and models, works as it does in any command.</li>
 *   <li>{@code grails <name>} starts the command with the class path the last build of the project
 *       wrote down for it, in {@code .grails/cache/standalone-commands.json}: a command of a plugin
 *       has what the project depends on at run time and the command line tier of Grails and of the
 *       plugins, <em>without the classes of the application</em>, which may not compile at the
 *       moment; a command of the application itself has the classes of the application as well.
 *       A command of a plugin that needs the classes of the application is a {@link GradleCommand}.</li>
 *   <li>When the build files, or for a command of the application its sources, were changed after
 *       that class path was written down, the command line runs the build once to bring it up to
 *       date, and then starts the command. No classes are run that are older than their sources.</li>
 *   <li>The {@code runCommand} task of a build, the task of the command and
 *       {@code grails run-command <name>} run the command as well, with Gradle, as they run a
 *       {@link GradleCommand}: without the application.</li>
 *   <li>The result is that of any command: {@code true} ends the JVM with 0, {@code false} or an
 *       exception with 1.</li>
 * </ul>
 *
 * <p>The descriptor of the command, which a build lists it from, names the execution target
 * {@code standalone}. A class that implements this interface and {@link GradleCommand} is a
 * standalone command.</p>
 *
 * @since 9.0
 */
interface StandaloneCommand {

}
