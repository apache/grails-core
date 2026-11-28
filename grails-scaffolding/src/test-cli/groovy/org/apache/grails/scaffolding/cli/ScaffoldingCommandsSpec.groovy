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
package org.apache.grails.scaffolding.cli

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.ToolSupport
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.script.ScriptConsole
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser

/**
 * What the commands of the scaffolding plugin write into a project, for a domain class of the project and for
 * a class the project has no source of.
 *
 * <p>The domain classes are named in full: a simple name is looked for under {@code grails-app/domain} of the
 * working directory, which is the project when the Gradle task runs a command and is not the project of a spec.</p>
 */
class ScaffoldingCommandsSpec extends Specification {

    private static final List<String> WRITTEN_TO = ['grails-app/controllers', 'grails-app/services', 'grails-app/views', 'src/test', 'src/integration-test']

    @TempDir
    File project

    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    GrailsConsole console

    def setup() {
        // the commands that are made in a feature say their errors where the feature reads them
        console = GrailsConsole.getInstance()
        GrailsConsole.setInstance(new ScriptConsole(new PrintStream(new ByteArrayOutputStream(), true), new PrintStream(errors, true)))
        File domain = new File(project, 'grails-app/domain/com/example/Book.groovy')
        domain.parentFile.mkdirs()
        domain.text = 'package com.example\n\nclass Book {\n    String title\n}\n'
        File templates = new File(project, 'src/main/templates/scaffolding')
        templates.mkdirs()
        new File('src/main/templates/scaffolding').eachFile { File template ->
            new File(templates, template.name).bytes = template.bytes
        }
    }

    def cleanup() {
        GrailsConsole.setInstance(console)
    }

    def "generate-controller writes the service spec, an integration spec, to the integration tests"() {
        when:
        boolean result = run(new GenerateControllerTool(), 'generate-controller com.example.Book')

        then:
        result
        written('grails-app/controllers/com/example/BookController.groovy').contains('package com.example')
        written('grails-app/services/com/example/BookService.groovy')
        written('src/test/groovy/com/example/BookControllerSpec.groovy')
        written('src/integration-test/groovy/com/example/BookServiceSpec.groovy').contains('@Integration')
        !new File(project, 'src/test/groovy/com/example/BookServiceSpec.groovy').exists()
    }

    def "generate-all generates the views of a class that is found although another class is not, and fails"() {
        when:
        boolean result = run(new GenerateAllTool(), 'generate-all Missing com.example.Book')

        then:
        !result
        written('grails-app/controllers/com/example/BookController.groovy')
        written('src/integration-test/groovy/com/example/BookServiceSpec.groovy')
        written('grails-app/views/book/index.gsp')
        !new File(project, 'grails-app/controllers/MissingController.groovy').exists()
    }

    def "generate-all generates the controller and the views of a domain class"() {
        expect:
        run(new GenerateAllTool(), 'generate-all com.example.Book')
        written('grails-app/controllers/com/example/BookController.groovy')
        ['create', 'edit', 'index', 'show'].every { written("grails-app/views/book/${it}.gsp") }
    }

    def "#name creates the class in the package of a domain class of the project"() {
        when:
        boolean result = run(command, "${name} com.example.Book")

        then:
        result
        files.every { written(it).contains('package com.example') }

        where:
        name                         | command                               | files
        'create-scaffold-controller' | new CreateScaffoldControllerTool() | ['grails-app/controllers/com/example/BookController.groovy']
        'create-scaffold-service'    | new CreateScaffoldServiceTool()    | ['grails-app/services/com/example/BookService.groovy']
        'generate-scaffold-all'      | new GenerateScaffoldAllTool()      | ['grails-app/controllers/com/example/BookController.groovy', 'grails-app/services/com/example/BookService.groovy']
    }

    def "#name takes a class the project has no source of by the name it is given"() {
        when: 'the domain class of a plugin or of another project of the build'
        boolean result = run(command, "${name} org.library.Author")

        then:
        result
        files.every { written(it).contains('package org.library') }

        where:
        name                         | command                               | files
        'create-scaffold-controller' | new CreateScaffoldControllerTool() | ['grails-app/controllers/org/library/AuthorController.groovy']
        'create-scaffold-service'    | new CreateScaffoldServiceTool()    | ['grails-app/services/org/library/AuthorService.groovy']
        'generate-scaffold-all'      | new GenerateScaffoldAllTool()      | ['grails-app/controllers/org/library/AuthorController.groovy', 'grails-app/services/org/library/AuthorService.groovy']
    }

    def "#name fails for a domain class that is not found"() {
        expect:
        !run(command, "${name} Missing")

        where:
        name                        | command
        'generate-controller'       | new GenerateControllerTool()
        'generate-async-controller' | new GenerateAsyncControllerTool()
        'generate-views'            | new GenerateViewsTool()
    }

    def "install-templates copies the templates of the plugin into the project"() {
        given: 'a project without templates of its own'
        new File(project, 'src/main/templates').deleteDir()

        when:
        boolean result = run(new InstallTemplatesTool(), 'install-templates')

        then:
        result
        ['Controller.groovy', 'ScaffoldedController.groovy', 'ServiceSpec.groovy', 'index.gsp', 'show.gsp'].every {
            written("src/main/templates/scaffolding/${it}")
        }
    }

    def "generate-async-controller writes an asynchronous controller and its spec, and no service"() {
        when:
        boolean result = run(new GenerateAsyncControllerTool(), 'generate-async-controller com.example.Book')

        then:
        result
        written('grails-app/controllers/com/example/BookController.groovy').contains('package com.example')
        written('grails-app/controllers/com/example/BookController.groovy').contains('Book.async.task {')
        written('src/test/groovy/com/example/BookControllerSpec.groovy').contains('class BookControllerSpec')
        generated() == ['grails-app/controllers/com/example/BookController.groovy', 'src/test/groovy/com/example/BookControllerSpec.groovy']
        errors.toString() == ''
    }

    def "generate-async-controller generates for a class that is found although another class is not, and fails"() {
        when:
        boolean result = run(new GenerateAsyncControllerTool(), 'generate-async-controller Missing com.example.Book')

        then:
        !result
        errors.toString().contains('Domain class not found for name: Missing')
        generated() == ['grails-app/controllers/com/example/BookController.groovy', 'src/test/groovy/com/example/BookControllerSpec.groovy']
    }

    @Unroll
    def "#name without a domain class fails, says so and writes nothing"() {
        when:
        boolean result = run(command.getDeclaredConstructor().newInstance(), name)

        then:
        !result
        errors.toString().contains('No domain-class specified')
        generated() == []

        where:
        name                         | command
        'generate-controller'        | GenerateControllerTool
        'generate-async-controller'  | GenerateAsyncControllerTool
        'generate-views'             | GenerateViewsTool
        'generate-all'               | GenerateAllTool
        'generate-service'           | GenerateServiceTool
        'create-scaffold-controller' | CreateScaffoldControllerTool
        'create-scaffold-service'    | CreateScaffoldServiceTool
        'generate-scaffold-all'      | GenerateScaffoldAllTool
    }

    @Unroll
    def "#name names the domain class that is not found, fails and writes nothing"() {
        when:
        boolean result = run(command.getDeclaredConstructor().newInstance(), "${name} Missing")

        then:
        !result
        errors.toString().contains(said)
        generated() == []

        where:
        name                        | command                        | said
        'generate-controller'       | GenerateControllerTool      | 'Domain class not found for name: Missing'
        'generate-async-controller' | GenerateAsyncControllerTool | 'Domain class not found for name: Missing'
        'generate-views'            | GenerateViewsTool           | 'Domain class not found for name: Missing'
        'generate-all'              | GenerateAllTool             | 'Domain class not found for name: Missing'
        'generate-service'          | GenerateServiceTool         | 'No domain-class found for name: Missing'
    }

    @Unroll
    def "#name keeps the files that are there, and writes them again with --force"() {
        given: 'what the command generated, edited since'
        assert run(command.getDeclaredConstructor().newInstance(), "${name} com.example.Book")
        List<String> files = generated()
        files.each { String path -> new File(project, path).text = 'edited' }

        when:
        boolean kept = run(command.getDeclaredConstructor().newInstance(), "${name} com.example.Book")

        then: 'a file that is there is passed over, which is no failure'
        kept
        files.size() == count
        files.every { String path -> new File(project, path).text == 'edited' }
        generated() == files

        when:
        boolean forced = run(command.getDeclaredConstructor().newInstance(), "${name} com.example.Book --force")

        then:
        forced
        files.every { String path -> new File(project, path).text != 'edited' }
        generated() == files

        where:
        name                         | command                         | count
        'generate-controller'        | GenerateControllerTool       | 4
        'generate-async-controller'  | GenerateAsyncControllerTool  | 2
        'generate-views'             | GenerateViewsTool            | 4
        'generate-all'               | GenerateAllTool              | 8
        'generate-service'           | GenerateServiceTool          | 2
        'create-scaffold-controller' | CreateScaffoldControllerTool | 1
        'create-scaffold-service'    | CreateScaffoldServiceTool    | 1
        'generate-scaffold-all'      | GenerateScaffoldAllTool      | 2
    }

    def "generate-service writes the data service of a domain class and its spec"() {
        expect:
        run(new GenerateServiceTool(), 'generate-service com.example.Book')
        written('grails-app/services/com/example/BookService.groovy').contains('package com.example')
        written('src/test/groovy/com/example/BookServiceSpec.groovy').contains('BookService')
    }

    @Unroll
    def "#line writes #path with what its flags ask for"() {
        when:
        boolean result = run(command.getDeclaredConstructor().newInstance(), line)

        then:
        result
        expected.every { String each -> written(path).contains(each) }

        where:
        command                         | line                                                                         | path                                                        | expected
        CreateScaffoldControllerTool | 'create-scaffold-controller com.example.Book'                                | 'grails-app/controllers/com/example/BookController.groovy'       | ['package com.example\n', '@Scaffold(Book)']
        CreateScaffoldControllerTool | 'create-scaffold-controller com.example.Book --service'                      | 'grails-app/controllers/com/example/BookController.groovy'       | ['import grails.plugin.scaffolding.RestfulServiceController', '@Scaffold(RestfulServiceController<Book>)']
        CreateScaffoldControllerTool | 'create-scaffold-controller com.example.Book --namespace=admin'              | 'grails-app/controllers/com/example/admin/BookController.groovy' | ['package com.example.admin\n', 'import com.example.Book\n', "static namespace = 'admin'"]
        CreateScaffoldControllerTool | 'create-scaffold-controller com.example.Book --extends=org.base.Base'        | 'grails-app/controllers/com/example/BookController.groovy'       | ['import org.base.Base\n', '@Scaffold(Base<Book>)']
        CreateScaffoldServiceTool    | 'create-scaffold-service com.example.Book --extends=org.base.BaseService'    | 'grails-app/services/com/example/BookService.groovy'             | ['import org.base.BaseService\n', '@Scaffold(BaseService<Book>)']
        GenerateScaffoldAllTool      | 'generate-scaffold-all com.example.Book --serviceExtends=org.base.BaseService --controllerExtends=org.base.Base --namespace=admin' | 'grails-app/services/com/example/BookService.groovy' | ['@Scaffold(BaseService<Book>)']
        GenerateScaffoldAllTool      | 'generate-scaffold-all com.example.Book --serviceExtends=org.base.BaseService --controllerExtends=org.base.Base --namespace=admin' | 'grails-app/controllers/com/example/admin/BookController.groovy' | ['@Scaffold(Base<Book>)', "static namespace = 'admin'"]
        GenerateScaffoldAllTool      | 'generate-scaffold-all com.example.Book'                                     | 'grails-app/controllers/com/example/BookController.groovy'       | ['@Scaffold(RestfulServiceController<Book>)']
    }

    def "install-templates into a directory that has templates writes each of them again, and keeps what is no template of the plugin"() {
        given: 'a template that was edited, and a file of the project'
        File edited = new File(project, 'src/main/templates/scaffolding/index.gsp')
        edited.text = 'edited'
        File own = new File(project, 'src/main/templates/scaffolding/own.gsp')
        own.text = 'of the project'

        when:
        boolean result = run(new InstallTemplatesTool(), 'install-templates')

        then:
        result
        edited.text == new File('src/main/templates/scaffolding/index.gsp').text
        own.text == 'of the project'
    }

    def "install-templates fails when the directory of the templates cannot be made"() {
        given: 'a file where the directory would be'
        File templates = new File(project, 'src/main/templates')
        templates.deleteDir()
        templates.text = 'a file'

        when:
        boolean result = run(new InstallTemplatesTool(), 'install-templates')

        then:
        !result
        errors.toString().contains('| Error ')
        templates.text == 'a file'
    }

    /**
     * Runs a command in the project of the spec. A command that the Gradle task starts gets the directory of
     * the project from the build settings of the JVM, which a spec cannot set.
     */
    private boolean run(ToolSupport command, String line) {
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    /**
     * @return the files the commands write, as paths in the project, in order
     */
    private List<String> generated() {
        List<String> paths = []
        WRITTEN_TO.each { String directory ->
            File at = new File(project, directory)
            if (at.isDirectory()) {
                at.eachFileRecurse { File file ->
                    if (file.isFile()) {
                        paths.add(project.toPath().relativize(file.toPath()).toString().replace(File.separator, '/'))
                    }
                }
            }
        }
        paths.sort()
    }

    private String written(String path) {
        File file = new File(project, path)
        assert file.isFile()
        file.text
    }
}
