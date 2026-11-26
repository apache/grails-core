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

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.control.messages.WarningMessage

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * Where the source of a tool is decides what it is compiled against, and a tool is one of two kinds:
 * the compiler refuses a tool of the wrong kind for its folder, a class that is both kinds or neither,
 * and a tool whose startsApplication it cannot read, except in a folder of tests. It warns of a class
 * that implements a deprecated type.
 */
class ToolLocationEnforcementSpec extends Specification {

    private static final String APPLICATION = 'org.apache.grails.core.cli.ApplicationTool'
    private static final String STANDALONE = 'org.apache.grails.core.cli.StandaloneTool'
    private static final String TOOL = 'org.apache.grails.core.cli.GrailsTool'
    private static final String SUPPORT = 'org.apache.grails.core.cli.ToolSupport'
    private static final String HANDLE = 'boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }'

    private static final String NOT_READ = 'startsApplication of RenderTool has to be the constant true or false, where it is declared ' +
            'or in every constructor: the build reads it without running the tool.'

    @TempDir
    File tempDir

    @Unroll
    def "a standalone tool in #folder does not compile, as a standalone tool is compiled without the classes of the project"() {
        when:
        Compiled compiled = compile("${folder}/example/RenderTool.groovy", "class RenderTool implements ${STANDALONE} { ${HANDLE} }")

        then:
        compiled.errors == [("RenderTool is a StandaloneTool, and ${folder}/example/RenderTool.groovy is not in src/standalone: " +
                                    'a standalone tool is compiled without the classes of the project. ' +
                                    'Move it to src/standalone/groovy, or implement ApplicationTool to keep it here.').toString()]

        where:
        folder << ['src/cli/groovy', 'grails-app/commands', 'grails-app/services', 'src/main/groovy']
    }

    def "an application tool in src/standalone does not compile, as the application and its classes are not there"() {
        when:
        Compiled compiled = compile('src/standalone/groovy/example/RenderTool.groovy', "class RenderTool implements ${APPLICATION} { ${HANDLE} }")

        then:
        compiled.errors == ['RenderTool is an ApplicationTool, and src/standalone/groovy/example/RenderTool.groovy is in src/standalone, ' +
                                    'which has neither the application nor its classes. Move it to src/cli/groovy, or implement StandaloneTool.']
    }

    @Unroll
    def "a class that implements both kinds of tool does not compile, in #folder"() {
        when:
        Compiled compiled = compile("${folder}/example/RenderTool.groovy", "class RenderTool implements ${APPLICATION}, ${STANDALONE} { ${HANDLE} }")

        then:
        compiled.errors == ['RenderTool implements ApplicationTool and StandaloneTool: a tool is one of the two.']

        where:
        folder << ['src/cli/groovy', 'src/standalone/groovy']
    }

    @Unroll
    def "a tool that is neither kind does not compile, and is told the two kinds and the folder of each: #type in #folder"() {
        when:
        Compiled compiled = compile("${folder}/example/RenderTool.groovy", "class RenderTool implements ${type} { ${members} }")

        then:
        compiled.errors == ['RenderTool is neither an ApplicationTool nor a StandaloneTool, and a tool is one of the two: ' +
                                    'an ApplicationTool, in src/cli/groovy, is run by the build with the classes of the project; ' +
                                    'a StandaloneTool, in src/standalone/groovy, is run as a process of its own without them.']

        where:
        type    | folder                  | members
        TOOL    | 'src/cli/groovy'        | HANDLE
        TOOL    | 'src/standalone/groovy' | HANDLE
        SUPPORT | 'src/cli/groovy'        | 'boolean handle() { true }'
        SUPPORT | 'src/standalone/groovy' | 'boolean handle() { true }'
    }

    def "an application tool whose startsApplication cannot be read does not compile, and in src/standalone it is told both"() {
        given:
        String source = "class RenderTool implements ${APPLICATION} { boolean startsApplication = Boolean.getBoolean('render.app'); ${HANDLE} }"

        expect:
        compile('src/cli/groovy/example/RenderTool.groovy', source).errors == [NOT_READ]
        compile('src/standalone/groovy/example/RenderTool.groovy', source).errors == [
                'RenderTool is an ApplicationTool, and src/standalone/groovy/example/RenderTool.groovy is in src/standalone, ' +
                        'which has neither the application nor its classes. Move it to src/cli/groovy, or implement StandaloneTool.',
                NOT_READ]
    }

    @Unroll
    def "a tool of the kind its folder has compiles and is registered: #type in #folder"() {
        when:
        Compiled compiled = compile("${folder}/example/RenderTool.groovy", "class RenderTool implements ${type} { ${HANDLE} }")

        then:
        compiled.errors.isEmpty()
        compiled.registered == ['example.RenderTool']
        compiled.described

        where:
        type        | folder
        APPLICATION | 'src/cli/groovy'
        APPLICATION | 'grails-app/commands'
        APPLICATION | 'src/main/groovy'
        STANDALONE  | 'src/standalone/groovy'
    }

    def "a class that is not a tool itself, abstract or an adapter, is not held to the rules of a tool"() {
        expect:
        compile('src/standalone/groovy/example/RenderTool.groovy', "abstract class RenderTool implements ${APPLICATION} { }").errors.isEmpty()
        compile('src/cli/groovy/example/RenderTool.groovy', "abstract class RenderTool implements ${STANDALONE} { }").errors.isEmpty()
        compile('src/cli/groovy/example/RenderTool.groovy',
                "class RenderTool implements ${TOOL}, org.apache.grails.core.cli.ApplicationCommandTargetAware { Object getTarget() { null }; ${HANDLE} }").errors.isEmpty()
    }

    @Unroll
    def "in a folder of tests nothing is refused: #folder"() {
        when:
        List<Compiled> compiled = [
                "class RenderTool implements ${STANDALONE} { boolean startsApplication = Boolean.getBoolean('x'); ${HANDLE} }",
                "class RenderTool implements ${APPLICATION}, ${STANDALONE} { ${HANDLE} }",
                "class RenderTool implements ${TOOL} { ${HANDLE} }",
                "class RenderTool implements ${APPLICATION} { boolean startsApplication = Boolean.getBoolean('x'); ${HANDLE} }",
        ].collect { String source -> compile("${folder}/example/RenderTool.groovy", source) }

        then:
        compiled*.errors.every { it.isEmpty() }

        and: 'a tool of src/test is registered, as it is a source of the project; one whose startsApplication cannot be read has no descriptor'
        compiled*.registered == (registered ? [['example.RenderTool']] * 4 : [[]] * 4)
        compiled*.described == (registered ? [true, true, true, false] : [false] * 4)

        where:
        folder                       || registered
        'src/test/groovy'            || true
        'src/integration-test/groovy' || false
        'src/testFixtures/groovy'    || false
        'src/test-cli/groovy'        || false
    }

    def "a source without a file is not held to the rules of a tool, and is not registered"() {
        given:
        File classes = new File(tempDir, 'build/classes/groovy/cli')
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: classes))
        unit.addSource(new File(tempDir, 'src/cli/groovy/example/RenderTool.groovy').path, "package example\nclass RenderTool implements ${STANDALONE} { ${HANDLE} }")

        when:
        unit.compile(Phases.CANONICALIZATION)

        then:
        noExceptionThrown()
        !new File(classes, CommandFactoriesTransformation.CLI_FACTORIES_LOCATION).exists()
    }

    @Unroll
    def "a class that implements #type itself compiles with a warning that names what to implement instead"() {
        when:
        Compiled compiled = compile('src/cli/groovy/example/OldTool.groovy', "${abstractness}class OldTool implements org.apache.grails.core.cli.${type} { ${members} }")

        then:
        compiled.errors.isEmpty()
        compiled.warnings == [("OldTool implements ${type}, which is deprecated: implement ApplicationTool " +
                                      '(GrailsApplicationCommand: ApplicationTool, ToolSupport). Nothing else changes.').toString()]

        and: 'a build that does not show the warnings of the compiler, as Gradle, shows the standard error'
        compiled.err.readLines() == ["${compiled.source.path}:2: warning: ${compiled.warnings[0]}".toString()]

        where:
        type                       | abstractness | members
        'ApplicationCommand'       | ''           | HANDLE
        'GrailsApplicationCommand' | ''           | 'boolean handle() { true }'
        'ApplicationCommand'       | 'abstract '  | ''
    }

    @Unroll
    def "no warning is given for #what"() {
        when:
        Compiled compiled = compile("${folder}/example/OldTool.groovy", source)

        then:
        compiled.errors.isEmpty()
        compiled.warnings.isEmpty()
        compiled.err.isEmpty()

        where:
        what                                               | folder            | source
        'an application tool'                              | 'src/cli/groovy'  | "class OldTool implements ${APPLICATION}, ${SUPPORT} { boolean handle() { true } }"
        'a deprecated type in a folder of tests'           | 'src/test/groovy' | "class OldTool implements org.apache.grails.core.cli.GrailsApplicationCommand { boolean handle() { true } }"
    }

    def "of a class that implements a deprecated type and one that extends it, the first is warned of"() {
        when:
        Compiled compiled = compile('src/cli/groovy/example/OldTool.groovy',
                "abstract class BaseTool implements org.apache.grails.core.cli.ApplicationCommand { }\nclass OldTool extends BaseTool { ${HANDLE} }")

        then:
        compiled.errors.isEmpty()
        compiled.warnings == ['BaseTool implements ApplicationCommand, which is deprecated: implement ApplicationTool ' +
                                      '(GrailsApplicationCommand: ApplicationTool, ToolSupport). Nothing else changes.']
    }

    def "the warnings follow the level the compilation is configured with"() {
        when:
        Compiled compiled = compile('src/cli/groovy/example/OldTool.groovy',
                "class OldTool implements org.apache.grails.core.cli.ApplicationCommand { ${HANDLE} }", WarningMessage.NONE)

        then:
        compiled.errors.isEmpty()
        compiled.warnings.isEmpty()
        compiled.err.isEmpty()
    }

    private Compiled compile(String path, String source, int warningLevel = WarningMessage.LIKELY_ERRORS) {
        File sourceFile = new File(tempDir, path)
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package example\n${source}"
        File classes = new File(tempDir, "build/classes/${path.replace('/', '-')}")
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: classes, warningLevel: warningLevel,
                disabledGlobalASTTransformations: [
                        'org.grails.compiler.injection.GlobalGrailsClassInjectorTransformation',
                        'org.grails.compiler.injection.GlobalImportTransformation',
                ] as Set))
        unit.addSource(sourceFile)
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        PrintStream original = System.err
        List<String> errors = []
        System.err = new PrintStream(err, true, 'UTF-8')
        try {
            unit.compile(Phases.CANONICALIZATION)
        } catch (MultipleCompilationErrorsException e) {
            errors = e.errorCollector.errors.collect { Message message -> ((SyntaxErrorMessage) message).cause.originalMessage }
        } finally {
            System.err = original
        }
        File factories = new File(classes, CommandFactoriesTransformation.CLI_FACTORIES_LOCATION)
        Properties registrations = new Properties()
        if (factories.isFile()) {
            factories.withInputStream { registrations.load(it) }
        }
        new Compiled(
                source: sourceFile,
                errors: errors,
                warnings: (unit.errorCollector.warnings ?: []).collect { WarningMessage warning -> warning.message },
                err: err.toString('UTF-8'),
                registered: registrations.getProperty(TOOL)?.tokenize(',') ?: [],
                described: new File(classes, 'META-INF/grails-tools/example.RenderTool.json').isFile())
    }

    static class Compiled {
        File source
        List<String> errors
        List<String> warnings
        String err
        List<String> registered
        boolean described
    }
}
