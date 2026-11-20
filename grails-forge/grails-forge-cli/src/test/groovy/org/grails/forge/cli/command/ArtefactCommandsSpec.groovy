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
package org.grails.forge.cli.command

import java.nio.file.Files
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Unroll

/**
 * The commands that create the classes of an application, run in a directory that has the record of
 * a generated project and nothing else of it: what each writes, byte for byte, with the templates of
 * the command line and with those of the project, and what each refuses. No application is built.
 */
class ArtefactCommandsSpec extends CommandSpec implements CommandFixture {

    static final String PROFILE_CONTROLLER = '@artifact.package@\nclass @artifact.name@Controller {\n\n    def index() { }\n}\n'
    static final String QUARTZ_JOB = 'package ${packageName}\n\nclass ${className}Job {\n    static triggers = {\n' +
            '      simple repeatInterval: 5000l // execute job once in 5 seconds\n    }\n\n    def execute() {\n        // execute job\n    }\n}\n'

    @Shared
    Map<String, File> records = [:]

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    File outside

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        outside = Files.createTempDirectory('grailsforgeoutside').toFile()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        outside.deleteDir()
    }

    void cleanupSpec() {
        records.values()*.deleteDir()
    }

    void "the commands that were there write what they wrote before"() {
        given:
        project(ApplicationType.WEB, 'grails-quartz')

        when:
        Map<String, String> written = writtenBy {
            ['create-controller', 'create-service', 'create-domain-class', 'create-taglib', 'create-interceptor', 'create-job'].each { String command ->
                assert ForgeCli.execute(beanContext, command, 'fooBar') == 0
                assert ForgeCli.execute(beanContext, command, 'other.pkg.Baz') == 0
            }
        }

        then:
        written == golden('existing')

        and: 'as before, every file is said, and no template of the project is'
        out.toString().contains('| Rendered controller to grails-app/controllers/example/grails/FooBarController.groovy')
        !out.toString().contains('Using the template')
        !out.toString().contains('Error')
    }

    void "the commands every project has"() {
        given:
        project(ApplicationType.WEB)

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-unit-test', 'fooBar') == 0
            assert ForgeCli.execute(beanContext, 'create-unit-test', 'other.pkg.Baz') == 0
            assert ForgeCli.execute(beanContext, 'create-integration-test', 'fooBarFlow') == 0
            assert ForgeCli.execute(beanContext, 'create-integration-test', 'other.pkg.BazFlow') == 0
            assert ForgeCli.execute(beanContext, 'create-command', 'fooBar') == 0
            assert ForgeCli.execute(beanContext, 'create-command', 'other.pkg.Baz') == 0
            assert ForgeCli.execute(beanContext, 'create-script', 'fooBar') == 0
            assert ForgeCli.execute(beanContext, 'create-script', 'other-thing') == 0
        }

        then:
        written == golden('web')
        out.toString().readLines() == [
                '| Rendered unit test to src/test/groovy/example/grails/FooBarSpec.groovy',
                '| Rendered unit test to src/test/groovy/other/pkg/BazSpec.groovy',
                '| Rendered integration test to src/integration-test/groovy/example/grails/FooBarFlowSpec.groovy',
                '| Rendered integration test to src/integration-test/groovy/other/pkg/BazFlowSpec.groovy',
                '| Rendered command to grails-app/commands/example/grails/FooBarCommand.groovy',
                '| Rendered command to grails-app/commands/other/pkg/BazCommand.groovy',
                '| Rendered script to src/main/scripts/foo-bar.groovy',
                '| Rendered script to src/main/scripts/other-thing.groovy'
        ]
    }

    void "the commands of a REST API application"() {
        given:
        project(ApplicationType.REST_API)

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-functional-test', 'fooBarApi') == 0
            assert ForgeCli.execute(beanContext, 'create-functional-test', 'other.pkg.BazApi') == 0
            assert ForgeCli.execute(beanContext, 'create-restful-controller', 'fooBar') == 0
            assert ForgeCli.execute(beanContext, 'create-restful-controller', 'other.pkg.Baz') == 0
            assert ForgeCli.execute(beanContext, 'create-domain-resource', 'fooBar') == 0
            assert ForgeCli.execute(beanContext, 'create-domain-resource', 'other.pkg.Baz') == 0
        }

        then:
        written == golden('rest-api')
        out.toString().readLines() == [
                '| Rendered functional test to src/integration-test/groovy/example/grails/FooBarApiSpec.groovy',
                '| Rendered functional test to src/integration-test/groovy/other/pkg/BazApiSpec.groovy',
                '| Rendered controller to grails-app/controllers/example/grails/FooBarController.groovy',
                '| Rendered controller to grails-app/controllers/other/pkg/BazController.groovy',
                '| Rendered domain resource to grails-app/domain/example/grails/FooBar.groovy',
                '| Rendered domain resource to src/test/groovy/example/grails/FooBarSpec.groovy',
                '| Rendered domain resource to grails-app/domain/other/pkg/Baz.groovy',
                '| Rendered domain resource to src/test/groovy/other/pkg/BazSpec.groovy'
        ]

        and: 'it has the commands every project has as well'
        ForgeCli.execute(beanContext, 'create-unit-test', 'Third') == 0
        ForgeCli.execute(beanContext, 'create-integration-test', 'Third') == 0
    }

    @Unroll
    void "what is written in #set is Groovy"() {
        expect:
        golden(set).findAll { String path, String text -> path.endsWith('.groovy') }.each { String path, String text ->
            CompilationUnit unit = new CompilationUnit()
            unit.addSource(path, text)
            unit.compile(Phases.CONVERSION)
        }

        where:
        set << ['existing', 'web', 'rest-api']
    }

    @Unroll
    void "#command is a command of a #has project"() {
        given:
        project(has)

        expect:
        ForgeCli.execute(beanContext, command, '--help') == 0
        out.toString().contains("Usage: grails-forge-cli ${command}")

        where:
        command                     | has
        'create-functional-test'    | ApplicationType.REST_API
        'create-restful-controller' | ApplicationType.REST_API
        'create-domain-resource'    | ApplicationType.REST_API
        'create-integration-test'   | ApplicationType.WEB_PLUGIN
        'create-unit-test'          | ApplicationType.PLUGIN
        'create-command'            | ApplicationType.PLUGIN
        'create-script'             | ApplicationType.PLUGIN
        'install-templates'         | ApplicationType.PLUGIN
    }

    @Unroll
    void "#command is no command of a #hasNot project"() {
        given:
        project(hasNot)

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, command, 'Book')
        }

        then:
        exit == 2
        out.toString().contains("There is no command ${command}")
        written == [:]

        where:
        command                     | hasNot
        'create-functional-test'    | ApplicationType.WEB
        'create-restful-controller' | ApplicationType.WEB
        'create-domain-resource'    | ApplicationType.WEB_PLUGIN
        'create-integration-test'   | ApplicationType.PLUGIN
    }

    void "a directory without the record of a project has none of the commands"() {
        expect:
        ForgeCli.execute(beanContext, command, 'Book') == 2
        dir.list().length == 0

        where:
        command << ['create-unit-test', 'create-command', 'create-script', 'install-templates']
    }

    @Unroll
    void "#command #given is the class #file"() {
        given:
        project(ApplicationType.REST_API)

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, command, given) == 0
        }

        then:
        written.keySet() == [file] as Set
        written[file].contains(declared)

        where:
        command                     | given                  | file                                                             | declared
        'create-unit-test'          | 'BookSpec'             | 'src/test/groovy/example/grails/BookSpec.groovy'                 | 'class BookSpec extends'
        'create-unit-test'          | 'book-store'           | 'src/test/groovy/example/grails/BookStoreSpec.groovy'            | 'class BookStoreSpec extends'
        'create-unit-test'          | 'Spec'                 | 'src/test/groovy/example/grails/SpecSpec.groovy'                 | 'class SpecSpec extends'
        'create-integration-test'   | 'a.b.BookSpec'         | 'src/integration-test/groovy/a/b/BookSpec.groovy'                | 'package a.b\n'
        'create-functional-test'    | 'a.BookSpec'           | 'src/integration-test/groovy/a/BookSpec.groovy'                  | 'class BookSpec extends'
        'create-restful-controller' | 'BookController'       | 'grails-app/controllers/example/grails/BookController.groovy'    | 'class BookController extends RestfulController<Book>'
        'create-command'            | 'ImportCommand'        | 'grails-app/commands/example/grails/ImportCommand.groovy'        | 'class ImportCommand implements'
        'create-command'            | 'data-import'          | 'grails-app/commands/example/grails/DataImportCommand.groovy'    | 'class DataImportCommand implements'
        'create-script'             | 'DataImport'           | 'src/main/scripts/data-import.groovy'                            | 'println'
        'create-script'             | 'data-import'          | 'src/main/scripts/data-import.groovy'                            | 'println'
        'create-script'             | 'dataImport'           | 'src/main/scripts/data-import.groovy'                            | 'println'
    }

    void "a file that is there is kept, and then nothing is written"() {
        given:
        project(ApplicationType.REST_API)
        File test = new File(dir, 'src/test/groovy/example/grails/BookSpec.groovy')
        test.parentFile.mkdirs()
        test.text = 'mine'

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-domain-resource', 'Book') == 1
        }

        then: 'neither the test nor the class it would have been written with'
        written == [:]
        test.text == 'mine'
        out.toString().readLines() == ['| Error src/test/groovy/example/grails/BookSpec.groovy already exists. Run again with -f to overwrite.']

        when:
        out.reset()
        written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-domain-resource', 'Book', flag) == 0
        }

        then:
        written.keySet() == ['grails-app/domain/example/grails/Book.groovy', 'src/test/groovy/example/grails/BookSpec.groovy'] as Set
        test.text.contains('DomainUnitTest<Book>')

        when: 'both are there now'
        out.reset()
        int again = ForgeCli.execute(beanContext, 'create-domain-resource', 'Book')

        then:
        again == 1
        out.toString().readLines() == [
                '| Error grails-app/domain/example/grails/Book.groovy already exists. Run again with -f to overwrite.',
                '| Error src/test/groovy/example/grails/BookSpec.groovy already exists. Run again with -f to overwrite.'
        ]

        where:
        flag << ['-f', '--force']
    }

    @Unroll
    void "#command refuses the name #name"() {
        given:
        project(ApplicationType.REST_API)

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, command, name)
        }

        then: 'a mistake in the command line, said in one line, before anything is written anywhere'
        exit == 2
        written == [:]
        dir.parentFile.list().every { !it.endsWith('.groovy') }
        outside.list().length == 0
        out.toString().readLines().first().contains(says)
        !out.toString().readLines().first().contains('\u0007')
        !out.toString().contains('Exception')

        where:
        command                   | name                          | says
        'create-unit-test'        | '../Escape'                   | 'is not a name for a class: it is a path'
        'create-unit-test'        | '..'                          | 'is not a name for a class'
        'create-unit-test'        | 'a/b/Book'                    | 'is not a name for a class: it is a path'
        'create-unit-test'        | 'a\\Book'                     | 'is not a name for a class: it is a path'
        'create-unit-test'        | '/tmp/Book'                   | 'is not a name for a class: it is a path'
        'create-unit-test'        | 'C:Book'                      | 'is not a name for a class: it is a path'
        'create-unit-test'        | 'a..Book'                     | 'a. is not a package of Java'
        'create-unit-test'        | '.Book'                       | 'it starts or ends with a dot'
        'create-unit-test'        | 'a.b.'                        | 'it starts or ends with a dot'
        'create-unit-test'        | 'a b'                         | 'is not an identifier of Java'
        'create-unit-test'        | '1Book'                       | '1Book is not an identifier of Java'
        'create-unit-test'        | 'a.class.Book'                | 'a.class is not a package of Java'
        'create-unit-test'        | 'aux.Book'                    | 'aux is the name of a device on Windows'
        'create-unit-test'        | 'Bo\u0007ok'                  | 'it has a character that is not shown'
        'create-unit-test'        | 'B' * 201                     | 'it has more than 200 characters'
        'create-unit-test'        | 'Book@|red x|@'               | 'is not a name for a class'
        'create-integration-test' | '../Escape'                   | 'is not a name for a class: it is a path'
        'create-functional-test'  | 'a/Book'                      | 'is not a name for a class: it is a path'
        'create-restful-controller' | '../Book'                   | 'is not a name for a class: it is a path'
        'create-domain-resource'  | 'con'                         | 'Con is the name of a device on Windows'
        'create-domain-resource'  | 'null.Book'                   | 'null is not a package of Java'
        'create-command'          | '../Escape'                   | 'is not a name for a class: it is a path'
        'create-script'           | '../escape'                   | 'is not a name for a script'
        'create-script'           | 'a.b'                         | 'is not a name for a script: a script has no package'
        'create-script'           | 'import.groovy'               | 'its name is given without .groovy'
        'create-script'           | 'a/b'                         | 'is not a name for a class: it is a path'
        'create-script'           | 'nul'                         | 'Nul is the name of a device on Windows'
    }

    void "a class without a package, in a project whose record names none"() {
        given:
        ConsoleOutput console = Mock(ConsoleOutput)
        CreateUnitTestCommand command = new CreateUnitTestCommand(new CodeGenConfig(), getOutputHandler(console), console)
        command.testName = 'Book'

        when:
        Integer exit = command.call()

        then:
        exit == 0
        new File(dir, 'src/test/groovy/BookSpec.groovy').text.startsWith('import org.grails.testing.GrailsUnitTest\n')
        1 * console.out('@|blue ||@ Rendered unit test to src/test/groovy/BookSpec.groovy')
    }

    @Unroll
    void "#command writes from the template #location of the project"() {
        given:
        project(type, features as String[])
        template(location, source)

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, command, 'org.shop.bookStore') == 0
        }

        then:
        written[file] == expected
        out.toString().contains("| Using the template src/main/templates/${location} of the project")

        and: 'the other file of the command is written from the template of the command line'
        written.size() == files

        where:
        command                     | location                             | type                     | features          | file                                                  | files
        'create-controller'         | 'artifacts/Controller.groovy'        | ApplicationType.WEB      | []                | 'grails-app/controllers/org/shop/BookStoreController.groovy'     | 2
        'create-controller'         | 'testing/Controller.groovy'          | ApplicationType.WEB      | []                | 'src/test/groovy/org/shop/BookStoreControllerSpec.groovy'        | 2
        'create-service'            | 'artifacts/Service.groovy'           | ApplicationType.WEB      | []                | 'grails-app/services/org/shop/BookStoreService.groovy'           | 2
        'create-service'            | 'testing/Service.groovy'             | ApplicationType.WEB      | []                | 'src/test/groovy/org/shop/BookStoreServiceSpec.groovy'           | 2
        'create-domain-class'       | 'artifacts/DomainClass.groovy'       | ApplicationType.WEB      | []                | 'grails-app/domain/org/shop/BookStore.groovy'                    | 2
        'create-domain-class'       | 'testing/DomainClass.groovy'         | ApplicationType.WEB      | []                | 'src/test/groovy/org/shop/BookStoreSpec.groovy'                  | 2
        'create-taglib'             | 'artifacts/TagLib.groovy'            | ApplicationType.WEB      | []                | 'grails-app/taglib/org/shop/BookStoreTagLib.groovy'              | 2
        'create-taglib'             | 'testing/TagLib.groovy'              | ApplicationType.WEB      | []                | 'src/test/groovy/org/shop/BookStoreTagLibSpec.groovy'            | 2
        'create-interceptor'        | 'artifacts/Interceptor.groovy'       | ApplicationType.WEB      | []                | 'grails-app/controllers/org/shop/BookStoreInterceptor.groovy'    | 2
        'create-interceptor'        | 'testing/Interceptor.groovy'         | ApplicationType.WEB      | []                | 'src/test/groovy/org/shop/BookStoreInterceptorSpec.groovy'       | 2
        'create-job'                | 'Job.groovy'                         | ApplicationType.WEB      | ['grails-quartz'] | 'grails-app/jobs/org/shop/BookStoreJob.groovy'                   | 1
        'create-unit-test'          | 'testing/Generic.groovy'             | ApplicationType.WEB      | []                | 'src/test/groovy/org/shop/BookStoreSpec.groovy'                  | 1
        'create-integration-test'   | 'testing/Integration.groovy'         | ApplicationType.WEB      | []                | 'src/integration-test/groovy/org/shop/BookStoreSpec.groovy'      | 1
        'create-functional-test'    | 'testing/Functional.groovy'          | ApplicationType.REST_API | []                | 'src/integration-test/groovy/org/shop/BookStoreSpec.groovy'      | 1
        'create-restful-controller' | 'artifacts/RestfulController.groovy' | ApplicationType.REST_API | []                | 'grails-app/controllers/org/shop/BookStoreController.groovy'     | 1
        'create-domain-resource'    | 'artifacts/Resource.groovy'          | ApplicationType.REST_API | []                | 'grails-app/domain/org/shop/BookStore.groovy'                    | 2
        'create-domain-resource'    | 'testing/Resource.groovy'            | ApplicationType.REST_API | []                | 'src/test/groovy/org/shop/BookStoreSpec.groovy'                  | 2
        'create-command'            | 'artifacts/Command.groovy'           | ApplicationType.WEB      | []                | 'grails-app/commands/org/shop/BookStoreCommand.groovy'           | 1

        source = location == 'Job.groovy' ? '// ours ${packageName} ${packagePath} ${className} ${propertyName} ${lowerCaseName} ${fullName} ${simpleName} ${modelName} \\$x\n' :
                '// ours\n@artifact.package@\n// @artifact.package.name@ @artifact.package.path@\nclass @artifact.name@Thing { def @artifact.propertyName@ = "$name ${it} <% %> @other@ \\$" }\n'
        expected = location == 'Job.groovy' ? '// ours org.shop org/shop BookStore bookStore book-store org.shop.BookStore BookStore bookStore $x\n' :
                '// ours\npackage org.shop\n\n// org.shop org/shop\nclass BookStoreThing { def bookStore = "$name ${it} <% %> @other@ \\$" }\n'
    }

    void "the templates that are Groovy are run, with the names the scripts gave them"() {
        given:
        project(ApplicationType.WEB, 'grails-quartz')
        String source = '<% def twice = className * 2 %>// ${packageName} ${packagePath} ${className} ${propertyName} ${lowerCaseName} ${fullName} ${simpleName} ${modelName} \\$x <%= twice %>\n'
        template('Job.groovy', source)
        template('artifacts/Script.groovy', source)

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-job', 'org.shop.bookStore') == 0
            assert ForgeCli.execute(beanContext, 'create-script', 'book-store') == 0
        }

        then:
        written == [
                'grails-app/jobs/org/shop/BookStoreJob.groovy': '// org.shop org/shop BookStore bookStore book-store org.shop.BookStore BookStore bookStore $x BookStoreBookStore\n',
                'src/main/scripts/book-store.groovy': '// example.grails example/grails BookStore bookStore book-store example.grails.BookStore BookStore bookStore $x BookStoreBookStore\n'
        ]
    }

    void "the templates of a profile and of the Quartz plugin are read as they are"() {
        given:
        project(ApplicationType.WEB, 'grails-quartz')
        template('artifacts/Controller.groovy', PROFILE_CONTROLLER)
        template('Job.groovy', QUARTZ_JOB)

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-controller', 'Book') == 0
            assert ForgeCli.execute(beanContext, 'create-job', 'Cleanup') == 0
        }

        then:
        written['grails-app/controllers/example/grails/BookController.groovy'] == 'package example.grails\n\nclass BookController {\n\n    def index() { }\n}\n'
        written['grails-app/jobs/example/grails/CleanupJob.groovy'] == QUARTZ_JOB.replace('${packageName}', 'example.grails').replace('${className}', 'Cleanup')
    }

    void "a template with line ends of Windows keeps them"() {
        given:
        project(ApplicationType.WEB)
        template('testing/Generic.groovy', '@artifact.package@\r\nclass @artifact.name@Spec {\r\n}\r\n')

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-unit-test', 'Book') == 0
        }

        then:
        written['src/test/groovy/example/grails/BookSpec.groovy'] == 'package example.grails\r\n\r\nclass BookSpec {\r\n}\r\n'
    }

    @Unroll
    void "a template of the project that cannot be rendered is said with its line: #says"() {
        given:
        project(ApplicationType.REST_API, 'grails-quartz')
        template(location, source)

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, command, 'Book')
        }

        then: 'a failure in one line, and no file of the command is written, the one of the other template neither'
        exit == 1
        written == [:]
        out.toString().readLines().findAll { it.contains('Error') } == ["| Error The template src/main/templates/${location} cannot be rendered: ${says}".toString()]
        !out.toString().contains('\tat ')

        where:
        command             | location                    | source                                                                    | says
        'create-domain-resource' | 'testing/Resource.groovy' | '@artifact.package@\n\nclass @artifact.nme@Spec {\n}\n' | 'line 3: there is no @artifact.nme@; a template has @artifact.package@, @artifact.package.name@, @artifact.package.path@, @artifact.name@, @artifact.propertyName@'
        'create-controller'      | 'artifacts/Controller.groovy' | 'class X {\n@artifact.@ @artifact.name@\n}\n'         | 'line 2: there is no @artifact.@; a template has @artifact.package@, @artifact.package.name@, @artifact.package.path@, @artifact.name@, @artifact.propertyName@'
        'create-job'             | 'Job.groovy'                | 'package ${packageName}\n\n<% def x = = 2 %>\nclass A {\n}\n' | "line 3: it does not compile: Unexpected input: '='"
        'create-job'             | 'Job.groovy'                | 'package ${packageName}\n\nclass ${className Job {\n}\n' | 'it does not compile: Unclosed string literal'
        'create-job'             | 'Job.groovy'                | 'package ${packageName}\n\n\n<% throw new IllegalStateException("no\\n@|red job|@") %>\n' | 'line 4: no @ |red job|@'
        'create-job'             | 'Job.groovy'                | 'a\nb\n<% %>c<% def x = 1\n %>\n${[].first()}\n'                     | 'line 5: Cannot access first() element from an empty List'
        'create-job'             | 'Job.groovy'                | 'package ${packageName}\nclass ${classNme}Job {\n}\n'    | "line 2: it has no 'classNme'; a template has className, fullName, propertyName, modelName, packageName, packagePath, simpleName, lowerCaseName"
        'create-script'          | 'artifacts/Script.groovy'   | 'println "${artifact.name}"\n'                          | "line 1: it has no 'artifact'; a template has className, fullName, propertyName, modelName, packageName, packagePath, simpleName, lowerCaseName"
    }

    @Unroll
    void "a template of the project that is #what is refused"() {
        given:
        project(ApplicationType.WEB)
        File file = new File(dir, 'src/main/templates/testing/Generic.groovy')
        file.parentFile.mkdirs()
        make(file)

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, 'create-unit-test', 'Book')
        }

        then:
        exit == 1
        written == [:]
        out.toString().readLines() == ["| Error The template src/main/templates/testing/Generic.groovy ${says}".toString()]

        where:
        what          | make                                                                  | says
        'too large'   | { File f -> f.bytes = new byte[256 * 1024 + 1] }                      | 'is not used: it has more than the 262144 bytes a template can have'
        'no text'     | { File f -> f.bytes = [0x63, 0xC3, 0x28] as byte[] }                  | 'is not used: it is not text in UTF-8'
        'a directory' | { File f -> f.mkdirs() }                                              | 'is not used: it is not a file'
    }

    @IgnoreIf({ os.windows })
    void "a template that is a link out of the project is refused, and one inside it is used"() {
        given:
        project(ApplicationType.WEB)
        File elsewhere = new File(outside, 'Generic.groovy')
        elsewhere.text = 'class @artifact.name@Stolen {}\n'
        File file = new File(dir, 'src/main/templates/testing/Generic.groovy')
        file.parentFile.mkdirs()
        Files.createSymbolicLink(file.toPath(), elsewhere.toPath())

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, 'create-unit-test', 'Book')
        }

        then:
        exit == 1
        written == [:]
        out.toString().readLines() == ['| Error The template src/main/templates/testing/Generic.groovy is not used: it is a link, or below one, that leads out of the project']

        when: 'the directory of the templates is the link'
        out.reset()
        new File(dir, 'src/main/templates').deleteDir()
        new File(outside, 'testing').mkdirs()
        new File(outside, 'testing/Generic.groovy').text = elsewhere.text
        Files.createSymbolicLink(new File(dir, 'src/main/templates').toPath(), outside.toPath())
        exit = ForgeCli.execute(beanContext, 'create-unit-test', 'Book')

        then:
        exit == 1
        !new File(dir, 'src/test').exists()
        out.toString().readLines() == ['| Error The template src/main/templates/testing/Generic.groovy is not used: it is a link, or below one, that leads out of the project']

        when: 'a link that leads nowhere'
        out.reset()
        Files.delete(new File(dir, 'src/main/templates').toPath())
        file.parentFile.mkdirs()
        Files.createSymbolicLink(file.toPath(), new File(outside, 'none').toPath())
        exit = ForgeCli.execute(beanContext, 'create-unit-test', 'Book')

        then:
        exit == 1
        !new File(dir, 'src/test').exists()
        out.toString().readLines() == ['| Error The template src/main/templates/testing/Generic.groovy cannot be read (NoSuchFileException)']

        when: 'a link to a file of the project'
        out.reset()
        Files.delete(file.toPath())
        new File(dir, 'shared.txt').text = 'class @artifact.name@Shared {}\n'
        Files.createSymbolicLink(file.toPath(), new File(dir, 'shared.txt').toPath())
        exit = ForgeCli.execute(beanContext, 'create-unit-test', 'Book')

        then:
        exit == 0
        new File(dir, 'src/test/groovy/example/grails/BookSpec.groovy').text == 'class BookShared {}\n'
    }

    void "install-templates copies the templates of the commands the project has, which then write the same"() {
        given:
        project(ApplicationType.WEB, 'grails-quartz')

        when:
        Map<String, String> installed = writtenBy {
            assert ForgeCli.execute(beanContext, 'install-templates') == 0
        }

        then:
        installed.keySet() == [
                'Job.groovy', 'artifacts/Command.groovy', 'artifacts/Controller.groovy', 'artifacts/DomainClass.groovy', 'artifacts/Interceptor.groovy',
                'artifacts/Script.groovy', 'artifacts/Service.groovy', 'artifacts/TagLib.groovy', 'testing/Controller.groovy', 'testing/DomainClass.groovy',
                'testing/Generic.groovy', 'testing/Integration.groovy', 'testing/Interceptor.groovy', 'testing/Service.groovy', 'testing/TagLib.groovy'
        ].collect { 'src/main/templates/' + it } as Set
        out.toString().readLines() == installed.keySet().sort().collect { '| Copied template to ' + it }
        installed['src/main/templates/artifacts/Controller.groovy'] == '@artifact.package@\n\nclass @artifact.name@Controller {\n\n    def index() {\n\n    }\n}'

        when: 'the commands read them from there'
        out.reset()
        Map<String, String> written = writtenBy {
            ['create-controller', 'create-service', 'create-domain-class', 'create-taglib', 'create-interceptor', 'create-job'].each { String command ->
                assert ForgeCli.execute(beanContext, command, 'fooBar') == 0
                assert ForgeCli.execute(beanContext, command, 'other.pkg.Baz') == 0
            }
        }

        then:
        written == golden('existing')
        out.toString().readLines().count { it.startsWith('| Using the template src/main/templates/') } == 22

        when:
        out.reset()
        written = writtenBy {
            ['create-unit-test', 'create-integration-test', 'create-command', 'create-script'].each { String command ->
                assert ForgeCli.execute(beanContext, command, 'again') == 0
            }
        }

        then:
        out.toString().readLines().count { it.startsWith('| Using the template src/main/templates/') } == 4
        written['src/test/groovy/example/grails/AgainSpec.groovy'] == golden('web')['src/test/groovy/example/grails/FooBarSpec.groovy'].replace('FooBar', 'Again')
    }

    void "install-templates keeps a template the project has, unless it is forced"() {
        given:
        project(ApplicationType.REST_API)
        template('artifacts/Resource.groovy', 'ours')
        template('testing/Resource.groovy', 'ours too')

        when:
        Map<String, String> installed = writtenBy {
            assert ForgeCli.execute(beanContext, 'install-templates') == 0
        }

        then: 'the templates of a REST API application, without the two that are there'
        installed.keySet() == [
                'artifacts/Command.groovy', 'artifacts/Controller.groovy', 'artifacts/DomainClass.groovy', 'artifacts/Interceptor.groovy',
                'artifacts/RestfulController.groovy', 'artifacts/Script.groovy', 'artifacts/Service.groovy', 'artifacts/TagLib.groovy',
                'testing/Controller.groovy', 'testing/DomainClass.groovy', 'testing/Functional.groovy', 'testing/Generic.groovy', 'testing/Integration.groovy',
                'testing/Interceptor.groovy', 'testing/Service.groovy', 'testing/TagLib.groovy'
        ].collect { 'src/main/templates/' + it } as Set
        new File(dir, 'src/main/templates/artifacts/Resource.groovy').text == 'ours'
        out.toString().readLines().findAll { !it.startsWith('| Copied') } == [
                '| Warning Kept src/main/templates/artifacts/Resource.groovy, which the project has already',
                '| Warning Kept src/main/templates/testing/Resource.groovy, which the project has already',
                '| Run again with -f to replace the 2 templates that were kept'
        ]

        when:
        out.reset()
        installed = writtenBy {
            assert ForgeCli.execute(beanContext, 'install-templates', '--force') == 0
        }

        then: 'all of them are written, and those that were the same are the same'
        installed.keySet() == ['src/main/templates/artifacts/Resource.groovy', 'src/main/templates/testing/Resource.groovy'] as Set
        installed['src/main/templates/artifacts/Resource.groovy'].contains('@Resource(readOnly = false')
        out.toString().readLines().size() == 18
        out.toString().readLines().every { it.startsWith('| Copied template to src/main/templates/') }
    }

    private void project(ApplicationType type, String... features) {
        if (type == null) {
            return
        }
        String key = type.name() + features.toList()
        if (!records.containsKey(key)) {
            File generated = Files.createTempDirectory('grailsforgerecord').toFile()
            beanContext.getBean(ProjectGenerator).generate(type, NameUtils.parse('example.grails.foo'), new Options(), OperatingSystem.MACOS_ARCH64,
                    features.toList(), new FileSystemOutputHandler(generated, ConsoleOutput.NOOP), ConsoleOutput.NOOP)
            records[key] = generated
        }
        // the record of the project, as it was written when the project was generated, and nothing else of it
        File record = records[key]
        new File(record, '.grails').eachFileRecurse { File each ->
            if (each.file) {
                File copy = new File(dir, record.toPath().relativize(each.toPath()).toString())
                copy.parentFile.mkdirs()
                copy.bytes = each.bytes
            }
        }
        new File(dir, 'grails-forge-cli.yml').bytes = new File(record, 'grails-forge-cli.yml').bytes
    }

    private void template(String location, String source) {
        File file = new File(dir, 'src/main/templates/' + location)
        file.parentFile.mkdirs()
        file.setText(source, 'UTF-8')
    }

    private Map<String, String> files() {
        Map<String, String> files = new TreeMap<>()
        dir.eachFileRecurse { File each ->
            String path = dir.toPath().relativize(each.toPath()).toString().replace(File.separator, '/')
            if (each.file && !path.startsWith('.grails/') && path != 'grails-forge-cli.yml') {
                files[path] = each.getText('UTF-8')
            }
        }
        return files
    }

    // the files that are new or not what they were, by their path
    private Map<String, String> writtenBy(Closure<?> run) {
        Map<String, String> before = files()
        run.call()
        return files().findAll { String path, String text -> before[path] != text }
    }

    private Map<String, String> golden(String set) {
        File root = new File(getClass().getResource("/golden/artefacts/${set}").toURI())
        Map<String, String> files = new TreeMap<>()
        root.eachFileRecurse { File each ->
            if (each.file) {
                files[root.toPath().relativize(each.toPath()).toString().replace(File.separator, '/')] = each.getText('UTF-8')
            }
        }
        return files
    }
}
