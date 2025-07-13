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

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime

@CompileStatic
@CacheableTask
abstract class FetchTagsTask extends Exec {

    @Input
    final Property<LocalDateTime> cacheDate // allows for forcing refreshing the tags, defaults to once a day

    @Input
    final Property<String> defaultTag // if no git repo present

    @OutputFile
    final RegularFileProperty tagsFile

    @Inject
    FetchTagsTask(ObjectFactory objectFactory, Project project) {
        group = 'documentation'
        cacheDate = objectFactory.property(LocalDateTime).convention(LocalDate.now().atStartOfDay())
        tagsFile = objectFactory.fileProperty().convention(project.layout.buildDirectory.file('git-tags.txt'))
        defaultTag = objectFactory.property(String).convention(project.provider { "v${project.version as String}" as String })

        commandLine("git", "tag", "-l", "--sort=-creatordate")
        ignoreExitValue = !project.rootProject.layout.projectDirectory.dir('.git').asFile.exists()

        def output = new ByteArrayOutputStream()
        standardOutput = output

        doLast {
            File file = tagsFile.get().asFile
            if (!file.parentFile.exists()) {
                file.mkdirs()
            }

            if(ignoreExitValue) {
                logger.lifecycle("not a git repo, so assuming a default tag of {}", defaultTag.get())
            }

            file.text = ignoreExitValue ? defaultTag.get() : new String(output.toByteArray(), StandardCharsets.UTF_8).trim()
        }
    }
}
