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
import info.solidsoft.gradle.pitest.PitestPlugin
import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTreeElement
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.plugins.quality.PmdPlugin

/**
 * Convention plugin for Grails byte code analysis (PMD, SpotBugs, and PITest mutation testing).
 * Tools are opt-in: a module calls {@code grailsCodeAnalysis { enablePmd() }}, {@code enableSpotbugs()}, or
 * {@code enablePitest()}, and Gradle properties can enable or disable them for baseline or targeted runs.
 */
@CompileStatic
class GrailsCodeAnalysisPlugin implements Plugin<Project> {

    private static final Logger LOGGER = Logging.getLogger(GrailsViolationAggregationPlugin)

    static String PMD_DIR_PROPERTY = 'grails.code-analysis.dir.pmd'
    static String PMD_ENABLED_PROPERTY = 'grails.code-analysis.enabled.pmd'
    static String PMD_ENABLED_PROJECTS_PROPERTY = 'grails.code-analysis.enabled.pmd.projects'
    static String PMD_CONFIG_FILE_NAME = 'pmd.xml'

    static String SPOTBUGS_ENABLED_PROPERTY = 'grails.code-analysis.enabled.spotbugs'
    static String SPOTBUGS_ENABLED_PROJECTS_PROPERTY = 'grails.code-analysis.enabled.spotbugs.projects'

    static String PITEST_ENABLED_PROPERTY = 'grails.code-analysis.enabled.pitest'
    static String PITEST_ENABLED_PROJECTS_PROPERTY = 'grails.code-analysis.enabled.pitest.projects'
    static String PITEST_TARGET_CLASSES_PROPERTY = 'grails.code-analysis.pitest.targetClasses'
    static String PITEST_TARGET_TESTS_PROPERTY = 'grails.code-analysis.pitest.targetTests'
    static String PITEST_THREADS_PROPERTY = 'grails.code-analysis.pitest.threads'
    static String PITEST_MUTATORS_PROPERTY = 'grails.code-analysis.pitest.mutators'

    static final Set<String> GROOVY_EXCLUDED_METHODS = [
            '$getStaticMetaClass',
            '$getLookup',
            'pfget$*',
            'pfset$*',
            'super$*',
            'this$*',
    ] as Set<String>

    static final Set<String> GROOVY_EXCLUDED_CLASSES = [
            '*$_closure*',
            '*$_*',
    ] as Set<String>

    static final Set<String> GROOVY_AVOID_CALLS_TO = [
            'org.codehaus.groovy.runtime',
            'org.apache.groovy.runtime',
            'groovy.lang.MetaClass',
            'org.slf4j',
            'org.apache.commons.logging',
            'java.util.logging',
    ] as Set<String>

    static String IGNORE_FAILURES_PROPERTY = 'grails.code-analysis.ignoreFailures'
    static String TEST_ANALYSIS_PROPERTY = 'grails.code-analysis.enabled.tests'

    /** Skips PMD and SpotBugs only; {@link GrailsCodeStylePlugin#SKIP_CODE_STYLE_PROPERTY} also skips them. */
    static String SKIP_CODE_ANALYSIS_PROPERTY = 'skipCodeAnalysis'

    static String BASE_RESOURCE_PATH = '/META-INF/org.apache.grails.buildsrc.grails-code-analysis'

    @Override
    void apply(Project project) {
        GrailsCodeAnalysisExtension extension = initExtension(project)
        if (isEnabledByProperties(project, PMD_ENABLED_PROPERTY, PMD_ENABLED_PROJECTS_PROPERTY)) {
            configurePmd(project, extension)
        }
        if (isEnabledByProperties(project, SPOTBUGS_ENABLED_PROPERTY, SPOTBUGS_ENABLED_PROJECTS_PROPERTY)) {
            configureSpotbugs(project, extension)
        }
        if (isEnabledByProperties(project, PITEST_ENABLED_PROPERTY, PITEST_ENABLED_PROJECTS_PROPERTY)
                || (!isDisabledByProperty(project, PITEST_ENABLED_PROPERTY) && isPitestTaskRequested(project))) {
            configurePitest(project, extension)
        }

        // withType returns a live empty collection when the tool is not enabled,
        // so these dependsOn calls are safe regardless of whether PMD/SpotBugs are active
        project.tasks.register('codeAnalysis') {
            it.group = 'verification'
            it.description = 'Runs code analysis checks (PMD, SpotBugs)'
            it.dependsOn(project.tasks.withType(Pmd))
            it.dependsOn(project.tasks.withType(SpotBugsTask))
        }
    }

    private static GrailsCodeAnalysisExtension initExtension(Project project) {
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
        gca
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

    /**
     * Enables PMD for a project that opts in, unless the all-project property disables it. Enabling it again, or
     * after the properties already enabled it, does nothing.
     */
    static void enablePmd(Project project, GrailsCodeAnalysisExtension extension) {
        if (!isDisabledByProperty(project, PMD_ENABLED_PROPERTY) && !project.pluginManager.hasPlugin('pmd')) {
            configurePmd(project, extension)
        }
    }

    /**
     * Enables SpotBugs for a project that opts in, unless the all-project property disables it. Enabling it again,
     * or after the properties already enabled it, does nothing.
     */
    static void enableSpotbugs(Project project, GrailsCodeAnalysisExtension extension) {
        if (!isDisabledByProperty(project, SPOTBUGS_ENABLED_PROPERTY)
                && !project.pluginManager.hasPlugin('com.github.spotbugs')) {
            configureSpotbugs(project, extension)
        }
    }

    /**
     * Enables PITest for a project that opts in, unless the all-project property disables it. Enabling it again,
     * or after the properties already enabled it, does nothing.
     */
    static void enablePitest(Project project, GrailsCodeAnalysisExtension extension) {
        if (!isDisabledByProperty(project, PITEST_ENABLED_PROPERTY)
                && !project.pluginManager.hasPlugin('info.solidsoft.pitest')) {
            configurePitest(project, extension)
        }
    }

    private static void configurePmd(Project project, GrailsCodeAnalysisExtension extension) {
        project.pluginManager.apply(PmdPlugin)

        def ignoreFailures = GradleUtils.booleanProvider(project, IGNORE_FAILURES_PROPERTY)
        def testStylingEnabled = GradleUtils.booleanProvider(project, TEST_ANALYSIS_PROPERTY)
        def skipCodeStyle = project.providers.gradleProperty(GrailsCodeStylePlugin.SKIP_CODE_STYLE_PROPERTY)
        def skipCodeAnalysis = project.providers.gradleProperty(SKIP_CODE_ANALYSIS_PROPERTY)
        // Resolved when the task reads its sources, because the build script may still move the build directory
        def projectBuildDirectory = project.layout.buildDirectory.map { Directory directory ->
            directory.asFile.toPath().toAbsolutePath().normalize()
        }

        project.extensions.configure(PmdExtension) {
            it.ruleSetFiles = project.files(extension.pmdDirectory.file(PMD_CONFIG_FILE_NAME))
            it.ruleSets = []
            it.ignoreFailures = ignoreFailures.get()
            it.consoleOutput = true
            it.toolVersion = project.findProperty('pmdVersion')
        }

        project.tasks.withType(Pmd).configureEach {
            it.group = 'verification'
            it.onlyIf { !skipCodeStyle.present && !skipCodeAnalysis.present }
            it.ignoreFailures = ignoreFailures.get()

            if (it.name.contains('Test') || it.name.contains('test')) {
                it.enabled = testStylingEnabled.get()
            }

            it.exclude { FileTreeElement element ->
                element.file.toPath().toAbsolutePath().normalize().startsWith(projectBuildDirectory.get())
            }

            it.reports.xml.required.set(true)
            String reportFileName = GradleUtils.reportFileName(project, it.name)
            it.reports.xml.outputLocation.set(extension.reportsDirectory.dir('pmd').map { Directory directory ->
                directory.file(reportFileName)
            })
            GradleUtils.configureReportMarker(it, project.rootProject.layout.projectDirectory, it.reports.xml.outputLocation,
                    GradleUtils.reportMarker(project, 'pmd', it.name))
        }
    }

    private static void configureSpotbugs(Project project, GrailsCodeAnalysisExtension extension) {
        project.pluginManager.apply(SpotBugsPlugin)

        def ignoreFailures = GradleUtils.booleanProvider(project, IGNORE_FAILURES_PROPERTY)
        def testStylingEnabled = GradleUtils.booleanProvider(project, TEST_ANALYSIS_PROPERTY)
        def skipCodeStyle = project.providers.gradleProperty(GrailsCodeStylePlugin.SKIP_CODE_STYLE_PROPERTY)
        def skipCodeAnalysis = project.providers.gradleProperty(SKIP_CODE_ANALYSIS_PROPERTY)

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
            String reportFileName = GradleUtils.reportFileName(project, it.name)
            xmlReport.outputLocation.set(extension.reportsDirectory.dir('spotbugs').map { Directory directory ->
                directory.file(reportFileName)
            })
            GradleUtils.configureReportMarker(it, project.rootProject.layout.projectDirectory, xmlReport.outputLocation,
                    GradleUtils.reportMarker(project, 'spotbugs', it.name))
            it.onlyIf { !skipCodeStyle.present && !skipCodeAnalysis.present }

            if (it.name.contains('Test') || it.name.contains('test')) {
                it.enabled = testStylingEnabled.get()
            }
        }
    }

    private static void configurePitest(Project project, GrailsCodeAnalysisExtension extension) {
        Runnable applyAndConfigure = {
            if (project.pluginManager.hasPlugin('info.solidsoft.pitest')) {
                return
            }
            project.pluginManager.apply(PitestPlugin)

            def targetClassesProp = project.providers.gradleProperty(PITEST_TARGET_CLASSES_PROPERTY)
            def targetTestsProp = project.providers.gradleProperty(PITEST_TARGET_TESTS_PROPERTY)
            def threadsProp = project.providers.gradleProperty(PITEST_THREADS_PROPERTY)
            def mutatorsProp = project.providers.gradleProperty(PITEST_MUTATORS_PROPERTY)

            project.extensions.configure(PitestPluginExtension) { PitestPluginExtension pitest ->
                String configuredPitestVersion = project.findProperty('pitestVersion') as String
                if (configuredPitestVersion) {
                    pitest.pitestVersion.set(configuredPitestVersion)
                }
                String configuredJunit5Version = project.findProperty('pitestJunit5PluginVersion') as String
                if (configuredJunit5Version) {
                    pitest.junit5PluginVersion.set(configuredJunit5Version)
                }
                pitest.useClasspathFile.set(true)
                pitest.features.set(['-FGROOVY'])
                pitest.outputFormats.set(['XML', 'HTML'] as Set<String>)
                pitest.timestampedReports.set(false)
                pitest.failWhenNoMutations.set(false)
                pitest.threads.set(threadsProp.map { Integer.parseInt(it.trim()) }.orElse(4))
                pitest.excludedMethods.set(GROOVY_EXCLUDED_METHODS)
                pitest.excludedClasses.set(GROOVY_EXCLUDED_CLASSES)
                pitest.avoidCallsTo.set(GROOVY_AVOID_CALLS_TO)
                pitest.reportDir.set(extension.reportsDirectory.dir("pitest/${project.name}"))

                pitest.targetClasses.set(
                        targetClassesProp.map { String val -> val.split(',')*.trim().findAll() as Set<String> }
                                .orElse(project.provider {
                                    Set<String> defaults = ['org.apache.grails.*', 'org.grails.*', 'grails.*'] as LinkedHashSet<String>
                                    String groupName = project.group?.toString()
                                    if (groupName) {
                                        defaults.add("${groupName}.*" as String)
                                    }
                                    defaults
                                })
                )
                pitest.targetTests.set(
                        targetTestsProp.map { String val -> val.split(',')*.trim().findAll() as Set<String> }
                                .orElse(['*'] as Set<String>)
                )
                if (mutatorsProp.present) {
                    pitest.mutators.set(mutatorsProp.map { String val -> val.split(',')*.trim().findAll() as Set<String> })
                }
            }
        }

        if (project.plugins.hasPlugin('java')) {
            applyAndConfigure.run()
        } else {
            project.plugins.withId('java') {
                applyAndConfigure.run()
            }
        }
    }

    private static boolean isPitestTaskRequested(Project project) {
        project.gradle.startParameter.taskNames.any { String requested ->
            requested == 'pitest'
                    || requested == "${project.path}:pitest"
                    || requested == "${project.name}:pitest"
                    || (requested.endsWith(':pitest') && project.path.endsWith(requested.substring(0, requested.length() - ':pitest'.length())))
        }
    }

    /**
     * Whether the Gradle properties enable a tool before the project's build script runs. When set, the all-project
     * property ({@code -Pgrails.code-analysis.enabled.pmd=true|false}) decides for every project and wins over both the
     * selected-projects property and the project's own opt-in. Otherwise the selected-projects property enables the
     * projects it lists.
     */
    private static boolean isEnabledByProperties(Project project, String enabledProperty, String enabledProjectsProperty) {
        if (project.providers.gradleProperty(enabledProperty).present) {
            return GradleUtils.booleanProvider(project, enabledProperty).get()
        }
        project.providers.gradleProperty(enabledProjectsProperty)
                .map { it.split(',')*.trim().contains(project.path) }
                .orElse(false)
                .get()
    }

    private static boolean isDisabledByProperty(Project project, String enabledProperty) {
        project.providers.gradleProperty(enabledProperty).present && !GradleUtils.booleanProvider(project, enabledProperty).get()
    }
}
