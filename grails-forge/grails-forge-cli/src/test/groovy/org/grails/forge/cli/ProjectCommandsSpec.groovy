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
import groovy.json.JsonSlurper

import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.Parser
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.springframework.context.ConfigurableApplicationContext
import picocli.CommandLine

import org.grails.build.parsing.CommandLineParser
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

    private static final List<String> MANIFEST_TASK = ['generateCommandManifest', '-PgrailsCommandManifest=build/grails/commands.json']

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
        gradleInvocations() == [['-p', dir.path, '-q', '--console=plain'] + MANIFEST_TASK]
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
        gradleInvocations() == [['-p', dir.path, '-q', '--console=plain'] + MANIFEST_TASK]
        commandLine.subcommands.containsKey('s2-quickstart')

        where:
        changed << ['build.gradle', 'settings.gradle', 'gradle.properties', 'gradle/libs.versions.toml',
                    'dependencies.gradle', 'gradle/publishing.gradle', 'buildSrc/src/main/groovy/Conventions.groovy',
                    'buildSrc/build.gradle', 'grails-app/commands/example/HelloCommand.groovy']
    }

    void 'the manifest is written again when a command is deleted from the application'() {
        given:
        File command = new File(dir, 'grails-app/commands/example/HelloCommand.groovy')
        command.parentFile.mkdirs()
        command.text = 'class HelloCommand {}'
        writeManifest(URL_MAPPINGS_REPORT)
        [command, command.parentFile, new File(dir, 'grails-app/commands')].each { it.setLastModified(manifest.lastModified() - 10_000) }
        buildWrites(SECURITY_QUICKSTART)

        when: 'the command is deleted, which changes only its directory'
        assert command.delete()
        command.parentFile.setLastModified(manifest.lastModified() + 10_000)
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations()*.first() == ['-p']
        commandLine.subcommands.containsKey('s2-quickstart')
    }

    void 'the manifest is written again when the commands directory is removed with the last command'() {
        given:
        File command = new File(dir, 'grails-app/commands/example/HelloCommand.groovy')
        command.parentFile.mkdirs()
        command.text = 'class HelloCommand {}'
        writeManifest(URL_MAPPINGS_REPORT)
        [command, command.parentFile, command.parentFile.parentFile, new File(dir, 'grails-app'), dir].each {
            it.setLastModified(manifest.lastModified() - 10_000)
        }
        buildWrites(SECURITY_QUICKSTART)

        when: 'the directory is deleted, which changes only grails-app'
        assert new File(dir, 'grails-app/commands').deleteDir()
        new File(dir, 'grails-app').setLastModified(manifest.lastModified() + 10_000)
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == [['-p', dir.path, '-q', '--console=plain'] + MANIFEST_TASK]
        commandLine.subcommands.containsKey('s2-quickstart')
    }

    void 'a project without a commands directory does not run the build while nothing changed'() {
        given:
        assert new File(dir, 'grails-app/commands').deleteDir()
        writeManifest(URL_MAPPINGS_REPORT)
        [new File(dir, 'grails-app'), dir].each { it.setLastModified(manifest.lastModified() - 10_000) }

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == []
        commandLine.subcommands.containsKey('url-mappings-report')
    }

    void 'what the build writes for itself does not make the manifest outdated'() {
        given:
        writeManifest(URL_MAPPINGS_REPORT)
        ['buildSrc/build/classes/Conventions.class', 'buildSrc/.gradle/file-system.probe'].each { String path ->
            File written = new File(dir, path)
            written.parentFile.mkdirs()
            written.text = 'written by the build'
            written.setLastModified(manifest.lastModified() + 10_000)
        }

        when:
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == []
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

    void '--help lists the commands the build already wrote, even when they are outdated, without running the build'() {
        given:
        writeManifest(SECURITY_QUICKSTART)
        new File(dir, 'build.gradle').setLastModified(manifest.lastModified() + 10_000)
        buildWrites(URL_MAPPINGS_REPORT)

        when:
        int exitCode = ForgeCli.execute(context, '--help')

        then:
        exitCode == 0
        output.toString().contains('s2-quickstart')
        !output.toString().contains('url-mappings-report')
        gradleInvocations() == []
    }

    void '--help without a manifest lists only the Forge CLI commands and runs no build'() {
        given:
        buildWrites(SECURITY_QUICKSTART)

        when:
        ForgeCli.execute(context, '--help')

        then:
        output.toString().contains('create-app')
        !output.toString().contains('s2-quickstart')
        gradleInvocations() == []
    }

    void 'a project command runs through runCommand with its arguments, then its options'() {
        given:
        writeManifest(SECURITY_QUICKSTART)

        when:
        int exitCode = ForgeCli.execute(context, 's2-quickstart', '--uiOnly', 'com.example', 'User', 'Role',
                '--groupClassName=RoleGroup')

        then:
        exitCode == 0
        gradleInvocations().size() == 1
        gradleInvocations().first().take(5) == ['-p', dir.path, '-q', '--console=plain', 'runCommand']
        commandArguments() == ['s2-quickstart', 'com.example', 'User', 'Role', '--groupClassName=RoleGroup', '--uiOnly']

        and: 'the file of the arguments is gone once the command has run'
        !new File(argumentsFile()).exists()
    }

    void 'an option value given as a separate argument is passed as one option'() {
        given:
        writeManifest(SECURITY_QUICKSTART)

        when:
        ForgeCli.execute(context, 's2-quickstart', 'com.example', 'User', 'Role', '--groupClassName', 'RoleGroup')

        then:
        commandArguments() == ['s2-quickstart', 'com.example', 'User', 'Role', '--groupClassName=RoleGroup']
    }

    void 'an option value with equals signs in it is passed whole, as #given'() {
        given:
        writeManifest(SECURITY_QUICKSTART)

        when:
        ForgeCli.execute(context, (['s2-quickstart', 'com.example', 'User', 'Role'] + given) as String[])

        then:
        commandArguments() == ['s2-quickstart', 'com.example', 'User', 'Role', '--groupClassName=a=b=c']

        and: 'the parser runCommand reads them with gives the command the whole value'
        with(new CommandLineParser().parse(commandArguments() as String[])) {
            commandName == 's2-quickstart'
            remainingArgs == ['com.example', 'User', 'Role']
            optionValue('groupClassName') == 'a=b=c'
        }

        where:
        given << [['--groupClassName', 'a=b=c'], ['--groupClassName=a=b=c']]
    }

    void 'a command that describes no options is given the words in the order they were typed'() {
        given:
        writeManifest(declared)

        when: 'an undeclared option whose value is a word of its own'
        ForgeCli.execute(context, 'url-mappings-report', '--format', 'json', 'first')

        then: 'as runCommand gives them, so the command reads json as the value of --format'
        commandArguments() == ['url-mappings-report', '--format', 'json', 'first']

        where:
        declared << [URL_MAPPINGS_REPORT, URL_MAPPINGS_REPORT + [arguments: [[name: 'first', required: false]]]]
    }

    void 'arguments are passed on unchanged, whatever quotes and spaces they hold'() {
        given:
        writeManifest(URL_MAPPINGS_REPORT)
        List<String> arguments = ['two words', 'say "hi"', 'it\'s "x" y', '', '%PATH% & | <>']

        when:
        ForgeCli.execute(context, (['url-mappings-report'] + arguments) as String[])

        then:
        commandArguments() == ['url-mappings-report'] + arguments
    }

    void '-D sets a system property of the build, such as the environment, and is not passed to the command'() {
        given:
        writeManifest(SECURITY_QUICKSTART, URL_MAPPINGS_REPORT)

        when:
        ForgeCli.execute(context, typed as String[])

        then:
        gradleInvocations().first().subList(4, 6) == ['-Dgrails.env=test', 'runCommand']
        commandArguments() == passed

        where:
        typed                                                            || passed
        ['s2-quickstart', '-Dgrails.env=test', 'com.example']            || ['s2-quickstart', 'com.example']
        ['url-mappings-report', '-Dgrails.env=test', 'first']            || ['url-mappings-report', 'first']
        ['url-mappings-report', 'first', '-D', 'grails.env=test']        || ['url-mappings-report', 'first']
    }

    void "a project command after the Forge CLI's own options runs the build that finds it"() {
        given:
        buildWrites(URL_MAPPINGS_REPORT)

        when:
        int exitCode = ForgeCli.execute(context, '-x', 'url-mappings-report')

        then:
        exitCode == 0
        gradleInvocations()*.get(4) == ['generateCommandManifest', 'runCommand']
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

    void 'a build that cannot write the manifest, such as one of an earlier Grails, is reported once, and the Forge CLI still runs'() {
        given:
        new File(dir, 'gradle-exit-code').text = '1'

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains('Could not read the commands of the project: its build failed with exit code 1. ' +
                'Its commands are not offered, and the build is not run for them again until a build file changes. ' +
                'A project of Grails 9 or later lists them with its generateCommandManifest Gradle task; run it to see what went wrong.')
        commandLine.subcommands.containsKey('create-app')
        !commandLine.subcommands.containsKey('s2-quickstart')

        when: 'the Forge CLI starts again in the same project'
        ForgeCli.createCommandLine(context, false)

        then: 'the build is not run again'
        gradleInvocations().size() == 1

        when: 'a build file changes, and the build works again'
        new File(dir, 'gradle-exit-code').delete()
        buildWrites(SECURITY_QUICKSTART)
        new File(dir, 'build.gradle').setLastModified(System.currentTimeMillis() + 20_000)
        commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 2
        commandLine.subcommands.containsKey('s2-quickstart')
    }

    void 'a failed build keeps the commands of the last manifest, and is not run again for them'() {
        given: 'an outdated manifest, and a build that fails, as while the application does not compile'
        writeManifest(URL_MAPPINGS_REPORT)
        changedBefore(20_000, manifest)
        changedBefore(10_000, new File(dir, 'build.gradle'))
        new File(dir, 'gradle-exit-code').text = '1'

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        commandLine.subcommands.containsKey('url-mappings-report')
        output.toString().contains('The commands it listed before are offered')

        when:
        new File(dir, 'gradle-exit-code').delete()
        int exitCode = ForgeCli.execute(context, 'url-mappings-report')

        then: 'the command runs without the build being tried again'
        exitCode == 0
        gradleInvocations()*.get(4) == ['generateCommandManifest', 'runCommand']
    }

    void 'a wrapper that cannot be started is reported, and the Forge CLI still runs'() {
        given:
        writeManifest(URL_MAPPINGS_REPORT)
        new File(dir, 'build.gradle').setLastModified(manifest.lastModified() + 10_000)
        Files.setPosixFilePermissions(new File(dir, 'gradlew').toPath(), [PosixFilePermission.OWNER_READ] as Set)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains('Could not read the commands of the project: ')
        commandLine.subcommands.containsKey('create-app')
        commandLine.subcommands.containsKey('url-mappings-report')
    }

    void 'a build whose manifest task is up to date is not run again'() {
        given: 'a build file changed in a way that leaves the classpath, and so the manifest, as it was'
        writeManifest(URL_MAPPINGS_REPORT)
        changedBefore(20_000, manifest)
        changedBefore(10_000, new File(dir, 'build.gradle'))

        when:
        ForgeCli.createCommandLine(context, false)
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 1
    }

    void 'a manifest #problem is reported, and the Forge CLI still runs'() {
        given:
        manifest.parentFile.mkdirs()
        manifest.text = JsonOutput.toJson(content)
        manifest.setLastModified(System.currentTimeMillis() + 10_000)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains("Could not read the commands of the project from ${manifest}, so they are not offered: ${message}")
        commandLine.subcommands.containsKey('create-app')
        gradleInvocations() == []

        where:
        problem                          | content                                              || message
        'of a newer format'              | [version: 2, commands: [URL_MAPPINGS_REPORT]]        || 'it was written in format 2, by a newer Grails'
        'without a format version'       | [commands: [URL_MAPPINGS_REPORT]]                    || 'it has no format version'
        'whose commands are not a list'  | [version: 1, commands: 'oops']                       || 'it has no list of commands'
        'with a command without a name'  | [version: 1, commands: [[description: 'nameless']]]  || 'a command has no name'
        'that is not an object'          | [URL_MAPPINGS_REPORT]                                || 'it is not a JSON object'
    }

    void 'a manifest that is not JSON is reported'() {
        given:
        manifest.parentFile.mkdirs()
        manifest.text = 'not json'

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains("Could not read the commands of the project from ${manifest}, so they are not offered")
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
        complete(commandLine, 's2-create-persistent-token --format=').containsAll(['--format=json', '--format=yaml'])
        complete(commandLine, 's2-create-persistent-token --format=j').contains('--format=json')
    }

    void 'a project of a multi-project build runs the wrapper at the root of the build'() {
        given: 'the project in a subdirectory of a build whose wrapper and settings are at its root'
        File project = moveProjectInto('app')
        new File(dir, 'settings.gradle').text = "include 'app'\n"
        writeManifest(new File(project, 'build/grails/commands.json'), URL_MAPPINGS_REPORT)

        when:
        int exitCode = ForgeCli.execute(context, 'url-mappings-report')

        then:
        exitCode == 0
        gradleInvocations()*.take(5) == [['-p', project.path, '-q', '--console=plain', 'runCommand']]
        commandArguments() == ['url-mappings-report']
    }

    void 'a wrapper above the root of the build is never run'() {
        given: 'a project with a settings file of its own but no wrapper, inside a directory that has one'
        File project = moveProjectInto('app')
        new File(project, 'settings.gradle').text = ''
        writeManifest(new File(project, 'build/grails/commands.json'), URL_MAPPINGS_REPORT)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        !commandLine.subcommands.containsKey('url-mappings-report')
        output.toString().contains('The build of the project has no Gradle wrapper, so its commands are not offered.')
        gradleInvocations() == []
    }

    void 'a wrapper above a project without a settings file is never run'() {
        given:
        File project = moveProjectInto('app')
        buildWrites(URL_MAPPINGS_REPORT)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        !commandLine.subcommands.containsKey('url-mappings-report')
        gradleInvocations() == []
        !new File(project, 'build/grails/commands.json').exists()
    }

    /**
     * Moves the project into a subdirectory of the directory that keeps the Gradle wrapper, and makes
     * it the current directory.
     */
    private File moveProjectInto(String name) {
        File project = new File(dir, name)
        assert project.mkdirs()
        assert new File(dir, 'grails-app').renameTo(new File(project, 'grails-app'))
        assert new File(dir, 'build.gradle').renameTo(new File(project, 'build.gradle'))
        System.setProperty('user.dir', project.path)
        project
    }

    /**
     * Dates the file in the past, so that what the Forge CLI writes now is newer than it.
     */
    private static void changedBefore(long millis, File file) {
        assert file.setLastModified(System.currentTimeMillis() - millis)
    }

    private void buildWrites(Map... commands) {
        new File(dir, 'manifest-source.json').text = JsonOutput.toJson([version: 1, commands: commands.toList()])
    }

    private void writeManifest(Map... commands) {
        writeManifest(manifest, commands)
    }

    private static void writeManifest(File file, Map... commands) {
        file.parentFile.mkdirs()
        file.text = JsonOutput.toJson([version: 1, commands: commands.toList()])
        file.setLastModified(System.currentTimeMillis() + 10_000)
    }

    private List<List<String>> gradleInvocations(File wrapperDir = dir) {
        File log = new File(wrapperDir, 'gradle-invocations.txt')
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

    /**
     * The arguments the last command ran with, from the file runCommand was given, as the stand-in
     * wrapper logged it.
     */
    private List<String> commandArguments() {
        String logged = gradleInvocations().last().find { String argument -> argument.startsWith('argsFile:') }
        (List<String>) new JsonSlurper().parseText(logged.substring('argsFile:'.length()))
    }

    private String argumentsFile() {
        gradleInvocations().last().find { String argument -> argument.startsWith('-PargsFile=') }.substring('-PargsFile='.length())
    }

    private void writeGradleWrapper(File wrapperDir = dir) {
        File wrapper = new File(wrapperDir, 'gradlew')
        wrapper.text = '''\
            #!/bin/sh
            dir=$(cd "$(dirname "$0")" && pwd)
            { for argument in "$@"; do
                printf '%s\\n' "$argument"
                case "$argument" in -PargsFile=*) printf 'argsFile:%s\\n' "$(cat "${argument#-PargsFile=}")";; esac
              done; echo '--'; } >> "$dir/gradle-invocations.txt"
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
