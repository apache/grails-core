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

import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
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

    @TempDir
    File project

    def setup() {
        File domain = new File(project, 'grails-app/domain/com/example/Book.groovy')
        domain.parentFile.mkdirs()
        domain.text = 'package com.example\n\nclass Book {\n    String title\n}\n'
        File templates = new File(project, 'src/main/templates/scaffolding')
        templates.mkdirs()
        new File('src/main/templates/scaffolding').eachFile { File template ->
            new File(templates, template.name).bytes = template.bytes
        }
    }

    def "generate-controller writes the service spec, an integration spec, to the integration tests"() {
        when:
        boolean result = run(new GenerateControllerCommand(), 'generate-controller com.example.Book')

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
        boolean result = run(new GenerateAllCommand(), 'generate-all Missing com.example.Book')

        then:
        !result
        written('grails-app/controllers/com/example/BookController.groovy')
        written('src/integration-test/groovy/com/example/BookServiceSpec.groovy')
        written('grails-app/views/book/index.gsp')
        !new File(project, 'grails-app/controllers/MissingController.groovy').exists()
    }

    def "generate-all generates the controller and the views of a domain class"() {
        expect:
        run(new GenerateAllCommand(), 'generate-all com.example.Book')
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
        'create-scaffold-controller' | new CreateScaffoldControllerCommand() | ['grails-app/controllers/com/example/BookController.groovy']
        'create-scaffold-service'    | new CreateScaffoldServiceCommand()    | ['grails-app/services/com/example/BookService.groovy']
        'generate-scaffold-all'      | new GenerateScaffoldAllCommand()      | ['grails-app/controllers/com/example/BookController.groovy', 'grails-app/services/com/example/BookService.groovy']
    }

    def "#name takes a class the project has no source of by the name it is given"() {
        when: 'the domain class of a plugin or of another project of the build'
        boolean result = run(command, "${name} org.library.Author")

        then:
        result
        files.every { written(it).contains('package org.library') }

        where:
        name                         | command                               | files
        'create-scaffold-controller' | new CreateScaffoldControllerCommand() | ['grails-app/controllers/org/library/AuthorController.groovy']
        'create-scaffold-service'    | new CreateScaffoldServiceCommand()    | ['grails-app/services/org/library/AuthorService.groovy']
        'generate-scaffold-all'      | new GenerateScaffoldAllCommand()      | ['grails-app/controllers/org/library/AuthorController.groovy', 'grails-app/services/org/library/AuthorService.groovy']
    }

    def "#name fails for a domain class that is not found"() {
        expect:
        !run(command, "${name} Missing")

        where:
        name                        | command
        'generate-controller'       | new GenerateControllerCommand()
        'generate-async-controller' | new GenerateAsyncControllerCommand()
        'generate-views'            | new GenerateViewsCommand()
    }

    def "install-templates copies the templates of the plugin into the project"() {
        given: 'a project without templates of its own'
        new File(project, 'src/main/templates').deleteDir()

        when:
        boolean result = run(new InstallTemplatesCommand(), 'install-templates')

        then:
        result
        ['Controller.groovy', 'ScaffoldedController.groovy', 'ServiceSpec.groovy', 'index.gsp', 'show.gsp'].every {
            written("src/main/templates/scaffolding/${it}")
        }
    }

    /**
     * Runs a command in the project of the spec. A command that the Gradle task starts gets the directory of
     * the project from the build settings of the JVM, which a spec cannot set.
     */
    private boolean run(GrailsApplicationCommand command, String line) {
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    private String written(String path) {
        File file = new File(project, path)
        assert file.isFile()
        file.text
    }
}
