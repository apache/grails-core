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
import java.nio.file.attribute.FileTime

import groovy.json.JsonSlurper

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import org.apache.grails.gradle.model.cli.CommandCatalogFreshness

/**
 * The catalog of the commands of a project is kept current by the build: every build that compiles the
 * project writes it, after the classes, when what it lists changed, and when it is older than a file
 * the command line compares it with. A build in which nothing changed leaves it as it is.
 */
class CommandCatalogBuildSpec extends CommandProjectSpecification {

    private static final String CATALOG = '.grails/cache/command-catalog.json'

    def "a build that compiles the project with #task writes the catalog after the classes"() {
        given:
        setupCommandProject()

        when:
        BuildResult result = executeTask(task)

        then:
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        result.tasks*.path.indexOf(':grailsCommandCatalog') > result.tasks*.path.indexOf(':classes')
        catalog().commands*.name == ['config-report']
        new File(projectDir, '.grails/cache/.gitignore').text == '*\n'

        and: 'the command line would not take the catalog for out of date'
        isCurrent()

        where:
        task << ['classes', 'testClasses']
    }

    def "compiling alone does not write the catalog, and a build that fails to compile does not either"() {
        given:
        setupCommandProject()

        when:
        BuildResult compiled = executeTask('compileGroovy')

        then:
        compiled.task(':grailsCommandCatalog') == null
        !new File(projectDir, CATALOG).exists()

        when:
        projectFile('src/main/groovy/example/Broken.groovy', 'package example\nclass Broken {\n')
        BuildResult failed = runner.withArguments('classes').buildAndFail()

        then:
        failed.task(':grailsCommandCatalog') == null
        !new File(projectDir, CATALOG).exists()
    }

    def "a build in which nothing changed leaves the catalog up to date and as it is, from the configuration cache"() {
        given:
        setupCommandProject()
        executeTask('classes')
        File catalog = new File(projectDir, CATALOG)
        byte[] written = catalog.bytes
        FileTime modified = Files.getLastModifiedTime(catalog.toPath())

        when:
        BuildResult again = executeTask('classes')

        then:
        again.output.contains('Configuration cache entry reused')
        again.task(':classes').outcome == TaskOutcome.UP_TO_DATE
        again.task(':grailsCommandCatalog').outcome == TaskOutcome.UP_TO_DATE
        catalog.bytes == written
        Files.getLastModifiedTime(catalog.toPath()) == modified
        isCurrent()
    }

    def "a command that is added is in the catalog after the next build"() {
        given:
        setupCommandProject()
        executeTask('classes')

        when:
        projectFile("src/main/resources/${CLI_FACTORIES}", registrations('example.GreetCommand'))
        projectFile("src/main/resources/${descriptorPath('example.GreetCommand')}", descriptor('example.GreetCommand', 'greet', 'Greets'))
        BuildResult result = executeTask('classes')

        then:
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        catalog().commands*.name == ['config-report', 'greet']
        isCurrent()

        when: 'it is removed again'
        new File(projectDir, "src/main/resources/${CLI_FACTORIES}").delete()
        new File(projectDir, "src/main/resources/${descriptorPath('example.GreetCommand')}").delete()
        result = executeTask('classes')

        then:
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.SUCCESS
        catalog().commands*.name == ['config-report']
        isCurrent()
    }

    def "a catalog that is older than #changed is written again with what it had, so that the command line takes it for current"() {
        given: 'a catalog, and then a file the command line compares it with that is written again as it was'
        setupCommandProject()
        projectFile("src/main/resources/${CLI_FACTORIES}", registrations('example.GreetCommand'))
        projectFile("src/main/resources/${descriptorPath('example.GreetCommand')}", descriptor('example.GreetCommand', 'greet', 'Greets'))
        projectFile('grails-app/commands/example/GreetCommand.groovy', '// the source of the command\n')
        executeTask('classes')
        File catalog = new File(projectDir, CATALOG)
        byte[] written = catalog.bytes
        long then = catalog.lastModified()
        backdate(projectDir, then - 60_000)
        new File(projectDir, changed).setLastModified(then)

        expect:
        changedSince() == named

        when:
        BuildResult result = executeTask('classes')

        then: 'the task ran, or took what it wrote before from the build cache, and the catalog is the same'
        result.task(':grailsCommandCatalog').outcome in [TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE]
        result.task(':classes').outcome == TaskOutcome.UP_TO_DATE
        catalog.bytes == written
        isCurrent()

        when: 'nothing changed since'
        result = executeTask('classes')

        then:
        result.task(':grailsCommandCatalog').outcome == TaskOutcome.UP_TO_DATE

        where:
        changed                                                          | named
        'build.gradle'                                                   | 'build.gradle'
        'grails-app/commands/example/GreetCommand.groovy'                | 'grails-app/commands'
        "build/resources/main/${descriptorPath('example.GreetCommand')}" | 'build/resources/main/META-INF/grails-commands'
    }

    private Map catalog() {
        (Map) new JsonSlurper().parseText(new File(projectDir, CATALOG).getText('US-ASCII'))
    }

    // as the command line reads it: nothing it compares the catalog with is newer
    private boolean isCurrent() {
        changedSince() == null
    }

    private String changedSince() {
        FileTime written = Files.getLastModifiedTime(new File(projectDir, CATALOG).toPath())
        List<File> output = ['build/classes/groovy/main', 'build/classes/java/main', 'build/resources/main'].collect { new File(projectDir, it) }
        CommandCatalogFreshness.changedSince(projectDir, output, written)
    }

    // every file and directory of the project as changed at one instant, the catalog too; what Gradle keeps in its own directory is left alone
    private static void backdate(File directory, long instant) {
        directory.eachFile { File each ->
            if (each.name == '.gradle') {
                return
            }
            if (each.isDirectory()) {
                backdate(each, instant)
            }
            each.setLastModified(instant)
        }
        directory.setLastModified(instant)
    }
}
