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

import com.diffplug.gradle.spotless.SpotlessTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.plugins.quality.CodeNarcExtension
import org.gradle.api.plugins.quality.CodeNarcPlugin
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.plugins.quality.PmdPlugin

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsPlugin
import com.github.spotbugs.snom.SpotBugsTask

/**
 * Convention plugin for Grails code style enforcement.
 */
@CompileStatic
class GrailsCodeStylePlugin implements Plugin<Project> {

    static String CHECKSTYLE_DIR_PROPERTY = 'grails.codestyle.dir.checkstyle'
    static String CHECKSTYLE_CONFIG_FILE_NAME = 'checkstyle.xml'
    static String CHECKSTYLE_SUPPRESSION_CONFIG_FILE_NAME = 'checkstyle-suppressions.xml'

    static String SPOTLESS_DIR_PROPERTY = 'grails.codestyle.dir.spotless'
    static String SPOTLESS_ENABLED_PROPERTY = 'grails.codestyle.enabled.spotless'
    static String SPOTLESS_GRECLIPSE_CONFIG_FILE_NAME = 'greclipse.properties'

    static String PMD_DIR_PROPERTY = 'grails.codestyle.dir.pmd'
    static String PMD_ENABLED_PROPERTY = 'grails.codestyle.enabled.pmd'
    static String PMD_CONFIG_FILE_NAME = 'pmd.xml'

    static String CODENARC_DIR_PROPERTY = 'grails.codestyle.dir.codenarc'
    static String CODENARC_CONFIG_FILE_NAME = 'codenarc.groovy'

    static String SPOTBUGS_ENABLED_PROPERTY = 'grails.codestyle.enabled.spotbugs'

    static String TEST_STYLING_PROPERTY = 'grails.codestyle.enabled.tests'

    static String BASE_RESOURCE_PATH = '/META-INF/org.apache.grails.buildsrc.codestyle'

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

        gce.spotlessDirectory.set(project.provider {
            def directory = project.hasProperty(SPOTLESS_DIR_PROPERTY) ?
                    project.rootProject.layout.projectDirectory.dir(project.property(SPOTLESS_DIR_PROPERTY) as String) :
                    project.rootProject.layout.buildDirectory.get().dir('codestyle').dir('spotless')

            def toCreate = directory.asFile.toPath()
            Files.createDirectories(toCreate)

            createOrLoad(
                    toCreate.resolve(SPOTLESS_GRECLIPSE_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/spotless/${SPOTLESS_GRECLIPSE_CONFIG_FILE_NAME}",
                    project
            )

            directory
        })

        gce.pmdDirectory.set(project.provider {
            def directory = project.hasProperty(PMD_DIR_PROPERTY) ?
                    project.rootProject.layout.projectDirectory.dir(project.property(PMD_DIR_PROPERTY) as String) :
                    project.rootProject.layout.buildDirectory.get().dir('codestyle').dir('pmd')

            def toCreate = directory.asFile.toPath()
            Files.createDirectories(toCreate)

            createOrLoad(
                    toCreate.resolve(PMD_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/pmd/${PMD_CONFIG_FILE_NAME}",
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
        boolean defaultPath = expectedPath.startsWith(project.rootProject.buildDir.toPath())
        if (!Files.exists(expectedPath) || expectedPath.size() == 0 || defaultPath) {
            def defaultValue = GrailsCodeStylePlugin.getResourceAsStream(defaultResource)
            if (!defaultValue) {
                throw new IllegalStateException("Could not locate default configuration file: ${defaultResource}")
            }
            // TODO: This really need to use gradle caching instead
            project.logger.info("Replacing code style configuration")
            expectedPath.text = defaultValue.text
        }
    }

    private static void configureCodeStyle(Project project) {
        configureCheckstyle(project)
        configureSpotless(project)
        configureCodenarc(project)
        configurePmd(project)
        configureSpotbugs(project)

        project.tasks.register('codeStyle') {
            it.group = 'verification'
            it.description = 'Runs code style checks'
            it.dependsOn(project.tasks.withType(CodeNarc))
            it.dependsOn(project.tasks.withType(Checkstyle))
            if (GradleUtils.lookupProperty(project, SPOTLESS_ENABLED_PROPERTY, false)) {
                it.dependsOn('spotlessCheck')
            }
            if (GradleUtils.lookupProperty(project, PMD_ENABLED_PROPERTY, false)) {
                it.dependsOn(project.tasks.withType(Pmd))
            }
            if (GradleUtils.lookupProperty(project, SPOTBUGS_ENABLED_PROPERTY, false)) {
                it.dependsOn(project.tasks.withType(SpotBugsTask))
            }
        }
    }

    static void configureCheckstyle(Project project) {
        project.pluginManager.apply(CheckstylePlugin)

        project.extensions.configure(CheckstyleExtension) {
            // Explicit `it` is required in extension configuration
            it.getConfigDirectory().set(project.extensions.getByType(GrailsCodeStyleExtension).checkstyleDirectory)
            it.maxWarnings = 0
            it.showViolations = true
            it.ignoreFailures = false
            it.toolVersion = project.findProperty('checkstyleVersion')
        }

        project.tasks.withType(Checkstyle).configureEach { Checkstyle task ->
            task.group = 'verification'
            task.onlyIf { !project.hasProperty('skipCodeStyle') }

            // Redirect XML report output to a single directory to consolidate
            // reports across all subprojects into one known location.
            // Include the task name to avoid overlapping outputs when a project has
            // multiple source sets (e.g. grails-cache has ast + main).
            task.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeStyleExtension)
                            .reportsDirectory.get()
                            .dir('checkstyle')
                            .file("${project.name}-${task.name}.xml")
            )
        }

        if (!GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)) {
            project.tasks.named('checkstyleTest') {
                it.enabled = false // Do not check test sources at this time
            }
        }
    }

    @CompileDynamic
    static void configureSpotless(Project project) {
        if (!GradleUtils.lookupProperty(project, SPOTLESS_ENABLED_PROPERTY, false)) {
            return
        }

        project.pluginManager.apply(SpotlessPlugin)

        boolean applyToTests = GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)

        project.extensions.configure(SpotlessExtension) { SpotlessExtension spotless ->
            def gce = project.extensions.getByType(GrailsCodeStyleExtension)
            spotless.java { javaFmt ->
                javaFmt.palantirJavaFormat()

                // Import management (replaces Checkstyle ImportOrderCheck, AvoidStarImport,
                // RedundantImport, UnusedImports)
                javaFmt.importOrder('java|javax',
                                     'groovy|org.apache.groovy|org.codehaus.groovy',
                                     'jakarta',
                                     '',
                                     'io.spring|org.springframework',
                                     'grails|org.apache.grails|org.grails',
                                     '\\#')
                javaFmt.removeUnusedImports()

                // TODO: Switch to expandWildcardImports() once it no longer triggers afterEvaluate.
                //  For now, forbidWildcardImports() reports violations without auto-fixing; wildcard
                //  imports must be cleaned up manually.
                javaFmt.forbidWildcardImports()

                // Whitespace (replaces Checkstyle NewlineAtEndOfFile, FileTabCharacter)
                javaFmt.trimTrailingWhitespace()
                javaFmt.endWithNewline()
                javaFmt.leadingTabsToSpaces(4)

                List<String> javaIncludes = ['src/main/**/*.java']
                if (applyToTests) {
                    javaIncludes.add('src/test/**/*.java')
                    javaIncludes.add('src/integrationTest/**/*.java')
                }
                javaFmt.target(project.fileTree(project.projectDir) { ft ->
                    ft.include(javaIncludes)
                })
            }

            // TODO: Groovy formatting is so close, but doesn't fully work
//            spotless.groovy { groovyFmt ->
//                // Groovy-Eclipse formatter for auto-fixing CodeNarc spacing/indentation violations.
//                groovyFmt.greclipse()
//                        .configFile(gce.spotlessDirectory.file(SPOTLESS_GRECLIPSE_CONFIG_FILE_NAME))
//
//                // Import management (matches CodeNarc MisorderedStaticImports, NoWildcardImports,
//                // DuplicateImport, UnusedImport, UnnecessaryGroovyImport)
//                groovyFmt.importOrder(DEFAULT_IMPORT_ORDER as String[])
//
//                // Remove unnecessary semicolons (matches CodeNarc UnnecessarySemicolon)
//                groovyFmt.removeSemicolons()
//
//                // Whitespace (matches CodeNarc NoTabCharacter, FileEndsWithoutNewline)
//                groovyFmt.trimTrailingWhitespace()
//                groovyFmt.endWithNewline()
//
//                if (applyToTests) {
//                    groovyFmt.excludeJava()
//                } else {
//                    // Only apply to main sources (excludeJava() cannot be combined with target())
//                    groovyFmt.target(project.fileTree(project.projectDir) { ft ->
//                        ft.include('src/main/**/*.groovy')
//                    })
//                }
//            }
        }

        project.tasks.withType(SpotlessTask).configureEach {
            it.group = 'verification'
            // it.outputs.cacheIf { false }
            it.notCompatibleWithConfigurationCache("Spotless greclipse classloader issues")
            it.onlyIf { !project.hasProperty('skipCodeStyle') }
        }
    }

    static void configurePmd(Project project) {
        if (!GradleUtils.lookupProperty(project, PMD_ENABLED_PROPERTY, false)) {
            return
        }

        project.pluginManager.apply(PmdPlugin)

        project.extensions.configure(PmdExtension) {
            it.ruleSetFiles = project.files(project.extensions.getByType(GrailsCodeStyleExtension).pmdDirectory.file(PMD_CONFIG_FILE_NAME))
            it.ruleSets = []
            it.ignoreFailures = false
            it.consoleOutput = true
            it.toolVersion = project.findProperty('pmdVersion')
        }

        project.tasks.withType(Pmd).configureEach {
            it.group = 'verification'
            it.onlyIf { !project.hasProperty('skipCodeStyle') }
        }

        if (!GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)) {
            project.tasks.withType(Pmd).configureEach { Pmd task ->
                if (task.name.contains('Test') || task.name.contains('test')) {
                    task.enabled = false
                }
            }
        }
    }

    @CompileDynamic
    static void configureSpotbugs(Project project) {
        if (!GradleUtils.lookupProperty(project, SPOTBUGS_ENABLED_PROPERTY, false)) {
            return
        }

        project.pluginManager.apply(SpotBugsPlugin)

        project.extensions.configure(SpotBugsExtension) {
            it.effort.set(Effort.valueOf('MAX'))
            it.reportLevel.set(Confidence.valueOf('HIGH'))
        }

        project.tasks.withType(SpotBugsTask).configureEach {
            it.group = 'verification'
            it.reports {
                it.create('html') { it.required = true }
                it.create('xml') { it.required = false }
            }
            it.onlyIf { !project.hasProperty('skipCodeStyle') }
        }

        if (!GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)) {
            project.tasks.withType(SpotBugsTask).configureEach { SpotBugsTask task ->
                if (task.name.contains('Test') || task.name.contains('test')) {
                    task.enabled = false
                }
            }
        }
    }

    static void configureCodenarc(Project project) {
        project.pluginManager.apply(CodeNarcPlugin)

        project.extensions.configure(CodeNarcExtension) {
            it.configFile = project.extensions.getByType(GrailsCodeStyleExtension)
                    .codenarcDirectory.file(CODENARC_CONFIG_FILE_NAME).get().asFile
            it.toolVersion = project.findProperty('codenarcVersion')
        }

        project.tasks.withType(CodeNarc).configureEach { CodeNarc task ->
            task.group = 'verification'
            task.onlyIf { !project.hasProperty('skipCodeStyle') }

            // Redirect XML report output to a single directory to consolidate
            // reports across all subprojects into one known location.
            // Include the task name to avoid overlapping outputs when a project has
            // multiple source sets.
            task.reports.xml.required.set(true)
            task.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeStyleExtension)
                            .reportsDirectory.get()
                            .dir('codenarc')
                            .file("${project.name}-${task.name}.xml")
            )
        }

        if (!GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)) {
            project.tasks.withType(CodeNarc).configureEach { CodeNarc task ->
                if (task.name.contains('Test') || task.name.contains('test')) {
                    task.enabled = false
                }
            }
        }

        if (!GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)) {
            project.afterEvaluate {
                // Do not check test sources at this time
                ['codenarcIntegrationTest', 'codenarcTest'].each { testTaskName ->
                    if (project.tasks.names.contains(testTaskName)) {
                        project.tasks.named(testTaskName) {
                            it.enabled = false
                        }
                    }
                }
            }
        }
    }
}
