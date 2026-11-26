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
package org.apache.grails.forge.cli.console

import java.nio.file.Files
import java.nio.file.Path
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout

/**
 * The three kinds of command in a generated application, with the Gradle plugin, the compiler and
 * the classes that run commands as they are published: an application command runs in the
 * application, a Gradle command is run by the build without it, and a standalone command is started
 * by the command line as a process of its own, for which Gradle is not run while nothing it is made
 * from has changed. The Gradle wrapper of the application writes down every time it is started.
 */
@IgnoreIf({ os.windows })
@Timeout(1200)
class StandaloneGeneratedApplicationSpec extends CommandSpec implements CommandFixture {

    // the Gradle daemon of the builds of the application is one of this specification, which ends it
    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ConsoleProject.setSafeDirectories(null)
    }

    void cleanupSpec() {
        gradle.close()
    }

    void "a command of each kind runs as its kind says, and a standalone command runs without Gradle"() {
        given: 'a generated application with a command of each kind, which was never built'
        generateProject(ApplicationType.WEB)
        new File(dir, 'settings.gradle') << '\n' + gradle.recording
        new File(dir, 'gradle.properties') << "\norg.gradle.daemon.registry.base=${gradle.registry.canonicalPath}\norg.gradle.daemon.idletimeout=${IsolatedGradle.IDLE_MILLIS}\n"
        File commands = new File(dir, 'grails-app/commands/example/grails')
        commands.mkdirs()
        new File(commands, 'InsideCommand.groovy').text = '''\
            package example.grails

            import org.apache.grails.core.cli.GrailsApplicationCommand

            class InsideCommand implements GrailsApplicationCommand {

                boolean handle() {
                    println "KIND application context=${applicationContext != null} args=${args}"
                    true
                }
            }
            '''.stripIndent()
        new File(commands, 'BuiltCommand.groovy').text = '''\
            package example.grails

            import org.apache.grails.core.cli.GradleCommand
            import org.apache.grails.core.cli.GrailsApplicationCommand

            class BuiltCommand implements GrailsApplicationCommand, GradleCommand {

                boolean handle() {
                    println "KIND gradle context=${applicationContext != null} args=${args}"
                    true
                }
            }
            '''.stripIndent()
        File plain = new File(commands, 'PlainCommand.groovy')
        plain.text = plainCommand('first')
        // the wrapper of the application, with a line in front that writes down what it was started with
        File wrapper = new File(dir, 'gradlew')
        File real = new File(dir, 'gradlew.real')
        Files.move(wrapper.toPath(), real.toPath())
        wrapper.text = '#!/bin/sh\nhere="$(dirname "$0")"\necho "$*" >> "$here/gradle-runs.txt"\nexec "$here/gradlew.real" "$@"\n'
        wrapper.setExecutable(true, false)
        // the framework of this build is in a repository that is a directory, whose jars a build uses where they are
        safeRepository()

        when: 'the standalone command is run by its name'
        int exit = ForgeCli.execute(beanContext, 'plain', 'one', '--flag=x')

        then: 'the build listed the commands, which compiles the project and writes down the class path, and the command ran as a process'
        assert exit == 0 : err.toString() + out.toString()
        err.toString().readLines()[0] == '| Listing the commands of the application and its plugins, of which plain may be one, with the Gradle task grailsCommandCatalog'
        err.toString().readLines().contains('| Running the command plain of the application, as a process of its own, without Gradle')
        out.toString().readLines().contains("KIND standalone first context=false args=[one] flag=x base=${dir.canonicalPath}".toString())
        runs() == 1
        standaloneFile().commands.plain == 'application'

        when: 'it is run again, and nothing has changed'
        out.reset()
        err.reset()
        long before = System.nanoTime()
        exit = ForgeCli.execute(beanContext, 'plain', 'two')
        long standaloneMillis = (System.nanoTime() - before).intdiv(1_000_000)

        then: 'Gradle is not started at all, and the application is not either'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 1
        err.toString().readLines()[0] == '| Running the command plain of the application, as a process of its own, without Gradle'
        !err.toString().contains('Gradle task')
        out.toString().readLines().contains("KIND standalone first context=false args=[two] flag=null base=${dir.canonicalPath}".toString())
        // nothing of an application that starts
        !out.toString().contains('Grails application running')
        !(out.toString() + err.toString()).contains('Tomcat')

        when: 'the command fails'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'plain', 'fail')

        then: 'the command line ends with 1, and still no Gradle'
        exit == 1
        runs() == 1
        err.toString().readLines().last().endsWith('The command did not succeed: it ended with exit code 1')

        when: 'the source of the command is changed'
        plain.text = plainCommand('second')
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'plain', 'three')

        then: 'the build runs once before the command, which is the new one: no class is run that is older than its source'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 2
        err.toString().readLines()[0].startsWith('| Building the project before plain is run without Gradle, with the Gradle task grailsStandaloneCommands: ')
        out.toString().readLines().contains("KIND standalone second context=false args=[three] flag=null base=${dir.canonicalPath}".toString())

        when: 'the Gradle command is run by its name, by a daemon that is running'
        out.reset()
        err.reset()
        before = System.nanoTime()
        exit = ForgeCli.execute(beanContext, 'built', 'one')
        long gradleMillis = (System.nanoTime() - before).intdiv(1_000_000)

        then: 'the build runs it, without the application'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 3
        err.toString().readLines().contains('| Running the command built of the application, with the Gradle task runCommand')
        out.toString().readLines().contains('KIND gradle context=false args=[one]')

        when: 'the application command is run by its name'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'inside', 'one')

        then: 'the build runs it in the application'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 4
        out.toString().readLines().contains('KIND application context=true args=[one]')

        when: 'the standalone command is run with run-command'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'run-command', 'plain', 'four')

        then: 'the build runs it, without the application'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 5
        err.toString().readLines().contains('| Running the command plain of the application, with the Gradle task runCommand')
        out.toString().readLines().contains("KIND standalone second context=false args=[four] flag=null base=${dir.canonicalPath}".toString())

        when: 'a standalone command of a plugin is run for a domain class the build has not compiled'
        new File(dir, 'grails-app/domain/example/grails').mkdirs()
        new File(dir, 'grails-app/domain/example/grails/Book.groovy').text = 'package example.grails\n\nclass Book {\n\n    String title\n}\n'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'generate-controller', 'example.grails.Book')

        then: 'it has the run time and command line dependencies of the project and nothing of the application, and Gradle is not run for it'
        assert exit == 0 : err.toString() + out.toString()
        runs() == 5
        standaloneFile().commands.'generate-controller' == 'dependencies'
        err.toString().readLines()[0].startsWith('| Running the command generate-controller of org.apache.grails:grails-scaffolding-cli:')
        err.toString().readLines()[0].endsWith(', as a process of its own, without Gradle')
        new File(dir, 'grails-app/controllers/example/grails/BookController.groovy').isFile()

        when: 'a tool asks how the commands are run'
        out.reset()
        err.reset()
        ForgeCli.execute(beanContext, 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString().readLines().last())

        then:
        ((List<Map>) described.commands).findAll { it.origin == 'application' }.collectEntries { [(it.name): it.target] } ==
                [built: 'gradle', inside: 'application', plain: 'standalone']
        ((List<Map>) described.commands).find { it.name == 'generate-controller' }.target == 'standalone'
        runs() == 5

        cleanup: 'how long the same work took each way, for whoever reads the report of the specification'
        originalOut?.println("TIMING standalone command, no Gradle: ${standaloneMillis} ms; Gradle command, running daemon: ${gradleMillis} ms")
    }

    private static String plainCommand(String version) {
        return """\
            package example.grails

            import org.apache.grails.core.cli.GrailsApplicationCommand
            import org.apache.grails.core.cli.StandaloneCommand

            class PlainCommand implements GrailsApplicationCommand, StandaloneCommand {

                boolean handle() {
                    println "KIND standalone ${version} context=\${applicationContext != null} args=\${args} flag=\${executionContext.commandLine.optionValue('flag')} base=\${executionContext.baseDir.canonicalPath}"
                    !args.contains('fail')
                }
            }
            """.stripIndent()
    }

    // how often the Gradle wrapper of the application was started
    private int runs() {
        File file = new File(dir, 'gradle-runs.txt')
        return file.isFile() ? file.readLines().size() : 0
    }

    private Map standaloneFile() {
        return (Map) new JsonSlurper().parse(new File(dir, StandaloneCommandFile.LOCATION))
    }

    // names the repository of the framework as a directory whose files are used whoever owns them, where it is one
    private static void safeRepository() {
        String repository = System.getenv('GRAILS_REPO_URL')
        if (repository == null || repository.trim().isEmpty()) {
            return
        }
        try {
            Path directory = repository.startsWith('file:') ? Path.of(URI.create(repository)) : Path.of(repository)
            if (directory.isAbsolute() && Files.isDirectory(directory)) {
                ConsoleProject.setSafeDirectories(directory.toRealPath().toString())
            }
        } catch (RuntimeException ignored) {
            // a repository that is no directory has its files in the Gradle user home
        }
    }
}
