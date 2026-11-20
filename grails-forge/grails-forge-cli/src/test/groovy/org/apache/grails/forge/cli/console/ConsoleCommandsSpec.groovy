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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.zip.ZipFile
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.command.BaseCommand
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * The commands that build, test and look at the project of the current directory. The projects here
 * have the wrapper of a generated application around a build that needs nothing of the framework: its
 * tasks have the names the commands run and say what Gradle was asked for, so that each case shows
 * the whole Gradle invocation a command line stands for.
 */
class ConsoleCommandsSpec extends CommandSpec implements CommandFixture {

    static final String BUILD = '''\
        import org.gradle.api.tasks.options.Option

        abstract class Recorded extends DefaultTask {

            @Input @Optional @Option(option = 'tests', description = 'The tests to run')
            List<String> tests = []

            @Input @Option(option = 'debug-jvm', description = 'Debug the tests')
            boolean debugJvm = false

            @TaskAction
            void run() {
                println "ran ${name} tests=${tests.collect { '<' + it + '>' }.join('')} debugJvm=${debugJvm}"
                def given = project.gradle.startParameter.systemPropertiesArgs
                if (given.containsKey('fail')) {
                    throw new GradleException('this build fails on purpose')
                }
                if (given.containsKey('hang')) {
                    new File(project.projectDir, 'started.txt').text = ProcessHandle.current().pid() + '\\n'
                    Thread.sleep(180000)
                }
            }
        }

        def start = gradle.startParameter
        println 'requested=' + start.taskNames.collect { '<' + it + '>' }.join('')
        // Gradle puts these there itself
        def own = ['file.encoding', 'user.country', 'user.language', 'user.variant']
        println 'system=' + start.systemPropertiesArgs.findAll { !(it.key in own) }.sort().collect { name, value -> '<' + name + '=' + value + '>' }.join('')
        println "stacktrace=${start.showStacktrace} level=${start.logLevel} continuous=${start.continuous} offline=${start.offline}"
        println "given=[${providers.gradleProperty('given').getOrElse('(none)')}] in ${project.projectDir.name}"

        configurations.create('runtimeClasspath')
        ['test', 'integrationTest', 'cleanTest', 'cleanIntegrationTest', 'classes', 'clean', 'assemble', 'jar', 'publish'].each { String name ->
            tasks.register(name, Recorded)
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
        ConsoleProject.setOwnedByUser(null)
    }

    @Unroll
    void "#line runs Gradle with #requested and #system"() {
        given:
        plainProject(dir)
        new File(dir, 'src/integration-test/groovy').mkdirs()

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 0
        lineOf('requested=') == requested
        lineOf('system=') == system
        lineOf('stacktrace=') == 'INTERNAL_EXCEPTIONS level=LIFECYCLE continuous=false offline=false'
        errors() == []
        said == null || out.toString().contains(said)

        where:
        line                                                                      | requested                                                                     | system                                        | said
        ['compile']                                                               | '<classes>'                                                                   | ''                                            | 'ran classes'
        ['clean']                                                                 | '<clean>'                                                                     | ''                                            | 'ran clean'
        ['war']                                                                   | '<assemble>'                                                                  | '<grails.env=production>'                     | '| Built application to build/libs using environment: production'
        ['package']                                                               | '<assemble>'                                                                  | '<grails.env=production>'                     | '| Built application to build/libs using environment: production'
        ['war', '--clean', '-Dgrails.env=staging']                                | '<clean><assemble>'                                                           | '<grails.env=staging>'                        | '| Built application to build/libs using environment: staging'
        ['assemble', '-clean', '-D', 'other=1']                                   | '<clean><assemble>'                                                           | '<grails.env=production><other=1>'            | null
        ['dependency-report']                                                     | '<dependencies>'                                                              | '<grails.env=development>'                    | 'runtimeClasspath'
        ['dependency-report', 'runtimeClasspath', '-Dgrails.env=test']            | '<dependencies><--configuration><runtimeClasspath>'                           | '<grails.env=test>'                           | 'runtimeClasspath'
        ['test-app']                                                              | '<test><integrationTest>'                                                     | '<grails.env=test>'                           | '| Tests PASSED'
        ['test-app', '--unit']                                                    | '<test>'                                                                      | '<grails.env=test>'                           | null
        ['test-app', '--integration']                                             | '<integrationTest>'                                                           | '<grails.env=test>'                           | null
        ['test-app', '-integration']                                              | '<integrationTest>'                                                           | '<grails.env=test>'                           | null
        ['test-app', '--unit', '--integration']                                   | '<test><integrationTest>'                                                     | '<grails.env=test>'                           | null
        ['test-app', 'example.BookSpec', '--tests', '*Other*', 'Third']           | '<test><--tests><example.BookSpec><--tests><Third><--tests><*Other*><integrationTest><--tests><example.BookSpec><--tests><Third><--tests><*Other*>' | '<grails.env=test>' | 'ran integrationTest tests=<example.BookSpec><Third><*Other*> debugJvm=false'
        ['test-app', '--clean', '--debug-jvm']                                    | '<cleanTest><test><--debug-jvm><cleanIntegrationTest><integrationTest><--debug-jvm>' | '<grails.env=test>'                    | 'ran test tests= debugJvm=true'
        ['test-app', '-clean', '-debug-jvm', '-unit']                             | '<cleanTest><test><--debug-jvm>'                                              | '<grails.env=test>'                           | null
        ['test-app', '-Dgrails.env=ci', '-Dserver.port=0', '-Dflag']              | '<test><integrationTest>'                                                     | '<flag=><grails.env=ci><server.port=0>'       | null
        ['test-app', '--', '--tests', 'Later']                                    | '<test><integrationTest><--tests><Later>'                                     | '<grails.env=test>'                           | 'ran integrationTest tests=<Later> debugJvm=false'
    }

    @Unroll
    void "#line turns the options every command has into the ones of Gradle"() {
        given:
        plainProject(dir)

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 0
        lineOf('requested=') == requested
        lineOf('system=') == system
        lineOf('stacktrace=') == start
        errors() == []

        where:
        line                                 | requested   | system                                                | start
        ['compile', '--stacktrace']          | '<classes>' | '<grails.full.stacktrace=true>'                       | 'ALWAYS level=LIFECYCLE continuous=false offline=false'
        ['test-app', '-x']                   | '<test>'    | '<grails.env=test><grails.full.stacktrace=true>'      | 'ALWAYS level=LIFECYCLE continuous=false offline=false'
        ['clean', '--verbose']               | '<clean>'   | ''                                                    | 'INTERNAL_EXCEPTIONS level=DEBUG continuous=false offline=false'
        ['compile', '--', '--offline', '-q'] | '<classes>' | ''                                                    | 'INTERNAL_EXCEPTIONS level=QUIET continuous=false offline=true'
    }

    @IgnoreIf({ os.windows })
    void "an argument with spaces, quotes and what a shell would expand is one argument for Gradle, in a filter and after the two dashes"() {
        given:
        plainProject(dir)

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', '*BookSpec.saves a "new" book', '-Dnote=it\'s  $HOME *',
                '--', '-Pgiven=two  words "double" \'single\' \\ -x end')

        then:
        exit == 0
        lineOf('requested=') == '<test><--tests><*BookSpec.saves a "new" book>'
        lineOf('system=') == '<grails.env=test><note=it\'s  $HOME *>'
        out.toString().contains('given=[two  words "double" \'single\' \\ -x end]')
        out.toString().contains('ran test tests=<*BookSpec.saves a "new" book> debugJvm=false')
    }

    void "a project without integration tests has its unit tests run, and is told when the integration tests alone are asked for"() {
        given:
        plainProject(dir)

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app')

        then:
        exit == 0
        lineOf('requested=') == '<test>'

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'test-app', '--integration')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains('has no integration tests: there is no src/integration-test directory in it. Gradle was not run')
        !out.toString().contains('requested=')
        !out.toString().contains('Usage:')
    }

    void "a build that fails is a failure of the command, with one line after the report of Gradle"() {
        given:
        plainProject(dir)

        when:
        int exit = ForgeCli.execute(beanContext, 'test-app', '-Dfail=true')

        then:
        exit == 1
        out.toString().contains('this build fails on purpose')
        out.toString().contains('BUILD FAILED')
        errors() == ['| Error Gradle did not succeed: it ended with exit code 1']
        !out.toString().contains('Tests PASSED')
        !out.toString().contains('Usage:')
        !out.toString().contains('\tat ')
    }

    @Unroll
    void "outside a project #command runs nothing and says so"() {
        given:
        plainProject(dir)
        new File(dir, 'grails-app').deleteDir()

        when:
        int exit = ForgeCli.execute(beanContext, command)

        then:
        exit == 1
        errors().size() == 1
        errors()[0].startsWith('| Error There is no Grails project here: neither ')
        errors()[0].endsWith(notDone)
        !out.toString().contains('Usage:')
        !new File(dir, '.gradle').exists()
        !new File(dir, 'build').exists()

        where:
        command             | notDone
        'compile'           | 'Gradle was not run'
        'clean'             | 'Gradle was not run'
        'assemble'          | 'Gradle was not run'
        'war'               | 'Gradle was not run'
        'package'           | 'Gradle was not run'
        'dependency-report' | 'Gradle was not run'
        'test-app'          | 'Gradle was not run'
        'stats'             | 'Nothing was counted'
        'bug-report'        | 'No bug report was created'
    }

    void "a project without a Gradle wrapper is not built with another Gradle"() {
        given:
        new File(dir, 'grails-app').mkdirs()

        when:
        int exit = ForgeCli.execute(beanContext, 'compile')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains('has no Gradle wrapper: there is no gradlew')
    }

    void "a system property given to a command is one for Gradle, and none of the CLI itself"() {
        given:
        new File(dir, 'grails-app').mkdirs()

        when:
        int exit = ForgeCli.execute(beanContext, 'war', '-Dconsole.commands.spec=given', '-Dgrails.env=staging')

        then:
        exit == 1
        errors()[0].contains('has no Gradle wrapper')
        System.getProperty('console.commands.spec') == null
        System.getProperty('grails.env') != 'staging'
        !beanContext.getBeansOfType(Map, true, false).values().any { Map map -> map.containsKey('console.commands.spec') }
    }

    @Unroll
    void "#line is a mistake in the use of the command: nothing is run and the usage is shown"() {
        given:
        plainProject(dir)

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 2
        out.toString().contains(said)
        out.toString().contains('Usage:')
        !out.toString().contains('requested=')
        !new File(dir, '.gradle').exists()

        where:
        line                              | said
        ['compile', 'classes']            | "Unmatched argument at index 1: 'classes'"
        ['clean', '--offline']            | "Unknown option: '--offline'"
        ['test-app', '--rerun']           | "Unknown option: '--rerun'"
        ['test-app', '--tests']           | "Missing required parameter for option '--tests'"
        ['dependency-report', 'a', 'b']   | "Unmatched argument at index 2: 'b'"
        ['stats', 'now']                  | "Unmatched argument at index 1: 'now'"
        ['bug-report', '--overwrite']     | "Unknown option: '--overwrite'"
        ['test-app', '-D', 'BookSpec']    | '-D is followed by the name of a system property and its value, as in -Dgrails.env=test: -D BookSpec names none'
        ['test-app', '-D=x']              | '-D is followed by the name of a system property and its value, as in -Dgrails.env=test: -D=x names none'
        ['test-app', '-D', '=x']          | '-D is followed by the name of a system property and its value, as in -Dgrails.env=test: -D =x names none'
        ['assemble', '-D']                | '-D is followed by the name of a system property and its value, as in -Dgrails.env=test: -D names none'
        ['war', '-Dgrails.env=a', '-D', '--clean'] | '-D is followed by the name of a system property and its value, as in -Dgrails.env=test: -D --clean names none'
    }

    void "the collection or the map of an option is the command's own, whatever type the field has, and no bean of the application"() {
        given: 'a command whose options have the types the application has beans of'
        CommandLine commandLine = ForgeCli.createCommandLine(beanContext, true, false)
        Throwaway command = new Throwaway()
        commandLine.addSubcommand(command)

        when:
        int exit = ForgeCli.execute(commandLine, 'throwaway', '-m', 'console.commands.spec=map', '-c', 'console.commands.spec', '-q', 'console.commands.spec',
                '-p', 'console.commands.spec=properties')

        then:
        exit == 0
        command.map == ['console.commands.spec': 'map']
        command.collection as List == ['console.commands.spec']
        command.queue as List == ['console.commands.spec']
        command.properties == ['console.commands.spec': 'properties'] as Properties

        and: 'the application was asked for none of them'
        [Map, Collection, Queue, Properties].every { Class type ->
            !beanContext.getBeansOfType(type, true, false).values().any { Object bean ->
                bean.is(command.map) || bean.is(command.collection) || bean.is(command.queue) || bean.is(command.properties)
            }
        }
        !beanContext.getBeansOfType(Map, true, false).values().any { Map map -> map.containsKey('console.commands.spec') }
        !beanContext.getBeansOfType(Collection, true, false).values().any { Collection collection ->
            try {
                return collection.contains('console.commands.spec')
            } catch (ClassCastException | NullPointerException ignored) {
                return false
            }
        }
        System.getProperty('console.commands.spec') == null

        when: 'the command line is read again, as the next line of the interactive shell is'
        Throwaway next = new Throwaway()
        CommandLine again = ForgeCli.createCommandLine(beanContext, true, false)
        again.addSubcommand(next)
        exit = ForgeCli.execute(again, 'throwaway', '-m', 'other=1')

        then: 'nothing of the line before is left'
        exit == 0
        next.map == [other: '1']
        next.collection == null
    }

    @Unroll
    void "on Windows an argument a batch file would not get as it is, is refused before Gradle is run: #line"() {
        given:
        plainProject(dir)
        System.setProperty('os.name', 'Windows 11')

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 2
        out.toString().contains("The argument ${shown} cannot be given to Gradle from here")
        out.toString().contains('Usage:')
        !out.toString().contains('\u001b[35m')
        !new File(dir, '.gradle').exists()

        where:
        line                                               | shown
        ['test-app', 'a&b']                                | 'a&b'
        ['war', '-Dname=@|red x|@\u001b[35m']              | '-Dname=@ |red x|@?[35m'
        ['compile', '--', '-Pgiven="quoted"']              | '-Pgiven="quoted"'
    }

    void "the project is looked for above the current directory, and Gradle runs in the project"() {
        given:
        plainProject(dir)
        File deep = new File(dir, 'grails-app/controllers/example')
        deep.mkdirs()
        System.setProperty('user.dir', deep.absolutePath)

        when:
        int exit = ForgeCli.execute(beanContext, 'compile')

        then:
        exit == 0
        out.toString().contains("given=[(none)] in ${dir.name}")
    }

    void "the commands that package and publish a plugin are there in a plugin project, and run jar and publish"() {
        given:
        plainProject(dir)

        when: 'the project is an application'
        int exit = ForgeCli.execute(beanContext, command)

        then:
        exit == 2
        out.toString().contains("There is no command ${command}")
        !out.toString().contains('requested=')

        when: 'it has the class of a plugin'
        out.reset()
        File plugin = new File(dir, 'src/main/groovy/example/ExampleGrailsPlugin.groovy')
        plugin.parentFile.mkdirs()
        plugin.text = 'package example\n\nclass ExampleGrailsPlugin { }\n'
        exit = ForgeCli.execute(beanContext, command, '--', '-Pgiven=on')

        then:
        exit == 0
        lineOf('requested=') == requested
        lineOf('system=') == ''
        out.toString().contains('given=[on]')

        when: 'the commands of the directory are listed'
        out.reset()
        exit = ForgeCli.execute(beanContext, '--help')

        then:
        exit == 0
        out.toString().contains(command)

        where:
        command          | requested
        'package-plugin' | '<jar>'
        'publish-plugin' | '<publish>'
    }

    void "install runs nothing and says which Gradle task installs a plugin"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'install', '--anything', 'more')

        then:
        exit == 1
        errors() == ['| Error There is no install command any more, and nothing was installed: a plugin is put into the local Maven repository by Gradle. ' +
                             'Run: grails-forge-cli gradle publishToMavenLocal']
        !out.toString().contains('Usage:')
    }

    void "the commands are listed, also outside a project, and the help of one does not run Gradle"() {
        when:
        int exit = ForgeCli.execute(beanContext, '--help')

        then:
        exit == 0
        ['compile', 'clean', 'assemble, war, package', 'dependency-report', 'test-app', 'stats', 'bug-report'].every { String name ->
            out.toString().readLines().any { String line -> line.trim().startsWith(name + ' ') }
        }
        !out.toString().readLines().any { String line -> line.trim().startsWith('install ') }
        !out.toString().contains('package-plugin')
        !out.toString().contains('publish-plugin')

        when:
        out.reset()
        plainProject(dir)
        exit = ForgeCli.execute(beanContext, 'test-app', '--help')

        then:
        exit == 0
        out.toString().contains('--integration')
        out.toString().contains('--continuous')
        out.toString().contains('-D=NAME=VALUE') || out.toString().contains('-D NAME=VALUE')
        !out.toString().contains('requested=')
        !new File(dir, '.gradle').exists()
    }

    @IgnoreIf({ os.windows })
    void "continuous testing is the continuous build of Gradle, and cancelling the command stops Gradle and the build"() {
        given:
        plainProject(dir)
        File started = new File(dir, 'started.txt')
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'test-app', '--continuous', '-Dhang=true')) })

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
        lineOf('requested=') == '<test>'
        lineOf('stacktrace=') == 'INTERNAL_EXCEPTIONS level=LIFECYCLE continuous=true offline=false'
        errors() == ['| Error Gradle was stopped before it finished, because the command was cancelled']
        !out.toString().contains('Tests PASSED')

        and: 'the JVM the task ran in ends as well, long before the task would have'
        new PollingConditions(timeout: 60, delay: 0.5).eventually {
            assert !ProcessHandle.of(building).map { ProcessHandle handle -> handle.alive }.orElse(false)
        }
    }

    void "stats counts the source files and their lines of code by the kind of class, without Gradle"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('grails-app/controllers/example/BookController.groovy', '''\
            package example

            // a comment of one line
            class BookController {
                /* a comment of one line */
                /*
                 * a comment of several
                 */
                def index() { }
            }
            '''.stripIndent())
        source('grails-app/controllers/example/UrlMappings.groovy', 'package example\nclass UrlMappings { }\n')
        source('grails-app/controllers/example/BookInterceptor.groovy', 'package example\nclass BookInterceptor { }\n')
        source('grails-app/controllers/example/notes.txt', 'not counted\n')
        source('grails-app/domain/example/Book.groovy', 'package example\n\nclass Book {\n    String title\n}\n')
        source('grails-app/services/example/BookService.groovy', 'package example\nclass BookService { }\n')
        source('grails-app/services/example/Helper.groovy', 'package example\nclass Helper { }\n')
        source('grails-app/taglib/example/BookTagLib.groovy', 'package example\nclass BookTagLib { }\n')
        source('grails-app/jobs/example/NightlyJob.groovy', 'package example\nclass NightlyJob { }\n')
        source('src/main/groovy/example/Util.groovy', 'package example\nclass Util { }\n')
        source('src/main/groovy/example/Tool.java', 'package example;\nclass Tool { }\n')
        source('src/test/groovy/example/BookSpec.groovy', 'package example\nclass BookSpec { }\n')
        source('src/integration-test/groovy/example/BookIntegrationSpec.groovy', 'package example\nclass BookIntegrationSpec { }\n\n')
        source('src/main/scripts/report.groovy', 'println 1\n')
        source('build/classes/Generated.groovy', 'class Generated { }\n')

        when:
        int exit = ForgeCli.execute(beanContext, 'stats')

        then:
        exit == 0
        out.toString().readLines().findAll { it.startsWith('|') || it.startsWith('+') } == [
                '+----------------------+-------+-------+',
                '| Name                 | Files |  LOC  |',
                '+----------------------+-------+-------+',
                '| Controllers          |     1 |     5 | ',
                '| URL Mappings         |     1 |     2 | ',
                '| Interceptors         |     1 |     2 | ',
                '| Domain Classes       |     1 |     4 | ',
                '| Jobs                 |     1 |     2 | ',
                '| Services             |     1 |     2 | ',
                '| Tag Libraries        |     1 |     2 | ',
                '| Helpers              |     2 |     4 | ',
                '| Unit Tests           |     1 |     2 | ',
                '| Integration Tests    |     1 |     2 | ',
                '| Scripts              |     1 |     1 | ',
                '+----------------------+-------+-------+',
                '| Totals               |    12 |    28 | ',
                '+----------------------+-------+-------+'
        ]
        !new File(dir, '.gradle').exists()
    }

    @IgnoreIf({ os.windows })
    void "stats does not count what a link points to"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('grails-app/domain/example/Book.groovy', 'package example\nclass Book { }\n')
        File outside = Files.createTempDirectory('grailsforgeoutside').toFile()
        new File(outside, 'Secret.groovy').text = 'class Secret { }\n'
        Files.createSymbolicLink(new File(dir, 'grails-app/domain/linked').toPath(), outside.toPath())
        Files.createSymbolicLink(new File(dir, 'grails-app/domain/Linked.groovy').toPath(), new File(outside, 'Secret.groovy').toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'stats')

        then:
        exit == 0
        out.toString().contains('| Domain Classes       |     1 |     2 | ')
        out.toString().contains('| Totals               |     1 |     2 | ')

        cleanup:
        outside?.deleteDir()
    }

    void "bug-report puts the sources of the project into a ZIP file in the build directory, and says what is in it"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('build.gradle', 'version = 1\n')
        source('settings.gradle', 'rootProject.name = "x"\n')
        source('gradle.properties', 'secret=1\n')
        source('grails-app/conf/application.yml', 'dataSource:\n  password: hunter2\n')
        source('grails-app/controllers/example/BookController.groovy', 'package example\nclass BookController { }\n')
        source('src/main/groovy/example/Util.groovy', 'package example\nclass Util { }\n')
        source('src/main/resources/keys/server.p12', 'key')
        source('src/main/resources/.env', 'TOKEN=1\n')
        source('src/main/resources/prod.ENV', 'TOKEN=1\n')
        source('src/main/resources/.env.local', 'TOKEN=1\n')
        source('src/main/groovy/example/Util.groovy~', 'backup\n')
        source('src/main/groovy/.gitignore', '*.class\n')
        source('src/.git/config', 'repository\n')
        source('build/classes/Generated.class', 'compiled\n')
        source('.gradle/cache.bin', 'cache\n')
        source('.grails/project.yml', 'record\n')
        source('.env', 'TOKEN=1\n')

        when:
        int exit = ForgeCli.execute(beanContext, 'bug-report')

        then:
        exit == 0
        archive().isFile()
        entries() == [
                'build.gradle',
                'grails-app/conf/application.yml',
                'grails-app/controllers/example/BookController.groovy',
                'src/main/groovy/example/Util.groovy'
        ]
        new ZipFile(archive()).withCloseable { ZipFile zip -> zip.getInputStream(zip.getEntry('src/main/groovy/example/Util.groovy')).text } == 'package example\nclass Util { }\n'
        out.toString().contains("| Created bug report ZIP: build/${archive().name}")
        out.toString().contains('| It holds 4 file(s): what is under grails-app and src, and build.gradle, as they are')
        out.toString().contains('| Left out: 0 link(s), which are not followed, and 4 file(s) that are keys by their name')
        out.toString().readLines().contains('| Warning The bug report holds the configuration of the application, ' +
                'which is where passwords usually are: grails-app/conf/application.yml. Look through it before the ZIP is given to anyone')
        errors() == []

        and: 'nothing else is left in the build directory, and Gradle was not run'
        new File(dir, 'build').list().sort() == [archive().name, 'classes'].sort()
        new File(dir, '.gradle').list() == ['cache.bin']
    }

    void "bug-report does not replace a ZIP file unless it is told to"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('build.gradle', 'version = 1\n')
        source("build/${archive().name}", 'the report of this morning')

        when:
        int exit = ForgeCli.execute(beanContext, 'bug-report')

        then:
        exit == 1
        errors() == ["| Error build/${archive().name} exists already and was not replaced. Give --force to replace it".toString()]
        archive().text == 'the report of this morning'
        new File(dir, 'build').list() == [archive().name]

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'bug-report', '--force')

        then:
        exit == 0
        entries() == ['build.gradle']
        new File(dir, 'build').list() == [archive().name]
    }

    @IgnoreIf({ os.windows })
    void "bug-report follows no link, neither to a file nor to a directory, in the project or out of it"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('build.gradle', 'version = 1\n')
        source('src/main/groovy/example/Util.groovy', 'package example\nclass Util { }\n')
        File outside = Files.createTempDirectory('grailsforgeoutside').toFile()
        new File(outside, 'id_rsa').text = 'a private key\n'
        Files.createSymbolicLink(new File(dir, 'src/main/groovy/outside').toPath(), outside.toPath())
        Files.createSymbolicLink(new File(dir, 'src/main/groovy/key.txt').toPath(), new File(outside, 'id_rsa').toPath())
        Files.createSymbolicLink(new File(dir, 'src/main/groovy/again').toPath(), new File(dir, 'src/main/groovy/example').toPath())
        Files.createSymbolicLink(new File(dir, 'grails-app/conf').toPath(), outside.toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'bug-report')

        then:
        exit == 0
        entries() == ['build.gradle', 'src/main/groovy/example/Util.groovy']
        out.toString().contains('| Left out: 4 link(s), which are not followed, and 0 file(s) that are keys by their name')

        cleanup:
        outside?.deleteDir()
    }

    @IgnoreIf({ os.windows })
    void "bug-report does not write through a build directory that is a link"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('build.gradle', 'version = 1\n')
        File outside = Files.createTempDirectory('grailsforgeoutside').toFile()
        Files.createSymbolicLink(new File(dir, 'build').toPath(), outside.toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'bug-report')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains('is a file or a link, so the bug report cannot be written to it. No bug report was created')
        outside.list().length == 0

        cleanup:
        outside?.deleteDir()
    }

    void "bug-report holds a bounded number of files and of bytes, and writes nothing beyond them"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('build.gradle', 'version = 1\n')
        File many = new File(dir, 'src/main/resources/many')
        many.mkdirs()
        (1..BugReportCommand.MAX_FILES).each { int number -> new File(many, "${number}.txt").createNewFile() }

        when: 'the build file is one more than the bound'
        int exit = ForgeCli.execute(beanContext, 'bug-report')

        then:
        exit == 1
        errors() == ["| Error The sources of the project are more than ${BugReportCommand.MAX_FILES} files, which is more than a bug report holds. No bug report was created".toString()]
        !new File(dir, 'build').exists()

        when: 'one file is larger than everything may be together'
        out.reset()
        many.deleteDir()
        File large = new File(dir, 'src/main/resources/large.bin')
        new RandomAccessFile(large, 'rw').withCloseable { RandomAccessFile file -> file.setLength(BugReportCommand.MAX_BYTES + 1) }
        exit = ForgeCli.execute(beanContext, 'bug-report')

        then:
        exit == 1
        errors() == ['| Error The sources of the project are larger than 256 MiB, which is more than a bug report holds. No bug report was created']
        !new File(dir, 'build').exists()

        when: 'a project has nothing to report'
        out.reset()
        large.delete()
        new File(dir, 'build.gradle').delete()
        exit = ForgeCli.execute(beanContext, 'bug-report')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].endsWith('has no files under grails-app or src and no build.gradle. No bug report was created')
    }

    @IgnoreIf({ os.windows })
    void "stats does not count a source directory that is reached through a link"() {
        given: 'a project whose src is elsewhere'
        new File(dir, 'grails-app').mkdirs()
        source('grails-app/domain/example/Book.groovy', 'package example\nclass Book { }\n')
        File outside = Files.createTempDirectory('grailsforgeoutside').toFile()
        new File(outside, 'main/groovy').mkdirs()
        new File(outside, 'main/groovy/Secret.groovy').text = 'class Secret { }\n'
        new File(outside, 'services').mkdirs()
        new File(outside, 'services/OtherService.groovy').text = 'class OtherService { }\n'
        Files.createSymbolicLink(new File(dir, 'src').toPath(), outside.toPath())
        Files.createSymbolicLink(new File(dir, 'grails-app/services').toPath(), new File(outside, 'services').toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'stats')

        then:
        exit == 0
        out.toString().contains('| Domain Classes       |     1 |     2 | ')
        out.toString().contains('| Totals               |     1 |     2 | ')
        !out.toString().contains('Helpers')
        !out.toString().contains('Services')
        out.toString().readLines().findAll { String line -> line.contains('Warning') } ==
                ['| Warning Not counted, because it is reached through a link and links are not followed: grails-app/services, src/main/groovy, src/test/groovy, src/integration-test/groovy, src/main/scripts']

        cleanup:
        outside?.deleteDir()
    }

    @IgnoreIf({ os.windows })
    void "the commands that look at a project do not look at one above that is not the user's own, and write nothing there"() {
        given:
        source('grails-app/domain/example/Book.groovy', 'package example\nclass Book { }\n')
        source('build.gradle', 'version = 1\n')
        File below = new File(dir, 'work/any')
        below.mkdirs()
        System.setProperty('user.dir', below.absolutePath)
        ConsoleProject.setOwnedByUser({ Path path -> path != dir.canonicalFile.toPath() } as Predicate<Path>)

        when:
        int stats = ForgeCli.execute(beanContext, 'stats')
        int report = ForgeCli.execute(beanContext, 'bug-report')
        int compile = ForgeCli.execute(beanContext, 'compile')

        then:
        stats == 1
        report == 1
        compile == 1
        errors().size() == 3
        errors().every { String line -> line.startsWith("| Error The Grails project in ${dir.canonicalPath}, above ${below.canonicalPath}, is not used: ${dir.canonicalPath} is owned by ") }
        errors()[0].endsWith("run the command in ${dir.canonicalPath} to use this one. Nothing was counted")
        errors()[1].endsWith("run the command in ${dir.canonicalPath} to use this one. No bug report was created")
        errors()[2].endsWith("run the command in ${dir.canonicalPath} to use this one. Gradle was not run")
        !out.toString().contains('Domain Classes')
        !new File(dir, 'build').exists()

        when: 'the project is the user\'s own'
        out.reset()
        ConsoleProject.setOwnedByUser(null)
        stats = ForgeCli.execute(beanContext, 'stats')
        report = ForgeCli.execute(beanContext, 'bug-report')

        then:
        stats == 0
        report == 0
        out.toString().contains('| Domain Classes       |     1 |     2 | ')
        entries() == ['build.gradle', 'grails-app/domain/example/Book.groovy']
    }

    @IgnoreIf({ os.windows })
    void "bug-report leaves out what is a key or a list of secrets by its name, what a build left behind, and a file with another name elsewhere"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('build.gradle', 'version = 1\n')
        source('grails-app/controllers/example/CredentialsController.groovy', 'package example\nclass CredentialsController { }\n')
        source('grails-app/services/example/SecretsService.groovy', 'package example\nclass SecretsService { }\n')
        source('src/main/resources/certs/server.crt', 'a certificate\n')
        ['id_rsa', 'id_rsa.pub', 'id_dsa', 'id_ecdsa', 'id_ed25519', 'ID_ED25519.old', '.netrc', '.npmrc', '.pypirc', 'k.p8', 'k.ppk', 'k.kdbx', 'k.der', 'k.asc', 'k.gpg',
         'truststore', 'truststore.jks', 'cacerts', 'credentials', 'credentials.json', 'secrets.properties', 'Secrets.yml', 'key.pem.bak', 'server.key.old', 'k.P12.BAK',
         'k.jks.bak.old', '.env.bak'].each { String name ->
            source("src/main/resources/certs/${name}", 'secret\n')
        }
        source('src/build/out.class', 'compiled\n')
        source('src/main/groovy/example/build/Generated.groovy', 'generated\n')
        source('src/.gradle/cache.bin', 'cache\n')
        source('grails-app/assets/.gradle/cache.bin', 'cache\n')
        File outside = Files.createTempDirectory(dir.parentFile.toPath(), 'grailsforgeoutside').toFile()
        new File(outside, 'elsewhere.txt').text = 'a file of another directory\n'
        Files.createLink(new File(dir, 'src/main/resources/linked.txt').toPath(), new File(outside, 'elsewhere.txt').toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'bug-report')

        then:
        exit == 0
        entries() == [
                'build.gradle',
                'grails-app/controllers/example/CredentialsController.groovy',
                'grails-app/services/example/SecretsService.groovy',
                'src/main/resources/certs/server.crt'
        ]
        out.toString().contains('| Left out: 0 link(s), which are not followed, and 27 file(s) that are keys by their name: keys, stores of keys and files of credentials')
        out.toString().contains('| Left out: 4 directory(ies) named build or .gradle, and 1 file(s) that have another name as well, as a hard link, and so may be a file from elsewhere')
        errors() == []

        cleanup:
        outside?.deleteDir()
    }

    @IgnoreIf({ os.windows })
    void "bug-report leaves out a file whose name has a control character, and says so"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('build.gradle', 'version = 1\n')
        source('src/main/groovy/a\nb.groovy', 'class A { }\n')
        source('src/main/groovy/e\u001b[31m@|red z|@.groovy', 'class E { }\n')
        source('src/main/groovy/dir\u0007/Inner.groovy', 'class Inner { }\n')
        source('src/main/groovy/Plain.groovy', 'class Plain { }\n')

        when:
        int exit = ForgeCli.execute(beanContext, 'bug-report')

        then:
        exit == 0
        entries() == ['build.gradle', 'src/main/groovy/Plain.groovy']
        out.toString().readLines().findAll { String line -> line.contains('Warning') } ==
                ['| Warning Left out: 3 file(s) with a control character in the name, which a ZIP file is not given, the first of them src/main/groovy/a?b.groovy']
        !out.toString().contains('\u001b[31m')
    }

    void "bug-report names the configuration files it holds, as where passwords usually are, before it says what it created"() {
        given:
        new File(dir, 'grails-app').mkdirs()
        source('build.gradle', 'version = 1\n')
        source('grails-app/conf/application.yml', 'dataSource:\n  password: hunter2\n')
        source('grails-app/conf/application-prod.yml', 'dataSource:\n  password: s3cret\n')
        source('grails-app/conf/application.groovy', 'dataSource.password = "x"\n')
        source('grails-app/conf/application-test.properties', 'dataSource.password=x\n')
        source('grails-app/conf/logback-spring.xml', '<configuration/>\n')
        source('grails-app/conf/spring/resources.groovy', 'beans = { }\n')

        when:
        int exit = ForgeCli.execute(beanContext, 'bug-report')
        List<String> said = out.toString().readLines()

        then:
        exit == 0
        said[0] == '| Warning The bug report holds the configuration of the application, which is where passwords usually are: ' +
                'grails-app/conf/application-prod.yml, grails-app/conf/application-test.properties, grails-app/conf/application.groovy, grails-app/conf/application.yml. ' +
                'Look through it before the ZIP is given to anyone'
        said[1] == "| Created bug report ZIP: build/${archive().name}"
        said[2] == '| It holds 7 file(s): what is under grails-app and src, and build.gradle, as they are'
        said.size() == 3

        when: 'a project without such a file'
        out.reset()
        new File(dir, 'grails-app/conf').deleteDir()
        exit = ForgeCli.execute(beanContext, 'bug-report', '--force')

        then:
        exit == 0
        !out.toString().contains('Warning')
        out.toString().readLines()[0] == "| Created bug report ZIP: build/${archive().name}"
    }

    void "the list of commands gives each a sentence, and the usage of every command is as wide as that of the others"() {
        given:
        generateProject(ApplicationType.WEB)

        when:
        int exit = ForgeCli.execute(beanContext, '--help')
        List<String> listed = out.toString().readLines()

        then:
        exit == 0
        listed.any { String line -> line ==~ /  test-app +Runs the tests of the Grails project\./ }
        listed.any { String line -> line ==~ /  assemble, war, package +Creates the JAR or WAR archive of the Grails project for deployment\./ }
        listed.any { String line -> line ==~ /  bug-report +Creates a ZIP file of the sources of the Grails project\./ }

        and: 'which is all the list says of them: the next line is the next command'
        ['test-app', 'assemble, war, package', 'bug-report'].every { String name ->
            int at = listed.findIndexOf { String line -> line.startsWith("  ${name} ") }
            at + 1 == listed.size() || listed[at + 1] ==~ /  \S.*/
        }

        when:
        Map<String, Integer> widths = ['create-app', 'test-app', 'bug-report', 'create-controller', 'feature'].collectEntries { String command ->
            out.reset()
            assert ForgeCli.execute(beanContext, command, '--help') == 0
            [(command): out.toString().readLines()*.length().max()]
        }

        then: 'the commands that are added for a project and those that are there from the start alike'
        widths.values().every { int width -> width <= 100 }
        ['create-app', 'test-app', 'bug-report'].every { String command -> widths[command] > 80 }
    }

    private File archive() {
        return new File(dir, "build/${dir.name}-bug-report-${LocalDate.now().format(DateTimeFormatter.ofPattern('ddMMyyyy'))}.zip")
    }

    private List<String> entries() {
        return new ZipFile(archive()).withCloseable { ZipFile zip -> zip.entries().toList()*.name }
    }

    private void source(String path, String text) {
        File file = new File(dir, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    private String lineOf(String start) {
        // with the debug log of Gradle the line has the time and the level before it
        List<String> lines = out.toString().readLines().findAll { String line -> line.startsWith(start) || line.contains(' ' + start) }
        assert lines.size() == 1
        return lines[0].substring(lines[0].indexOf(start) + start.length())
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }

    @CommandLine.Command(name = 'throwaway', description = 'A command with options of the types the application has beans of')
    static class Throwaway extends BaseCommand implements Callable<Integer> {

        @CommandLine.Option(names = ['-m'])
        Map<String, String> map

        @CommandLine.Option(names = ['-c'])
        Collection<String> collection

        @CommandLine.Option(names = ['-q'])
        Queue<String> queue

        @CommandLine.Option(names = ['-p'])
        Properties properties

        @Override
        Integer call() {
            return 0
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
        // a daemon would keep the system properties of one build for the next
        new File(directory, 'gradle.properties').text = 'org.gradle.daemon=false\n'
        new File(directory, 'build.gradle').text = BUILD
        new File(directory, 'grails-app').mkdirs()
    }
}
