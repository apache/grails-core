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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult

import com.diffplug.gradle.spotless.SpotlessTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
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
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

/**
 * Convention plugin for Grails code style enforcement.
 */
@CompileDynamic
class GrailsCodeStylePlugin implements Plugin<Project> {

    static String CHECKSTYLE_DIR_PROPERTY = 'grails.codestyle.dir.checkstyle'
    static String CHECKSTYLE_ENABLED_PROPERTY = 'grails.codestyle.enabled.checkstyle'
    static String CHECKSTYLE_CONFIG_FILE_NAME = 'checkstyle.xml'
    static String CHECKSTYLE_SUPPRESSION_CONFIG_FILE_NAME = 'checkstyle-suppressions.xml'

    static String SPOTLESS_DIR_PROPERTY = 'grails.codestyle.dir.spotless'
    static String SPOTLESS_ENABLED_PROPERTY = 'grails.codestyle.enabled.spotless'
    static String SPOTLESS_GRECLIPSE_CONFIG_FILE_NAME = 'greclipse.properties'

    static String PMD_DIR_PROPERTY = 'grails.codestyle.dir.pmd'
    static String PMD_ENABLED_PROPERTY = 'grails.codestyle.enabled.pmd'
    static String PMD_CONFIG_FILE_NAME = 'pmd.xml'

    static String CODENARC_DIR_PROPERTY = 'grails.codestyle.dir.codenarc'
    static String CODENARC_ENABLED_PROPERTY = 'grails.codestyle.enabled.codenarc'
    static String CODENARC_CONFIG_FILE_NAME = 'codenarc.groovy'

    static String CODENARC_FIX_PROPERTY = 'grails.codestyle.codenarc.fix'

    static String SPOTBUGS_ENABLED_PROPERTY = 'grails.codestyle.enabled.spotbugs'

    static String JACOCO_ENABLED_PROPERTY = 'grails.codestyle.enabled.jacoco'

    static String IGNORE_FAILURES_PROPERTY = 'grails.codestyle.ignoreFailures'

    static String TEST_STYLING_PROPERTY = 'grails.codestyle.enabled.tests'

    static String BASE_RESOURCE_PATH = '/META-INF/org.apache.grails.buildsrc.codestyle'

    @Override
    void apply(Project project) {
        initExtension(project)
        configureCodeStyle(project)
        configureAggregation(project)
        
        boolean jacocoEnabled = GradleUtils.lookupProperty(project, JACOCO_ENABLED_PROPERTY, false)
        if (jacocoEnabled) {
            configureJacoco(project)
            if (project == project.rootProject) {
                project.logger.info("JaCoCo enabled globally, applying to subprojects")
                project.subprojects.each { subproject ->
                    subproject.pluginManager.withPlugin('java') {
                        configureJacoco(subproject)
                    }
                    subproject.pluginManager.withPlugin('groovy') {
                        configureJacoco(subproject)
                    }
                }
            }
        }
    }

    static void configureJacoco(Project project) {
        project.logger.info("Configuring JaCoCo for project: ${project.name}")
        project.pluginManager.apply(JacocoPlugin)

        project.extensions.configure(JacocoPluginExtension) {
            it.toolVersion = "0.8.14"
        }

        project.tasks.withType(Test).configureEach {
            it.finalizedBy 'jacocoTestReport'
        }

        project.tasks.withType(JacocoReport).configureEach {
            it.dependsOn project.tasks.withType(Test)
            it.reports {
                it.xml.required = true
                it.html.required = true
                it.csv.required = true
            }
        }
    }

    private static void configureAggregation(Project project) {
        Project root = project.rootProject
        if (root.tasks.findByName('aggregateStyleViolations')) {
            return
        }

        root.tasks.register('aggregateStyleViolations') { task ->
            task.group = 'verification'
            task.description = 'Aggregates all code style violations into separate reports'

            boolean checkTests = GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)
            boolean jacocoEnabled = GradleUtils.lookupProperty(project, JACOCO_ENABLED_PROPERTY, false)

            // Dependencies: all check tasks in all subprojects
            root.subprojects.each { subproject ->
                // CodeNarc (prod only unless test styling is enabled)
                task.dependsOn(subproject.tasks.withType(CodeNarc).matching { t ->
                    checkTests || (!t.name.toLowerCase().contains('test') && !t.name.toLowerCase().contains('integrationtest'))
                })

                // Checkstyle (prod only unless test styling is enabled)
                task.dependsOn(subproject.tasks.withType(Checkstyle).matching { t ->
                    checkTests || (!t.name.toLowerCase().contains('test') && !t.name.toLowerCase().contains('integrationtest'))
                })

                if (GradleUtils.lookupProperty(project, PMD_ENABLED_PROPERTY, false)) {
                    task.dependsOn(subproject.tasks.withType(Pmd).matching { t ->
                        checkTests || (!t.name.toLowerCase().contains('test') && !t.name.toLowerCase().contains('integrationtest'))
                    })
                }

                if (GradleUtils.lookupProperty(project, SPOTBUGS_ENABLED_PROPERTY, false)) {
                    task.dependsOn(subproject.tasks.withType(SpotBugsTask).matching { t ->
                        checkTests || (!t.name.toLowerCase().contains('test') && !t.name.toLowerCase().contains('integrationtest'))
                    })
                }

                if (jacocoEnabled) {
                    task.dependsOn(subproject.tasks.withType(JacocoReport))
                }
            }

            def reportsDir = project.extensions.getByType(GrailsCodeStyleExtension).reportsDirectory
            task.inputs.dir(reportsDir).optional()
            task.outputs.file(root.layout.projectDirectory.file('CODENARC_VIOLATIONS.md'))
            task.outputs.file(root.layout.projectDirectory.file('CHECKSTYLE_VIOLATIONS.md'))
            task.outputs.file(root.layout.projectDirectory.file('PMD_VIOLATIONS.md'))
            task.outputs.file(root.layout.projectDirectory.file('SPOTBUGS_VIOLATIONS.md'))
            
            if (jacocoEnabled) {
                task.outputs.file(root.layout.projectDirectory.file('JACOCO_COVERAGE_VIOLATIONS.md'))
            }

            task.doLast {
                parseViolations(root, reportsDir.get())
            }
        }
    }

    @CompileDynamic
    private static void parseViolations(Project project, Directory reportsDir) {
        def slurper = new XmlSlurper()
        slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        slurper.setFeature("http://xml.org/sax/features/namespaces", false)

        boolean checkTests = GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)
        boolean jacocoEnabled = GradleUtils.lookupProperty(project, JACOCO_ENABLED_PROPERTY, false)

        def getModule = { String fileName ->
            def lastDash = fileName.lastIndexOf('-')
            return lastDash != -1 ? fileName.substring(0, lastDash) : fileName
        }

        def isTestFile = { String fileName ->
            fileName.toLowerCase().contains('test') || fileName.toLowerCase().contains('integrationtest')
        }

        def shouldSkipClass = { String className, String filePath = null ->
            if (checkTests) return false
            // Only skip if it's explicitly in a test source directory
            if (filePath && (filePath.contains('src/test/') || filePath.contains('src/integrationTest/'))) return true
            // If we don't have a path, be conservative
            if (!filePath && (className.contains('Spec') || className.contains('Test') || className.contains('Tests'))) return true
            return false
        }

        def writeReport = { String fileName, List violations, String title ->
            def reportFile = project.layout.projectDirectory.file(fileName).asFile
            def out = new StringBuilder()
            out.append("# ${title}\n")
            out.append("Generated on: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")

            if (violations.isEmpty()) {
                out.append("No violations found! 🎉\n")
            } else {
                def uniqueViolations = violations.unique().sort { v -> "${v.module}:${v.className}:${v.line}" }
                def groupedByModule = uniqueViolations.groupBy { it.module }.sort()
                groupedByModule.each { module, modViolations ->
                    out.append("## Module: ${module}\n")
                    out.append("| Class | Tool | Violation | Line | Message |\n")
                    out.append("| :--- | :--- | :--- | :--- | :--- |\n")
                    modViolations.each { v ->
                        out.append("| ${v.className} | ${v.tool} | ${v.type} | ${v.line} | ${v.message.replaceAll(/\|/, '\\|')} |\n")
                    }
                    out.append("\n")
                }
            }
            reportFile.text = out.toString()
            project.logger.lifecycle("Aggregated report generated: ${reportFile.absolutePath}")
        }

        // 1. CodeNarc
        def codenarcViolations = []
        def codenarcDir = reportsDir.dir('codenarc').asFile
        if (codenarcDir.exists() && GradleUtils.lookupProperty(project, CODENARC_ENABLED_PROPERTY, true)) {
            codenarcDir.eachFileMatch(~/.*\.xml/) { file ->
                // Respect checkTests property for CodeNarc
                if (file.size() == 0 || (!checkTests && isTestFile(file.name))) {
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
                        // Also skip if it is a test file path (backup check)
                        if (shouldSkipClass(className, f.@name.text())) {
                            return
                        }
                        f.Violation.each { v ->
                            codenarcViolations << [
                                    module: module,
                                    className: className,
                                    tool: 'CodeNarc',
                                    type: v.@ruleName.text(),
                                    line: v.@lineNumber.text(),
                                    message: v.Message.text().trim()
                            ]
                        }
                    }
                }
            }
        }
        writeReport('CODENARC_VIOLATIONS.md', codenarcViolations, 'CodeNarc Violations Summary')

        // 2. PMD
        def pmdViolations = []
        def pmdDir = reportsDir.dir('pmd').asFile
        if (pmdDir.exists() && GradleUtils.lookupProperty(project, PMD_ENABLED_PROPERTY, false)) {
            pmdDir.eachFileMatch(~/.*\.xml/) { file ->
                if (file.size() == 0 || (!checkTests && isTestFile(file.name))) {
                    return
                }
                def module = getModule(file.name)
                def xml = slurper.parse(file)
                xml.file.each { f ->
                    f.violation.each { v ->
                        def className = "${v.@package}.${v.@class}"
                        if (shouldSkipClass(className)) {
                            return
                        }
                        pmdViolations << [
                                module: module,
                                className: className,
                                tool: 'PMD',
                                type: v.@rule.text(),
                                line: v.@beginline.text(),
                                message: v.text().trim()
                        ]
                    }
                }
            }
        }
        writeReport('PMD_VIOLATIONS.md', pmdViolations, 'PMD Violations Summary')

        // 3. Checkstyle
        def checkstyleViolations = []
        def checkstyleDir = reportsDir.dir('checkstyle').asFile
        if (checkstyleDir.exists() && GradleUtils.lookupProperty(project, CHECKSTYLE_ENABLED_PROPERTY, true)) {
            checkstyleDir.eachFileMatch(~/.*\.xml/) { file ->
                if (file.size() == 0 || (!checkTests && isTestFile(file.name))) {
                    return
                }
                def module = getModule(file.name)
                def xml = slurper.parse(file)
                xml.file.each { f ->
                    def filePath = f.@name.text()
                    def className = filePath.contains('src/main/groovy/') ? filePath.split('src/main/groovy/')[1] :
                                    filePath.contains('src/main/java/') ? filePath.split('src/main/java/')[1] :
                                    filePath.contains('src/test/groovy/') ? filePath.split('src/test/groovy/')[1] :
                                    filePath.contains('src/test/java/') ? filePath.split('src/test/java/')[1] :
                                    filePath.split('/').last()
                    className = className.replace('.groovy', '').replace('.java', '').replace('/', '.')

                    if (shouldSkipClass(className)) {
                        return
                    }

                    f.error.each { e ->
                        checkstyleViolations << [
                                module: module,
                                className: className,
                                tool: 'Checkstyle',
                                type: e.@source.text().split(/\./).last(),
                                line: e.@line.text(),
                                message: e.@message.text().trim()
                        ]
                    }
                }
            }
        }
        writeReport('CHECKSTYLE_VIOLATIONS.md', checkstyleViolations, 'Checkstyle Violations Summary')

        // 4. SpotBugs
        def spotbugsViolations = []
        def spotbugsDir = reportsDir.dir('spotbugs').asFile
        if (spotbugsDir.exists() && GradleUtils.lookupProperty(project, SPOTBUGS_ENABLED_PROPERTY, false)) {
            spotbugsDir.eachFileMatch(~/.*\.xml/) { file ->
                if (file.size() == 0 || (!checkTests && isTestFile(file.name))) {
                    return
                }
                def module = getModule(file.name)
                def xml = slurper.parse(file)
                xml.BugInstance.each { b ->
                    def className = b.Class.@classname.text()
                    if (shouldSkipClass(className)) {
                        return
                    }
                    spotbugsViolations << [
                            module: module,
                            className: className,
                            tool: 'SpotBugs',
                            type: b.@type.text(),
                            line: b.SourceLine.@start.text(),
                            message: b.LongMessage.text().trim()
                    ]
                }
            }
        }
        writeReport('SPOTBUGS_VIOLATIONS.md', spotbugsViolations, 'SpotBugs Violations Summary')

        // 5. JaCoCo
        if (jacocoEnabled) {
            project.logger.info("Aggregating JaCoCo coverage reports")
            def jacocoCoverage = []
            project.rootProject.allprojects.each { p ->
                // JaCoCo reports for test are usually in build/reports/jacoco/test/jacocoTestReport.csv
                def csvReport = p.file("build/reports/jacoco/test/jacocoTestReport.csv")
                if (csvReport.exists()) {
                    project.logger.debug("Processing JaCoCo report: ${csvReport.absolutePath}")
                    csvReport.splitEachLine(',') { fields ->
                        if (fields.size() < 5 || fields[0] == 'GROUP') return // header or malformed line
                        def module = fields[0]
                        def pkg = fields[1]
                        def clazz = fields[2]
                        def missedStr = fields[3]
                        def coveredStr = fields[4]

                        // Skip if fields are not numeric
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

            if (!jacocoCoverage.isEmpty()) {
                // Filter out classes in the 'org.grails.orm.hibernate.support.hibernate7' package
                jacocoCoverage.removeIf { it.className.startsWith('org.grails.orm.hibernate.support.hibernate7.') }

                def jacocoReportFile = project.layout.projectDirectory.file('JACOCO_COVERAGE_VIOLATIONS.md').asFile
                def out = new StringBuilder()
                out.append("# JaCoCo Coverage Report\n")
                out.append("Generated on: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")

                def groupedByModule = jacocoCoverage.groupBy { it.module }.sort()
                groupedByModule.each { module, coverageList ->
                    out.append("## Module: ${module}\n")
                    out.append("| Class | % Instructions Covered |\n")
                    out.append("| :--- | :--- |\n")
                    coverageList.sort { it.percent }.each { c ->
                        out.append("| ${c.className} | ${c.percent}% |\n")
                    }
                    out.append("\n")
                }
                jacocoReportFile.text = out.toString()
                project.logger.lifecycle("Aggregated JaCoCo report generated: ${jacocoReportFile.absolutePath}")
            } else {
                project.logger.info("No JaCoCo coverage reports found to aggregate")
            }
        }
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

            boolean checkTests = GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)

            it.dependsOn(project.tasks.withType(CodeNarc).matching { t ->
                checkTests || (!t.name.toLowerCase().contains('test') && !t.name.toLowerCase().contains('integrationtest'))
            })
            it.dependsOn(project.tasks.withType(Checkstyle).matching { t ->
                checkTests || (!t.name.toLowerCase().contains('test') && !t.name.toLowerCase().contains('integrationtest'))
            })
            if (GradleUtils.lookupProperty(project, SPOTLESS_ENABLED_PROPERTY, false)) {
                it.dependsOn('spotlessCheck')
            }
            if (GradleUtils.lookupProperty(project, PMD_ENABLED_PROPERTY, false)) {
                it.dependsOn(project.tasks.withType(Pmd).matching { t ->
                    checkTests || (!t.name.toLowerCase().contains('test') && !t.name.toLowerCase().contains('integrationtest'))
                })
            }
            if (GradleUtils.lookupProperty(project, SPOTBUGS_ENABLED_PROPERTY, false)) {
                it.dependsOn(project.tasks.withType(SpotBugsTask).matching { t ->
                    checkTests || (!t.name.toLowerCase().contains('test') && !t.name.toLowerCase().contains('integrationtest'))
                })
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
            it.ignoreFailures = GradleUtils.lookupProperty(project, IGNORE_FAILURES_PROPERTY, false)
            it.toolVersion = project.findProperty('checkstyleVersion')
        }

        project.tasks.withType(Checkstyle).configureEach {
            it.group = 'verification'
            it.onlyIf { !project.hasProperty('skipCodeStyle') }
            it.ignoreFailures = GradleUtils.lookupProperty(project, IGNORE_FAILURES_PROPERTY, false)

            // Redirect XML report output to a single directory to consolidate
            // reports across all subprojects into one known location
            it.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeStyleExtension)
                            .reportsDirectory.get()
                            .dir('checkstyle')
                            .file("${project.name}-${it.name}.xml")
            )
        }

        if (!GradleUtils.lookupProperty(project, TEST_STYLING_PROPERTY, false)) {
            project.tasks.matching { it.name == 'checkstyleTest' }.configureEach {
                it.enabled = false // Do not check test sources at this time
            }
        }
    }

    @CompileDynamic
    static void configureSpotless(Project project) {
        if (!GradleUtils.lookupProperty(project, SPOTLESS_ENABLED_PROPERTY, false)) {
            return
        }

        project.extensions.getByType(GrailsCodeStyleExtension).spotlessDirectory.set(project.provider {
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
            it.ignoreFailures = GradleUtils.lookupProperty(project, IGNORE_FAILURES_PROPERTY, false)
            it.consoleOutput = true
            it.toolVersion = project.findProperty('pmdVersion')
        }

        project.tasks.withType(Pmd).configureEach {
            it.group = 'verification'
            it.onlyIf { !project.hasProperty('skipCodeStyle') }
            it.ignoreFailures = GradleUtils.lookupProperty(project, IGNORE_FAILURES_PROPERTY, false)

            it.reports.xml.required.set(true)
            it.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeStyleExtension)
                            .reportsDirectory.get()
                            .dir('pmd')
                            .file("${project.name}-${it.name}.xml")
            )
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
            it.ignoreFailures.set(GradleUtils.lookupProperty(project, IGNORE_FAILURES_PROPERTY, false))
        }

        project.tasks.withType(SpotBugsTask).configureEach { task ->
            task.group = 'verification'
            task.reports {
                it.create('html') { it.required = true }
                it.create('xml') {
                    it.required = true
                    it.outputLocation.set(
                        project.extensions.getByType(GrailsCodeStyleExtension)
                                .reportsDirectory.get()
                                .dir('spotbugs')
                                .file("${project.name}-${task.name}.xml")
                    )
                }
            }
            task.onlyIf { !project.hasProperty('skipCodeStyle') }
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

        registerCodenarcFixTask(project)

        project.extensions.configure(CodeNarcExtension) {
            it.configFile = project.extensions.getByType(GrailsCodeStyleExtension)
                    .codenarcDirectory.file(CODENARC_CONFIG_FILE_NAME).get().asFile
            it.ignoreFailures = GradleUtils.lookupProperty(project, IGNORE_FAILURES_PROPERTY, false)
            it.toolVersion = project.findProperty('codenarcVersion')
        }

        project.tasks.withType(CodeNarc).configureEach {
            it.group = 'verification'
            it.onlyIf { !project.hasProperty('skipCodeStyle') }
            it.ignoreFailures = GradleUtils.lookupProperty(project, IGNORE_FAILURES_PROPERTY, false)

            if (GradleUtils.lookupProperty(project, CODENARC_FIX_PROPERTY, false)) {
                it.dependsOn('codenarcFix')
            }

            // Redirect XML report output to a single directory to consolidate
            // reports across all subprojects into one known location
            it.reports.xml.required.set(true)
            it.reports.xml.outputLocation.set(
                    project.extensions.getByType(GrailsCodeStyleExtension)
                            .reportsDirectory.get()
                            .dir('codenarc')
                            .file("${project.name}-${it.name}.xml")
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
                    project.tasks.matching { it.name == testTaskName }.configureEach {
                        it.enabled = false
                    }
                }
            }
        }
    }

    private static void registerCodenarcFixTask(Project project) {
        if (project.tasks.findByName('codenarcFix')) {
            return
        }

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
                    content = content.replaceAll(/([\[,]\s*(?:[\w\-.]+|'[^']+'|"[^"]+")):([^\s\/])/, '$1: $2')
                    content = content.replaceAll(/(\(\s*(?:[\w\-.]+|'[^']+'|"[^"]+")):([^\s\/])/, '$1: $2')

                    // 3. UnnecessaryGString
                    content = content.replaceAll(/(?<!\\)(?<!")"([^"$\n\\]*)"(?!")/) { all, inner ->
                        if (!inner.contains("'")) {
                            return "'$inner'"
                        }
                        return all
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
