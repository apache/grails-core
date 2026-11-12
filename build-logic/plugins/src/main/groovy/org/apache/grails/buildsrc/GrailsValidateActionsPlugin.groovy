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

package org.apache.grails.buildsrc

import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree

/**
 * Registers {@code validateActions}, which checks the repository's GitHub Actions workflow files
 * against the ASF approved-actions policy. Intended for the root project, whose
 * {@code .github/workflows} directory is the one scanned.
 *
 * <p>Like {@code validateDependencyVersions}, the task is invoked explicitly by CI rather than
 * hung off {@code check} - it reaches the network, so it has no place in a local build.</p>
 *
 * @see ValidateActionsTask
 * @since 8.0
 */
@CompileStatic
class GrailsValidateActionsPlugin implements Plugin<Project> {

    static final String VALIDATE_ACTIONS_TASK_NAME = 'validateActions'

    private static final String WORKFLOWS_DIR = '.github/workflows'

    @Override
    void apply(Project project) {
        ConfigurableFileTree workflows = project.fileTree(project.layout.projectDirectory.dir(WORKFLOWS_DIR))
        workflows.include('*.yml', '*.yaml')

        project.tasks.register(VALIDATE_ACTIONS_TASK_NAME, ValidateActionsTask) { ValidateActionsTask task ->
            task.group = 'verification'
            task.description = 'Validates that every uses: entry in the GitHub workflow files is on the ASF approved list.'
            task.rootDirectory.set(project.layout.projectDirectory)
            task.workflowFiles.from(workflows)
            // The approved list is fetched from ASF infrastructure on every run, so unchanged
            // workflow files do not imply an unchanged verdict. The scheduled run exists precisely
            // to catch a pinned SHA being delisted upstream, which up-to-date checks would hide.
            task.outputs.upToDateWhen { false }
        }
    }
}
