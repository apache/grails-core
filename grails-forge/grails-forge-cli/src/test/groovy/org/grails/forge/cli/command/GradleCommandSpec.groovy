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
package org.grails.forge.cli.command

import java.util.concurrent.atomic.AtomicInteger
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * {@code gradle}, which runs the Gradle wrapper of the project the current directory is in. The
 * projects here have the wrapper of a generated application and a build of a few tasks that needs
 * nothing of the framework, so Gradle resolves nothing.
 */
class GradleCommandSpec extends CommandSpec implements CommandFixture {

    static final String BUILD = '''\
        tasks.register('other') {
            doLast { println "other ran in ${project.path}" }
        }
        tasks.register('show') {
            dependsOn 'other'
            def given = providers.gradleProperty('given').orElse('(none)')
            def path = project.path
            def directory = layout.projectDirectory.asFile.name
            doLast { println "show ran in ${path}, directory ${directory}, given=[${given.get()}]" }
        }
        tasks.register('fail') {
            doLast { throw new GradleException('this build fails on purpose') }
        }
        tasks.register('hang') {
            def started = layout.projectDirectory.file('started.txt').asFile
            doLast {
                started.text = ProcessHandle.current().pid() + '\\n'
                Thread.sleep(180000)
            }
        }
        '''.stripIndent()

    PrintStream originalOut
    PrintStream originalErr
    String originalOs
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        originalOs = System.getProperty('os.name')
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('os.name', originalOs)
    }

    @IgnoreIf({ os.windows })
    void "every argument reaches Gradle as the one argument it is, the options too"() {
        given:
        plainProject(dir)

        when: 'an argument with spaces, quotes and what a shell would expand, and options the commands of the CLI have themselves'
        int exit = ForgeCli.execute(beanContext, 'gradle', 'show', '-x', 'other', '-q', '--stacktrace',
                '-Pgiven=two  words "double" \'single\' $HOME * \\ -x end', '--no-daemon')

        then:
        exit == 0
        out.toString().contains('given=[two  words "double" \'single\' $HOME * \\ -x end]')

        and: 'the task -x names was left out, by Gradle'
        !out.toString().contains('other ran')
        !out.toString().contains('Error')
    }

    @IgnoreIf({ os.windows })
    void "two tasks in one argument are no two tasks"() {
        given:
        plainProject(dir)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'show other', '--no-daemon')

        then: 'Gradle says so, and the command fails with one line of its own'
        exit == 1
        out.toString().contains("'show other' not found")
        out.toString().readLines().findAll { it.contains('| Error') } == ['| Error Gradle did not succeed: it ended with exit code 1']
        !out.toString().contains('\tat ')
    }

    void "what Gradle writes is shown, and a build that fails is a failure of the command"() {
        given:
        plainProject(dir)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'other', 'fail', '--no-daemon')

        then:
        exit == 1
        out.toString().contains('other ran in :')
        out.toString().contains('this build fails on purpose')
        out.toString().contains('BUILD FAILED')
        out.toString().readLines().findAll { it.contains('| Error') } == ['| Error Gradle did not succeed: it ended with exit code 1']

        and: 'the usage of the command is not shown for it'
        !out.toString().contains('Usage:')
    }

    void "an option after the name of the command is an option of Gradle"() {
        given:
        plainProject(dir)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', '--help')

        then: 'the help is the help of Gradle'
        exit == 0
        out.toString().contains('--exclude-task')
        !out.toString().contains('ARGS')
    }

    void "without arguments the command says how it is used, and has no options of its own"() {
        given:
        plainProject(dir)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle')

        then:
        exit == 2
        out.toString().contains('Say what Gradle is to do')
        out.toString().contains('Usage:')
        out.toString().contains('gradle ARGS...')
        !out.toString().contains('--stacktrace')
        !out.toString().contains('--verbose')
        !new File(dir, '.gradle').exists()
    }

    void "the command is listed with the commands that need no project"() {
        when:
        int exit = ForgeCli.execute(beanContext, '--help')

        then:
        exit == 0
        out.toString().contains('Runs Gradle in the Grails project of the current directory')
    }

    void "outside a project nothing is run, also where there is a Gradle build"() {
        given:
        plainProject(dir)
        new File(dir, 'grails-app').deleteDir()

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'show')

        then:
        exit == 1
        out.toString().contains('| Error There is no Grails project here: neither ')
        out.toString().contains('Gradle was not run')
        !out.toString().contains('Usage:')
        !out.toString().contains('show ran')
        !new File(dir, '.gradle').exists()
    }

    @Unroll
    void "a directory is a project by #marker, and a project without a Gradle wrapper is not built with another Gradle"() {
        given:
        File file = new File(dir, marker)
        if (directory) {
            file.mkdirs()
        } else {
            file.parentFile.mkdirs()
            file.text = 'anything\n'
        }

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        out.toString().contains('has no Gradle wrapper: there is no gradlew')
        out.toString().contains('Gradle was not run')
        !out.toString().contains('There is no Grails project here')

        where:
        marker                 | directory
        'grails-app'           | true
        '.grails/project.yml'  | false
        'grails-forge-cli.yml' | false
        'grails-cli.yml'       | false
    }

    void "the project is looked for above the current directory, and Gradle runs in the project"() {
        given:
        plainProject(dir)
        File deep = new File(dir, 'grails-app/controllers/example')
        deep.mkdirs()
        System.setProperty('user.dir', deep.absolutePath)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'show', '-q', '--no-daemon')

        then:
        exit == 0
        out.toString().contains("show ran in :, directory ${dir.name}, given=[(none)]")
    }

    void "in a build of several projects the wrapper above is run for the project, and for no other"() {
        given:
        plainProject(dir)
        new File(dir, 'grails-app').deleteDir()
        new File(dir, 'settings.gradle').text = "rootProject.name = 'several'\ninclude 'app', 'beside'\n"
        ['app', 'beside'].each { String name ->
            new File(dir, name).mkdirs()
            new File(dir, "${name}/build.gradle").text = BUILD
        }
        new File(dir, 'app/grails-app').mkdirs()
        System.setProperty('user.dir', new File(dir, 'app/grails-app').absolutePath)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'show', '-q', '--no-daemon')

        then:
        exit == 0
        out.toString().contains('show ran in :app, directory app, given=[(none)]')
        !out.toString().contains('ran in :beside')
        !out.toString().contains('show ran in :,')
    }

    void "neither a project nor a wrapper is taken from above the repository"() {
        given: 'a project and a wrapper above a checkout'
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'gradlew').text = '#!/bin/sh\necho the wrapper outside ran\n'
        new File(dir, 'gradlew.bat').text = 'echo the wrapper outside ran\r\n'
        File checkout = new File(dir, 'checkout')
        new File(checkout, '.git').mkdirs()
        new File(checkout, 'sub').mkdirs()

        when: 'the checkout has no project'
        System.setProperty('user.dir', new File(checkout, 'sub').absolutePath)
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        out.toString().contains('There is no Grails project here')

        when: 'the checkout has a project and no wrapper'
        out.reset()
        new File(checkout, 'sub/grails-app').mkdirs()
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        out.toString().contains('has no Gradle wrapper')
        !out.toString().contains('the wrapper outside ran')
    }

    void "an argument that names a file with an at sign is read from the file, as on every command line, and two at signs are one for Gradle"() {
        given:
        plainProject(dir)
        File arguments = new File(dir, 'arguments.txt')
        arguments.text = 'show\n-q\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', "@${arguments.absolutePath}".toString(), '--no-daemon')

        then:
        exit == 0
        out.toString().contains('show ran in :')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'gradle', '@@show', '--no-daemon')

        then:
        exit == 1
        out.toString().contains("'@show' not found")
    }

    @IgnoreIf({ os.windows })
    void "a wrapper that is not executable is run all the same"() {
        given:
        plainProject(dir)
        new File(dir, 'gradlew').setExecutable(false, false)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'show', '-q', '--no-daemon')

        then:
        exit == 0
        out.toString().contains('show ran in :')
    }

    @Unroll
    void "on Windows an argument a batch file would not get as it is, is refused before Gradle is run: #argument"() {
        given:
        plainProject(dir)
        System.setProperty('os.name', 'Windows 11')

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'show', argument)

        then:
        exit == 2
        out.toString().contains("The argument ${shown} cannot be given to Gradle from here")
        out.toString().contains('Usage:')
        !out.toString().contains('\u001b[35m')
        !new File(dir, '.gradle').exists()

        where:
        argument                       | shown
        '-Pgiven=100%'                 | '-Pgiven=100%'
        '-Pgiven="quoted"'             | '-Pgiven="quoted"'
        '-Pgiven=a&b'                  | '-Pgiven=a&b'
        '-Pgiven=@|red x|@\u001b[35m'  | '-Pgiven=@ |red x|@?[35m'
    }

    @IgnoreIf({ os.windows })
    void "when the command is cancelled Gradle is stopped, and so is the build"() {
        given:
        plainProject(dir)
        File started = new File(dir, 'started.txt')
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'gradle', 'hang', '-q', '--no-daemon')) })

        when:
        command.start()
        new PollingConditions(timeout: 120, delay: 0.5).eventually {
            assert started.isFile() && started.text.endsWith('\n')
        }
        long building = started.text.trim() as long
        command.interrupt()
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 1
        out.toString().contains('| Error Gradle was stopped before it finished, because the command was cancelled')

        and: 'the JVM the task ran in ends as well, long before the task would have'
        new PollingConditions(timeout: 60, delay: 0.5).eventually {
            assert !ProcessHandle.of(building).map { ProcessHandle handle -> handle.alive }.orElse(false)
        }
    }

    // the wrapper of a generated application, and around it a build that needs nothing of Grails
    private void plainProject(File directory) {
        generateProject(ApplicationType.WEB)
        directory.eachFile { File file ->
            if (!(file.name in ['gradlew', 'gradlew.bat', 'gradle'])) {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        new File(directory, 'gradle').eachFile { File file ->
            if (file.name != 'wrapper') {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        new File(directory, 'settings.gradle').text = "rootProject.name = 'plain'\n"
        new File(directory, 'build.gradle').text = BUILD
        new File(directory, 'grails-app').mkdirs()
    }
}
