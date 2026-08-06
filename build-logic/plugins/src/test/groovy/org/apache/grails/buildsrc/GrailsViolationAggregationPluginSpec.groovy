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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

class GrailsViolationAggregationPluginSpec extends Specification {

    @TempDir
    Path testProjectDir

    String pmdVersion = System.getProperty('grails.test.pmdVersion')
    String codenarcVersion = System.getProperty('grails.test.codenarcVersion')
    String checkstyleVersion = System.getProperty('grails.test.checkstyleVersion')

    def "plugin must be applied to root project only"() {
        given: "a subproject-only build with the aggregation plugin"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'sub'"
        testProjectDir.resolve('build.gradle').toFile().text = ''
        def sub = testProjectDir.resolve('sub')
        sub.toFile().mkdirs()
        sub.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """

        when: "configuring the project"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks')
                .withPluginClasspath()
                .buildAndFail()

        then: "an error is thrown"
        result.output.contains('must be applied to the root project only')
    }

    def "canonical roots register repository convention validation"() {
        given: "root project with aggregation plugin"
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('AGENTS.md').toFile().text = ''
        testProjectDir.resolve('.asf.yaml').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """

        when: "listing verification tasks"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('tasks', '--group=verification')
                .withPluginClasspath()
                .build()

        then:
        result.output.contains('aggregateStyleViolations')
        result.output.contains('aggregateAnalysisViolations')
        result.output.contains('aggregateViolations')
        result.output.contains('validateRepositoryConventions')
        result.output.contains('aggregateJacocoCoverage')
    }

    def "generated profile wrappers avoid convention validation implicit dependencies"() {
        given: "a source-tree convention input and the legacy wrapper copy output"
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('AGENTS.md').toFile().text = ''
        testProjectDir.resolve('.asf.yaml').toFile().text = ''
        def i18n = testProjectDir.resolve('skeleton/grails-app/i18n/messages.properties').toFile()
        i18n.parentFile.mkdirs()
        i18n.text = 'message=Message\n'
        def staleWrappers = [
                'grails-wrapper.jar': 'stale-jar',
                'grailsw': 'stale-shell',
                'grailsw.bat': 'stale-bat',
        ]
        staleWrappers.each { String name, String content ->
            new File(testProjectDir.resolve('skeleton').toFile(), name).text = content
        }
        def wrapper = testProjectDir.resolve('wrapper').toFile()
        wrapper.mkdirs()
        def generatedWrappers = [
                'grails-wrapper.jar': 'generated-jar',
                'grailsw': 'generated-shell',
                'grailsw.bat': 'generated-bat',
        ]
        generatedWrappers.each { String name, String content ->
            new File(wrapper, name).text = content
        }
        new File(wrapper, 'LICENSE').text = 'license'
        new File(wrapper, 'NOTICE').text = 'notice'
        testProjectDir.resolve('build.gradle').toFile().text = '''
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }

            def copyWrapper = tasks.register('copyGrailsWrapperScripts', Copy) {
                from('wrapper')
                into(layout.projectDirectory.dir('skeleton'))
            }
            tasks.register('packageProfile', Zip) {
                dependsOn(copyWrapper)
                from(layout.projectDirectory.dir('skeleton'))
                archiveFileName.set('packageProfile.zip')
                destinationDirectory.set(layout.buildDirectory.dir('distributions'))
            }
            tasks.register('sourcesJar', Zip) {
                dependsOn(copyWrapper)
                from(layout.projectDirectory.dir('skeleton'))
                archiveFileName.set('sourcesJar.zip')
                destinationDirectory.set(layout.buildDirectory.dir('distributions'))
            }
        '''

        when: "Gradle 9 validates the old combined task graph"
        def failedResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('validateRepositoryConventions', 'packageProfile', 'sourcesJar', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        then: "the source-owned wrapper output is rejected"
        failedResult.output.contains("Task ':validateRepositoryConventions' uses this output of task ':copyGrailsWrapperScripts'")

        when: "wrappers are generated under build and consumed at the skeleton archive path"
        def generatedOutput = testProjectDir.resolve('build/generated/wrapper').toFile()
        generatedOutput.mkdirs()
        new File(generatedOutput, 'NOTICE').text = 'stale-sidecar'
        testProjectDir.resolve('build.gradle').toFile().text = '''
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }

            def wrapperFiles = ['grails-wrapper.jar', 'grailsw', 'grailsw.bat']
            def sourceSkeleton = layout.projectDirectory.dir('skeleton').asFile
            def copyWrapper = tasks.register('copyGrailsWrapperScripts', Sync) {
                from('wrapper') {
                    include 'grails-wrapper.jar'
                    include 'grailsw'
                    include 'grailsw.bat'
                }
                into(layout.buildDirectory.dir('generated/wrapper'))
            }
            tasks.register('packageProfile', Zip) {
                exclude { details -> details.file.parentFile == sourceSkeleton && wrapperFiles.contains(details.name) }
                from(sourceSkeleton) {
                    into('skeleton')
                }
                from(copyWrapper) {
                    into('skeleton')
                }
                archiveFileName.set('packageProfile.zip')
                destinationDirectory.set(layout.buildDirectory.dir('distributions'))
            }
            tasks.register('sourcesJar', Zip) {
                exclude { details -> details.file.parentFile == sourceSkeleton && wrapperFiles.contains(details.name) }
                from(sourceSkeleton) {
                    into('skeleton')
                }
                from(copyWrapper) {
                    into('skeleton')
                }
                archiveFileName.set('sourcesJar.zip')
                destinationDirectory.set(layout.buildDirectory.dir('distributions'))
            }
        '''
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('validateRepositoryConventions', 'packageProfile', 'sourcesJar', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "validation and wrapper packaging succeed together"
        result.task(':validateRepositoryConventions').outcome == TaskOutcome.SUCCESS
        result.task(':copyGrailsWrapperScripts').outcome == TaskOutcome.SUCCESS
        result.task(':packageProfile').outcome == TaskOutcome.SUCCESS
        result.task(':sourcesJar').outcome == TaskOutcome.SUCCESS
        !new File(generatedOutput, 'NOTICE').exists()

        and: "both archives retain source content and exactly the generated wrappers"
        ['build/distributions/packageProfile.zip', 'build/distributions/sourcesJar.zip'].each { String archive ->
            new java.util.zip.ZipFile(testProjectDir.resolve(archive).toFile()).withCloseable { zipFile ->
                def entries = zipFile.entries()*.name
                assert entries.contains('skeleton/grails-app/i18n/messages.properties')
                generatedWrappers.each { String wrapperName, String generatedContent ->
                    assert entries.count { it == 'skeleton/' + wrapperName } == 1
                    zipFile.getInputStream(zipFile.getEntry('skeleton/' + wrapperName)).withCloseable { input ->
                        assert input.getText('UTF-8') == generatedContent
                    }
                }
                assert !entries.contains('skeleton/LICENSE')
                assert !entries.contains('skeleton/NOTICE')
            }
        }
    }

    def "canonical roots report a missing AGENTS.md"() {
        given:
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('.asf.yaml').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('validateRepositoryConventions', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        then:
        result.output.contains('AGENTS.md: file is missing')
    }

    def "nested builds ignore stale legacy style XML without AGENTS.md"() {
        given: "a nested build root with a stale leaf-keyed style report"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
            dependencies {
                implementation localGroovy()
            }
        """
        def checkstyleDir = testProjectDir.resolve('build/reports/code-style/checkstyle').toFile()
        checkstyleDir.mkdirs()
        new File(checkstyleDir, 'app-module-checkstyleMain.xml').text = '''<?xml version="1.0" encoding="UTF-8"?>
<checkstyle version="10.0">
<file name="src/main/groovy/com/example/AppClass.groovy">
<error line="2" column="1" severity="error" message="Legacy report." source="com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocPackageCheck"/>
</file>
</checkstyle>
'''
        def codenarcDir = testProjectDir.resolve('build/reports/code-style/codenarc').toFile()
        codenarcDir.mkdirs()
        new File(codenarcDir, 'app-module-codenarcMain.xml').text = '''<?xml version="1.0" encoding="UTF-8"?>
<CodeNarc version="3.1.0">
<Package name="com.example">
<File name="AppClass.groovy">
<Violation ruleName="EmptyClass" priority="2" lineNumber="1">
<Message>Legacy report</Message>
</Violation>
</File>
</Package>
</CodeNarc>
'''

        when: "running aggregateStyleViolations"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "task succeeds"
        result.task(':aggregateStyleViolations').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        result.task(':writeStyleViolations').outcome == TaskOutcome.SUCCESS
        result.task(':validateRepositoryConventions') == null

        and: "reports land in build/reports/violations/ — NOT in the repo root"
        def violationsDir = testProjectDir.resolve('build/reports/violations').toFile()
        new File(violationsDir, 'CHECKSTYLE_VIOLATIONS.md').exists()
        new File(violationsDir, 'CODENARC_VIOLATIONS.md').exists()
        !testProjectDir.resolve('CHECKSTYLE_VIOLATIONS.md').toFile().exists()
        !testProjectDir.resolve('CODENARC_VIOLATIONS.md').toFile().exists()

        and: "legacy Checkstyle XML is ignored"
        def checkstyleMd = new File(violationsDir, 'CHECKSTYLE_VIOLATIONS.md').text
        !checkstyleMd.contains('Legacy report.')

        and: "legacy CodeNarc XML is ignored"
        def codenarcMd = new File(violationsDir, 'CODENARC_VIOLATIONS.md').text
        !codenarcMd.contains('Legacy report')
    }

    def "aggregateAnalysisViolations recognizes the PMD project allowlist and reports disabled SpotBugs"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = """grails.code-analysis.enabled.pmd.projects=:app-module
grails.code-analysis.ignoreFailures=true
pmdVersion=${pmdVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
        """
        def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
        sourceFile.parentFile.mkdirs()
        sourceFile.text = '''package com.example;

public class App {
    private void unused() {
    }
}
'''

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':app-module:pmdMain').outcome == TaskOutcome.SUCCESS
        result.task(':aggregateAnalysisViolations').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        result.task(':writeAnalysisViolations').outcome == TaskOutcome.SUCCESS
        def violationsDir = testProjectDir.resolve('build/reports/violations').toFile()
        def pmdReport = new File(violationsDir, 'PMD_VIOLATIONS.md').text
        pmdReport.contains('UnusedPrivateMethod')
        new File(violationsDir, 'SPOTBUGS_VIOLATIONS.md').text.contains('SpotBugs is disabled.')

        when: "the generated configuration inputs settle after the first run"
        def updatedResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        updatedResult.output.contains('Configuration cache entry stored')

        when: "running the stable lifecycle from the reused configuration cache"
        def reusedResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        reusedResult.output.contains('Reusing configuration cache')
        reusedResult.task(':writeAnalysisViolations').outcome == TaskOutcome.SUCCESS
    }

    def "aggregateAnalysisViolations fails for PMD violations in an allowlisted project"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = """grails.code-analysis.enabled.pmd.projects=:app-module
pmdVersion=${pmdVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
        """
        def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
        sourceFile.parentFile.mkdirs()
        sourceFile.text = '''package com.example;

public class App {
    private void unused() {
    }
}
'''

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--continue', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        then:
        result.task(':app-module:pmdMain').outcome == TaskOutcome.FAILED
        result.task(':writeAnalysisViolations').outcome == TaskOutcome.FAILED
        def pmdReport = testProjectDir.resolve('build/reports/violations/PMD_VIOLATIONS.md').toFile().text
        pmdReport.contains('UnusedPrivateMethod')
    }

    def "aggregateAnalysisViolations fails when an executed task removes its XML report"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = """grails.code-analysis.enabled.pmd.projects=:app-module
pmdVersion=${pmdVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
            tasks.configureEach {
                if (name == 'pmdMain') {
                    doLast {
                        reports.xml.outputLocation.get().asFile.delete()
                    }
                }
            }
        """
        def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
        sourceFile.parentFile.mkdirs()
        sourceFile.text = '''package com.example;

public class App {
    public void used() {
    }
}
'''

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        then:
        result.task(':app-module:pmdMain').outcome == TaskOutcome.SUCCESS
        result.task(':writeAnalysisViolations').outcome == TaskOutcome.FAILED
        def pmdReport = testProjectDir.resolve('build/reports/violations/PMD_VIOLATIONS.md').toFile().text
        pmdReport.contains('MissingReport')
    }

    def "aggregateAnalysisViolations aggregates SpotBugs reports from an extension-enabled project"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = 'grails.code-analysis.ignoreFailures=true\n'
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
            grailsCodeAnalysis {
                spotbugsEnabled = true
            }
        """
        def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
        sourceFile.parentFile.mkdirs()
        sourceFile.text = '''package com.example;

public class App {
    public int nullDereference() {
        String value = null;
        return value.length();
    }
}
'''

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':app-module:spotbugsMain').outcome == TaskOutcome.SUCCESS
        File[] markers = testProjectDir.resolve('build/reports/aggregation-markers/spotbugs').toFile().listFiles()
        markers.length == 1
        markers[0].text.trim() == 'build/reports/code-analysis/spotbugs/3a6170702d6d6f64756c65-spotbugsMain.xml'
        def spotbugsReport = testProjectDir.resolve('build/reports/violations/SPOTBUGS_VIOLATIONS.md').toFile().text
        spotbugsReport.contains('## Module: :app-module')
        spotbugsReport.contains('NP_ALWAYS_NULL')
    }

    def "aggregateStyleViolations aggregates Checkstyle reports with violations"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = """grails.code-style.ignoreFailures=true
checkstyleVersion=${checkstyleVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
        """
        def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
        sourceFile.parentFile.mkdirs()
        sourceFile.text = 'package com.example;\n\npublic class App {\n\tpublic void run() {\n    }\n}\n'

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':app-module:checkstyleMain').outcome == TaskOutcome.SUCCESS
        def checkstyleReport = testProjectDir.resolve('build/reports/violations/CHECKSTYLE_VIOLATIONS.md').toFile().text
        checkstyleReport.contains('## Module: :app-module')
        checkstyleReport.contains('FileTabCharacter')
    }

    def "aggregateStyleViolations fails when an executed task removes its XML report"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = "checkstyleVersion=${checkstyleVersion}\n"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
            tasks.configureEach {
                if (name == 'checkstyleMain') {
                    doLast {
                        reports.xml.outputLocation.get().asFile.delete()
                    }
                }
            }
        """
        def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
        sourceFile.parentFile.mkdirs()
        sourceFile.text = 'package com.example;\n\npublic class App {\n}\n'

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        then:
        result.task(':app-module:checkstyleMain').outcome == TaskOutcome.SUCCESS
        result.task(':writeStyleViolations').outcome == TaskOutcome.FAILED
        def checkstyleReport = testProjectDir.resolve('build/reports/violations/CHECKSTYLE_VIOLATIONS.md').toFile().text
        checkstyleReport.contains('## Module: :app-module')
        checkstyleReport.contains('MissingReport')
    }

    def "aggregate Checkstyle task reports only its configured analyzer module"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = "checkstyleVersion=${checkstyleVersion}\n"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'one', 'two'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        ['one', 'two'].each { projectName ->
            def moduleDir = testProjectDir.resolve(projectName)
            moduleDir.toFile().mkdirs()
            moduleDir.resolve('build.gradle').toFile().text = projectName == 'one' ? """
                plugins {
                    id 'java'
                    id 'org.apache.grails.gradle.grails-code-style'
                }
                repositories { mavenCentral() }
            """ : """
                plugins {
                    id 'java'
                }
                repositories { mavenCentral() }
            """
            def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
            sourceFile.parentFile.mkdirs()
            sourceFile.text = 'package com.example;\n\npublic class App {\n}\n'
        }

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':one:checkstyleMain').outcome == TaskOutcome.SUCCESS
        result.task(':aggregateStyleViolations').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        result.task(':writeStyleViolations').outcome == TaskOutcome.SUCCESS
        def checkstyleReport = testProjectDir.resolve('build/reports/violations/CHECKSTYLE_VIOLATIONS.md').toFile().text
        checkstyleReport.contains('Modules analyzed: :one')
        !checkstyleReport.contains(':two')
    }

    def "aggregate PMD task aggregates only its configured report"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = """grails.code-analysis.enabled.pmd.projects=:one
grails.code-analysis.ignoreFailures=true
pmdVersion=${pmdVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'one', 'two'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        ['one', 'two'].each { projectName ->
            def moduleDir = testProjectDir.resolve(projectName)
            moduleDir.toFile().mkdirs()
            moduleDir.resolve('build.gradle').toFile().text = """
                plugins {
                    id 'java'
                    id 'org.apache.grails.gradle.grails-code-style'
                }
                repositories { mavenCentral() }
            """
            def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
            sourceFile.parentFile.mkdirs()
            sourceFile.text = 'package com.example; public class App {}'
        }

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':one:pmdMain').outcome == TaskOutcome.SUCCESS
        result.task(':aggregateAnalysisViolations').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        result.task(':writeAnalysisViolations').outcome == TaskOutcome.SUCCESS
        def pmdReport = testProjectDir.resolve('build/reports/violations/PMD_VIOLATIONS.md').toFile().text
        !pmdReport.contains('MissingReport')
        pmdReport.contains('Modules analyzed: :one')
        !pmdReport.contains(':two')
    }

    def "direct Checkstyle task leaves the aggregate report unchanged"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = "checkstyleVersion=${checkstyleVersion}\n"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'one', 'two'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        File moduleOneSource
        ['one', 'two'].each { projectName ->
            def moduleDir = testProjectDir.resolve(projectName)
            moduleDir.toFile().mkdirs()
            moduleDir.resolve('build.gradle').toFile().text = """
                plugins {
                    id 'java'
                    id 'org.apache.grails.gradle.grails-code-style'
                }
                repositories { mavenCentral() }
            """
            def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
            sourceFile.parentFile.mkdirs()
            sourceFile.text = 'package com.example;\n\npublic class App {\n}\n'
            if (projectName == 'one') {
                moduleOneSource = sourceFile
            }
        }

        when: "writing the authoritative aggregate report"
        def aggregateResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '--stacktrace')
                .withPluginClasspath()
                .build()
        File checkstyleReport = testProjectDir.resolve('build/reports/violations/CHECKSTYLE_VIOLATIONS.md').toFile()
        byte[] aggregateReport = checkstyleReport.bytes
        moduleOneSource << '\n'

        and: "running a direct analyzer task"
        def directResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(':one:checkstyleMain', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        aggregateResult.task(':writeStyleViolations').outcome == TaskOutcome.SUCCESS
        directResult.task(':one:checkstyleMain').outcome == TaskOutcome.SUCCESS
        directResult.task(':writeStyleViolations') == null
        checkstyleReport.bytes == aggregateReport

        when: "reusing the direct task configuration cache"
        def reusedResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(':one:checkstyleMain', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        reusedResult.output.contains('Reusing configuration cache')
    }

    def "aggregateViolations runs repository convention validation but aggregateStyleViolations does not"() {
        given:
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('AGENTS.md').toFile().text = ''
        testProjectDir.resolve('.asf.yaml').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
            tasks.register('rat')
        """

        when:
        def styleResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        styleResult.task(':validateRepositoryConventions') == null

        when:
        def aggregateResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        aggregateResult.task(':validateRepositoryConventions').outcome == TaskOutcome.SUCCESS
    }

    def "NO-SOURCE analyzers are omitted from aggregation"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = """grails.code-analysis.enabled.pmd=true
pmdVersion=${pmdVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'common', 'model'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        ['common', 'model'].each { projectName ->
            testProjectDir.resolve(projectName).toFile().mkdirs()
            testProjectDir.resolve("${projectName}/build.gradle").toFile().text = """
                plugins {
                    id 'java'
                    id 'org.apache.grails.gradle.grails-code-style'
                }
            """
        }

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':aggregateAnalysisViolations').outcome == TaskOutcome.SUCCESS
        !testProjectDir.resolve('build/reports/violations/PMD_VIOLATIONS.md').toFile().text.contains('MissingReport')
    }

    def "renamed analysis XML reports aggregate through root-relative cache-safe markers"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = """grails.code-analysis.enabled.pmd.projects=:app-module
grails.code-analysis.ignoreFailures=true
pmdVersion=${pmdVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
            grailsCodeAnalysis {
                pmdEnabled = true
                reportsDirectory.set(layout.buildDirectory.dir('custom-analysis'))
            }
            afterEvaluate {
                tasks.named('pmdMain') {
                    reports.xml.outputLocation.set(layout.buildDirectory.file('custom-analysis/pmd/renamed-pmd.xml'))
                }
            }
        """
        def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
        sourceFile.parentFile.mkdirs()
        sourceFile.text = '''package com.example;

public class App {
    private void unused() {
    }
}
'''

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':aggregateAnalysisViolations').outcome == TaskOutcome.SUCCESS
        moduleDir.resolve('build/custom-analysis/pmd/renamed-pmd.xml').toFile().isFile()
        testProjectDir.resolve('build/reports/violations/PMD_VIOLATIONS.md').toFile().text.contains('UnusedPrivateMethod')
        File[] markers = testProjectDir.resolve('build/reports/aggregation-markers/pmd').toFile().listFiles()
        markers.length == 1
        def marker = markers[0]
        marker.text.trim() == 'app-module/build/custom-analysis/pmd/renamed-pmd.xml'
        !new File(marker.text.trim()).absolute

        when:
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()
        def reusedResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--configuration-cache', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        reusedResult.output.contains('Reusing configuration cache.')
    }

    def "renamed style XML reports aggregate through root-relative markers"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = """grails.code-style.ignoreFailures=true
codenarcVersion=${codenarcVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'groovy'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
            dependencies { implementation localGroovy() }
            grailsCodeStyle.reportsDirectory.set(layout.buildDirectory.dir('custom-style'))
            tasks.named('codenarcMain') {
                reports.xml.outputLocation.set(layout.buildDirectory.file('custom-style/codenarc/renamed-codenarc.xml'))
            }
        """
        def sourceFile = moduleDir.resolve('src/main/groovy/com/example/App.groovy').toFile()
        sourceFile.parentFile.mkdirs()
        sourceFile.text = 'package com.example\nclass App {}'

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':aggregateStyleViolations').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        moduleDir.resolve('build/custom-style/codenarc/renamed-codenarc.xml').toFile().isFile()
        testProjectDir.resolve('build/reports/violations/CODENARC_VIOLATIONS.md').toFile().text.contains('App | CodeNarc')
        File[] markers = testProjectDir.resolve('build/reports/aggregation-markers/codenarc').toFile().listFiles()
        markers.length == 1
        def marker = markers[0]
        marker.text.trim() == 'app-module/build/custom-style/codenarc/renamed-codenarc.xml'
        !new File(marker.text.trim()).absolute
    }

    def "aggregateAnalysisViolations distinguishes duplicate nested project leaf names"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = """grails.code-analysis.enabled.pmd.projects=:one:shared,:two:shared
grails.code-analysis.ignoreFailures=true
pmdVersion=${pmdVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'one:shared', 'two:shared'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        ['one', 'two'].each { parent ->
            def moduleDir = testProjectDir.resolve("${parent}/shared")
            moduleDir.toFile().mkdirs()
            moduleDir.resolve('build.gradle').toFile().text = """
                plugins {
                    id 'java'
                    id 'org.apache.grails.gradle.grails-code-style'
                }
                repositories { mavenCentral() }
            """
            def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
            sourceFile.parentFile.mkdirs()
            sourceFile.text = '''package com.example;

public class App {
    private void unused() {
    }
}
'''
        }

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        result.task(':aggregateAnalysisViolations').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
        def pmdReport = testProjectDir.resolve('build/reports/violations/PMD_VIOLATIONS.md').toFile().text
        pmdReport.contains('## Module: :one:shared')
        pmdReport.contains('## Module: :two:shared')
    }

    def "analysis validation failure removes reports from a previous run"() {
        given:
        def propertiesFile = testProjectDir.resolve('gradle.properties').toFile()
        propertiesFile.text = """grails.code-analysis.enabled.pmd.projects=:app-module
pmdVersion=${pmdVersion}
"""
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'app-module'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        def moduleDir = testProjectDir.resolve('app-module')
        moduleDir.toFile().mkdirs()
        moduleDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-code-style'
            }
            repositories { mavenCentral() }
        """
        def sourceFile = moduleDir.resolve('src/main/java/com/example/App.java').toFile()
        sourceFile.parentFile.mkdirs()
        sourceFile.text = 'package com.example; public class App { public void used() {} }'

        when: "writing valid reports"
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--stacktrace')
                .withPluginClasspath()
                .build()

        then:
        def pmdReport = testProjectDir.resolve('build/reports/violations/PMD_VIOLATIONS.md').toFile()
        def spotbugsReport = testProjectDir.resolve('build/reports/violations/SPOTBUGS_VIOLATIONS.md').toFile()
        pmdReport.exists()
        spotbugsReport.exists()

        when: "the next run fails before parsing reports"
        propertiesFile.text = """grails.code-analysis.enabled.pmd.projects=:missing
pmdVersion=${pmdVersion}
"""
        def failedResult = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        then:
        failedResult.task(':writeAnalysisViolations').outcome == TaskOutcome.FAILED
        !pmdReport.exists()
        !spotbugsReport.exists()
    }

    @Unroll
    def "aggregateAnalysisViolations rejects unknown #tool project paths"() {
        given:
        testProjectDir.resolve('gradle.properties').toFile().text = "${property}=${projectPath}"
        testProjectDir.resolve('settings.gradle').toFile().text = "include 'known'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        testProjectDir.resolve('known').toFile().mkdirs()
        testProjectDir.resolve('known/build.gradle').toFile().text = ''
        testProjectDir.resolve('build/reports/code-analysis').toFile().mkdirs()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateAnalysisViolations', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        then:
        result.output.contains("Unknown project path(s) in ${property}: ${projectPath}")
        result.output.contains("Run './gradlew projects' to list valid subproject paths.")

        where:
        tool            | property                                                             | projectPath
        'PMD'           | GrailsCodeAnalysisPlugin.PMD_ENABLED_PROJECTS_PROPERTY                | ':missing'
        'SpotBugs'      | GrailsCodeAnalysisPlugin.SPOTBUGS_ENABLED_PROJECTS_PROPERTY           | ':missing'
        'PMD root-path' | GrailsCodeAnalysisPlugin.PMD_ENABLED_PROJECTS_PROPERTY                | ':'
    }

    def "aggregateJacocoCoverage handles no csv reports gracefully"() {
        given: "root project with no subproject csv reports"
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateJacocoCoverage', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "task succeeds without error"
        result.task(':aggregateJacocoCoverage').outcome == TaskOutcome.SUCCESS

        and: "no report file is created"
        !testProjectDir.resolve('build/reports/violations/JACOCO_COVERAGE.md').toFile().exists()
    }

    def "aggregateJacocoCoverage excludes the default hibernate7 support classes"() {
        given: "a root project with a jacoco csv containing an h7 support class and a normal class"
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        writeJacocoCsv([
                'app,org.grails.orm.hibernate.support.hibernate7,HibernateSupport,10,0',
                'app,org.example.kept,KeptClass,0,20',
        ])

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateJacocoCoverage', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "task succeeds and the report drops the colliding h7 class but keeps the normal one"
        result.task(':aggregateJacocoCoverage').outcome == TaskOutcome.SUCCESS
        def report = testProjectDir.resolve('build/reports/violations/JACOCO_COVERAGE.md').toFile()
        report.exists()
        def text = report.text
        text.contains('org.example.kept.KeptClass')
        !text.contains('org.grails.orm.hibernate.support.hibernate7.HibernateSupport')
    }

    def "aggregateJacocoCoverage exclusion prefixes are configurable via property"() {
        given: "a root project and a custom exclusion prefix that keeps the h7 class and drops a custom one"
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.grails-violation-aggregation'
            }
        """
        writeJacocoCsv([
                'app,org.grails.orm.hibernate.support.hibernate7,HibernateSupport,10,0',
                'app,com.example.skip,SkipMe,5,5',
                'app,org.example.kept,KeptClass,0,20',
        ])

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateJacocoCoverage', '-Pgrails.jacoco.aggregation.excludedClassPrefixes=com.example.skip', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "the custom prefix is dropped while the default h7 class is now retained"
        result.task(':aggregateJacocoCoverage').outcome == TaskOutcome.SUCCESS
        def text = testProjectDir.resolve('build/reports/violations/JACOCO_COVERAGE.md').toFile().text
        text.contains('org.example.kept.KeptClass')
        text.contains('org.grails.orm.hibernate.support.hibernate7.HibernateSupport')
        !text.contains('com.example.skip.SkipMe')
    }

    private void writeJacocoCsv(List<String> dataRows) {
        def csv = testProjectDir.resolve('build/reports/jacoco/test/jacocoTestReport.csv').toFile()
        csv.parentFile.mkdirs()
        csv.text = (['GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED'] + dataRows).join('\n') + '\n'
    }
}
