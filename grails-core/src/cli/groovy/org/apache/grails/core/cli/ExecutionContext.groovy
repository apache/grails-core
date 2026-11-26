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

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString

import grails.util.BuildSettings
import org.grails.build.parsing.CommandLine

/**
 * What a {@link GrailsTool} is run with: the command line, the directories of the project, and what the project is
 *
 * @since 3.0
 */
@CompileStatic
@Canonical
@ToString(allProperties = false)
class ExecutionContext {

    /**
     * The command line instance
     */
    CommandLine commandLine
    /**
     * The base directory for the project
     */
    final File baseDir = BuildSettings.BASE_DIR
    /**
     * The classes directory of the project
     */
    final File classesDir = BuildSettings.CLASSES_DIR
    /**
     * The target directory of the project
     */
    final File targetDir = BuildSettings.TARGET_DIR

    private ProjectInfo project

    /**
     * What the project is, as the record of the {@code grails} command line says it: read from the
     * directory of the project when it is first asked for, and the same whatever started the tool.
     *
     * @return the type of the application and its features; never {@code null}, and for a project
     * without a record one that is not recorded
     * @since 9.0
     */
    ProjectInfo getProject() {
        if (project == null) {
            project = ProjectInfo.of(baseDir)
        }
        project
    }
}
