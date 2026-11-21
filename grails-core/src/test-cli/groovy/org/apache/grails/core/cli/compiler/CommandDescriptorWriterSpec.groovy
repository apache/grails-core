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
import org.apache.grails.gradle.model.cli.CommandCatalogBuilder
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
        String description = 'Sagt "Grüße" \\ 日本 😀 @|red x|@ ${y} end'

        when:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} { String description = '''${description.replace('\\', '\\\\').replace('$', '\\$')}'''; ${HANDLE} }")

        then:
        descriptorFile('example.GreetCommand').bytes.every { byte b -> b == 0x0a || (b >= 0x20 && b <= 0x7e) }
        descriptor('example.GreetCommand').description == description
    }

    @Unroll
    def "a description the reader of a descriptor refuses is not written: #title"() {
        when:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} { String description = ${literal}; ${HANDLE} }")

        then: 'the descriptor is one the reader takes, without the description'
        descriptor('example.GreetCommand') == [format: 1, class: 'example.GreetCommand', name: 'greet', target: 'application']

        where:
        title                | literal
        'two lines'          | "'''line one\nline two'''"
        'a tab'              | "'one\\ttwo'"
        '600 characters'     | "'${'x' * 600}'"
        'an escape sequence' | "'red \\u001b[31m'"
    }

    @Unroll
    def "a command the reader of a descriptor would refuse gets no descriptor and keeps its registration: #title"() {
        when:
        compile(fileName, source)

        then:
        !descriptorFile(className).exists()
        registered() == [className]

        where:
        title                                        | fileName       | className           | source
        'a class name that gives no name'            | 'Command'      | 'example.Command'   | "class Command implements ${CONTRACT} { ${HANDLE} }"
        'a name that is not the name of a command'   | 'GreetCommand' | 'example.GreetCommand' | "class GreetCommand implements ${CONTRACT} { String name = 'Say Hello'; ${HANDLE} }"
        'a name of 65 characters'                    | 'GreetCommand' | 'example.GreetCommand' | "class GreetCommand implements ${CONTRACT} { String name = '${'a' * 65}'; ${HANDLE} }"
        'a class without a name of its own'          | 'Holder'       | 'example.Holder$1'  | "class Holder { def command = new ${CONTRACT}() { ${HANDLE} } }"
        'a name its constructor can change'          | 'GreetCommand' | 'example.GreetCommand' | "class GreetCommand implements ${CONTRACT} { String name = 'say-hello'; GreetCommand() { name = 'other' }; ${HANDLE} }"
        'a name an initializer can change'           | 'GreetCommand' | 'example.GreetCommand' | "class GreetCommand implements ${CONTRACT} { String name = 'say-hello'; { name = 'other' }; ${HANDLE} }"
    }

    def "a final property is what it starts as, whatever else the class has"() {
        when:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} { final String name = 'say-hello'; GreetCommand() { println 'made' }; ${HANDLE} }")

        then:
        descriptor('example.GreetCommand').name == 'say-hello'
    }

    def "an application with a compiled command and one registered and described by hand lists both by their descriptors"() {
        given: 'the registration and the descriptor of a command the compiler does not describe, in the resources, as the guide says'
        File resources = new File(tempDir, 'src/main/resources/META-INF')
        new File(resources, 'grails-commands').mkdirs()
        new File(resources, 'grails-cli.factories').text = "${CONTRACT}=example.AuditReportJavaCommand\n"
        new File(resources, 'grails-commands/example.AuditReportJavaCommand.json').text =
                '{"format": 1, "class": "example.AuditReportJavaCommand", "name": "audit", "description": "Writes the audit report", "target": "application"}\n'

        and: 'a command under grails-app/commands, compiled'
        File classes = new File(tempDir, 'build/classes/groovy/main')
        File source = new File(tempDir, 'grails-app/commands/example/GreetCommand.groovy')
        source.parentFile.mkdirs()
        source.text = "package example\nclass GreetCommand implements ${CONTRACT} { String description = 'Greets someone'; ${HANDLE} }"
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: classes, disabledGlobalASTTransformations: [
                'org.grails.compiler.injection.GlobalGrailsClassInjectorTransformation',
                'org.grails.compiler.injection.GlobalImportTransformation',
        ] as Set))
        unit.addSource(source)
        unit.compile(Phases.CANONICALIZATION)

        and: 'the resources where a build puts them'
        File processed = new File(tempDir, 'build/resources/main/META-INF')
        new File(processed, 'grails-commands').mkdirs()
        new File(processed, 'grails-cli.factories').bytes = new File(resources, 'grails-cli.factories').bytes
        new File(processed, 'grails-commands/example.AuditReportJavaCommand.json').bytes = new File(resources, 'grails-commands/example.AuditReportJavaCommand.json').bytes

        and:
        Properties merged = new Properties()
        new File(classes, CommandFactoriesTransformation.CLI_FACTORIES_LOCATION).withInputStream { merged.load(it) }

        expect: 'the compiler copied the registration written by hand into the classes directory, without a descriptor for it'
        merged.getProperty(CONTRACT).tokenize(',').sort() == ['example.AuditReportJavaCommand', 'example.GreetCommand']
        new File(classes, 'META-INF/grails-commands').list() as List == ['example.GreetCommand.json']

        when: 'the catalog is put together from the output of the project, as one element of the class path'
        CommandCatalogBuilder together = new CommandCatalogBuilder()
        together.add(CommandCatalogBuilder.ORIGIN_APPLICATION, [classes, processed.parentFile])

        and: 'and from the two directories as elements of their own, as two jars would be'
        CommandCatalogBuilder apart = new CommandCatalogBuilder()
        apart.add(CommandCatalogBuilder.ORIGIN_APPLICATION, classes)
        apart.add(CommandCatalogBuilder.ORIGIN_APPLICATION, processed.parentFile)

        then:
        together.build() == [
                format     : 1,
                complete   : true,
                diagnostics: [],
                commands   : [
                        [name: 'audit', aliases: [], description: 'Writes the audit report', origin: 'application', target: 'application', registration: 'descriptor'],
                        [name: 'greet', aliases: [], description: 'Greets someone', origin: 'application', target: 'application', registration: 'descriptor'],
                ],
        ]
        apart.build() == together.build()
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

    @Unroll
    def "a command that runs without the application, marked #where, is described as standalone"() {
        when:
        compile('TailCommand', source)

        then:
        descriptor('example.TailCommand') == [format: 1, class: 'example.TailCommand', name: 'tail',
                                              description: 'Follows a file', target: 'standalone']
        registered() == ['example.TailCommand']

        where:
        where                         | source
        'on the class'                | "class TailCommand implements ${CONTRACT}, ${STANDALONE} { String description = 'Follows a file'; ${HANDLE} }"
        'on a Grails command'         | "class TailCommand implements org.apache.grails.core.cli.GrailsApplicationCommand, ${STANDALONE} { String description = 'Follows a file'; boolean handle() { true } }"
        'on an interface it has'      | "interface FileCommand extends ${STANDALONE} {}\nclass TailCommand implements ${CONTRACT}, FileCommand { String description = 'Follows a file'; ${HANDLE} }"
        'on a class it extends'       | "abstract class FileCommand implements ${CONTRACT}, ${STANDALONE} { String description = 'Follows a file' }\nclass TailCommand extends FileCommand { ${HANDLE} }"
    }

    private static final String STANDALONE = 'org.apache.grails.core.cli.StandaloneCommand'

    private static final String HANDLE = 'boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }'

    private File getTargetDirectory() {
        new File(tempDir, 'build/classes/groovy/cli')
    }

    private File descriptorFile(String className) {
        new File(targetDirectory, CommandDescriptor.location(className))
    }

    private Map descriptor(String className) {
        // whatever the compiler writes, the reader of a descriptor takes
        CommandDescriptor.read(className, descriptorFile(className).bytes)
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
