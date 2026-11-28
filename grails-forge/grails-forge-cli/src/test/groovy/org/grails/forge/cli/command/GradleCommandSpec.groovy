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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Predicate
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.ProcessUser
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.Handshake
import org.grails.forge.cli.ForkedCli
import org.grails.forge.cli.InteractiveShell
import org.grails.forge.cli.SpecWaits
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.terminal.impl.DumbTerminal
import org.opentest4j.TestAbortedException
import spock.lang.IgnoreIf
import spock.lang.Unroll

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
        // says in the directory of the handshake of the feature that it has started, and waits until it is released
        tasks.register('hang') {
            def directory = providers.gradleProperty('handshake')
            doLast {
                def handshake = new File(directory.get())
                def part = new File(handshake, 'started.part')
                part.text = ProcessHandle.current().pid() + '\\n'
                java.nio.file.Files.move(part.toPath(), new File(handshake, 'started').toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                while (handshake.isDirectory() && !new File(handshake, 'release-hang').exists() && !new File(handshake, 'release-all').exists()) {
                    Thread.sleep(50)
                }
            }
        }
        '''.stripIndent()

    PrintStream originalOut
    PrintStream originalErr
    String originalOs
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        // whatever the environment of whoever runs this names as directories to use
        ConsoleProject.setSafeDirectories('')
        originalErr = System.err
        originalOs = System.getProperty('os.name')
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('os.name', originalOs)
        ConsoleProject.setOwnedByUser(null)
        ConsoleProject.setSafeDirectories(null)
        ProcessUser.setCurrent(null)
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

    void "outside a project Gradle is run where there is a Gradle build, and nothing is run where there is none"() {
        given:
        plainProject(dir)
        new File(dir, 'grails-app').deleteDir()

        when: 'the directory is the root of a build, as that of a build of several projects is'
        int exit = ForgeCli.execute(beanContext, 'gradle', 'show', '--no-daemon')

        then:
        exit == 0
        errors() == []
        out.toString().contains('show ran')

        when: 'a directory with a wrapper and no settings is no build'
        out.reset()
        new File(dir, 'settings.gradle').delete()
        // on Windows the Gradle that has just ended can hold a file of the directory for a moment longer
        SpecWaits.until('the removal of the .gradle directory the build left') { new File(dir, '.gradle').deleteDir() && !new File(dir, '.gradle').exists() }
        exit = ForgeCli.execute(beanContext, 'gradle', 'show')

        then:
        exit == 1
        errors() == ["| Error gradle is a command of a Grails project; there is none in ${dir.canonicalPath} or above it".toString()]
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
        errors() == ["| Error gradle is a command of a Grails project; there is none in ${new File(checkout, 'sub').canonicalPath} or above it in the same repository".toString()]

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
    @IgnoreIf({ os.windows })
    void "a wrapper that #what is a failure of the command, said in one line"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        File wrapper = new File(dir, 'gradlew')
        wrapper.bytes = content
        wrapper.setExecutable(executable, false)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors().size() == 1
        said.any { String start -> errors()[0].startsWith("| Error ${start.replace('WRAPPER', new File(dir.canonicalFile, 'gradlew').path)}") }
        !out.toString().contains('Usage:')
        !out.toString().contains('\tat ')

        and: 'the command is not left waiting for it'
        !GradleRunner.running

        where:
        what                                          | executable | content                                              | said
        'names a program that is not there'           | true       | '#!/nonexistent/interpreter\n'.bytes                 | ['The Gradle wrapper WRAPPER could not be started: ']
        'is a directory of a program that is no file' | true       | '#!/\n'.bytes                                        | ['The Gradle wrapper WRAPPER could not be started: ', 'Gradle did not succeed: it ended with exit code ']
        // the system refuses to run it, and a shell is then asked to, as for a script without a first line
        'is no program and no script'                 | true       | ([0x7f, 0x45, 0x4c, 0x46, 0, 1, 2, 3] as byte[])     | ['The Gradle wrapper WRAPPER could not be started: ', 'Gradle did not succeed: it ended with exit code ']
        'is no script and cannot be executed'         | false      | ([0x7f, 0x45, 0x4c, 0x46, 0, 1, 2, 3] as byte[])     | ['Gradle did not succeed: it ended with exit code ']
        'ends as soon as it is started, with 3'       | true       | '#!/bin/sh\nexit 3\n'.bytes                          | ['Gradle did not succeed: it ended with exit code 3']
        'ends at once and cannot be executed'         | false      | 'exit 4\n'.bytes                                     | ['Gradle did not succeed: it ended with exit code 4']
    }

    @IgnoreIf({ os.windows })
    void "a wrapper that ends at once without a failure is a build that succeeded, whatever it did"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = ''
        wrapper.setExecutable(false, false)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        errors() == []
        !GradleRunner.running
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
        '-Pgiven=two words\\'          | '-Pgiven=two words\\'
        ''                             | '(empty)'
    }

    @Unroll
    void "on Windows an argument that ends with a backslash and has no space is one a batch file gets as it is: #argument"() {
        expect:
        !GradleRunner.notForABatchFile(argument)

        where:
        argument << ['-Pgiven=C:\\dir\\', 'build', '-Pgiven=two words', '--project-dir=..\\other']
    }

    @Unroll
    void "on Windows a project whose path a batch file would not be started with as it is, is not built: #name"() {
        given:
        File project = new File(dir, name)
        new File(project, 'grails-app').mkdirs()
        new File(project, 'gradlew.bat').text = '@echo the wrapper ran\r\n'
        System.setProperty('user.dir', project.canonicalPath)
        System.setProperty('os.name', 'Windows 11')

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains(' Gradle cannot be run from here for ')
        errors()[0].contains("${File.separator}${name}${File.separator}gradlew.bat: the Gradle wrapper of Windows is a batch file, and a path with one of ")
        errors()[0].contains('Gradle was not run')
        !out.toString().contains('could not be started')

        where:
        name << ['a&b', '100%', 'x^y']
    }

    @Unroll
    @IgnoreIf({ os.windows })
    void "a project above the current directory that is not the user's own is not used, and its wrapper is not run: #what"() {
        given: 'a project with a wrapper, as anyone can put one into a directory that others work below'
        new File(dir, 'grails-app').mkdirs()
        wrapper(dir)
        File below = new File(dir, 'work/any')
        below.mkdirs()
        System.setProperty('user.dir', below.canonicalPath)
        File another = notOwned == '.' ? dir : new File(dir, notOwned)
        ConsoleProject.setOwnedByUser({ Path path -> path != another.canonicalFile.toPath() } as Predicate<Path>)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].startsWith(said.replace('DIR', dir.canonicalPath).replace('BELOW', below.canonicalPath))
        errors()[0].contains("${another.canonicalPath} is owned by ")
        errors()[0].contains(', not by the user who runs the command')
        errors()[0].endsWith(ends.replaceAll(/\bDIR\b/, java.util.regex.Matcher.quoteReplacement(dir.canonicalPath)))
        !out.toString().contains('the wrapper ran')
        !out.toString().contains('Usage:')

        when: 'the command is run in the project, which is then the one the user chose'
        out.reset()
        System.setProperty('user.dir', dir.canonicalPath)
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        out.toString().contains('the wrapper ran with build')

        where:
        what                                  | notOwned     | said                                                              | ends
        'the directory is another user\'s'    | '.'          | '| Error The Grails project in DIR, above BELOW, is not used: '  | 'run the command in DIR to use this one, or name DIR in the environment variable GRAILS_SAFE_DIRECTORY. gradle is a command of a Grails project, and was not run'
        'the grails-app is another user\'s'   | 'grails-app' | '| Error The Grails project in DIR, above BELOW, is not used: '  | 'run the command in DIR to use this one, or name DIR in the environment variable GRAILS_SAFE_DIRECTORY. gradle is a command of a Grails project, and was not run'
        'the wrapper is another user\'s'      | 'gradlew'    | '| Error The Gradle wrapper DIR/gradlew is not run for the project in DIR: ' | 'Gradle was not run, since the command runs the Gradle of the project and no other'
    }

    @IgnoreIf({ os.windows })
    void "a project above that is the user's own is used, and a record another user put beside it makes it one that is not"() {
        given:
        new File(dir, 'grails-app/controllers').mkdirs()
        wrapper(dir)
        System.setProperty('user.dir', new File(dir, 'grails-app/controllers').canonicalPath)

        when: 'what the system says of the files this specification created'
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        out.toString().contains('the wrapper ran with build')

        when:
        out.reset()
        File record = new File(dir, 'grails-forge-cli.yml')
        record.text = 'applicationType: web\ndefaultPackage: planted\nfeatures: []\n'
        ConsoleProject.setOwnedByUser({ Path path -> path != record.canonicalFile.toPath() } as Predicate<Path>)
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains("is not used: ${record.canonicalPath} is owned by ")
        !out.toString().contains('the wrapper ran')
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "the user who runs the command owns what the command creates, and not what the system has"() {
        expect:
        ConsoleProject.isOwnedByUser(dir.toPath())
        ConsoleProject.isOwnedByUser(Files.createFile(dir.toPath().resolve('created')))
        !ConsoleProject.isOwnedByUser(Path.of('/'))
        !ConsoleProject.isOwnedByUser(Path.of('/bin/sh'))
        !ConsoleProject.isOwnedByUser(dir.toPath().resolve('missing'))
    }

    @Unroll
    @IgnoreIf({ os.windows })
    void "a wrapper above the project is run only from the root of a build that is the user's own: #what"() {
        given: 'a project in the current directory, and a wrapper in the directory above it'
        File project = new File(dir, 'victim')
        new File(project, 'grails-app').mkdirs()
        wrapper(dir)
        if (settings != null) {
            new File(dir, settings).text = "include 'victim'\n"
        }
        System.setProperty('user.dir', project.canonicalPath)
        ConsoleProject.setOwnedByUser({ Path path -> !(path.fileName.toString() in notOwned) && (dirOwned || path != dir.canonicalFile.toPath()) } as Predicate<Path>)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == (said == null ? 0 : 1)
        out.toString().contains('the wrapper ran') == (said == null)
        said == null || (errors().size() == 1 && errors()[0].startsWith("| Error The Gradle wrapper ${dir.canonicalPath}/gradlew is not run for the project in ${project.canonicalPath}: ${said.replace('DIR', dir.canonicalPath)}"))
        said == null || errors()[0].endsWith('. Gradle was not run, since the command runs the Gradle of the project and no other')

        where:
        what                               | settings              | dirOwned | notOwned    | said
        'no settings'                      | null                  | true     | []          | 'DIR is above the project and has no settings.gradle or settings.gradle.kts, so it is not the root of a build the project is part of'
        'settings, another user\'s root'   | 'settings.gradle'     | false    | []          | 'DIR is owned by '
        'settings, another user\'s script' | 'settings.gradle.kts' | true     | ['gradlew'] | 'DIR/gradlew is owned by '
        'settings, all the user\'s own'    | 'settings.gradle.kts' | true     | []          | null
    }

    @IgnoreIf({ os.windows })
    void "a wrapper that is a link is run when it leads to a file beside it, and not when it leads out of the project"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        File outside = Files.createTempDirectory('grailsforgeoutside').toFile()
        wrapper(outside)
        Files.createSymbolicLink(new File(dir, 'gradlew').toPath(), new File(outside, 'gradlew').toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors() == ["| Error The Gradle wrapper ${dir.canonicalPath}/gradlew is not run for the project in ${dir.canonicalPath}: it is a link to a file outside ${dir.canonicalPath}. " +
                'Gradle was not run, since the command runs the Gradle of the project and no other']
        !out.toString().contains('the wrapper ran')

        when:
        out.reset()
        new File(dir, 'gradlew').delete()
        new File(dir, 'tools').mkdirs()
        wrapper(new File(dir, 'tools'))
        Files.createSymbolicLink(new File(dir, 'gradlew').toPath(), Path.of('tools/gradlew'))
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        out.toString().contains('the wrapper ran with build')

        cleanup:
        outside?.deleteDir()
    }

    @Unroll
    @IgnoreIf({ os.windows })
    void "a wrapper that is a link, in a project above the current directory, is held to what its file is: #what"() {
        given: 'a project of this user whose wrapper is a link, and the command run in a directory below the project'
        new File(dir, 'grails-app').mkdirs()
        File below = new File(dir, 'work')
        below.mkdirs()
        System.setProperty('user.dir', below.canonicalPath)
        File outside = Files.createTempDirectory(dir.parentFile.toPath(), 'grailsforgeoutside').toFile()
        wrapper(outside)
        new File(dir, 'tools').mkdirs()
        wrapper(new File(dir, 'tools'))
        if (secondName) {
            new File(dir, 'tools/gradlew').delete()
            Files.createLink(new File(dir, 'tools/gradlew').toPath(), new File(outside, 'gradlew').toPath())
        }
        Files.createSymbolicLink(new File(dir, 'gradlew').toPath(), Path.of(target.replace('OUTSIDE', outside.canonicalPath).replace('BESIDE', outside.name).replace('GIVEN', dir.absolutePath)))
        if (notOwned != null) {
            ConsoleProject.setOwnedByUser({ Path path -> path != dir.canonicalFile.toPath().resolve(notOwned) && ProcessUser.current().owns(path) } as Predicate<Path>)
        }
        if (safe) {
            ConsoleProject.setSafeDirectories(dir.canonicalPath)
        }

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == (said == null ? 0 : 1)
        out.toString().contains('the wrapper ran with build') == (said == null)
        said == null || (errors().size() == 1 && errors()[0].contains(said.replace('DIR', dir.canonicalPath)))
        said == null || errors()[0].endsWith('Gradle was not run, since the command runs the Gradle of the project and no other')

        cleanup:
        outside?.deleteDir()

        where:
        what                                                 | target             | secondName | notOwned        | safe  | said
        'a file of this user below the project'              | 'tools/gradlew'    | false      | null            | false | null
        'a file of this user, named by its whole path'       | 'GIVEN/tools/gradlew' | false   | null            | false | null
        'a file outside the project'                         | 'OUTSIDE/gradlew'  | false      | null            | false | 'DIR/gradlew is not run for the project in DIR: it is a link to a file outside DIR'
        'a file that leaves the project and comes back'      | '../BESIDE/gradlew' | false     | null            | false | 'DIR/gradlew is not run for the project in DIR: it is a link to a file outside DIR'
        'a file below the project that is another user\'s'   | 'tools/gradlew'    | false      | 'tools/gradlew' | false | 'DIR/tools/gradlew is owned by '
        'a link of another user to a file of this user'      | 'tools/gradlew'    | false      | 'gradlew'       | false | 'DIR/gradlew is owned by '
        'a file below the project with another name elsewhere' | 'tools/gradlew'  | true       | null            | false | 'DIR/tools/gradlew has another name elsewhere (a hard link)'
        'another user\'s file, in a directory named as safe' | 'tools/gradlew'    | false      | 'tools/gradlew' | true  | null
        'nothing that is there'                              | 'missing'          | false      | null            | false | 'has no Gradle wrapper: there is no gradlew in it or in a directory above it'
        'a directory'                                        | 'tools'            | false      | null            | false | 'has no Gradle wrapper: there is no gradlew in it or in a directory above it'
    }

    @Unroll
    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "a wrapper above that is a link is not run when another user can change where the link leads: #what"() {
        given: 'a project of this user whose wrapper is a link, and a file of this user in it that is no wrapper'
        new File(dir, 'grails-app').mkdirs()
        File below = new File(dir, 'work')
        below.mkdirs()
        System.setProperty('user.dir', below.canonicalPath)
        new File(dir, 'tools').mkdirs()
        wrapper(new File(dir, 'tools'))
        new File(dir, 'notes').mkdirs()
        new File(dir, 'notes/written.txt').text = 'echo "a file that is no wrapper ran"\n'
        arrange.call(dir)

        when: 'what the system says, with nothing replaced'
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains(said.replace('DIR', dir.canonicalPath))
        errors()[0].contains("name ${dir.canonicalPath} in the environment variable GRAILS_SAFE_DIRECTORY to run it all the same")
        !out.toString().contains(' ran')

        when: 'the directory is named as one to use'
        out.reset()
        ConsoleProject.setSafeDirectories(dir.canonicalPath)
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        out.toString().contains(' ran')

        where:
        what                                                         | said                                                    | arrange
        'its file is in a directory everybody can write to'          | 'DIR/tools can be written to by everybody'              | { File project ->
            Files.createSymbolicLink(new File(project, 'gradlew').toPath(), Path.of('tools/gradlew'))
            Files.setPosixFilePermissions(new File(project, 'tools').toPath(), PosixFilePermissions.fromString('rwxrwxrwx'))
        }
        'a directory on the way to its file is one everybody can write to' | 'DIR/tools can be written to by everybody'        | { File project ->
            new File(project, 'tools/bin').mkdirs()
            wrapper(new File(project, 'tools/bin'))
            Files.createSymbolicLink(new File(project, 'gradlew').toPath(), Path.of('tools/bin/gradlew'))
            Files.setPosixFilePermissions(new File(project, 'tools').toPath(), PosixFilePermissions.fromString('rwxrwxrwx'))
        }
        'it leads to a second link, which was pointed at another file of this user' | 'it is a link that leads to its file through another link' | { File project ->
            // what anyone can do in a directory everybody can write to: the file the first link names is replaced by a link
            Files.setPosixFilePermissions(new File(project, 'tools').toPath(), PosixFilePermissions.fromString('rwxrwxrwx'))
            new File(project, 'tools/gradlew').delete()
            Files.createSymbolicLink(new File(project, 'tools/gradlew').toPath(), Path.of('../notes/written.txt'))
            Files.createSymbolicLink(new File(project, 'gradlew').toPath(), Path.of('tools/gradlew'))
        }
        'it leads through a directory that is a link'                | 'it is a link that leads to its file through another link' | { File project ->
            Files.createSymbolicLink(new File(project, 'linked').toPath(), Path.of('tools'))
            Files.createSymbolicLink(new File(project, 'gradlew').toPath(), Path.of('linked/gradlew'))
        }
    }

    @Unroll
    @IgnoreIf({ os.windows })
    void "a wrapper above the project that is a link is run from the root of a build when its file is the user's own too: #what"() {
        given: 'a project in the current directory, and in the root of the build above it a wrapper that is a link'
        File project = new File(dir, 'app')
        new File(project, 'grails-app').mkdirs()
        new File(dir, 'settings.gradle').text = "include 'app'\n"
        new File(dir, 'tools').mkdirs()
        wrapper(new File(dir, 'tools'))
        Files.createSymbolicLink(new File(dir, 'gradlew').toPath(), Path.of('tools/gradlew'))
        System.setProperty('user.dir', project.canonicalPath)
        if (notOwned != null) {
            ConsoleProject.setOwnedByUser({ Path path -> path != dir.canonicalFile.toPath().resolve(notOwned) && ProcessUser.current().owns(path) } as Predicate<Path>)
        }

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == (said == null ? 0 : 1)
        out.toString().contains('the wrapper ran with build') == (said == null)
        said == null || (errors().size() == 1 && errors()[0].contains("${dir.canonicalPath}/${said}"))

        where:
        what                              | notOwned        | said
        'a file of this user'             | null            | null
        'a file of another user'          | 'tools/gradlew' | 'tools/gradlew is owned by '
        'a directory of another user'     | 'tools'         | 'tools is owned by '
    }

    void "where the search for a project ended is what the command says: at the directories it looks at, or where there are no more"() {
        given: 'more directories than are looked at, none of them a project'
        File deep = new File(dir, (1..(ConsoleProject.MAX_LEVELS + 2)).collect { int level -> "l${level}" }.join('/'))
        deep.mkdirs()

        when:
        System.setProperty('user.dir', deep.canonicalPath)
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors() == ["| Error gradle is a command of a Grails project; there is none in ${deep.canonicalPath} or in the 32 directories above it, which is as far as is looked".toString()]

        when: 'a project whose wrapper would be further up than is looked'
        out.reset()
        new File(deep, 'grails-app').mkdirs()
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors() == ["| Error The project in ${deep.canonicalPath} has no Gradle wrapper: there is no ${wrapperName} in it or in one of the 32 directories above it, which is as far as is looked. " +
                'Gradle was not run, since the command runs the Gradle of the project and no other']

        when: 'a directory with nothing above it but the file system'
        out.reset()
        System.setProperty('user.dir', dir.canonicalPath)
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors() == ["| Error gradle is a command of a Grails project; there is none in ${dir.canonicalPath} or above it".toString()]

        where:
        wrapperName = System.getProperty('os.name').toLowerCase().contains('win') ? 'gradlew.bat' : 'gradlew'
    }

    @IgnoreIf({ os.windows })
    void "what Gradle writes reaches the output as the bytes it is"() {
        given: 'a wrapper that writes what is no text in the encoding of the output'
        new File(dir, 'grails-app').mkdirs()
        wrapper(dir, 'printf \'before \\377\\376\\200\\342\\202 after\\n\'\nprintf \'error \\377\\n\' >&2\n')

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')
        List<Integer> written = out.toByteArray().collect { byte each -> each & 0xff }

        then:
        exit == 0
        Collections.indexOfSubList(written, [0xff, 0xfe, 0x80, 0xe2, 0x82]) > -1
        Collections.indexOfSubList(written, 'error '.bytes.toList().collect { byte each -> each & 0xff } + [0xff, 0x0a]) > -1
        !out.toString('UTF-8').contains('Error')
    }

    @IgnoreIf({ os.windows })
    void "when the command is cancelled every process the wrapper started is ended, and the command says it was stopped"() {
        given: 'a wrapper that starts a process and stays the script it is, as one that does not become Gradle; neither ends until the feature lets it'
        new File(dir, 'grails-app').mkdirs()
        stubWrapper(dir, 'hold child &\nsay started $!\nwait\n')
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'gradle', 'build')) })

        when:
        command.start()
        long child = handshake.awaitStep('started').number(0)
        boolean ran = SpecWaits.alive(child)
        command.interrupt()
        SpecWaits.join(command, 'the end of the command')

        then:
        ran
        exit.get() == 1
        errors() == ['| Error Gradle was stopped before it finished, because the command was cancelled']

        and: 'what the script started is gone, though nothing released it'
        Handshake.awaitExit(child)
    }

    void "the shell asks under the name the command line was started under, takes exit with spaces around it, and ends its last line when the input ends"() {
        given:
        ByteArrayOutputStream written = new ByteArrayOutputStream()
        List<List<String>> lines = [].asSynchronized()
        Closure<Integer> shell = { String typed ->
            // a terminal that reads the stream itself: one with a pty behind it is closed when the stream ends, before a line of it was read
            Terminal terminal = new DumbTerminal(new ByteArrayInputStream(typed.getBytes('UTF-8')), written)
            try {
                InteractiveShell started = new InteractiveShell(ForgeCli.createCommandLine(beanContext, true),
                        { String[] args -> lines.add(args.toList()) } as Consumer<String[]>, null)
                assert started.prompt == '@|blue grails-forge-cli>|@ '
                return started.start(terminal)
            } finally {
                terminal.close()
            }
        }

        when: 'a command, and then exit as it is typed with spaces'
        int exit = shell.call('stats\n  exit  \nnever\n')

        then:
        exit == 0
        lines == [['stats']]

        when: 'the input ends at the prompt'
        written.reset()
        lines.clear()
        exit = shell.call('stats\n')

        then: 'what follows the shell starts on a line of its own'
        exit == 0
        lines == [['stats']]
        written.toString().endsWith('\n')
    }

    void "in a JVM that is not the shell's own the interrupt of the terminal ends the shell after the command, with the exit code of an interrupt, and not the JVM"() {
        given:
        Terminal terminal = new DumbTerminal(new ByteArrayInputStream('stats\nnever\n'.getBytes('UTF-8')), new ByteArrayOutputStream())
        List<List<String>> lines = [].asSynchronized()
        InteractiveShell shell = new InteractiveShell(ForgeCli.createCommandLine(beanContext, true), { String[] args ->
            lines.add(args.toList())
            // Ctrl-C while a command runs that does not wait for Gradle
            terminal.raise(Terminal.Signal.INT)
        } as Consumer<String[]>, null) {

            @Override
            protected void interruptedOutsideGradle() {
                throw new IllegalStateException('the JVM would have been ended')
            }
        }
        shell.endsJvm = false

        when:
        int exit = shell.start(terminal)

        then:
        exit == 130
        lines == [['stats']]
        // the interrupt was for the command, and is not left on the thread
        !Thread.interrupted()

        cleanup:
        terminal?.close()
    }

    @IgnoreIf({ os.windows })
    void "in the interactive shell the interrupt of the terminal cancels the command that runs, and the shell goes on"() {
        given: 'a shell on a terminal that is none, and a wrapper that does not end by itself'
        new File(dir, 'grails-app').mkdirs()
        stubWrapper(dir, 'hold child &\nsay started $!\nwait\n')
        Terminal terminal = TerminalBuilder.builder().dumb(true).system(false)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()).build()
        List<Integer> exits = [].asSynchronized()
        InteractiveShell shell = new InteractiveShell(ForgeCli.createCommandLine(beanContext, true),
                { String[] args -> exits.add(ForgeCli.execute(beanContext, args)) } as Consumer<String[]>, null)
        Thread line = new Thread({
            shell.execute(terminal, 'gradle', 'build')
            // the next line of the shell, on the same thread
            new File(dir, 'gradlew').text = '#!/bin/sh\necho "the wrapper ran with $*"\n'
            shell.execute(terminal, 'gradle', 'next')
        })

        when: 'Ctrl-C while the command runs'
        line.start()
        long child = handshake.awaitStep('started').number(0)
        terminal.raise(Terminal.Signal.INT)
        SpecWaits.join(line, 'the end of the two lines of the shell')

        then: 'the command was stopped, and the one after it ran'
        exits == [1, 0]
        errors() == ['| Error Gradle was stopped before it finished, because the command was cancelled']
        out.toString().contains('the wrapper ran with next')
        Handshake.awaitExit(child)

        cleanup:
        terminal?.close()
    }

    void "in the interactive shell the interrupt of the terminal during a command that does not run Gradle ends the shell, as it does at the prompt"() {
        given: 'a command that waits for something else than Gradle'
        Terminal terminal = TerminalBuilder.builder().dumb(true).system(false)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()).build()
        CountDownLatch running = new CountDownLatch(1)
        CountDownLatch left = new CountDownLatch(1)
        AtomicInteger interrupted = new AtomicInteger()
        InteractiveShell shell = new InteractiveShell(ForgeCli.createCommandLine(beanContext, true), { String[] args ->
            running.countDown()
            try {
                left.await(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
            } catch (InterruptedException ignored) {
                interrupted.incrementAndGet()
            }
        } as Consumer<String[]>, null) {

            @Override
            protected void interruptedOutsideGradle() {
                left.countDown()
            }
        }
        Thread line = new Thread({ shell.execute(terminal, 'create-app', 'shop') })

        when:
        line.start()
        SpecWaits.await(running, 'the start of the command')
        terminal.raise(Terminal.Signal.INT)
        SpecWaits.join(line, 'the end of the line of the shell')

        then: 'the shell was told to end, and the thread of the command was left alone'
        left.count == 0
        interrupted.get() == 0

        cleanup:
        terminal?.close()
    }

    @IgnoreIf({ os.windows })
    void "a wrapper that another process ends was not stopped by the command, which says with what it ended, in one line"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        stubWrapper(dir, 'hold child &\nsay started $!\nwait\n')
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'gradle', 'build')) })

        when: 'the wrapper itself is signalled, and neither the command nor this virtual machine'
        command.start()
        Handshake.Step started = handshake.awaitStep('started')
        List<Long> pids = [started.pid, started.number(0)]
        ProcessHandle.of(pids[0]).get().destroy()
        SpecWaits.join(command, 'the end of the command')

        then:
        exit.get() == 1
        errors() == ['| Error Gradle did not succeed: it ended with exit code 143']

        cleanup:
        pids?.each { long pid -> ProcessHandle.of(pid).ifPresent { ProcessHandle handle -> handle.destroyForcibly() } }
    }

    @IgnoreIf({ os.windows || !ForkedCli.available() })
    void "the CLI as a program gives Gradle its own output, and says that Gradle was stopped when the program is ended"() {
        given: 'a wrapper that says what its output is, and writes what is no text'
        new File(dir, 'grails-app').mkdirs()
        wrapper(dir, 'if [ -f /dev/stdout ]; then echo FILE; elif [ -p /dev/stdout ]; then echo PIPE; else echo OTHER; fi\n' +
                'printf \'\\377\\376\\200 bytes\\n\'\nprintf \'error \\377\\n\' >&2\n')
        File written = new File(dir, 'out.bin')
        File errors = new File(dir, 'err.bin')

        when: 'the output of the program is a file'
        Process program = ForkedCli.builder(dir, [], 'gradle', 'build').redirectOutput(written).redirectError(errors).start()
        int exit = SpecWaits.exited(program, 'the end of the program')

        then: 'the wrapper wrote to that file itself, and nothing between changed a byte'
        exit == 0
        written.bytes == ('FILE\n'.bytes.toList() + [(byte) 0xff, (byte) 0xfe, (byte) 0x80] + ' bytes\n'.bytes.toList()) as byte[]
        errors.bytes == ('error '.bytes.toList() + [(byte) 0xff, (byte) 0x0a]) as byte[]

        when: 'the program is ended while a wrapper runs that does not end by itself'
        stubWrapper(dir, 'hold child &\nsay started $!\necho started\nwait\n')
        program = ForkedCli.builder(dir, [], 'gradle', 'build').redirectOutput(written).redirectErrorStream(true).start()
        long child = handshake.awaitStep('started').number(0)
        program.destroy()
        exit = SpecWaits.exited(program, 'the end of the program')

        then:
        exit == 143
        written.text.readLines().findAll { String line -> line.contains('| Error') || line.contains('Gradle') } ==
                ['| Error Gradle was stopped before it finished, because the command was cancelled']
        Handshake.awaitExit(child)

        cleanup:
        program?.destroyForcibly()
    }

    @IgnoreIf({ os.windows })
    void "what has another name elsewhere is not the user's own, whoever owns it: #what"() {
        given: 'a project of this user with a wrapper, and one file of it that is a second name of another file'
        new File(dir, 'grails-app').mkdirs()
        wrapper(dir)
        new File(dir, 'gradle/wrapper').mkdirs()
        new File(dir, 'gradle/wrapper/gradle-wrapper.jar').text = 'jar'
        File elsewhere = Files.createTempFile(dir.parentFile.toPath(), 'grailsforgeelsewhere', '.txt').toFile()
        elsewhere.text = "rootProject.name = 'x'\n"
        File linked = new File(dir, name)
        linked.delete()
        try {
            Files.createLink(linked.toPath(), system ? Path.of('/etc/hosts').toRealPath() : elsewhere.toPath())
        } catch (IOException | UnsupportedOperationException e) {
            throw new TestAbortedException("this system gives no second name to that file: ${e.message}")
        }
        File below = new File(dir, 'work')
        below.mkdirs()
        System.setProperty('user.dir', below.canonicalPath)

        when: 'what the system says, with nothing replaced'
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains("${linked.canonicalPath} ${said}")
        errors()[0].contains("${dir.canonicalPath} in the environment variable GRAILS_SAFE_DIRECTORY")
        !out.toString().contains('the wrapper ran')

        cleanup:
        elsewhere?.delete()

        where:
        what                                          | name                                | system | said
        'a record, of a file of this user'            | 'grails-forge-cli.yml'              | false  | 'has another name elsewhere (a hard link)'
        'the wrapper, of a file of this user'         | 'gradlew'                           | false  | 'has another name elsewhere (a hard link)'
        'the jar of the wrapper'                      | 'gradle/wrapper/gradle-wrapper.jar' | false  | 'has another name elsewhere (a hard link)'
        'a record, of a file of the system'           | 'grails-cli.yml'                    | true   | (System.getProperty('user.name') == 'root' ? 'has another name elsewhere (a hard link)' : 'is owned by ')
    }

    @IgnoreIf({ os.windows })
    void "a wrapper above the project is not run when the settings beside it are a second name of another file"() {
        given:
        File project = new File(dir, 'app')
        new File(project, 'grails-app').mkdirs()
        wrapper(dir)
        File elsewhere = Files.createTempFile(dir.parentFile.toPath(), 'grailsforgeelsewhere', '.gradle').toFile()
        elsewhere.text = "include 'app'\n"
        Files.createLink(new File(dir, 'settings.gradle').toPath(), elsewhere.toPath())
        System.setProperty('user.dir', project.canonicalPath)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains("${dir.canonicalPath}/settings.gradle has another name elsewhere (a hard link)")
        !out.toString().contains('the wrapper ran')

        when: 'the settings are a file of their own'
        out.reset()
        new File(dir, 'settings.gradle').delete()
        new File(dir, 'settings.gradle').text = "include 'app'\n"
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        out.toString().contains('the wrapper ran with build')

        cleanup:
        elsewhere?.delete()
    }

    @IgnoreIf({ os.windows })
    void "a project above in a directory that everybody can write to is not used, unless the user names the directory as one to use"() {
        given: 'a project of this user whose directory its group can write to, as a umask of 002 leaves it'
        new File(dir, 'grails-app').mkdirs()
        wrapper(dir)
        File below = new File(dir, 'work')
        below.mkdirs()
        Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString('rwxrwxr-x'))
        Files.setPosixFilePermissions(new File(dir, 'grails-app').toPath(), PosixFilePermissions.fromString('rwxrwxr-x'))
        System.setProperty('user.dir', below.canonicalPath)

        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then: 'the group is taken to be the user\'s own'
        exit == 0
        out.toString().contains('the wrapper ran with build')

        when: 'everybody can write to it'
        out.reset()
        Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString('rwxr-xrwx'))
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors() == ["| Error The Grails project in ${dir.canonicalPath}, above ${below.canonicalPath}, is not used: ${dir.canonicalPath} can be written to by everybody, " +
                "so what is in it need not be the user's (chmod o-w keeps the others out). A project above the current directory is used when it is the user's own; " +
                "run the command in ${dir.canonicalPath} to use this one, or name ${dir.canonicalPath} in the environment variable GRAILS_SAFE_DIRECTORY. gradle is a command of a Grails project, and was not run"]

        when: 'the environment names the directory, or every directory'
        out.reset()
        ConsoleProject.setSafeDirectories(named.replace('DIR', dir.canonicalPath).replace(':', File.pathSeparator))
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        (exit == 0) == used
        out.toString().contains('the wrapper ran with build') == used

        where:
        named                  | used
        'DIR'                  | true
        '/nowhere:DIR:'        | true
        '*'                    | true
        'DIR/work'             | false
        'DIR/*'                | false
        '*:/nowhere'           | false
        '..'                   | false
        ''                     | false
    }

    @IgnoreIf({ os.windows })
    void "who runs the command is taken from the process, in a set order, and from nothing a directory for temporary files or a home says"() {
        given:
        File created = new File(dir, 'created')
        created.text = 'x'
        ProcessUser first = ProcessUser.establish([])
        ProcessUser second = ProcessUser.establish([ProcessUser.UID])
        ProcessUser third = ProcessUser.establish([ProcessUser.UID, ProcessUser.PROC])
        ProcessUser none = ProcessUser.establish(ProcessUser.SOURCES)

        expect: 'the first source that answers is the one, and each says the same of a file'
        ProcessUser.SOURCES == [ProcessUser.UID, ProcessUser.PROC, ProcessUser.NAME]
        first.source == ProcessUser.UID
        second.source == (new File('/proc/self').exists() ? ProcessUser.PROC : ProcessUser.NAME)
        third.source == ProcessUser.NAME
        [first, second, third].every { ProcessUser user -> user.known && user.owns(created.toPath()) && user.owns(dir.toPath()) }
        System.getProperty('user.name') == 'root' || [first, second, third].every { ProcessUser user -> !user.owns(Path.of('/')) }

        and: 'when none answers nothing is the user\'s'
        !none.known
        none.source == null
        !none.owns(created.toPath())

        when: 'nothing can be created where temporary files go, and the home is a directory of the system'
        new File(dir, 'grails-app').mkdirs()
        wrapper(dir)
        File below = new File(dir, 'work')
        below.mkdirs()
        System.setProperty('user.dir', below.canonicalPath)
        String temporary = System.getProperty('java.io.tmpdir')
        String home = System.getProperty('user.home')
        System.setProperty('java.io.tmpdir', '/nonexistent')
        System.setProperty('user.home', '/')
        int exit
        try {
            exit = ForgeCli.execute(beanContext, 'gradle', 'build')
        } finally {
            System.setProperty('java.io.tmpdir', temporary)
            System.setProperty('user.home', home)
        }

        then: 'the project of this user above is still this user\'s'
        exit == 0
        out.toString().contains('the wrapper ran with build')
    }

    @IgnoreIf({ os.windows })
    void "when the user who runs the command cannot be established, nothing above the current directory is used, and the command says why"() {
        given: 'a project of this user, in the root of a build with a wrapper, and no source that says who the user is'
        File project = new File(dir, 'app')
        new File(project, 'grails-app/controllers').mkdirs()
        new File(dir, 'settings.gradle').text = "include 'app'\n"
        wrapper(dir)
        ProcessUser.setCurrent(ProcessUser.establish(ProcessUser.SOURCES))

        when: 'the command is run below the project'
        System.setProperty('user.dir', new File(project, 'grails-app/controllers').canonicalPath)
        int exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then: 'the project is not used, and the line says that it is the user that is not known, and not who owns the directory'
        exit == 1
        errors().size() == 1
        errors()[0].contains("is not used: the user who runs the command could not be established (asked for: ${ProcessUser.SOURCES.join(', ')}), " +
                "so ${project.canonicalPath} is not known to be the user's own")
        !errors()[0].contains('is owned by')
        errors()[0].contains("name ${project.canonicalPath} in the environment variable GRAILS_SAFE_DIRECTORY")
        !out.toString().contains('the wrapper ran')

        when: 'in the project, which the user chose, the wrapper above it is the next thing that is not known to be the user\'s'
        out.reset()
        System.setProperty('user.dir', project.canonicalPath)
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains("${dir.canonicalPath}/gradlew is not run for the project in ${project.canonicalPath}: the user who runs the command could not be established")
        errors()[0].contains("so ${dir.canonicalPath} is not known to be the user's own; name ${dir.canonicalPath} in the environment variable GRAILS_SAFE_DIRECTORY")
        !out.toString().contains('the wrapper ran')

        when: 'the directory of the wrapper is named as one to use'
        out.reset()
        ConsoleProject.setSafeDirectories(dir.canonicalPath)
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        out.toString().contains('the wrapper ran with build')

        when: 'a project that has its wrapper, and is the current directory, needs nobody to be known'
        out.reset()
        ConsoleProject.setSafeDirectories('')
        wrapper(project, 'echo "the wrapper of the project ran"\n')
        new File(dir, 'settings.gradle').delete()
        new File(project, 'settings.gradle').text = "rootProject.name = 'app'\n"
        exit = ForgeCli.execute(beanContext, 'gradle', 'build')

        then:
        exit == 0
        out.toString().contains('the wrapper of the project ran')
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }

    // a wrapper that is a script of a few lines, so that Gradle itself is not started
    private static void wrapper(File directory, String script = 'echo "the wrapper ran with $*"\n') {
        File wrapper = new File(directory, 'gradlew')
        wrapper.text = '#!/bin/sh\n' + script
        wrapper.setExecutable(true, false)
    }

    @IgnoreIf({ os.windows })
    void "when the command is cancelled Gradle is stopped, and so is the build"() {
        given: 'a task that waits until the feature lets it go'
        plainProject(dir)
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'gradle', 'hang', '-q', '--no-daemon', "-Phandshake=${handshake.directory}".toString())) })

        when:
        command.start()
        long building = handshake.awaitStarted()
        command.interrupt()
        SpecWaits.join(command, 'the end of the command')

        then:
        exit.get() == 1
        out.toString().contains('| Error Gradle was stopped before it finished, because the command was cancelled')

        and: 'the JVM the task ran in ends as well, though nothing released the task'
        Handshake.awaitExit(building)
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
