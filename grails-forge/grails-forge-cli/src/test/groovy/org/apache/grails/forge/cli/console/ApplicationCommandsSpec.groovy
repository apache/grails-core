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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.gradle.model.cli.CommandCatalogBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * The commands of the application and of its plugins as commands of the CLI. The file in which the
 * build of a project lists them is written here by hand, and where a command is run the project has
 * the wrapper of a generated application around a build that needs nothing of the framework: its
 * {@code runCommand} task says what the file of the arguments held. No application is built.
 */
class ApplicationCommandsSpec extends CommandSpec implements CommandFixture {

    static final String BUILD = '''\
        import org.gradle.api.tasks.options.Option

        abstract class RunCommand extends DefaultTask {

            @Input @Optional @Option(option = 'args-file', description = 'The file of the arguments')
            String argsFile

            // read when the task is made: the task is run from the configuration cache as well
            @Internal
            final File base = project.projectDir

            @TaskAction
            void run() {
                File file = new File(base, argsFile)
                println 'file=' + file.absolutePath
                println 'option=' + argsFile
                if (file.toPath().fileSystem.supportedFileAttributeViews().contains('posix')) {
                    println 'permissions=' + java.nio.file.attribute.PosixFilePermissions.toString(java.nio.file.Files.getPosixFilePermissions(file.toPath())) +
                            ' ' + java.nio.file.attribute.PosixFilePermissions.toString(java.nio.file.Files.getPosixFilePermissions(file.parentFile.toPath()))
                }
                List vector = (List) new groovy.json.JsonSlurper().parse(file, 'UTF-8')
                println 'vector=' + vector.collect { '<' + it.getBytes('UTF-8').encodeHex() + '>' }.join('')
                if (vector.contains('fail')) {
                    throw new GradleException('this command fails on purpose')
                }
                if (vector.contains('hang')) {
                    new File(base, 'started.txt').text = file.absolutePath + '\\n'
                    Thread.sleep(180000)
                }
            }
        }

        println 'requested=' + gradle.startParameter.taskNames.collect { '<' + it.replaceAll(/=.*/, '=') + '>' }.join('')
        tasks.register('runCommand', RunCommand)
        '''.stripIndent()

    static final Map HELLO = [name: 'hello-world', aliases: [], description: 'Says hello', origin: 'org.example:hello-cli:1.2.3', target: 'application', registration: 'descriptor']
    static final String MISSING = 'The commands of the application and its plugins are not listed yet. To list them: grails-forge-cli gradle grailsCommandCatalog'
    static final String REFUSED = 'The commands of the application and its plugins are not listed: build/grails/command-catalog.json '

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        new File(dir, 'grails-app').mkdirs()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ConsoleProject.setOwnedByUser(null)
        ConsoleProject.setSafeDirectories(null)
    }

    void "the commands the build lists are listed in the help after those of the CLI, with where each comes from"() {
        given:
        catalog([HELLO,
                 [name: 'audit-report', description: '', origin: 'application', target: 'application', registration: 'factories'],
                 [name: 'old-one', aliases: ['oldest'], description: 'From a Grails 7 plugin', origin: 'file:legacy-plugin.jar', target: 'application', registration: 'legacy']])

        when:
        int exit = ForgeCli.execute(beanContext, 'help')
        List<String> lines = out.toString().readLines()
        int own = lines.indexOf('Commands:')
        int application = lines.indexOf('Commands of the application and its plugins:')

        then:
        exit == 0
        err.toString().isEmpty()
        own > 0 && application > own
        lines.subList(own, application).any { it.startsWith('  compile ') }
        !lines.subList(own, application).any { it.contains('hello-world') || it.contains('audit-report') || it.contains('old-one') }
        lines.subList(application + 1, lines.size()).findAll { !it.trim().isEmpty() }*.replaceAll(/\s+$/, '') == [
                '  audit-report     (application)',
                '  hello-world      Says hello (org.example:hello-cli:1.2.3)',
                '  old-one, oldest  From a Grails 7 plugin (file:legacy-plugin.jar)']

        when: 'the help of one of them'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', name)

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines()[0] == usage
        out.toString().contains(from)
        out.toString().contains('Everything after the name of the command is given to the command as it is, the options too.')

        and: 'nothing was run for any of it, and nothing written'
        dir.list().sort() == ['build', 'grails-app']
        new File(dir, 'build/grails').list() == ['command-catalog.json']

        where:
        name           | usage                                                          | from
        'hello-world'  | 'Usage: grails-forge-cli hello-world [ARGS...]'                | 'A command of org.example:hello-cli:1.2.3, which runs in the application'
        'hW'           | 'Usage: grails-forge-cli hello-world [ARGS...]'                | 'Says hello'
        'audit-report' | 'Usage: grails-forge-cli audit-report [ARGS...]'               | 'A command of the application, which runs in the application'
        'oldest'       | 'Usage: grails-forge-cli old-one [ARGS...]'                    | 'A command of file:legacy-plugin.jar, which runs in the application'
    }

    void "the document for a tool has the commands the build lists after those of the CLI, with what the build says of them"() {
        given:
        catalog([HELLO, [name: 'audit-report', origin: 'application', target: 'application', registration: 'factories', options: [[names: [['deep']]]], later: 1]], [later: [a: [b: 1]]])

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()
        List<Map> commands = (List<Map>) described.commands
        int first = commands.findIndexOf { it.origin != 'built-in' }

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines().size() == 1
        described.complete == true
        described.diagnostics == []
        described.projectDirectory == dir.canonicalPath
        commands.subList(0, first).every { it.origin == 'built-in' } && commands.subList(0, first)*.name.containsAll(['compile', 'help', 'gradle'])
        commands.subList(first, commands.size()) == [
                [name: 'audit-report', aliases: [], description: '', project: true, origin: 'application', target: 'application', registration: 'factories', commands: []],
                [name: 'hello-world', aliases: [], description: 'Says hello', project: true, origin: 'org.example:hello-cli:1.2.3', target: 'application', registration: 'descriptor', commands: []]]
        commands*.name.unique(false) == commands*.name
    }

    void "what the build could not list is said as the build says it, in a document that is not complete"() {
        given:
        catalog([HELLO], [complete: false, diagnostics: ['The command class example.A of application is not listed: its descriptor @|red is|@ wrong.', 'And 3 more.']])

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == false
        described.diagnostics == ['The Gradle task grailsCommandCatalog says: The command class example.A of application is not listed: its descriptor @|red is|@ wrong.',
                                  'The Gradle task grailsCommandCatalog says: And 3 more.']
        ((List<Map>) described.commands).any { it.name == 'hello-world' }

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().replaceAll(/\s+/, ' ').contains('The Gradle task grailsCommandCatalog says: The command class example.A of application is not listed: its descriptor @|red is|@ wrong.')
    }

    void "without the file of the build there are no commands of the application, nothing is run to have them, and it is said which task lists them"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        err.toString().isEmpty()
        described.complete == false
        described.diagnostics == [MISSING]
        ((List<Map>) described.commands).every { it.origin == 'built-in' }

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().contains('Commands of the application and its plugins:')
        out.toString().replaceAll(/\s+/, ' ').contains(MISSING)

        when: 'what is typed may be a command of the application'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'hello-world', 'Alice')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command hello-world')
        err.toString().readLines()[1] == MISSING
        err.toString().readLines()[2].startsWith('Usage: grails-forge-cli ')

        and: 'the list of the commands is not shown for a mistake: the line that says where the help is ends it'
        err.toString().readLines()[3] == "Run 'grails-forge-cli help' for the commands"
        err.toString().readLines().size() == 4

        when:
        err.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'hello-world')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command hello-world')
        err.toString().readLines()[1] == MISSING

        and: 'no build was started, and nothing written'
        dir.list() == ['grails-app']

        when: 'outside a project nothing is said of it'
        new File(dir, 'grails-app').deleteDir()
        err.reset()
        out.reset()
        exit = ForgeCli.execute(beanContext, 'hello-world')

        then:
        exit == 2
        !err.toString().contains('grailsCommandCatalog')

        when:
        exit = ForgeCli.execute(beanContext, 'help', '--json')

        then:
        exit == 0
        document().complete == true
        document().diagnostics == []
    }

    @Unroll
    void "a file of the build that is older than #changed is used, and said to be possibly out of date"() {
        given:
        catalog([HELLO])
        catalogFile().setLastModified(System.currentTimeMillis() - 120_000)
        File newer = new File(dir, written)
        newer.parentFile.mkdirs()
        newer.text = '// changed\n'
        // only the file is new: a directory it was put into long ago says nothing
        for (File each = newer.parentFile; each != dir && written.contains('/'); each = each.parentFile) {
            assert each.setLastModified(System.currentTimeMillis() - 240_000)
        }
        String stale = "The commands of the application and its plugins may have changed: ${changed} is newer than build/grails/command-catalog.json. " +
                'To list them again: grails-forge-cli gradle grailsCommandCatalog --rerun'

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == false
        described.diagnostics == [stale]
        ((List<Map>) described.commands).any { it.name == 'hello-world' && it.origin == 'org.example:hello-cli:1.2.3' }

        when:
        exit = ForgeCli.execute(beanContext, 'bogus')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command bogus')
        err.toString().readLines()[1] == stale

        where:
        changed                                             | written
        'build.gradle'                                      | 'build.gradle'
        'build.gradle.kts'                                  | 'build.gradle.kts'
        'settings.gradle'                                   | 'settings.gradle'
        'gradle.properties'                                 | 'gradle.properties'
        'grails-app/commands'                               | 'grails-app/commands/BarCommand.groovy'
        'grails-app/commands'                               | 'grails-app/commands/demo/deep/BarCommand.groovy'
        'build/classes/groovy/main/META-INF/grails-commands' | 'build/classes/groovy/main/META-INF/grails-commands/demo.BarCommand.json'
    }

    @Unroll
    void "a file of the build is said to be possibly out of date when #what, wherever below the commands of the application"() {
        given:
        File packaged = new File(dir, 'grails-app/commands/demo')
        packaged.mkdirs()
        new File(packaged, 'FooCommand.groovy').text = 'class FooCommand { }\n'
        new File(packaged, 'notes.txt').text = 'no command\n'
        catalog([HELLO])
        long before = System.currentTimeMillis() - 240_000
        [new File(packaged, 'FooCommand.groovy'), new File(packaged, 'notes.txt'), packaged, packaged.parentFile].each { File each -> assert each.setLastModified(before) }
        assert catalogFile().setLastModified(System.currentTimeMillis() - 120_000)

        when: 'nothing was changed since'
        int exit = ForgeCli.execute(beanContext, 'help', '--json')

        then:
        exit == 0
        document().complete == true

        when:
        change.call(packaged)
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')

        then:
        exit == 0
        document().complete == !stale
        document().diagnostics.size() == (stale ? 1 : 0)
        !stale || document().diagnostics[0].startsWith('The commands of the application and its plugins may have changed: grails-app/commands is newer than')

        where:
        what                                        | stale | change
        'a command was written again'               | true  | { File directory -> new File(directory, 'FooCommand.groovy').text = 'class FooCommand { int a }\n' }
        'a command was deleted'                     | true  | { File directory -> assert new File(directory, 'FooCommand.groovy').delete() }
        'a file that is no command was written again' | false | { File directory -> new File(directory, 'notes.txt').setLastModified(System.currentTimeMillis()) }
    }

    void "a file of the build that is as new as what it is made from is not out of date"() {
        given:
        new File(dir, 'build.gradle').text = '// a build\n'
        new File(dir, 'grails-app/commands').mkdirs()
        catalog([HELLO])
        long now = System.currentTimeMillis()
        [new File(dir, 'build.gradle'), new File(dir, 'grails-app/commands'), catalogFile()].each { File file -> assert file.setLastModified(now - 60_000) }
        new File(dir, 'grails-app/controllers').mkdirs()

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')

        then:
        exit == 0
        document().complete == true
        document().diagnostics == []

        when: 'with it, what is typed and is no command is not said to be possibly one of the application'
        exit = ForgeCli.execute(beanContext, 'bogus')

        then:
        exit == 2
        !err.toString().contains('grailsCommandCatalog')
    }

    @Unroll
    void "a file that is not what the build writes is not used at all: #what"() {
        given:
        write(content)

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        err.toString().isEmpty()
        described.complete == false
        described.diagnostics == [REFUSED + reason + '. To write it again: grails-forge-cli gradle grailsCommandCatalog --rerun']
        ((List<Map>) described.commands).every { it.origin == 'built-in' }

        when: 'the help a person reads says the same, with nothing of the file in it'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help')
        String help = out.toString()

        then:
        exit == 0
        err.toString().isEmpty()
        help.replaceAll(/\s+/, ' ').contains((REFUSED + reason).replaceAll(/\s+/, ' '))
        !help.contains('hostile') && !help.contains('\u001b') && !help.contains('\u202e')

        when: 'and none of its commands is a command'
        exit = ForgeCli.execute(beanContext, 'hello-world')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command hello-world')
        err.toString().readLines()[1].startsWith(REFUSED + reason)

        where:
        what                                      | content                                                             | reason
        'more bytes than a file has'              | (' ' * (256 * 1024 + 1)).bytes                                      | 'has more than the 262144 bytes it can have'
        'nothing'                                 | ''                                                                  | 'ends before the JSON document does'
        'no JSON'                                 | 'hostile'                                                           | 'is not JSON'
        'arrays in arrays'                        | '[' * 200_000                                                       | 'is nested deeper than 8'
        'objects in objects'                      | '{"a":' * 9 + '1' + '}' * 9                                         | 'is nested deeper than 8'
        'a great many values'                     | '[' + '1,' * 100_000 + '1]'                                         | 'has more than 100000 values'
        'a text without an end'                   | '{"format": "' + 'x' * 5000 + '"}'                                  | 'has a text of more than 4096 characters'
        'a key twice'                             | doc([HELLO]).replace('"format"', '"commands":[],"format"')          | 'has an object with the same key twice'
        'something after the document'            | doc([HELLO]) + '{}'                                                 | 'has something after the JSON document'
        'a byte that is not ASCII'                | doc([HELLO]).replace('Says', 'S\u00e4ys').getBytes('UTF-8')              | 'is not the ASCII the task writes'
        'a control character in a text'           | doc([HELLO]).replace('Says', 'Sa\u001b[31mys')                      | 'has a control character in a text'
        'an escape there is not'                  | doc([HELLO]).replace('Says', 'Sa\\x41ys')                           | 'has an escape in a text that JSON does not have'
        'a number there is not'                   | doc([HELLO]).replace('"format":1', '"format":01')                   | 'has a number that JSON does not have'
        'an array'                                | '[]'                                                                | 'is not a JSON object'
        'another format'                          | doc([HELLO], [format: 2])                                           | 'is not of format 1, the one that is read here'
        'a format that is text'                   | doc([HELLO], [format: '1'])                                         | 'is not of format 1, the one that is read here'
        'a format that is 1.0'                    | doc([HELLO]).replace('"format":1', '"format":1.0')                  | 'is not of format 1, the one that is read here'
        'a format that is 1e0'                    | doc([HELLO]).replace('"format":1', '"format":1e0')                  | 'is not of format 1, the one that is read here'
        'no format'                               | doc([HELLO]).replace('"format":1,', '')                             | 'is not of format 1, the one that is read here'
        'no word on whether it is complete'       | doc([HELLO], [complete: 'yes'])                                     | 'does not say with true or false whether it is complete'
        'diagnostics that are no list'            | doc([HELLO], [diagnostics: 'hostile'])                              | 'has diagnostics that are not at most 65 lines of at most 2048 characters of printable ASCII'
        'a diagnostic with an escape sequence'    | doc([HELLO], [diagnostics: ['hostile \u001b[31m']])                 | 'has diagnostics that are not at most 65 lines of at most 2048 characters of printable ASCII'
        'a diagnostic that turns the line around' | doc([HELLO], [diagnostics: ['hostile \u202e']])                     | 'has diagnostics that are not at most 65 lines of at most 2048 characters of printable ASCII'
        'a diagnostic that is no text'            | doc([HELLO], [diagnostics: [1]])                                    | 'has diagnostics that are not at most 65 lines of at most 2048 characters of printable ASCII'
        'too many diagnostics'                    | doc([HELLO], [diagnostics: ['hostile'] * 66])                       | 'has diagnostics that are not at most 65 lines of at most 2048 characters of printable ASCII'
        'a diagnostic that is too long'           | doc([HELLO], [diagnostics: ['h' * 2049]])                           | 'has diagnostics that are not at most 65 lines of at most 2048 characters of printable ASCII'
        'commands that are no list'               | doc([HELLO], [commands: [name: 'hostile']])                         | 'has no list of commands'
    }

    static final String NAME = 'its name is not lower-case letters and digits in words joined by single hyphens, starting with a letter, of at most 64 characters'
    static final String DESCRIPTION = 'its description is not one line of at most 512 characters that can be shown'
    static final String ORIGIN = 'its origin is not at most 120 characters of printable ASCII without a space, an @ or a dollar sign'
    static final Map FINE = [name: 'fine', description: 'Is fine', origin: 'application', target: 'application', registration: 'descriptor']

    @Unroll
    void "a command that is not as the build writes one is left out, and the others are used: #what"() {
        given: 'the command between two that are as they should be'
        write(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], commands: [HELLO, bad, FINE]]))
        String leftOut = 'Command 2 of build/grails/command-catalog.json is not used: ' + reason

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        err.toString().isEmpty()
        described.complete == false
        described.diagnostics == [leftOut]
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }*.name == ['hello-world', 'fine']

        when: 'the help a person reads lists the others and says the same, with nothing of the command in it'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help')
        String help = out.toString()

        then:
        exit == 0
        help.contains('  hello-world  Says hello (org.example:hello-cli:1.2.3)')
        help.contains('  fine         Is fine (application)')
        help.replaceAll(/\s+/, ' ').contains(leftOut)
        !help.contains('hostile') && !help.contains('\u001b') && !help.contains('\u202e')

        where:
        what                                      | bad                                                    | reason
        'a command that is text'                  | 'hostile'                                              | 'it is no command'
        'a command that is a list'                | ['hostile']                                            | 'it is no command'
        'a name in upper case'                    | HELLO + [name: 'Hostile']                              | NAME
        'a name that is a number'                 | HELLO + [name: 7]                                      | NAME
        'no name'                                 | HELLO - [name: 'hello-world']                          | NAME
        'a name with two hyphens'                 | HELLO + [name: 'hostile--x']                           | NAME
        'a name that is an option'                | HELLO + [name: '--hostile']                            | NAME
        'a name that is a path'                   | HELLO + [name: '../hostile']                           | NAME
        'a name with a space'                     | HELLO + [name: 'hostile x']                            | NAME
        'a name with markup'                      | HELLO + [name: '@|red hostile|@']                      | NAME
        'a name with an escape sequence'          | HELLO + [name: 'hostile\u001b[31m']                    | NAME
        'a name that turns the line around'       | HELLO + [name: 'hostile\u202e']                        | NAME
        'a name of 65 characters'                 | HELLO + [name: 'h' * 65]                               | NAME
        'other names that are no list'            | HELLO + [name: 'other', aliases: 'hostile']            | 'its other names are not at most 16 names of a command'
        'another name that is none'               | HELLO + [name: 'other', aliases: ['@|red hostile|@']]  | 'its other names are not at most 16 names of a command'
        'seventeen other names'                   | HELLO + [name: 'other', aliases: (1..17).collect { 'hostile-' + it }] | 'its other names are not at most 16 names of a command'
        'a description of two lines'              | HELLO + [name: 'other', description: 'hostile\nline']  | DESCRIPTION
        'a description with an escape sequence'   | HELLO + [name: 'other', description: 'hostile \u001b[31m'] | DESCRIPTION
        'a description that turns the line around' | HELLO + [name: 'other', description: 'hostile \u202e'] | DESCRIPTION
        'a description with half a character'     | HELLO + [name: 'other', description: 'hostile \ud83d'] | DESCRIPTION
        'a description of 513 characters'         | HELLO + [name: 'other', description: 'h' * 513]        | DESCRIPTION
        'a description that is a list'            | HELLO + [name: 'other', description: ['hostile']]      | DESCRIPTION
        'an origin with a space'                  | HELLO + [name: 'other', origin: 'hostile origin']      | ORIGIN
        'an origin with markup'                   | HELLO + [name: 'other', origin: '@|red hostile|@:a:1'] | ORIGIN
        'an origin with a variable'               | HELLO + [name: 'other', origin: 'hostile:${sys:user.home}:1'] | ORIGIN
        'an origin with an escape sequence'       | HELLO + [name: 'other', origin: 'hostile:a:1\u001b[31m'] | ORIGIN
        'an origin that turns the line around'    | HELLO + [name: 'other', origin: 'hostile:\u202ea:1']   | ORIGIN
        'an origin of 121 characters'             | HELLO + [name: 'other', origin: 'h' * 117 + ':a:1']    | ORIGIN
        'an origin that is empty'                 | HELLO + [name: 'other', origin: '']                    | ORIGIN
        'no origin'                               | HELLO + [name: 'other'] - [origin: 'org.example:hello-cli:1.2.3'] | ORIGIN
        'a command for Gradle'                    | HELLO + [name: 'other', target: 'gradle']              | 'its target is gradle, which is not run from here'
        'a target there is not'                   | HELLO + [name: 'other', target: 'hostile']             | 'its target is none there is'
        'no target'                               | HELLO + [name: 'other'] - [target: 'application']      | 'its target is none there is'
        'a registration there is not'             | HELLO + [name: 'other', registration: 'hostile']       | 'its registration is none of descriptor, factories, legacy'
        'a name twice'                            | HELLO                                                  | 'it has a name that a command before it has'
        'another name that is a name'             | HELLO + [name: 'other', aliases: ['hello-world']]      | 'it has a name that a command before it has'
        'its own name twice'                      | HELLO + [name: 'other', aliases: ['other']]            | 'it has a name that a command before it has'
    }

    void "of many commands that are not as the build writes them the first are named and the others counted, and of too many commands the first are used"() {
        given:
        List commands = (1..5).collect { HELLO + [name: 'Hostile' + it] } + (1..600).collect { FINE + [name: 'fine-' + it] }
        write(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], commands: commands]))

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == false
        described.diagnostics == [
                'Command 1 of build/grails/command-catalog.json is not used: ' + NAME,
                'Command 2 of build/grails/command-catalog.json is not used: ' + NAME,
                'Command 3 of build/grails/command-catalog.json is not used: ' + NAME,
                '2 more commands of build/grails/command-catalog.json are not used, for reasons like these',
                'The commands of build/grails/command-catalog.json after the first 512 are not used: 88 of them']
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }*.name == (1..512).collect { 'fine-' + it }
    }

    @Unroll
    void "what the task of the build writes for a command of #origin is read"() {
        given: 'the catalog as the builder of the task writes it, for a class that the element registers'
        File element = new File(dir, 'element')
        new File(element, 'META-INF').mkdirs()
        new File(element, 'META-INF/grails-cli.factories').text = 'org.apache.grails.core.cli.ApplicationCommand=demo.SeedCommand\n'
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        builder.add(origin, element)
        String json = builder.toJson()
        write(json)
        String written = ((List<Map>) ((Map) new JsonSlurper().parseText(json)).commands)[0].origin
        element.deleteDir()

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then: 'one definition of where a command can come from: nothing the task writes is refused'
        ApplicationCommandFile.isOrigin(written)
        exit == 0
        described.diagnostics == []
        described.complete == true
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' } ==
                [[name: 'seed', aliases: [], description: '', project: true, origin: written, target: 'application', registration: 'factories', commands: []]]

        when: 'and it is shown as it is written'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().contains("  seed  (${written})")

        where:
        origin << ['application', 'org.example:reports:1.0', 'file:reports-1.0.jar', 'file:my plugin.jar', 'file:lib (1).jar', 'file:reports~1.jar', 'file:.hidden.jar',
                   'my app:reports-cli:unspecified', ':reports-cli:unspecified', 'org.example:reports:1.0-SNAPSHOT', 'org.example:reports:[1.0,2.0)', 'file:r\u00e9ports.jar',
                   'org.example:reports:1.0:extra', 'file:' + 'a' * 130 + '.jar', 'org.example:rep@rts:$1', 'org.example:reports:1.0+build.5', 'com.github.User:repo:-SNAPSHOT',
                   'file:plugin#1.jar', 'file:plugin,v2.jar', 'file:plugin%20x.jar', "file:it's.jar", 'file:a\u001b[31mb.jar', 'file:\u202egro.jar', 'file:@|red x|@.jar',
                   'file:${sys:user.home}.jar', 'x' * 500]
    }

    @IgnoreIf({ os.windows })
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    @Unroll
    void "a file of the build that is #what is not read"() {
        given: 'a file with a command, somewhere else'
        File elsewhere = new File(dir, 'elsewhere/grails')
        elsewhere.mkdirs()
        new File(elsewhere, 'command-catalog.json').text = doc([HELLO])
        place.call(dir.toPath(), elsewhere.toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == false
        described.diagnostics == [REFUSED + reason + '. To write it again: grails-forge-cli gradle grailsCommandCatalog --rerun']
        ((List<Map>) described.commands).every { it.origin == 'built-in' }

        where:
        what                                  | reason                               | place
        'a link'                              | 'is a link'                          | { Path project, Path other -> Files.createDirectories(project.resolve('build/grails')); Files.createSymbolicLink(project.resolve('build/grails/command-catalog.json'), other.resolve('command-catalog.json')) }
        'in a directory that is a link'       | 'is behind build/grails, which is a link' | { Path project, Path other -> Files.createDirectories(project.resolve('build')); Files.createSymbolicLink(project.resolve('build/grails'), other) }
        'in a build directory that is a link' | 'is behind build, which is a link'   | { Path project, Path other -> Files.createSymbolicLink(project.resolve('build'), other.getParent()) }
        'a pipe'                              | 'is not a file'                      | { Path project, Path other -> Files.createDirectories(project.resolve('build/grails')); assert new ProcessBuilder('mkfifo', project.resolve('build/grails/command-catalog.json').toString()).start().waitFor() == 0 }
        'a directory'                         | 'is not a file'                      | { Path project, Path other -> Files.createDirectories(project.resolve('build/grails/command-catalog.json')) }
    }

    void "a description is shown as the text it is, also where it has what the command line or a terminal would read"() {
        given:
        String description = '@|red danger|@ 100% %n %s ${sys:user.home} ${COMMAND-NAME} $HOME'
        catalog([HELLO + [description: description, origin: 'application']])

        when:
        int exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().contains('  hello-world  @ |red danger|@ 100% %n %s ${sys:user.home} ${COMMAND-NAME} $HOME (application)')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'hello-world')

        then:
        exit == 0
        out.toString().contains('@ |red danger|@ 100% %n %s ${sys:user.home} ${COMMAND-NAME} $HOME')
        !out.toString().contains(System.getProperty('user.home'))

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')

        then:
        exit == 0
        ((List<Map>) document().commands).find { it.name == 'hello-world' }.description == description
    }

    void "a command of the CLI keeps its name, its other names and its short forms, and it is said which command of the build is not there for it"() {
        given: 'a generated application, which has the code generation commands'
        generateProject(ApplicationType.WEB)
        catalog([HELLO,
                 [name: 'install-templates', description: 'Installs the scaffolding templates', origin: 'org.apache.grails:grails-scaffolding:9.0.0', target: 'application', registration: 'descriptor'],
                 [name: 'war', origin: 'application', target: 'application', registration: 'factories'],
                 [name: 'stat', origin: 'file:stat.jar', target: 'application', registration: 'legacy'],
                 [name: 'c', origin: 'application', target: 'application', registration: 'factories'],
                 [name: 'help', origin: 'org.example:helper:1', target: 'application', registration: 'descriptor'],
                 [name: 'gradle', origin: 'org.example:helper:1', target: 'application', registration: 'descriptor'],
                 [name: 'fine', aliases: ['install'], origin: 'org.example:helper:1', target: 'application', registration: 'descriptor']])
        new File(dir, 'build.gradle').setLastModified(System.currentTimeMillis() - 120_000)
        new File(dir, 'settings.gradle').setLastModified(System.currentTimeMillis() - 120_000)
        new File(dir, 'gradle.properties').setLastModified(System.currentTimeMillis() - 120_000)
        String how = 'That one is run with: grails-forge-cli run-command '
        List<String> expected = [
                "c is short for more than one command of the CLI itself (built-in), so the command c of application is not a command here. ${how}c",
                "install is a command of the CLI itself (built-in), so the command fine of org.example:helper:1 is not a command here. ${how}fine",
                "gradle is a command of the CLI itself (built-in), so the command gradle of org.example:helper:1 is not a command here. ${how}gradle",
                "help is a command of the CLI itself (built-in), so the command help of org.example:helper:1 is not a command here. ${how}help",
                "install-templates is a command of the CLI itself (built-in), so the command install-templates of org.apache.grails:grails-scaffolding:9.0.0 is not a command here. ${how}install-templates",
                "stat is short for stats, a command of the CLI itself (built-in), so the command stat of file:stat.jar is not a command here. ${how}stat",
                "war is another name of assemble, a command of the CLI itself (built-in), so the command war of application is not a command here. ${how}war"]

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()
        List<Map> commands = (List<Map>) described.commands

        then: 'the commands of the CLI are there once, and of the build only the one whose names are free'
        exit == 0
        err.toString().isEmpty()
        described.complete == true
        described.diagnostics == expected
        commands*.name.unique(false) == commands*.name
        commands.findAll { it.origin != 'built-in' }*.name == ['hello-world']
        commands.find { it.name == 'install-templates' }.origin == 'built-in'
        commands.find { it.name == 'assemble' }.aliases == ['war', 'package']

        when: 'the help a person reads'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help')
        String help = out.toString().replaceAll(/\s+/, ' ')

        then:
        exit == 0
        expected.every { String line -> help.contains(line) }

        when: 'the name is typed: the command of the CLI is run, and it is said that the other is not'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, typed, '--help')

        then:
        exit == 0
        out.toString().readLines()[0].startsWith("Usage: grails-forge-cli ${runs}")
        err.toString().readLines() == ['| Warning ' + expected.find { String line -> line.startsWith(typed + ' is ') }]

        where:
        typed               | runs
        'install-templates' | 'install-templates'
        'war'               | 'assemble'
        'stat'              | 'stats'
    }

    void "a command of the build that is not there is said to be run with run-command"() {
        given:
        catalog([[name: 'compile', origin: 'org.example:helper:1', target: 'application', registration: 'descriptor']])
        CommandLine commandLine = ForgeCli.createCommandLine(beanContext, false)

        when:
        int exit = ForgeCli.execute(commandLine, 'help', '--json')

        then:
        exit == 0
        document().diagnostics == ['compile is a command of the CLI itself (built-in), so the command compile of org.example:helper:1 is not a command here. ' +
                                           'That one is run with: grails-forge-cli run-command compile']
    }

    void "a short form is one of a command of the CLI before it is one of a command of the build, which takes none away"() {
        given: 'a generated application, which has create-controller and create-command, and commands of the build that are named like commands of the CLI'
        generateProject(ApplicationType.WEB)
        catalog([HELLO] + ['create-cat', 'hello-there', 'hlep', 'complie', 'create-application'].collect { String name ->
            [name: name, origin: 'org.example:typos:1.0', target: 'application', registration: 'descriptor']
        })

        when: 'what was short for two commands of the CLI is short for those two'
        int exit = ForgeCli.execute(beanContext, 'cC', 'Book')

        then:
        exit == 2
        err.toString().readLines()[0] == 'cC is short for more than one command: create-controller, create-command. Type more of the name of the one that is meant'
        !new File(dir, 'grails-app/controllers/example/grails/BookController.groovy').exists()

        when: 'and what was short for one command of the CLI is that command'
        err.reset()
        out.reset()
        exit = ForgeCli.execute(beanContext, typed, '--help')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines()[0].startsWith("Usage: grails-forge-cli ${command} ")

        when: 'a short form that only commands of the build have is theirs'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'hT')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines()[0] == 'Usage: grails-forge-cli hello-there [ARGS...]'

        when: 'the commands of the build are all there, under their names'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')

        then:
        exit == 0
        document().diagnostics == []
        ((List<Map>) document().commands).findAll { it.origin != 'built-in' }*.name == ['complie', 'create-application', 'create-cat', 'hello-there', 'hello-world', 'hlep']

        where:
        typed  | command
        'h'    | 'help'
        'comp' | 'compile'
        'cA'   | 'create-app'
        'cApp' | 'create-app'
    }

    @IgnoreIf({ os.windows })
    void "a command of the build is run by the Gradle task runCommand, with every argument as the one argument it is, in a file only the user can read that is gone afterwards"() {
        given:
        plainProject()
        catalog([HELLO + [aliases: ['hi']]])
        List<String> arguments = ['--stacktrace', '-x', '-h', '--help', '--', '', ' ', 'two  words "double" \'single\' $HOME * \\', 'line\nbreak\ttab', '\u00e9\ud83d\ude00\u202e',
                                  'a @|red x|@ %n ${sys:user.home}', '--args-file=/etc/passwd', '-Pargs=evil', '--arg=x', '-Dgrails.env=prod', 'cA', '\u001b[31m', ']', '"', '\\u0000']

        when:
        int exit = ForgeCli.execute(beanContext, (['hW'] + arguments) as String[])
        File file = new File(lineOf('file='))

        then:
        exit == 0
        lineOf('requested=') == '<runCommand><--args-file=>'
        lineOf('vector=') == (['hello-world'] + arguments).collect { '<' + it.getBytes('UTF-8').encodeHex() + '>' }.join('')
        lineOf('permissions=') == 'rw------- rwx------'
        errors() == []

        and: 'before Gradle is started it is said what is run, and where it comes from, on the error stream'
        err.toString().readLines()[0] == '| Running the command hello-world of org.example:hello-cli:1.2.3, with the Gradle task runCommand'

        and: 'the file has the same name in the project every time, which is what Gradle is given, and is gone afterwards'
        lineOf('option=') == '.grails/run/command-args-0.json'
        file.canonicalFile == new File(dir, '.grails/run/command-args-0.json').canonicalFile
        new File(dir, '.grails/run').list().sort() == ['.gitignore']
        permissions('.grails/run/.gitignore') == 'rw-------'

        when: 'by another name of the command, and without arguments'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'hi')

        then:
        exit == 0
        lineOf('vector=') == '<' + 'hello-world'.bytes.encodeHex() + '>'
        lineOf('option=') == '.grails/run/command-args-0.json'
        new File(dir, '.grails/run').list().sort() == ['.gitignore']
    }

    @IgnoreIf({ os.windows })
    void "a build with the configuration cache configures once for a command, whatever its arguments are the next time"() {
        given:
        plainProject()
        new File(dir, 'gradle.properties') << 'org.gradle.configuration-cache=true\n'
        catalog([HELLO])

        when:
        int first = ForgeCli.execute(beanContext, 'hello-world', 'one')
        String stored = out.toString()
        out.reset()
        int second = ForgeCli.execute(beanContext, 'hello-world', 'two', 'three  words')

        then:
        first == 0
        stored.contains('Configuration cache entry stored')
        second == 0
        out.toString().contains('Reusing configuration cache')
        out.toString().contains('Configuration cache entry reused')
        lineOf('vector=') == ['hello-world', 'two', 'three  words'].collect { '<' + it.bytes.encodeHex() + '>' }.join('')
    }

    @IgnoreIf({ os.windows })
    void "a command that fails is a failure of the command of the CLI, with one line after what Gradle says, and the file of the arguments is gone"() {
        given:
        plainProject()
        catalog([HELLO])

        when:
        int exit = ForgeCli.execute(beanContext, 'hello-world', 'fail')

        then:
        exit == 1
        all().contains('this command fails on purpose')
        errors() == ['| Error Gradle did not succeed: it ended with exit code 1']
        !all().contains('Usage:')
        new File(dir, '.grails/run').list().sort() == ['.gitignore']
    }

    @IgnoreIf({ os.windows })
    void "cancelling a command stops Gradle, and the file of the arguments is gone"() {
        given:
        plainProject()
        catalog([HELLO])
        File started = new File(dir, 'started.txt')
        AtomicInteger exit = new AtomicInteger(-1)
        Thread command = new Thread({ exit.set(ForgeCli.execute(beanContext, 'hello-world', 'hang')) })

        when:
        command.start()
        new PollingConditions(timeout: 120, delay: 0.5).eventually {
            assert started.isFile() && started.text.endsWith('\n')
        }
        File file = new File(started.text.trim())
        boolean there = file.isFile()
        command.interrupt()
        command.join(60_000)

        then:
        there
        !command.alive
        exit.get() == 1
        errors() == ['| Error Gradle was stopped before it finished, because the command was cancelled']
        !file.exists()
        new File(dir, '.grails/run').list().sort() == ['.gitignore']
    }

    @Unroll
    void "arguments the Gradle task would refuse are refused before a build is started, without being repeated: #what"() {
        given:
        catalog([HELLO])

        when:
        int exit = ForgeCli.execute(beanContext, (['hello-world'] + arguments) as String[])

        then:
        exit == 2
        err.toString().readLines() == ['The command cannot be given these arguments: ' + message,
                                       'Usage: grails-forge-cli hello-world [ARGS...]',
                                       "Run 'grails-forge-cli help hello-world' for the options"]
        dir.list().sort() == ['build', 'grails-app']

        where:
        what                    | arguments                         | message
        'the character U+0000'  | ['fine', 'hostile\u0000']         | 'argument 3 has the character U+0000, which no command line carries'
        'half a character'      | ['hostile\ud83d']                 | 'argument 2 has half of a character of two code units, which cannot be written as text'
        'the other half'        | ['\ude00hostile']                 | 'argument 2 has half of a character of two code units, which cannot be written as text'
        'too many'              | ['hostile'] * 4096                | 'these are 4097 arguments, and the task that runs a command takes 4096 at most'
        'too much'              | ['hostile' * 150_000]             | 'the arguments are more than 1048576 bytes in UTF-8, and the task that runs a command reads no more'
    }

    void "a command of the build is not run without the Gradle wrapper of the project, and not outside the project"() {
        given:
        catalog([HELLO])

        when:
        int exit = ForgeCli.execute(beanContext, 'hello-world', 'Alice')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains('has no Gradle wrapper')
        errors()[0].endsWith('Gradle was not run, since the command runs the Gradle of the project and no other')
    }

    void "the commands of a project above that is not the user's own are not there, whatever its build lists"() {
        given:
        catalog([HELLO])
        File below = new File(dir, 'work/any')
        below.mkdirs()
        System.setProperty('user.dir', below.absolutePath)
        ConsoleProject.setOwnedByUser({ Path path -> path != dir.canonicalFile.toPath() } as Predicate<Path>)

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == false
        described.diagnostics.size() == 1
        described.diagnostics[0].startsWith("The Grails project in ${dir.canonicalPath}, above ${below.canonicalPath}, is not used: ")
        ((List<Map>) described.commands).every { it.origin == 'built-in' }

        when:
        exit = ForgeCli.execute(beanContext, 'hello-world')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command hello-world')
        !err.toString().contains('grailsCommandCatalog')

        when: 'the environment names the directory as one to use, whoever owns it'
        ConsoleProject.setSafeDirectories(dir.canonicalPath)
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        described = document()

        then: 'what its build lists is read'
        exit == 0
        described.complete == true
        described.diagnostics == []
        described.projectDirectory == dir.canonicalPath
        ((List<Map>) described.commands).any { it.name == 'hello-world' }

        when: 'the project is the user\'s own: its commands are there in every directory of it'
        ConsoleProject.setSafeDirectories('')
        ConsoleProject.setOwnedByUser(null)
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        described = document()

        then:
        exit == 0
        described.complete == true
        described.projectDirectory == dir.canonicalPath
        ((List<Map>) described.commands).any { it.name == 'hello-world' }
    }

    void "the file of the build is not read for a command that has nothing to do with the project of the directory"() {
        given: 'a file that would be said to be wrong'
        write('hostile')

        when:
        int exit = ForgeCli.execute(beanContext, 'create-app', '--help')

        then:
        exit == 0
        err.toString().isEmpty()
        !out.toString().contains('command-catalog')
    }

    private String permissions(String path) {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(dir, path).toPath()))
    }

    private File catalogFile() {
        return new File(dir, 'build/grails/command-catalog.json')
    }

    private void catalog(List commands, Map others = [:]) {
        write(doc(commands, others))
    }

    private void write(Object content) {
        catalogFile().parentFile.mkdirs()
        catalogFile().bytes = content instanceof byte[] ? (byte[]) content : ((String) content).getBytes(StandardCharsets.UTF_8)
    }

    // one line of ASCII JSON, as the task writes it: what is not ASCII is written as an escape
    static String doc(List commands, Map others = [:]) {
        List sorted = commands.every { it instanceof Map && ((Map) it).name instanceof String } ? commands.sort(false) { ((Map) it).name } : commands
        return JsonOutput.toJson([format: 1, complete: true, diagnostics: [], commands: sorted] + others)
    }

    private Map document() {
        return (Map) new JsonSlurper().parseText(out.toString().readLines().last())
    }

    private String all() {
        return out.toString() + err.toString()
    }

    private String lineOf(String start) {
        List<String> lines = out.toString().readLines().findAll { String line -> line.startsWith(start) }
        assert lines.size() == 1
        return lines[0].substring(start.length())
    }

    private List<String> errors() {
        return all().readLines().findAll { String line -> line.contains('| Error') }
    }

    // the wrapper of a generated application, and around it a build that needs nothing of Grails
    private void plainProject() {
        generateProject(ApplicationType.WEB)
        dir.eachFile { File file ->
            if (!(file.name in ['gradlew', 'gradlew.bat', 'gradle'])) {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        new File(dir, 'gradle').eachFile { File file ->
            if (file.name != 'wrapper') {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        new File(dir, 'settings.gradle').text = "rootProject.name = 'plain'\n"
        new File(dir, 'gradle.properties').text = 'org.gradle.daemon=false\n'
        new File(dir, 'build.gradle').text = BUILD
        new File(dir, 'grails-app').mkdirs()
    }
}
