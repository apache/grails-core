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
import org.apache.grails.core.cli.ConfigReportCommand
import org.apache.grails.gradle.model.cli.CommandDescriptor
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * Compiling a command writes its descriptor beside its registration, with what the source says as
 * a constant and nothing that would take running the command to know.
 */
class CommandDescriptorWriterSpec extends Specification {

    private static final String CONTRACT = 'org.apache.grails.core.cli.ApplicationCommand'

    @TempDir
    File tempDir

    def "a command that declares nothing is described by the name its class gives"() {
        when:
        compile('BackupDatabaseCommand', "class BackupDatabaseCommand implements ${CONTRACT} { ${HANDLE} }")

        then: 'the bytes are these, so that the same source gives the same jar'
        descriptorFile('example.BackupDatabaseCommand').getText('US-ASCII') ==
                '{"format":1,"class":"example.BackupDatabaseCommand","name":"backup-database",' +
                '"description":"backup-database","target":"application"}\n'

        and: 'it is the name the command has when it runs'
        compiledName('example.BackupDatabaseCommand') == 'backup-database'

        and: 'the registration is beside it'
        registered() == ['example.BackupDatabaseCommand']
    }

    @Unroll
    def "the description is read from #declaration"() {
        when:
        compile('GreetCommand', "class GreetCommand implements ${contract} { ${members} }")

        then:
        descriptor('example.GreetCommand') == [format: 1, class: 'example.GreetCommand', name: 'greet',
                                               description: 'Greets someone', target: 'application']

        where:
        declaration                          | contract                                              | members
        'a property'                         | CONTRACT                                              | "String description = 'Greets someone'; ${HANDLE}"
        'a final property'                   | CONTRACT                                              | "final String description = 'Greets someone'; ${HANDLE}"
        'a method'                           | CONTRACT                                              | "String getDescription() { 'Greets someone' }; ${HANDLE}"
        'a method with a return'             | CONTRACT                                              | "String getDescription() { return 'Greets someone' }; ${HANDLE}"
        'a method, beside a property'        | CONTRACT                                              | "String description = 'Not this'; String getDescription() { 'Greets someone' }; ${HANDLE}"
        'a property of a Grails command'     | 'org.apache.grails.core.cli.GrailsApplicationCommand' | "String description = 'Greets someone'; boolean handle() { true }"
    }

    def "a Grails command that declares nothing is described by the name its class gives"() {
        when:
        compile('GreetCommand', 'class GreetCommand implements org.apache.grails.core.cli.GrailsApplicationCommand { boolean handle() { true } }')

        then:
        descriptor('example.GreetCommand') == [format: 1, class: 'example.GreetCommand', name: 'greet',
                                               description: 'greet', target: 'application']
    }

    @Unroll
    def "the name is read from #declaration, and is the description when none is declared"() {
        when:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} {\n${members}\n${HANDLE}\n}")

        then:
        descriptor('example.GreetCommand') == [format: 1, class: 'example.GreetCommand', name: 'say-hello',
                                               description: 'say-hello', target: 'application']

        where:
        declaration  | members
        'a property' | "String name = 'say-hello'"
        'a method'   | "String getName() { 'say-hello' }"
    }

    @Unroll
    def "a description that is #kind is left out, and the command is described by its name"() {
        when:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} {\n${members}\n${HANDLE}\n}")

        then:
        descriptor('example.GreetCommand') == [format: 1, class: 'example.GreetCommand', name: 'greet', target: 'application']

        where:
        kind                         | members
        'worked out when it is read' | 'String getDescription() { "Greets ${System.getProperty(\'user.name\')}" }'
        'more than a constant'       | "String getDescription() { String who = 'someone'; 'Greets ' + who }"
        'a property without a value' | 'String description'
        'a property that is worked out' | "String description = 'Greets ' + System.getProperty('user.name')"
    }

    @Unroll
    def "a command whose name is #kind gets no descriptor and keeps its registration"() {
        given: 'the descriptor of an earlier compilation'
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} { ${HANDLE} }")
        assert descriptorFile('example.GreetCommand').isFile()

        when:
        compile('GreetCommand', source)

        then:
        !descriptorFile('example.GreetCommand').exists()
        registered() == ['example.GreetCommand']

        where:
        kind                               | source
        'worked out when it is read'       | "class GreetCommand implements ${CONTRACT} { String getName() { System.getProperty('greet.name') }; ${HANDLE} }"
        'given by another trait'           | "trait Renamed { String getName() { 'renamed' } }\nclass GreetCommand implements ${CONTRACT}, Renamed { ${HANDLE} }"
        'given by a class compiled before' | "class GreetCommand extends ${NamedElsewhere.name} { ${HANDLE} }"
    }

    def "what a class extends is read too, and a class that cannot be a command gets nothing"() {
        when:
        compile('GreetCommand', """
            abstract class Described implements ${CONTRACT} { String description = 'From the base' }
            class GreetCommand extends Described { ${HANDLE} }
            class LoudGreetCommand extends GreetCommand { String description = 'Its own' }
        """)

        then:
        descriptor('example.GreetCommand').description == 'From the base'
        descriptor('example.LoudGreetCommand') == [format: 1, class: 'example.LoudGreetCommand', name: 'loud-greet',
                                                   description: 'Its own', target: 'application']
        !descriptorFile('example.Described').exists()
        registered() == ['example.GreetCommand', 'example.LoudGreetCommand']
    }

    def "a class compiled again as something that is not a command loses its descriptor"() {
        given:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} { ${HANDLE} }")
        compile('OtherCommand', "class OtherCommand implements ${CONTRACT} { ${HANDLE} }")

        when:
        compile('GreetCommand', 'class GreetCommand { }')

        then:
        !descriptorFile('example.GreetCommand').exists()
        descriptorFile('example.OtherCommand').isFile()
        registered() == ['example.OtherCommand']
    }

    def "what is not printable ASCII is escaped and read back as it was"() {
        given:
        String description = 'Sagt "Grüße" \\ 日本   \t end'

        when:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} { String description = '''${description.replace('\\', '\\\\')}'''; ${HANDLE} }")

        then:
        descriptorFile('example.GreetCommand').bytes.every { byte b -> b == 0x0a || (b >= 0x20 && b <= 0x7e) }
        descriptor('example.GreetCommand').description == description

        and: 'whether it can be shown is for the reader to say'
        !CommandDescriptor.isDescription(description)
    }

    def "the descriptor written by hand for the command this module registers by hand says what the command says"() {
        given: 'the compiler does not describe a command of the module its transformation is in'
        ConfigReportCommand command = new ConfigReportCommand()
        InputStream resource = ConfigReportCommand.classLoader.getResourceAsStream(CommandDescriptor.location(ConfigReportCommand.name))

        expect:
        resource.getText('US-ASCII') == CommandDescriptor.toJson(ConfigReportCommand.name, command.name, command.description)
        CommandDescriptor.isCommandName(command.name)
        CommandDescriptor.isDescription(command.description)
    }

    private static final String HANDLE = 'boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }'

    private File getTargetDirectory() {
        new File(tempDir, 'build/classes/groovy/cli')
    }

    private File descriptorFile(String className) {
        new File(targetDirectory, CommandDescriptor.location(className))
    }

    private Map descriptor(String className) {
        (Map) new JsonSlurper().parse(descriptorFile(className), 'US-ASCII')
    }

    private List<String> registered() {
        Properties factories = new Properties()
        new File(targetDirectory, CommandFactoriesTransformation.CLI_FACTORIES_LOCATION).withInputStream { factories.load(it) }
        factories.getProperty(CONTRACT).tokenize(',').sort()
    }

    private void compile(String fileName, String source, int phase = Phases.CANONICALIZATION) {
        File sourceFile = new File(tempDir, "src/cli/groovy/example/${fileName}.groovy")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package example\n${source}"
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: targetDirectory))
        unit.addSource(sourceFile)
        unit.compile(phase)
    }

    private String compiledName(String className) {
        compile(className.substring(className.lastIndexOf('.') + 1),
                "class BackupDatabaseCommand implements ${CONTRACT} { ${HANDLE} }", Phases.ALL)
        new URLClassLoader([targetDirectory.toURI().toURL()] as URL[], ApplicationCommand.classLoader).withCloseable { URLClassLoader loader ->
            ((ApplicationCommand) loader.loadClass(className).getDeclaredConstructor().newInstance()).name
        }
    }
}

abstract class NamedElsewhere implements ApplicationCommand {

    String getName() {
        'named-elsewhere'
    }
}
