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

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Covers which Java compiles a project's pages.
 *
 * <p>Compilation is forked, and a forked process runs whatever JVM it is given. Left to itself it
 * inherits the one running Gradle, so a project that asked for a toolchain had its pages built by a
 * different Java from everything else it built. Nothing failed at build time: the mismatch surfaced
 * as an {@code UnsupportedClassVersionError} the first time a page was rendered.</p>
 *
 * @since 8.0
 */
class GroovyPageToolchainSpec extends Specification {

    private Project projectWithPages() {
        Project project = ProjectBuilder.builder().build()
        project.pluginManager.apply('groovy')
        project.pluginManager.apply(GroovyPagePlugin)
        project
    }

    private static GroovyPageForkCompileTask compileTask(Project project, String name) {
        project.tasks.getByName(name) as GroovyPageForkCompileTask
    }

    void 'pages are compiled by the Java the project asked for'() {
        given:
            Project project = projectWithPages()

        when: 'a toolchain, as a project pins the Java it is built with'
            project.extensions.getByType(JavaPluginExtension)
                    .toolchain.languageVersion.set(JavaLanguageVersion.of(21))

        then: 'and not by whichever Java happens to be running the build'
            compileTask(project, 'compileGroovyPages')
                    .javaLauncher.get().metadata.languageVersion == JavaLanguageVersion.of(21)
    }

    void 'the pages under src/main/webapp are compiled by it too'() {
        given:
            Project project = projectWithPages()

        when:
            project.extensions.getByType(JavaPluginExtension)
                    .toolchain.languageVersion.set(JavaLanguageVersion.of(21))

        then: 'the second of the two compile tasks is no less able to produce an unreadable class'
            compileTask(project, 'compileWebappGroovyPages')
                    .javaLauncher.get().metadata.languageVersion == JavaLanguageVersion.of(21)
    }

    void 'a project that asked for nothing is built by the Java running the build'() {
        given: 'which is what compiling pages fell back to before, and is still the right answer'
            Project project = projectWithPages()

        expect:
            compileTask(project, 'compileGroovyPages').javaLauncher.get().metadata.languageVersion ==
                    JavaLanguageVersion.of(System.getProperty('java.specification.version'))
    }

    void 'an application can name the Java for pages alone'() {
        given:
            Project project = projectWithPages()
            project.extensions.getByType(JavaPluginExtension)
                    .toolchain.languageVersion.set(JavaLanguageVersion.of(21))

        when: 'the convention is a default rather than a fixture'
            GroovyPageForkCompileTask task = compileTask(project, 'compileGroovyPages')
            task.javaLauncher.set(project.extensions.getByType(org.gradle.jvm.toolchain.JavaToolchainService)
                    .launcherFor { it.languageVersion.set(JavaLanguageVersion.of(21)) })

        then:
            task.javaLauncher.get().metadata.languageVersion == JavaLanguageVersion.of(21)
    }

    void 'the Java that did the compiling is part of what the result is'() {
        given: 'the task is cacheable, so an entry built by one Java must not be reused by another'
            Project project = projectWithPages()

        expect: 'declared as an input, which is what keeps the two apart'
            GroovyPageForkCompileTask.getMethod('getJavaLauncher')
                    .isAnnotationPresent(org.gradle.api.tasks.Nested)
    }
}
