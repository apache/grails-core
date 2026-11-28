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

import java.nio.file.Files

import groovy.transform.CompileStatic

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * Has the task {@code publishAllPublicationsToTestCaseMavenRepoRepository} of a root project end by writing
 * the record of what it published into the repository of the tests: see {@link TestRepositoryMarker}.
 *
 * <p>It is applied to the root project of the framework build and of the build of the Grails Gradle
 * plugins, the two builds that fill that repository. The record is written by the task itself, as its last
 * action, so it is written only when every publish the task depends on has succeeded, and also when the
 * task runs as a task of an included build, as it does for the specifications of Forge.</p>
 */
@CompileStatic
class TestRepositoryMarkerPlugin implements Plugin<Project> {

    static final String TASK = "publishAllPublicationsTo${TestRepositoryPublish.REPOSITORY}Repository"

    @Override
    void apply(Project project) {
        if (project != project.rootProject) {
            throw new GradleException("The record of the repository of the tests is written by the root project, not by ${project.path}")
        }
        File sources = GradleUtils.findRootGrailsCoreDir(project).asFile
        String build = GradleUtils.isRootGrailsCoreDir(project) ? TestRepositoryMarker.FRAMEWORK : TestRepositoryMarker.GRADLE_PLUGINS
        // read when the task graph is stored: the scripts that set the version have run by then
        Provider<Map<String, String>> published = project.provider { TestRepositoryMarkerPlugin.published(project) }
        boolean framework = build == TestRepositoryMarker.FRAMEWORK
        File repository = new File(sources, 'build/local-maven')
        project.allprojects { Project module ->
            module.tasks.withType(PublishToMavenRepository).configureEach { PublishToMavenRepository publish ->
                if (publish.name.endsWith("To${TestRepositoryPublish.REPOSITORY}Repository")) {
                    // A partial or failed republish, including one of the other call-site variant, must
                    // never keep the previous complete-publish record valid.
                    publish.doFirst(new InvalidateMarker(TestRepositoryMarker.file(repository, build)))
                }
            }
        }
        boolean installsWrapper = framework && project.findProject(TestRepositoryMarker.WRAPPER_INSTALL_TASK.substring(0, TestRepositoryMarker.WRAPPER_INSTALL_TASK.lastIndexOf(':'))) != null
        project.tasks.named { String name -> name == TASK }.configureEach { Task task ->
            if (installsWrapper) {
                // the wrapper is copied into the repository with the record: see TestRepositoryMarker.FRAMEWORK_OUTPUTS
                task.dependsOn(TestRepositoryMarker.WRAPPER_INSTALL_TASK)
            }
            task.doLast(new WriteMarker(repository, sources, build, published))
        }
    }

    private static Map<String, String> published(Project project) {
        Boolean indy = GradleUtils.lookupPropertyByType(project, 'grailsIndy', Boolean)
        boolean slim = TestRepositoryPublish.isAllThatIsAsked(project)
        boolean withoutSbom = slim && GradleUtils.booleanProvider(project, TestRepositoryPublish.WITHOUT_SBOM_PROPERTY).get()
        Map<String, String> values = [:]
        values.put(TestRepositoryMarker.VERSION, String.valueOf(project.findProperty('projectVersion')))
        values.put(TestRepositoryMarker.CALL_SITES, indy == null || indy ? TestRepositoryMarker.INDY : TestRepositoryMarker.CLASSIC)
        values.put(TestRepositoryMarker.PUBLICATION, slim ? 'slim' : 'complete')
        values.put(TestRepositoryMarker.SBOM, String.valueOf(!withoutSbom))
        values.put('format', '2')
        values.put('javaVersion', String.valueOf(project.findProperty('javaVersion') ?: '21'))
        return values
    }

    @CompileStatic
    static class InvalidateMarker implements Action<Task> {

        private final File marker

        InvalidateMarker(File marker) {
            this.marker = marker
        }

        @Override
        void execute(Task task) {
            // Every publish task of the build runs this, and with --parallel several at once: a record another
            // one has just deleted is no failure, only a record that is still there after this is.
            try {
                Files.deleteIfExists(marker.toPath())
            } catch (IOException e) {
                throw new GradleException("Cannot invalidate test repository record ${marker}", e)
            }
        }
    }

    /** The last action of the publish task. It holds values only, so the configuration cache can store it. */
    @CompileStatic
    static class WriteMarker implements Action<Task> {

        private final File repository
        private final File sources
        private final String build
        private final Provider<Map<String, String>> published

        WriteMarker(File repository, File sources, String build, Provider<Map<String, String>> published) {
            this.repository = repository
            this.sources = sources
            this.build = build
            this.published = published
        }

        @Override
        void execute(Task task) {
            if (!repository.isDirectory()) {
                // nothing was published: a build without a published module
                return
            }
            if (build == TestRepositoryMarker.FRAMEWORK) {
                TestRepositoryMarker.copyFrameworkOutputs(sources, repository)
            }
            FrameworkSources state = FrameworkSources.of(sources)
            TestRepositoryMarker.write(repository, build, published.get(), state)
            task.logger.info('Recorded the publish of {} in {} (sources {})', build, TestRepositoryMarker.file(repository, build),
                    state == null ? 'unknown to Git' : state.fingerprint)
        }
    }
}
