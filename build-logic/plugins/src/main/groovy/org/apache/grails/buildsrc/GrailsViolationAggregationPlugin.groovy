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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.github.spotbugs.snom.SpotBugsTask
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.testing.jacoco.tasks.JacocoReport

/**
 * Root-only convention plugin that aggregates code-style violation XML reports and JaCoCo coverage
 * CSV reports into human-readable Markdown files under build/reports/violations/.
 *
 * Apply this plugin to the root project only. Subprojects should apply
 * grails-code-style and grails-jacoco individually.
 *
 * Tasks registered:
 *   aggregateStyleViolations    — CodeNarc + Checkstyle only
 *   aggregateAnalysisViolations — PMD + SpotBugs only (requires opt-in properties)
 *   aggregateViolations         — depends on both of the above
 *   aggregateJacocoCoverage     — JaCoCo CSV → Markdown
 */
@CompileStatic
class GrailsViolationAggregationPlugin implements Plugin<Project> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')
    private static final Logger LOGGER = Logging.getLogger(GrailsViolationAggregationPlugin)

    /**
     * Comma-separated list of fully-qualified class-name prefixes to exclude from the aggregated
     * JaCoCo coverage report. Configure via {@code -Pgrails.jacoco.aggregation.excludedClassPrefixes=...}
     * or in {@code gradle.properties}.
     *
     * <p>Defaults to {@link #DEFAULT_JACOCO_EXCLUDED_CLASS_PREFIXES}: the Hibernate 7 support classes
     * share fully-qualified names with their Hibernate 5 counterparts, and JaCoCo cannot aggregate
     * coverage for two different classes with the same name (it fails with
     * "Can't add different class with same name"). Excluding one variant keeps the aggregate valid.
     */
    static final String JACOCO_EXCLUDED_CLASS_PREFIXES_PROPERTY = 'grails.jacoco.aggregation.excludedClassPrefixes'

    static final String DEFAULT_JACOCO_EXCLUDED_CLASS_PREFIXES = 'org.grails.orm.hibernate.support.hibernate7.'

    @Override
    void apply(Project project) {
        if (project != project.rootProject) {
            throw new GradleException(
                'GrailsViolationAggregationPlugin must be applied to the root project only. ' +
                'Apply grails-code-style and grails-jacoco to subprojects instead.'
            )
        }

        def violationsDir = project.layout.buildDirectory.dir('reports/violations')
        TaskProvider<RepositoryConventionsTask> repositoryConventionsTask = project.file('.asf.yaml').isFile() &&
                GradleUtils.findRootGrailsCoreDir(project).asFile == project.projectDir ?
                registerRepositoryConventions(project, violationsDir) : null
        def styleTask = registerStyleAggregation(project, violationsDir)
        def analysisTask = registerAnalysisAggregation(project, violationsDir)
        registerJacocoAggregation(project, violationsDir)

        project.tasks.register('aggregateViolations') { Task task ->
            task.group = 'verification'
            task.description = 'Aggregates all violation reports (style + analysis) into build/reports/violations/'
            task.dependsOn(styleTask, analysisTask)
            if (repositoryConventionsTask) {
                task.dependsOn(repositoryConventionsTask)
                task.dependsOn(project.tasks.named { String name -> name == 'rat' })
            }
        }
    }

    private static TaskProvider<RepositoryConventionsTask> registerRepositoryConventions(Project root, Provider<Directory> violationsDir) {
        root.tasks.register('validateRepositoryConventions', RepositoryConventionsTask) { RepositoryConventionsTask task ->
            task.group = 'verification'
            task.description = 'Validates repository conventions and writes build/reports/violations/REPOSITORY_CONVENTIONS.md'
            task.repositoryDirectory.set(root.layout.projectDirectory)
            List<String> buildOutputExcludes = root.allprojects.collect { Project sub ->
                root.layout.projectDirectory.asFile.toPath()
                        .relativize(sub.layout.buildDirectory.get().asFile.toPath())
                        .toString().replace(File.separator, '/') + '/**'
            }
            task.conventionSources.from(
                    root.file('AGENTS.md'),
                    root.fileTree('.agents/skills') { include '*/SKILL.md' },
                    root.fileTree('.github/workflows') { include '**/*.yml', '**/*.yaml' },
                    root.fileTree('.') {
                        include '**/action.yml', '**/action.yaml'
                        exclude(buildOutputExcludes)
                        exclude '**/.gradle/**', '**/.git/**', '**/.hg/**', '**/.svn/**'
                    },
                    root.fileTree('.') {
                        include '**/grails-app/i18n/**/*.properties'
                        exclude(buildOutputExcludes)
                        exclude '**/.gradle/**', '**/.git/**', '**/.hg/**', '**/.svn/**'
                    }
            )
            task.reportFile.set(violationsDir.map { it.file('REPOSITORY_CONVENTIONS.md') })
            task.outputs.upToDateWhen { false }
            task.mustRunAfter(root.tasks.named { String name -> name == 'rat' })
        }
    }

    private static TaskProvider<Task> registerStyleAggregation(Project root, Provider<Directory> violationsDir) {
        Directory rootDirectory = root.layout.projectDirectory
        def checkStyleTests = GradleUtils.booleanProvider(root, GrailsCodeStylePlugin.TEST_STYLING_PROPERTY)
        def ignoreFailures = GradleUtils.booleanProvider(root, GrailsCodeStylePlugin.IGNORE_FAILURES_PROPERTY)
        def codenarcEnabled = GradleUtils.booleanProvider(root, GrailsCodeStylePlugin.CODENARC_ENABLED_PROPERTY, true)
        def checkstyleEnabled = GradleUtils.booleanProvider(root, GrailsCodeStylePlugin.CHECKSTYLE_ENABLED_PROPERTY, true)
        def codenarcMarkers = root.files()
        def checkstyleMarkers = root.files()
        def codenarcMarkdown = root.layout.buildDirectory.file('reports/violations/CODENARC_VIOLATIONS.md')
        def checkstyleMarkdown = root.layout.buildDirectory.file('reports/violations/CHECKSTYLE_VIOLATIONS.md')
        def cleanupTask = root.tasks.register('cleanAggregateStyleReports') {
            it.doLast {
                deleteReports(codenarcMarkers.files)
                deleteReports(checkstyleMarkers.files)
            }
        }

        def writerTask = root.tasks.register('writeStyleViolations') {
            it.group = 'verification'
            it.description = 'Writes CodeNarc and Checkstyle violation reports into build/reports/violations/'
            it.inputs.files(codenarcMarkers).optional()
            it.inputs.files(checkstyleMarkers).optional()
            it.outputs.file(codenarcMarkdown)
            it.outputs.file(checkstyleMarkdown)
            it.outputs.upToDateWhen { false }
            it.doFirst {
                codenarcMarkdown.get().asFile.delete()
                checkstyleMarkdown.get().asFile.delete()
            }
            it.doLast {
                parseStyleViolations(codenarcMarkers.files, checkstyleMarkers.files, rootDirectory, violationsDir.get(),
                    checkStyleTests.get(), codenarcEnabled.get(),
                    checkstyleEnabled.get())
            }
        }
        def aggregateTask = root.tasks.register('aggregateStyleViolations') {
            it.group = 'verification'
            it.description = 'Aggregates CodeNarc and Checkstyle violations into build/reports/violations/'
            it.dependsOn(writerTask)
        }
        root.allprojects { Project sub ->
            def codenarcTasks = sub.tasks.withType(CodeNarc)
            aggregateTask.configure {
                it.dependsOn(codenarcTasks)
                it.dependsOn(cleanupTask)
            }
            writerTask.configure { it.mustRunAfter(codenarcTasks) }
            codenarcTasks.configureEach { CodeNarc codeNarcTask ->
                codenarcMarkers.from(GradleUtils.reportMarker(sub, 'codenarc', codeNarcTask.name))
                codeNarcTask.mustRunAfter(cleanupTask)
            }
            def checkstyleTasks = sub.tasks.withType(Checkstyle)
            aggregateTask.configure {
                it.dependsOn(checkstyleTasks)
                it.dependsOn(cleanupTask)
            }
            writerTask.configure { it.mustRunAfter(checkstyleTasks) }
            checkstyleTasks.configureEach { Checkstyle checkstyleTask ->
                checkstyleMarkers.from(GradleUtils.reportMarker(sub, 'checkstyle', checkstyleTask.name))
                checkstyleTask.mustRunAfter(cleanupTask)
            }
        }
        aggregateTask
    }

    private static TaskProvider<Task> registerAnalysisAggregation(Project root, Provider<Directory> violationsDir) {
        Directory rootDirectory = root.layout.projectDirectory
        def checkAnalysisTests = GradleUtils.booleanProvider(root, GrailsCodeAnalysisPlugin.TEST_ANALYSIS_PROPERTY)
        def ignoreFailures = GradleUtils.booleanProvider(root, GrailsCodeAnalysisPlugin.IGNORE_FAILURES_PROPERTY)
        def pmdEnabled = GradleUtils.booleanProvider(root, GrailsCodeAnalysisPlugin.PMD_ENABLED_PROPERTY)
        def pmdConfiguredProjectPaths = root.providers.gradleProperty(GrailsCodeAnalysisPlugin.PMD_ENABLED_PROJECTS_PROPERTY)
                .map { configuredProjectPaths(it) }
                .orElse([])
        def spotbugsEnabled = GradleUtils.booleanProvider(root, GrailsCodeAnalysisPlugin.SPOTBUGS_ENABLED_PROPERTY)
        def spotbugsConfiguredProjectPaths = root.providers.gradleProperty(GrailsCodeAnalysisPlugin.SPOTBUGS_ENABLED_PROJECTS_PROPERTY)
                .map { configuredProjectPaths(it) }
                .orElse([])
        List<String> knownProjectPaths = root.allprojects.findAll { Project candidate -> candidate != root }
                .collect { Project candidate -> candidate.path }.sort()
        def pmdMarkers = root.files()
        def spotbugsMarkers = root.files()
        Set<String> pmdEnabledProjectPaths = [] as Set<String>
        Set<String> spotbugsEnabledProjectPaths = [] as Set<String>
        root.allprojects { Project sub ->
            sub.pluginManager.withPlugin('pmd') {
                pmdEnabledProjectPaths << sub.path
            }
            sub.pluginManager.withPlugin('com.github.spotbugs') {
                spotbugsEnabledProjectPaths << sub.path
            }
        }
        def activePmdProjectPaths = root.providers.provider { pmdEnabledProjectPaths.toList().sort() }
        def activeSpotbugsProjectPaths = root.providers.provider { spotbugsEnabledProjectPaths.toList().sort() }
        def pmdMarkdown = root.layout.buildDirectory.file('reports/violations/PMD_VIOLATIONS.md')
        def spotbugsMarkdown = root.layout.buildDirectory.file('reports/violations/SPOTBUGS_VIOLATIONS.md')
        def cleanupTask = root.tasks.register('cleanAggregateAnalysisReports') {
            it.doLast {
                deleteReports(pmdMarkers.files)
                deleteReports(spotbugsMarkers.files)
            }
        }

        def writerTask = root.tasks.register('writeAnalysisViolations') {
            it.group = 'verification'
            it.description = 'Writes PMD and SpotBugs violation reports into build/reports/violations/'
            it.inputs.files(pmdMarkers).optional()
            it.inputs.files(spotbugsMarkers).optional()
            it.inputs.property('ignoreFailures', ignoreFailures)
            it.inputs.property('pmdEnabled', pmdEnabled)
            it.inputs.property('pmdEnabledProjectPaths', activePmdProjectPaths)
            it.inputs.property('spotbugsEnabled', spotbugsEnabled)
            it.inputs.property('spotbugsEnabledProjectPaths', activeSpotbugsProjectPaths)
            it.inputs.property('knownProjectPaths', knownProjectPaths)
            it.outputs.file(pmdMarkdown)
            it.outputs.file(spotbugsMarkdown)
            it.outputs.upToDateWhen { false }
            it.doLast {
                parseAnalysisViolations(pmdMarkers.files, spotbugsMarkers.files, rootDirectory, violationsDir.get(),
                    checkAnalysisTests.get(), pmdEnabled.get(), activePmdProjectPaths.get(),
                    spotbugsEnabled.get(), activeSpotbugsProjectPaths.get(), ignoreFailures.get())
            }
            it.doFirst {
                pmdMarkdown.get().asFile.delete()
                spotbugsMarkdown.get().asFile.delete()
                validateConfiguredProjectPaths(GrailsCodeAnalysisPlugin.PMD_ENABLED_PROJECTS_PROPERTY,
                        pmdConfiguredProjectPaths.get(), knownProjectPaths)
                validateConfiguredProjectPaths(GrailsCodeAnalysisPlugin.SPOTBUGS_ENABLED_PROJECTS_PROPERTY,
                        spotbugsConfiguredProjectPaths.get(), knownProjectPaths)
            }
        }
        def aggregateTask = root.tasks.register('aggregateAnalysisViolations') {
            it.group = 'verification'
            it.description = 'Aggregates PMD and SpotBugs violations into build/reports/violations/'
            it.dependsOn(writerTask)
        }
        root.allprojects { Project sub ->
            def pmdTasks = sub.tasks.withType(Pmd)
            aggregateTask.configure {
                it.dependsOn(pmdTasks)
                it.dependsOn(cleanupTask)
            }
            writerTask.configure { it.mustRunAfter(pmdTasks) }
            pmdTasks.configureEach { Pmd pmdTask ->
                pmdMarkers.from(GradleUtils.reportMarker(sub, 'pmd', pmdTask.name))
                pmdTask.mustRunAfter(cleanupTask)
            }
            def spotbugsTasks = sub.tasks.withType(SpotBugsTask)
            aggregateTask.configure {
                it.dependsOn(spotbugsTasks)
                it.dependsOn(cleanupTask)
            }
            writerTask.configure { it.mustRunAfter(spotbugsTasks) }
            spotbugsTasks.configureEach { SpotBugsTask spotbugsTask ->
                spotbugsMarkers.from(GradleUtils.reportMarker(sub, 'spotbugs', spotbugsTask.name))
                spotbugsTask.mustRunAfter(cleanupTask)
            }
        }
        aggregateTask
    }

    private static List<String> configuredProjectPaths(String value) {
        value.split(',')*.trim().findAll()
    }

    private static void validateConfiguredProjectPaths(String property, List<String> configuredPaths, List<String> knownPaths) {
        List<String> unknownPaths = configuredPaths.findAll { String path -> !knownPaths.contains(path) }.sort()
        if (!unknownPaths.isEmpty()) {
            throw new GradleException("Unknown project path(s) in ${property}: ${unknownPaths.join(', ')}. Run './gradlew projects' to list valid subproject paths.")
        }
    }

    private static void registerJacocoAggregation(Project root, Provider<Directory> violationsDir) {
        // Collect all potential CSV paths at configuration time — Project must not be referenced from task actions
        def jacocoCsvFiles = root.files(
            root.allprojects.collect { it.file('build/reports/jacoco/test/jacocoTestReport.csv') }
        )

        // Resolve the excluded class-name prefixes as a Provider so the value is captured
        // configuration-cache-safely and read at task execution time.
        def excludedClassPrefixes = root.providers
            .gradleProperty(JACOCO_EXCLUDED_CLASS_PREFIXES_PROPERTY)
            .orElse(DEFAULT_JACOCO_EXCLUDED_CLASS_PREFIXES)
            .map {
                it.split(',')*.trim().findAll()
            }

        def aggregateTask = root.tasks.register('aggregateJacocoCoverage') {
            it.group = 'verification'
            it.description = 'Aggregates JaCoCo coverage reports from all subprojects into build/reports/violations/'
            it.inputs.files(jacocoCsvFiles).optional(true)
            it.inputs.property('excludedClassPrefixes', excludedClassPrefixes)
            it.outputs.file(root.file('build/reports/violations/JACOCO_COVERAGE.md'))
            it.doLast {
                parseJacocoCoverage(jacocoCsvFiles, violationsDir.get(), excludedClassPrefixes.get())
            }
        }
        root.subprojects { Project sub ->
            sub.pluginManager.withPlugin('jacoco') {
                aggregateTask.configure {
                    it.dependsOn(sub.tasks.withType(JacocoReport))
                }
            }
        }
    }

    private static XmlSlurper createSecureSlurper() {
        new XmlSlurper().tap {
            setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
            setFeature('http://apache.org/xml/features/nonvalidating/load-external-dtd', false)
            setFeature('http://xml.org/sax/features/external-general-entities', false)
            setFeature('http://xml.org/sax/features/external-parameter-entities', false)
            setFeature('http://xml.org/sax/features/namespaces', false)
        }
    }

    private static String resolveModule(String fileName) {
        int separator = fileName.indexOf('-')
        separator > 0 ? GradleUtils.projectPathFromKey(fileName.substring(0, separator)) : fileName
    }

    private static boolean isTestFile(String fileName) {
        fileName.toLowerCase().contains('test') || fileName.toLowerCase().contains('integrationtest')
    }

    @CompileDynamic
    private static void writeReport(Directory violationsDir, String fileName, List violations, String title,
            Collection<String> modules, String disabledMessage = null) {
        def reportFile = new File(
                violationsDir.asFile.tap { it.mkdirs() },
                fileName
        )
        def text = new StringBuilder()
        text.append("# ${title}\n")
        text.append("Generated on: ${LocalDateTime.now().format(TIMESTAMP_FORMAT)}\n\n")
        text.append("Modules analyzed: ${modules.isEmpty() ? 'none' : modules.toList().sort().join(', ')}\n\n")

        if (disabledMessage) {
            text.append("${disabledMessage}\n")
        } else if (violations.isEmpty()) {
            text.append('No violations found! 🎉\n')
        } else {
            def uniqueViolations = violations.unique().sort { v -> "${v.module}:${v.className}:${v.line}" }
            def groupedByModule = uniqueViolations.groupBy { it.module }.sort()
            groupedByModule.each { module, modViolations ->
                text.append("## Module: ${module}\n")
                text.append('| Class | Tool | Violation | Line | Message |\n')
                text.append('| :--- | :--- | :--- | :--- | :--- |\n')
                modViolations.each { v ->
                    text.append("| ${v.className} | ${v.tool} | ${v.type} | ${v.line} | ${v.message.replaceAll(/\|/, '\\|')} |\n")
                }
                text.append('\n')
            }
        }
        reportFile.text = text
        LOGGER.lifecycle('Aggregated report generated: {}', reportFile.absolutePath)
    }

    @CompileDynamic
    private static void parseStyleViolations(Set<File> codenarcMarkers, Set<File> checkstyleMarkers, Directory rootDirectory,
            Directory violationsDir, boolean checkStyleTests,
            boolean codenarcEnabled, boolean checkstyleEnabled) {
        def slurper = createSecureSlurper()
        def missingReports = []

        def shouldSkipClass = { boolean includeTests, String className, String filePath = null ->
            if (includeTests) {
                return false
            }
            if (filePath && (filePath.contains('src/test/') || filePath.contains('src/integrationTest/'))) {
                return true
            }
            !filePath && (className.contains('Spec') || className.contains('Test') || className.contains('Tests'))
        }

        // CodeNarc
        def codenarcViolations = []
        Set<String> codenarcModules = [] as Set<String>
        if (codenarcEnabled) {
            codenarcMarkers.each { File marker ->
                if (!marker.exists() || (!checkStyleTests && isTestFile(marker.name))) {
                    return
                }
                codenarcModules << resolveModule(marker.name)
                File file = reportForMarker(marker, rootDirectory)
                if (!file || !file.exists() || file.size() == 0) {
                    def violation = missingReportViolation(resolveModule(marker.name), 'CodeNarc')
                    codenarcViolations << violation
                    missingReports << violation
                    return
                }
                def module = resolveModule(marker.name)
                def xml = slurper.parse(file)
                xml.Package.each { pkg ->
                    pkg.File.each { f ->
                        def pkgName = pkg.@name.text()
                        def fileName = f.@name.text()
                        def className = pkgName ? "${pkgName}.${fileName}" : fileName
                        className = className
                                .replace('.groovy', '')
                                .replace('.java', '')
                        if (shouldSkipClass(checkStyleTests, className, f.@name.text())) {
                            return
                        }
                        f.Violation.each { v ->
                            codenarcViolations << [
                                    module   : module,
                                    className: className,
                                    tool     : 'CodeNarc',
                                    type     : v.@ruleName.text(),
                                    line     : v.@lineNumber.text(),
                                    message  : v.Message.text().trim()
                            ]
                        }
                    }
                }
            }
        }
        writeReport(violationsDir, 'CODENARC_VIOLATIONS.md', codenarcViolations, 'CodeNarc Violations Summary', codenarcModules)

        // Checkstyle
        def checkstyleViolations = []
        Set<String> checkstyleModules = [] as Set<String>
        if (checkstyleEnabled) {
            checkstyleMarkers.each { File marker ->
                if (!marker.exists() || (!checkStyleTests && isTestFile(marker.name))) {
                    return
                }
                checkstyleModules << resolveModule(marker.name)
                File file = reportForMarker(marker, rootDirectory)
                if (!file || !file.exists() || file.size() == 0) {
                    def violation = missingReportViolation(resolveModule(marker.name), 'Checkstyle')
                    checkstyleViolations << violation
                    missingReports << violation
                    return
                }
                def module = resolveModule(marker.name)
                def xml = slurper.parse(file)
                xml.file.each { f ->
                    String filePath = f.@name.text()
                    def className = filePath.contains('src/main/groovy/') ? filePath.split('src/main/groovy/')[1] :
                                    filePath.contains('src/main/java/')   ? filePath.split('src/main/java/')[1] :
                                    filePath.contains('src/test/groovy/') ? filePath.split('src/test/groovy/')[1] :
                                    filePath.contains('src/test/java/')   ? filePath.split('src/test/java/')[1] :
                                    filePath.split('/').last()
                    className = className
                            .replace('.groovy', '')
                            .replace('.java', '')
                            .replace('/', '.')
                    if (shouldSkipClass(checkStyleTests, className)) {
                        return
                    }
                    f.error.each { e ->
                        checkstyleViolations << [
                                module   : module,
                                className: className,
                                tool     : 'Checkstyle',
                                type     : e.@source.text().split(/\./).last(),
                                line     : e.@line.text(),
                                message  : e.@message.text().trim()
                        ]
                    }
                }
            }
        }
        writeReport(violationsDir, 'CHECKSTYLE_VIOLATIONS.md', checkstyleViolations, 'Checkstyle Violations Summary', checkstyleModules)
        if (!missingReports.isEmpty()) {
            throw new GradleException('Expected style XML reports were not generated. See build/reports/violations/ for details.')
        }
    }

    @CompileDynamic
    private static void parseAnalysisViolations(Set<File> pmdMarkers, Set<File> spotbugsMarkers, Directory rootDirectory,
            Directory violationsDir, boolean checkAnalysisTests, boolean pmdEnabled,
            List<String> pmdEnabledProjects, boolean spotbugsEnabled, List<String> spotbugsEnabledProjects,
            boolean ignoreFailures) {
        def slurper = createSecureSlurper()
        def missingReports = []
        boolean pmdEnabledForAnyProject = pmdEnabled || !pmdEnabledProjects.isEmpty()
        boolean spotbugsEnabledForAnyProject = spotbugsEnabled || !spotbugsEnabledProjects.isEmpty()

        def shouldSkipClass = { boolean includeTests, String className ->
            if (includeTests) {
                return false
            }
            className.contains('Spec') || className.contains('Test') || className.contains('Tests')
        }

        // PMD
        def pmdViolations = []
        Set<String> pmdModules = [] as Set<String>
        if (pmdEnabledForAnyProject) {
            pmdMarkers.each { File marker ->
                if (!marker.exists() || (!checkAnalysisTests && isTestFile(marker.name))) {
                    return
                }
                def module = resolveModule(marker.name)
                pmdModules << module
                File file = reportForMarker(marker, rootDirectory)
                if (!file || !file.exists() || file.size() == 0) {
                    def violation = missingReportViolation(module, 'PMD')
                    pmdViolations << violation
                    missingReports << violation
                } else {
                    def xml = slurper.parse(file)
                    xml.file.each { f ->
                        f.violation.each { v ->
                            def className = "${v.@package}.${v.@class}"
                            if (shouldSkipClass(checkAnalysisTests, className)) {
                                return
                            }
                            pmdViolations << [
                                    module   : module,
                                    className: className,
                                    tool     : 'PMD',
                                    type     : v.@rule.text(),
                                    line     : v.@beginline.text(),
                                    message  : v.text().trim()
                            ]
                        }
                    }
                }
            }
        }
        writeReport(violationsDir, 'PMD_VIOLATIONS.md', pmdViolations, 'PMD Violations Summary', pmdModules,
                pmdEnabledForAnyProject ? null : 'PMD is disabled.')

        // SpotBugs
        def spotbugsViolations = []
        Set<String> spotbugsModules = [] as Set<String>
        if (spotbugsEnabledForAnyProject) {
            spotbugsMarkers.each { File marker ->
                if (!marker.exists() || (!checkAnalysisTests && isTestFile(marker.name))) {
                    return
                }
                def module = resolveModule(marker.name)
                spotbugsModules << module
                File file = reportForMarker(marker, rootDirectory)
                if (!file || !file.exists() || file.size() == 0) {
                    def violation = missingReportViolation(module, 'SpotBugs')
                    spotbugsViolations << violation
                    missingReports << violation
                } else {
                    def xml = slurper.parse(file)
                    xml.BugInstance.each { b ->
                        def className = b.Class.@classname.text()
                        if (shouldSkipClass(checkAnalysisTests, className)) {
                            return
                        }
                        spotbugsViolations << [
                                module   : module,
                                className: className,
                                tool     : 'SpotBugs',
                                type     : b.@type.text(),
                                line     : b.SourceLine.@start.text(),
                                message  : b.LongMessage.text().trim()
                        ]
                    }
                }
            }
        }
        writeReport(violationsDir, 'SPOTBUGS_VIOLATIONS.md', spotbugsViolations, 'SpotBugs Violations Summary', spotbugsModules,
                spotbugsEnabledForAnyProject ? null : 'SpotBugs is disabled.')
        boolean hasFindings = (pmdViolations + spotbugsViolations).any { it.type != 'MissingReport' }
        if (!missingReports.isEmpty() || (!ignoreFailures && hasFindings)) {
            throw new GradleException('Code analysis violations were found. See build/reports/violations/ for details.')
        }
    }

    private static Map<String, String> missingReportViolation(String module, String tool) {
        [
                module   : module,
                className: '',
                tool     : tool,
                type     : 'MissingReport',
                line     : '',
                message  : 'Expected XML report was not generated.'
        ]
    }

    private static File reportForMarker(File marker, Directory rootDirectory) {
        String relativeReportPath = marker.text.trim()
        if (!relativeReportPath || new File(relativeReportPath).absolute) {
            return null
        }
        new File(rootDirectory.asFile, relativeReportPath)
    }

    private static void deleteReports(Set<File> markers) {
        markers.each { File marker ->
            marker.delete()
        }
    }

    @CompileDynamic
    private static void parseJacocoCoverage(FileCollection csvFiles, Directory violationsDir, List<String> excludedClassPrefixes) {
        def jacocoCoverage = []
        csvFiles.each { File csvReport ->
            if (csvReport.exists()) {
                LOGGER.debug('Processing JaCoCo report: {}', csvReport.absolutePath)
                csvReport.splitEachLine(',') { fields ->
                    if (fields.size() < 5 || fields[0] == 'GROUP') {
                        return
                    }
                    def module = fields[0]
                    def pkg = fields[1]
                    def clazz = fields[2]
                    def missedStr = fields[3]
                    def coveredStr = fields[4]

                    if (missedStr.isNumber() && coveredStr.isNumber()) {
                        def m = missedStr.toInteger()
                        def c = coveredStr.toInteger()
                        def total = m + c
                        def percent = total > 0 ? (c * 100 / total).round(2) : 100.0

                        jacocoCoverage << [
                                module   : module,
                                className: "${pkg}.${clazz}",
                                percent  : percent
                        ]
                    }
                }
            }
        }

        if (jacocoCoverage.isEmpty()) {
            LOGGER.info('No JaCoCo coverage reports found to aggregate')
            return
        }

        // Drop classes whose fully-qualified names collide across Hibernate variants (see
        // JACOCO_EXCLUDED_CLASS_PREFIXES_PROPERTY) so the aggregate report stays valid.
        if (excludedClassPrefixes) {
            jacocoCoverage.removeIf { entry -> excludedClassPrefixes.any { prefix -> entry.className.startsWith(prefix) } }
        }

        def reportFile = new File(
                violationsDir.asFile.tap { it.mkdirs() },
                'JACOCO_COVERAGE.md'
        )
        def text = new StringBuilder()
        text.append('# JaCoCo Coverage Report\n')
        text.append("Generated on: ${LocalDateTime.now().format(TIMESTAMP_FORMAT)}\n\n")

        def groupedByModule = jacocoCoverage.groupBy { it.module }.sort()
        groupedByModule.each { module, coverageList ->
            text.append("## Module: ${module}\n")
            text.append('| Class | % Instructions Covered |\n')
            text.append('| :--- | :--- |\n')
            coverageList.sort { it.percent }.each { c ->
                text.append("| ${c.className} | ${c.percent}% |\n")
            }
            text.append('\n')
        }
        reportFile.text = text
        LOGGER.lifecycle('Aggregated JaCoCo report generated: {}', reportFile.absolutePath)
    }
}
