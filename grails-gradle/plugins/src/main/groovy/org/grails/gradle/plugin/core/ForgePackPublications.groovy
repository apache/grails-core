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
package org.grails.gradle.plugin.core

import groovy.transform.CompileStatic

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * The Maven publications of a project as the files of a Maven repository: what {@code forgePackTest}
 * puts into a repository of its own, which the applications it generates resolve from before any
 * other, so that they get what this build has just built and not what a repository had of the same
 * version before.
 *
 * <p>A publication is laid out as {@code maven-publish} lays it out in a repository, by its
 * coordinates: its artifacts, its POM, and its Gradle module metadata where the build writes that. A
 * snapshot version is laid out without a time stamp, as the local Maven repository has one, and Gradle
 * resolves it so. Where two publications have a file of the same path, the one of the publication that
 * comes first by name is taken.</p>
 *
 * <p>The publications are read when the files are asked for, which is after every build script has
 * declared and configured its own.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ForgePackPublications {

    private ForgePackPublications() {
    }

    /**
     * The files of the Maven publications of a project, by where they go in a repository.
     *
     * @param publishing the publications of the project
     * @param tasks the tasks of the project, which write the POM and the module metadata
     * @return the absolute path of each file by its path in the repository, the paths in order
     */
    static Map<String, String> layout(PublishingExtension publishing, TaskContainer tasks) {
        Map<String, String> files = new TreeMap<String, String>()
        for (MavenPublication publication : mavenPublications(publishing)) {
            String directory = "${publication.groupId.replace('.', '/')}/${publication.artifactId}/${publication.version}"
            String base = "${publication.artifactId}-${publication.version}"
            for (MavenArtifact artifact : publication.artifacts) {
                String classifier = artifact.classifier ? "-${artifact.classifier}" : ''
                String extension = artifact.extension ? ".${artifact.extension}" : ''
                files.putIfAbsent("${directory}/${base}${classifier}${extension}".toString(), artifact.file.absolutePath)
            }
            files.putIfAbsent("${directory}/${base}.pom".toString(), pomTask(publication, tasks).get().destination.absolutePath)
            TaskProvider<GenerateModuleMetadata> module = moduleTask(publication, tasks)
            if (module != null) {
                files.putIfAbsent("${directory}/${base}.module".toString(), module.get().outputFile.get().asFile.absolutePath)
            }
        }
        return files
    }

    /**
     * What builds the files of the Maven publications of a project: their artifacts, and the tasks that
     * write their POMs and module metadata.
     *
     * @param publishing the publications of the project
     * @param tasks the tasks of the project
     * @return what the files are built by, for {@code dependsOn} or {@code builtBy}
     */
    static List<Object> builtBy(PublishingExtension publishing, TaskContainer tasks) {
        List<Object> builtBy = []
        for (MavenPublication publication : mavenPublications(publishing)) {
            builtBy.addAll(publication.artifacts)
            builtBy.add(pomTask(publication, tasks))
            TaskProvider<GenerateModuleMetadata> module = moduleTask(publication, tasks)
            if (module != null) {
                builtBy.add(module)
            }
        }
        return builtBy
    }

    private static List<MavenPublication> mavenPublications(PublishingExtension publishing) {
        return publishing.publications.withType(MavenPublication).sort(false) { MavenPublication publication -> publication.name }
    }

    // the names maven-publish gives the tasks of a publication
    private static TaskProvider<GenerateMavenPom> pomTask(MavenPublication publication, TaskContainer tasks) {
        return tasks.named("generatePomFileFor${publication.name.capitalize()}Publication".toString(), GenerateMavenPom)
    }

    private static TaskProvider<GenerateModuleMetadata> moduleTask(MavenPublication publication, TaskContainer tasks) {
        String name = "generateMetadataFileFor${publication.name.capitalize()}Publication"
        return tasks.names.contains(name) ? tasks.named(name, GenerateModuleMetadata) : null
    }
}
