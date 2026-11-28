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

import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Function
import org.apache.grails.forge.cli.artefact.ArtefactTemplate
import org.apache.grails.forge.cli.artefact.ArtefactTemplateException
import org.apache.grails.forge.cli.artefact.ArtefactTemplates
import org.apache.grails.forge.cli.artefact.ProjectFiles
import org.apache.grails.forge.cli.artefact.ProjectOutputHandler
import org.apache.grails.forge.cli.artefact.ProjectPlace
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
import org.grails.forge.io.OutputHandler
import org.grails.forge.options.Options
import org.grails.forge.template.Template
import org.grails.forge.util.NameUtils
import spock.lang.IgnoreIf
import spock.lang.Requires
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

    static final List<String> ALL_COMMANDS = ['create-controller', 'create-service', 'create-domain-class', 'create-taglib', 'create-interceptor', 'create-job',
                                              'create-unit-test', 'create-integration-test', 'create-functional-test', 'create-restful-controller',
                                              'create-domain-resource', 'create-tool']
    // the commands that would write a file another command writes are given a name of their own
    static final Map<String, String> OWN_NAMES = ['create-unit-test': 'Unit', 'create-integration-test': 'Flow', 'create-functional-test': 'Api',
                                                  'create-restful-controller': 'Rest', 'create-domain-resource': 'Res']
    // what the templates of the command line import, each a class of the framework, of Groovy, of Spock or of Java
    static final List<String> KNOWN_CLASSES = [
            'grails.gorm.transactions.Rollback', 'grails.rest.Resource', 'grails.rest.RestfulController', 'grails.testing.gorm.DomainUnitTest',
            'grails.testing.mixin.integration.Integration', 'grails.testing.services.ServiceUnitTest', 'grails.testing.web.controllers.ControllerUnitTest',
            'grails.testing.web.interceptor.InterceptorUnitTest', 'grails.testing.web.taglib.TagLibUnitTest', 'groovy.transform.CompileStatic',
            'java.net.http.HttpClient', 'java.net.http.HttpRequest', 'java.net.http.HttpResponse', 'org.apache.grails.core.cli.ApplicationTool',
            'org.apache.grails.core.cli.StandaloneTool', 'org.apache.grails.core.cli.ToolHints', 'org.apache.grails.core.cli.ToolParameter',
            'org.apache.grails.core.cli.ToolSupport',
            'org.grails.testing.GrailsUnitTest', 'spock.lang.Specification']
    static final String NO_EXPRESSION = 'between ${ and } there can only be one of the names className, fullName, propertyName, modelName, packageName, packagePath, ' +
            'simpleName, lowerCaseName; a $ that is to be written is \\$. Templates are not run'
    // a class whose file has a name of 253 bytes, and whose test has one of 257
    static final String LONG = 'u.' + (((char) 0x4E66) as String) * 82

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
            assert ForgeCli.execute(beanContext, 'create-tool', 'fooBar') == 0
            assert ForgeCli.execute(beanContext, 'create-tool', 'other.pkg.Baz') == 0
            assert ForgeCli.execute(beanContext, 'create-tool', '--standalone', 'render') == 0
        }

        then:
        written == golden('web')
        out.toString().readLines() == [
                '| Rendered unit test to src/test/groovy/example/grails/FooBarSpec.groovy',
                '| Rendered unit test to src/test/groovy/other/pkg/BazSpec.groovy',
                '| Rendered integration test to src/integration-test/groovy/example/grails/FooBarFlowSpec.groovy',
                '| Rendered integration test to src/integration-test/groovy/other/pkg/BazFlowSpec.groovy',
                '| Rendered tool to src/cli/groovy/example/grails/FooBarTool.groovy',
                '| Run it with: grails-forge-cli foo-bar',
                '| Rendered tool to src/cli/groovy/other/pkg/BazTool.groovy',
                '| Run it with: grails-forge-cli baz',
                '| Rendered tool to src/standalone/groovy/example/grails/RenderTool.groovy',
                '| Run it with: grails-forge-cli render'
        ]
    }

    void "the tools of a plugin given without a package are in packages of their own, one for each companion"() {
        given:
        project(ApplicationType.PLUGIN)

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-tool', 'fooBar') == 0
            assert ForgeCli.execute(beanContext, 'create-tool', 'other.pkg.Baz') == 0
            assert ForgeCli.execute(beanContext, 'create-tool', '--standalone', 'render') == 0
        }

        then: 'the same classes as in an application, a tool of src/cli in <package>.cli and a standalone tool in <package>.cli.standalone'
        written == golden('plugin')
        out.toString().readLines() == [
                '| Rendered tool to src/cli/groovy/example/grails/cli/FooBarTool.groovy',
                '| Run it with: grails-forge-cli foo-bar',
                '| Rendered tool to src/cli/groovy/other/pkg/BazTool.groovy',
                '| Run it with: grails-forge-cli baz',
                '| Rendered tool to src/standalone/groovy/example/grails/cli/standalone/RenderTool.groovy',
                '| Run it with: grails-forge-cli render'
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
    void "what is written in #set is Groovy, and imports classes there are"() {
        expect:
        golden(set).findAll { String path, String text -> path.endsWith('.groovy') }.each { String path, String text ->
            CompilationUnit unit = new CompilationUnit()
            unit.addSource(path, text)
            unit.compile(Phases.CONVERSION)
            // the sources are not compiled against the framework here: a class they import is one the framework is known to have
            text.readLines().findAll { it.startsWith('import ') }.each { String line ->
                String imported = line.substring('import '.length())
                assert imported in KNOWN_CLASSES
                assert text.replace(line, '').contains(imported.substring(imported.lastIndexOf('.') + 1))
            }
        }

        where:
        set << ['existing', 'web', 'rest-api', 'plugin']
    }

    @Unroll
    void "every file a command writes in a #type project ends with one line break, and so does every template of the command line"() {
        given:
        project(type, features as String[])

        when:
        Map<String, String> written = writtenBy {
            ALL_COMMANDS.each { String command ->
                ForgeCli.execute(beanContext, command, 'fooBar' + (OWN_NAMES[command] ?: ''), '-f')
                ForgeCli.execute(beanContext, command, 'other.pkg.Baz' + (OWN_NAMES[command] ?: ''), '-f')
            }
        }

        then:
        written.size() == files
        written.each { String path, String text ->
            assert text.endsWith('}\n'): path
            assert !text.contains('\r'): path
        }

        and:
        ArtefactTemplate.values().each { ArtefactTemplate template ->
            assert ArtefactTemplates.builtIn(template).endsWith('}\n'): template.resource
        }

        where:
        type                     | features          | files
        ApplicationType.WEB      | ['grails-quartz'] | 30
        ApplicationType.REST_API | []                | 34
    }

    void "the templates of the command line are resources on its class path, one of each"() {
        expect:
        ArtefactTemplate.values().each { ArtefactTemplate template ->
            List<URL> found = Collections.list(Thread.currentThread().getContextClassLoader().getResources('META-INF/grails-forge-cli/templates/' + template.resource))
            assert found.size() == 1
            assert !found[0].toString().contains('/src/main/')
            assert found[0].getText('UTF-8') == ArtefactTemplates.builtIn(template)
        }
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
        'create-functional-test'    | ApplicationType.REST_API_PLUGIN
        'create-restful-controller' | ApplicationType.REST_API_PLUGIN
        'create-domain-resource'    | ApplicationType.REST_API_PLUGIN
        'create-integration-test'   | ApplicationType.REST_API_PLUGIN
        'create-integration-test'   | ApplicationType.WEB_PLUGIN
        'create-unit-test'          | ApplicationType.PLUGIN
        'create-tool'               | ApplicationType.PLUGIN
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
        'create-functional-test'    | ApplicationType.PLUGIN
        'create-restful-controller' | ApplicationType.WEB
        'create-domain-resource'    | ApplicationType.WEB_PLUGIN
        'create-integration-test'   | ApplicationType.PLUGIN
        'create-script'             | ApplicationType.WEB
        'create-script'             | ApplicationType.PLUGIN
    }

    void "a command that is given an option it does not have says so, and not that its name is missing"() {
        given:
        project(ApplicationType.WEB)

        when:
        int exit = ForgeCli.execute(beanContext, 'create-controller', '--nonsense-option')

        then:
        exit == 2
        out.toString().readLines().size() == 3
        out.toString().readLines()[0] == 'create-controller has no option --nonsense-option'
        out.toString().readLines()[1].startsWith('Usage: grails-forge-cli create-controller ')
        out.toString().readLines()[1].endsWith(' CONTROLLER-NAME')
        out.toString().readLines()[2] == "Run 'grails-forge-cli help create-controller' for the options"

        when: 'without anything, what is missing is what is wrong'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'create-controller')

        then:
        exit == 2
        out.toString().readLines()[0] == "Missing required parameter: 'CONTROLLER-NAME'"

        when: 'a command names the command line by the name it was started under'
        out.reset()
        exit = ForgeCli.execute(beanContext, 'add-property', 'Nope', 'title:String')

        then:
        exit == 1
        out.toString().contains("Run 'grails-forge-cli create-domain-class Nope' first.")
        !out.toString().contains("'grails create-domain-class")
    }

    void "in a directory without the record of a project the commands write nothing, and say that they are commands of a project"() {
        expect:
        ForgeCli.execute(beanContext, (command == 'install-templates' ? [command] : [command, 'Book']) as String[]) == 1
        out.toString().readLines() == ["| Error ${command} is a command of a Grails project; there is none in ${dir.canonicalPath} or above it".toString()]
        dir.list().length == 0

        where:
        command << ['create-unit-test', 'create-tool', 'install-templates']
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
        'create-tool'               | 'ImportTool'           | 'src/cli/groovy/example/grails/ImportTool.groovy'               | 'class ImportTool implements'
        'create-tool'               | 'data-import'          | 'src/cli/groovy/example/grails/DataImportTool.groovy'           | 'class DataImportTool implements'
        'create-unit-test'          | (((char) 0xE9) as String) + 'lan' | 'src/test/groovy/example/grails/' + (((char) 0xC9) as String) + 'lanSpec.groovy' | 'Spec extends'
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
        'create-unit-test'        | 'Book.groovy'                 | 'it is the name of a file. The name of the class is given, without .groovy'
        'create-unit-test'        | 'a.Book.java'                 | 'it is the name of a file'
        'create-unit-test'        | (((char) 0xDF) as String) + 'ook' | 'starts with a letter that has no capital'
        'create-unit-test'        | 'book--store'                 | 'a hyphen stands between two words of a name'
        'create-unit-test'        | 'Book-'                       | 'a hyphen stands between two words of a name'
        'create-unit-test'        | '-Book'                       | ''
        'create-integration-test' | '../Escape'                   | 'is not a name for a class: it is a path'
        'create-functional-test'  | 'a/Book'                      | 'is not a name for a class: it is a path'
        'create-restful-controller' | '../Book'                   | 'is not a name for a class: it is a path'
        'create-domain-resource'  | 'con'                         | 'Con is the name of a device on Windows'
        'create-domain-resource'  | 'null.Book'                   | 'null is not a package of Java'
        'create-tool'             | '../Escape'                   | 'is not a name for a class: it is a path'
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
        'create-tool'               | 'artifacts/Tool.groovy'              | ApplicationType.WEB      | []                | 'src/cli/groovy/org/shop/BookStoreTool.groovy'               | 1

        source = location == 'Job.groovy' ? '// ours ${packageName} ${packagePath} ${className} ${propertyName} ${lowerCaseName} ${fullName} ${simpleName} ${modelName} \\$x\n' :
                '// ours\n@artifact.package@\n// @artifact.package.name@ @artifact.package.path@\nclass @artifact.name@Thing { def @artifact.propertyName@ = "$name ${it} <% %> @other@ \\$" }\n'
        expected = location == 'Job.groovy' ? '// ours org.shop org/shop BookStore bookStore book-store org.shop.BookStore BookStore bookStore $x\n' :
                '// ours\npackage org.shop\n\n// org.shop org/shop\nclass BookStoreThing { def bookStore = "$name ${it} <% %> @other@ \\$" }\n'
    }

    void "the template of a job names what it wants in braces"() {
        given:
        project(ApplicationType.WEB, 'grails-quartz')
        template('Job.groovy', '// ${packageName} ${packagePath} ${className} ${propertyName} ${lowerCaseName} ${fullName} ${simpleName} ${modelName} \\$x \\\\ $ 5 $\n')

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-job', 'org.shop.bookStore') == 0
        }

        then:
        written == ['grails-app/jobs/org/shop/BookStoreJob.groovy': '// org.shop org/shop BookStore bookStore book-store org.shop.BookStore BookStore bookStore $x \\ $ 5 $\n']
    }

    void "a job whose name is given with Job at its end is the job of the name without it, as the script of the Quartz plugin had it"() {
        given:
        project(ApplicationType.WEB, 'grails-quartz')

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-job', given) == 0
        }

        then:
        written.keySet() == [file] as Set
        written[file].contains('package org.shop\n')
        written[file].contains("class ${name}Job {")

        where:
        given                 | file                                           | name
        'org.shop.NightlyJob' | 'grails-app/jobs/org/shop/NightlyJob.groovy'   | 'Nightly'
        'org.shop.Nightly'    | 'grails-app/jobs/org/shop/NightlyJob.groovy'   | 'Nightly'
        'org.shop.JobRunner'  | 'grails-app/jobs/org/shop/JobRunnerJob.groovy' | 'JobRunner'
        'org.shop.Job'        | 'grails-app/jobs/org/shop/JobJob.groovy'       | 'Job'
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
    void "a template of the project that cannot be rendered is said with its line, and nothing of it is run: #says"() {
        given:
        project(ApplicationType.REST_API, 'grails-quartz')
        template(location, source.replace('OUTSIDE', outside.absolutePath))

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, command, 'Book')
        }

        then: 'a failure in one line, and no file of the command is written, the one of the other template neither'
        exit == 1
        written == [:]
        outside.list().length == 0
        out.toString().readLines().findAll { it.contains('Error') } == ["| Error The template src/main/templates/${location} cannot be rendered: ${says}".toString()]
        !out.toString().contains('\tat ')

        where:
        command                  | location                      | source                                                | says
        'create-domain-resource' | 'testing/Resource.groovy'     | '@artifact.package@\n\nclass @artifact.nme@Spec {\n}\n' | 'line 3: there is no @artifact.nme@; a template has @artifact.package@, @artifact.package.name@, @artifact.package.path@, @artifact.name@, @artifact.propertyName@'
        'create-controller'      | 'artifacts/Controller.groovy' | 'class X {\n@artifact.@ @artifact.name@\n}\n'           | 'line 2: there is no @artifact.@; a template has @artifact.package@, @artifact.package.name@, @artifact.package.path@, @artifact.name@, @artifact.propertyName@'
        'create-controller'      | 'testing/Controller.groovy'   | 'class X {\n@artifact.@ @artifact.name@\n}\n'           | 'line 2: there is no @artifact.@; a template has @artifact.package@, @artifact.package.name@, @artifact.package.path@, @artifact.name@, @artifact.propertyName@'
        'create-job'             | 'Job.groovy'                  | 'a\n<% new File("OUTSIDE/ran").text = "x" %>\n'         | 'line 2: it has code between <% and %>. Templates are not run'
        'create-job'             | 'Job.groovy'                  | '<%= new File("OUTSIDE/ran").text = "x" %>'             | 'line 1: it has code between <% and %>. Templates are not run'
        'create-job'             | 'Job.groovy'                  | 'a\rb\r<% x %>'                                         | 'line 3: it has code between <% and %>. Templates are not run'
        'create-job'             | 'Job.groovy'                  | 'a\n\n${new File("OUTSIDE/ran").text = "x"}\n'          | 'line 3: ' + NO_EXPRESSION
        'create-job'             | 'Job.groovy'                  | 'class ${className.toLowerCase()}Job {}'                | 'line 1: ' + NO_EXPRESSION
        'create-job'             | 'Job.groovy'                  | 'class ${ className }Job {}'                            | 'line 1: ' + NO_EXPRESSION
        'create-job'             | 'Job.groovy'                  | 'a\nclass $className {}'                                | 'line 2: a name is written in braces, as in ${className}, and a $ that is to be written is \\$. Templates are not run'
        'create-job'             | 'Job.groovy'                  | 'a\\tb'                                                 | 'line 1: a backslash is written \\\\, and stands otherwise only in front of a $ that is to be written. Templates are not run'
        'create-job'             | 'Job.groovy'                  | 'package ${packageName}\nclass ${classNme}Job {\n}\n'   | "line 2: it has no 'classNme'; a template has className, fullName, propertyName, modelName, packageName, packagePath, simpleName, lowerCaseName"
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
        'with a NUL'  | { File f -> f.bytes = [0x63, 0x0A, 0x00, 0x64] as byte[] }            | 'is not used: line 2: it has a NUL character, which no source file has'
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

    @Unroll
    void "install-templates copies the templates of the commands a #type project has, and every command then writes what it wrote without them"() {
        given:
        project(type, features as String[])
        Map<String, List<String>> names = ALL_COMMANDS.collectEntries { String command ->
            [command, ['fooBar' + (OWN_NAMES[command] ?: ''), 'other.pkg.Baz' + (OWN_NAMES[command] ?: '')]]
        }
        Set<String> has = [] as Set

        when: 'every command, with the templates of the command line'
        Map<String, String> before = writtenBy {
            names.each { String command, List<String> given ->
                given.each { String name ->
                    if (ForgeCli.execute(beanContext, command, name, '-f') == 0) {
                        has << command
                    }
                }
            }
        }
        new File(dir, 'src').deleteDir()
        new File(dir, 'grails-app').deleteDir()
        out.reset()
        Map<String, String> installed = writtenBy {
            assert ForgeCli.execute(beanContext, 'install-templates') == 0
        }

        then:
        has == (ALL_COMMANDS - without) as Set
        installed.keySet() == templates.collect { 'src/main/templates/' + it } as Set
        out.toString().readLines() == installed.keySet().sort().collect { '| Copied template to ' + it }
        installed['src/main/templates/artifacts/Controller.groovy'] == controller

        when: 'every command again, which now reads the templates of the project'
        out.reset()
        Map<String, String> after = writtenBy {
            names.findAll { it.key in has }.each { String command, List<String> given ->
                given.each { String name -> assert ForgeCli.execute(beanContext, command, name, '-f') == 0 }
            }
        }

        then: 'byte for byte what was written before, each file from a template of the project'
        after == before
        before.size() == files
        out.toString().readLines().count { it.startsWith('| Using the template src/main/templates/') } == files

        where:
        type                     | features          | without                                                                                     | files
        ApplicationType.WEB      | ['grails-quartz'] | ['create-restful-controller', 'create-domain-resource']                                     | 30
        ApplicationType.REST_API | []                | ['create-job']                                                                              | 34

        templates = (type == ApplicationType.WEB ? ['FunctionalSpec.groovy', 'Job.groovy'] : ['artifacts/Resource.groovy', 'artifacts/RestfulController.groovy', 'testing/Functional.groovy', 'testing/Resource.groovy']) + [
                'artifacts/StandaloneTool.groovy', 'artifacts/Tool.groovy', 'artifacts/Controller.groovy', 'artifacts/DomainClass.groovy', 'artifacts/Interceptor.groovy',
                'artifacts/Service.groovy', 'artifacts/TagLib.groovy', 'testing/Controller.groovy', 'testing/DomainClass.groovy',
                'testing/Generic.groovy', 'testing/Integration.groovy', 'testing/Interceptor.groovy', 'testing/Service.groovy', 'testing/TagLib.groovy']
        // the controller of a REST API project answers in JSON only, as the one of its profile did
        controller = type == ApplicationType.WEB ? '@artifact.package@\n\nclass @artifact.name@Controller {\n\n    def index() {\n\n    }\n}\n' :
                '@artifact.package@\nclass @artifact.name@Controller {\n\n    static responseFormats = [\'json\']\n\n    def index() {\n\n    }\n}\n'
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
                'artifacts/StandaloneTool.groovy', 'artifacts/Tool.groovy', 'artifacts/Controller.groovy', 'artifacts/DomainClass.groovy', 'artifacts/Interceptor.groovy',
                'artifacts/RestfulController.groovy', 'artifacts/Service.groovy', 'artifacts/TagLib.groovy',
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

    @Unroll
    void "#command writes none of its files when one of them is there"() {
        given:
        project(ApplicationType.WEB, 'grails-quartz')
        File there = new File(dir, kept)
        there.parentFile.mkdirs()
        there.text = 'mine'

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, command, 'Book')
        }

        then:
        exit == 1
        written == [:]
        there.text == 'mine'
        out.toString().readLines() == ["| Error ${kept} already exists. Run again with -f to overwrite.".toString()]

        when:
        out.reset()
        written = writtenBy {
            exit = ForgeCli.execute(beanContext, command, 'Book', '-f')
        }

        then:
        exit == 0
        written.size() == files
        there.text != 'mine'

        where:
        command               | kept                                                            | files
        'create-controller'   | 'src/test/groovy/example/grails/BookControllerSpec.groovy'      | 2
        'create-service'      | 'src/test/groovy/example/grails/BookServiceSpec.groovy'         | 2
        'create-domain-class' | 'src/test/groovy/example/grails/BookSpec.groovy'                | 2
        'create-taglib'       | 'src/test/groovy/example/grails/BookTagLibSpec.groovy'          | 2
        'create-interceptor'  | 'src/test/groovy/example/grails/BookInterceptorSpec.groovy'     | 2
        'create-job'          | 'grails-app/jobs/example/grails/BookJob.groovy'                 | 1
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "#command writes nothing through #link when the project has a link there, wherever the link leads"() {
        given:
        project(type, features as String[])
        File inside = new File(dir, 'elsewhere')
        inside.mkdirs()
        File at = new File(dir, link)
        at.parentFile.mkdirs()

        when: 'the link leads out of the project'
        Files.createSymbolicLink(at.toPath(), outside.toPath())
        int exit = ForgeCli.execute(beanContext, (([command] + arguments) as String[]))
        List<String> said = out.toString().readLines()

        then: 'the command fails, says which step is the link, and nothing is written, there or in the project'
        exit == 1
        outside.list().length == 0
        said.size() >= 1
        said.every { it.startsWith('| Error Cannot write ') && it.endsWith(": ${link} is a link, and a file of the project is not written through a link") }
        !said.any { it.contains(dir.name) || it.contains(outside.name) }
        dir.list().sort() == ([link.split('/')[0], '.grails', 'elsewhere'] as Set).sort()

        when: 'the link leads to a directory of the project'
        out.reset()
        Files.delete(at.toPath())
        Files.createSymbolicLink(at.toPath(), inside.toPath())
        exit = ForgeCli.execute(beanContext, (([command] + arguments) as String[]))

        then:
        exit == 1
        inside.list().length == 0
        out.toString().readLines().every { it.startsWith('| Error Cannot write ') && it.endsWith(": ${link} is a link, and a file of the project is not written through a link") }

        when: 'the link leads nowhere'
        out.reset()
        Files.delete(at.toPath())
        Files.createSymbolicLink(at.toPath(), new File(outside, 'none').toPath())
        exit = ForgeCli.execute(beanContext, (([command] + arguments) as String[]))

        then:
        exit == 1
        outside.list().length == 0
        out.toString().readLines().every { it.startsWith('| Error Cannot write ') && it.endsWith(": ${link} is a link, and a file of the project is not written through a link") }

        where:
        command                     | arguments | type                     | features          | link
        'create-controller'         | ['Book']  | ApplicationType.WEB      | []                | 'grails-app/controllers'
        'create-service'            | ['Book']  | ApplicationType.WEB      | []                | 'grails-app/services/example'
        'create-domain-class'       | ['Book']  | ApplicationType.WEB      | []                | 'grails-app/domain'
        'create-taglib'             | ['Book']  | ApplicationType.WEB      | []                | 'grails-app'
        'create-interceptor'        | ['Book']  | ApplicationType.WEB      | []                | 'src/test'
        'create-job'                | ['Book']  | ApplicationType.WEB      | ['grails-quartz'] | 'grails-app/jobs'
        'create-unit-test'          | ['Book']  | ApplicationType.WEB      | []                | 'src'
        'create-integration-test'   | ['Book']  | ApplicationType.WEB      | []                | 'src/integration-test/groovy/example/grails'
        'create-functional-test'    | ['Book']  | ApplicationType.REST_API | []                | 'src/integration-test'
        'create-restful-controller' | ['Book']  | ApplicationType.REST_API | []                | 'grails-app/controllers'
        'create-domain-resource'    | ['Book']  | ApplicationType.REST_API | []                | 'src/test/groovy'
        'create-tool'               | ['Book']  | ApplicationType.WEB      | []                | 'src/cli'
        'install-templates'         | []        | ApplicationType.WEB      | []                | 'src/main/templates'
        'install-templates'         | []        | ApplicationType.WEB      | []                | 'src'
        'install-templates'         | ['-f']    | ApplicationType.WEB      | []                | 'src/main'
    }

    @IgnoreIf({ os.windows })
    void "a repository cannot have a command write its own text outside the project"() {
        given: 'what a clone brings: a link where the commands are, and a template with the text'
        project(ApplicationType.WEB)
        new File(dir, 'src').mkdirs()
        Files.createSymbolicLink(new File(dir, 'src/cli').toPath(), outside.toPath())
        template('artifacts/Tool.groovy', 'anything at all\n')

        when:
        int exit = ForgeCli.execute(beanContext, 'create-tool', 'Deploy')

        then:
        exit == 1
        outside.list().length == 0
        out.toString().readLines() == ['| Error Cannot write src/cli/groovy/example/grails/DeployTool.groovy: src/cli is a link, ' +
                'and a file of the project is not written through a link']
    }

    @IgnoreIf({ os.windows })
    void "a link in place of the file is replaced when the command is forced, and not written through"() {
        given:
        project(ApplicationType.WEB)
        File victim = new File(outside, 'victim.txt')
        victim.text = 'victim'
        File file = new File(dir, 'src/test/groovy/example/grails/BookSpec.groovy')
        file.parentFile.mkdirs()
        Files.createSymbolicLink(file.toPath(), victim.toPath())

        when:
        int exit = ForgeCli.execute(beanContext, 'create-unit-test', 'Book')

        then:
        exit == 1
        Files.isSymbolicLink(file.toPath())
        out.toString().readLines() == ['| Error src/test/groovy/example/grails/BookSpec.groovy already exists. Run again with -f to overwrite.']

        when:
        exit = ForgeCli.execute(beanContext, 'create-unit-test', 'Book', '-f')

        then:
        exit == 0
        victim.text == 'victim'
        !Files.isSymbolicLink(file.toPath())
        file.text.contains('class BookSpec extends')

        when: 'a link that leads nowhere is in the way like any file'
        Files.delete(file.toPath())
        Files.createSymbolicLink(file.toPath(), new File(outside, 'new.txt').toPath())
        exit = ForgeCli.execute(beanContext, 'create-unit-test', 'Book')

        then:
        exit == 1
        outside.list() == ['victim.txt'] as String[]
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "a command of two files writes neither when the second cannot be written: #what"() {
        given:
        project(ApplicationType.REST_API)
        prepare(dir)

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, (['create-domain-resource', name] + options) as String[])
        }

        then:
        exit == 1
        written == [:]
        !new File(dir, 'grails-app').exists()
        out.toString().readLines().size() == 1
        out.toString().readLines()[0].startsWith('| Error Cannot write src/test/groovy/')
        out.toString().readLines()[0].endsWith(says)
        !out.toString().contains(dir.name)

        cleanup:
        new File(dir, 'src/test/groovy/ro').with { if (it.exists()) { Files.setPosixFilePermissions(it.toPath(), PosixFilePermissions.fromString('rwxr-xr-x')) } }

        where:
        what                                 | name            | options | says
        'a file where a directory is needed' | 'blocked.Book'  | []      | 'blocked/BookSpec.groovy: src/test/groovy/blocked is a file where a directory is needed'
        'a directory that is read only'      | 'ro.Book'       | []      | 'ro/BookSpec.groovy: the directory src/test/groovy/ro cannot be written to'
        'an empty directory in its place'    | 'Book'          | ['-f']  | 'BookSpec.groovy: it is a directory'
        'an empty directory, not forced'     | 'Book'          | []      | 'BookSpec.groovy: it is a directory'
        'a directory with files in its place' | 'full.Book'    | ['-f']  | 'full/BookSpec.groovy: it is a directory'
        'a name too long in bytes'           | LONG            | []      | 'has more than the 255 bytes a file system takes for a name'

        prepare = [
                'a file where a directory is needed' : { File d -> new File(d, 'src/test/groovy').mkdirs(); new File(d, 'src/test/groovy/blocked').text = 'a file' },
                'a directory that is read only'      : { File d -> File ro = new File(d, 'src/test/groovy/ro'); ro.mkdirs(); Files.setPosixFilePermissions(ro.toPath(), PosixFilePermissions.fromString('r-xr-xr-x')) },
                'an empty directory in its place'    : { File d -> new File(d, 'src/test/groovy/example/grails/BookSpec.groovy').mkdirs() },
                'an empty directory, not forced'     : { File d -> new File(d, 'src/test/groovy/example/grails/BookSpec.groovy').mkdirs() },
                'a directory with files in its place': { File d -> new File(d, 'src/test/groovy/full/BookSpec.groovy').mkdirs(); new File(d, 'src/test/groovy/full/BookSpec.groovy/keep.txt').text = 'x' },
                'a name too long in bytes'           : { File d -> }
        ][what]
    }

    void "a directory in the place of a file is still there after the command was forced"() {
        given:
        project(ApplicationType.WEB)
        File inPlace = new File(dir, 'src/test/groovy/example/grails/BookSpec.groovy')
        inPlace.mkdirs()

        expect:
        ForgeCli.execute(beanContext, 'create-unit-test', 'Book', '-f') == 1
        inPlace.isDirectory()
        out.toString().readLines() == ['| Error Cannot write src/test/groovy/example/grails/BookSpec.groovy: it is a directory']
    }

    @IgnoreIf({ os.windows })
    void "when the second file fails halfway, the project is what it was, byte for byte and mode for mode"() {
        given: 'a class that is there, executable and with a second name, and a handler whose second file fails after half of it'
        project(ApplicationType.REST_API)
        File domain = new File(dir, 'grails-app/domain/example/grails/Book.groovy')
        domain.parentFile.mkdirs()
        domain.bytes = new byte[17 * 1024 * 1024]
        Files.setPosixFilePermissions(domain.toPath(), PosixFilePermissions.fromString('rwxr-xr-x'))
        File otherName = new File(dir, 'kept-under-another-name')
        Files.createLink(otherName.toPath(), domain.toPath())
        ConsoleOutput console = Mock(ConsoleOutput)
        List<String> seen = []
        OutputHandler writing = new ProjectOutputHandler(dir, console)
        OutputHandler failing = new ProjectOutputHandler(dir, console) {

            @Override
            void write(String path, Template contents) throws IOException {
                if (path.endsWith('BookSpec.groovy')) {
                    // what the command wrote before is there now, and half of this file gets written
                    seen << new File(applicationDirectory, 'grails-app/domain/example/grails/Book.groovy').text
                    File half = new File(applicationDirectory, path)
                    half.text = 'package exam'
                    throw new IOException('No space left on device: ' + half.absolutePath)
                }
                writing.write(path, contents)
            }
        }
        CreateDomainResourceCommand command = new CreateDomainResourceCommand(CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP), { -> failing }, console)
        command.resourceName = 'Book'
        command.overwrite = true
        Map<String, List> before = tree()

        when:
        Integer exit = command.call()

        then: 'the first file had been written when the second failed'
        exit == 1
        seen.size() == 1
        seen[0].contains('@Resource(readOnly = false')

        and: 'and nothing of either is left: no file, no directory, and the class with its size, its mode and its other name'
        tree() == before
        Files.readAttributes(domain.toPath(), BasicFileAttributes).fileKey() == Files.readAttributes(otherName.toPath(), BasicFileAttributes).fileKey()
        1 * console.err('Cannot write src/test/groovy/example/grails/BookSpec.groovy: the file system refused it (IOException). The project is as it was before the command')
        0 * console.out(_)
    }

    @IgnoreIf({ os.windows })
    void "a directory that is made a link after the command looked is not written through"() {
        given: 'a handler that has the directory become a link to elsewhere right before the file is written'
        project(ApplicationType.WEB)
        File victim = new File(outside, 'BookSpec.groovy')
        victim.text = 'victim'
        File grails = new File(dir, 'src/test/groovy/example/grails')
        grails.mkdirs()
        ConsoleOutput console = Mock(ConsoleOutput)
        OutputHandler swapped = new ProjectOutputHandler(dir, console) {

            @Override
            void write(String path, Template contents) throws IOException {
                Files.delete(new File(applicationDirectory, 'src/test/groovy/example/grails').toPath())
                Files.createSymbolicLink(new File(applicationDirectory, 'src/test/groovy/example/grails').toPath(), new File(victim.parent).toPath())
                super.write(path, contents)
            }
        }
        CreateUnitTestCommand command = new CreateUnitTestCommand(CodeGenConfig.load(beanContext, dir, ConsoleOutput.NOOP), { -> swapped }, console)
        command.testName = 'Book'
        command.overwrite = true

        when:
        Integer exit = command.call()

        then:
        exit == 1
        victim.text == 'victim'
        outside.list() == ['BookSpec.groovy'] as String[]
        1 * console.err({ String message -> message.startsWith('Cannot write src/test/groovy/example/grails/BookSpec.groovy: ') && !message.contains(outside.name) })
    }

    @Unroll
    void "#command holds the name #name to the name of a class, as the newer commands do"() {
        given:
        project(ApplicationType.WEB, 'grails-quartz')

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, command, name)
        }
        String first = out.toString().readLines().first()

        then:
        exit == 2
        written == [:]
        outside.list().length == 0
        first.contains(says)
        !out.toString().contains(((char) 0x1B) as String)
        !out.toString().contains('\u0007')
        !out.toString().contains('@|red')

        where:
        [command, row] << [['create-controller', 'create-service', 'create-domain-class', 'create-taglib', 'create-interceptor', 'create-job'], [
                ['a/b', 'it is a path'],
                ['../Escape', 'it is a path'],
                ['C:x', 'it is a path'],
                ['my class', 'is not an identifier of Java'],
                ['a.con.Book', 'con is the name of a device on Windows'],
                ['a.B.groovy', 'it is the name of a file'],
                ['my-pkg.Book', 'my-pkg is not a package of Java'],
                ['Book-', 'a hyphen stands between two words of a name'],
                ['1Book', '1Book is not an identifier of Java'],
                ['Bo' + (((char) 0x1B) as String) + '[31mok', 'it has a character that is not shown'],
                ['Book@|red x|@', 'is not an identifier of Java'],
                ['B' * 201, 'it has more than 200 characters']
        ]].combinations()
        name = row[0]
        says = row[1]
    }

    @Unroll
    void "#command #given still is #file"() {
        given:
        project(ApplicationType.WEB, 'grails-quartz')

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, command, given) == 0
        }

        then:
        file in written.keySet()

        where:
        command               | given                | file
        'create-controller'   | 'book'               | 'grails-app/controllers/example/grails/BookController.groovy'
        'create-controller'   | 'book-store'         | 'grails-app/controllers/example/grails/BookStoreController.groovy'
        'create-controller'   | 'BookController'     | 'grails-app/controllers/example/grails/BookControllerController.groovy'
        'create-service'      | 'org.shop.bookStore' | 'grails-app/services/org/shop/BookStoreService.groovy'
        'create-domain-class' | 'a.b.book-store'     | 'grails-app/domain/a/b/BookStore.groovy'
        'create-taglib'       | 'micronaut'          | 'grails-app/taglib/example/grails/MicronautTagLib.groovy'
        'create-interceptor'  | 'URLCheck'           | 'grails-app/controllers/example/grails/URLCheckInterceptor.groovy'
        'create-job'          | 'cleanup'            | 'grails-app/jobs/example/grails/CleanupJob.groovy'
    }

    void "a name whose letters are not written in the composed form is refused, and a directory that is listed in another form is the same directory"() {
        given: 'a package that is there under a name with a letter and a mark, as some file systems list every name'
        project(ApplicationType.WEB)
        String composed = 'caf' + (((char) 0xE9) as String)
        String decomposed = 'cafe' + (((char) 0x301) as String)
        new File(dir, "src/test/groovy/${decomposed}").mkdirs()

        when:
        int refused = ForgeCli.execute(beanContext, 'create-unit-test', "${decomposed}.One")

        then:
        refused == 2
        out.toString().readLines().first().contains('Write the letter as one character (the form NFC of Unicode)')
        new File(dir, "src/test/groovy/${decomposed}").list().length == 0

        when: 'the name in the composed form, several times over'
        out.reset()
        List<Integer> exits = [
                ForgeCli.execute(beanContext, 'create-unit-test', "${composed}.One"),
                ForgeCli.execute(beanContext, 'create-unit-test', "${composed}.Two"),
                ForgeCli.execute(beanContext, 'create-unit-test', "${composed}.One"),
                ForgeCli.execute(beanContext, 'create-unit-test', "${composed}.One", '-f')
        ]

        then: 'every command finds the package, and a file that is there is in the way and replaced as anywhere else'
        exits == [0, 0, 1, 0]
        new File(dir, "src/test/groovy/${composed}/OneSpec.groovy").text.contains("package ${composed}\n")
        new File(dir, "src/test/groovy/${composed}").list().sort() == ['OneSpec.groovy', 'TwoSpec.groovy'] as String[]
        !out.toString().contains('written differently')
    }

    @IgnoreIf({ ArtefactCommandsSpec.caseInsensitive() })
    void "where the file system tells one case from another, a name in another case is another name"() {
        given:
        project(ApplicationType.WEB)

        expect:
        ForgeCli.execute(beanContext, 'create-unit-test', 'Kase') == 0
        ForgeCli.execute(beanContext, 'create-unit-test', 'EXAMPLE.grails.Other') == 0
        ForgeCli.execute(beanContext, 'create-unit-test', 'KASE') == 0
        new File(dir, 'src/test/groovy').list().sort() == ['EXAMPLE', 'example'] as String[]
        new File(dir, 'src/test/groovy/example/grails').list().sort() == ['KASESpec.groovy', 'KaseSpec.groovy'] as String[]
    }

    @Unroll
    void "the template of a job for a class without a package: #what"() {
        given:
        ConsoleOutput console = Mock(ConsoleOutput)
        CodeGenConfig config = new CodeGenConfig()
        config.features = ['grails-quartz']
        CreateJobCommand command = new CreateJobCommand(config, getOutputHandler(console), console)
        command.jobName = 'Cleanup'
        template('Job.groovy', source)

        when:
        Integer exit = command.call()

        then:
        exit == 0
        new File(dir, 'grails-app/jobs/CleanupJob.groovy').text == written

        where:
        what                           | source                                                          | written
        'the declaration is left out'  | 'package ${packageName}\n\nclass ${className}Job {}\n'          | 'class CleanupJob {}\n'
        'with a semicolon'             | 'package ${packageName};\n\nclass ${className}Job {}\n'         | 'class CleanupJob {}\n'
        'lines that end with a return' | 'package ${packageName}\r\rclass ${className}Job {}\r'          | 'class CleanupJob {}\r'
        'lines of Windows'             | '// a\r\npackage ${packageName}\r\n\r\nclass ${className}Job {}' | '// a\r\nclass CleanupJob {}'
        'no declaration of its own'    | 'class ${className}Job { String p = "package ${packageName}" }' | 'class CleanupJob { String p = "package " }'
    }

    void "the line of a mistake in the template of a job is the line the template has it on"() {
        given:
        ConsoleOutput console = Mock(ConsoleOutput)
        CodeGenConfig config = new CodeGenConfig()
        config.features = ['grails-quartz']
        CreateJobCommand command = new CreateJobCommand(config, getOutputHandler(console), console)
        command.jobName = 'Cleanup'
        template('Job.groovy', source)

        when:
        command.call()

        then:
        ArtefactTemplateException e = thrown()
        e.message == "The template src/main/templates/Job.groovy cannot be rendered: line ${line}: it has code between <% and %>. Templates are not run"
        !new File(dir, 'grails-app').exists()

        where:
        source                                                           | line
        'package ${packageName}\n\nclass ${className}Job {\n<% x %>\n}'  | 4
        'package ${packageName}\r\rclass ${className}Job {\r<% x %>\r}'  | 4
        '// a\r\n// b\r\n<% x %>'                                        | 3
    }

    @IgnoreIf({ !ArtefactCommandsSpec.caseInsensitive() })
    void "where the file system takes one case for another, a name that differs only in case is not written to"() {
        given:
        project(ApplicationType.WEB)

        expect:
        ForgeCli.execute(beanContext, 'create-unit-test', 'Kase') == 0

        when:
        out.reset()
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, 'create-unit-test', 'EXAMPLE.grails.Other')
        }

        then:
        exit == 1
        written == [:]
        out.toString().readLines() == ['| Error Cannot write src/test/groovy/EXAMPLE/grails/OtherSpec.groovy: the project has src/test/groovy/EXAMPLE under a name that is written differently, ' +
                'as in the case of its letters']

        when:
        out.reset()
        written = writtenBy {
            exit = ForgeCli.execute(beanContext, 'create-unit-test', 'KASE', '-f')
        }

        then:
        exit == 1
        written == [:]
        out.toString().readLines() == ['| Error Cannot write src/test/groovy/example/grails/KASESpec.groovy: the project has a file of that name that is written differently, ' +
                'as in the case of its letters']
    }

    void "a template is one under the very name of its location"() {
        given:
        project(ApplicationType.WEB)
        template('TESTING/generic.groovy', 'class @artifact.name@Ours {}\n')

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-unit-test', 'Book') == 0
        }

        then: 'on every file system the template of the command line is used'
        written['src/test/groovy/example/grails/BookSpec.groovy'] == golden('web')['src/test/groovy/example/grails/FooBarSpec.groovy'].replace('FooBar', 'Book')
        !out.toString().contains('Using the template')
    }

    void "a byte order mark at the start of a template is not written, and any other is"() {
        given:
        project(ApplicationType.WEB)
        char mark = (char) 0xFEFF
        template('testing/Generic.groovy', "${mark}${mark}class @artifact.name@Spec {}\n")

        when:
        Map<String, String> written = writtenBy {
            assert ForgeCli.execute(beanContext, 'create-unit-test', 'Book') == 0
        }

        then:
        written['src/test/groovy/example/grails/BookSpec.groovy'] == "${mark}class BookSpec {}\n".toString()
    }

    static boolean caseInsensitive() {
        File probe = File.createTempFile('grailsforgecase', '.tmp')
        try {
            return new File(probe.parentFile, probe.name.toUpperCase()).exists()
        } finally {
            probe.delete()
        }
    }

    @Unroll
    void "#command #given is refused or written by the names of the files it would write: #result"() {
        given:
        project(ApplicationType.REST_API, 'grails-quartz')

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, command, given)
        }

        then:
        exit == (result.contains('/') ? 0 : 2)
        result.contains('/') ? result in written.keySet() : written == [:] && out.toString().readLines().first().contains(result)

        where:
        command                  | given         | result
        'create-controller'      | 'con'         | 'grails-app/controllers/example/grails/ConController.groovy'
        'create-controller'      | 'Con'         | 'grails-app/controllers/example/grails/ConController.groovy'
        'create-controller'      | 'nul'         | 'grails-app/controllers/example/grails/NulController.groovy'
        'create-service'         | 'aux'         | 'grails-app/services/example/grails/AuxService.groovy'
        'create-taglib'          | 'prn'         | 'grails-app/taglib/example/grails/PrnTagLib.groovy'
        'create-interceptor'     | 'com1'        | 'grails-app/controllers/example/grails/Com1Interceptor.groovy'
        'create-job'             | 'lpt1'        | 'grails-app/jobs/example/grails/Lpt1Job.groovy'
        'create-unit-test'       | 'con'         | 'src/test/groovy/example/grails/ConSpec.groovy'
        'create-tool'            | 'nul'         | 'src/cli/groovy/example/grails/NulTool.groovy'
        'create-domain-class'    | 'con'         | 'Con is the name of a device on Windows'
        'create-domain-resource' | 'nul'         | 'Nul is the name of a device on Windows'
        'create-controller'      | 'aux.Book'    | 'aux is the name of a device on Windows'
        'create-controller'      | 'a.con.Book'  | 'con is the name of a device on Windows'
        'create-controller'      | 'my.groovy'   | 'it is the name of a file. The name of the class is given, without .groovy'
        'create-controller'      | 'app.java'    | 'it is the name of a file. The name of the class is given, without .java'
        'create-unit-test'       | 'a.B.groovy'  | 'it is the name of a file'
        'create-controller'      | 'foo.Groovy'  | 'grails-app/controllers/foo/GroovyController.groovy'
        'create-controller'      | 'book.Java'   | 'grails-app/controllers/book/JavaController.groovy'
        'create-unit-test'       | 'foo.Groovy'  | 'src/test/groovy/foo/GroovySpec.groovy'
        'create-unit-test'       | 'a.b.JAVA'    | 'src/test/groovy/a/b/JAVASpec.groovy'
        'create-domain-class'    | 'foo.Java'    | 'grails-app/domain/foo/Java.groovy'
    }

    @Requires({ os.macOs })
    void "a file that cannot be moved aside is not touched, and the command does not say it wrote it"() {
        given: 'a controller the file system lets nobody rename, remove or write'
        project(ApplicationType.WEB)
        File controller = new File(dir, 'grails-app/controllers/example/grails/BookController.groovy')
        controller.parentFile.mkdirs()
        controller.text = 'mine'
        assert ['chflags', 'uchg', controller.absolutePath].execute().waitFor() == 0
        Map<String, List> before = tree()

        when:
        int exit = ForgeCli.execute(beanContext, 'create-controller', 'Book', '-f')

        then:
        exit == 1
        controller.text == 'mine'
        tree() == before
        out.toString().readLines().size() == 1
        out.toString().readLines()[0].startsWith('| Error Cannot write grails-app/controllers/example/grails/BookController.groovy: ')
        out.toString().readLines()[0].endsWith('. The project is as it was before the command')
        !out.toString().contains('what the command wrote')

        cleanup:
        ['chflags', 'nouchg', new File(dir, 'grails-app/controllers/example/grails/BookController.groovy').absolutePath].execute().waitFor()
    }

    void "when what is there cannot be given another name, it is not removed in its place"() {
        given: 'two files that are there, and a file system that renames the first and not the second'
        project(ApplicationType.WEB)
        ['grails-app/a/One.groovy', 'grails-app/b/Two.groovy'].each { String path ->
            File file = new File(dir, path)
            file.parentFile.mkdirs()
            file.text = 'mine ' + path
        }
        Map<String, List> before = tree()
        OutputHandler output = new ProjectOutputHandler(dir, ConsoleOutput.NOOP)
        Function<String, ProjectPlace> opener = { String path ->
            new ProjectPlace(path) {

                @Override
                protected void move(String from, String to) throws IOException {
                    if (from == 'Two.groovy') {
                        throw new AccessDeniedException(new File(dir, path).absolutePath)
                    }
                    super.move(from, to)
                }
            }.opened(dir)
        } as Function<String, ProjectPlace>

        when:
        ProjectFiles.write(dir, output, ['grails-app/a/One.groovy': 'new one', 'grails-app/b/Two.groovy': 'new two'], opener)

        then: 'the first has its name and its content again, and the second was never anything else'
        ProjectFiles.Failed e = thrown()
        e.message == 'Cannot write grails-app/b/Two.groovy: it, or the directory it is in, cannot be written to. The project is as it was before the command'
        tree() == before
    }

    void "what a command that did not end left of a file is said, and nothing is written until it is dealt with"() {
        given: 'a controller that was moved aside by a command that was killed, and the file it had begun to write'
        project(ApplicationType.WEB)
        File directory = new File(dir, 'grails-app/controllers/example/grails')
        directory.mkdirs()
        File aside = new File(directory, '.BookController.groovy.grails-aside-1f2e3d4c5b6a7988')
        aside.text = 'what it was'
        File begun = new File(directory, '.BookController.groovy.grails-new-0011223344556677')
        begun.text = 'half'
        File ofAnother = new File(directory, '.OtherController.groovy.grails-new-0011223344556677')
        ofAnother.text = 'not of this file'

        when:
        int exit = 0
        Map<String, String> written = writtenBy {
            exit = ForgeCli.execute(beanContext, (['create-controller', 'Book'] + options) as String[])
        }

        then: 'whether the file is there or not, and forced or not'
        exit == 1
        written == [:]
        aside.text == 'what it was'
        out.toString().readLines() == ['| Error Cannot write grails-app/controllers/example/grails/BookController.groovy: a command that did not end left what the file was before ' +
                'in the same directory, under the name .BookController.groovy.grails-aside-1f2e3d4c5b6a7988. Give that file its name back, or remove it, and run the command again']

        when: 'it has its name back'
        out.reset()
        assert aside.renameTo(new File(directory, 'BookController.groovy'))
        exit = ForgeCli.execute(beanContext, 'create-controller', 'Book', '-f')

        then: 'the command writes, removes what was begun for this file, and leaves nothing of its own beside it'
        exit == 0
        new File(directory, 'BookController.groovy').text.contains('class BookController')
        directory.list().sort() == ['.OtherController.groovy.grails-new-0011223344556677', 'BookController.groovy'] as String[]
        new File(dir, 'src/test/groovy/example/grails').list() == ['BookControllerSpec.groovy'] as String[]

        where:
        options << [[], ['-f']]
    }

    void "the name a file is moved aside under has the name of the file in it, and is a name a file system takes"() {
        expect:
        ProjectPlace.besideName('Book.groovy', 'aside') == '.Book.groovy.grails-aside-'
        (ProjectPlace.besideName(name, 'aside') + 'f' * 16).getBytes('UTF-8').length <= 255
        ProjectPlace.besideName(name, 'aside').startsWith('.' + name.substring(0, 60))

        where:
        name << ['B' * 248 + '.groovy', ((((char) 0x4E66) as String) * 82) + '.groovy']
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
        Map<String, String> after = files()
        Map<String, String> changed = new TreeMap<>(after.findAll { String path, String text -> before[path] != text })
        // a file that is gone is a change as well
        before.keySet().findAll { String path -> !after.containsKey(path) }.each { String path -> changed[path] = null }
        return changed
    }

    // everything the directory holds, the record of the project too: what each entry is, its mode, its size and its content
    private Map<String, List> tree() {
        Map<String, List> tree = new TreeMap<>()
        // a file system without modes, as that of Windows, has none to compare
        boolean posix = dir.toPath().fileSystem.supportedFileAttributeViews().contains('posix')
        dir.eachFileRecurse { File each ->
            String path = dir.toPath().relativize(each.toPath()).toString().replace(File.separator, '/')
            tree[path] = [each.directory ? 'directory' : Files.isSymbolicLink(each.toPath()) ? 'link' : 'file',
                          posix ? PosixFilePermissions.toString(Files.getPosixFilePermissions(each.toPath())) : '',
                          each.directory ? 0L : each.length(), each.directory ? 0 : Arrays.hashCode(each.bytes)]
        }
        return tree
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
