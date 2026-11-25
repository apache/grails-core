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

import java.lang.reflect.Modifier

import groovy.json.JsonSlurper
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ApplicationContextCommandRegistry
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
        'a property that is null'    | 'String description = null'
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
    def "a command that runs without the application, marked #where, is described as one the build runs"() {
        when:
        compile('TailCommand', source)

        then:
        descriptor('example.TailCommand') == [format: 1, class: 'example.TailCommand', name: 'tail',
                                              description: 'Follows a file', target: 'gradle']
        registered() == ['example.TailCommand']

        where:
        where                         | source
        'on the class'                | "class TailCommand implements ${CONTRACT}, ${GRADLE} { String description = 'Follows a file'; ${HANDLE} }"
        'on a Grails command'         | "class TailCommand implements org.apache.grails.core.cli.GrailsApplicationCommand, ${GRADLE} { String description = 'Follows a file'; boolean handle() { true } }"
        'on an interface it has'      | "interface FileCommand extends ${GRADLE} {}\nclass TailCommand implements ${CONTRACT}, FileCommand { String description = 'Follows a file'; ${HANDLE} }"
        'on a class it extends'       | "abstract class FileCommand implements ${CONTRACT}, ${GRADLE} { String description = 'Follows a file' }\nclass TailCommand extends FileCommand { ${HANDLE} }"
    }

    @Unroll
    def "a name the class sets when an instance is made is the name recorded, and the name it runs under: #how"() {
        when:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} {\nString name = 'say-hello';\n${members}\n${HANDLE}\n}", Phases.ALL)

        then:
        descriptor('example.GreetCommand') == [format: 1, class: 'example.GreetCommand', name: 'other',
                                               description: 'other', target: 'application']
        made('example.GreetCommand') == [name: 'other', description: 'other']

        where:
        how                                                  | members
        'its constructor assigns it'                         | "GreetCommand() { name = 'other' }"
        'its constructor assigns it through this'            | "GreetCommand() { this.name = 'other' }"
        'its constructor calls its setter'                   | "GreetCommand() { setName('other') }"
        'its constructor calls its setter through this'      | "GreetCommand() { this.setName('other') }"
        'an initializer block assigns it'                    | "{ name = 'other' }"
        'an initializer block calls its setter'              | "{ setName('other') }"
        'a statement before it cannot skip it'               | "GreetCommand() { println 'made'; name = 'other' }"
        'every constructor sets it'                          | "GreetCommand() { name = 'other' }\nGreetCommand(int times) { setName('other') }"
        'an initializer block and the constructor set it alike' | "{ name = 'other' }\nGreetCommand() { name = 'other' }"
        'it is assigned twice, the second time last'         | "GreetCommand() { name = 'another'; name = 'other' }"
        'a statement before it cannot be followed'           | "GreetCommand() { [1].each { name = 'another' }; name = 'other' }"
    }

    @Unroll
    def "a name the class sets in another way when an instance is made is not known: #how"() {
        when:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} {\nString name = 'say-hello';\n${members}\n${HANDLE}\n}")

        then:
        !descriptorFile('example.GreetCommand').exists()
        registered() == ['example.GreetCommand']

        where:
        how                                                | members
        'it is added to'                                   | "GreetCommand() { name += '-loudly' }"
        'it is assigned in a closure'                      | "GreetCommand() { [1].each { name = 'other' } }"
        'it is assigned when a condition holds'            | "GreetCommand() { if (System.getProperty('greet.other')) { name = 'other' } }"
        'a statement before it can end the constructor'    | "GreetCommand() { if (System.getProperty('greet.same')) { return }; name = 'other' }"
        'it is assigned what is worked out'                | "GreetCommand() { name = System.getProperty('greet.name') }"
        'one constructor sets it and another does not'     | "GreetCommand() { name = 'other' }\nGreetCommand(int times) { }"
        'its setter is written by hand'                    | "GreetCommand() { name = 'other' }\nvoid setName(String name) { this.name = name + '-loudly' }"
        'a call after it is not followed'                  | "GreetCommand() { name = 'other'; println 'made' }"
        'one constructor runs another'                     | "GreetCommand() { this(1) }\nGreetCommand(int times) { name = 'other' }"
    }

    @Unroll
    def "a command that extends another is described by what it declares or sets itself before what it inherits: #how"() {
        when:
        compile('ImportDataCommand', "abstract class OldNameCommand implements ${CONTRACT} {\n${base}\n}\n" +
                "class ImportDataCommand extends OldNameCommand {\n${members}\n${HANDLE}\n}", Phases.ALL)

        then: 'what is recorded is what the command has when it runs'
        descriptor('example.ImportDataCommand') == [format: 1, class: 'example.ImportDataCommand', name: name,
                                                    description: description, target: 'application']
        made('example.ImportDataCommand') == [name: name, description: description]
        registered() == ['example.ImportDataCommand']

        where:
        how                                                         | base                                                         | members                                                                      || name          | description
        'it declares the property again'                            | "String name = 'old-name'"                                   | "String name = 'import-data'"                                                || 'import-data' | 'import-data'
        'it declares the property over a final one'                 | "final String name = 'old-name'"                             | "String name = 'import-data'"                                                || 'import-data' | 'import-data'
        'it declares the property over the method'                  | "String getName() { 'old-name' }"                            | "String name = 'import-data'"                                                || 'import-data' | 'import-data'
        'it writes the method over the property'                    | "String name = 'old-name'"                                   | "String getName() { 'import-data' }"                                         || 'import-data' | 'import-data'
        'it writes the method again'                                | "String getName() { 'old-name' }"                            | "String getName() { 'import-data' }"                                         || 'import-data' | 'import-data'
        'its constructor assigns the property'                      | "String name = 'old-name'"                                   | "ImportDataCommand() { name = 'import-data' }"                               || 'import-data' | 'import-data'
        'its constructor assigns the property through this'        | "String name = 'old-name'"                                   | "ImportDataCommand() { this.name = 'import-data' }"                          || 'import-data' | 'import-data'
        'its constructor calls the setter'                          | "String name = 'old-name'"                                   | "ImportDataCommand() { setName('import-data') }"                             || 'import-data' | 'import-data'
        'an initializer block assigns the property'                 | "String name = 'old-name'"                                   | "{ name = 'import-data' }"                                                   || 'import-data' | 'import-data'
        'it sets the property the class it extends sets'           | "String name\nOldNameCommand() { name = 'old-name' }"        | "ImportDataCommand() { name = 'import-data' }"                               || 'import-data' | 'import-data'
        'it sets the property the class it extends works out'      | "String name\nOldNameCommand() { name = System.getProperty('old.name', 'old-name') }" | "ImportDataCommand() { name = 'import-data' }" || 'import-data' | 'import-data'
        'its constructor sets the description'                      | "String description = 'Imports old data'"                    | "ImportDataCommand() { description = 'Imports the data' }"                   || 'import-data' | 'Imports the data'
        'an initializer block sets the description'                 | "String description = 'Imports old data'"                    | "{ description = 'Imports the data' }"                                       || 'import-data' | 'Imports the data'
        'its constructor sets both'                                 | "String name = 'old-name'\nString description = 'Imports old data'" | "ImportDataCommand() { name = 'import-data'; description = 'Imports the data' }" || 'import-data' | 'Imports the data'
        'it declares nothing, and has the property'                 | "String name = 'old-name'"                                   | ''                                                                           || 'old-name'    | 'old-name'
        'it declares nothing, and has the method'                   | "String getName() { 'old-name' }"                            | ''                                                                           || 'old-name'    | 'old-name'
        'it declares nothing, and has what the class it extends sets' | "String name\nOldNameCommand() { name = 'old-name' }"      | ''                                                                           || 'old-name'    | 'old-name'
        'it sets the property, which the method it has does not read' | "String name\nString getName() { 'old-name' }"             | "ImportDataCommand() { name = 'import-data' }"                               || 'old-name'    | 'old-name'
        'it assigns a field of its own of that name'               | "String name = 'old-name'"                                   | "private String name\nImportDataCommand() { name = 'import-data'; this.name = 'import-data' }" || 'old-name' | 'old-name'
        'it has a field of its own of that name, and calls the setter' | "String name = 'old-name'"                               | "private String name\nImportDataCommand() { setName('import-data') }"       || 'import-data' | 'import-data'
    }

    @Unroll
    def "of three classes the one nearest to the command that declares or sets the name decides: #how"() {
        when:
        compile('ImportDataCommand', "abstract class OldNameCommand implements ${CONTRACT} {\n${first}\n}\n" +
                "abstract class RenamedCommand extends OldNameCommand {\n${second}\n}\n" +
                "class ImportDataCommand extends RenamedCommand {\n${third}\n${HANDLE}\n}", Phases.ALL)

        then:
        descriptor('example.ImportDataCommand').name == name
        made('example.ImportDataCommand').name == name

        where:
        how                                                  | first                      | second                                       | third                                          || name
        'the second sets what the first declares'            | "String name = 'old-name'" | "RenamedCommand() { name = 'middle-name' }"  | ''                                             || 'middle-name'
        'the third sets it after the second'                 | "String name = 'old-name'" | "RenamedCommand() { name = 'middle-name' }"  | "ImportDataCommand() { name = 'import-data' }" || 'import-data'
        'the third sets it after the second works it out'    | "String name = 'old-name'" | "RenamedCommand() { name = System.getProperty('middle.name', 'middle-name') }" | "ImportDataCommand() { name = 'import-data' }" || 'import-data'
        'the third sets what the first declares'             | "String name = 'old-name'" | ''                                           | "{ setName('import-data') }"                   || 'import-data'
        'the second declares it again'                       | "String name = 'old-name'" | "String name = 'middle-name'"                | ''                                             || 'middle-name'
        'the third sets what the second declares again'      | "String name = 'old-name'" | "String name = 'middle-name'"                | "ImportDataCommand() { name = 'import-data' }" || 'import-data'
        'the second writes the method, which nothing changes' | "String name = 'old-name'" | "String getName() { 'middle-name' }"        | "ImportDataCommand() { name = 'import-data' }" || 'middle-name'
    }

    @Unroll
    def "a command that extends another and sets the name in a way that cannot be read gets no descriptor and keeps its registration: #how"() {
        when:
        compile('ImportDataCommand', "abstract class OldNameCommand implements ${CONTRACT} {\n${base}\n}\n" +
                "class ImportDataCommand extends OldNameCommand {\n${members}\n${HANDLE}\n}")

        then:
        !descriptorFile('example.ImportDataCommand').exists()
        registered() == ['example.ImportDataCommand']

        where:
        how                                          | base                                                                                   | members
        'it assigns what is worked out'              | "String name = 'old-name'"                                                             | "ImportDataCommand() { name = System.getProperty('import.name') }"
        'it assigns when a condition holds'          | "String name = 'old-name'"                                                             | "ImportDataCommand() { if (System.getProperty('import.name')) { name = 'import-data' } }"
        'it assigns through super'                   | "String name = 'old-name'"                                                             | "ImportDataCommand() { super.name = 'import-data' }"
        'the class it extends writes the setter'     | "String name = 'old-name'\nvoid setName(String name) { this.name = 'legacy-' + name }" | "ImportDataCommand() { name = 'import-data' }"
    }

    @Unroll
    def "of a command that extends a class compiled before, what the command declares itself is read, and what it sets over that class is not: #how"() {
        given: 'the class it extends, compiled on its own'
        File classes = new File(tempDir, 'build/classes/groovy/base')
        compile('OldNameCommand', "abstract class OldNameCommand implements ${CONTRACT} {\n${base}\n}", Phases.ALL, classes)

        when:
        compile('ImportDataCommand', "class ImportDataCommand extends OldNameCommand {\n${members}\n${HANDLE}\n}", Phases.ALL, targetDirectory, [classes])

        then:
        (descriptorFile('example.ImportDataCommand').isFile() ? descriptor('example.ImportDataCommand').name : null) == recorded
        made('example.ImportDataCommand', classes).name == name
        registered() == ['example.ImportDataCommand']

        where:
        how                                                   | base                       | members                                        || recorded      | name
        'it declares the property'                            | "String name = 'old-name'" | "String name = 'import-data'"                  || 'import-data' | 'import-data'
        'it writes the method'                                | "String name = 'old-name'" | "String getName() { 'import-data' }"           || 'import-data' | 'import-data'
        'it declares nothing, and the name is the contract\'s' | ''                        | ''                                             || 'import-data' | 'import-data'
        'it sets the property'                                | "String name = 'old-name'" | "ImportDataCommand() { name = 'import-data' }" || null          | 'import-data'
        'it declares nothing, and has the property'           | "String name = 'old-name'" | ''                                             || null          | 'old-name'
    }

    @Unroll
    def "a name the code a constructor calls sets is recorded when that code is read, and nothing is recorded when it is not: #how"() {
        when:
        compile('ImportDataCommand', "abstract class OldNameCommand implements ${CONTRACT} {\n${base}\n}\n" +
                "class ImportDataCommand extends OldNameCommand {\n${members}\n${HANDLE}\n}\n${others}", Phases.ALL)

        then: 'what is recorded is the name the command has when it is made, or nothing'
        recordedName(className) == recorded
        Map<String, String> made = made(className)
        made.name == name
        recorded == null || descriptor(className).subMap('name', 'description') == made
        registered().contains(className)

        where:
        how                                                              | base                                                               | members                                                                                              | others                                                                                  | className                       || recorded      | name
        'it calls a helper that sets the name (the review)'              | "String name = 'old-name'"                                         | "ImportDataCommand() { rename() }\nprivate void rename() { name = 'import-data' }"                   | ''                                                                                      | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'the class it extends sets the name in its constructor'          | "String name\nOldNameCommand() { name = 'old-name' }"              | "ImportDataCommand() { rename() }\nprivate void rename() { name = 'import-data' }"                   | ''                                                                                      | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'the helper is final'                                            | "String name = 'old-name'"                                         | "ImportDataCommand() { rename() }\nfinal void rename() { this.name = 'import-data' }"                | ''                                                                                      | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'the helper can be overridden'                                   | "String name = 'old-name'"                                         | "ImportDataCommand() { this.rename() }\nvoid rename() { setName('import-data') }"                    | ''                                                                                      | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'the helper is static, and is given the command'                 | "String name = 'old-name'"                                         | "ImportDataCommand() { rename(this) }\nstatic void rename(ImportDataCommand command) { command.name = 'import-data' }" | ''                                                       | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'the static helper calls the setter of the command it is given'  | "String name = 'old-name'"                                         | "ImportDataCommand() { ImportDataCommand.rename(this) }\nstatic void rename(OldNameCommand command) { command.setName('import-data') }" | ''                                     | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'a helper calls a helper'                                        | "String name = 'old-name'"                                         | "ImportDataCommand() { prepare() }\nprivate void prepare() { println 'preparing'; rename() }\nprivate void rename() { name = 'import-data' }" | ''                         | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'it calls a helper of the class it extends'                      | "String name = 'old-name'\nprotected void useNewName() { name = 'import-data' }" | 'ImportDataCommand() { useNewName() }'                                                 | ''                                                                                      | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'the class it extends calls a method it overrides'               | "String name = 'old-name'\nOldNameCommand() { configure() }\nprotected void configure() { }" | "protected void configure() { name = 'import-data' }"                        | ''                                                                                      | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'a field initializer calls the helper'                           | "String name = 'old-name'"                                         | "private final String marker = rename()\nprivate String rename() { name = 'import-data'; 'renamed' }" | ''                                                                                    | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'the helper sets the name, then the constructor makes something' | "String name = 'old-name'"                                         | "ImportDataCommand() { rename(); new StringBuilder('made').append('!') }\nprivate void rename() { name = 'import-data' }" | ''                                                  | 'example.ImportDataCommand'     || 'import-data' | 'import-data'
        'a further class overrides the helper'                           | "String name = 'old-name'"                                         | "ImportDataCommand() { rename() }\nvoid rename() { name = 'import-data' }"                           | "class LoudImportCommand extends ImportDataCommand { void rename() { name = 'loud-import' } }" | 'example.LoudImportCommand' || 'loud-import' | 'loud-import'
        'the class a further class overrides the helper of'              | "String name = 'old-name'"                                         | "ImportDataCommand() { rename() }\nvoid rename() { name = 'import-data' }"                           | "class LoudImportCommand extends ImportDataCommand { void rename() { name = 'loud-import' } }" | 'example.ImportDataCommand' || 'import-data' | 'import-data'
        'the helper sets the name when a condition holds'                | "String name = 'old-name'"                                         | "ImportDataCommand() { rename() }\nprivate void rename() { if (!System.getProperty('import.old')) { name = 'import-data' } }" | ''                                              | 'example.ImportDataCommand'     || null          | 'import-data'
        'the helper sets the name it is given'                           | "String name = 'old-name'"                                         | "ImportDataCommand() { rename('import-data') }\nprivate void rename(String to) { name = to }"         | ''                                                                                      | 'example.ImportDataCommand'     || null          | 'import-data'
        'the helper calls itself'                                        | "String name = 'old-name'"                                         | "ImportDataCommand() { rename(2) }\nprivate void rename(int times) { if (times > 0) { rename(times - 1) } else { name = 'import-data' } }" | ''                   | 'example.ImportDataCommand'     || null          | 'import-data'
        'a closure sets the name'                                        | "String name = 'old-name'"                                         | "ImportDataCommand() { [1].each { name = 'import-data' } }"                                          | ''                                                                                      | 'example.ImportDataCommand'     || null          | 'import-data'
        'a with block sets the name'                                     | "String name = 'old-name'"                                         | "ImportDataCommand() { with { name = 'import-data' } }"                                              | ''                                                                                      | 'example.ImportDataCommand'     || null          | 'import-data'
        'a closure the helper is given sets the name'                    | "String name = 'old-name'"                                         | "ImportDataCommand() { apply { name = 'import-data' } }\nprivate void apply(Closure change) { change() }" | ''                                                                                  | 'example.ImportDataCommand'     || null          | 'import-data'
        'a method of another class is given the command'                 | "String name = 'old-name'"                                         | 'ImportDataCommand() { Renamer.rename(this) }'                                                       | "class Renamer { static void rename(OldNameCommand command) { command.name = 'import-data' } }" | 'example.ImportDataCommand' || null          | 'import-data'
        'the runtime sets the property by its name'                      | "String name = 'old-name'"                                         | "ImportDataCommand() { setProperty('name', 'import-data') }"                                         | ''                                                                                      | 'example.ImportDataCommand'     || null          | 'import-data'
        'a subscript sets the property'                                  | "String name = 'old-name'"                                         | "ImportDataCommand() { this['name'] = 'import-data' }"                                               | ''                                                                                      | 'example.ImportDataCommand'     || null          | 'import-data'
        'the meta class sets the property'                               | "String name = 'old-name'"                                         | "ImportDataCommand() { metaClass.setProperty(this, 'name', 'import-data') }"                         | ''                                                                                      | 'example.ImportDataCommand'     || null          | 'import-data'
        'reflection sets the field'                                      | "String name = 'old-name'"                                         | "ImportDataCommand() { java.lang.reflect.Field field = OldNameCommand.getDeclaredField('name'); field.accessible = true; field.set(this, 'import-data') }" | '' | 'example.ImportDataCommand'  || null          | 'import-data'
        'a trait of the command sets it when its fields are given values' | "String name = 'old-name'"                                        | ''                                                                                                   | "trait Renaming { String marker = rename()\nString rename() { setName('import-data'); 'renamed' } }\nclass RenamingImportCommand extends OldNameCommand implements Renaming { ${HANDLE} }" | 'example.RenamingImportCommand' || null | 'import-data'
        'code the command was handed to sets it after the constructor does' | "String name = 'old-name'"                                      | "ImportDataCommand() { Keeper.keep(this); name = 'import-data'; Keeper.touch() }"                     | "class Keeper { static OldNameCommand kept\nstatic void keep(OldNameCommand command) { kept = command }\nstatic void touch() { kept.name = 'touched' } }" | 'example.ImportDataCommand' || null | 'touched'
    }

    @Unroll
    def "a description the code a constructor calls sets is recorded when that code is read: #how"() {
        when:
        compile('ImportDataCommand', "abstract class OldNameCommand implements ${CONTRACT} { String description = 'Imports old data' }\n" +
                "class ImportDataCommand extends OldNameCommand {\n${members}\n${HANDLE}\n}", Phases.ALL)

        then:
        descriptor('example.ImportDataCommand').description == recorded
        made('example.ImportDataCommand').description == description

        where:
        how                                       | members                                                                                                     || recorded           | description
        'a helper sets it'                        | "ImportDataCommand() { describe() }\nprivate void describe() { description = 'Imports the data' }"          || 'Imports the data' | 'Imports the data'
        'a helper sets it when a condition holds' | "ImportDataCommand() { describe() }\nprivate void describe() { if (!System.getProperty('x')) { description = 'Imports the data' } }" || null | 'Imports the data'
    }

    def "a command that extends another is listed under the name it is found by when it is run"() {
        given: 'a command that sets its name in its constructor over the one of the class it extends, compiled'
        compile('ImportDataCommand', "abstract class OldNameCommand implements ${CONTRACT} { String name = 'old-name' }\n" +
                "class ImportDataCommand extends OldNameCommand { ImportDataCommand() { name = 'import-data' }; ${HANDLE} }", Phases.ALL)

        when: 'the build lists the commands of the compiled classes'
        CommandCatalogBuilder catalog = new CommandCatalogBuilder()
        catalog.add(CommandCatalogBuilder.ORIGIN_APPLICATION, targetDirectory)
        List<Map> listed = (List<Map>) catalog.build().commands

        and: 'the command is looked for by the name listed, as the runner of runCommand looks for it'
        ApplicationCommand found = withCompiledCommands { new ApplicationContextCommandRegistry().findCommand((String) listed[0].name) }

        then:
        listed == [[name: 'import-data', aliases: [], description: 'import-data', origin: 'application', target: 'application', registration: 'descriptor']]
        found?.getClass()?.name == 'example.ImportDataCommand'
    }

    @Unroll
    def "a command whose name cannot be read is listed by the name its class gives, and is run by that name: #how"() {
        given: 'a command whose name is set in a way the compiler does not read, compiled'
        compile('LegacyImportCommand', "abstract class OldNameCommand implements ${CONTRACT} { String name = 'old-name' }\n" +
                "class LegacyImportCommand extends OldNameCommand {\n${members}\n${HANDLE}\n}", Phases.ALL)

        when: 'the build lists the commands of the compiled classes'
        CommandCatalogBuilder catalog = new CommandCatalogBuilder()
        catalog.add(CommandCatalogBuilder.ORIGIN_APPLICATION, targetDirectory)
        List<Map> listed = (List<Map>) catalog.build().commands

        and: 'the command is looked for by the name listed, as the runner of runCommand looks for it, and by the name it has'
        String name = made('example.LegacyImportCommand').name
        List<String> found = withCompiledCommands {
            ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()
            [registry.findCommand((String) listed[0].name), registry.findCommand(name)].collect { ApplicationCommand command -> command?.getClass()?.name }
        }

        then:
        !descriptorFile('example.LegacyImportCommand').exists()
        listed == [[name: 'legacy-import', aliases: [], description: '', origin: 'application', target: 'application', registration: 'factories']]
        name == 'import-data'
        found == ['example.LegacyImportCommand', 'example.LegacyImportCommand']

        where:
        how                                       | members
        'a helper sets it when a condition holds' | "LegacyImportCommand() { rename() }\nprivate void rename() { if (!System.getProperty('import.old')) { name = 'import-data' } }"
        'a closure sets it'                       | "LegacyImportCommand() { with { name = 'import-data' } }"
        'it is worked out'                        | "LegacyImportCommand() { name = 'import-' + 'data' }"
    }

    def "of two commands whose classes give one name, the one listed by it is run by it"() {
        given: 'two classes of one simple name in two packages: the name of one is read, the name of the other is not'
        File sources = new File(tempDir, 'src/cli/groovy/example')
        File described = new File(sources, 'a/TwinCommand.groovy')
        File undescribed = new File(sources, 'b/TwinCommand.groovy')
        described.parentFile.mkdirs()
        undescribed.parentFile.mkdirs()
        described.text = "package example.a\nclass TwinCommand implements ${CONTRACT} { String name = 'twin-a'; ${HANDLE} }"
        undescribed.text = "package example.b\nclass TwinCommand implements ${CONTRACT} {\nString name = 'twin-a'\n" +
                "TwinCommand() { if (!System.getProperty('twin.a')) { name = 'twin-b' } }\n${HANDLE}\n}"
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: targetDirectory))
        unit.addSource(described)
        unit.addSource(undescribed)
        unit.compile(Phases.ALL)

        when:
        CommandCatalogBuilder catalog = new CommandCatalogBuilder()
        catalog.add(CommandCatalogBuilder.ORIGIN_APPLICATION, targetDirectory)
        List<Map> listed = (List<Map>) catalog.build().commands
        Map<String, String> found = withCompiledCommands {
            ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()
            listed.collectEntries { Map command -> [(command.name): registry.findCommand((String) command.name)?.getClass()?.name] }
        }

        then: 'each name listed runs the class listed by it'
        listed.collect { Map command -> [command.name, command.registration] } == [['twin', 'factories'], ['twin-a', 'descriptor']]
        found == [twin: 'example.b.TwinCommand', 'twin-a': 'example.a.TwinCommand']
    }

    @Unroll
    def "what a constructor does to something else leaves the name what it starts as: #how"() {
        when:
        compile('GreetCommand', "class GreetCommand implements ${CONTRACT} { String name = 'say-hello'; String other; List<String> words = ['a']; " +
                "GreetCommand() { ${statement} }; ${HANDLE} }")

        then:
        descriptor('example.GreetCommand') == [format: 1, class: 'example.GreetCommand', name: 'say-hello',
                                               description: 'say-hello', target: 'application']

        where:
        how                                          | statement
        'another property is assigned'               | "other = 'x'"
        'another property is assigned through this'  | "this.other = 'x'"
        'an element of a list is assigned'           | "words[0] = 'name'"
        'the setter of another property is called'   | "setOther('name')"
        'the name is read'                           | "other = name"
        'something is made that is not given it'     | "new StringBuilder('name').append('x')"
        'a method of another class is called'        | "other = System.getProperty('greet.other', 'x')"
    }

    def "a class whose name a descriptor cannot have gets none, and loses nothing when it is no command any more"() {
        when: 'a name that is not ASCII, which the reader of a descriptor refuses'
        compile('GrüßeCommand', "class GrüßeCommand implements ${CONTRACT} { ${HANDLE} }")

        then:
        registered() == ['example.GrüßeCommand']
        !new File(targetDirectory, 'META-INF/grails-commands').exists()

        when:
        compile('GrüßeCommand', 'class GrüßeCommand { }')

        then:
        noExceptionThrown()
        !new File(targetDirectory, 'META-INF/grails-commands').exists()
    }

    def "a class that declares nothing, and has nothing of the command contract in it, is described by the name its class gives"() {
        given: 'a class as the compiler has it before anything is woven into it'
        ClassNode plain = new ClassNode('example.ArchiveReportCommand', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)

        expect:
        CommandDescriptorWriter.write(plain, targetDirectory)
        descriptor('example.ArchiveReportCommand') == [format: 1, class: 'example.ArchiveReportCommand', name: 'archive-report',
                                                       description: 'archive-report', target: 'application']

        when:
        CommandDescriptorWriter.remove('example.ArchiveReportCommand', targetDirectory)

        then:
        !descriptorFile('example.ArchiveReportCommand').exists()

        when: 'there is none to remove'
        CommandDescriptorWriter.remove('example.ArchiveReportCommand', targetDirectory)

        then:
        noExceptionThrown()
    }

    private static final String GRADLE = 'org.apache.grails.core.cli.GradleCommand'

    private static final String HANDLE = 'boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }'

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

    private List<String> registered() {
        Properties factories = new Properties()
        new File(targetDirectory, CommandFactoriesTransformation.CLI_FACTORIES_LOCATION).withInputStream { factories.load(it) }
        factories.getProperty(CONTRACT).tokenize(',').sort()
    }

    private void compile(String fileName, String source, int phase = Phases.CANONICALIZATION, File target = null, List<File> classpath = []) {
        File sourceFile = new File(tempDir, "src/cli/groovy/example/${fileName}.groovy")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package example\n${source}"
        // a directory that is not there yet is false to Groovy
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: target != null ? target : targetDirectory,
                classpathList: classpath*.absolutePath))
        unit.addSource(sourceFile)
        unit.compile(phase)
    }

    // the name and the description a compiled command has when it is made, as the registry of commands makes it
    private Map<String, String> made(String className, File... classpath) {
        URL[] urls = ([targetDirectory] + (classpath as List<File>)).collect { File directory -> directory.toURI().toURL() } as URL[]
        new URLClassLoader(urls, ApplicationCommand.classLoader).withCloseable { URLClassLoader loader ->
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
