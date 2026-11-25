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

import java.security.MessageDigest

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration

import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.script.ScriptConsole
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser

/**
 * What {@code generate-views}, {@code generate-controller} and {@code generate-all} write into a REST API
 * application: the JSON views and the REST controller the {@code rest-api} profile of Grails 8 generated.
 *
 * <p>A REST API application is one with the JSON views plugin on its class path and no GSP under
 * {@code grails-app/views}. The plugin is not on the class path of the tests, where it would make the project
 * of every spec such an application: the build hands its class path over, and a spec that means a REST API
 * application runs the command with a class loader that has it.</p>
 */
class RestScaffoldingCommandsSpec extends Specification {

    private static final List<String> WRITTEN_TO = ['grails-app/controllers', 'grails-app/services', 'grails-app/views', 'src/test', 'src/integration-test']

    private static final List<String> REST_CONTROLLER = [
            'grails-app/controllers/com/example/BookController.groovy',
            'grails-app/services/com/example/BookService.groovy',
            'src/integration-test/groovy/com/example/BookServiceSpec.groovy',
            'src/test/groovy/com/example/BookControllerSpec.groovy',
    ]

    private static final List<String> JSON_VIEWS = [
            'grails-app/views/book/_book.gson',
            'grails-app/views/book/index.gson',
            'grails-app/views/book/show.gson',
    ]

    private static final List<String> GSP_VIEWS = ['create', 'edit', 'index', 'show'].collect { "grails-app/views/book/${it}.gsp".toString() }

    /**
     * The SHA-256 of each source file the {@code rest-api} profile of Grails 8.1 generated for
     * {@code com.example.Book}: its templates under {@code grails-profiles/rest-api/templates/artifacts/scaffolding}
     * with the package, the class name and the property name of the class in place of the variables.
     */
    private static final Map<String, String> GRAILS_8 = [
            'grails-app/controllers/com/example/BookController.groovy'      : 'd68a511826fc6da6b66b7a6a37e3adf43464fc4287e7f81a636f5c2f16e26239',
            'grails-app/services/com/example/BookService.groovy'            : 'a88713cd5bbc4ecc4d3eb2dacbbd165e25159325ce3730f6f3165894fc5e28a2',
            'src/test/groovy/com/example/BookControllerSpec.groovy'         : '8164664a0a63cc59fe2ff6fea6e5c7f10dde9b49ecf265d9ef540e5f2ce9b7b5',
            'src/integration-test/groovy/com/example/BookServiceSpec.groovy': 'ed5c7bd2d4cfa49176bbca31fff17fa7277bd6d5cd49564b8e1bd22feb193124',
    ]

    /** What the controller spec has that the one of Grails 8 had not: it renders the view the action answered with */
    private static final String RENDER_VIEW = '''\
    /**
     * Renders the JSON view the action answered with. A unit test calls the action and nothing after it,
     * so the view is rendered here, as the application renders it once the action has returned.
     */
    private void renderView() {
        def modelAndView = controller.modelAndView
        modelAndView?.view?.render(modelAndView.model, request, response)
    }

'''

    /** The cleanup of the service spec of Grails 8, which failed every test of an application without MongoDB */
    private static final String GRAILS_8_CLEANUP = 'assert false, "TODO: Provide a cleanup implementation if using MongoDB"'
    private static final String CLEANUP = '// TODO: With MongoDB, where what a test saves is not rolled back, delete here what setupData() saved'

    @Shared
    URLClassLoader restApplication

    @TempDir
    File project

    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    GrailsConsole console

    def setupSpec() {
        String plugins = System.getProperty('scaffolding.restApplicationPlugins')
        assert plugins: 'the build hands the class path of the JSON views plugin to the tests'
        URL[] urls = plugins.split(File.pathSeparator).collect { new File(it).toURI().toURL() } as URL[]
        restApplication = new URLClassLoader(urls, getClass().classLoader)
    }

    def cleanupSpec() {
        restApplication?.close()
    }

    def setup() {
        console = GrailsConsole.getInstance()
        GrailsConsole.setInstance(new ScriptConsole(new PrintStream(new ByteArrayOutputStream(), true), new PrintStream(errors, true)))
        write('grails-app/domain/com/example/Book.groovy', 'package com.example\n\nclass Book {\n    String title\n}\n')
    }

    def cleanup() {
        GrailsConsole.setInstance(console)
    }

    def "the class path of the tests has no JSON views plugin, and the one the build hands over has"() {
        expect:
        getClass().classLoader.getResource('grails/plugin/json/view/JsonViewGrailsPlugin.class') == null
        restApplication.getResource('grails/plugin/json/view/JsonViewGrailsPlugin.class') != null
    }

    def "generate-all in a REST API application writes the REST controller, its service, their specs and the JSON views"() {
        given: 'the views a REST API application is created with'
        write('grails-app/views/error.gson', 'json {\n}\n')

        when:
        boolean result = runInRestApplication(new GenerateAllCommand(), 'generate-all com.example.Book')

        then:
        result
        errors.toString() == ''
        generated() == (REST_CONTROLLER + JSON_VIEWS + ['grails-app/views/error.gson']).sort()

        and: 'the controller answers with JSON only, as one create-controller writes in a REST API application, and has the actions of a REST resource only'
        String controller = written('grails-app/controllers/com/example/BookController.groovy')
        controller.contains("static responseFormats = ['json']\n")
        controller.contains('render status: NO_CONTENT')
        !controller.contains('def create()')
        !controller.contains('def edit(')
        !controller.contains('flash.message')

        and: 'its service returns what it deleted, which the controller answers 404 without'
        written('grails-app/services/com/example/BookService.groovy').contains('Book delete(Serializable id)')
    }

    def "what is generated for a REST API application is what the rest-api profile of Grails 8 generated"() {
        when:
        boolean result = runInRestApplication(new GenerateAllCommand(), 'generate-all com.example.Book')

        then: 'the service is the same byte for byte'
        result
        sameAsGrails8('grails-app/services/com/example/BookService.groovy') { String text -> text }

        and: 'the controller is the same but for its formats: Grails 8 advertised XML as well, which a REST API of Grails 9 does not'
        sameAsGrails8('grails-app/controllers/com/example/BookController.groovy') { String text ->
            assert text.count("static responseFormats = ['json']\n") == 1
            text.replace("static responseFormats = ['json']\n", "static responseFormats = ['json', 'xml']\n")
        }

        and: 'the controller spec is the same but for rendering the view of each action whose answer it reads, without which those tests fail'
        sameAsGrails8('src/test/groovy/com/example/BookControllerSpec.groovy') { String text ->
            assert text.count(RENDER_VIEW) == 1
            assert text.count('renderView()') == 7
            text.replace(RENDER_VIEW, '').replaceAll(/\n *renderView\(\)/, '')
        }

        and: 'the service spec is the same but for its cleanup, which failed every test of an application without MongoDB'
        sameAsGrails8('src/integration-test/groovy/com/example/BookServiceSpec.groovy') { String text ->
            assert text.count(CLEANUP) == 1
            text.replace(CLEANUP, GRAILS_8_CLEANUP)
        }

        and: 'the views are the same but for their indentation, which was a tab'
        written('grails-app/views/book/index.gson') == '''\
            import com.example.Book

            model {
                Iterable<Book> bookList
            }

            json tmpl.book(bookList ?: [])

            '''.stripIndent()
        written('grails-app/views/book/show.gson') == '''\
            import com.example.Book

            model {
                Book book
            }

            json tmpl.book(book)
            '''.stripIndent()
        written('grails-app/views/book/_book.gson') == '''\
            import com.example.Book

            model {
                Book book
            }

            json g.render(book, [resolveTemplate: false])
            '''.stripIndent()
    }

    def "the controller, the service and the controller spec that are generated compile, and the JSON views compile and render"() {
        given:
        assert runInRestApplication(new GenerateAllCommand(), 'generate-all com.example.Book')
        File classes = new File(project, 'build/classes')
        File views = new File(project, 'build/views')

        when: 'the sources are compiled with the class path of a REST API application'
        compile(classes, ['grails-app/domain/com/example/Book.groovy',
                          'grails-app/services/com/example/BookService.groovy',
                          'grails-app/controllers/com/example/BookController.groovy',
                          'src/test/groovy/com/example/BookControllerSpec.groovy'])

        then:
        ['Book', 'BookService', 'BookController', 'BookControllerSpec'].every { new File(classes, "com/example/${it}.class").isFile() }

        when: 'the views are compiled with the compiler of JSON views'
        views.mkdirs()
        URLClassLoader application = new URLClassLoader([classes.toURI().toURL(), views.toURI().toURL()] as URL[], restApplication)
        def configuration = application.loadClass('grails.plugin.json.view.JsonViewConfiguration').getDeclaredConstructor().newInstance()
        configuration.packageName = 'library'
        File sources = new File(project, 'grails-app/views')
        withClassLoader(application) {
            def compiler = application.loadClass('grails.plugin.json.view.JsonViewCompiler').newInstance(configuration, sources)
            compiler.setTargetDirectory(views)
            compiler.setClasspathList([classes.absolutePath])
            compiler.compile(JSON_VIEWS.collect { new File(project, it) })
        }

        then:
        List<String> compiled = []
        views.eachFileRecurse { File file ->
            if (file.name.endsWith('_gson.class')) {
                compiled.add(file.name)
            }
        }
        compiled.sort() == ['library_book__book_gson.class', 'library_book_index_gson.class', 'library_book_show_gson.class']

        when: 'the compiled views render a book and a list of books'
        def engine = application.loadClass('grails.plugin.json.view.JsonViewTemplateEngine').newInstance(configuration, application)
        def resolver = application.loadClass('grails.views.resolve.GenericGroovyTemplateResolver').getDeclaredConstructor().newInstance()
        resolver.baseDir = null
        resolver.packageName = 'library'
        resolver.classLoader = application
        engine.templateResolver = resolver
        Class<?> book = application.loadClass('com.example.Book')
        String shown = render(engine, '/book/show.gson', [book: book.newInstance(title: 'Metropolis')])
        String listed = render(engine, '/book/index.gson', [bookList: [book.newInstance(title: 'Metropolis'), book.newInstance(title: 'Nosferatu')]])
        String none = render(engine, '/book/index.gson', [bookList: null])

        then:
        shown == '{"title":"Metropolis"}'
        listed == '[{"title":"Metropolis"},{"title":"Nosferatu"}]'
        none == '[]'

        cleanup:
        application?.close()
    }

    @Unroll
    def "#name in a REST API application writes #files"() {
        when:
        boolean result = runInRestApplication(command.getDeclaredConstructor().newInstance(), "${name} com.example.Book")

        then:
        result
        generated() == files

        where:
        name                  | command                   | files
        'generate-views'      | GenerateViewsCommand      | JSON_VIEWS
        'generate-controller' | GenerateControllerCommand | REST_CONTROLLER
        'generate-all'        | GenerateAllCommand        | (REST_CONTROLLER + JSON_VIEWS).sort()
    }

    @Unroll
    def "generate-all writes #kind views where the JSON views plugin #plugin, the project #gsp and the line ends with '#option'"() {
        given:
        if (hasGsp) {
            write('grails-app/views/layouts/main.gsp', '<html></html>\n')
        }
        List<String> there = hasGsp ? ['grails-app/views/layouts/main.gsp'] : []
        String line = "generate-all com.example.Book ${option}".trim()

        when:
        boolean result = hasPlugin ? runInRestApplication(new GenerateAllCommand(), line) : run(new GenerateAllCommand(), line)

        then:
        result
        generated() == (REST_CONTROLLER + (json ? JSON_VIEWS : GSP_VIEWS) + there).sort()
        written('grails-app/controllers/com/example/BookController.groovy').contains('static responseFormats') == json

        where:
        hasPlugin | hasGsp | option         || json
        false     | false  | ''             || false
        false     | true   | ''             || false
        true      | false  | ''             || true
        true      | true   | ''             || false
        true      | true   | '--views=json' || true
        true      | true   | '--views=JSON' || true
        true      | true   | '--views json' || true
        true      | false  | '--views gsp'  || false
        true      | false  | '--views=gsp'  || false
        false     | false  | '--views=json' || true
        false     | true   | '--views=json' || true
        false     | false  | '--views=gsp'  || false

        kind = json ? 'JSON' : 'GSP'
        plugin = hasPlugin ? 'is on the class path' : 'is not on the class path'
        gsp = hasGsp ? 'has a GSP' : 'has no GSP'
    }

    def "a REST API plugin, which has no views at all, is given JSON views"() {
        expect:
        !new File(project, 'grails-app/views').exists()
        runInRestApplication(new GenerateViewsCommand(), 'generate-views com.example.Book')
        generated() == JSON_VIEWS
    }

    def "a GSP anywhere under grails-app/views keeps the GSP views of an application that has the JSON views plugin as well"() {
        given:
        write('grails-app/views/admin/reports/_row.gsp', '<tr></tr>\n')

        expect:
        runInRestApplication(new GenerateViewsCommand(), 'generate-views com.example.Book')
        generated() == (GSP_VIEWS + ['grails-app/views/admin/reports/_row.gsp']).sort()
    }

    @Unroll
    def "#name refuses '#option', says what the option takes and writes nothing"() {
        when:
        boolean result = runInRestApplication(command.getDeclaredConstructor().newInstance(), "${name} com.example.Book ${option}")

        then:
        !result
        errors.toString().contains("The --views option takes gsp or json, as in --views=json, and not: ${given}")
        errors.toString().count('The --views option') == 1
        generated() == []

        where:
        name                  | command                   | option        | given
        'generate-views'      | GenerateViewsCommand      | '--views=xml' | 'xml'
        'generate-controller' | GenerateControllerCommand | '--views=xml' | 'xml'
        'generate-all'        | GenerateAllCommand        | '--views=xml' | 'xml'
        'generate-all'        | GenerateAllCommand        | '--views'     | 'true'
    }

    def "the templates of the project, which install-templates writes, are used for the JSON views and the REST controller too"() {
        given: 'the templates of the plugin in the project'
        assert runInRestApplication(new InstallTemplatesCommand(), 'install-templates')

        expect: 'the templates of a REST API application are among them'
        ['RestController.groovy', 'RestService.groovy', 'RestSpec.groovy', 'RestServiceSpec.groovy', 'index.gson', 'show.gson', '_domain.gson'].every {
            new File(project, "src/main/templates/scaffolding/${it}").isFile()
        }

        when: 'two of them are changed'
        write('src/main/templates/scaffolding/show.gson', 'json { title ${propertyName}.title }\n')
        write('src/main/templates/scaffolding/RestController.groovy', 'package ${packageName}\n\nclass ${className}Controller { /* of the project */ }\n')
        boolean result = runInRestApplication(new GenerateAllCommand(), 'generate-all com.example.Book')

        then:
        result
        written('grails-app/views/book/show.gson') == 'json { title book.title }\n'
        written('grails-app/controllers/com/example/BookController.groovy').contains('/* of the project */')

        and: 'the others are still those of the plugin'
        written('grails-app/views/book/index.gson').contains('json tmpl.book(bookList ?: [])')
    }

    @Unroll
    def "#name in a REST API application keeps the files that are there, and writes them again with --force"() {
        given:
        assert runInRestApplication(command.getDeclaredConstructor().newInstance(), "${name} com.example.Book")
        List<String> files = generated()
        files.each { String path -> new File(project, path).text = 'edited' }

        when:
        boolean kept = runInRestApplication(command.getDeclaredConstructor().newInstance(), "${name} com.example.Book")

        then:
        kept
        files.size() == count
        files.every { String path -> new File(project, path).text == 'edited' }

        when:
        boolean forced = runInRestApplication(command.getDeclaredConstructor().newInstance(), "${name} com.example.Book --force")

        then:
        forced
        files.every { String path -> new File(project, path).text != 'edited' }
        generated() == files

        where:
        name                  | command                   | count
        'generate-views'      | GenerateViewsCommand      | 3
        'generate-controller' | GenerateControllerCommand | 4
        'generate-all'        | GenerateAllCommand        | 7
    }

    def "generate-all in a REST API application generates for a class that is found although another is not, and fails"() {
        when:
        boolean result = runInRestApplication(new GenerateAllCommand(), 'generate-all Missing com.example.Book')

        then:
        !result
        errors.toString().contains('Domain class not found for name: Missing')
        generated() == (REST_CONTROLLER + JSON_VIEWS).sort()
    }

    /**
     * Runs a command in the project of the spec, which has what the class path of the tests has: no JSON views plugin.
     */
    private boolean run(GrailsApplicationCommand command, String line) {
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    /**
     * Runs a command as the build of a REST API application runs it: with the JSON views plugin on the class path.
     */
    private boolean runInRestApplication(GrailsApplicationCommand command, String line) {
        withClassLoader(restApplication) {
            run(command, line)
        }
    }

    private static <T> T withClassLoader(ClassLoader classLoader, Closure<T> action) {
        Thread thread = Thread.currentThread()
        ClassLoader before = thread.contextClassLoader
        thread.contextClassLoader = classLoader
        try {
            return action.call()
        } finally {
            thread.contextClassLoader = before
        }
    }

    private void compile(File classes, List<String> sources) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = classes
        CompilationUnit unit = new CompilationUnit(configuration, null, new GroovyClassLoader(restApplication, configuration))
        sources.each { String path -> unit.addSource(new File(project, path)) }
        withClassLoader(restApplication) {
            unit.compile()
        }
    }

    private static String render(Object engine, String path, Map<String, Object> model) {
        StringWriter writer = new StringWriter()
        engine.resolveTemplate(path).make(model).writeTo(writer)
        writer.toString()
    }

    /**
     * Whether a generated file is what Grails 8 generated once what Grails 9 changed in it is taken back.
     */
    private boolean sameAsGrails8(String path, Closure<String> withoutTheChanges) {
        assert sha256(withoutTheChanges.call(written(path).replace('\r\n', '\n'))) == GRAILS_8[path]: "${path} is not what Grails 8 generated"
        true
    }

    private static String sha256(String text) {
        MessageDigest.getInstance('SHA-256').digest(text.replace('\r\n', '\n').getBytes('UTF-8')).encodeHex().toString()
    }

    private void write(String path, String text) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = text
    }

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
