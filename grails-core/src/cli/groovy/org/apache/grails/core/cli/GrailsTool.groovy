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

import grails.util.Described
import grails.util.Named
import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * A tool of an application or of a plugin: something the {@code grails} command line runs by its
 * name that is not a command of {@code grails} itself. This is what every tool has, and the type a
 * tool class is registered under in {@code META-INF/grails-cli.factories}.
 *
 * <p>A tool is one of two kinds, and is written as one of them and not as this type:</p>
 * <ul>
 *   <li>an {@link ApplicationTool} is run by the build of the project, with the classes of the
 *       project, and with the application started around it unless it says otherwise;</li>
 *   <li>a {@link StandaloneTool} only works on the files of the project, and is run as a process of
 *       its own, without the build and without the application.</li>
 * </ul>
 *
 * <p>Either kind can also implement {@link ToolSupport}, which adds what a tool that writes files
 * needs: templates, files, models and the arguments of the command line.</p>
 *
 * @since 9.0
 */
@CompileStatic
trait GrailsTool implements Named, Described {

    /**
     * Whether the tool reads what is typed, as a tool that asks questions does: such a tool needs a
     * user at a terminal, and is not run where nothing can be typed. A tool that reads what is typed
     * says so:
     * <pre>
     * boolean readsInput = true
     * </pre>
     * <p>The value has to be the constant {@code true} or {@code false}: the compiler reads it from the
     * source, without running the tool.</p>
     *
     * @since 9.0
     */
    boolean readsInput = false

    /**
     * The name of the tool as it is typed, which its class name gives: {@code CreateReportTool} is
     * {@code create-report}. A class whose name does not end with {@code Tool} is named without the
     * ending {@code Command}: {@code UrlMappingsReportCommand} is {@code url-mappings-report}.
     *
     * @return the name of the tool
     */
    @Override
    String getName() {
        ToolDescriptor.conventionName(getClass().name)
    }

    /**
     * The description of the tool, one line. By default this is the name of the tool.
     *
     * @return the description of the tool
     */
    @Override
    String getDescription() {
        name
    }

    /**
     * Runs the tool.
     *
     * @param executionContext the command line the tool was started with, and the directories of the project
     * @return {@code true} if the tool was successful; {@code false} otherwise
     */
    abstract boolean handle(ExecutionContext executionContext)

}
