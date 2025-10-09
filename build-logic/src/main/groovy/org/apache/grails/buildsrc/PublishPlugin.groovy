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

import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.CompileStatic
import org.apache.grails.gradle.publish.GrailsPublishExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.crypto.checksum.Checksum
import org.gradle.crypto.checksum.ChecksumPlugin
import org.gradle.plugins.signing.Sign

import static org.apache.grails.buildsrc.GradleUtils.lookupProperty
import static org.apache.grails.buildsrc.GradleUtils.findRootGrailsCoreDir

/**
 * Handles generating the checksum file, published artifact list, and grails-publish configuration
 */
@CompileStatic
class PublishPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def initialized = new AtomicBoolean(false)
        ['java', 'java-library', 'java-platform', 'org.apache.grails.gradle.grails-profile'].each {
            project.plugins.withId(it) {
                if (initialized.compareAndSet(false, true)) {
                    publish(project)
                }
            }
        }
    }

    private static boolean shouldSkipJavaComponent(Project project) {
        lookupProperty(project, 'skipJavaComponent', false)
    }

    private static void publish(Project project) {
        project.logger.info('Configuring the Grails Publish Plugin for [{}].', project.name)

        configureGrailsPublish(project)

        project.afterEvaluate {
            if (shouldSkipJavaComponent(project)) {
                // since the grails-publish plugin won't register, add this task to ensure it exists due to the forge dependency
                project.tasks.register('publishAllPublicationsToTestCaseMavenRepoRepository')
            }
        }

        ensureJarContainsASFFiles(project)
        disableSigningWhenTesting(project)

        project.plugins.withId('maven-publish') {
            def artifactsTask = configurePublishedArtifacts(project)

            configureChecksums(project, artifactsTask)
        }
    }

    private static TaskProvider<Task> configurePublishedArtifacts(Project project) {
        def artifactsDir = project.layout.buildDirectory.dir('artifacts')
        def artifactsTask = project.tasks.register('savePublishedArtifacts')
        artifactsTask.configure { Task task ->
            task.group = 'publishing'
            task.outputs.dir(artifactsDir)
            task.dependsOn(project.tasks.withType(Jar))
            task.doLast {
                Map<String, String> artifacts = [:]
                project.extensions.getByType(PublishingExtension).publications.withType(MavenPublication).each { MavenPublication publication ->
                    publication.artifacts.each { MavenArtifact artifact ->
                        if (!artifact.file.exists() || artifact.file.name in ['grails-plugin.xml', 'profile.yml']) {
                            return
                        }
                        if (artifact.classifier) {
                            artifacts.put(
                                    artifact.file.name,
                                    "$publication.groupId:$publication.artifactId:$publication.version:$artifact.classifier" as String
                            )
                        } else {
                            artifacts.put(
                                    artifact.file.name,
                                    "$publication.groupId:$publication.artifactId:$publication.version" as String
                            )
                        }
                    }
                }

                artifactsDir.get().asFile.with { dir ->
                    dir.mkdirs()
                    artifacts.each { key, value ->
                        new File(dir, "${key}.txt").text = value
                    }
                }
            }
        }
        artifactsTask
    }

    private static void configureChecksums(Project project, TaskProvider<Task> artifactsTask) {
        project.pluginManager.apply(ChecksumPlugin)

        def checksumTask = project.tasks.register('publishedChecksums', Checksum)
        checksumTask.configure { Checksum check ->
            check.group = 'publishing'
            check.checksumAlgorithm.set(Checksum.Algorithm.SHA512)
            check.outputDirectory.set(project.layout.buildDirectory.dir('checksums'))
            check.dependsOn(project.tasks.withType(Jar))
            check.finalizedBy(artifactsTask)
        }

        project.gradle.taskGraph.whenReady {
            project.extensions.configure(PublishingExtension) {
                it.publications.withType(MavenPublication).configureEach {
                    project.logger.lifecycle('Maven Publication Found for project: {} with name {}', project.name, it.name)
                    List<File> filesToChecksum = []
                    it.artifacts.each {
                        if (it.file.name in ['grails-plugin.xml', 'profile.yml']) {
                            return
                        }
                        filesToChecksum << it.file
                    }

                    checksumTask.configure { Checksum check ->
                        check.inputFiles.setFrom(filesToChecksum.unique())
                    }
                }
            }
        }

        project.tasks.withType(AbstractPublishToMaven).configureEach {
            it.finalizedBy(checksumTask)
        }
    }

    private static void disableSigningWhenTesting(Project project) {
        project.pluginManager.withPlugin('signing') {
            if (System.getenv('TEST_BUILD_REPRODUCIBLE')) {
                project.logger.lifecycle('Signing is disabled for this build to test build reproducibility.')
                project.tasks.withType(Sign).configureEach {
                    it.enabled = false
                }
            }
        }
    }

    private static void configureGrailsPublish(Project project) {
        project.extensions.configure(GrailsPublishExtension) {
            // Explicit `it` is required here
            it.artifactId.set(project.provider { lookupProperty(project, 'pomArtifactId', project.name) })
            it.githubSlug.set(project.provider { lookupProperty(project, 'githubSlug', 'apache/grails-core')})
            it.license.name = 'Apache-2.0'
            it.title.set(project.provider { lookupProperty(project, 'pomTitle', project.rootProject.name == 'grails-forge' ? 'Apache Grails® Application Forge' : 'Apache Grails® framework')})
            it.desc.set(project.provider { lookupProperty(project, 'pomDescription', project.rootProject.name == 'grails-forge' ? 'Generates Apache Grails® applications' : 'Apache Grails® Web Application Framework')})
            it.developers.set(project.provider { lookupProperty(project, 'pomDevelopers', determineDevelopers(project))})
            it.pomCustomization = lookupProperty(project, 'pomCustomization') as Closure
            it.publishTestSources.set(project.provider { lookupProperty(project, 'pomPublishTestSources', false)})
            it.testRepositoryPath.set(project.provider { shouldSkipJavaComponent(project) ? null : findRootGrailsCoreDir(project).dir('build/local-maven')})
            it.publicationName.set(project.provider { lookupProperty(project, 'pomMavenPublicationName', 'maven')})
            it.addComponents.set(project.provider { !shouldSkipJavaComponent(project) && !project.pluginManager.hasPlugin('java-gradle-plugin')})
        }
    }

    private static void ensureJarContainsASFFiles(Project project) {
        project.afterEvaluate {
            if (shouldSkipJavaComponent(project)) {
                // no jar to configure, do not accidentally create one
                return
            }

            project.tasks.withType(Jar).configureEach { Jar jar ->
                if (jar.archiveClassifier.orNull == 'javadoc') {
                    // only the source jar & the binary jar have the license files
                    return
                }

                def licenseInProject = project.layout.projectDirectory.file('src/main/resources/META-INF/LICENSE')
                def needsLicense = project.providers.provider { !licenseInProject.asFile.exists() }
                def fallbackLicense = findRootGrailsCoreDir(project).file('licenses/LICENSE-Apache-2.0.txt')
                jar.from(fallbackLicense) { CopySpec spec ->
                    spec.into('META-INF')
                    spec.rename { 'LICENSE' }
                    spec.include { needsLicense.get() }
                }

                def noticeInProject = project.layout.projectDirectory.file('src/main/resources/META-INF/NOTICE')
                def needsNotice = project.providers.provider { !noticeInProject.asFile.exists() }
                def fallbackNotice = findRootGrailsCoreDir(project).file('grails-core/src/main/resources/META-INF/NOTICE')
                jar.from(fallbackNotice) { CopySpec spec ->
                    spec.into('META-INF')
                    spec.include { needsNotice.get() }
                }

                jar.doFirst {
                    if (needsLicense.get()) {
                        jar.logger.info(
                                'Project specific LICENSE file not found in {}, adding fallback license to {}.',
                                project.name,
                                jar.archiveFileName.orNull
                        )
                    }
                    if (needsNotice.get()) {
                        jar.logger.info(
                                'Project specific NOTICE file not found in [{}], adding default NOTICE to [{}].',
                                project.name,
                                jar.archiveFileName.orNull
                        )
                    }
                }
            }
        }
    }

    private static Map determineDevelopers(Project project) {
        //TODO: This needs changed so any past contributor is listed as a developer per ASF policy
        if (project.name == 'grails-gradle') {
            return [graemerocher: 'Graeme Rocher', jeffscottbrown: 'Jeff Scott Brown', puneetbehl: 'Puneet Behl']
        } else if (project.name == 'grails-forge') {
            return [puneetbehl: 'Puneet Behl']
        }

        [graemerocher: 'Graeme Rocher']
    }
}
