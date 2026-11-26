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
package org.grails.gradle.plugin.commands

import java.nio.file.Files
import java.nio.file.Path

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

import org.grails.gradle.plugin.core.GradleSpecification

/**
 * The compilation of the tools of a project says nothing of SLF4J. The transformations of Grails make a
 * logger of SLF4J when the compiler loads them, and the class path of {@code src/standalone} has the API
 * of SLF4J and no binding, so SLF4J said three lines on the standard error for every compilation, which
 * the {@code grails} command showed when it refreshed a standalone tool. The test project has a global
 * transformation that makes such a logger on the class path of both folders of tools, and the real API of
 * SLF4J beside it, the one of the class path of this specification.
 */
class ToolCompilerOutputSpec extends GradleSpecification {

    def "the compilation of src/standalone and src/cli with a transformation that makes a logger of SLF4J and no binding says nothing of SLF4J"() {
        given:
        GradleRunner runner = setupTestResourceProject('tool-compile-logging')
        File projectDir = runner.projectDir
        Path api = slf4jApi()
        Files.createDirectories(projectDir.toPath().resolve('slf4j'))
        Files.copy(api, projectDir.toPath().resolve('slf4j/slf4j-api.jar'))

        when:
        BuildResult result = executeTask('compileStandaloneGroovy', ['compileCliGroovy'])

        then: 'both folders are compiled, and the transformation made its logger in each compilation'
        result.task(':compileStandaloneGroovy').outcome == TaskOutcome.SUCCESS
        result.task(':compileCliGroovy').outcome == TaskOutcome.SUCCESS
        result.output.contains('LOGGING TRANSFORMATION visited PlainTool.groovy')
        result.output.contains('LOGGING TRANSFORMATION visited BuiltTool.groovy')

        and: 'SLF4J said nothing of itself'
        !result.output.readLines().any { String line -> line.contains('SLF4J') }
    }

    // the jar of the API of SLF4J of version 2, which reports its want of a binding, on the class path of this specification
    private static Path slf4jApi() {
        String jar = System.getProperty('java.class.path').split(File.pathSeparator).find { String entry ->
            String name = new File(entry).name
            name.startsWith('slf4j-api-2.') && name.endsWith('.jar')
        }
        assert jar != null : 'the class path of the specification has no API of SLF4J of version 2'
        Path.of(jar)
    }
}
