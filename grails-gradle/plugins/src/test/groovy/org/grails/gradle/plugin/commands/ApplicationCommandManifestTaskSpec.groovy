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

import java.nio.file.Path

import org.gradle.testkit.runner.TaskOutcome

import org.grails.gradle.plugin.core.GradleSpecification

class ApplicationCommandManifestTaskSpec extends GradleSpecification {

    void 'the manifest is written by the grails-core-cli writer on the classpath runCommand uses'() {
        given:
        def runner = setupTestResourceProject('command-manifest')
        Path manifest = runner.projectDir.toPath().resolve('app/build/grails/commands.json')

        when:
        def result = executeTask(':app:generateCommandManifest')

        then:
        assertTaskSuccess('generateCommandManifest', result)

        and: 'the writer was given only the file to write, which is in the build directory'
        Map<String, String> written = read(manifest)
        written.arguments == '1'
        Path.of(written.file).toRealPath() == manifest.toRealPath()

        and: "the application's own classes and resources are on the classpath next to its grailsCli dependencies"
        written.runtimeMarker == 'from the application'
    }

    void 'the manifest is up to date until the classpath changes'() {
        given:
        def runner = setupTestResourceProject('command-manifest')
        executeTask(':app:generateCommandManifest')

        when:
        def unchanged = executeTask(':app:generateCommandManifest')

        then:
        unchanged.task(':app:generateCommandManifest').outcome == TaskOutcome.UP_TO_DATE

        when: 'a resource of the application changes'
        runner.projectDir.toPath().resolve('app/src/main/resources/runtime-marker.txt').toFile().text = 'changed'
        def changed = executeTask(':app:generateCommandManifest')

        then:
        changed.task(':app:generateCommandManifest').outcome == TaskOutcome.SUCCESS
        read(runner.projectDir.toPath().resolve('app/build/grails/commands.json')).runtimeMarker == 'changed'
    }

    private static Map<String, String> read(Path file) {
        file.toFile().readLines().collectEntries { String line ->
            int separator = line.indexOf('=')
            [(line.substring(0, separator)): line.substring(separator + 1)]
        }
    }
}
