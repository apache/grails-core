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

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.testing.Test

/**
 * Deterministically partitions root-build {@link Test} tasks across CI shards.
 */
@CompileStatic
class TestTaskShardingPlugin implements Plugin<Project> {

    static final String SHARD_COUNT_PROPERTY = 'testShardCount'
    static final String SHARD_INDEX_PROPERTY = 'testShardIndex'
    static final String SHARD_TASK_NAME = 'testShard'
    private static final String MANIFEST_PREFIX = 'TEST_SHARD_MANIFEST'

    @Override
    void apply(Project project) {
        if (project != project.rootProject) {
            throw new GradleException('TestTaskShardingPlugin must be applied to the root project only.')
        }

        ShardConfiguration configuration = readConfiguration(project)
        if (configuration == null) {
            return
        }

        Set<Test> candidateTasks = new LinkedHashSet<>()
        project.allprojects { Project candidateProject ->
            candidateProject.tasks.withType(Test).configureEach { Test task ->
                candidateTasks.add(task)
                task.onlyIf {
                    isSelectedForShard(task, configuration)
                }
            }
        }
        registerShardTask(project, candidateTasks, configuration)
        project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
            emitManifest(project, candidateTasks, configuration)
        }
    }

    private static ShardConfiguration readConfiguration(Project project) {
        boolean hasCount = project.hasProperty(SHARD_COUNT_PROPERTY)
        boolean hasIndex = project.hasProperty(SHARD_INDEX_PROPERTY)
        if (!hasCount && !hasIndex) {
            return null
        }
        if (hasCount != hasIndex) {
            throw new GradleException("${SHARD_COUNT_PROPERTY} and ${SHARD_INDEX_PROPERTY} must be supplied together")
        }

        int shardCount = parseInteger(project, SHARD_COUNT_PROPERTY)
        int shardIndex = parseInteger(project, SHARD_INDEX_PROPERTY)
        if (shardCount < 1) {
            throw new GradleException("${SHARD_COUNT_PROPERTY} must be at least 1")
        }
        if (shardIndex < 0 || shardIndex >= shardCount) {
            throw new GradleException("${SHARD_INDEX_PROPERTY} must be in the range [0, ${shardCount})")
        }
        new ShardConfiguration(shardCount, shardIndex)
    }

    private static int parseInteger(Project project, String propertyName) {
        String value = project.findProperty(propertyName)?.toString()
        try {
            Integer.parseInt(value)
        } catch (NumberFormatException ignored) {
            throw new GradleException("${propertyName} must be an integer")
        }
    }

    private static void registerShardTask(Project rootProject, Set<Test> candidateTasks, ShardConfiguration configuration) {
        rootProject.tasks.register(SHARD_TASK_NAME) { Task task ->
            task.group = 'verification'
            task.description = 'Runs the Test tasks assigned to the current deterministic shard.'
            task.dependsOn {
                collectCandidateTasks(rootProject, candidateTasks).findAll { Test testTask ->
                    isSelectedForShard(testTask, configuration)
                }
            }
        }
    }

    private static void emitManifest(Project rootProject, Set<Test> candidateTasks, ShardConfiguration configuration) {
        List<Test> candidates = collectCandidateTasks(rootProject, candidateTasks)
        List<String> candidatePaths = candidates.collect { Test task -> normalizeTaskPath(task.path) }
        validateUniqueTaskPaths(candidatePaths)
        if (candidates.empty) {
            throw new GradleException('No Test tasks were found for sharding')
        }

        List<String> selectedPaths = candidates.findAll { Test task ->
            isSelectedForShard(task, configuration)
        }.collect { Test task -> normalizeTaskPath(task.path) }.sort()
        rootProject.logger.lifecycle("${MANIFEST_PREFIX} totalCandidates=${candidatePaths.size()} shardIndex=${configuration.shardIndex} shardCount=${configuration.shardCount} selectedTasks=${selectedPaths.join(',')}")
    }

    private static List<Test> collectCandidateTasks(Project rootProject, Set<Test> candidateTasks) {
        rootProject.allprojects.each { Project project ->
            project.tasks.withType(Test).each { Test task ->
                candidateTasks.add(task)
            }
        }
        candidateTasks.toList().sort { Test left, Test right ->
            normalizeTaskPath(left.path) <=> normalizeTaskPath(right.path)
        }
    }

    private static boolean isSelectedForShard(Test task, ShardConfiguration configuration) {
        if (task.project.path == ':grails-test-report') {
            return configuration.shardIndex == 0
        }
        shardFor(normalizeTaskPath(task.path), configuration.shardCount) == configuration.shardIndex
    }

    static void validateUniqueTaskPaths(Collection<String> taskPaths) {
        Set<String> seen = new LinkedHashSet<>()
        taskPaths.each { String taskPath ->
            String normalizedPath = normalizeTaskPath(taskPath)
            if (!seen.add(normalizedPath)) {
                throw new IllegalArgumentException("Duplicate normalized Gradle Test task path: ${normalizedPath}")
            }
        }
    }

    private static String normalizeTaskPath(String taskPath) {
        String normalizedPath = taskPath.replace('\\', '/')
        normalizedPath.startsWith(':') ? normalizedPath : ":${normalizedPath}"
    }

    static int shardFor(String taskPath, int shardCount) {
        byte[] digest = MessageDigest.getInstance('SHA-256').digest(taskPath.getBytes(StandardCharsets.UTF_8))
        new BigInteger(1, digest).mod(BigInteger.valueOf(shardCount)).intValue()
    }

    private static final class ShardConfiguration {
        final int shardCount
        final int shardIndex

        ShardConfiguration(int shardCount, int shardIndex) {
            this.shardCount = shardCount
            this.shardIndex = shardIndex
        }
    }
}
