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
import java.util.function.Predicate
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import picocli.AutoComplete
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * The scripts of an application as commands of the CLI. A script is listed by the name of its file
 * and run by the build of the project: where one is run, the project has the wrapper of a generated
 * application around a build that needs nothing of the framework, whose {@code runScriptCommand}
 * task says what the file of the arguments held. Every script of these projects would leave a file
 * behind if anything evaluated it, and none is ever left.
 */
@Timeout(600)
class ScriptCommandsSpec extends CommandSpec implements CommandFixture {

    static final String BUILD = ApplicationCommandsSpec.BUILD + "tasks.register('runScriptCommand', RunCommand)\n"
    static final String MARKER = 'new File("evaluated.txt").text = "a script was evaluated"\nnew File(System.getProperty("user.dir"), "evaluated.txt").text = "a script was evaluated"\n'
    static final String DEPRECATED = '| Warning Script commands are deprecated. Write a new command as a class of the application: create-command'
    static final String HOW = 'That one is run with: grails-forge-cli gradle runScriptCommand --arg='

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File unread

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
        // a file nobody can read is in the way of deleting the directory on some systems
        unread?.setReadable(true)
    }

    void "the scripts of the application are listed by the names of their files, in the help, for a tool and for completion, and none is evaluated"() {
        given:
        catalog([ApplicationCommandsSpec.HELLO])
        script('data-import.groovy')
        script('NightlyReport.groovy')
        script('x.groovy')

        when:
        int exit = ForgeCli.execute(beanContext, 'help')
        List<String> lines = out.toString().readLines()
        int application = lines.indexOf('Commands of the application and its plugins:')

        then:
        exit == 0
        err.toString().isEmpty()
        lines.subList(application + 1, lines.size()).findAll { !it.trim().isEmpty() }*.replaceAll(/\s+$/, '') == [
                '  hello-world     Says hello (org.example:hello-cli:1.2.3)',
                '  data-import     (script of the application)',
                '  nightly-report  (script of the application)',
                '  x               (script of the application)']

        when: 'the help of one of them'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'nR')
        String help = out.toString().replaceAll(/\s+/, ' ')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines()[0] == 'Usage: grails-forge-cli nightly-report [ARGS...]'
        help.contains('A script of the application, in src/main/scripts. It is run by the build of the project and not by this program: ' +
                'it is started by the Gradle task runScriptCommand of the project.')
        help.contains('Script commands are deprecated. Write a new command as a class of the application: create-command')
        help.contains('Everything after the name of the command is given to the command as it is, the options too.')

        when: 'the document for a tool'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        err.toString().isEmpty()
        described.complete == true
        described.diagnostics == []
        ((List<Map>) described.commands).findAll { it.registration == 'script' } == ['data-import', 'nightly-report', 'x'].collect {
            [name: it, aliases: [], description: '', project: true, origin: 'application', target: 'standalone', registration: 'script', commands: []]
        }

        when: 'completion, as the interactive shell asks for it'
        List<CharSequence> candidates = []
        AutoComplete.complete(ForgeCli.createCommandLine(beanContext, true).getCommandSpec(), ['night'] as String[], 0, 5, 5, candidates)

        then:
        candidates*.toString() == ['ly-report']

        and: 'nothing was run for any of it, and nothing written'
        dir.list().sort() == ['.grails', 'grails-app', 'src']
        new File(dir, '.grails').list() == ['cache']
        notEvaluated()
    }

    @IgnoreIf({ os.windows })
    void "a file that is no script, or whose name is no name of a command, is not listed, not opened and not named"() {
        given: 'two scripts, one of them a file nobody can read, among what is none'
        catalog([])
        script('fine.groovy')
        unread = script('unread.groovy')
        Files.setPosixFilePermissions(unread.toPath(), PosixFilePermissions.fromString('---------'))
        // every one of them has zzq in its name, which nothing else that is written has
        List<String> hostile = ['zzq\u001b[31mred.groovy', '@|red zzq|@.groovy', 'zzq' + 'a' * 245 + '.groovy', '..groovy', 'zzq..b.groovy', '.zzq.groovy',
                                '-zzq.groovy', 'zzq-.groovy', 'zzq--y.groovy', '\u00dcberzzq.groovy', 'zzq words.groovy', '%n%szzq.groovy', '${sys:user.home}zzq.groovy',
                                'zzq\nbreak.groovy', '_Zzq.groovy', 'zzq\there.groovy']
        hostile.each { String name -> script(name) }
        File scripts = new File(dir, 'src/main/scripts')
        Files.createSymbolicLink(new File(scripts, 'zzqlinked.groovy').toPath(), new File(scripts, 'fine.groovy').toPath())
        Files.createSymbolicLink(new File(scripts, 'zzqoutside.groovy').toPath(), Path.of('/etc/passwd'))
        new File(scripts, 'zzqdirectory.groovy').mkdirs()
        assert ['mkfifo', new File(scripts, 'zzqpipe.groovy').absolutePath].execute().waitFor() == 0
        // what is no script by its ending is not counted
        new File(scripts, 'zzqsteps.yml').text = 'description: a command of steps\n'
        new File(scripts, 'README').text = 'notes\n'
        int notCommands = hostile.size() + 4
        String expected = "${notCommands} of the .groovy files in src/main/scripts are no commands: a script is a file, and not a link, that is named with " +
                'the letters a to z and digits in words that capitals or hyphens set apart, as in data-import.groovy or DataImport.groovy, ' +
                'with at most 64 characters, and the only one of its name'

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        err.toString().isEmpty()
        described.complete == true
        described.diagnostics == [expected]
        ((List<Map>) described.commands).findAll { it.registration == 'script' }*.name == ['fine', 'unread']
        out.toString().readLines().size() == 1
        !out.toString().toLowerCase().contains('zzq')

        when: 'the help a person reads'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        out.toString().replaceAll(/\s+/, ' ').contains(expected)
        !out.toString().contains('\u001b[31m')
        !out.toString().toLowerCase().contains('zzq')
        notEvaluated()

        and: 'a name longer than a file system takes is no name either'
        ScriptCommandFiles.commandName('a' * 300 + '.groovy') == null
        ScriptCommandFiles.commandName('A' + 'aB' * 30 + '.groovy') == null
        ScriptCommandFiles.commandName('../x.groovy') == null
        ScriptCommandFiles.commandName('DataImport.groovy') == 'data-import'
        ScriptCommandFiles.commandName('data-IMPORT.groovy') == 'data-import'
    }

    void "two files that give one name are one command, and it is said that one of them is none"() {
        given:
        catalog([])
        script('DataImport.groovy')
        script('data-import.groovy')

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        ((List<Map>) described.commands).findAll { it.registration == 'script' }*.name == ['data-import']
        described.diagnostics.size() == 1
        described.diagnostics[0].startsWith('1 of the .groovy files in src/main/scripts is no command: ')
    }

    void "a directory with ten thousand files has no commands, since which of them are looked at is not in anyone's hands, and that is said"() {
        given:
        catalog([])
        File scripts = new File(dir, 'src/main/scripts')
        scripts.mkdirs()
        (1..10_000).each { int number -> new File(scripts, "s${number}.groovy").text = '' }

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        err.toString().isEmpty()
        described.complete == false
        described.diagnostics == ['The scripts of the application are not listed: src/main/scripts has more than the 4096 files that are looked at']
        !((List<Map>) described.commands).any { it.registration == 'script' }
    }

    void "of more scripts than are made commands the first in the order of their names are, whatever order the directory has them in"() {
        given:
        catalog([])
        File scripts = new File(dir, 'src/main/scripts')
        scripts.mkdirs()
        List<String> names = (1..300).collect { int number -> 's' + String.valueOf(1000 + number) }
        names.reverse().each { String name -> new File(scripts, "${name}.groovy").text = '' }

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == false
        described.diagnostics == ['The scripts of the application are not all listed: src/main/scripts has more than the 256 scripts that are made commands']
        ((List<Map>) described.commands).findAll { it.registration == 'script' }*.name == names.sort().take(256)
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "scripts behind a link are not listed: #link"() {
        given:
        catalog([])
        File elsewhere = Files.createTempDirectory('grailsforgescripts').toFile()
        new File(elsewhere, 'main/scripts').mkdirs()
        new File(elsewhere, 'scripts').mkdirs()
        new File(elsewhere, 'main/scripts/planted.groovy').text = MARKER
        new File(elsewhere, 'scripts/planted.groovy').text = MARKER
        new File(elsewhere, 'planted.groovy').text = MARKER
        File at = new File(dir, link)
        at.parentFile.mkdirs()
        Files.createSymbolicLink(at.toPath(), new File(elsewhere, target).toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == false
        described.diagnostics == ["The scripts of the application are not listed: ${link} is a link".toString()]
        !((List<Map>) described.commands).any { it.registration == 'script' }

        when:
        exit = ForgeCli.execute(beanContext, 'planted')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command planted')
        notEvaluated()

        cleanup:
        elsewhere?.deleteDir()

        where:
        link               | target
        'src'              | '.'
        'src/main'         | 'main'
        'src/main/scripts' | 'scripts'
    }

    void "a script does not take the name of a command of the CLI or of a command the build lists, and it is said how it is run"() {
        given:
        catalog([ApplicationCommandsSpec.HELLO + [aliases: ['hi']],
                 [name: 'compile', origin: 'org.example:helper:1', target: 'application', registration: 'descriptor']])
        ['compile.groovy', 'hello-world.groovy', 'hi.groovy', 'stat.groovy', 'CreateApp.groovy', 'fine.groovy'].each { script(it) }
        List<String> expected = [
                'compile is a command of the CLI itself (built-in), so the command compile of org.example:helper:1 is not a command here. ' +
                        'That one is run with: grails-forge-cli run-command compile',
                "compile is a command of the CLI itself (built-in), so the script compile of src/main/scripts is not a command here. ${HOW}compile",
                "create-app is a command of the CLI itself (built-in), so the script create-app of src/main/scripts is not a command here. ${HOW}create-app",
                "hello-world is a command the build lists (org.example:hello-cli:1.2.3), so the script hello-world of src/main/scripts is not a command here. ${HOW}hello-world",
                "hi is another name of hello-world, a command the build lists (org.example:hello-cli:1.2.3), so the script hi of src/main/scripts is not a command here. ${HOW}hi",
                "stat is short for stats, a command of the CLI itself (built-in), so the script stat of src/main/scripts is not a command here. ${HOW}stat"]*.toString()

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()
        List<Map> commands = (List<Map>) described.commands

        then:
        exit == 0
        err.toString().isEmpty()
        described.complete == true
        described.diagnostics == expected
        commands*.name.unique(false) == commands*.name
        commands.findAll { it.registration == 'script' }*.name == ['fine']
        commands.find { it.name == 'hello-world' }.registration == 'descriptor'
        commands.find { it.name == 'compile' }.origin == 'built-in'

        when: 'the help a person reads'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help')
        String help = out.toString().replaceAll(/\s+/, ' ')

        then:
        exit == 0
        expected.every { String line -> help.contains(line) }

        when: 'the name is typed: the command that has it is the one that is meant, and it is said that the script is not'
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'hi')

        then:
        exit == 0
        out.toString().readLines()[0] == 'Usage: grails-forge-cli hello-world [ARGS...]'

        when:
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'stat', '--help')

        then:
        exit == 0
        out.toString().readLines()[0].startsWith('Usage: grails-forge-cli stats')
        err.toString().readLines() == ['| Warning ' + expected[5]]
        notEvaluated()
    }

    @IgnoreIf({ os.windows })
    void "a script is run by the Gradle task runScriptCommand of the project, with every argument as the one argument it is, and is said to be deprecated"() {
        given:
        plainProject()
        catalog([])
        script('DataImport.groovy')
        List<String> arguments = ['--stacktrace', '-x', '-h', '--help', '--', '', ' ', 'two  words "double" \'single\' $HOME * \\', 'line\nbreak\ttab', '\u00e9\ud83d\ude00\u202e',
                                  'a @|red x|@ %n ${sys:user.home}', '--args-file=/etc/passwd', '--arg=x', '-Dgrails.env=prod', 'cA', '\u001b[31m', '../../etc', 'data-import',
                                  '--plain-output', '--non-interactive']

        when:
        int exit = ForgeCli.execute(beanContext, (['dI'] + arguments) as String[])
        File file = new File(lineOf('file='))

        then:
        exit == 0
        lineOf('requested=') == '<runScriptCommand><--args-file=>'
        lineOf('vector=') == (['data-import'] + arguments).collect { '<' + it.getBytes('UTF-8').encodeHex() + '>' }.join('')
        lineOf('option=') == '.grails/run/command-args-0.json'
        lineOf('permissions=') == 'rw------- rwx------'
        errors() == []

        and: 'before Gradle is started it is said what is run, and that scripts are deprecated'
        err.toString().readLines()[0] == '| Running the script data-import of src/main/scripts, with the Gradle task runScriptCommand'
        err.toString().readLines()[1] == DEPRECATED

        and: 'the file of the arguments is gone; the init script with which Gradle checks the path of the project stays, see ProjectPathCheck'
        !file.exists()
        new File(dir, '.grails/run').list().sort() - ProjectPathCheck.FILE == ['.gitignore']

        and: 'the script was run by nothing but the build, which here is a stand-in that runs none'
        notEvaluated()
    }

    @IgnoreIf({ os.windows })
    void "the stack trace that is asked for in front of a script is asked of the build that runs it"() {
        given:
        plainProject()
        catalog([])
        script('report.groovy')

        when:
        int exit = ForgeCli.execute(beanContext, '-x', 'report', '-x')

        then: 'as for a command that is a build; after the name, -x is an argument of the script'
        exit == 0
        lineOf('requested=') == '<runScriptCommand><--args-file=>'
        lineOf('system=') == '<grails.full.stacktrace=true>'
        lineOf('stacktrace=') == 'ALWAYS'
        lineOf('vector=') == ['report', '-x'].collect { '<' + it.bytes.encodeHex() + '>' }.join('')

        when: 'without it'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'report')

        then: 'a script is not given the environment a command of the application runs in'
        exit == 0
        lineOf('system=') == ''
        lineOf('stacktrace=') == 'INTERNAL_EXCEPTIONS'
    }

    @IgnoreIf({ os.windows })
    void "a script that fails is a failure of the command of the CLI, with one line after what Gradle says"() {
        given:
        plainProject()
        catalog([])
        script('report.groovy')

        when:
        int exit = ForgeCli.execute(beanContext, 'report', 'fail')

        then:
        exit == 1
        all().contains('this command fails on purpose')
        errors() == ['| Error Gradle did not succeed: it ended with exit code 1']
        !all().contains('Usage:')
        new File(dir, '.grails/run').list().sort() - ProjectPathCheck.FILE == ['.gitignore']
    }

    @Unroll
    void "arguments the Gradle task would refuse are refused before a build is started, without being repeated: #what"() {
        given:
        catalog([])
        script('report.groovy')

        when:
        int exit = ForgeCli.execute(beanContext, (['report'] + arguments) as String[])

        then:
        exit == 2
        err.toString().readLines() == ['The command cannot be given these arguments: ' + message,
                                       'Usage: grails-forge-cli report [ARGS...]',
                                       "Run 'grails-forge-cli help report' for the options"]
        dir.list().sort() == ['.grails', 'grails-app', 'src']
        new File(dir, '.grails').list() == ['cache']
        notEvaluated()

        where:
        what                   | arguments                 | message
        'the character U+0000' | ['fine', 'hostile\u0000'] | 'argument 3 has the character U+0000, which no command line carries'
        'half a character'     | ['hostile\ud83d']         | 'argument 2 has half of a character of two code units, which cannot be written as text'
        'too many'             | ['hostile'] * 4096        | 'these are 4097 arguments, and the task that runs a command takes 4096 at most'
        'too much'             | ['hostile' * 150_000]     | 'the arguments are more than 1048576 bytes in UTF-8, and the task that runs a command reads no more'
    }

    void "a script is not run without the Gradle wrapper of the project"() {
        given:
        catalog([])
        script('report.groovy')

        when:
        int exit = ForgeCli.execute(beanContext, 'report', 'Alice')

        then:
        exit == 1
        errors().size() == 1
        errors()[0].contains('has no Gradle wrapper')
        notEvaluated()
    }

    void "the scripts of a project above that is not the user's own are no commands"() {
        given:
        catalog([])
        script('report.groovy')
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
        exit = ForgeCli.execute(beanContext, 'report')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command report')
        !new File(below, 'evaluated.txt').exists()
        notEvaluated()

        when: 'the project is the user\'s own: its scripts are commands in every directory of it'
        ConsoleProject.setOwnedByUser(null)
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        described = document()

        then:
        exit == 0
        ((List<Map>) described.commands).findAll { it.registration == 'script' }*.name == ['report']
        !new File(below, 'evaluated.txt').exists()
        notEvaluated()
    }

    void "no script is looked for by a command that has nothing to do with the project of the directory, and a name that is no command is none"() {
        given:
        catalog([])
        script('report.groovy')
        // where a file can have the name: Windows has no file with a | in its name
        if (File.separatorChar != ('\\' as char)) {
            new File(dir, 'src/main/scripts/@|red x|@.groovy').text = MARKER
        }

        when:
        int exit = ForgeCli.execute(beanContext, 'create-app', '--help')

        then:
        exit == 0
        err.toString().isEmpty()
        !out.toString().contains('src/main/scripts')

        when: 'something is typed that is no command'
        exit = ForgeCli.execute(beanContext, 'repotr')

        then:
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command repotr')
        notEvaluated()
    }

    void "a script that has the name of a command of earlier versions is the script, and nothing is said about the earlier command"() {
        given:
        catalog([])
        script('list-profiles.groovy')

        when:
        int exit = ForgeCli.execute(beanContext, 'help', 'list-profiles')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines()[0] == 'Usage: grails-forge-cli list-profiles [ARGS...]'
        out.toString().contains('A script of the application')

        when: 'another one of that table, which no script is'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'profile-info')

        then: 'the line of the table follows the refusal'
        exit == 2
        err.toString().readLines()[0].startsWith('There is no command profile-info')
        err.toString().readLines()[1].startsWith('profile-info described a profile')
        notEvaluated()
    }

    void "the name of a file gives the name of a command by the table the build that runs the script is held to as well"() {
        given: 'the table, which this build and the build of the framework each have a copy of'
        List<List<String>> cases = table('script-command-names.tsv')

        expect:
        cases.size() > 30
        cases.each { List<String> each ->
            assert ScriptCommandFiles.commandName(each[0]) == (each[1] == '-' ? null : each[1])
        }
    }

    @IgnoreIf({ os.windows })
    void "a name is listed exactly when the build has a file of the project to run for it, by the table the build is held to as well"() {
        given:
        List<List<String>> cases = table('script-command-files.tsv')
        File top = Files.createTempDirectory('grailsforgescripts').toFile().canonicalFile

        expect:
        cases.size() > 12
        cases.eachWithIndex { List<String> each, int number ->
            File project = layout(new File(top, "case${number}"), each[0])
            ScriptCommandFiles listed = ScriptCommandFiles.list(project.toPath())
            assert (each[1] in listed.commands*.name) == (each[2] != '-'), each.toString()
        }

        cleanup:
        top?.deleteDir()
    }

    // A project and what is outside it, as a line of the table of cases says: see script-command-files.tsv.
    private static File layout(File top, String what) {
        File project = new File(top, 'project')
        File outside = new File(top, 'outside')
        new File(outside, 'main/scripts').mkdirs()
        String elsewhere = "new File(baseDir, 'ran.txt').text = 'outside'\n"
        [outside, new File(outside, 'main'), new File(outside, 'main/scripts')].each { File each -> new File(each, 'planted.groovy').text = elsewhere }
        new File(outside, 'script.groovy').text = elsewhere
        List<String> items = what.split(' ').toList()
        String link = items.find { String item -> item.startsWith('L:') }?.substring(2)
        File scripts = new File(project, 'src/main/scripts')
        if (link == null) {
            scripts.mkdirs()
        } else {
            File at = new File(project, link)
            at.parentFile.mkdirs()
            Files.createSymbolicLink(at.toPath(), (link == 'src' ? outside : new File(outside, link.substring(4))).toPath())
        }
        items.each { String item ->
            File file = new File(scripts, item.substring(2))
            if (item.startsWith('f:')) {
                file.text = "new File(baseDir, 'ran.txt').text = '${file.name}'\n"
            } else if (item.startsWith('l:')) {
                Files.createSymbolicLink(file.toPath(), new File(outside, 'script.groovy').toPath())
            } else if (item.startsWith('d:')) {
                file.mkdirs()
            }
        }
        return project
    }

    private List<List<String>> table(String name) {
        URL table = getClass().getResource('/' + name)
        File root = new File(table.toURI())
        while (root != null && !(new File(root, 'grails-forge/grails-forge-cli').isDirectory() && new File(root, 'grails-core/src/test-cli').isDirectory())) {
            root = root.parentFile
        }
        // the two copies are one table
        assert root != null
        assert new File(root, "grails-forge/grails-forge-cli/src/test/resources/${name}").bytes == new File(root, "grails-core/src/test-cli/resources/${name}").bytes
        return table.getText('UTF-8').readLines().findAll { String line -> !line.startsWith('#') && !line.isEmpty() }*.split('\t')*.toList()
    }

    private boolean notEvaluated() {
        assert !new File(dir, 'evaluated.txt').exists()
        assert !new File('evaluated.txt').exists()
        return true
    }

    // a script that would leave a file behind if it were ever evaluated
    private File script(String fileName) {
        File directory = new File(dir, 'src/main/scripts')
        directory.mkdirs()
        File file = new File(directory, fileName)
        file.text = 'description("@|red hostile|@ %n ${sys:user.home}", "usage")\n' + MARKER
        return file
    }

    private void catalog(List commands) {
        File file = new File(dir, '.grails/cache/command-catalog.json')
        file.parentFile.mkdirs()
        file.bytes = ApplicationCommandsSpec.doc(commands).getBytes(StandardCharsets.UTF_8)
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
        // A task of the project of these specifications is named as that of the root of its build, :classes for
        // classes, because the project has the settings of a build: MultiProjectBuildSpec holds that, and here
        // the task is read without the mark
        String line = lines[0].substring(start.length())
        return start == 'requested=' ? line.replaceAll(/<:([A-Za-z]+)>/, '<$1>') : line
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
