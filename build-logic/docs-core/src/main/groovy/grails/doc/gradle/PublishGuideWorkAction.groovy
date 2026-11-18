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
package grails.doc.gradle

import org.gradle.workers.WorkAction

import grails.doc.UserGuideBuilder

/**
 * Renders the HTML user guide in the worker process {@link PublishGuideTask} forks.
 *
 * <p>Rendering starts a JRuby runtime for AsciidoctorJ, which settles under a gigabyte
 * resident for the English guide. That belongs in a worker process rather than in the Gradle
 * daemon, which by this point in the build is already holding everything the rest of the
 * build produced. The worker is stopped when the build session ends, not when the task
 * finishes - see {@link PublishGuideTask}.</p>
 */
abstract class PublishGuideWorkAction implements WorkAction<PublishGuideWorkParameters> {

    @Override
    void execute() {
        PublishGuideWorkParameters params = parameters

        new UserGuideBuilder(
                sourceDir: params.sourceDir.get().asFile,
                resourcesDir: params.resourcesDir.get().asFile,
                targetDir: params.targetDir.get().asFile,
                asciidoc: params.asciidoc.get(),
                language: params.language.getOrElse(''),
                sourceRepo: params.sourceRepo.getOrElse(''),
                propertiesFiles: params.propertiesFiles.files.toList(),
                properties: params.properties.get(),
                propertiesWithFilePaths: params.propertiesWithFilePaths.get(),
                macroClassNames: params.macroClassNames.get(),
                verboseAnt: params.verboseAnt.getOrElse(false)
        ).build()
    }
}
