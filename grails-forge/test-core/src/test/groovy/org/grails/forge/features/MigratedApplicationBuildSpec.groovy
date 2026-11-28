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
package org.grails.forge.features

import java.nio.file.Files

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.command.FeatureAddCommand
import org.grails.forge.cli.command.ProjectMigrateCommand
import org.grails.forge.utils.CommandSpec

/**
 * An application whose build file is laid out otherwise than this version writes it, as that of an
 * application of an earlier version is, given its record by {@code project migrate} and then two
 * features: the lines the features add to {@code build.gradle} are put where a Gradle build has
 * them, and Gradle builds the result.
 */
class MigratedApplicationBuildSpec extends CommandSpec {

    String previousHome
    String previousDirectory

    void setup() {
        // the journal of a change is kept in the home directory of whoever runs the command
        File home = Files.createTempDirectory('migratedhome').toFile()
        previousHome = System.getProperty('user.home')
        previousDirectory = System.getProperty('user.dir')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        new File(System.getProperty('user.home')).deleteDir()
        System.setProperty('user.home', previousHome)
        System.setProperty('user.dir', previousDirectory)
    }

    void 'an application with a build of its own layout builds after project migrate and feature add of a Gradle plugin, of a dependency and of an entry of a list'() {
        given: 'an application without a record, whose plugins are applied elsewhere and whose dependencies are in another order'
        generateProjectWithDefaults(ApplicationType.WEB)
        assert new File(dir, '.grails').deleteDir()
        File build = new File(dir, 'build.gradle')
        List<String> lines = build.readLines()
        List<String> applied = lines.findAll { String line -> line.startsWith('apply plugin:') }
        assert applied.size() > 1
        lines.removeAll(applied)
        int group = lines.findIndexOf { String line -> line.startsWith('group') }
        lines.addAll(group + 1, [''] + applied.reverse())
        int from = lines.indexOf('dependencies {')
        int to = (from..<lines.size()).find { int index -> lines[index] == '}' }
        List<String> declared = lines.subList(from + 1, to).reverse()
        lines = lines.subList(0, from + 1) + declared + lines.subList(to, lines.size())
        // and the last entry of a list without the comma this version ends it with, as earlier versions wrote it
        String last = "            'webjars/bootstrap-icons/**',\n    ]\n"
        assert (lines.join('\n') + '\n').count(last) == 1
        build.text = (lines.join('\n') + '\n').replace(last, "            'webjars/bootstrap-icons/**'\n    ]\n")
        String own = build.text

        when:
        int migrated = cli(ProjectMigrateCommand)
        int json = cli(FeatureAddCommand, 'views-json')
        int cache = cli(FeatureAddCommand, 'cache')
        int openapi = cli(FeatureAddCommand, 'openapi')
        List<String> after = build.readLines()

        then: 'both features are added, and every line of the build is still there, in its order'
        migrated == 0
        json == 0
        cache == 0
        openapi == 0
        build.text.contains("            'webjars/bootstrap-icons/**',\n            'webjars/swagger-ui/**'\n    ]\n")
        ProjectState.read(dir.toPath()).manifest.generationState == ProjectManifest.ADOPTED
        after.findAll { String line -> own.readLines().contains(line) } == own.readLines() - ["            'webjars/bootstrap-icons/**'"]

        and: 'the plugin is applied after the plugins the build applies, and the dependencies are the last of their block'
        after.indexOf('apply plugin: "org.apache.grails.gradle.grails-gson"') == after.findLastIndexOf { String line -> line.startsWith('apply plugin:') }
        after.contains('    implementation "org.apache.grails:grails-cache"')

        when:
        BuildResult result = executeGradle('compileGroovy')

        then:
        result.task(':compileGroovy').outcome in [TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE, TaskOutcome.UP_TO_DATE]
    }

    private int cli(Class<?> command, String... arguments) {
        System.setProperty('user.dir', dir.absolutePath)
        try {
            return CliRunner.run(command, applicationContext, arguments)
        } finally {
            System.setProperty('user.dir', previousDirectory)
        }
    }

    @Override
    String getTempDirectoryPrefix() {
        'migratedbuild'
    }
}
