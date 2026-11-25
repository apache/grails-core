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
package org.apache.grails.core.cli.compiler

import groovy.json.JsonSlurper
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ApplicationContextCommandRegistry
import org.apache.grails.gradle.model.cli.CommandCatalogBuilder
import org.apache.grails.gradle.model.cli.CommandDescriptor
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The name a command is listed by is the name it is run by: a descriptor records the name the
 * command has when it is made or is not written, and the registry of commands finds, by a name
 * the build lists, the command the build lists by it.
 */
class ListedCommandNameSpec extends Specification {

    private static final String CONTRACT = 'org.apache.grails.core.cli.ApplicationCommand'

    private static final String HANDLE = 'boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }'

    @TempDir
    File tempDir

    @Unroll
    def "a property that is final is described by what the command is made with: #how"() {
        when:
        compile("${annotation}\nclass ImportDataCommand implements ${CONTRACT} {\n${members}\n${HANDLE}\n}")

        then: 'what is recorded is the name the command has when it is made, or nothing'
        recordedName('example.ImportDataCommand') == recorded
        made('example.ImportDataCommand').name == name

        where:
        how                                                    | annotation                       | members                                                                                                             || recorded      | name
        'it is declared with its value'                        | ''                               | "final String name = 'import-data'"                                                                                 || 'import-data' | 'import-data'
        'the constructor sets it over the declared value'      | ''                               | "final String name = 'old-name'\nImportDataCommand() { name = 'import-data' }"                                      || 'import-data' | 'import-data'
        'the constructor sets it, compiled statically'         | '@groovy.transform.CompileStatic' | "final String name = 'old-name'\nImportDataCommand() { name = 'import-data' }"                                     || 'import-data' | 'import-data'
        'the constructor sets it through this'                 | ''                               | "final String name = 'old-name'\nImportDataCommand() { this.name = 'import-data' }"                                 || 'import-data' | 'import-data'
        'the constructor sets it, and it is declared without'  | ''                               | "final String name\nImportDataCommand() { name = 'import-data' }"                                                   || 'import-data' | 'import-data'
        'the constructor sets it when a condition holds'       | ''                               | "final String name = 'old-name'\nImportDataCommand() { if (!System.getProperty('import.old')) { name = 'import-data' } }" || null     | 'import-data'
        'the constructor sets it to a value that is worked out' | ''                              | "final String name = 'old-name'\nImportDataCommand() { name = 'import-' + 'data' }"                                 || null          | 'import-data'
        'the constructor sets it to what a method gives'       | '@groovy.transform.CompileStatic' | "final String name = 'old-name'\nImportDataCommand() { name = System.getProperty('import.name', 'import-data') }"  || null          | 'import-data'
    }

    def "a final property the class a command extends sets in its constructor is described by what it is set to"() {
        when:
        compile("abstract class OldNameCommand implements ${CONTRACT} {\nfinal String name = 'old-name'\nOldNameCommand() { name = 'import-data' }\n}\n" +
                "class ImportDataCommand extends OldNameCommand {\n${HANDLE}\n}")

        then:
        recordedName('example.ImportDataCommand') == 'import-data'
        made('example.ImportDataCommand').name == 'import-data'
    }

    @Unroll
    def "a final description is what the command is made with, or is left out: #how"() {
        when:
        compile("class ImportDataCommand implements ${CONTRACT} {\nfinal String description = 'Imports old data'\n${members}\n${HANDLE}\n}")

        then:
        descriptor('example.ImportDataCommand').description == recorded
        made('example.ImportDataCommand').description == description

        where:
        how                                   | members                                                                                             || recorded           | description
        'nothing sets it'                     | ''                                                                                                  || 'Imports old data' | 'Imports old data'
        'the constructor sets it'             | "ImportDataCommand() { description = 'Imports the data' }"                                          || 'Imports the data' | 'Imports the data'
        'the constructor sets it conditionally' | "ImportDataCommand() { if (!System.getProperty('x')) { description = 'Imports the data' } }"      || null               | 'Imports the data'
    }

    @Unroll
    def "a helper that is given the command and stops holding it on one way through is read for each way apart: #how"() {
        when:
        compile("abstract class OldNameCommand implements ${CONTRACT} {\nString name = 'old-name'\n}\n" +
                "class ImportDataCommand extends OldNameCommand {\nImportDataCommand() { rename(this, System.getProperty('import.never.set') != null) }\n" +
                "static void rename(OldNameCommand command, boolean keep) {\n${helper}\n}\n${HANDLE}\n}", Phases.ALL)

        then: 'what is recorded is the name the command has when it is made, or nothing'
        recordedName('example.ImportDataCommand') == recorded
        made('example.ImportDataCommand').name == 'import-data'

        where:
        how                                                            | helper                                                                                         || recorded
        'one branch lets go of it and returns (the review)'            | "if (keep) { command = null; return }\ncommand.name = 'import-data'"                           || null
        'one branch lets go of it and returns, the other is an else'   | "if (keep) { command = null; return } else { int kept = 1 }\ncommand.name = 'import-data'"     || null
        'one branch lets go of it, and both go on'                     | "if (keep) { command = null }\nif (command != null) { command.name = 'import-data' }"          || null
        'both branches set the name, one after letting go of another'  | "if (keep) { command = null; return }\nelse { command.name = 'import-data' }"                  || null
        'the right of a condition lets go of it'                       | "boolean let = keep && (command = null) == null\ncommand.name = 'import-data'"                 || null
        'one value of a choice lets go of it'                          | "Object chosen = keep ? (command = null) : 1\ncommand.name = 'import-data'"                    || null
        'a loop lets go of it'                                         | "while (keep) { command = null; break }\ncommand.name = 'import-data'"                         || null
        'a closure that may run lets go of it'                         | "Closure let = { command = null }\nif (keep) { let() }\ncommand.name = 'import-data'"          || null
        'no branch lets go of it'                                      | "if (keep) { int kept = 1 }\ncommand.name = 'import-data'"                                     || 'import-data'
        'a branch that returns leaves the name, and none lets go'      | "command.name = 'import-data'\nif (keep) { return }\nint kept = 1"                             || 'import-data'
    }

    @Unroll
    def "each name the build lists runs the command it is listed for: #how"() {
        given:
        compile(sources, Phases.ALL)
        descriptors.each { String className, String name ->
            File descriptor = descriptorFile("example.${className}")
            descriptor.parentFile.mkdirs()
            descriptor.setText(CommandDescriptor.toJson("example.${className}", name, null), 'US-ASCII')
        }

        when: 'the build lists the commands of the compiled classes'
        CommandCatalogBuilder catalog = new CommandCatalogBuilder()
        catalog.add(CommandCatalogBuilder.ORIGIN_APPLICATION, targetDirectory)
        List<String> listed = ((List<Map>) catalog.build().commands).collect { Map command -> (String) command.name }

        and: 'each is looked for by its name, as the runner of runCommand looks for it, and so are the names the commands have'
        Map<String, String> found = withCompiledCommands {
            ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()
            (listed + unlisted).collectEntries { String name -> [(name): registry.findCommand(name)?.getClass()?.simpleName] }
        }

        then:
        listed == runs.keySet().toList()
        found == runs + other

        where:
        how << [
                'two commands without a descriptor, and the first gives itself the name the second is listed by (the review)',
                'a command without a descriptor gives itself the name a command with a descriptor is listed by',
                'a descriptor gives a name the command does not have when it is made',
                'a descriptor gives a name the command does not have, which another command is listed by',
                'two commands give themselves one name, and are listed by the names of their classes',
                'two commands are listed by one name, which the build then does not list',
        ]
        sources << [
                "class AlphaCommand implements ${CONTRACT} { String getName() { 'be' + 'ta' }; ${HANDLE} }\n" +
                        "class BetaCommand implements ${CONTRACT} { String getName() { 'be' + 'ta' }; ${HANDLE} }",
                "class AlphaCommand implements ${CONTRACT} { String getName() { 'be' + 'ta' }; ${HANDLE} }\n" +
                        "class BetaCommand implements ${CONTRACT} { ${HANDLE} }",
                "class AlphaCommand implements ${CONTRACT} { String getName() { 'new-' + 'name' }; ${HANDLE} }",
                "class AlphaCommand implements ${CONTRACT} { String getName() { 'new-' + 'name' }; ${HANDLE} }\n" +
                        "class NewNameCommand implements ${CONTRACT} { ${HANDLE} }",
                "class AlphaCommand implements ${CONTRACT} { String getName() { 'sa' + 'me' }; ${HANDLE} }\n" +
                        "class BetaCommand implements ${CONTRACT} { String getName() { 'sa' + 'me' }; ${HANDLE} }",
                "class AlphaCommand implements ${CONTRACT} { String name = 'twin'; ${HANDLE} }\n" +
                        "class BetaCommand implements ${CONTRACT} { String name = 'twin'; ${HANDLE} }\n" +
                        "class GammaCommand implements ${CONTRACT} { String getName() { 'tw' + 'in' }; ${HANDLE} }",
        ]
        descriptors << [
                [:],
                [:],
                [AlphaCommand: 'old-name'],
                [AlphaCommand: 'old-name'],
                [:],
                [:],
        ]
        runs << [
                [alpha: 'AlphaCommand', beta: 'BetaCommand'],
                [alpha: 'AlphaCommand', beta: 'BetaCommand'],
                ['old-name': 'AlphaCommand'],
                ['new-name': 'NewNameCommand', 'old-name': 'AlphaCommand'],
                [alpha: 'AlphaCommand', beta: 'BetaCommand'],
                [gamma: 'GammaCommand'],
        ]
        unlisted << [
                [],
                [],
                ['new-name', 'alpha'],
                ['alpha'],
                ['same'],
                ['twin', 'alpha', 'beta'],
        ]
        other << [
                [:],
                [:],
                ['new-name': 'AlphaCommand', alpha: null],
                [alpha: null],
                [same: 'AlphaCommand'],
                [twin: 'AlphaCommand', alpha: null, beta: null],
        ]
    }

    private File getTargetDirectory() {
        new File(tempDir, 'build/classes/groovy/cli')
    }

    private File descriptorFile(String className) {
        new File(targetDirectory, CommandDescriptor.location(className))
    }

    // the name the descriptor of the class records, or null when it has none
    private String recordedName(String className) {
        descriptorFile(className).isFile() ? descriptor(className).name : null
    }

    private Map descriptor(String className) {
        // whatever the compiler writes, the reader of a descriptor takes
        CommandDescriptor.read(className, descriptorFile(className).bytes)
        (Map) new JsonSlurper().parse(descriptorFile(className), 'US-ASCII')
    }

    private void compile(String source, int phase = Phases.ALL) {
        File sourceFile = new File(tempDir, 'src/cli/groovy/example/Commands.groovy')
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package example\n${source}"
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: targetDirectory))
        unit.addSource(sourceFile)
        unit.compile(phase)
    }

    // the name and the description a compiled command has when it is made, as the registry of commands makes it
    private Map<String, String> made(String className) {
        new URLClassLoader([targetDirectory.toURI().toURL()] as URL[], ApplicationCommand.classLoader).withCloseable { URLClassLoader loader ->
            ApplicationCommand command = (ApplicationCommand) loader.loadClass(className).getDeclaredConstructor().newInstance()
            [name: command.getName(), description: command.getDescription()]
        }
    }

    // runs the action with the compiled commands on the class path the registry of commands reads
    private <T> T withCompiledCommands(Closure<T> action) {
        Thread thread = Thread.currentThread()
        ClassLoader original = thread.contextClassLoader
        new URLClassLoader([targetDirectory.toURI().toURL()] as URL[], ApplicationCommand.classLoader).withCloseable { URLClassLoader loader ->
            thread.contextClassLoader = loader
            try {
                action.call()
            }
            finally {
                thread.contextClassLoader = original
            }
        }
    }
}
