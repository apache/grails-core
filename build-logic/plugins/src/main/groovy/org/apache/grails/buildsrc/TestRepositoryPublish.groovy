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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.invocation.Gradle
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven

/**
 * The publish into the repository the tests resolve Grails from ({@code build/local-maven}, the repository
 * {@code TestCaseMavenRepo} of every published project), when it is all a build was asked to publish.
 *
 * <p>The builds that read that repository (the applications the Forge specifications generate, the
 * end-to-end projects) resolve jars, POMs and Gradle module metadata from it and nothing else. A
 * publication of the build has more: a sources jar and a javadoc jar, which is the Groovydoc of the
 * module, and every publish task is followed by the checksums of the release, which depend on every jar
 * task of the project and so on its compiled tests. Building those for some 140 modules is most of the
 * time the publish takes, and none of it is read.</p>
 *
 * <p>So when the tasks a build was asked for publish to that repository only, the publications are
 * without the sources and javadoc jars and their variants, and the publish tasks are not followed by the
 * checksums. Whatever else is asked for, a release or snapshot publish among it, leaves every publication
 * as it is. A publish task for another repository that runs after all while the publications are without
 * those jars fails and says so, so that nothing incomplete is published anywhere else.</p>
 */
@CompileStatic
class TestRepositoryPublish {

    /** The name of the repository the tests resolve from, as the Grails publish plugin registers it. */
    static final String REPOSITORY = 'TestCaseMavenRepo'

    /** Set to {@code true} (as a Gradle property) to publish the sources and javadoc jars to the repository of the tests too. */
    static final String COMPLETE_PROPERTY = 'completeTestRepository'

    private static final String ROOT = ':'
    private static final String TASK_SUFFIX = "To${REPOSITORY}Repository"
    private static final List<String> DOCUMENTATION_CLASSIFIERS = ['sources', 'javadoc']
    private static final List<String> DOCUMENTATION_VARIANTS = ['sourcesElements', 'javadocElements']

    private TestRepositoryPublish() {
    }

    /**
     * Whether the tasks that were asked for publish to the repository of the tests and to no other.
     *
     * <p>A build that is the one Gradle was started in was asked for the tasks of its command line: every
     * one of them has to be a publish to the repository of the tests. A build that is included in another
     * runs the tasks that build depends on, which are the jars it compiles against and the publish to the
     * repository of the tests; any other task of an included build is run only when the command line names
     * it by its path, so the publications are left as they are when the command line names a task of an
     * included build that is no publish to the repository of the tests.</p>
     *
     * @param project a project of the build
     * @return whether the publications of the build can be without what only a release needs
     */
    static boolean isAllThatIsAsked(Project project) {
        if (GradleUtils.booleanProvider(project, COMPLETE_PROPERTY).get()) {
            return false
        }
        Gradle started = project.gradle
        while (started.parent != null) {
            started = started.parent
        }
        return isAllThatIsAsked(started.startParameter.taskNames, buildPath(project))
    }

    /**
     * @param requested the task names of the command line
     * @param buildPath the path of the build in the build tree: {@code :} for the build Gradle was started
     * in, and {@code :name} for a build that is included in it
     */
    static boolean isAllThatIsAsked(List<String> requested, String buildPath) {
        if (buildPath != ROOT) {
            return !requested.any { String name -> name.startsWith(buildPath + ROOT) && !isPublishForTests(name) }
        }
        return !requested.isEmpty() && requested.every { String name -> isPublishForTests(name) }
    }

    // the path of the build of a project: what the path of the project in the build tree has in front of its path in the build
    private static String buildPath(Project project) {
        String inTree = project.buildTreePath
        if (project.path == ROOT) {
            return inTree
        }
        String path = inTree.substring(0, inTree.length() - project.path.length())
        return path.isEmpty() ? ROOT : path
    }

    /**
     * @param taskName the name or the path of a task
     * @return whether it is a task that publishes to the repository of the tests
     */
    static boolean isPublishForTests(String taskName) {
        String name = taskName.substring(taskName.lastIndexOf(':') + 1)
        return name.startsWith('publish') && name.endsWith(TASK_SUFFIX)
    }

    /**
     * Leaves the sources and javadoc jars out of the publications of a project, and has every publish task
     * for another repository fail.
     */
    static void publishWhatIsResolved(Project project) {
        // once every project is evaluated: the Grails publish plugin has put the publications together by then,
        // and no task depends on what they hold yet
        project.gradle.projectsEvaluated {
            SoftwareComponent java = project.components.findByName('java')
            if (java instanceof AdhocComponentWithVariants) {
                for (String variant : DOCUMENTATION_VARIANTS) {
                    Configuration configuration = project.configurations.findByName(variant)
                    if (configuration != null) {
                        ((AdhocComponentWithVariants) java).withVariantsFromConfiguration(configuration) { ConfigurationVariantDetails details ->
                            details.skip()
                        }
                    }
                }
            }
            // the publications that are not made from the java component alone are given these jars one by one
            project.extensions.getByType(PublishingExtension).publications.withType(MavenPublication).each { MavenPublication publication ->
                Collection<MavenArtifact> documentation = publication.artifacts.findAll { MavenArtifact artifact ->
                    artifact.classifier in DOCUMENTATION_CLASSIFIERS
                }
                publication.artifacts.removeAll(documentation)
            }
        }
        project.tasks.withType(AbstractPublishToMaven).configureEach { AbstractPublishToMaven task ->
            if (!isPublishForTests(task.name)) {
                task.doFirst { Task publish ->
                    throw new GradleException("${publish.path} cannot run in this build: the tasks that were asked for publish to the repository of the tests " +
                            "(${REPOSITORY}) only, so the publications are without their sources and javadoc jars. Name the task on the command line, " +
                            "or pass -P${COMPLETE_PROPERTY}=true, to publish everything.")
                }
            }
        }
    }
}
