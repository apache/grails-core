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

    /** Files the commands depend on, in every place the Forge CLI watches without a manifest naming them */
    private static final List<String> WATCHED_FILES = [
            'build.gradle', 'settings.gradle', 'settings.gradle.kts', 'gradle.properties', 'dependencies.gradle',
            'libs.versions.toml', 'gradle/libs.versions.toml', 'gradle/publishing.gradle',
            'gradle/wrapper/gradle-wrapper.properties', 'buildSrc/build.gradle',
            'buildSrc/src/main/groovy/Conventions.groovy', 'buildSrc/src/main/groovy/com/acme/build/Conventions.groovy',
            'grails-app/commands/example/HelloCommand.groovy', 'grails-app/commands/com/acme/build/DeployCommand.groovy',
            'src/main/resources/META-INF/grails-cli.factories'
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
        gradleInvocations() == [prefix() + MANIFEST_TASK]
        output.toString().contains('Reading the commands of the project from its build...')

        and: "the project's commands are offered next to the Forge CLI's own"
        commandLine.subcommands.keySet().containsAll(['create-app', 's2-quickstart', 'url-mappings-report'])
    }

    void 'a manifest whose files are unchanged is read without running the build'() {
        given:
        writeManifest(SECURITY_QUICKSTART)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == []
        commandLine.subcommands.containsKey('s2-quickstart')
    }

    void 'the manifest is written again when #path is #change'() {
        given: 'the file there when the Forge CLI last ran the build, unless it is added later'
        File file = new File(dir, path)
        if (change != 'added') {
            file.parentFile.mkdirs()
            file.text = 'a=1'
            file.setLastModified(System.currentTimeMillis() - 3_600_000)
        }
        writeManifest(URL_MAPPINGS_REPORT)
        long recorded = file.lastModified()
        buildWrites(SECURITY_QUICKSTART)

        when:
        switch (change) {
            case 'added':
                file.parentFile.mkdirs()
                file.text = 'a=1'
                break
            case 'changed in size with its time kept':
                file.text = 'a=12'
                file.setLastModified(recorded)
                break
            case 'replaced by an older copy':
                file.text = 'b=2'
                file.setLastModified(recorded - 60_000)
                break
            case 'deleted':
                assert file.delete()
                break
        }
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == [prefix() + MANIFEST_TASK]
        commandLine.subcommands.containsKey('s2-quickstart')

        and: 'the change is recorded, so the build is not run again for it'
        ForgeCli.createCommandLine(context, false)
        gradleInvocations().size() == 1

        where:
        [path, change] << [WATCHED_FILES, ['added', 'changed in size with its time kept', 'replaced by an older copy', 'deleted']]
                .combinations()
                .findAll { String path, String change -> !(path == 'build.gradle' && change in ['added', 'deleted']) }
    }

    void 'the manifest is written again when the #removed directory is removed'() {
        given:
        File file = new File(dir, "${removed}/example/Hello.groovy")
        file.parentFile.mkdirs()
        file.text = 'class Hello {}'
        writeManifest(URL_MAPPINGS_REPORT)
        buildWrites(SECURITY_QUICKSTART)

        when:
        assert new File(dir, removed).deleteDir()
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == [prefix() + MANIFEST_TASK]

        where:
        removed << ['grails-app/commands', 'buildSrc', 'gradle', 'src/main/resources/META-INF']
    }

    void 'a project without the directories the commands depend on does not run the build while nothing changed'() {
        given:
        assert new File(dir, 'grails-app/commands').deleteDir()
        writeManifest(URL_MAPPINGS_REPORT)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == []
        commandLine.subcommands.containsKey('url-mappings-report')
    }

    void 'a manifest the build wrote without the Forge CLI is written again, as a file may have been deleted since'() {
        given:
        writeManifestOfTheBuild(URL_MAPPINGS_REPORT)
        buildWrites(SECURITY_QUICKSTART)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == [prefix() + MANIFEST_TASK]
        commandLine.subcommands.containsKey('s2-quickstart')

        and: 'the files are recorded, so it is not run again'
        ForgeCli.createCommandLine(context, false)
        gradleInvocations().size() == 1
    }

    void 'what builds and tools write next to a build script, and the sources the build compiles, do not make the manifest outdated'() {
        given:
        new File(dir, 'buildSrc/build.gradle').with { parentFile.mkdirs(); text = '' }
        writeManifest(URL_MAPPINGS_REPORT)

        when:
        ['buildSrc/build/classes/Conventions.class', 'buildSrc/.gradle/file-system.probe', 'buildSrc/.kotlin/sessions/a',
         'buildSrc/bin/main/Conventions.class', 'buildSrc/out/Conventions.class', 'build/libs/app.jar',
         '.gradle/file-system.probe', 'src/main/groovy/example/Service.groovy', 'grails-app/controllers/Home.groovy',
         'README.md'].each { String path ->
            File written = new File(dir, path)
            written.parentFile.mkdirs()
            written.text = 'written'
        }
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == []
    }

    void 'a source package named like an output directory is watched like any other'() {
        given:
        File command = new File(dir, "grails-app/commands/com/acme/${name}/DeployCommand.groovy")
        command.parentFile.mkdirs()
        writeManifest(URL_MAPPINGS_REPORT)

        when:
        command.text = 'class DeployCommand {}'
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == [prefix() + MANIFEST_TASK]

        where:
        name << ['build', 'bin', 'out']
    }

    void 'a file dated in the future makes the build run once, not on every start'() {
        given:
        File future = new File(dir, 'gradle.properties')
        future.text = 'a=1'
        future.setLastModified(System.currentTimeMillis() + 86_400_000)

        when:
        ForgeCli.createCommandLine(context, false)
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 1
    }

    void 'a file changed while the build runs makes the build run again next time'() {
        given: 'a build during which a command is saved'
        buildWrites(URL_MAPPINGS_REPORT)
        new File(dir, 'during-build.sh').text = 'printf "class Hello {}" > "$1/grails-app/commands/Hello.groovy"\n'

        when:
        ForgeCli.createCommandLine(context, false)
        new File(dir, 'during-build.sh').delete()
        ForgeCli.createCommandLine(context, false)
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 2
    }

    void 'the directories the manifest names are watched, such as those of the other projects of the build'() {
        given: 'a manifest naming a plugin project next to the application'
        File plugin = new File(dir, 'plugins/audit')
        new File(plugin, 'grails-app/commands').mkdirs()
        new File(plugin, 'build.gradle').text = ''
        buildWritesWithInputs([buildDirectories: [dir.path, plugin.path],
                     sourceDirectories: [new File(dir, 'grails-app/commands').path, new File(plugin, 'grails-app/commands').path]],
                URL_MAPPINGS_REPORT)
        ForgeCli.createCommandLine(context, false)
        forgetInvocations()

        when: 'the plugin gains a command'
        new File(plugin, 'grails-app/commands/AuditCommand.groovy').text = 'class AuditCommand {}'
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == [prefix() + MANIFEST_TASK]

        when: "the plugin's build changes"
        new File(plugin, 'build.gradle').text = 'plugins {}'
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 2
    }

    void 'the source of a command the manifest names is watched, wherever it is'() {
        given:
        File source = new File(dir, 'plugins/audit/src/main/groovy/com/acme/AuditCommand.groovy')
        source.parentFile.mkdirs()
        source.text = 'class AuditCommand {}'
        buildWritesWithInputs([buildDirectories: [dir.path], sourceDirectories: [], sourceFiles: [source.path]], URL_MAPPINGS_REPORT)
        ForgeCli.createCommandLine(context, false)
        forgetInvocations()

        when: 'the command gains an option'
        source.text = 'class AuditCommand { /* @CommandOption */ }'
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == [prefix() + MANIFEST_TASK]
    }

    void 'what tools write in an included build does not make the manifest outdated, wherever the build is'() {
        given: 'an included build, below a directory named src, with a subproject that has no build file of its own'
        File included = new File(dir, 'src/shared')
        new File(included, 'lib/src/main/groovy/com/acme/build').mkdirs()
        new File(included, 'lib/src/main/webapp').mkdirs()
        new File(included, 'settings.gradle.kts').text = 'include("lib")'
        buildWritesWithInputs([buildDirectories: [dir.path], sourceDirectories: [included.path]], URL_MAPPINGS_REPORT)
        ForgeCli.createCommandLine(context, false)
        forgetInvocations()

        when:
        ['lib/build/classes/A.class', 'lib/.gradle/probe', '.gradle/probe', '.kotlin/sessions/a', 'lib/bin/A.class',
         'node_modules/.cache/x', 'lib/src/main/webapp/node_modules/.cache/x', '.DS_Store', 'lib/.settings/prefs'].each { String path ->
            File written = new File(included, path)
            written.parentFile.mkdirs()
            written.text = 'written'
        }
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations() == []

        when: 'a source of the included build changes, in a package named build'
        new File(included, 'lib/src/main/groovy/com/acme/build/Plugin.groovy').text = 'class Plugin {}'
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 1
    }

    void 'a manifest naming #what as an input is read with the inputs of the project and its build instead'() {
        given:
        buildWritesWithInputs([buildDirectories: paths, sourceDirectories: []], URL_MAPPINGS_REPORT)

        when:
        CommandLine first = ForgeCli.createCommandLine(context, false)
        ForgeCli.createCommandLine(context, false)

        then:
        first.subcommands.containsKey('url-mappings-report')
        gradleInvocations().size() == 1

        where:
        what              | paths
        'a relative path' | ['relative/dir']
        'the root'        | ['/']
        'an empty path'   | ['']
        'a path with NUL' | ['/tmp/a\u0000b']
    }

    void 'a link that loops is recorded, not followed'() {
        given:
        File source = new File(dir, 'buildSrc/src/main/groovy')
        source.mkdirs()
        Files.createSymbolicLink(new File(source, 'loop').toPath(), new File(dir, 'buildSrc').toPath())

        when:
        ForgeCli.createCommandLine(context, false)
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 1
        commandLine.subcommands.containsKey('create-app')
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

    void "naming one of the Forge CLI's code generation commands never runs the build, even where it does not apply"() {
        given: 'a project without the configuration the code generation commands need'
        buildWrites(SECURITY_QUICKSTART)

        when:
        ForgeCli.execute(context, 'create-controller', 'Book')

        then:
        gradleInvocations() == []
    }

    void 'no command follows --, so it runs no build'() {
        given:
        buildWrites(URL_MAPPINGS_REPORT)

        when:
        ForgeCli.execute(context, '--', 'url-mappings-report')

        then:
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
        new File(dir, 'build.gradle') << '// changed\n'
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
        gradleInvocations().first().take(6) == prefix() + 'runCommand'
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

    void 'the file that passes the arguments, which can hold a password, is readable by its owner only, and removed after the command'() {
        given:
        writeManifest(URL_MAPPINGS_REPORT)

        when:
        ForgeCli.execute(context, ['url-mappings-report', 'secret'] as String[])

        then:
        gradleInvocations().last().contains('argsFileMode:-rw-------')
        !new File(argumentsFile()).exists()
    }

    void '-D sets a system property of the command, passed among its arguments as runCommand reads it, not on the command line of Gradle'() {
        given:
        writeManifest(SECURITY_QUICKSTART, URL_MAPPINGS_REPORT)

        when:
        ForgeCli.execute(context, typed as String[])

        then:
        gradleInvocations().first().take(7) == prefix() + ['runCommand', "-PargsFile=${argumentsFile()}".toString()]
        gradleInvocations().first().count { it.startsWith('-D') } == 0
        commandArguments() == passed

        and: 'the parser runCommand uses reads it as a system property'
        new CommandLineParser().parse(commandArguments() as String[]).systemProperties == ['grails.env': 'test', query: 'a=b']

        where:
        typed                                                                              || passed
        ['s2-quickstart', '-Dgrails.env=test', 'com.example', '-D', 'query=a=b']           || ['s2-quickstart', 'com.example', '-Dgrails.env=test', '-Dquery=a=b']
        ['url-mappings-report', '-Dgrails.env=test', 'first', '-Dquery=a=b']               || ['url-mappings-report', '-Dgrails.env=test', 'first', '-Dquery=a=b']
        ['url-mappings-report', 'first', '-D', 'grails.env=test', '-D', 'query=a=b']       || ['url-mappings-report', 'first', '-Dgrails.env=test', '-Dquery=a=b']
    }

    void "a project command after the Forge CLI's own options runs the build that finds it"() {
        given:
        buildWrites(URL_MAPPINGS_REPORT)

        when:
        int exitCode = ForgeCli.execute(context, '-x', 'url-mappings-report')

        then:
        exitCode == 0
        gradleInvocations()*.get(5) == ['generateCommandManifest', 'runCommand']
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

        when: 'the build directory is cleaned'
        new File(dir, 'build').deleteDir()
        ForgeCli.createCommandLine(context, false)

        then: 'the failed build is still remembered'
        gradleInvocations().size() == 1

        when: 'a build file changes, and the build works again'
        new File(dir, 'gradle-exit-code').delete()
        buildWrites(SECURITY_QUICKSTART)
        new File(dir, 'build.gradle') << '// changed\n'
        commandLine = ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 2
        commandLine.subcommands.containsKey('s2-quickstart')
        !new File(dir, '.gradle/grails/commands.failed').exists()
    }

    void 'a failed build keeps the commands of the last manifest, and is not run again for them'() {
        given: 'an outdated manifest, and a build that fails, as while the application does not compile'
        writeManifest(URL_MAPPINGS_REPORT)
        new File(dir, 'build.gradle') << '// changed\n'
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
        gradleInvocations()*.get(5) == ['generateCommandManifest', 'runCommand']
    }

    void 'a wrapper that cannot be started is reported, and the Forge CLI still runs'() {
        given:
        writeManifest(URL_MAPPINGS_REPORT)
        new File(dir, 'build.gradle') << '// changed\n'
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
        new File(dir, 'build.gradle') << '// changed\n'

        when:
        ForgeCli.createCommandLine(context, false)
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 1
    }

    void 'a manifest #problem is reported, and the Forge CLI still runs'() {
        given:
        buildWritesContent(content)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains("Could not read the commands of the project from ${manifest}, so they are not offered: ${message}")
        commandLine.subcommands.containsKey('create-app')

        and: 'the build that wrote it is not run again for nothing'
        ForgeCli.createCommandLine(context, false)
        gradleInvocations().size() == 1

        where:
        problem                          | content                                              || message
        'of a newer format'              | [version: 2, commands: [URL_MAPPINGS_REPORT]]        || 'it was written in format 2, by a newer Grails'
        'without a format version'       | [commands: [URL_MAPPINGS_REPORT]]                    || 'it has no format version'
        'whose commands are not a list'  | [version: 1, commands: 'oops']                       || 'it has no list of commands'
        'that is not an object'          | [URL_MAPPINGS_REPORT]                                || 'it is not a JSON object'
    }

    void 'a command that cannot be offered, #problem, costs only that command'() {
        given:
        buildWritesContent([version: 1, commands: [command, URL_MAPPINGS_REPORT]])

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains("The command ${name} of the project is not offered: ")
        commandLine.subcommands.containsKey('url-mappings-report')
        !commandLine.subcommands.containsKey('broken')

        where:
        problem                          | command                                                                    || name
        'as it has no name'              | [description: 'nameless']                                                  || 'without a name'
        'as an option takes --help'      | [name: 'broken', options: [[name: 'help', flag: true]]]                    || 'broken'
        'as it declares an option twice' | [name: 'broken', options: [[name: 'format', flag: false], [name: 'format', flag: true]]] || 'broken'
    }

    void 'a record that cannot be written is reported, and the Forge CLI still runs'() {
        given: 'a file where the directory of the record would be, as a directory the user cannot write'
        new File(dir, '.gradle').text = 'not a directory'
        buildWrites(URL_MAPPINGS_REPORT)

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains('Could not record what the commands of the project depend on in ')
        output.toString().contains('The build is run for them again next time.')
        commandLine.subcommands.containsKey('url-mappings-report')
    }

    void 'a build directory the Forge CLI cannot write is reported, and the Forge CLI still runs'() {
        given:
        buildWrites(URL_MAPPINGS_REPORT)
        new File(dir, 'build').text = 'not a directory'

        when:
        CommandLine commandLine = ForgeCli.createCommandLine(context, false)

        then:
        output.toString().contains('Could not read the commands of the project: ')
        commandLine.subcommands.containsKey('create-app')
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

    void 'after a line that runs the build, the prompt completes the commands the build found'() {
        given: 'the prompt started with the commands of the last manifest'
        writeManifest(URL_MAPPINGS_REPORT)
        CommandLine[] offered = [ForgeCli.createCommandLine(context, true)] as CommandLine[]
        assert !complete(offered[0], 's2-q').contains('s2-quickstart')
        buildWrites(URL_MAPPINGS_REPORT, SECURITY_QUICKSTART)
        new File(dir, 'build.gradle') << '// a plugin added\n'

        when: 'a line names a command the Forge CLI does not have, which runs the build'
        ForgeCli.runLine(context, offered, ['s2-quickstart', '--help'] as String[])

        then:
        gradleInvocations()*.get(5) == ['generateCommandManifest']
        complete(offered[0], 's2-q').contains('s2-quickstart')

        when: 'a line runs no build'
        ForgeCli.runLine(context, offered, ['--help'] as String[])

        then: 'the commands stay those of the manifest'
        complete(offered[0], 's2-q').contains('s2-quickstart')
        gradleInvocations().size() == 1
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
        buildWrites(URL_MAPPINGS_REPORT)

        when:
        int exitCode = ForgeCli.execute(context, 'url-mappings-report')

        then: 'the tasks run in that project only'
        exitCode == 0
        gradleInvocations()*.take(6) == [prefix(project) + 'generateCommandManifest', prefix(project) + 'runCommand']
        new File(project, 'build/grails/commands.json').isFile()
        commandArguments() == ['url-mappings-report']

        when: "a build file of the root changes"
        new File(dir, 'gradle.properties').text = 'a=1'
        ForgeCli.createCommandLine(context, false)

        then:
        gradleInvocations().size() == 3
    }

    void 'a wrapper above the root of the build is never run'() {
        given: 'a project with a settings file of its own but no wrapper, inside a directory that has one'
        File project = moveProjectInto('app')
        new File(project, 'settings.gradle').text = ''
        writeRawManifest(new File(project, 'build/grails/commands.json'), URL_MAPPINGS_REPORT)

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

    /** The arguments the Forge CLI runs the wrapper with before the tasks, for the project in the directory */
    private static List<String> prefix(File project) {
        ['-p', project.path, '-q', '--console=plain', "-PgrailsCommandProject=${project.path}".toString()]
    }

    private List<String> prefix() {
        prefix(dir)
    }

    /** Has the stand-in build write a manifest of the commands, naming no directories it depends on */
    private void buildWrites(Map... commands) {
        buildWritesContent([version: 1, commands: commands.toList()])
    }

    /** Has the stand-in build write a manifest of the commands, naming the directories it depends on */
    private void buildWritesWithInputs(Map inputs, Map... commands) {
        buildWritesContent([version: 1, commands: commands.toList(), inputs: inputs])
    }

    private void buildWritesContent(Object content) {
        new File(dir, 'manifest-source.json').text = JsonOutput.toJson(content)
    }

    /**
     * Leaves the manifest as an earlier run of the Forge CLI left it: written by the build, with the files
     * it depends on recorded. What that run did is forgotten.
     */
    private void writeManifest(Map... commands) {
        buildWrites(commands)
        ForgeCli.createCommandLine(context, false)
        assert manifest.isFile()
        forgetInvocations()
    }

    /** Writes the manifest as the project's own build leaves it, without the Forge CLI's record */
    private void writeManifestOfTheBuild(Map... commands) {
        writeRawManifest(manifest, commands)
    }

    private static void writeRawManifest(File file, Map... commands) {
        file.parentFile.mkdirs()
        file.text = JsonOutput.toJson([version: 1, commands: commands.toList()])
    }

    private void forgetInvocations() {
        new File(dir, 'gradle-invocations.txt').delete()
        output.reset()
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
            project=$dir; previous=''
            for argument in "$@"; do [ "$previous" = '-p' ] && project=$argument; previous=$argument; done
            { for argument in "$@"; do
                printf '%s\\n' "$argument"
                case "$argument" in -PargsFile=*)
                  printf 'argsFile:%s\\n' "$(cat "${argument#-PargsFile=}")"
                  printf 'argsFileMode:%s\\n' "$(ls -l "${argument#-PargsFile=}" | cut -c1-10)";; esac
              done; echo '--'; } >> "$dir/gradle-invocations.txt"
            case " $* " in
              *" generateCommandManifest "*)
                if [ -f "$dir/during-build.sh" ]; then sh "$dir/during-build.sh" "$project"; fi
                if [ -f "$dir/manifest-source.json" ]; then
                  mkdir -p "$project/build/grails" && cp "$dir/manifest-source.json" "$project/build/grails/commands.json" || exit 1
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
