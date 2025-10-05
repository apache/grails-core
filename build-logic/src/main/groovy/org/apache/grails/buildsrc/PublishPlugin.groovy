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
import org.apache.grails.gradle.publish.GrailsPublishExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
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

    private Boolean skipJavaComponent
    private Directory grailsCoreRoot
    private boolean initialized

    @Override
    void apply(Project project) {
        ['java', 'java-library', 'java-platform', 'org.apache.grails.gradle.grails-profile'].each {
            project.plugins.withId(it) {
                publish(project)
            }
        }
    }

    private void publish(Project project) {
        if(initialized) {
            return
        }

        project.logger.info('Applying plugin to project: {}', project.name)
        skipJavaComponent = lookupProperty(project, 'skipJavaComponent', false)
        grailsCoreRoot = findRootGrailsCoreDir(project)

        configureGrailsPublish(project)

        if (skipJavaComponent) {
            // since the publish plugin won't register and this task needs to exist
            project.tasks.register('publishAllPublicationsToTestCaseMavenRepoRepository')
        }

        ensureJarContainsASFFiles(project)

        disableSigningWhenTesting(project)

        project.plugins.withId('maven-publish') {
            def artifactsTask = configurePublishedArtifacts(project)

            configureChecksums(project, artifactsTask)
        }
    }

    private TaskProvider<Task> configurePublishedArtifacts(Project project) {
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
                            artifacts[artifact.file.name] = "$publication.groupId:$publication.artifactId:$publication.version:$artifact.classifier" as String
                        } else {
                            artifacts[artifact.file.name] = "$publication.groupId:$publication.artifactId:$publication.version" as String
                        }
                    }
                }
                File artifactsFile = artifactsDir.get().asFile
                artifactsFile.mkdirs()
                artifacts.each { key, value ->
                    new File(artifactsFile, "${key}.txt").text = value
                }
            }
        }
        artifactsTask
    }

    private void configureChecksums(Project project, TaskProvider<Task> artifactsTask) {
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

        initialized = true
    }

    private void disableSigningWhenTesting(Project project) {
        project.pluginManager.withPlugin('signing') {
            if (System.getenv('TEST_BUILD_REPRODUCIBLE')) {
                project.logger.lifecycle('Signing is disabled for this build to test build reproducibility.')
                project.tasks.withType(Sign).configureEach {
                    it.enabled = false
                }
            }
        }
    }

    private void configureGrailsPublish(Project project) {
        project.extensions.configure(GrailsPublishExtension) {
            // Explicit `it` is required here
            it.artifactId.set(lookupProperty(project, 'pomArtifactId', project.name))
            it.githubSlug.set(lookupProperty(project, 'githubSlug', 'apache/grails-core'))
            it.license.name = 'Apache-2.0'
            it.title.set(lookupProperty(project, 'pomTitle', project.rootProject.name == 'grails-forge' ? 'Apache Grails® Application Forge' : 'Grails® framework'))
            it.desc.set(lookupProperty(project, 'pomDescription', project.rootProject.name == 'grails-forge' ? 'Generates Apache Grails applications' : 'Grails Web Application Framework'))
            it.developers.set(lookupProperty(project, 'pomDevelopers', determineDevelopers(project)))
            it.pomCustomization = lookupProperty(project, 'pomCustomization') as Closure
            it.publishTestSources.set(lookupProperty(project, 'pomPublishTestSources', false))
            it.testRepositoryPath.set(skipJavaComponent ? null : grailsCoreRoot.dir('build/local-maven'))
            it.publicationName.set(lookupProperty(project, 'pomMavenPublicationName', 'maven'))
            it.addComponents.set(!skipJavaComponent && !project.pluginManager.hasPlugin('java-gradle-plugin'))
        }
    }


    private void ensureJarContainsASFFiles(Project project) {
        if (skipJavaComponent) {
            // no jar to configure, do not accidentally create one
            return
        }

        project.tasks.withType(Jar).configureEach { Jar jar ->
            if (jar.archiveClassifier.getOrNull() == 'javadoc') {
                // only the source jar & the binary jar have the license files
                return
            }

            def licenseInProject = project.layout.projectDirectory.file('src/main/resources/META-INF/LICENSE')
            def needsLicense = project.providers.provider { !licenseInProject.asFile.exists() }
            def fallbackLicense = grailsCoreRoot.file('licenses/LICENSE-Apache-2.0.txt')
            jar.from(fallbackLicense) { CopySpec spec ->
                spec.into 'META-INF'
                spec.rename { 'LICENSE' }
                spec.include { needsLicense.get() }
            }

            def noticeInProject  = project.layout.projectDirectory.file('src/main/resources/META-INF/NOTICE')
            def needsNotice  = project.providers.provider { !noticeInProject.asFile.exists() }
            def fallbackNotice  = grailsCoreRoot.file('grails-core/src/main/resources/META-INF/NOTICE')
            jar.from(fallbackNotice) { CopySpec spec ->
                spec.into 'META-INF'
                spec.include { needsNotice.get() }
            }

            jar.doFirst {
                if (needsLicense.get()) {
                    jar.logger.info('Did not find license file for project {} for jar file {}, using fallback license', project.name, jar.archiveFileName.getOrNull())
                }
                if (needsNotice.get()) {
                    jar.logger.info('Did not find notice file for project {} for jar file {}, using notice license', project.name, jar.archiveFileName.getOrNull())
                }
            }
        }
    }

    private Map determineDevelopers(Project project) {
        //TODO: This needs changed so any past contributor is listed as a developer per ASF policy
        if (project.name == 'grails-gradle') {
            return [graemerocher: 'Graeme Rocher', jeffscottbrown: 'Jeff Scott Brown', puneetbehl: 'Puneet Behl']
        } else if (project.name == 'grails-forge') {
            return [puneetbehl: 'Puneet Behl']
        }

        [graemerocher: 'Graeme Rocher']
    }
}
