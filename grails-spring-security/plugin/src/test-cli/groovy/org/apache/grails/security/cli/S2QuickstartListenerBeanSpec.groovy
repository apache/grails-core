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

package org.apache.grails.security.cli

import groovy.io.FileType

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.build.logging.GrailsConsole
import grails.core.DefaultGrailsApplication
import grails.spring.BeanBuilder
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.ToolSupport
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.script.ScriptConsole
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.grails.spring.RuntimeSpringConfigUtilities
import org.grails.spring.RuntimeSpringConfiguration

/**
 * Where {@code s2-quickstart} registers the password encoder listener, read back as an application reads it: the
 * specs run {@code resources.groovy} and build the beans of the closure that {@code beans} has when the script
 * ends, which is what a starting application does, and call {@code doWithSpring()} of a plugin class. A user that
 * is saved without the listener keeps its password as it was given, so the command may only report success when
 * the listener is among those beans.
 */
@Issue('https://github.com/apache/grails-core/issues/16348')
class S2QuickstartListenerBeanSpec extends Specification {

    private static final String RESOURCES = 'grails-app/conf/spring/resources.groovy'
    private static final String PLUGIN_CLASS = 'src/main/groovy/demo/DemoGrailsPlugin.groovy'
    private static final String LISTENER = 'com.example.UserPasswordEncoderListener'
    private static final String BEAN = 'userPasswordEncoderListener'
    private static final String QUICKSTART = 's2-quickstart com.example User Role'

    @TempDir
    File project

    @TempDir
    File classes

    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    GrailsConsole console

    def setup() {
        console = GrailsConsole.getInstance()
        GrailsConsole.setInstance(new ScriptConsole(new PrintStream(new ByteArrayOutputStream(), true), new PrintStream(errors, true)))
        write('grails-app/conf/application.yml', 'grails:\n    profile: web\n')
    }

    def cleanup() {
        GrailsConsole.setInstance(console)
        RuntimeSpringConfigUtilities.reset()
    }

    def "a project without resources.groovy gets one whose beans have the listener"() {
        when:
        boolean result = run(QUICKSTART)

        then:
        result
        errors.toString() == ''
        beans() == [(BEAN): LISTENER]
    }

    @Unroll
    def "the listener is a bean of an application whose resources.groovy is #what"() {
        given:
        write(RESOURCES, text)

        when:
        boolean result = run(QUICKSTART)

        then:
        result
        errors.toString() == ''
        beans() == expected + [(BEAN): LISTENER]

        and: 'a second run finds the bean and leaves the file as it is'
        String first = written(RESOURCES)
        run(QUICKSTART + ' --force')
        written(RESOURCES) == first

        where:
        what                                           | text                                                                    || expected
        'empty'                                        | ''                                                                      || [:]
        'blank lines'                                  | '\n\n'                                                                  || [:]
        'a comment'                                    | '// Place your Spring DSL code here\n'                                  || [:]
        'imports alone'                                | 'import java.time.Clock\n'                                              || [:]
        'one closure'                                  | 'beans = {\n    marker(Object)\n}\n'                                    || [marker: 'java.lang.Object']
        'one closure in a package'                     | 'package spring\n\nbeans = {\n    marker(Object)\n}\n'                  || [marker: 'java.lang.Object']
        'an empty closure on one line'                 | 'beans = {}\n'                                                          || [:]
        'a closure on one line'                        | 'beans = { marker(Object) }\n'                                          || [marker: 'java.lang.Object']
        'a closure with an arrow'                      | 'beans = { ->\n    marker(Object)\n}\n'                                 || [marker: 'java.lang.Object']
        'a closure with an arrow on the next line'     | 'beans = {\n    ->\n    marker(Object)\n}\n'                            || [marker: 'java.lang.Object']
        'a closure with a comment after its brace'     | 'beans = { // the beans\n    marker(Object)\n}\n'                       || [marker: 'java.lang.Object']
        'a closure opened on the line after beans ='   | 'beans =\n{\n    marker(Object)\n}\n'                                   || [marker: 'java.lang.Object']
        'a closure that sets beans on a bean'          | 'beans = {\n    marker(HashMap) { beans = 1 }\n}\n'                     || [marker: 'java.util.HashMap']
        'a closure with the listener in a comment'     | "beans = {\n    // ${BEAN}(UserPasswordEncoderListener)\n}\n"           || [:]
        'a closure that names the class in full'       | "beans = {\n    ${BEAN}(${LISTENER})\n}\n"                              || [:]
        'a closure that has the listener with a block' | "import ${LISTENER}\n\nbeans = {\n    ${BEAN}(UserPasswordEncoderListener) { bean ->\n    }\n}\n" || [:]
    }

    def "the listener goes to the closure an application reads when resources.groovy assigns beans twice"() {
        given: 'the shape of the finding: Grails reads the value beans has when the script ends'
        write(RESOURCES, 'beans = {\n}\nbeans = {\n    marker(Object)\n}\n')

        when:
        boolean result = run(QUICKSTART)

        then:
        result
        beans() == [marker: 'java.lang.Object', (BEAN): LISTENER]
        written(RESOURCES).readLines() == [
                'import com.example.UserPasswordEncoderListener',
                'beans = {',
                '}',
                'beans = {',
                "    ${BEAN}(UserPasswordEncoderListener)",
                '    marker(Object)',
                '}'
        ]
    }

    def "a listener that only a replaced closure has is added to the closure an application reads"() {
        given: 'what the command wrote before into the first of two closures'
        write(RESOURCES, "import ${LISTENER}\nbeans = {\n    ${BEAN}(UserPasswordEncoderListener)\n}\nbeans = {\n    marker(Object)\n}\n")

        when:
        boolean result = run(QUICKSTART)

        then:
        result
        beans() == [marker: 'java.lang.Object', (BEAN): LISTENER]
        written(RESOURCES).count("import ${LISTENER}") == 1
    }

    @Unroll
    def "s2-quickstart fails, says what to add and writes nothing when #what, and runs once the file is corrected"() {
        given:
        write(RESOURCES, text)
        Map<String, String> before = snapshot()

        when:
        boolean result = run(QUICKSTART)

        then: 'no class, no listener, no bean and no setting is written'
        !result
        snapshot() == before
        errors.toString().contains('so the command wrote nothing')
        errors.toString().contains("The password encoder listener is not registered: ${RESOURCES} ${said}")
        errors.toString().contains("'${BEAN}(UserPasswordEncoderListener)'")
        errors.toString().contains("'import ${LISTENER}'")
        errors.toString().contains('passwords are stored as they are given')

        when: 'the file is corrected and the same command is run again, without --force'
        errors.reset()
        write(RESOURCES, 'beans = {\n    marker(Object)\n}\n')
        result = run(QUICKSTART)

        then: 'nothing of the run that failed is in its way'
        result
        errors.toString() == ''
        beans() == [marker: 'java.lang.Object', (BEAN): LISTENER]
        new File(project, 'grails-app/domain/com/example/User.groovy').isFile()
        written('grails-app/conf/application.yml').count('userDomainClassName') == 1

        where:
        what                                            | text                                                                              || said
        'beans is assigned in a condition'              | 'beans = {\n}\nif (System.getenv("CI")) {\n    beans = {\n    }\n}\n'             || "sets or reads 'beans' in line 4"
        'beans is only assigned in a condition'         | 'if (System.getenv("CI")) {\n    beans = {\n    }\n}\n'                           || "sets or reads 'beans' in line 2"
        'beans is assigned something else last'         | 'def others = { }\nbeans = {\n}\nbeans = others\n'                                || "sets or reads 'beans' in line 4"
        'beans is composed'                             | 'beans = {\n}\nbeans = beans << { marker(Object) }\n'                             || "sets or reads 'beans' in line 3"
        'beans is a closure that is cast'               | 'beans = {\n}\nbeans = { marker(Object) } as Closure\n'                           || "sets or reads 'beans' in line 3"
        'beans is set through the binding'              | 'beans = {\n}\nbinding.setVariable("beans", { marker(Object) })\n'                || "sets or reads 'beans' in line 3"
        'beans is set as a property of the script'      | 'beans = {\n}\nthis.beans = { marker(Object) }\n'                                 || "sets or reads 'beans' in line 3"
        'beans is set in a method of the script'        | 'beans = {\n}\nvoid replace() {\n    beans = { marker(Object) }\n}\nreplace()\n'  || "sets or reads 'beans' in line 4"
        'beans is set in a closure that is called'      | 'beans = {\n}\n[1].each { beans = { marker(Object) } }\n'                         || "sets or reads 'beans' in line 3"
        'the script returns before the last assignment' | 'beans = {\n    marker(Object)\n}\nif (System.getenv("CI")) {\n    return\n}\nbeans = {\n}\n' || 'returns in line 5'
        'beans is a method call'                        | 'beans {\n}\n'                                                                    || "sets or reads 'beans' in line 1"
        'beans is a local variable'                     | 'def beans = {\n}\n'                                                              || "sets or reads 'beans' in line 1"
        'the script has code but no beans'              | 'println "no beans"\n'                                                            || "has no line with 'beans = {'"
        'the file is no Groovy'                         | 'beans = {\n    marker(Object\n}\n'                                               || 'cannot be read as Groovy'
        'the bean is of another class'                  | "beans = {\n    ${BEAN}(Object)\n}\n"                                             || "has a bean '${BEAN}' of another class in line 2"
        'the closure has an arrow in its parameters'    | 'beans = { one, two = { -> 1 } -> marker(Object) }\n'                             || 'has a closure in line 1 that the command cannot add a line to'
    }

    def "a closure whose first line the command cannot add to is refused, not written to"() {
        given: 'a comment that the line after the brace would be part of'
        String text = 'beans = { /* the beans\n    of the application */\n    marker(Object)\n}\n'
        write(RESOURCES, text)
        Map<String, String> before = snapshot()

        when:
        boolean result = run(QUICKSTART)

        then:
        !result
        snapshot() == before
        errors.toString().contains("${RESOURCES} has a closure in line 1 that the command cannot add a line to")

        when: 'the bean is added by hand, as the command says, and the command is run again'
        errors.reset()
        write(RESOURCES, "import ${LISTENER}\n\nbeans = { /* the beans\n    of the application */\n    ${BEAN}(UserPasswordEncoderListener)\n    marker(Object)\n}\n")
        String byHand = written(RESOURCES)
        result = run(QUICKSTART)

        then:
        result
        errors.toString() == ''
        written(RESOURCES) == byHand
        beans() == [(BEAN): LISTENER, marker: 'java.lang.Object']
    }

    @Unroll
    def "the listener is a bean of a plugin whose doWithSpring() #what"() {
        given:
        write(PLUGIN_CLASS, "package demo\n\nimport grails.plugins.Plugin\n\nclass DemoGrailsPlugin extends Plugin {\n\n${method}\n}\n")

        when:
        boolean result = run(QUICKSTART)

        then:
        result
        errors.toString() == ''
        !new File(project, RESOURCES).exists()
        pluginBeans() == expected + [(BEAN): LISTENER]

        and: 'a second run finds the bean and leaves the file as it is'
        String first = written(PLUGIN_CLASS)
        run(QUICKSTART + ' --force')
        written(PLUGIN_CLASS) == first

        where:
        what                                 | method                                                                                         || expected
        'opens the closure on its line'      | '    Closure doWithSpring() { {->\n            marker(Object)\n        }\n    }'               || [marker: 'java.lang.Object']
        'opens the closure on the next line' | '    Closure doWithSpring() {\n        { ->\n            marker(Object)\n        }\n    }'     || [marker: 'java.lang.Object']
        'returns the closure'                | '    Closure doWithSpring() {\n        return { ->\n            marker(Object)\n        }\n    }' || [marker: 'java.lang.Object']
        'is written on one line'             | '    Closure doWithSpring() { { -> } }'                                                        || [:]
        'comes after a comment that has one' | '    // doWithSpring() { -> beans }\n    Closure doWithSpring() { {->\n        }\n    }'       || [:]
    }

    @Unroll
    def "s2-quickstart fails and writes nothing when doWithSpring() #what, and runs once the plugin class is corrected"() {
        given:
        String head = 'package demo\n\nimport grails.plugins.Plugin\n\nclass DemoGrailsPlugin extends Plugin {\n\n'
        write(PLUGIN_CLASS, "${head}${method}\n}\n")
        Map<String, String> before = snapshot()

        when:
        boolean result = run(QUICKSTART)

        then:
        !result
        snapshot() == before
        errors.toString().contains("The password encoder listener is not registered: ${PLUGIN_CLASS} ${said}")
        errors.toString().contains("'${BEAN}(UserPasswordEncoderListener)'")
        errors.toString().contains('so the command wrote nothing')

        when: 'the class is corrected and the same command is run again, without --force'
        errors.reset()
        write(PLUGIN_CLASS, "${head}    Closure doWithSpring() { {->\n            marker(Object)\n        }\n    }\n}\n")
        result = run(QUICKSTART)

        then:
        result
        errors.toString() == ''
        pluginBeans() == [marker: 'java.lang.Object', (BEAN): LISTENER]
        written('grails-app/conf/application.yml').count('userDomainClassName') == 1

        where:
        what                           | method                                                                                                    || said
        'returns one of two closures'  | '    Closure doWithSpring() {\n        if (System.getenv("CI")) {\n            return { -> }\n        }\n        return { ->\n        }\n    }' || "has no closure that 'doWithSpring()' returns"
        'returns a closure it keeps'   | '    Closure doWithSpring() {\n        Closure kept = { -> }\n        kept\n    }'                         || "has no closure that 'doWithSpring()' returns"
        'is missing'                   | '    void doWithApplicationContext() {\n        Closure unused = { -> }\n    }'                            || "has no closure that 'doWithSpring()' returns"
        'is in a class that is broken' | '    Closure doWithSpring() { {->\n    }'                                                                 || 'cannot be read as Groovy'
    }

    private boolean run(String line) {
        ToolSupport command = new S2QuickstartCommand()
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    /**
     * Runs {@code resources.groovy} of the project and builds the beans of the closure it leaves in {@code beans},
     * with the loader an application reads the file with when it starts.
     *
     * @return the class name of each bean, by the name of the bean
     */
    private Map<String, String> beans() {
        withSources { GroovyClassLoader loader ->
            RuntimeSpringConfiguration configuration = new DefaultRuntimeSpringConfiguration()
            Class<?> script = loader.parseClass(new GroovyCodeSource(new File(project, RESOURCES).toURI().toURL()))
            RuntimeSpringConfigUtilities.reloadSpringResourcesConfig(configuration, new DefaultGrailsApplication(), script)
            classNames(configuration)
        }
    }

    /**
     * Builds the beans of the closure that {@code doWithSpring()} of the plugin class of the project returns.
     *
     * @return the class name of each bean, by the name of the bean
     */
    private Map<String, String> pluginBeans() {
        withSources { GroovyClassLoader loader ->
            RuntimeSpringConfiguration configuration = new DefaultRuntimeSpringConfiguration()
            Object plugin = loader.parseClass(new File(project, PLUGIN_CLASS)).getDeclaredConstructor().newInstance()
            new BeanBuilder(null, configuration, loader).beans(plugin.doWithSpring() as Closure)
            classNames(configuration)
        }
    }

    private static Map<String, String> classNames(RuntimeSpringConfiguration configuration) {
        configuration.beanNames.collectEntries { String name ->
            [(name): configuration.getBeanConfig(name).beanDefinition.beanClassName]
        }
    }

    /**
     * Does the work with a class loader that has the listener class. The class the command generated is compiled by
     * the build of the project, with the transformations of GORM, so the loader gets a class of its name in its
     * place, after a check that the command generated it: which bean definitions are read is what the specs are
     * about, not what the listener does.
     */
    private <T> T withSources(Closure<T> work) {
        assert new File(project, 'src/main/groovy/com/example/UserPasswordEncoderListener.groovy').isFile()
        File source = new File(classes, 'com/example/UserPasswordEncoderListener.groovy')
        source.parentFile.mkdirs()
        source.text = 'package com.example\n\nclass UserPasswordEncoderListener {\n}\n'
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader)
        loader.addClasspath(classes.path)
        ClassLoader previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader
        try {
            work.call(loader)
        } finally {
            Thread.currentThread().contextClassLoader = previous
            loader.close()
        }
    }

    /**
     * @return every file of the project, by its path in the project
     */
    private Map<String, String> snapshot() {
        Map<String, String> files = [:]
        project.eachFileRecurse(FileType.FILES) { File file ->
            files.put(project.toPath().relativize(file.toPath()).toString().replace(File.separator, '/'), file.text)
        }
        files
    }

    private void write(String path, String text) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    private String written(String path) {
        File file = new File(project, path)
        assert file.isFile()
        file.text
    }
}
