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
package org.grails.forge.cli

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

import groovy.json.JsonOutput

import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.Parser
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.springframework.context.ConfigurableApplicationContext
import picocli.CommandLine

import org.grails.forge.cli.command.CreateAppCommand
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

// the project's build is a shell script standing in for the Gradle wrapper
@IgnoreIf({ os.windows })
class ProjectCommandsSpec extends Specification {

    private static final Map SECURITY_QUICKSTART = [
            name       : 's2-quickstart',
            description: 'Creates domain classes and updates config settings for the Spring Security plugin',
            arguments  : [
                    [name: 'package', description: 'The package of the domain classes', required: false, values: []],
                    [name: 'user-class-name', description: 'The name of the user class', required: false, values: []],
                    [name: 'role-class-name', description: 'The name of the role class', required: false, values: []]
            ],
            options    : [
                    [name: 'groupClassName', description: 'Also generate role group classes', flag: false, values: []],
                    [name: 'uiOnly', description: 'Configure without generating classes', flag: true, values: []]
            ]
    ]

    private static final Map CREATE_TOKEN = [
            name       : 's2-create-persistent-token',
            description: 'Creates a persistent token domain class',
            arguments  : [[name: 'domain-class-name', description: 'The name of the domain class', required: true, values: []]],
            options    : [[name: 'format', description: 'The format', flag: false, values: ['json', 'yaml']]]
    ]

    private static final Map URL_MAPPINGS_REPORT = [
            name: 'url-mappings-report', description: 'url-mappings-report', arguments: [], options: []
    ]

    private static final Map PLUGIN_CREATE_APP = [
            name: 'create-app', description: 'A plugin command named like a Forge command', arguments: [], options: []
    ]

    @Shared
    @AutoCleanup
    ConfigurableApplicationContext context = Application.builder().run()

    @Shared
    @AutoCleanup
    Terminal terminal = TerminalBuilder.builder()
            .type(Terminal.TYPE_DUMB)
            .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
            .build()

    File dir = Files.createTempDirectory('forge-project-commands').toFile().canonicalFile
    File manifest = new File(dir, 'build/grails/commands.json')
    ByteArrayOutputStream output = new ByteArrayOutputStream()

    private String previousUserDir
    private PrintStream previousOut
    private PrintStream previousErr

    void setup() {
        previousUserDir = System.getProperty('user.dir')
        System.setProperty('user.dir', dir.path)
        previousOut = System.out
        previousErr = System.err
        PrintStream captured = new PrintStream(output, true)
        System.out = captured
        System.err = captured
        assert new File(dir, 'grails-app/commands').mkdirs()
        new File(dir, 'build.gradle').text = "plugins { id 'org.apache.grails.gradle.grails-web' }\n"
        writeGradleWrapper()
    }

    void cleanup() {
        System.out = previousOut
        System.err = previousErr
        System.setProperty('user.dir', previousUserDir)
        dir.deleteDir()
    }

    void 'the commands of a project without a manifest are read from its build first'() {
        given:
        buildWrites(SECURITY_QUICKSTART, URL_MAPPINGS_REPORT)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then: "the build is run once, quietly, in the project directory"
        gradleInvocations() == [['-p', dir.path, '-q', '--console=plain', 'generateCommandManifest']]
        output.toString().contains('Reading the commands of the project from its build...')

        and: "the project's commands are offered next to the Forge CLI's own"
        commandLine.subcommands.keySet().containsAll(['create-app', 's2-quickstart', 'url-mappings-report'])
    }

    void 'a manifest newer than the build is read without running the build'() {
        given:
        writeManifest(SECURITY_QUICKSTART)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        commandLine.subcommands.containsKey('s2-quickstart')
        gradleInvocations() == []
    }

    void 'the manifest is written again when #changed changed after it'() {
        given:
        writeManifest(URL_MAPPINGS_REPORT)
        buildWrites(SECURITY_QUICKSTART)
        File changedFile = new File(dir, changed)
        changedFile.parentFile.mkdirs()
        changedFile.text = 'changed'
        changedFile.setLastModified(manifest.lastModified() + 10_000)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations()*.last() == ['generateCommandManifest']
        commandLine.subcommands.containsKey('s2-quickstart')

        where:
        changed << ['build.gradle', 'settings.gradle', 'gradle.properties', 'gradle/libs.versions.toml',
                    'grails-app/commands/example/HelloCommand.groovy']
    }

    void 'a directory without a Grails project has no project commands and runs no build'() {
        given:
        new File(dir, 'grails-app').deleteDir()
        buildWrites(SECURITY_QUICKSTART)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        !commandLine.subcommands.containsKey('s2-quickstart')
        gradleInvocations() == []
    }

    void "the Forge CLI's own commands run without reading the project's commands"() {
        given:
        buildWrites(SECURITY_QUICKSTART)

        expect:
        ForgeCli.execute(context, 'create-app', '--help') == 0
        gradleInvocations() == []
    }

    void "a project command named like one of the Forge CLI's commands does not replace it"() {
        given:
        writeManifest(PLUGIN_CREATE_APP, URL_MAPPINGS_REPORT)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        commandLine.subcommands['create-app'].commandSpec.userObject() instanceof CreateAppCommand
        commandLine.subcommands.containsKey('url-mappings-report')
    }

    void 'a project command runs through runCommand with its arguments, then its options'() {
        given:
        writeManifest(SECURITY_QUICKSTART)

        when:
        int exitCode = ForgeCli.execute(context, 's2-quickstart', '--uiOnly', 'com.example', 'User', 'Role',
                '--groupClassName=RoleGroup')

        then:
        exitCode == 0
        gradleInvocations() == [['-p', dir.path, '-q', '--console=plain', 'runCommand',
                                 '-Pargs=s2-quickstart com.example User Role --groupClassName=RoleGroup --uiOnly']]
    }

    void 'an option value given as a separate argument is passed as one option'() {
        given:
        writeManifest(SECURITY_QUICKSTART)

        when:
        ForgeCli.execute(context, 's2-quickstart', 'com.example', 'User', 'Role', '--groupClassName', 'RoleGroup')

        then:
        gradleInvocations()*.last() == ['-Pargs=s2-quickstart com.example User Role --groupClassName=RoleGroup']
    }

    void 'a command that describes no arguments or options passes on whatever it is given, quoting what needs it'() {
        given:
        writeManifest(URL_MAPPINGS_REPORT)

        when:
        ForgeCli.execute(context, 'url-mappings-report', 'first', 'two words', '--format=json', 'say "hi"', '')

        then: 'the arguments in the order given, then the options it does not know'
        gradleInvocations()*.last() == ['-Pargs=url-mappings-report first "two words" \'say "hi"\' "" --format=json']
    }

    void 'the exit code of the command is the exit code of the build'() {
        given:
        writeManifest(URL_MAPPINGS_REPORT)
        new File(dir, 'gradle-exit-code').text = '3'

        expect:
        ForgeCli.execute(context, 'url-mappings-report') == 3
    }

    void 'a missing required argument or an undeclared option is reported without running the build'() {
        given:
        writeManifest(CREATE_TOKEN, SECURITY_QUICKSTART)

        expect:
        ForgeCli.execute(context, arguments as String[]) != 0
        gradleInvocations() == []

        where:
        arguments << [['s2-create-persistent-token'], ['s2-quickstart', '--unknown']]
    }

    void 'the help of a project command shows what it declares'() {
        given:
        writeManifest(SECURITY_QUICKSTART)

        when:
        ForgeCli.execute(context, 's2-quickstart', '--help')

        then: 'the usage, as picocli wraps it, read as one line'
        String help = output.toString().split(/\s+/).join(' ')
        help.contains('Creates domain classes and updates config settings for the Spring Security plugin')
        help.contains('[package] [user-class-name] [role-class-name]')
        help.contains('The package of the domain classes')
        help.contains('--groupClassName=GROUPCLASSNAME')
        help.contains('--uiOnly')
        gradleInvocations() == []
    }

    void 'a build that cannot write the manifest is reported, and the Forge CLI still runs'() {
        given:
        new File(dir, 'gradle-exit-code').text = '1'

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains('Could not read the commands of the project. Run its generateCommandManifest Gradle task to see why.')
        commandLine.subcommands.containsKey('create-app')
        !commandLine.subcommands.containsKey('s2-quickstart')
    }

    void 'a manifest that is not JSON is reported'() {
        given:
        manifest.parentFile.mkdirs()
        manifest.text = 'not json'

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains("Could not read the commands of the project from ${manifest}")
        commandLine.subcommands.containsKey('create-app')
    }

    void 'the prompt completes project commands, their options and the values they declare'() {
        given:
        writeManifest(SECURITY_QUICKSTART, CREATE_TOKEN)
        CommandLine commandLine = ForgeCli.createCommandLine(context, true)

        expect:
        complete(commandLine, 's2-').containsAll(['s2-quickstart', 's2-create-persistent-token'])
        complete(commandLine, 's2-quickstart --').containsAll(['--groupClassName', '--uiOnly', '--help'])
        complete(commandLine, 's2-create-persistent-token --format ').containsAll(['json', 'yaml'])
    }

    private void buildWrites(Map... commands) {
        new File(dir, 'manifest-source.json').text = JsonOutput.toJson([version: 1, commands: commands.toList()])
    }

    private void writeManifest(Map... commands) {
        manifest.parentFile.mkdirs()
        manifest.text = JsonOutput.toJson([version: 1, commands: commands.toList()])
        manifest.setLastModified(System.currentTimeMillis() + 10_000)
    }

    private List<List<String>> gradleInvocations() {
        File log = new File(dir, 'gradle-invocations.txt')
        if (!log.isFile()) {
            return []
        }
        List<List<String>> invocations = []
        List<String> current = []
        for (String line : log.readLines()) {
            if (line == '--') {
                invocations << current
                current = []
            } else {
                current << line
            }
        }
        invocations
    }

    private void writeGradleWrapper() {
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '''\
            #!/bin/sh
            dir=$(cd "$(dirname "$0")" && pwd)
            { for argument in "$@"; do printf '%s\\n' "$argument"; done; echo '--'; } >> "$dir/gradle-invocations.txt"
            case " $* " in
              *" generateCommandManifest "*)
                if [ -f "$dir/manifest-source.json" ]; then
                  mkdir -p "$dir/build/grails" && cp "$dir/manifest-source.json" "$dir/build/grails/commands.json"
                fi;;
            esac
            if [ -f "$dir/gradle-exit-code" ]; then exit "$(cat "$dir/gradle-exit-code")"; fi
            exit 0
            '''.stripIndent()
        Files.setPosixFilePermissions(wrapper.toPath(), [PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                                         PosixFilePermission.OWNER_EXECUTE] as Set)
    }

    private List<String> complete(CommandLine commandLine, String typed) {
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build()
        List<Candidate> candidates = []
        new CommandLineCompleter(commandLine.commandSpec)
                .complete(reader, new DefaultParser().parse(typed, typed.length(), Parser.ParseContext.COMPLETE), candidates)
        candidates*.value()
    }
}
