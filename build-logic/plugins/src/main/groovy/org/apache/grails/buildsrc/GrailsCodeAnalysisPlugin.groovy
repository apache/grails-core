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

import groovy.transform.CompileStatic

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsPlugin
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.plugins.quality.PmdPlugin

/**
 * Convention plugin for Grails byte code analysis (PMD and SpotBugs).
 * Both tools are opt-in; enable via Gradle properties.
 */
@CompileStatic
class GrailsCodeAnalysisPlugin implements Plugin<Project> {

    private static final Logger LOGGER = Logging.getLogger(GrailsViolationAggregationPlugin)

    static String PMD_DIR_PROPERTY = 'grails.code-analysis.dir.pmd'
    static String PMD_ENABLED_PROPERTY = 'grails.code-analysis.enabled.pmd'
    static String PMD_CONFIG_FILE_NAME = 'pmd.xml'

    static String SPOTBUGS_ENABLED_PROPERTY = 'grails.code-analysis.enabled.spotbugs'

    static String IGNORE_FAILURES_PROPERTY = 'grails.code-analysis.ignoreFailures'
    static String TEST_ANALYSIS_PROPERTY = 'grails.code-analysis.enabled.tests'

    static String BASE_RESOURCE_PATH = '/META-INF/org.apache.grails.buildsrc.grails-code-analysis'

    @Override
    void apply(Project project) {
        initExtension(project)
        configurePmd(project)
        configureSpotbugs(project)

        // withType returns a live empty collection when the tool is not enabled,
        // so these dependsOn calls are safe regardless of whether PMD/SpotBugs are active
        project.tasks.register('codeAnalysis') {
            it.group = 'verification'
            it.description = 'Runs code analysis checks (PMD, SpotBugs)'
            it.dependsOn(project.tasks.withType(Pmd))
            it.dependsOn(project.tasks.withType(SpotBugsTask))
        }
    }

    private static void initExtension(Project project) {
        def gca = project.extensions.create('grailsCodeAnalysis', GrailsCodeAnalysisExtension)
        def buildDirectory = project.layout.buildDirectory

        gca.pmdDirectory.set(project.provider {
            def directory = project.hasProperty(PMD_DIR_PROPERTY) ?
                    project.rootProject.layout.projectDirectory.dir(project.property(PMD_DIR_PROPERTY) as String) :
                    project.rootProject.layout.buildDirectory.get().dir('code-analysis').dir('pmd')

            def toCreate = directory.asFile.toPath()
            Files.createDirectories(toCreate)

            createOrLoad(
                    toCreate.resolve(PMD_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/pmd/${PMD_CONFIG_FILE_NAME}",
                    buildDirectory
            )

            directory
        })
    }

    private static void createOrLoad(Path expectedPath, String defaultResource, DirectoryProperty buildDirectory) {
        def defaultPath = expectedPath.startsWith(buildDirectory.get().asFile.toPath())
        if (!Files.exists(expectedPath) || expectedPath.size() == 0 || defaultPath) {
            def defaultValue = GrailsCodeAnalysisPlugin.getResourceAsStream(defaultResource)
            if (!defaultValue) {
                throw new IllegalStateException("Could not locate default configuration file: ${defaultResource}")
            }
            LOGGER.info('Replacing code analysis configuration')
            expectedPath.text = defaultValue.text
        }
    }

    static void configurePmd(Project project) {
        def pmdEnabled = GradleUtils.booleanProvider(project, PMD_ENABLED_PROPERTY)
        if (!pmdEnabled.get()) {
            return
        }

        project.pluginManager.apply(PmdPlugin)

        def ignoreFailures = GradleUtils.booleanProvider(project, IGNORE_FAILURES_PROPERTY)
        def testStylingEnabled = GradleUtils.booleanProvider(project, TEST_ANALYSIS_PROPERTY)

        project.extensions.configure(PmdExtension) {
            it.ruleSetFiles = project.files(project.extensions.getByType(GrailsCodeAnalysisExtension).pmdDirectory.file(PMD_CONFIG_FILE_NAME))
            it.ruleSets = []
            it.ignoreFailures = ignoreFailures.get()
            it.consoleOutput = true
            it.toolVersion = project.findProperty('pmdVersion')
        }

        project.tasks.withType(Pmd).configureEach {
            it.group = 'verification'
            it.onlyIf { !project.hasProperty('skipCodeStyle') }
            it.ignoreFailures = ignoreFailures.get()

            if (it.name.contains('Test') || it.name.contains('test')) {
                it.enabled = testStylingEnabled.get()
            }

            it.reports.xml.required.set(true)
            it.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeAnalysisExtension)
                            .reportsDirectory.get()
                            .dir('pmd')
                            .file("${project.name}-${it.name}.xml")
            )
        }
    }

    static void configureSpotbugs(Project project) {
        def spotbugsEnabled = GradleUtils.booleanProvider(project, SPOTBUGS_ENABLED_PROPERTY)
        if (!spotbugsEnabled.get()) {
            return
        }

        project.pluginManager.apply(SpotBugsPlugin)

        def ignoreFailures = GradleUtils.booleanProvider(project, IGNORE_FAILURES_PROPERTY)
        def testStylingEnabled = GradleUtils.booleanProvider(project, TEST_ANALYSIS_PROPERTY)

        project.extensions.configure(SpotBugsExtension) {
            it.effort.set(Effort.valueOf('MAX'))
            it.reportLevel.set(Confidence.valueOf('HIGH'))
            it.ignoreFailures.set(ignoreFailures)
        }

        project.tasks.withType(SpotBugsTask).configureEach {
            it.group = 'verification'
            def spotBugsReports = it.reports
            def htmlReport = spotBugsReports.maybeCreate('html')
            htmlReport.required.set(true)
            def xmlReport = spotBugsReports.maybeCreate('xml')
            xmlReport.required.set(true)
            xmlReport.outputLocation.set(
                project.extensions.getByType(GrailsCodeAnalysisExtension)
                        .reportsDirectory.get()
                        .dir('spotbugs')
                        .file("${project.name}-${it.name}.xml")
            )
            it.onlyIf { !project.hasProperty('skipCodeStyle') }

            if (it.name.contains('Test') || it.name.contains('test')) {
                it.enabled = testStylingEnabled.get()
            }
        }
    }
}
