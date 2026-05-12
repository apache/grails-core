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
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class GrailsTestPluginSpec extends Specification {

    @TempDir
    Path testProjectDir

    def "runAll runs modules sequentially and still aggregates failures"() {
        given:
        testProjectDir.resolve('settings.gradle').toFile().text = "rootProject.name = 'test-project'"
        testProjectDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'org.apache.grails.gradle.test-aggregation'
            }

            ext['grails.test.modules'] = ':module-a,:module-b'
        """
        createGradleWrapper()

        when:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments('runAll', '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()

        then:
        result.output.contains('Running :module-a:test')
        result.output.contains('Running :module-b:test')

        and:
        testProjectDir.resolve('invocations.log').toFile().readLines() == [
                '--no-daemon -PmaxTestParallel=1 --continue :module-a:test',
                '--no-daemon -PmaxTestParallel=1 --continue :module-b:test'
        ]

        and:
        def report = testProjectDir.resolve('TEST_FAILURES.md').toFile().text
        report.contains('## Module: module-a')
        report.contains('com.example.ModuleASpec')
        report.contains('boom')
        !report.contains('## Module: module-b')
    }

    private void createGradleWrapper() {
        def wrapper = testProjectDir.resolve('gradlew').toFile()
        wrapper.text = """#!/usr/bin/env bash
set -euo pipefail

printf '%s\\n' "\$*" >> invocations.log

case "\$*" in
  *:module-a:test*)
    mkdir -p module-a/build/test-results/test
    cat > module-a/build/test-results/test/TEST-module-a.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="module-a" tests="1" failures="1" errors="0" skipped="0">
  <testcase classname="com.example.ModuleASpec" name="fails">
    <failure message="boom" type="org.opentest4j.AssertionFailedError">boom</failure>
  </testcase>
</testsuite>
XML
    exit 1
    ;;
  *:module-b:test*)
    mkdir -p module-b/build/test-results/test
    cat > module-b/build/test-results/test/TEST-module-b.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="module-b" tests="1" failures="0" errors="0" skipped="0">
  <testcase classname="com.example.ModuleBSpec" name="passes"/>
</testsuite>
XML
    exit 0
    ;;
  *)
    echo "unexpected invocation: \$*" >&2
    exit 2
    ;;
esac
"""
        wrapper.setExecutable(true)
    }
}
