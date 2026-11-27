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
package org.grails.gradle.plugin.views.gsp

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import org.grails.gradle.plugin.views.AbstractViewCompileEnvironmentFunctionalSpec

/**
 * Covers the environment {@link GroovyPageForkCompileTask} compiles pages for, and that every page the
 * compiler reads is an input of the task.
 *
 * @since 7.0.18
 */
class GroovyPageCompileEnvironmentFunctionalSpec extends AbstractViewCompileEnvironmentFunctionalSpec {

    @Override
    String getProjectName() {
        'gsp-compile-environment'
    }

    @Override
    String getTaskName() {
        'compileGroovyPages'
    }

    @Override
    String getDestination() {
        'build/gsp-classes/main'
    }

    // the page compiler comes from grails-web-gsp, which this build cannot depend on
    @Override
    String getStandIn() {
        'org/grails/web/pages/GroovyPageForkedCompiler.class'
    }

    @Override
    File view(File checkout, String name) {
        File page = new File(checkout, "grails-app/views/book/${name}.gsp")
        page.parentFile.mkdirs()
        page.text = '${book.title}'
        page
    }

    def "the webapp pages task has no source in a project without src/main/webapp, rather than failing for want of it"() {
        given:
        projectWithViews()

        when:
        BuildResult result = compileViews()

        then:
        result.task(':compileWebappGroovyPages').outcome == TaskOutcome.NO_SOURCE
    }

    def "a project without pages of its own compiles none"() {
        given:
        projectWithViews()
        new File(checkout, 'grails-app/views').deleteDir()

        when:
        BuildResult result = compileViews()

        then:
        result.task(':compileGroovyPages').outcome == TaskOutcome.NO_SOURCE
    }

    def "a page the task's own filters leave out is compiled all the same, and so is an input all the same"() {
        given:
        projectWithViews()
        File other = view(checkout, 'other')
        configureTask("exclude 'book/other.gsp'")
        cached()
        compileViews([:], ['--build-cache'])

        when: 'the page outside the source is edited'
        other << '\n'
        BuildResult edited = compileViews([:], ['--build-cache'])

        then: 'the task runs rather than standing up to date, and the compiler sees the edit'
        edited.task(':compileGroovyPages').outcome == TaskOutcome.SUCCESS
        compiled().pages.contains("book/other.gsp:${other.length()}")

        when: 'a clean build is restored from the cache'
        executeTask('clean')
        BuildResult restored = compileViews([:], ['--build-cache'])

        then: 'with what the edited page compiled to'
        restored.task(':compileGroovyPages').outcome == TaskOutcome.FROM_CACHE
        compiled().pages.contains("book/other.gsp:${other.length()}")
    }
}
