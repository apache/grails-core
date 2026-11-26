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
package org.apache.grails.gradle.model.cli

import groovy.transform.CompileStatic

/**
 * The names the build of a project has for what belongs to its tools: the folders of their sources,
 * the source sets, the configurations, the tasks, and how a plugin says which companion artifacts
 * its tools are in. The Grails Gradle plugin, which makes them, and the {@code grails} command line,
 * which asks a build for them, agree on them here.
 *
 * @since 9.0
 */
@CompileStatic
final class ToolBuildNames {

    /** The folder of the sources of the tools the build runs, in the directory of a project. */
    public static final String CLI_SOURCE_FOLDER = 'src/cli'

    /** The folder of the sources of the tools that run as a process of their own, in the directory of a project. */
    public static final String STANDALONE_SOURCE_FOLDER = 'src/standalone'

    /** The deprecated folder of the sources of tools, which are compiled with those of {@link #CLI_SOURCE_FOLDER}. */
    public static final String LEGACY_SOURCE_FOLDER = 'grails-app/commands'

    /** The source set of {@link #CLI_SOURCE_FOLDER}. */
    public static final String CLI_SOURCE_SET = 'cli'

    /** The source set of {@link #STANDALONE_SOURCE_FOLDER}. */
    public static final String STANDALONE_SOURCE_SET = 'standalone'

    /** The configuration a project declares its tool-only dependencies and the {@code -cli} companions of plugins in. */
    public static final String GRAILS_CLI_CONFIGURATION = 'grailsCli'

    /** The resolvable configuration the build runs tools with. */
    public static final String GRAILS_CLI_CLASSPATH_CONFIGURATION = 'grailsCliClasspath'

    /**
     * The configuration a project declares the {@code -cli-standalone} companions of plugins in, and
     * the libraries only the tools that run as a process of their own need.
     */
    public static final String GRAILS_CLI_STANDALONE_CONFIGURATION = 'grailsCliStandalone'

    /** The resolvable configuration a tool that runs as a process of its own is started with. */
    public static final String GRAILS_CLI_STANDALONE_CLASSPATH_CONFIGURATION = 'grailsCliStandaloneClasspath'

    /** What the artifact id of the companion with the tools the build runs ends with. */
    public static final String CLI_ARTIFACT_SUFFIX = '-cli'

    /** What the artifact id of the companion with the tools that run as a process of their own ends with. */
    public static final String CLI_STANDALONE_ARTIFACT_SUFFIX = '-cli-standalone'

    /** The attribute of the manifest of the jar of a plugin that names its {@code -cli} companion, as {@code group:artifact}. */
    public static final String CLI_ARTIFACT_MANIFEST_ATTRIBUTE = 'Grails-Cli-Artifact'

    /** The attribute of the manifest of the jar of a plugin that names its {@code -cli-standalone} companion, as {@code group:artifact}. */
    public static final String CLI_STANDALONE_ARTIFACT_MANIFEST_ATTRIBUTE = 'Grails-Cli-Standalone-Artifact'

    /** The extra property of a project that has a {@code -cli} companion: the artifact id of the companion. */
    public static final String CLI_ARTIFACT_ID_PROPERTY = 'cliArtifactId'

    /** The extra property of a project that has a {@code -cli-standalone} companion: the artifact id of the companion. */
    public static final String CLI_STANDALONE_ARTIFACT_ID_PROPERTY = 'cliStandaloneArtifactId'

    /** The task that writes the catalog of the tools of a project ({@link ToolCatalogBuilder#LOCATION}). */
    public static final String TOOL_CATALOG_TASK = 'grailsToolCatalog'

    /** The task that writes down the class path of the tools that run as a process of their own ({@link StandaloneToolsDocument#LOCATION}). */
    public static final String STANDALONE_TOOLS_TASK = 'grailsStandaloneTools'

    /** The task that runs a tool of a project by its name. */
    public static final String RUN_TOOL_TASK = 'runTool'

    /** The deprecated name of {@link #RUN_TOOL_TASK}. */
    public static final String RUN_COMMAND_TASK = 'runCommand'

    private ToolBuildNames() {
    }
}
