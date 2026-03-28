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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Base class for Gradle plugin functional tests using TestKit.
 *
 * <p>Adapted from the {@code GradleSpecification} in the
 * {@code apache/grails-gradle-publish} project. Handles temp directory
 * management, GradleRunner setup, test resource project copying, and
 * common build assertions.</p>
 *
 * @since 7.0.8
 */
abstract class GradleSpecification extends Specification {

    private static Path basePath
    private static GradleRunner gradleRunner

    /** Project version injected by Gradle test config. */
    protected static final String PROJECT_VERSION = System.getProperty('projectVersion')

    /** Current JDK major version injected by Gradle test config. */
    protected static final int CURRENT_JDK = Integer.parseInt(System.getProperty('currentJdk'))

    void setupSpec() {
        basePath = Files.createTempDirectory('gradle-projects')
        Path testKitDir = Files.createDirectories(basePath.resolve('.gradle'))
        gradleRunner = GradleRunner.create()
                .withPluginClasspath()
                .withTestKitDir(testKitDir.toFile())
    }

    void cleanup() {
        basePath?.toFile()?.listFiles()?.each {
            if (it.name == '.gradle') {
                return
            }
            it.deleteDir()
        }
    }

    void cleanupSpec() {
        basePath?.toFile()?.deleteDir()
    }

    /**
     * Sets up a test project from resource files under
     * {@code src/test/resources/test-projects/{projectName}}.
     *
     * <p>Files are copied to a temp directory. Any occurrence of
     * {@code __CURRENT_JDK__} in {@code .gradle} files is replaced
     * with the actual current JDK version, and {@code __PROJECT_VERSION__}
     * is replaced with the actual project version.</p>
     */
    protected GradleRunner setupTestResourceProject(String projectName) {
        Path destination = basePath.resolve(projectName)
        Files.createDirectories(destination)

        Path source = Path.of("src/test/resources/test-projects/${projectName}")
        copyDirectory(source, destination)

        gradleRunner.withProjectDir(destination.toFile())
    }

    /**
     * Executes a Gradle task and returns the build result.
     */
    protected BuildResult executeTask(String taskName, List<String> otherArgs = []) {
        List<String> args = [taskName, '--stacktrace']
        args.addAll(otherArgs)
        gradleRunner.withArguments(args).forwardOutput().build()
    }

    /**
     * Asserts that the given task succeeded.
     */
    protected void assertTaskSuccess(String taskName, BuildResult result) {
        def task = result.tasks.find { it.path.endsWith(":${taskName}") }
        assert task != null : "Task '${taskName}' not found in build result"
        assert task.outcome == TaskOutcome.SUCCESS : "Task '${taskName}' outcome was ${task.outcome}"
    }

    private void copyDirectory(Path source, Path destination) {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Files.createDirectories(destination.resolve(source.relativize(dir)))
                FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path target = destination.resolve(source.relativize(file))
                if (file.toString().endsWith('.gradle') || file.toString().endsWith('.properties')) {
                    String content = Files.readString(file)
                            .replace('__CURRENT_JDK__', String.valueOf(CURRENT_JDK))
                            .replace('__PROJECT_VERSION__', PROJECT_VERSION)
                    Files.writeString(target, content)
                } else {
                    Files.copy(file, target)
                }
                FileVisitResult.CONTINUE
            }
        })
    }
}
