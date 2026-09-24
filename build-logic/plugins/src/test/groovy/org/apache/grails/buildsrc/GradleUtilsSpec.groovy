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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class GradleUtilsSpec extends Specification {

    @TempDir
    Path testProjectDir

    def "findRootGrailsCoreDir fails with an explanation when no ancestor has .asf.yaml"() {
        given:
        Project project = ProjectBuilder.builder()
                .withProjectDir(testProjectDir.toFile())
                .withName('no-marker')
                .build()

        when:
        GradleUtils.findRootGrailsCoreDir(project)

        then:
        IllegalStateException exception = thrown()
        exception.message.contains('no .asf.yaml found in')
        exception.message.contains(testProjectDir.toFile().name)
    }

    def "findRootGrailsCoreDir finds an ancestor .asf.yaml marker"() {
        given:
        testProjectDir.resolve('.asf.yaml').toFile().text = ''
        Path nested = testProjectDir.resolve('nested/module')
        nested.toFile().mkdirs()
        Project project = ProjectBuilder.builder()
                .withProjectDir(nested.toFile())
                .withName('nested-module')
                .build()

        expect:
        GradleUtils.findRootGrailsCoreDir(project).asFile.canonicalFile ==
                testProjectDir.toFile().canonicalFile
    }

    def "findRootGrailsCoreDir resolves a module of a worktree nested at #worktree to that worktree"() {
        given:
        testProjectDir.resolve('.asf.yaml').toFile().text = ''
        Path worktreeRoot = testProjectDir.resolve(worktree)
        Path module = worktreeRoot.resolve('grails-core')
        module.toFile().mkdirs()
        worktreeRoot.resolve('.asf.yaml').toFile().text = ''
        Project project = ProjectBuilder.builder()
                .withProjectDir(module.toFile())
                .withName('grails-core')
                .build()

        expect:
        GradleUtils.findRootGrailsCoreDir(project).asFile.canonicalFile == worktreeRoot.toFile().canonicalFile

        where:
        worktree << ['.worktrees/feature', '.claude/worktrees/feature']
    }

    def "isRootGrailsCoreDir distinguishes the repository root from nested builds"() {
        given:
        testProjectDir.resolve('.asf.yaml').toFile().text = ''
        Path nestedBuild = testProjectDir.resolve('grails-gradle')
        nestedBuild.toFile().mkdirs()

        expect:
        GradleUtils.isRootGrailsCoreDir(ProjectBuilder.builder().withProjectDir(testProjectDir.toFile()).build())
        !GradleUtils.isRootGrailsCoreDir(ProjectBuilder.builder().withProjectDir(nestedBuild.toFile()).build())
    }
}
