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
 * Marks an {@link ApplicationCommand} that is run with Gradle, in the build of the project, without
 * the application: a command that needs what only the build has, the compiled classes of the
 * application and the class path the build resolves for it, and has no use for the application
 * context.
 *
 * <p>Such a command is written, registered, listed and typed as any other command: a class that
 * implements {@link ApplicationCommand} or {@link GrailsApplicationCommand}, and this interface as
 * well. What differs is how it is run by the {@code runCommand} task of a build, by the task of the
 * command, and by {@code grails <name>} and {@code grails run-command <name>}, which start that task:</p>
 * <ul>
 *   <li>The application is not started. No application context is made, no plugin is loaded, no data
 *       source is opened and {@code BootStrap} does not run. The class of the application is not
 *       initialized.</li>
 *   <li>{@link ApplicationCommand#getApplicationContext()} is {@code null} and nothing is injected into
 *       the command. A {@code skipBootstrap} property is not read, since there is nothing to skip.</li>
 *   <li>{@link ApplicationCommand#handle(ExecutionContext)} is given the command line as every command
 *       is, with the directories of the project ({@link ExecutionContext#getBaseDir()},
 *       {@link ExecutionContext#getClassesDir()}, {@link ExecutionContext#getTargetDir()}). What
 *       {@link GrailsApplicationCommand} adds, templates, files and models, works as it does in any
 *       command.</li>
 *   <li>The build compiles the project before the command runs, and the class path is the one every
 *       command has: the classes of the application, what it depends on at run time, and the command
 *       line tier of Grails and of the plugins.</li>
 *   <li>The configuration of the application is not loaded. A command that needs a setting reads
 *       the files of the project, {@code grails-app/conf/application.yml} for one, and gets them
 *       as they are written, without what an environment, a profile or a plugin would add.</li>
 *   <li>The result is that of any command: {@code true} ends the JVM with 0, {@code false} or an
 *       exception with 1.</li>
 * </ul>
 *
 * <p>The descriptor of the command, which a build lists it from, names the execution target
 * {@code gradle} instead of {@code application}. What decides how the command is run is this
 * interface on its class, not the descriptor.</p>
 *
 * @since 9.0
 */
interface GradleCommand {

}
