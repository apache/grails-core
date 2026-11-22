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

import groovy.json.JsonSlurper
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

        and: 'the writer was given only the file to write, next to the manifest, which takes its place once it is whole'
        Map<String, String> written = read(manifest)
        written.arguments == '1'
        Path.of(written.file).parent.toRealPath() == manifest.parent.toRealPath()
        Path.of(written.file).fileName != manifest.fileName
        manifest.parent.toFile().list().toList() == ['commands.json']

        and: "the application's own classes and resources are on the classpath next to its grailsCli dependencies"
        written.runtimeMarker == 'from the application'
    }

    void 'a writer that fails leaves the manifest of the build before'() {
        given:
        def runner = setupTestResourceProject('command-manifest')
        Path manifest = runner.projectDir.toPath().resolve('app/build/grails/commands.json')
        executeTask(':app:generateCommandManifest')
        String before = manifest.toFile().text

        when: 'the writer fails part way through a build that has to run it'
        runner.projectDir.toPath().resolve('app/fail-writing').toFile().text = ''
        runner.projectDir.toPath().resolve('app/src/main/resources/runtime-marker.txt').toFile().text = 'changed'
        def result = runner.withArguments(':app:generateCommandManifest').buildAndFail()

        then:
        result.task(':app:generateCommandManifest').outcome == TaskOutcome.FAILED
        manifest.toFile().text == before
        manifest.parent.toFile().list().toList() == ['commands.json']
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

    void 'the manifest is written where the grailsCommandManifest property says'() {
        given:
        def runner = setupTestResourceProject('command-manifest')
        Path manifest = runner.projectDir.toPath().resolve('app/elsewhere/commands.json')

        when:
        def result = executeTask(':app:generateCommandManifest', ['-PgrailsCommandManifest=elsewhere/commands.json'])

        then:
        assertTaskSuccess('generateCommandManifest', result)
        manifest.toFile().isFile()
        Path.of(read(manifest).file).parent.toRealPath() == manifest.parent.toRealPath()
        !runner.projectDir.toPath().resolve('app/build/grails/commands.json').toFile().exists()
    }

    void 'a manifest with a short name is written too'() {
        given:
        def runner = setupTestResourceProject('command-manifest')

        when:
        def result = executeTask(':app:generateCommandManifest', ['-PgrailsCommandManifest=out/c'])

        then:
        assertTaskSuccess('generateCommandManifest', result)
        read(runner.projectDir.toPath().resolve('app/out/c')).runtimeMarker == 'from the application'
    }

    void 'the manifest names the directories whose files decide the commands and the build'() {
        given:
        def runner = setupTestResourceProject('command-manifest')
        File root = runner.projectDir.canonicalFile

        when:
        executeTask(':app:generateCommandManifest')
        Map<String, List<String>> inputs = (Map<String, List<String>>) read(root.toPath().resolve('app/build/grails/commands.json')).inputs

        then: 'the directories of every project, for their build files'
        inputs.buildDirectories.collect { new File(it).canonicalFile } as Set ==
                [root, new File(root, 'app'), new File(root, 'manifest-writer')] as Set

        and: 'the sources of commands of every project, and the logic of the build'
        inputs.sourceDirectories.collect { new File(it).canonicalFile }.containsAll([
                new File(root, 'gradle'), new File(root, 'buildSrc'),
                new File(root, 'app/grails-app/commands'), new File(root, 'app/src/main/resources/META-INF'),
                new File(root, 'app/src/cli/resources/META-INF'), new File(root, 'app/config/META-INF'),
                new File(root, 'manifest-writer/grails-app/commands'),
                new File(root, 'manifest-writer/src/cli/resources/META-INF')])

        and: 'the directories of the cli source sets, where a new source can be a new command'
        inputs.commandDirectories.collect { new File(it).canonicalFile } as Set ==
                [new File(root, 'app/src/cli/java'), new File(root, 'app/src/cli/resources')] as Set

        and: 'the main sources of every project, where a file named like a command can add one'
        inputs.commandFileDirectories.collect { new File(it).canonicalFile } as Set ==
                [new File(root, 'app/src/main/java'), new File(root, 'manifest-writer/src/main/java')] as Set

        and: 'every included build, also those that bring plugins of the build'
        inputs.sourceDirectories.collect { new File(it).canonicalFile }.containsAll([new File(root, 'shared'), new File(root, 'build-logic')])

        and: 'the sources of the listed commands found in the projects of the build, in any source set'
        inputs.sourceFiles.collect { new File(it).canonicalFile } == [new File(root, 'app/src/cli/java/org/example/FakeCommand.java')]
    }

    void 'the task runs only in the project the grailsCommandProject property names'() {
        given:
        def runner = setupTestResourceProject('command-manifest')
        File app = new File(runner.projectDir, 'app')

        when: 'another project is named'
        def other = executeTask(':app:generateCommandManifest', ["-PgrailsCommandProject=${new File(runner.projectDir, 'manifest-writer').absolutePath}".toString()])

        then:
        other.task(':app:generateCommandManifest').outcome == TaskOutcome.SKIPPED
        !new File(app, 'build/grails/commands.json').exists()

        when: 'the application is named'
        def named = executeTask(':app:generateCommandManifest', ["-PgrailsCommandProject=${app.absolutePath}".toString()])

        then:
        named.task(':app:generateCommandManifest').outcome == TaskOutcome.SUCCESS
        new File(app, 'build/grails/commands.json').exists()
    }

    void 'a project below the named one that does not compile leaves the named one alone'() {
        given: 'a project below the application whose source does not compile'
        def runner = setupTestResourceProject('command-manifest')
        File root = runner.projectDir
        new File(root, 'settings.gradle') << "\ninclude 'app:broken'\n"
        File broken = new File(root, 'app/broken')
        new File(broken, 'src/main/java/org/example').mkdirs()
        new File(broken, 'build.gradle').text = """
            plugins {
                id 'java'
                id 'org.apache.grails.gradle.grails-cli'
            }
        """.stripIndent()
        new File(broken, 'src/main/java/org/example/Broken.java').text = 'class Broken { not java }'

        when: 'the command list of the application is written by name, from its directory'
        def manifest = executeTask('generateCommandManifest', ['-p', 'app', '-PgrailsCommandProject=app'])

        then: 'the project below is skipped, and nothing of it is compiled'
        manifest.task(':app:generateCommandManifest').outcome == TaskOutcome.SUCCESS
        manifest.task(':app:broken:generateCommandManifest').outcome == TaskOutcome.SKIPPED
        !manifest.tasks*.path.contains(':app:broken:compileJava')

        when: 'a command of the application is planned the same way, the project below having no main class to find'
        new File(root, 'app/build.gradle') << "\ntasks.register('findMainClass', org.grails.gradle.plugin.run.FindMainClassTask)\n"
        def command = executeTask('runCommand', ['-m', '-p', 'app', '-PgrailsCommandProject=app'])
        List<String> planned = command.output.readLines().findAll { it.startsWith(':') }*.tokenize(' ')*.first()

        then: 'the project below brings only its own command task, which is skipped, and nothing it would depend on'
        planned.containsAll([':app:compileJava', ':app:runCommand'])
        planned.findAll { it.startsWith(':app:broken:') } == [':app:broken:runCommand']
    }

    private static Map<String, Object> read(Path file) {
        (Map<String, Object>) new JsonSlurper().parse(file.toFile(), 'UTF-8')
    }
}
