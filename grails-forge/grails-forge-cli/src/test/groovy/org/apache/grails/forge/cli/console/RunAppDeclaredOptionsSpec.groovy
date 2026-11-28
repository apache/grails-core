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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.gradle.model.cli.ToolCatalogBuilder
import org.apache.grails.gradle.model.cli.CommandOptions
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import picocli.AutoComplete
import picocli.CommandLine
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll

/**
 * The options the application and its plugins declare for {@code run-app}, which the build of the
 * project lists in {@code .grails/cache/tool-catalog.json}: each is an option of the command that
 * sets a property of the application, and nothing of a plugin is run for it.
 *
 * <p>Most projects here have the Gradle wrapper of {@link LifecycleCommandsSpec}, a program that
 * writes down the arguments it was given and, where it is asked to, answers on a port. One feature
 * runs the real wrapper around a build that needs nothing of Grails, with each of the two ways the
 * command line runs Gradle.</p>
 */
@IgnoreIf({ os.windows })
class RunAppDeclaredOptionsSpec extends CommandSpec implements CommandFixture {

    static final String DATA = 'org.apache.grails.data:grails-datamapping-core-cli:9.0.0'
    static final Map TAIL = [command: 'run-app', name: 'query-tail', description: 'Capture every statement GORM runs, for the query-tail command.', type: 'boolean',
                             property: 'grails.gorm.queryTail.enabled', value: 'true', origin: DATA]
    static final Map TENANT = [command: 'run-app', name: 'tenant', description: 'The tenant the application runs as.', type: 'string', property: 'demo.tenant',
                               origin: 'org.example:tenants:1.0']
    static final Map TRACE = [command: 'run-app', name: 'trace', description: 'Log every request.', type: 'boolean', property: 'logging.level.web', value: 'TRACE',
                              origin: 'application']
    static final String START = '<bootRun><--quiet><-Dgrails.env=development>'
    static final String LIST = 'grails-forge-cli gradle grailsToolCatalog'

    @Shared
    IsolatedGradle daemon = new IsolatedGradle()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void cleanupSpec() {
        daemon.close()
    }

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        GradleBuild.setRunner(null)
        ConsoleProject.setOwnedByUser(null)
        ConsoleCommands.setInteractive(false)
        RunAppCommand.stopWhatTheShellStarted()
        File server = new File(dir, 'server.pid')
        if (server.isFile() && server.text.trim() ==~ /[0-9]+/) {
            ProcessHandle.of(server.text.trim() as long).ifPresent { ProcessHandle handle -> handle.destroyForcibly() }
        }
    }

    @Unroll
    void "an option a plugin declares sets its property for the application, as one argument of the build: #line"() {
        given:
        scriptedProject('record')
        catalog([TAIL, TENANT, TRACE])

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then:
        exit == 0
        new File(dir, 'invoked.txt').text == invoked + '\n'
        errors() == []

        where:
        line                                                                             | invoked
        ['run-app', '--query-tail']                                                      | START + '<-Dgrails.grails.gorm.queryTail.enabled=true>'
        ['run', '--query-tail']                                                          | START + '<-Dgrails.grails.gorm.queryTail.enabled=true>'
        ['run-app', '--trace', '--query-tail', '--foreground']                           | START + '<-Dgrails.logging.level.web=TRACE><-Dgrails.grails.gorm.queryTail.enabled=true>'
        ['run-app', '--tenant', 'acme']                                                  | START + '<-Dgrails.demo.tenant=acme>'
        ['run-app', '--tenant=two words "$HOME" *', '--port', '9090']                    | START + '<-Dgrails.server.port=9090><-Dgrails.demo.tenant=two words "$HOME" *>'
        // a value is the one argument it is, whatever is in it
        ['run-app', '--tenant=a -Dother=1 --offline; rm -rf']                            | START + '<-Dgrails.demo.tenant=a -Dother=1 --offline; rm -rf>'
        ['run-app', '-Dgrails.env=test', '--query-tail', '-Pone=1']                      | '<bootRun><--quiet><-Dgrails.env=test><-Dgrails.grails.gorm.queryTail.enabled=true><-Pone=1>'
        // what the command line names itself with -D is left as it is given there, and the property is there once
        ['run-app', '--tenant', 'other', '-Dgrails.demo.tenant=mine', '--trace']         | START + '<-Dgrails.demo.tenant=mine><-Dgrails.logging.level.web=TRACE>'
        // what follows -- is Gradle's, also where it looks like a declared option
        ['run-app', '--', '--query-tail']                                                | START + '<--query-tail>'
        ['run-app']                                                                      | START
    }

    void "run-app --detach gives the build the property of a declared option once, and the application is stopped as any other"() {
        given:
        scriptedProject('serve')
        catalog([TAIL, TENANT])
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--query-tail', '--tenant', 'acme')
        String invoked = new File(dir, 'invoked.txt').text

        then:
        exit == 0
        errors() == []
        invoked == "${START}<-Dgrails.server.port=${port}><-Dgrails.grails.gorm.queryTail.enabled=true><-Dgrails.demo.tenant=acme>\n"
        invoked.count('queryTail') == 1
        out.toString().contains("| Application answers on port ${port}")

        when:
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        stopped == 0
        errors() == []
    }

    @Unroll
    void "the build is given the property as a system property of its own, whichever way Gradle is run: #runner"() {
        given:
        plainProject()
        catalog([TAIL, TENANT])
        GradleBuild.setRunner(runner)

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--foreground', '--query-tail', '--tenant=two words')

        then:
        exit == 0
        errors() == []
        lineOf('system=') == '<grails.demo.tenant=two words><grails.env=development><grails.grails.gorm.queryTail.enabled=true>'
        lineOf('requested=') == '<bootRun>'

        where:
        runner << [GradleBuild.PROCESS, GradleBuild.TOOLING_API]
    }

    void "the help of run-app lists the declared options, with who declares each and the property it sets"() {
        given:
        scriptedProject('record')
        catalog([TAIL, TENANT, TRACE])

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--help')
        String direct = out.toString()
        // the help breaks a line where it is full, also inside coordinates: what it says is compared without its white space
        String help = direct.replaceAll(/\s+/, '')
        out.reset()
        ForgeCli.execute(beanContext, 'help', 'run-app')

        then:
        exit == 0
        out.toString() == direct
        help.contains("--query-tailCapture every statement GORM runs, for the query-tail command.(${DATA}: sets grails.gorm.queryTail.enabled=true)".replace(' ', ''))
        help.contains('--tenant=VALUEThe tenant the application runs as.(org.example:tenants:1.0: sets demo.tenant)'.replace(' ', ''))
        help.contains('--traceLog every request.(the application: sets logging.level.web=TRACE)'.replace(' ', ''))
        direct.readLines().any { String line -> line.trim() ==~ /--trace +Log every request\./ }
        direct.readLines().any { String line -> line.trim() == '(the application: sets logging.level.web=TRACE)' }
        direct.readLines()[0] == 'Usage: grails-forge-cli run-app [-hvVx] [--non-interactive] [--plain-output]'
        direct.readLines()[1].trim() == '[--port=PORT] [--host=HOST] [--https] [--debug-jvm] [--detach | --foreground]'
        direct.readLines()[2].trim() == '[--query-tail] [--tenant=VALUE] [--trace]'
        direct.readLines()[3].trim() == '[--ready-timeout=SECONDS] [-DNAME=VALUE]... [-PNAME=VALUE]... [-- GRADLE-ARGUMENT...]'
        !direct.contains('once the build has listed them:')
        !direct.contains('may have changed')

        when: 'for a tool, and for the completion of the command line'
        out.reset()
        ForgeCli.execute(beanContext, 'help', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())
        Map runApp = document.commands.find { it.name == 'run-app' }
        List<CharSequence> completions = []
        AutoComplete.complete(ForgeCli.createCommandLine(beanContext, true, true).getCommandSpec(), ['run-app', ''] as String[], 1, 0, 0, completions)

        then:
        runApp.options.find { it.names == ['--query-tail'] } == [names: ['--query-tail'], arity: '0', type: 'boolean', label: null, required: false,
                                                                 description: 'Capture every statement GORM runs, for the query-tail command.', repeatable: false,
                                                                 split: null, choices: null, default: null, origin: DATA,
                                                                 property: 'grails.gorm.queryTail.enabled', value: 'true']
        runApp.options.find { it.names == ['--tenant'] }.subMap(['arity', 'label', 'origin', 'property']) ==
                [arity: '1', label: 'VALUE', origin: 'org.example:tenants:1.0', property: 'demo.tenant']
        !runApp.options.find { it.names == ['--tenant'] }.containsKey('value')
        runApp.options.find { it.names == ['--trace'] }.origin == 'application'
        !runApp.options.find { it.names == ['--port'] }.containsKey('origin')
        document.diagnostics == []
        completions*.toString().containsAll(['--query-tail', '--tenant', '--trace'])
    }

    void "an option of run-app itself stays its own: one that is declared by that name is not added, and that is said"() {
        given:
        scriptedProject('record')
        catalog([TAIL, [command: 'run-app', name: 'port', description: 'Another port.', type: 'string', property: 'demo.port', origin: 'org.hostile:ports:1.0'],
                 [command: 'run-app', name: 'debug-jvm', description: 'Another debugger.', type: 'boolean', property: 'demo.debug', value: 'true', origin: 'org.hostile:ports:1.0'],
                 [command: 'run-app', name: 'verbose', description: 'More words.', type: 'boolean', property: 'demo.verbose', value: 'true', origin: 'application'],
                 [command: 'run-app', name: 'help', description: 'Other help.', type: 'boolean', property: 'demo.help', value: 'true', origin: 'org.hostile:ports:1.0']])

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--port', '9090', '--debug-jvm', '--verbose', '--query-tail')

        then: 'the options are those of the command'
        exit == 0
        new File(dir, 'invoked.txt').text == '<bootRun><--debug-jvm><-Dgrails.env=development><-Dgrails.server.port=9090><-Dgrails.grails.gorm.queryTail.enabled=true>\n'

        when:
        out.reset()
        ForgeCli.execute(beanContext, 'run-app', '--help')
        String help = out.toString().readLines()*.trim().join(' ').replaceAll(/ +/, ' ')

        then:
        help.count('--port=PORT The port the application answers on.') == 1
        !help.contains('Another port.') && !help.contains('Other help.')
        help.contains('The option --port that org.hostile:ports:1.0 declares for run-app is not an option here: run-app has an option of that name itself')
        help.contains('The option --debug-jvm that org.hostile:ports:1.0 declares for run-app is not an option here: run-app has an option of that name itself')
        help.contains('The option --verbose that the application declares for run-app is not an option here: run-app has an option of that name itself')
        help.contains('The option --help that org.hostile:ports:1.0 declares for run-app is not an option here: run-app has an option of that name itself')

        when: 'where the commands of the application are spoken of'
        out.reset()
        ForgeCli.execute(beanContext, 'help', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        document.diagnostics.findAll { String line -> line.startsWith('The option --') }.size() == 4
        document.complete == true
        document.commands.find { it.name == 'run-app' }.options.count { it.names.contains('--port') } == 1
    }

    void "what the file lists that the task does not write is not an option: nothing of it reaches the build"() {
        given:
        scriptedProject('record')
        catalog([TAIL + [property: 'java.security.manager'], TAIL + [name: 'agent', property: 'demo.agent -javaagent:/tmp/x.jar'],
                 TAIL + [name: 'profile', property: 'spring.profiles.active', value: 'production'], TAIL + [name: 'value', value: 'true --offline'],
                 TAIL + [name: 'no-value', value: null], TAIL + [name: 'typed', type: 'integer'], TAIL + [name: 'given', type: 'string'],
                 TAIL + [name: 'markup', description: 'This is @|bold nothing|@'], TAIL + [name: 'variable', description: 'Home is ${sys:user.home}'],
                 TAIL + [name: 'Upper'], TAIL + [name: 'other-command', command: 'stop-app'], TAIL + [name: 'origin', origin: 'built-in'], 'tail',
                 TENANT, TENANT + [property: 'demo.second']])

        when:
        CommandLine.Model.CommandSpec spec = ForgeCli.createCommandLine(beanContext, true, true).getSubcommands().get('run-app').getCommandSpec()

        then:
        spec.options().collect { it.longestName() }.findAll { it.startsWith('--') }.sort() ==
                ['--debug-jvm', '--detach', '--foreground', '--help', '--host', '--https', '--non-interactive', '--plain-output', '--port', '--ready-timeout',
                 '--stacktrace', '--tenant', '--verbose', '--version']
        spec.findOption('--tenant').description() == ['The tenant the application runs as.', '(org.example:tenants:1.0: sets demo.tenant)'] as String[]

        when:
        ForgeCli.execute(beanContext, 'run-app', '--help')
        String help = out.toString().readLines()*.trim().join(' ').replaceAll(/ +/, ' ')

        then:
        help.contains('14 of the options .grails/cache/tool-catalog.json lists for commands are not used: they are not as the Gradle task grailsToolCatalog writes them')
        !help.contains('java.security') && !help.contains('javaagent') && !help.contains('spring.profiles')

        when:
        out.reset()
        int exit = ForgeCli.execute(beanContext, 'run-app', '--profile')

        then:
        exit == 2
        !new File(dir, 'invoked.txt').exists()
    }

    void "a percent sign of where an option comes from is shown as it is"() {
        given:
        scriptedProject('record')
        catalog([TAIL + [origin: 'file:100%25-data%n.jar']])

        when:
        ForgeCli.execute(beanContext, 'run-app', '--help')
        String help = out.toString().replaceAll(/\s+/, '')

        then:
        help.contains('(file:100%25-data%n.jar:setsgrails.gorm.queryTail.enabled=true)')
    }

    @Unroll
    void "an option nothing declares is refused, and nothing is run: #where"() {
        given:
        if (project) {
            scriptedProject('record')
        }
        if (listed != null) {
            catalog(listed)
        }
        if (stale) {
            new File(dir, 'build.gradle').text = ''
            new File(dir, 'build.gradle').setLastModified(System.currentTimeMillis() + 60_000)
        }

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--query-tial')
        List<String> lines = out.toString().readLines()

        then:
        exit == 2
        lines[0] == "run-app has no option --query-tial. ${said}"
        lines.any { String line -> line.startsWith('Usage:') }
        !new File(dir, 'invoked.txt').exists()

        where:
        where                                   | project | listed          | stale | said
        'outside a project'                     | false   | null            | false | 'A plugin of an application can declare options of run-app, which are options in the project of that application once its build has listed them'
        'in a project the build has not listed' | true    | null            | false | "A plugin of the application can declare options of run-app, which are options here once the build of the project has listed them: ${LIST}"
        'where no option is declared'           | true    | []              | false | 'The application and its plugins declare no options of run-app, as the build of the project lists them'
        'where other options are declared'      | true    | [TAIL, TENANT]  | false | 'The application and its plugins declare these options of run-app: --query-tail, --tenant'
        'where the list may be out of date'     | true    | [TAIL]          | true  | "The options the application and its plugins declare for run-app may have changed since the build listed them. To list them again: ${LIST}"
    }

    void "the help of run-app says how the options of plugins are listed where the build has not listed them, and that they may have changed where it may be out of date"() {
        given:
        scriptedProject('record')

        when:
        ForgeCli.execute(beanContext, 'run-app', '--help')
        String help = out.toString().readLines()*.trim().join(' ').replaceAll(/ +/, ' ')

        then:
        help.contains('The application and its plugins can declare more options, each of which sets a property of the application:')
        help.contains("The options the plugins of the project declare for run-app are options here once the build has listed them: ${LIST}")

        when:
        out.reset()
        catalog([TAIL])
        new File(dir, 'build.gradle').text = ''
        new File(dir, 'build.gradle').setLastModified(System.currentTimeMillis() + 60_000)
        ForgeCli.execute(beanContext, 'run-app', '--help')
        help = out.toString().readLines()*.trim().join(' ').replaceAll(/ +/, ' ')

        then: 'what was listed is an option still'
        help.contains('--query-tail Capture every statement GORM runs')
        help.contains("The options the plugins of the project declare for run-app may have changed: build.gradle is newer than .grails/cache/tool-catalog.json. To list them again: ${LIST}")
    }

    void "the rules of the command line for a declared option are those of the task that lists it, and what the task writes is read"() {
        expect:
        DeclaredOptions.MEMBER == 'commandOptions'
        DeclaredOptions.MAX_OPTIONS == CommandOptions.MAX_OPTIONS
        DeclaredOptions.MIN_NAME == CommandOptions.MIN_NAME_LENGTH
        DeclaredOptions.MAX_NAME == CommandOptions.MAX_NAME_LENGTH
        DeclaredOptions.MAX_DESCRIPTION == CommandOptions.MAX_DESCRIPTION_LENGTH
        DeclaredOptions.MAX_PROPERTY == CommandOptions.MAX_PROPERTY_LENGTH
        DeclaredOptions.MAX_VALUE == CommandOptions.MAX_VALUE_LENGTH
        DeclaredOptions.DENIED_PROPERTIES == CommandOptions.DENIED_PROPERTIES
        DeclaredOptions.DENIED_PROPERTY_PREFIXES == CommandOptions.DENIED_PROPERTY_PREFIXES
        DeclaredOptions.BOOLEAN == CommandOptions.TYPE_BOOLEAN
        DeclaredOptions.STRING == CommandOptions.TYPE_STRING
        RunAppCommand.NAME == CommandOptions.COMMAND_RUN_APP
        CommandOptions.COMMANDS == [RunAppCommand.NAME]

        and:
        ['query-tail', 'q', 'qt', 'a' * 32, 'a' * 33, 'Query', 'query--tail', '-query', 'query_tail', 'q=1', '', '9a', 'a9-b'].every { String name ->
            DeclaredOptions.isName(name) == CommandOptions.isOptionName(name)
        }
        ['grails.gorm.queryTail.enabled', 'debug', 'demo.a b', 'java.home', 'JAVA.home', 'spring.config.additional-location', 'spring.config.additional_location',
         'spring.profiles.active', 'server.port', 'server.PORT', 'server.http2.enabled', 'grails.env', 'grails.environment', 'logging.config', 'logging.level.root',
         'demo.' + 'a' * 123, 'demo.' + 'a' * 124, 'demo..a', '.demo.a', 'demo.a=b', 'user.dir', 'com.sun.management.jmxremote', 'grails.config.locations'].every { String property ->
            DeclaredOptions.isProperty(property) == CommandOptions.isProperty(property)
        }
        ['true', 'TRACE', '', 'a b', 'a' * 64, 'a' * 65, '-x', '1.5', 'a=b', 'a/b', 'a_b-c.d'].every { String value ->
            DeclaredOptions.isValue(value) == CommandOptions.isValue(value)
        }
        ['One line.', '', '  ', 'two\nlines', 'esc\u001b[0m', 'a' * 200, 'a' * 201, 'costs $5', '100%', 'a@b', 'gr\u00fc\u00dft \u65e5\u672c'].every { String description ->
            DeclaredOptions.isDescription(description) == CommandOptions.isDescription(description)
        }

        when: 'the task lists as many options as it lists, with the longest texts, and the command line reads the file'
        scriptedProject('record')
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        (1..3).each { int jar ->
            File element = new File(dir, "element${jar}")
            new File(element, 'META-INF').mkdirs()
            new File(element, CommandOptions.LOCATION).text = JsonOutput.toJson([format: 1, options: (1..16).collect { int number ->
                String name = "o${jar}-${number}-".padRight(32, 'a')
                [command: 'run-app', name: name, description: '\u65e5' * 200, type: number % 2 ? 'boolean' : 'string',
                 property: "demo.${name}.".padRight(128, 'p')] + (number % 2 ? [value: 'v' * 64] : [:])
            }])
            builder.add("org.example:jar${jar}:1.0".padRight(120, 'x'), element)
        }
        File file = new File(dir, '.grails/cache/tool-catalog.json')
        file.parentFile.mkdirs()
        file.text = builder.toJson()
        ToolCatalogFile read = ToolCatalogFile.read(dir.toPath())

        then:
        read.state == ToolCatalogFile.State.CURRENT
        read.declaredOptions.options.size() == CommandOptions.MAX_OPTIONS
        read.declaredOptions.notUsed == 0
        read.declaredOptions.options*.name == ((Map) builder.build()).commandOptions*.name
    }

    private void catalog(List options) {
        File file = new File(dir, '.grails/cache/tool-catalog.json')
        file.parentFile.mkdirs()
        file.text = JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: []] + (options == null ? [:] : [commandOptions: options]))
    }

    private void scriptedProject(String mode) {
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'Server.java').text = LifecycleCommandsSpec.SERVER
        new File(dir, 'mode.txt').text = mode + '\n'
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = "#!/bin/sh\n\"${System.getProperty('java.home')}/bin/java\" \"${dir.canonicalPath}/Server.java\" \"\$@\"\n"
        wrapper.setExecutable(true, false)
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
        new File(dir, 'settings.gradle').text = daemon.settingsOf(true)
        new File(dir, 'gradle.properties').text = daemon.propertiesOf(true)
        new File(dir, 'build.gradle').text = ConsoleCommandsSpec.BUILD + "tasks.register('bootRun', Recorded)\n"
        new File(dir, 'grails-app').mkdirs()
    }

    private static int freePort() {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress()).withCloseable { ServerSocket socket -> socket.localPort }
    }

    private String lineOf(String start) {
        List<String> lines = out.toString().readLines().findAll { String line -> line.startsWith(start) }
        assert lines.size() == 1
        String line = lines[0].substring(start.length())
        return start == 'requested=' ? line.replaceAll(/<:([A-Za-z]+)>/, '<$1>') : line
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}
