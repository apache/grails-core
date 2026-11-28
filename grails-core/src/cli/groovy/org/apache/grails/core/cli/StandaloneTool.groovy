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

import groovy.transform.CompileStatic

/**
 * A tool that only works on the files of the project, such as one that renders templates into it:
 * it is run as a process of its own, by the {@code grails} command line as a plain JVM which
 * {@link StandaloneToolRunner} is the main class of, without the build and without the application.
 * Its source is in {@code src/standalone} of an application or a plugin.
 *
 * <p>What holds for such a tool:</p>
 * <ul>
 *   <li>The application is not started, nothing is injected into the tool, and the configuration of
 *       the application is not loaded. A tool that needs a setting reads the files of the project.</li>
 *   <li>The classes of the project are not on its class path, which is why it runs while the project
 *       does not compile. A tool that needs them is an {@link ApplicationTool}.</li>
 *   <li>{@link GrailsTool#handle(ExecutionContext)} is given the command line as every tool is, with
 *       the directories of the project and what the project is ({@link ExecutionContext#getProject()}).
 *       What {@link ToolSupport} adds, templates, files and models, works as it does in any tool.</li>
 *   <li>A build that is asked for the tool runs it as well, without the application.</li>
 *   <li>The result is that of any tool: {@code true} ends the JVM with 0, {@code false} or an
 *       exception with 1.</li>
 * </ul>
 *
 * @since 9.0
 */
@CompileStatic
trait StandaloneTool implements GrailsTool {

}
