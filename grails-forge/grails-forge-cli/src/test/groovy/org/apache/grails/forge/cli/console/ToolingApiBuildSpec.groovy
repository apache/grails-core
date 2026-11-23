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
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * The commands that are a build of the project, run through the Tooling API of Gradle against a build
 * of Gradle itself. The other specifications of the commands have a script in the place of the Gradle
 * wrapper that writes down what it was given, and have the commands run the wrapper for that
 * ({@code GRAILS_GRADLE_RUNNER=process}, which the build sets for the tests): they hold each command
 * to the arguments it builds. This one holds the other way of running Gradle to the same: the build
 * writes down what it was asked, as Gradle read it, and each task what its options were. The wrapper
 * script of the project here writes down that it was run, and is not, unless a feature says so.
 * The features ask for the Tooling API ({@code GRAILS_GRADLE_RUNNER=tooling-api}), which a command
 * line that runs one command is not run through otherwise: {@code GradleRunnerChoiceSpec} holds which
 * way is taken where.
 *
 * <p>The build needs nothing of Grails. Its daemon is no daemon of whoever runs the specification,
 * see {@link IsolatedGradle}; the features share one.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class ToolingApiBuildSpec extends CommandSpec {

    static final String RECORDER = '''\
        def start = gradle.startParameter
        new File(settingsDir, 'requested.txt') << ('requested=' + start.taskRequests.collectMany { it.args }.collect { "<${it}>" }.join('') +
                ' system=' + start.systemPropertiesArgs.findAll { !it.key.startsWith('user.') && it.key != 'file.encoding' } + ' project=' + start.projectProperties +
                ' log=' + start.logLevel + ' stacktrace=' + start.showStacktrace + ' console=' + start.consoleOutput + '\\n')
        '''.stripIndent()

    static final String BUILD = '''\
        import org.gradle.api.tasks.options.Option

        abstract class Asked extends DefaultTask {

            @Option(option = 'phase', description = 'a phase') @Input @Optional abstract ListProperty<String> getPhase()
            @Option(option = 'tests', description = 'a test') @Input @Optional abstract ListProperty<String> getTests()
            @Option(option = 'rerun-tests', description = 'again') @Input @Optional abstract Property<Boolean> getRerunTests()
            @Option(option = 'debug-jvm', description = 'debug') @Input @Optional abstract Property<Boolean> getDebugJvm()
            @Option(option = 'args-file', description = 'arguments') @Input @Optional abstract Property<String> getArgsFile()
            @Internal abstract RegularFileProperty getRan()

            @TaskAction
            void run() {
                ran.get().asFile << "${name} phase=${phase.get()} tests=${tests.get()} rerun=${rerunTests.getOrElse(false)} debug=${debugJvm.getOrElse(false)} " +
                        "argsFile=${argsFile.present} env=${System.getProperty('grails.env')}\\n"
                println "ran ${name}"
            }
        }
        ['classes', 'clean', 'assemble', 'jar', 'publish', 'console', 'shell', 'runScript', 'runCommand', 'testApp', 'bootRun'].each { String each ->
            tasks.register(each, Asked) { ran = layout.projectDirectory.file('ran.txt') }
        }
        def directory = projectDir
        tasks.register('fails') {
            doLast { throw new GradleException('this build fails as it is asked to') }
        }
        tasks.register('reads') {
            doLast {
                new File(directory, 'read.txt').text = "line=${System.in.newReader().readLine()} variable=${System.getenv('GRAILS_NON_INTERACTIVE')} " +
                        "ofTheCommandLine=${System.getProperty('grails.spec.ofTheCommandLine')} given=${System.getProperty('grails.spec.given')}\\n"
            }
        }
        tasks.register('waits') {
            doLast {
                new File(directory, 'waiting.txt').text = 'waiting\\n'
                try {
                    Thread.sleep(600000)
                } finally {
                    new File(directory, 'ended.txt').text = 'ended\\n'
                }
            }
        }
        '''.stripIndent()

    static final String WRAPPER_THAT_SAYS_SO = '#!/bin/sh\nprintf "<%s>" "$@" >> wrapper-was-run.txt\necho >> wrapper-was-run.txt\n'

    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    InputStream originalIn
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        originalIn = System.in
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        gradle.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'settings.gradle') << RECORDER
        new File(dir, 'build.gradle').text = BUILD
        // a plugin project, which has the commands that package and publish one
        new File(dir, 'src/main/groovy/plain').mkdirs()
        new File(dir, 'src/main/groovy/plain/PlainGrailsPlugin.groovy').text = 'package plain\n\nclass PlainGrailsPlugin {\n}\n'
        // the Gradle of the project is the one its wrapper names, and the script is not what runs it
        new File(dir, 'gradlew').text = WRAPPER_THAT_SAYS_SO
        GradleBuild.setRunner(GradleBuild.TOOLING_API)
    }

    void cleanup() {
        GradleBuild.setRunner(null)
        GradleBuild.setNonInteractive(false)
        GradleBuild.outputOfProcess(null, null)
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setIn(originalIn)
        System.clearProperty('grails.spec.ofTheCommandLine')
    }

    void cleanupSpec() {
        gradle.close()
    }

    @Unroll
    void "#command is asked of Gradle through the Tooling API as #requested, and the options reach the task"() {
        when:
        int exit = ForgeCli.execute(beanContext, command as String[])

        then: 'the build succeeded, and no wrapper was run for it'
        exit == 0
        errors() == []
        !new File(dir, 'wrapper-was-run.txt').exists()

        and: 'Gradle read what the command gives the wrapper where it runs that, argument for argument and in that order'
        said('requested.txt').replaceAll(/--args-file=[^>]+/, '--args-file=FILE') == requested

        and: 'the options were those of the task'
        said('ran.txt') == ran

        and: 'the daemon is the one of this specification'
        gradle.daemons().size() == 1

        where:
        command                                                                                 | requested                                                                                                                                                    | ran
        ['compile']                                                                             | 'requested=<:classes> system=[:] project=[:] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto'                                                      | 'classes phase=[] tests=[] rerun=false debug=false argsFile=false env=null'
        ['compile', '--stacktrace', '--verbose', '-Da=b', '--', '--offline']                    | 'requested=<:classes> system=[a:b, grails.full.stacktrace:true] project=[:] log=INFO stacktrace=ALWAYS console=Auto'                                         | 'classes phase=[] tests=[] rerun=false debug=false argsFile=false env=null'
        ['clean']                                                                               | 'requested=<:clean> system=[:] project=[:] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto'                                                        | 'clean phase=[] tests=[] rerun=false debug=false argsFile=false env=null'
        ['war', '--clean']                                                                      | 'requested=<:clean><:assemble> system=[grails.env:production] project=[:] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto'                         | 'clean phase=[] tests=[] rerun=false debug=false argsFile=false env=production\nassemble phase=[] tests=[] rerun=false debug=false argsFile=false env=production'
        ['test-app', '--unit', 'BookSpec', '--tests', 'a.*', '--clean', '--debug-jvm']          | 'requested=<:testApp><--phase><unit><--tests><BookSpec><--tests><a.*><--rerun-tests><--debug-jvm> system=[grails.env:test] project=[:] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto' | 'testApp phase=[unit] tests=[BookSpec, a.*] rerun=true debug=true argsFile=false env=test'
        ['package-plugin']                                                                      | 'requested=<:jar> system=[:] project=[:] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto'                                                          | 'jar phase=[] tests=[] rerun=false debug=false argsFile=false env=null'
        ['publish-plugin']                                                                      | 'requested=<:publish> system=[:] project=[:] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto'                                                      | 'publish phase=[] tests=[] rerun=false debug=false argsFile=false env=null'
        ['run-command', '-Dgrails.env=test', 'migrate', '--to', 'x y']                          | 'requested=<:runCommand><--args-file=FILE> system=[grails.env:test] project=[:] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto'                   | 'runCommand phase=[] tests=[] rerun=false debug=false argsFile=true env=test'
        ['console', '--debug-jvm']                                                              | 'requested=<:console><--debug-jvm> system=[grails.env:development] project=[:] log=QUIET stacktrace=INTERNAL_EXCEPTIONS console=Auto'                        | 'console phase=[] tests=[] rerun=false debug=true argsFile=false env=development'
        ['run-app', '--foreground', '--debug-jvm', '--port', '8081', '-Pa=b', '--plain-output'] | 'requested=<:bootRun><--debug-jvm> system=[grails.env:development, grails.server.port:8081] project=[a:b] log=QUIET stacktrace=INTERNAL_EXCEPTIONS console=Plain' | 'bootRun phase=[] tests=[] rerun=false debug=true argsFile=false env=development'
    }

    void "shell is a session at the terminal, and runs the Gradle wrapper as a process for it"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'shell', '--debug-jvm')

        then:
        exit == 0
        new File(dir, 'wrapper-was-run.txt').text == "<:shell><--debug-jvm><--quiet><-Dgrails.env=development>${pathCheck(dir)}\n"
        !new File(dir, 'requested.txt').exists()
    }

    void "a script is run through the Tooling API by run-script, with its path as the project property the task reads"() {
        given:
        new File(dir, 'a script.groovy').text = 'println 1\n'

        when:
        int exit = ForgeCli.execute(beanContext, 'run-script', 'a script.groovy')

        then:
        exit == 0
        errors() == []
        !new File(dir, 'wrapper-was-run.txt').exists()
        said('requested.txt') == 'requested=<:runScript> system=[grails.env:development] project=[args:\'a script.groovy\'] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto'
    }

    void "a build that fails is the failure of the command, with what Gradle says of it and one line of the command"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'compile', '--', 'fails')

        then:
        exit == 1
        errors() == ['| Error Gradle did not succeed: the build failed']
        out.toString().contains('this build fails as it is asked to')
        out.toString().contains('BUILD FAILED')

        and: 'what Gradle wrote is in the order it wrote it, the two streams as one, and is plain text'
        out.toString().indexOf('ran classes') < out.toString().indexOf('FAILURE: Build failed')
        out.toString().indexOf('FAILURE: Build failed') < out.toString().indexOf('BUILD FAILED')
        out.toString().indexOf('BUILD FAILED') < out.toString().indexOf('| Error Gradle did not succeed')
        !out.toString().contains('\u001b[')

        when: 'the next build is none the worse for it'
        out.reset()
        int next = ForgeCli.execute(beanContext, 'compile')

        then:
        next == 0
        errors() == []
        out.toString().contains('BUILD SUCCESSFUL')
    }

    void "what Gradle does not take as an argument is the failure of the build, in the words of Gradle"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'compile', '--', '--no-such-option')

        then:
        exit == 1
        errors() == ['| Error Gradle did not succeed: the build failed']
        out.toString().contains('--no-such-option')
    }

    void "a build is cancelled when the thread of the command is interrupted, and the daemon stays"() {
        given:
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'compile', '--', 'waits')) })

        when:
        command.start()
        new PollingConditions(timeout: 300, delay: 0.1).eventually {
            assert new File(dir, 'waiting.txt').isFile()
        }

        then: 'the command waits for Gradle, which is what the interactive console goes by'
        GradleBuild.isRunning()

        when:
        command.interrupt()
        command.join(60_000)

        then:
        !command.alive
        exit.get() == 1
        errors() == ['| Error Gradle was stopped before it finished, because the command was cancelled']
        !GradleBuild.isRunning()

        and: 'the build has ended in the daemon, which is still there for the next one'
        new PollingConditions(timeout: 30, delay: 0.1).eventually {
            assert new File(dir, 'ended.txt').isFile()
        }
        gradle.daemons().size() == 1

        when:
        out.reset()
        int next = ForgeCli.execute(beanContext, 'compile')

        then:
        next == 0
        errors() == []
    }

    void "the second run of the same command reuses what the configuration cache holds for it"() {
        given:
        new File(dir, 'gradle.properties') << 'org.gradle.configuration-cache=true\n'
        // what is read while the build is configured would be an input of the entry
        new File(dir, 'settings.gradle').text = "rootProject.name = 'plain'\n"

        when:
        int first = ForgeCli.execute(beanContext, 'run-command', 'migrate', '--to', 'x')
        String stored = out.toString()
        out.reset()
        int second = ForgeCli.execute(beanContext, 'run-command', 'migrate', '--to', 'x')

        then:
        first == 0 && second == 0
        errors() == []
        stored.contains('Configuration cache entry stored')
        out.toString().contains('Configuration cache entry reused')
        said('ran.txt').readLines().size() == 2
    }

    void "the build reads what the command line reads, and has no system property of the command line that was not given to it"() {
        given:
        System.setIn(new ByteArrayInputStream('typed by the user\n'.bytes))
        System.setProperty('grails.spec.ofTheCommandLine', 'set in the JVM of the command line')
        // as a program, whose JVM ends with the command
        GradleBuild.outputOfProcess(originalOut, originalErr)

        when:
        int exit = ForgeCli.execute(beanContext, 'compile', '-Dgrails.spec.given=given', '--', 'reads')

        then:
        exit == 0
        errors() == []
        said('read.txt') == 'line=typed by the user variable=null ofTheCommandLine=null given=given'
    }

    void "in the interactive console a build reads an input that has ended, since what is typed there is the console's"() {
        given:
        System.setIn(new ByteArrayInputStream('typed for the console\n'.bytes))
        GradleBuild.outputOfProcess(originalOut, originalErr)
        ConsoleCommands.setInteractive(true)

        when:
        int exit = ForgeCli.execute(beanContext, 'compile', '--', 'reads')

        then:
        exit == 0
        errors() == []
        said('read.txt') == 'line=null variable=null ofTheCommandLine=null given=null'

        and: 'nothing of it was read for the build'
        System.in.newReader().readLine() == 'typed for the console'

        cleanup:
        ConsoleCommands.setInteractive(false)
        GradleBuild.closeConnections()
    }

    void "where the command line is run in the JVM of a caller a build reads an input that has ended, since the input is the caller's"() {
        given:
        System.setIn(new ByteArrayInputStream('typed for the caller\n'.bytes))

        when:
        int exit = ForgeCli.execute(beanContext, 'compile', '--', 'reads')

        then:
        exit == 0
        said('read.txt') == 'line=null variable=null ofTheCommandLine=null given=null'
        System.in.newReader().readLine() == 'typed for the caller'
    }

    void "with --non-interactive the build reads an input that has ended and is told so in its environment"() {
        given:
        System.setIn(new ByteArrayInputStream('typed by the user\n'.bytes))
        GradleBuild.outputOfProcess(originalOut, originalErr)

        when:
        int exit = ForgeCli.execute(beanContext, '--non-interactive', 'compile', '--', 'reads')

        then:
        exit == 0
        errors() == []
        said('read.txt') == 'line=null variable=true ofTheCommandLine=null given=null'
    }

    void "grails gradle runs the Gradle wrapper as a process, whatever else is run through the Tooling API"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'gradle', 'classes', '--status')

        then:
        exit == 0
        new File(dir, 'wrapper-was-run.txt').text == '<classes><--status>\n'
        !new File(dir, 'requested.txt').exists()
    }

    @Unroll
    void "an argument the program gradle reads itself, #argument, has the command run the Gradle wrapper as a process"() {
        when:
        int exit = ForgeCli.execute(beanContext, (['compile', '--'] + argument) as String[])

        then:
        exit == 0
        new File(dir, 'wrapper-was-run.txt').text == "<:classes>${argument.collect { "<${it}>" }.join('')}${pathCheck(dir)}\n"
        !new File(dir, 'requested.txt').exists()

        where:
        argument << [['--no-daemon'], ['--daemon'], ['--foreground'], ['-g', '/elsewhere'], ['--gradle-user-home=/elsewhere'], ['-p', '.'], ['-Dorg.gradle.jvmargs=-Xmx1g']]
    }

    void "a project without a Gradle wrapper is refused as it is where the wrapper is run, and no Gradle is chosen for it"() {
        given:
        new File(dir, 'gradlew').delete()

        when:
        int exit = ForgeCli.execute(beanContext, 'compile')

        then:
        exit == 1
        errors() == ["| Error The project in ${dir.canonicalPath} has no Gradle wrapper: there is no gradlew in it or in a directory above it. " +
                             'Gradle was not run, since the command runs the Gradle of the project and no other']
        !new File(dir, 'requested.txt').exists()
    }

    @Unroll
    void "a wrapper whose properties #what names no Gradle, and none is chosen in its place"() {
        given:
        File properties = new File(dir, 'gradle/wrapper/gradle-wrapper.properties')
        change(properties)

        when:
        int exit = ForgeCli.execute(beanContext, 'compile')

        then:
        exit == 1
        errors() == ["| Error The Gradle wrapper ${new File(dir, 'gradlew').canonicalPath} names no Gradle: there is no gradle/wrapper/gradle-wrapper.properties beside it that has a distributionUrl. " +
                             'Gradle was not run, since the command runs the Gradle of the project and no other']
        !new File(dir, 'requested.txt').exists()

        where:
        what                           | change
        'are not there'                | { File file -> file.delete() }
        'name no distribution'         | { File file -> file.text = 'distributionBase=GRADLE_USER_HOME\n' }
        'name an empty distribution'   | { File file -> file.text = 'distributionUrl=\n' }
        'are a directory'              | { File file -> file.delete(); file.mkdirs() }
    }

    void "the wrapper of a project that is checked where it is run is checked before a connection is made"() {
        given: 'a project below the directory the wrapper is in, which is then one above the directory the command is run in'
        File project = new File(dir, 'apps/shop')
        new File(project, 'grails-app').mkdirs()
        new File(dir, 'grails-app').deleteDir()
        new File(dir, 'settings.gradle') << "include 'apps:shop'\n"
        new File(project, 'build.gradle').text = "tasks.register('classes') { doLast { println 'ran classes of the shop' } }\n"
        System.setProperty('user.dir', project.absolutePath)
        File properties = new File(dir, 'gradle/wrapper/gradle-wrapper.properties')

        when: 'what names the Gradle of the build is not the user\'s own'
        ConsoleProject.setOwnedByUser { java.nio.file.Path path -> path.toFile().canonicalFile != properties.canonicalFile }
        int exit = ForgeCli.execute(beanContext, 'compile')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].startsWith("| Error The Gradle wrapper ${new File(dir, 'gradlew').canonicalPath} is not run for the project in ${project.canonicalPath}: ${properties.canonicalPath} is owned by ")
        !new File(dir, 'requested.txt').exists()

        when: 'it is'
        ConsoleProject.setOwnedByUser(null)
        out.reset()
        exit = ForgeCli.execute(beanContext, 'compile')

        then: 'the task of the project is run, through the Tooling API'
        exit == 0
        errors() == []
        said('requested.txt') == 'requested=<:apps:shop:classes> system=[:] project=[:] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto'
        out.toString().contains('ran classes of the shop')
        !new File(dir, 'wrapper-was-run.txt').exists()

        cleanup:
        ConsoleProject.setOwnedByUser(null)
    }

    @Unroll
    void "the wrapper is run as a process where #what, since the Tooling API would not look for the Gradle of the build there"() {
        given:
        arrange(dir)
        System.setProperty('user.dir', new File(dir, runIn).absolutePath)

        when:
        int exit = ForgeCli.execute(beanContext, 'compile')

        then:
        exit == 0
        errors() == []
        new File(dir, "${runIn}/wrapper-was-run.txt").text == "<:classes>${pathCheck(new File(dir, runIn))}\n"
        !new File(dir, 'requested.txt').exists()

        where:
        what                                                    | runIn  | arrange
        'the wrapper script is a link to a file of the project' | '.'    | { File root ->
            new File(root, 'tools').mkdirs()
            Files.move(new File(root, 'gradlew').toPath(), new File(root, 'tools/gradlew').toPath())
            Files.createSymbolicLink(new File(root, 'gradlew').toPath(), new File('tools/gradlew').toPath())
        }
        'it stands above a project with settings of its own'    | 'shop' | { File root ->
            new File(root, 'shop/grails-app').mkdirs()
            new File(root, 'grails-app').deleteDir()
            new File(root, 'shop/settings.gradle').text = "rootProject.name = 'shop'\n"
        }
    }

    void "what a batch file cannot be given is no reason to refuse an argument or a project where no batch file is run"() {
        given: 'the project as it is on Windows, as far as what runs Gradle is asked'
        new File(dir, 'gradlew.bat').text = '@rem the wrapper of Windows\r\n'
        List<String> failures = []
        ConsoleProject project = ConsoleProject.find(dir.toPath())
        List<String> arguments = [':classes', '-Dgrails.spec.given=a&b|"c" %d%']

        when: 'through the Tooling API'
        int exit = GradleBuild.of(GradleBuild.Purpose.BUILD, { String message -> failures.add(message) } as Consumer<String>, true).run(project, arguments)

        then: 'the argument reaches the build as it is'
        exit == 0
        failures == []
        said('requested.txt') == 'requested=<:classes> system=[grails.spec.given:a&b|"c" %d%] project=[:] log=LIFECYCLE stacktrace=INTERNAL_EXCEPTIONS console=Auto'

        when: 'by the wrapper as a process'
        GradleBuild.setRunner(GradleBuild.PROCESS)
        GradleBuild.of(GradleBuild.Purpose.BUILD, { String message -> failures.add(message) } as Consumer<String>, true).run(project, arguments)

        then: 'it is refused, as it has been'
        GradleRunner.RefusedArgumentException refused = thrown()
        refused.message.contains('the Gradle wrapper of Windows is a batch file')
    }

    // what the wrapper is given after the arguments of a command that names the tasks of the project as those of the root, see ProjectPathCheck
    private static String pathCheck(File project) {
        return "<--init-script><${new File(project.canonicalFile, '.grails/run/project-path.gradle').path}>"
    }

    private String said(String name) {
        return new File(dir, name).text.trim()
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}
