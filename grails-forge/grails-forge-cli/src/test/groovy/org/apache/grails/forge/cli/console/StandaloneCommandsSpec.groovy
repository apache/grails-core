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
import groovy.json.JsonSlurper
import org.apache.grails.gradle.model.cli.CommandCatalogBuilder
import org.apache.grails.gradle.model.cli.CommandDescriptor
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf

/**
 * A command the build of a project lists with the target {@code standalone}, one that is run without
 * the application, as a command of the CLI: listed and run as every command of the application and
 * its plugins is, and said to be what it is. The file of the build is written here by hand, or by
 * the class of the build that writes it.
 */
class StandaloneCommandsSpec extends CommandSpec implements CommandFixture {

    static final Map TAIL = [name: 'query-tail', aliases: [], description: 'Shows the statements', origin: 'org.apache.grails.data:grails-datamapping-core-cli:9.0.0',
                             target: 'standalone', registration: 'descriptor']

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

    void "a command that runs without the application is listed with the others, and its help says how it runs"() {
        given:
        catalog(ApplicationCommandsSpec.doc([TAIL, ApplicationCommandsSpec.HELLO]))

        when:
        int exit = ForgeCli.execute(beanContext, 'help')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines()*.replaceAll(/\s+/, ' ').contains(' query-tail Shows the statements (org.apache.grails.data:grails-datamapping-core-cli:9.0.0)')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'query-tail')
        String help = out.toString().replaceAll(/\s+/, ' ')

        then:
        exit == 0
        help.contains('A command of org.apache.grails.data:grails-datamapping-core-cli:9.0.0, which runs without the application: it is started by the Gradle task runCommand of the project.')
        !help.contains('which runs in the application')

        when: 'the other command is said to run in the application, as before'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', 'hello-world')

        then:
        exit == 0
        out.toString().replaceAll(/\s+/, ' ').contains('A command of org.example:hello-cli:1.2.3, which runs in the application: it is started by the Gradle task runCommand of the project.')
    }

    void "the document for a tool has the target of each command the build lists"() {
        given:
        catalog(ApplicationCommandsSpec.doc([TAIL, ApplicationCommandsSpec.HELLO]))

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString().readLines().last())
        List<Map> commands = ((List<Map>) described.commands).findAll { it.origin != 'built-in' }

        then:
        exit == 0
        err.toString().isEmpty()
        described.complete == true
        described.diagnostics == []
        commands == [
                [name: 'hello-world', aliases: [], description: 'Says hello', project: true, origin: 'org.example:hello-cli:1.2.3', target: 'application', registration: 'descriptor', commands: []],
                [name: 'query-tail', aliases: [], description: 'Shows the statements', project: true, origin: 'org.apache.grails.data:grails-datamapping-core-cli:9.0.0',
                 target: 'standalone', registration: 'descriptor', commands: []]]
    }

    void "what the build writes for a command that runs without the application is what is read here"() {
        given: 'a jar with one command of each kind, listed by the class of the build that writes the file'
        File jar = new File(dir, 'element')
        new File(jar, 'META-INF').mkdirs()
        new File(jar, 'META-INF/grails-cli.factories').text = 'org.apache.grails.core.cli.ApplicationCommand=demo.TailCommand,demo.ReportCommand\n'
        [['demo.TailCommand', 'tail', 'standalone'], ['demo.ReportCommand', 'report', 'application']].each { List<String> each ->
            File descriptor = new File(jar, CommandDescriptor.location(each[0]))
            descriptor.parentFile.mkdirs()
            descriptor.text = CommandDescriptor.describe(each[0], each[1], 'Does it', each[2])
        }
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        builder.add('org.example:element:1.0', jar)
        catalog(builder.toJson())
        jar.deleteDir()

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString().readLines().last())

        then:
        exit == 0
        described.complete == true
        described.diagnostics == []
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }.collectEntries { [(it.name): it.target] } == [report: 'application', tail: 'standalone']
    }

    @IgnoreIf({ os.windows })
    void "a command that runs without the application is run by the Gradle task runCommand, as every command of the build is"() {
        given:
        plainProject()
        catalog(ApplicationCommandsSpec.doc([TAIL]))

        when:
        int exit = ForgeCli.execute(beanContext, 'query-tail', '--once', '--file=out/capture.log')

        then:
        exit == 0
        line('requested=') == '<runCommand><--args-file=>'
        line('system=') == '<grails.env=development>'
        line('vector=') == ['query-tail', '--once', '--file=out/capture.log'].collect { '<' + it.getBytes('UTF-8').encodeHex() + '>' }.join('')
        err.toString().readLines()[0] == '| Running the command query-tail of org.apache.grails.data:grails-datamapping-core-cli:9.0.0, with the Gradle task runCommand'
    }

    private String line(String start) {
        List<String> lines = out.toString().readLines().findAll { String line -> line.startsWith(start) }
        assert lines.size() == 1
        // A task of the project of these specifications is named as that of the root of its build, :classes for
        // classes, because the project has the settings of a build: MultiProjectBuildSpec holds that, and here
        // the task is read without the mark
        String line = lines[0].substring(start.length())
        return start == 'requested=' ? line.replaceAll(/<:([A-Za-z]+)>/, '<$1>') : line
    }

    private void catalog(String content) {
        File file = new File(dir, '.grails/cache/command-catalog.json')
        file.parentFile.mkdirs()
        file.bytes = content.getBytes(StandardCharsets.UTF_8)
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
        new File(dir, 'build.gradle').text = ApplicationCommandsSpec.BUILD
        new File(dir, 'grails-app').mkdirs()
    }
}
