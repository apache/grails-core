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

import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout

/**
 * A command of the application or of one of its plugins is run by its name also before the build of the
 * project has listed it: a name that is no command, in a project whose list is missing or may be out of
 * date, has the build list the commands once, and is then read again. The build here needs nothing of
 * Grails: its {@code grailsCommandCatalog} writes down that it ran and writes the list the specification
 * gave it, and its {@code runCommand} writes down the arguments it was given.
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class ApplicationCommandListingSpec extends CommandSpec {

    static final String BUILD = '''\
        import org.gradle.api.tasks.options.Option

        abstract class RunCommand extends DefaultTask {

            @Input @Optional @Option(option = 'args-file', description = 'The file of the arguments')
            String argsFile

            @Internal
            final File base = project.projectDir

            @TaskAction
            void run() {
                List vector = (List) new groovy.json.JsonSlurper().parse(new File(base, argsFile), 'UTF-8')
                new File(base, 'ran.txt') << (vector.join(' ') + '\\n')
            }
        }

        abstract class ListCommands extends DefaultTask {

            @Internal
            final File base = project.projectDir

            // how the build was asked for the task, read while it is configured
            @Internal
            final String requested = project.gradle.startParameter.taskRequests.collectMany { it.args }.join(' ')

            @TaskAction
            void list() {
                new File(base, 'listed.txt') << (requested + ' input=' + (System.getenv('GRAILS_NON_INTERACTIVE') ?: 'given') + '\\n')
                if (new File(base, 'listing-fails').exists()) {
                    throw new GradleException('the listing fails as it is asked to')
                }
                File catalog = new File(base, '.grails/cache/command-catalog.json')
                catalog.parentFile.mkdirs()
                catalog.text = new File(base, 'commands.json').text
            }
        }

        tasks.register('runCommand', RunCommand)
        tasks.register('grailsCommandCatalog', ListCommands)
        '''.stripIndent()

    static final Map GREET = [name: 'greet', aliases: [], description: 'Greets', origin: 'application', target: 'application', registration: 'descriptor']
    static final Map STATS = [name: 'stats', aliases: [], description: 'Counts', origin: 'org.example:stats-cli:1.0', target: 'application', registration: 'descriptor']
    static final String MISSING = 'The commands of the application and its plugins are not listed yet: a build that compiles the project lists them, and so does typing ' +
            'the name of one. To list them now: grails-forge-cli gradle grailsCommandCatalog'
    static final String LISTING = '| Listing the commands of the application and its plugins, of which greet may be one, with the Gradle task grailsCommandCatalog'

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
        gradle.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'build.gradle').text = BUILD
        new File(dir, 'commands.json').text = ApplicationCommandsSpec.doc([GREET, STATS])
    }

    void cleanup() {
        ApplicationCommands.setListing(null)
        ConsoleCommands.setInteractive(false)
        GradleBuild.closeConnections()
        GradleBuild.setRunner(null)
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void cleanupSpec() {
        gradle.close()
    }

    void "a command of the application is run by its name before the build has listed it: the build lists the commands once, and the command is run with every argument after its name, --help among them"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'greet', 'Alice', '--help')

        then:
        exit == 0
        err.toString().readLines()[0] == LISTING
        err.toString().contains('| Running the command greet of the application, with the Gradle task runCommand')
        ran() == ['greet Alice --help']

        and: 'the task was asked to run although Gradle may take it for up to date, with an input that has ended'
        listed() == [':grailsCommandCatalog --rerun input=true']

        when: 'it is run again, and another command of the list'
        err.reset()
        exit = ForgeCli.execute(beanContext, 'greet', 'Bob')

        then: 'the list is not made again'
        exit == 0
        !err.toString().contains('| Listing')
        ran() == ['greet Alice --help', 'greet Bob']
        listed().size() == 1
    }

    void "a name that is no command has the build list the commands once and is then refused as before, and a second one is refused without a build"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'gret')
        List<String> lines = err.toString().readLines()

        then:
        exit == 2
        lines[0] == '| Listing the commands of the application and its plugins, of which gret may be one, with the Gradle task grailsCommandCatalog'
        lines.any { String line -> line.startsWith('There is no command gret. Did you mean: ') && line.contains('greet') }
        lines.contains('The build of the project has just listed the commands of the application and its plugins, and gret is none of them')
        listed().size() == 1
        !new File(dir, 'ran.txt').exists()

        when:
        err.reset()
        exit = ForgeCli.execute(beanContext, 'grete')
        lines = err.toString().readLines()

        then: 'the list is current, so it is not made again, and nothing is said of it'
        exit == 2
        lines[0].startsWith('There is no command grete. Did you mean: ')
        !lines.any { String line -> line.contains('Listing') || line.contains('listed') }
        listed().size() == 1
    }

    void "a list that may be out of date is made again for a name that is not in it, and the command it now has is run"() {
        given: 'a list without the command, older than the build script'
        File catalog = new File(dir, ApplicationCommandFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.text = ApplicationCommandsSpec.doc([STATS])
        catalog.setLastModified(System.currentTimeMillis() - 60_000)

        when:
        int exit = ForgeCli.execute(beanContext, 'greet')

        then:
        exit == 0
        err.toString().readLines()[0] == LISTING
        listed().size() == 1
        ran() == ['greet']

        when: 'a list that is current has the command'
        err.reset()
        exit = ForgeCli.execute(beanContext, 'greet')

        then:
        exit == 0
        listed().size() == 1
        ran() == ['greet', 'greet']
    }

    void "the build is not run for #what"() {
        when:
        int exit = ForgeCli.execute(beanContext, typed as String[])

        then:
        exit == expected
        !new File(dir, 'listed.txt').exists()
        !all().contains('| Listing')
        said.every { String line -> all().replaceAll(/\s+/, ' ').contains(line) }

        where:
        what                                             | typed                    | expected | said
        'the help'                                       | ['help']                 | 0        | [MISSING]
        'the help of a name that is no command'          | ['help', 'greet']        | 2        | ['There is no command greet', MISSING]
        'the help asked for in front of a name'          | ['--help', 'greet']      | 0        | ['Usage: grails-forge-cli']
        'the version'                                    | ['--version']            | 0        | []
        'the help of a command of the command line'      | ['cA', '--help']         | 0        | ['Usage: grails-forge-cli create-app']
        'a command of the command line'                  | ['stats']                | 0        | []
        'what cannot be the name of a command'           | ['greet.groovy']         | 2        | ['There is no command greet.groovy', MISSING]
        'a command of the Grails shell that is replaced' | ['run']                  | 2        | ['Type: grails-forge-cli run-app']
        'a program of the operating system, after !'     | ['!ls']                  | 2        | ['There is no command']
        'a program of the operating system, as !name'    | ['!greet']               | 2        | ['There is no command']
    }

    void "a command of the command line keeps its name over the command of the application of that name, which is said"() {
        given: 'the build has listed the commands, a command of the application named as a command of the command line among them'
        ForgeCli.execute(beanContext, 'greet')
        err.reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'stats')

        then:
        exit == 0
        err.toString().contains('| Warning stats is a command of the CLI itself (built-in), so the command stats of org.example:stats-cli:1.0 is not a command here')
        listed().size() == 1
        ran() == ['greet']
    }

    void "with GRAILS_LIST_COMMANDS=false a name that is no command is refused at once, as a check without a build"() {
        expect:
        ApplicationCommands.LISTING == 'GRAILS_LIST_COMMANDS'

        when:
        ApplicationCommands.setListing(' False ')
        int exit = ForgeCli.execute(beanContext, 'greet')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command greet')
        err.toString().readLines()[1] == MISSING
        !new File(dir, 'listed.txt').exists()

        when: 'any other value lists'
        ApplicationCommands.setListing('true')
        err.reset()
        exit = ForgeCli.execute(beanContext, 'greet')

        then:
        exit == 0
        listed().size() == 1
    }

    void "a build that fails to list the commands ends the command with what it said, and nothing is run"() {
        given:
        new File(dir, 'listing-fails').text = ''

        when:
        int exit = ForgeCli.execute(beanContext, 'greet')

        then:
        exit == 1
        all().contains('the listing fails as it is asked to')
        err.toString().readLines().findAll { String line -> line.startsWith('| Error') }.last() ==
                '| Error The commands of the application and its plugins could not be listed, so it is not known whether greet is one of them. It was not run'
        listed().size() == 1
        !new File(dir, 'ran.txt').exists()

        when: 'the next time it lists again'
        new File(dir, 'listing-fails').delete()
        exit = ForgeCli.execute(beanContext, 'greet')

        then:
        exit == 0
        listed().size() == 2
        ran() == ['greet']
    }

    void "nothing is listed #where"() {
        given:
        prepare.call(dir)

        when:
        int exit = ForgeCli.execute(beanContext, 'greet')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command greet')
        said == null || err.toString().readLines()[1].startsWith(said)
        !new File(dir, 'listed.txt').exists()

        where:
        where                                                        | prepare                                                                    | said
        'outside a project'                                          | { File directory -> new File(directory, 'grails-app').deleteDir() }        | null
        'in a project without a Gradle wrapper'                      | { File directory -> new File(directory, 'gradlew').delete() }              | MISSING
        'where the catalog is not used, which is said with its task' | { File directory -> written(directory, '{"format":2}') }                   | ApplicationCommandsSpec.REFUSED
    }

    private static void written(File directory, String catalog) {
        File file = new File(directory, ApplicationCommandFile.LOCATION)
        file.parentFile.mkdirs()
        file.text = catalog
    }

    void "in the interactive console the build lists the commands through the Tooling API, and the command is run"() {
        given: 'the wrapper script is not what runs Gradle there'
        new File(dir, 'gradlew').text = ToolingApiBuildSpec.WRAPPER_THAT_SAYS_SO
        GradleBuild.setRunner(GradleBuild.AUTO)
        ConsoleCommands.setInteractive(true)

        when:
        int exit = ForgeCli.execute(beanContext, 'greet', 'Carol')

        then:
        exit == 0
        all().contains(LISTING)
        listed() == [':grailsCommandCatalog --rerun input=true']
        ran() == ['greet Carol']
        !new File(dir, 'wrapper-was-run.txt').exists()

        when: 'a typo in the console after that'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'gret')

        then:
        exit == 2
        !all().contains('| Listing')
        listed().size() == 1
    }

    private List<String> listed() {
        File file = new File(dir, 'listed.txt')
        return file.isFile() ? file.readLines() : []
    }

    private List<String> ran() {
        File file = new File(dir, 'ran.txt')
        return file.isFile() ? file.readLines() : []
    }

    private String all() {
        return out.toString() + err.toString()
    }
}
