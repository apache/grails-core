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

import java.nio.file.Path

class GrailsViolationAggregationPluginSpec extends Specification {

    @TempDir
    Path testProjectDir

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

    def "all aggregation tasks are registered on root"() {
        given: "root project with aggregation plugin"
        testProjectDir.resolve('settings.gradle').toFile().text = ''
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
        result.output.contains('aggregateJacocoCoverage')
    }

    def "aggregateStyleViolations writes CodeNarc and Checkstyle reports to build/reports/violations/"() {
        given: "a root project with a subproject that has codestyle XML reports"
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
        def srcFile = moduleDir.resolve('src/main/groovy/com/example/AppClass.groovy').toFile()
        srcFile.parentFile.mkdirs()
        srcFile.text = 'package com.example\nclass AppClass {}'

        // Pre-populate XML reports in the standard consolidated location (build/reports/codestyle/)
        def checkstyleDir = testProjectDir.resolve('build/reports/codestyle/checkstyle').toFile()
        checkstyleDir.mkdirs()
        new File(checkstyleDir, 'app-module-checkstyleMain.xml').text = """<?xml version="1.0" encoding="UTF-8"?>
<checkstyle version="10.0">
<file name="${srcFile.absolutePath}">
<error line="2" column="1" severity="error" message="Missing a Javadoc comment." source="com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocPackageCheck"/>
</file>
</checkstyle>
"""
        def codenarcDir = testProjectDir.resolve('build/reports/codestyle/codenarc').toFile()
        codenarcDir.mkdirs()
        new File(codenarcDir, 'app-module-codenarcMain.xml').text = """<?xml version="1.0" encoding="UTF-8"?>
<CodeNarc version="3.1.0">
<Package name="com.example">
<File name="AppClass.groovy">
<Violation ruleName="EmptyClass" priority="2" lineNumber="1">
<Message>The class is empty</Message>
</Violation>
</File>
</Package>
</CodeNarc>
"""

        when: "running aggregateStyleViolations skipping the actual style tasks"
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('aggregateStyleViolations', '-x', 'checkstyleMain', '-x', 'codenarcMain', '--stacktrace')
                .withPluginClasspath()
                .build()

        then: "task succeeds"
        result.task(':aggregateStyleViolations').outcome == TaskOutcome.SUCCESS

        and: "reports land in build/reports/violations/ — NOT in the repo root"
        def violationsDir = testProjectDir.resolve('build/reports/violations').toFile()
        new File(violationsDir, 'CHECKSTYLE_VIOLATIONS.md').exists()
        new File(violationsDir, 'CODENARC_VIOLATIONS.md').exists()
        !testProjectDir.resolve('CHECKSTYLE_VIOLATIONS.md').toFile().exists()
        !testProjectDir.resolve('CODENARC_VIOLATIONS.md').toFile().exists()

        and: "checkstyle report contains the violation"
        def checkstyleMd = new File(violationsDir, 'CHECKSTYLE_VIOLATIONS.md').text
        checkstyleMd.contains('## Module: app-module')
        checkstyleMd.contains('JavadocPackageCheck')

        and: "codenarc report contains the violation"
        def codenarcMd = new File(violationsDir, 'CODENARC_VIOLATIONS.md').text
        codenarcMd.contains('## Module: app-module')
        codenarcMd.contains('EmptyClass')
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
