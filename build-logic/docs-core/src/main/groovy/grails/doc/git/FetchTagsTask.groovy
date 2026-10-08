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

package grails.doc.git

import java.nio.charset.StandardCharsets

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec

/**
 * Writes the tags of the repository, newest first, to {@link #getTagsFile()}; outside a git
 * repository it writes {@link #getDefaultTag()} instead.
 *
 * <p>The tags change without the build changing, so the task lists them every time it runs, which
 * {@code git tag -l} does quickly.</p>
 */
@CompileStatic
@UntrackedTask(because = 'The tags of the repository change without the build changing')
abstract class FetchTagsTask extends DefaultTask {

    @Input
    final Property<String> defaultTag // if no git repo present

    @Input
    final Property<Boolean> gitRepository

    @Internal
    final DirectoryProperty repositoryDir

    @OutputFile
    final RegularFileProperty tagsFile

    @Inject
    abstract ExecOperations getExecOperations()

    @Inject
    FetchTagsTask(ObjectFactory objectFactory, Project project) {
        group = 'documentation'
        tagsFile = objectFactory.fileProperty().convention(project.layout.buildDirectory.file('git-tags.txt'))
        defaultTag = objectFactory.property(String).convention(project.provider { "v${project.version as String}" as String })
        repositoryDir = objectFactory.directoryProperty().convention(project.rootProject.layout.projectDirectory)
        gitRepository = objectFactory.property(Boolean).convention(repositoryDir.map { it.file('.git').asFile.exists() })
    }

    @TaskAction
    void fetchTags() {
        File file = tagsFile.get().asFile
        file.parentFile.mkdirs()

        if (!gitRepository.get()) {
            logger.lifecycle('not a git repo, so assuming a default tag of {}', defaultTag.get())
            file.text = defaultTag.get()
            return
        }

        def output = new ByteArrayOutputStream()
        execOperations.exec { ExecSpec spec ->
            spec.commandLine('git', 'tag', '-l', '--sort=-creatordate')
            spec.workingDir(repositoryDir.get().asFile)
            spec.standardOutput = output
        }
        file.text = new String(output.toByteArray(), StandardCharsets.UTF_8).trim()
    }
}
