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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.testing.jacoco.tasks.JacocoReport

import com.github.spotbugs.snom.SpotBugsTask
import groovy.xml.XmlSlurper

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

    private static final Logger LOGGER = Logging.getLogger(GrailsViolationAggregationPlugin)

    @Override
    void apply(Project project) {
        if (project != project.rootProject) {
            throw new GradleException(
                'GrailsViolationAggregationPlugin must be applied to the root project only. ' +
                'Apply grails-code-style and grails-jacoco to subprojects instead.'
            )
        }

        Provider<Directory> violationsDir = project.layout.buildDirectory.dir('reports/violations')
        Provider<Directory> styleXmlDir = project.layout.buildDirectory.dir('reports/codestyle')
        Provider<Directory> analysisXmlDir = project.layout.buildDirectory.dir('reports/codeanalysis')

        TaskProvider<Task> styleTask = registerStyleAggregation(project, styleXmlDir, violationsDir)
        TaskProvider<Task> analysisTask = registerAnalysisAggregation(project, analysisXmlDir, violationsDir)
        registerJacocoAggregation(project, violationsDir)

        project.tasks.register('aggregateViolations') { Task task ->
            task.group = 'verification'
            task.description = 'Aggregates all violation reports (style + analysis) into build/reports/violations/'
            task.dependsOn(styleTask, analysisTask)
        }
    }

    private static TaskProvider<Task> registerStyleAggregation(Project root, Provider<Directory> styleXmlDir, Provider<Directory> violationsDir) {
        // Wire property flags as Providers — values are resolved at task execution time, not at apply() time,
        // and Providers are configuration-cache safe to capture in task actions
        Provider<Boolean> checkStyleTests = GradleUtils.booleanProvider(root, GrailsCodeStylePlugin.TEST_STYLING_PROPERTY)
        Provider<Boolean> codenarcEnabled = GradleUtils.booleanProvider(root, GrailsCodeStylePlugin.CODENARC_ENABLED_PROPERTY, true)
        Provider<Boolean> checkstyleEnabled = GradleUtils.booleanProvider(root, GrailsCodeStylePlugin.CHECKSTYLE_ENABLED_PROPERTY, true)

        TaskProvider<Task> aggregateTask = root.tasks.register('aggregateStyleViolations') { Task task ->
            task.group = 'verification'
            task.description = 'Aggregates CodeNarc and Checkstyle violation reports into build/reports/violations/'
            task.outputs.file(root.file('build/reports/violations/CODENARC_VIOLATIONS.md'))
            task.outputs.file(root.file('build/reports/violations/CHECKSTYLE_VIOLATIONS.md'))
            task.doLast {
                parseStyleViolations(styleXmlDir.get(), violationsDir.get(),
                    checkStyleTests.get(), codenarcEnabled.get(), checkstyleEnabled.get())
            }
        }
        root.subprojects { Project sub ->
            sub.pluginManager.withPlugin('codenarc') { AppliedPlugin p ->
                aggregateTask.configure { Task task ->
                    task.dependsOn(sub.tasks.withType(CodeNarc))
                }
            }
            sub.pluginManager.withPlugin('checkstyle') { AppliedPlugin p ->
                aggregateTask.configure { Task task ->
                    task.dependsOn(sub.tasks.withType(Checkstyle))
                }
            }
        }
        aggregateTask
    }

    private static TaskProvider<Task> registerAnalysisAggregation(Project root, Provider<Directory> analysisXmlDir, Provider<Directory> violationsDir) {
        Provider<Boolean> checkAnalysisTests = GradleUtils.booleanProvider(root, GrailsCodeAnalysisPlugin.TEST_ANALYSIS_PROPERTY)
        Provider<Boolean> pmdEnabled = GradleUtils.booleanProvider(root, GrailsCodeAnalysisPlugin.PMD_ENABLED_PROPERTY)
        Provider<Boolean> spotbugsEnabled = GradleUtils.booleanProvider(root, GrailsCodeAnalysisPlugin.SPOTBUGS_ENABLED_PROPERTY)

        TaskProvider<Task> aggregateTask = root.tasks.register('aggregateAnalysisViolations') { Task task ->
            task.group = 'verification'
            task.description = 'Aggregates PMD and SpotBugs violation reports into build/reports/violations/'
            task.outputs.file(root.file('build/reports/violations/PMD_VIOLATIONS.md'))
            task.outputs.file(root.file('build/reports/violations/SPOTBUGS_VIOLATIONS.md'))
            task.doLast {
                parseAnalysisViolations(analysisXmlDir.get(), violationsDir.get(),
                    checkAnalysisTests.get(), pmdEnabled.get(), spotbugsEnabled.get())
            }
        }
        root.subprojects { Project sub ->
            sub.pluginManager.withPlugin('pmd') { AppliedPlugin p ->
                aggregateTask.configure { Task task ->
                    task.dependsOn(sub.tasks.withType(Pmd))
                }
            }
            sub.pluginManager.withPlugin('com.github.spotbugs') { AppliedPlugin p ->
                aggregateTask.configure { Task task ->
                    task.dependsOn(sub.tasks.withType(SpotBugsTask))
                }
            }
        }
        aggregateTask
    }

    private static void registerJacocoAggregation(Project root, Provider<Directory> violationsDir) {
        // Collect all potential CSV paths at configuration time — Project must not be referenced from task actions
        FileCollection jacocoCsvFiles = root.files(
            root.allprojects.collect { Project p -> p.file('build/reports/jacoco/test/jacocoTestReport.csv') }
        )

        TaskProvider<Task> aggregateTask = root.tasks.register('aggregateJacocoCoverage') { Task task ->
            task.group = 'verification'
            task.description = 'Aggregates JaCoCo coverage reports from all subprojects into build/reports/violations/'
            task.inputs.files(jacocoCsvFiles).optional(true)
            task.outputs.file(root.file('build/reports/violations/JACOCO_COVERAGE.md'))
            task.doLast {
                parseJacocoCoverage(jacocoCsvFiles, violationsDir.get())
            }
        }
        root.subprojects { Project sub ->
            sub.pluginManager.withPlugin('jacoco') { AppliedPlugin p ->
                aggregateTask.configure { Task task ->
                    task.dependsOn(sub.tasks.withType(JacocoReport))
                }
            }
        }
    }

    @CompileDynamic
    private static void parseStyleViolations(Directory styleXmlDir, Directory violationsDir,
            boolean checkStyleTests, boolean codenarcEnabled, boolean checkstyleEnabled) {
        def slurper = new XmlSlurper()
        slurper.setFeature('http://apache.org/xml/features/nonvalidating/load-external-dtd', false)
        slurper.setFeature('http://xml.org/sax/features/namespaces', false)

        def getModule = { String fileName ->
            def lastDash = fileName.lastIndexOf('-')
            lastDash != -1 ? fileName.substring(0, lastDash) : fileName
        }

        def isTestFile = { String fileName ->
            fileName.toLowerCase().contains('test') || fileName.toLowerCase().contains('integrationtest')
        }

        def shouldSkipClass = { boolean includeTests, String className, String filePath = null ->
            if (includeTests) {
                return false
            }
            if (filePath && (filePath.contains('src/test/') || filePath.contains('src/integrationTest/'))) {
                return true
            }
            !filePath && (className.contains('Spec') || className.contains('Test') || className.contains('Tests'))
        }

        def writeReport = { String fileName, List violations, String title ->
            def outDir = violationsDir.asFile
            outDir.mkdirs()
            def reportFile = new File(outDir, fileName)
            def out = new StringBuilder()
            out.append("# ${title}\n")
            out.append("Generated on: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss'))}\n\n")

            if (violations.isEmpty()) {
                out.append('No violations found! 🎉\n')
            } else {
                def uniqueViolations = violations.unique().sort { v -> "${v.module}:${v.className}:${v.line}" }
                def groupedByModule = uniqueViolations.groupBy { it.module }.sort()
                groupedByModule.each { module, modViolations ->
                    out.append("## Module: ${module}\n")
                    out.append('| Class | Tool | Violation | Line | Message |\n')
                    out.append('| :--- | :--- | :--- | :--- | :--- |\n')
                    modViolations.each { v ->
                        out.append("| ${v.className} | ${v.tool} | ${v.type} | ${v.line} | ${v.message.replaceAll(/\|/, '\\|')} |\n")
                    }
                    out.append('\n')
                }
            }
            reportFile.text = out.toString()
            LOGGER.lifecycle("Aggregated report generated: ${reportFile.absolutePath}")
        }

        // CodeNarc
        def codenarcViolations = []
        def codenarcDir = styleXmlDir.dir('codenarc').asFile
        if (codenarcDir.exists() && codenarcEnabled) {
            codenarcDir.eachFileMatch(~/.*\.xml/) { file ->
                if (file.size() == 0 || (!checkStyleTests && isTestFile(file.name))) {
                    return
                }
                def module = getModule(file.name)
                def xml = slurper.parse(file)
                xml.Package.each { pkg ->
                    pkg.File.each { f ->
                        def pkgName = pkg.@name.text()
                        def fileName = f.@name.text()
                        def className = pkgName ? "${pkgName}.${fileName}" : fileName
                        className = className.replace('.groovy', '').replace('.java', '')
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
        writeReport('CODENARC_VIOLATIONS.md', codenarcViolations, 'CodeNarc Violations Summary')

        // Checkstyle
        def checkstyleViolations = []
        def checkstyleDir = styleXmlDir.dir('checkstyle').asFile
        if (checkstyleDir.exists() && checkstyleEnabled) {
            checkstyleDir.eachFileMatch(~/.*\.xml/) { file ->
                if (file.size() == 0 || (!checkStyleTests && isTestFile(file.name))) {
                    return
                }
                def module = getModule(file.name)
                def xml = slurper.parse(file)
                xml.file.each { f ->
                    def filePath = f.@name.text()
                    def className = filePath.contains('src/main/groovy/') ? filePath.split('src/main/groovy/')[1] :
                                    filePath.contains('src/main/java/')   ? filePath.split('src/main/java/')[1] :
                                    filePath.contains('src/test/groovy/') ? filePath.split('src/test/groovy/')[1] :
                                    filePath.contains('src/test/java/')   ? filePath.split('src/test/java/')[1] :
                                    filePath.split('/').last()
                    className = className.replace('.groovy', '').replace('.java', '').replace('/', '.')
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
        writeReport('CHECKSTYLE_VIOLATIONS.md', checkstyleViolations, 'Checkstyle Violations Summary')
    }

    @CompileDynamic
    private static void parseAnalysisViolations(Directory analysisXmlDir, Directory violationsDir,
            boolean checkAnalysisTests, boolean pmdEnabled, boolean spotbugsEnabled) {
        def slurper = new XmlSlurper()
        slurper.setFeature('http://apache.org/xml/features/nonvalidating/load-external-dtd', false)
        slurper.setFeature('http://xml.org/sax/features/namespaces', false)

        def getModule = { String fileName ->
            def lastDash = fileName.lastIndexOf('-')
            lastDash != -1 ? fileName.substring(0, lastDash) : fileName
        }

        def isTestFile = { String fileName ->
            fileName.toLowerCase().contains('test') || fileName.toLowerCase().contains('integrationtest')
        }

        def shouldSkipClass = { boolean includeTests, String className ->
            if (includeTests) {
                return false
            }
            className.contains('Spec') || className.contains('Test') || className.contains('Tests')
        }

        def writeReport = { String fileName, List violations, String title ->
            def outDir = violationsDir.asFile
            outDir.mkdirs()
            def reportFile = new File(outDir, fileName)
            def out = new StringBuilder()
            out.append("# ${title}\n")
            out.append("Generated on: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss'))}\n\n")

            if (violations.isEmpty()) {
                out.append('No violations found! 🎉\n')
            } else {
                def uniqueViolations = violations.unique().sort { v -> "${v.module}:${v.className}:${v.line}" }
                def groupedByModule = uniqueViolations.groupBy { it.module }.sort()
                groupedByModule.each { module, modViolations ->
                    out.append("## Module: ${module}\n")
                    out.append('| Class | Tool | Violation | Line | Message |\n')
                    out.append('| :--- | :--- | :--- | :--- | :--- |\n')
                    modViolations.each { v ->
                        out.append("| ${v.className} | ${v.tool} | ${v.type} | ${v.line} | ${v.message.replaceAll(/\|/, '\\|')} |\n")
                    }
                    out.append('\n')
                }
            }
            reportFile.text = out.toString()
            LOGGER.lifecycle("Aggregated report generated: ${reportFile.absolutePath}")
        }

        // PMD
        def pmdViolations = []
        def pmdDir = analysisXmlDir.dir('pmd').asFile
        if (pmdDir.exists() && pmdEnabled) {
            pmdDir.eachFileMatch(~/.*\.xml/) { file ->
                if (file.size() == 0 || (!checkAnalysisTests && isTestFile(file.name))) {
                    return
                }
                def module = getModule(file.name)
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
        writeReport('PMD_VIOLATIONS.md', pmdViolations, 'PMD Violations Summary')

        // SpotBugs
        def spotbugsViolations = []
        def spotbugsDir = analysisXmlDir.dir('spotbugs').asFile
        if (spotbugsDir.exists() && spotbugsEnabled) {
            spotbugsDir.eachFileMatch(~/.*\.xml/) { file ->
                if (file.size() == 0 || (!checkAnalysisTests && isTestFile(file.name))) {
                    return
                }
                def module = getModule(file.name)
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
        writeReport('SPOTBUGS_VIOLATIONS.md', spotbugsViolations, 'SpotBugs Violations Summary')
    }

    @CompileDynamic
    private static void parseJacocoCoverage(FileCollection csvFiles, Directory violationsDir) {
        def jacocoCoverage = []
        csvFiles.each { File csvReport ->
            if (csvReport.exists()) {
                LOGGER.debug("Processing JaCoCo report: ${csvReport.absolutePath}")
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

        jacocoCoverage.removeIf { it.className.startsWith('org.grails.orm.hibernate.support.hibernate7.') }

        def outDir = violationsDir.asFile
        outDir.mkdirs()
        def reportFile = new File(outDir, 'JACOCO_COVERAGE.md')
        def out = new StringBuilder()
        out.append('# JaCoCo Coverage Report\n')
        out.append("Generated on: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss'))}\n\n")

        def groupedByModule = jacocoCoverage.groupBy { it.module }.sort()
        groupedByModule.each { module, coverageList ->
            out.append("## Module: ${module}\n")
            out.append('| Class | % Instructions Covered |\n')
            out.append('| :--- | :--- |\n')
            coverageList.sort { it.percent }.each { c ->
                out.append("| ${c.className} | ${c.percent}% |\n")
            }
            out.append('\n')
        }
        reportFile.text = out.toString()
        LOGGER.lifecycle("Aggregated JaCoCo report generated: ${reportFile.absolutePath}")
    }
}
