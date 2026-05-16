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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.plugins.quality.PmdPlugin
import org.gradle.api.provider.Provider

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsPlugin
import com.github.spotbugs.snom.SpotBugsReport
import com.github.spotbugs.snom.SpotBugsTask

/**
 * Convention plugin for Grails byte code analysis (PMD and SpotBugs).
 * Both tools are opt-in; enable via Gradle properties.
 */
@CompileStatic
class GrailsCodeAnalysisPlugin implements Plugin<Project> {

    static String PMD_DIR_PROPERTY = 'grails.codeanalysis.dir.pmd'
    static String PMD_ENABLED_PROPERTY = 'grails.codeanalysis.enabled.pmd'
    static String PMD_CONFIG_FILE_NAME = 'pmd.xml'

    static String SPOTBUGS_ENABLED_PROPERTY = 'grails.codeanalysis.enabled.spotbugs'

    static String IGNORE_FAILURES_PROPERTY = 'grails.codeanalysis.ignoreFailures'
    static String TEST_ANALYSIS_PROPERTY = 'grails.codeanalysis.enabled.tests'

    static String BASE_RESOURCE_PATH = '/META-INF/org.apache.grails.buildsrc.grails-code-analysis'

    @Override
    void apply(Project project) {
        initExtension(project)
        configurePmd(project)
        configureSpotbugs(project)

        // withType returns a live empty collection when the tool is not enabled,
        // so these dependsOn calls are safe regardless of whether PMD/SpotBugs are active
        project.tasks.register('codeAnalysis') { task ->
            task.group = 'verification'
            task.description = 'Runs code analysis checks (PMD, SpotBugs)'
            task.dependsOn(project.tasks.withType(Pmd))
            task.dependsOn(project.tasks.withType(SpotBugsTask))
        }
    }

    private static void initExtension(Project project) {
        def gca = project.extensions.create('grailsCodeAnalysis', GrailsCodeAnalysisExtension)

        gca.pmdDirectory.set(project.provider {
            def directory = project.hasProperty(PMD_DIR_PROPERTY) ?
                    project.rootProject.layout.projectDirectory.dir(project.property(PMD_DIR_PROPERTY) as String) :
                    project.rootProject.layout.buildDirectory.get().dir('codeanalysis').dir('pmd')

            def toCreate = directory.asFile.toPath()
            Files.createDirectories(toCreate)

            createOrLoad(
                    toCreate.resolve(PMD_CONFIG_FILE_NAME),
                    "${BASE_RESOURCE_PATH}/pmd/${PMD_CONFIG_FILE_NAME}",
                    project
            )

            directory
        })
    }

    private static void createOrLoad(Path expectedPath, String defaultResource, Project project) {
        boolean defaultPath = expectedPath.startsWith(project.rootProject.buildDir.toPath())
        if (!Files.exists(expectedPath) || expectedPath.size() == 0 || defaultPath) {
            def defaultValue = GrailsCodeAnalysisPlugin.getResourceAsStream(defaultResource)
            if (!defaultValue) {
                throw new IllegalStateException("Could not locate default configuration file: ${defaultResource}")
            }
            project.logger.info('Replacing code analysis configuration')
            expectedPath.text = defaultValue.text
        }
    }

    static void configurePmd(Project project) {
        Provider<Boolean> pmdEnabled = GradleUtils.booleanProvider(project, PMD_ENABLED_PROPERTY)
        if (!pmdEnabled.get()) {
            return
        }

        project.pluginManager.apply(PmdPlugin)

        Provider<Boolean> ignoreFailures = GradleUtils.booleanProvider(project, IGNORE_FAILURES_PROPERTY)
        Provider<Boolean> testStylingEnabled = GradleUtils.booleanProvider(project, TEST_ANALYSIS_PROPERTY)

        project.extensions.configure(PmdExtension) {
            it.ruleSetFiles = project.files(project.extensions.getByType(GrailsCodeAnalysisExtension).pmdDirectory.file(PMD_CONFIG_FILE_NAME))
            it.ruleSets = []
            it.ignoreFailures = ignoreFailures.get()
            it.consoleOutput = true
            it.toolVersion = project.findProperty('pmdVersion')
        }

        project.tasks.withType(Pmd).configureEach { Pmd task ->
            task.group = 'verification'
            task.onlyIf { !project.hasProperty('skipCodeStyle') }
            task.ignoreFailures = ignoreFailures.get()

            if (task.name.contains('Test') || task.name.contains('test')) {
                task.enabled = testStylingEnabled.get()
            }

            task.reports.xml.required.set(true)
            task.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeAnalysisExtension)
                            .reportsDirectory.get()
                            .dir('pmd')
                            .file("${project.name}-${task.name}.xml")
            )
        }
    }

    static void configureSpotbugs(Project project) {
        Provider<Boolean> spotbugsEnabled = GradleUtils.booleanProvider(project, SPOTBUGS_ENABLED_PROPERTY)
        if (!spotbugsEnabled.get()) {
            return
        }

        project.pluginManager.apply(SpotBugsPlugin)

        Provider<Boolean> ignoreFailures = GradleUtils.booleanProvider(project, IGNORE_FAILURES_PROPERTY)
        Provider<Boolean> testStylingEnabled = GradleUtils.booleanProvider(project, TEST_ANALYSIS_PROPERTY)

        project.extensions.configure(SpotBugsExtension) {
            it.effort.set(Effort.valueOf('MAX'))
            it.reportLevel.set(Confidence.valueOf('HIGH'))
            it.ignoreFailures.set(ignoreFailures)
        }

        project.tasks.withType(SpotBugsTask).configureEach { SpotBugsTask task ->
            task.group = 'verification'
            NamedDomainObjectContainer<SpotBugsReport> spotBugsReports = task.reports
            SpotBugsReport htmlReport = spotBugsReports.maybeCreate('html')
            htmlReport.required.set(true)
            SpotBugsReport xmlReport = spotBugsReports.maybeCreate('xml')
            xmlReport.required.set(true)
            xmlReport.outputLocation.set(
                project.extensions.getByType(GrailsCodeAnalysisExtension)
                        .reportsDirectory.get()
                        .dir('spotbugs')
                        .file("${project.name}-${task.name}.xml")
            )
            task.onlyIf { !project.hasProperty('skipCodeStyle') }

            if (task.name.contains('Test') || task.name.contains('test')) {
                task.enabled = testStylingEnabled.get()
            }
        }
    }
}
