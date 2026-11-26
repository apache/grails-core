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
import org.apache.grails.core.cli.ProjectInfo
import org.apache.grails.core.cli.ToolSupport
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.script.ScriptConsole
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser

/**
 * Which kind of views {@code generate-all}, {@code generate-controller} and {@code generate-views} write: GSP views
 * and the controller for pages, or JSON views and the REST controller. The first of these that applies decides:
 *
 * <ol>
 *   <li>the {@code --views} option;</li>
 *   <li>the features in the record of the command line, which a project it generated or migrated has: {@code gsp}
 *   gives GSP views, {@code views-json} without {@code gsp} JSON views, neither GSP views;</li>
 *   <li>without a record, the views of the project: a GSP under {@code grails-app/views} gives GSP views, a JSON view
 *   and no GSP there JSON views;</li>
 *   <li>GSP views.</li>
 * </ol>
 *
 * <p>The records are those the command line writes for a web and for a REST API application, which the build hands
 * over. The tool reads the record of the project from the directory it is given, as the command line gives it the
 * directory of the project it runs in.</p>
 */
class ScaffoldingViewsChoiceSpec extends Specification {

    private static final List<String> JSON_VIEWS = ['_book.gson', 'index.gson', 'show.gson']

    private static final List<String> GSP_VIEWS = ['create.gsp', 'edit.gsp', 'index.gsp', 'show.gsp']

    private static final String CONTROLLER = 'grails-app/controllers/com/example/BookController.groovy'

    @TempDir
    File project

    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    GrailsConsole console

    def setup() {
        console = GrailsConsole.getInstance()
        GrailsConsole.setInstance(new ScriptConsole(new PrintStream(new ByteArrayOutputStream(), true), new PrintStream(errors, true)))
        write('grails-app/domain/com/example/Book.groovy', 'package com.example\n\nclass Book {\n    String title\n}\n')
    }

    def cleanup() {
        GrailsConsole.setInstance(console)
    }

    def "the samples are the records of a web application with GSP views and of a REST API application with JSON views"() {
        given:
        ProjectInfo web = ProjectInfo.of(new File(samples(), 'web'))
        ProjectInfo rest = ProjectInfo.of(new File(samples(), 'rest-api'))

        expect:
        web.recorded
        web.applicationType == 'web'
        'gsp' in web.features
        !('views-json' in web.features)

        rest.recorded
        rest.applicationType == 'rest-api'
        'views-json' in rest.features
        !('gsp' in rest.features)
    }

    @Unroll
    def "--views=#option decides in #what, whatever its record and its views say"() {
        given:
        if (type) {
            record(type)
        }
        views.each { String view -> write("grails-app/views/${view}", '') }

        when:
        boolean result = run(new GenerateAllTool(), "generate-all com.example.Book --views=${option}")

        then:
        result
        errors.toString() == ''
        generatedViews() == (option == 'json' ? JSON_VIEWS : GSP_VIEWS)
        restController() == (option == 'json')

        where:
        type       | views                 | option
        'web'      | ['layouts/main.gsp']  | 'json'
        'rest-api' | ['error.gson']        | 'gsp'
        null       | ['error.gson']        | 'gsp'
        null       | ['layouts/main.gsp']  | 'json'
        null       | []                    | 'json'

        what = type ? "a ${type} application" : 'a project without a record'
    }

    @Unroll
    def "a project whose record has #features is given #kind views, whatever views it has: #views"() {
        given:
        record(type, added, removed)
        views.each { String view -> write("grails-app/views/${view}", '') }

        when:
        boolean result = run(new GenerateAllTool(), 'generate-all com.example.Book')

        then:
        result
        generatedViews() == (json ? JSON_VIEWS : GSP_VIEWS)
        restController() == json

        where:
        type       | added   | removed | views                 || json
        'web'      | []      | []      | []                    || false
        'web'      | []      | []      | ['error.gson']        || false
        'rest-api' | []      | []      | []                    || true
        'rest-api' | []      | []      | ['layouts/main.gsp']  || true
        'rest-api' | ['gsp'] | []      | []                    || false
        'web'      | []      | ['gsp'] | ['error.gson']        || false

        features = describe(type, added, removed)
        kind = json ? 'JSON' : 'GSP'
    }

    @Unroll
    def "a project without a record is given #kind views where grails-app/views has #views"() {
        given:
        views.each { String view -> write("grails-app/views/${view}", '') }

        when:
        boolean result = run(new GenerateAllTool(), 'generate-all com.example.Book')

        then:
        result
        generatedViews() == (json ? JSON_VIEWS : GSP_VIEWS)
        restController() == json

        where:
        views                                    || json
        ['error.gson']                           || true
        ['error.gson', 'notes.txt']              || true
        ['author/list.gson', 'layouts/main.gsp'] || false
        ['admin/reports/_row.gsp']               || false
        ['notes.txt']                            || false
        []                                       || false

        kind = json ? 'JSON' : 'GSP'
    }

    def "a project with no grails-app/views and no record is given GSP views"() {
        expect:
        !new File(project, 'grails-app/views').exists()
        run(new GenerateViewsTool(), 'generate-views com.example.Book')
        generatedViews() == GSP_VIEWS
    }

    @Unroll
    def "a record that is #what is no record, and the views of the project decide"() {
        given:
        write('.grails/forge.lock.yml', content)
        write('grails-app/views/error.gson', 'json {\n}\n')

        expect:
        !ProjectInfo.of(project).recorded
        run(new GenerateViewsTool(), 'generate-views com.example.Book')
        generatedViews() == JSON_VIEWS

        where:
        what                    | content
        'not YAML'              | ': : :'
        'of another format'     | recordText('web').replace('format: 1\n', 'format: 2\n')
        'without features'      | 'format: 1\napplicationType: web\n'
    }

    @Unroll
    def "#name decides the kind of #what by the record of the project"() {
        given:
        record('rest-api')

        when:
        boolean result = run(command.getDeclaredConstructor().newInstance(), "${name} com.example.Book")

        then:
        result
        generatedViews() == views
        new File(project, CONTROLLER).isFile() == controller
        !controller || restController()

        where:
        name                  | command                   | what                           | views      | controller
        'generate-views'      | GenerateViewsTool      | 'its views'                    | JSON_VIEWS | false
        'generate-controller' | GenerateControllerTool | 'its controller'               | []         | true
        'generate-all'        | GenerateAllTool        | 'its views and its controller' | JSON_VIEWS | true
    }

    def "the JSON views plugin on the class path does not make a project a REST API application"() {
        given: 'the class path a REST API application has, which a tool that runs on its own does not see'
        String plugins = System.getProperty('scaffolding.restApplicationPlugins')
        assert plugins: 'the build hands the class path of the JSON views plugin to the tests'
        URLClassLoader restApplication = new URLClassLoader(
                plugins.split(File.pathSeparator).collect { new File(it).toURI().toURL() } as URL[], getClass().classLoader)
        assert restApplication.getResource('grails/plugin/json/view/JsonViewGrailsPlugin.class') != null

        when:
        boolean result = withClassLoader(restApplication) {
            run(new GenerateViewsTool(), 'generate-views com.example.Book')
        }

        then:
        result
        generatedViews() == GSP_VIEWS

        cleanup:
        restApplication?.close()
    }

    /**
     * Runs a tool in the project of the spec, as the command line runs it in a project: the tool is given the
     * directory of the project, and reads what the project is from the record in it.
     */
    private boolean run(ToolSupport tool, String line) {
        File directory = project
        tool.executionContext = new ExecutionContext(new CommandLineParser().parseString(line)) {

            @Override
            File getBaseDir() {
                directory
            }

            @Override
            ProjectInfo getProject() {
                ProjectInfo.of(directory)
            }
        }
        tool.templateRenderer = new TemplateRendererImpl(project)
        tool.fileSystemInteraction = new FileSystemInteractionImpl(project)
        tool.handle()
    }

    /**
     * Writes the record the command line writes for an application of the type into the project, with the features
     * added and those removed.
     */
    private void record(String type, List<String> added = [], List<String> removed = []) {
        String text = recordText(type)
        removed.each { String feature ->
            String entry = "- name: ${feature}\n  source: bundled-pack\n"
            assert text.contains(entry)
            text = text.replace(entry, '')
        }
        added.each { String feature ->
            text = text.replace('features:\n', "features:\n- name: ${feature}\n  source: bundled-pack\n")
        }
        write('.grails/forge.lock.yml', text)
        ProjectInfo written = ProjectInfo.of(project)
        assert written.recorded
        assert added.every { it in written.features } && removed.every { !(it in written.features) }
    }

    private static String recordText(String type) {
        new File(samples(), "${type}/.grails/forge.lock.yml").getText('UTF-8').replace('\r\n', '\n')
    }

    private static File samples() {
        String directory = System.getProperty('scaffolding.projectRecordSamples')
        assert directory: 'the build hands the directory of the sample records of the command line to the tests'
        new File(directory)
    }

    private static String describe(String type, List<String> added, List<String> removed) {
        List<String> features = recordText(type).readLines()
                .findAll { it.startsWith('- name: ') }
                .collect { it.substring('- name: '.length()) }
        features.addAll(added)
        features.removeAll(removed)
        boolean gsp = 'gsp' in features
        boolean json = 'views-json' in features
        gsp && json ? 'gsp and views-json' : gsp ? 'gsp' : json ? 'views-json and not gsp' : 'neither gsp nor views-json'
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

    private void write(String path, String text) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    /**
     * @return the names of the views generated for the domain class, in order
     */
    private List<String> generatedViews() {
        File views = new File(project, 'grails-app/views/book')
        views.isDirectory() ? views.list().toList().sort() : []
    }

    private boolean restController() {
        new File(project, CONTROLLER).text.contains("static responseFormats = ['json']")
    }
}
