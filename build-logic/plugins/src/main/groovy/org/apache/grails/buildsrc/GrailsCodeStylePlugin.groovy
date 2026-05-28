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
import java.nio.file.Path

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.plugins.quality.CodeNarcExtension
import org.gradle.api.plugins.quality.CodeNarcPlugin
import org.gradle.api.provider.Provider

/**
 * Convention plugin for Grails code style enforcement (Checkstyle and CodeNarc).
 */
@CompileStatic
class GrailsCodeStylePlugin implements Plugin<Project> {

    static String CHECKSTYLE_DIR_PROPERTY = 'grails.codestyle.dir.checkstyle'
    static String CHECKSTYLE_ENABLED_PROPERTY = 'grails.codestyle.enabled.checkstyle'
    static String CHECKSTYLE_CONFIG_FILE_NAME = 'checkstyle.xml'
    static String CHECKSTYLE_SUPPRESSION_CONFIG_FILE_NAME = 'checkstyle-suppressions.xml'

    static String CODENARC_DIR_PROPERTY = 'grails.codestyle.dir.codenarc'
    static String CODENARC_ENABLED_PROPERTY = 'grails.codestyle.enabled.codenarc'
    static String CODENARC_CONFIG_FILE_NAME = 'codenarc.groovy'

    static String CODENARC_FIX_PROPERTY = 'grails.codestyle.codenarc.fix'

    static String IGNORE_FAILURES_PROPERTY = 'grails.codestyle.ignoreFailures'

    static String TEST_STYLING_PROPERTY = 'grails.codestyle.enabled.tests'

    static String BASE_RESOURCE_PATH = '/META-INF/org.apache.grails.buildsrc.grails-code-style'

    @Override
    void apply(Project project) {
        initExtension(project)
        configureCodeStyle(project)
    }

    private static void initExtension(Project project) {
        def gce = project.extensions.create('grailsCodeStyle', GrailsCodeStyleExtension)

        // We are trying to avoid afterEvaluate usage here, so use properties for enabling / disabling

        gce.checkstyleDirectory.set(project.provider {
            def directory = project.hasProperty(CHECKSTYLE_DIR_PROPERTY) ?
                    project.rootProject.layout.projectDirectory.dir(project.property(CHECKSTYLE_DIR_PROPERTY) as String) :
                    project.rootProject.layout.buildDirectory.get().dir('codestyle').dir('checkstyle')

            def toCreate = directory.asFile.toPath()
            Files.createDirectories(toCreate)

            createOrLoad(
                    toCreate.resolve(CHECKSTYLE_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/checkstyle/${CHECKSTYLE_CONFIG_FILE_NAME}",
                    project
            )
            createOrLoad(
                    toCreate.resolve(CHECKSTYLE_SUPPRESSION_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/checkstyle/${CHECKSTYLE_SUPPRESSION_CONFIG_FILE_NAME}",
                    project
            )

            directory
        })

        gce.codenarcDirectory.set(project.provider {
            def directory = project.hasProperty(CODENARC_DIR_PROPERTY) ?
                    project.rootProject.layout.projectDirectory.dir(project.property(CODENARC_DIR_PROPERTY) as String) :
                    project.rootProject.layout.buildDirectory.get().dir('codestyle').dir('codenarc')

            def toCreate = directory.asFile.toPath()
            Files.createDirectories(toCreate)

            createOrLoad(
                    toCreate.resolve(CODENARC_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/codenarc/${CODENARC_CONFIG_FILE_NAME}",
                    project
            )

            directory
        })
    }

    private static void createOrLoad(Path expectedPath, String defaultResource, Project project) {
        def defaultValue = GrailsCodeStylePlugin.getResourceAsStream(defaultResource)
        if (!defaultValue) {
            throw new IllegalStateException("Could not locate default configuration file: ${defaultResource}")
        }
        String defaultText = defaultValue.text
        boolean missing = !Files.exists(expectedPath) || expectedPath.size() == 0
        // Only rewrite when missing or the on-disk content differs from the bundled
        // resource, so repeated builds across many subprojects don't churn the file.
        if (missing || expectedPath.text != defaultText) {
            project.logger.debug("Writing code style configuration to ${expectedPath}")
            expectedPath.text = defaultText
        }
    }

    private static void configureCodeStyle(Project project) {
        configureCheckstyle(project)
        configureCodenarc(project)

        project.tasks.register('codeStyle') {
            it.group = 'verification'
            it.description = 'Runs code style checks'
            it.dependsOn(project.tasks.withType(CodeNarc))
            it.dependsOn(project.tasks.withType(Checkstyle))
        }
    }

    static void configureCheckstyle(Project project) {
        project.pluginManager.apply(CheckstylePlugin)

        Provider<Boolean> ignoreFailures = GradleUtils.booleanProvider(project, IGNORE_FAILURES_PROPERTY)
        Provider<Boolean> testStylingEnabled = GradleUtils.booleanProvider(project, TEST_STYLING_PROPERTY)

        project.extensions.configure(CheckstyleExtension) {
            // Explicit `it` is required in extension configuration
            it.getConfigDirectory().set(project.extensions.getByType(GrailsCodeStyleExtension).checkstyleDirectory)
            it.maxWarnings = 0
            it.showViolations = true
            it.ignoreFailures = ignoreFailures.get()
            it.toolVersion = project.findProperty('checkstyleVersion')
        }

        project.tasks.withType(Checkstyle).configureEach { Checkstyle task ->
            task.group = 'verification'
            task.onlyIf { !project.hasProperty('skipCodeStyle') }
            task.ignoreFailures = ignoreFailures.get()

            if (task.name.contains('Test') || task.name.contains('test')) {
                task.enabled = testStylingEnabled.get()
            }

            // Redirect XML report output to a single directory to consolidate
            // reports across all subprojects into one known location
            task.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeStyleExtension)
                            .reportsDirectory.get()
                            .dir('checkstyle')
                            .file("${project.name}-${task.name}.xml")
            )
        }
    }

    static void configureCodenarc(Project project) {
        project.pluginManager.apply(CodeNarcPlugin)

        registerCodenarcFixTask(project)

        Provider<Boolean> ignoreFailures = GradleUtils.booleanProvider(project, IGNORE_FAILURES_PROPERTY)
        Provider<Boolean> testStylingEnabled = GradleUtils.booleanProvider(project, TEST_STYLING_PROPERTY)
        Provider<Boolean> codenarcFix = GradleUtils.booleanProvider(project, CODENARC_FIX_PROPERTY)

        project.extensions.configure(CodeNarcExtension) {
            it.configFile = project.extensions.getByType(GrailsCodeStyleExtension)
                    .codenarcDirectory.file(CODENARC_CONFIG_FILE_NAME).get().asFile
            it.ignoreFailures = ignoreFailures.get()
            it.toolVersion = project.findProperty('codenarcVersion')
        }

        project.tasks.withType(CodeNarc).configureEach { CodeNarc task ->
            task.group = 'verification'
            task.onlyIf { !project.hasProperty('skipCodeStyle') }
            task.ignoreFailures = ignoreFailures.get()

            if (codenarcFix.get()) {
                task.dependsOn('codenarcFix')
            }

            if (task.name.contains('Test') || task.name.contains('test')) {
                task.enabled = testStylingEnabled.get()
            }

            // Redirect XML report output to a single directory to consolidate
            // reports across all subprojects into one known location
            task.reports.xml.required.set(true)
            task.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeStyleExtension)
                            .reportsDirectory.get()
                            .dir('codenarc')
                            .file("${project.name}-${task.name}.xml")
            )
        }
    }

    @CompileDynamic
    private static void registerCodenarcFixTask(Project project) {
        project.tasks.register('codenarcFix') {
            it.group = 'verification'
            it.description = 'Automatically fixes some CodeNarc violations'
            it.doLast {
                project.fileTree(project.projectDir) {
                    it.include 'src/**/*.groovy'
                    it.include 'grails-app/**/*.groovy'
                    it.include 'scripts/**/*.groovy'
                    it.exclude '**/build/**'
                }.each { file ->
                    String content = file.text
                    String original = content

                    // 1. ClassStartsWithBlankLine
                    content = content.replaceAll(/(class\s+[^{]+\{\n)([ \t]*[^ \s\n\/])/, '$1\n$2')

                    // 2. SpaceAroundMapEntryColon
                    // (?!:) prevents matching the first : in a :: method reference (e.g. String::trim)
                    content = content.replaceAll(/([\[,]\s*(?:[\w\-.]+|'[^']+'|"[^"]+")):(?!:)([^\s\/])/, '$1: $2')
                    content = content.replaceAll(/(\(\s*(?:[\w\-.]+|'[^']+'|"[^"]+")):(?!:)([^\s\/])/, '$1: $2')

                    // 3. UnnecessaryGString
                    // The alternation skips over single-quoted strings so their embedded double-quote
                    // content is never touched. The (?<!}) lookbehind prevents fusing the closing "
                    // of a GString with the opening " of an adjacent plain string (e.g. obj."${x}"("y")).
                    content = content.replaceAll(/(?<!\\)'(?:[^'\\\n]|\\.)*'|(?<!\\)(?<!")(?<!})"([^"$\n\\]*)"(?!")/) { List<String> args ->
                        if (args[1] == null) {
                            return args[0]  // single-quoted string matched — leave it untouched
                        }
                        String inner = args[1]
                        if (!inner.contains("'")) {
                            return "'$inner'"
                        }
                        return args[0]
                    }

                    // 4. UnnecessarySemicolon
                    content = content.replaceAll(/(?m);[ \t]*$/, '')

                    // 5. SpaceBeforeOpeningBrace
                    content = content.replaceAll(/(?<!\\)([\)\]\}\w])\{/, '$1 {')

                    // 6. ConsecutiveBlankLines
                    content = content.replaceAll(/\n{3,}/, '\n\n')

                    if (content != original) {
                        file.text = content
                        project.logger.lifecycle("Fixed CodeNarc violations in ${file.path}")
                    }
                }
            }
        }
    }
}
