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

import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.messages.WarningMessage
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * How the author of a tool finds out, from the compiler, what the tool lacks for help, completion and
 * agents: a description, the declaration of the arguments it reads, and whether it reads what is typed.
 * These are warnings, which never fail a build; what the tool lacks, its hints included, is written into
 * its descriptor as {@code incomplete}. {@code readsInput} is read from the source as
 * {@code startsApplication} is.
 */
class ToolChecksSpec extends Specification {

    private static final String IMPORTS = 'import org.apache.grails.core.cli.ToolOption\nimport org.apache.grails.core.cli.ToolParameter\n' +
            'import org.apache.grails.core.cli.ToolHints\n'
    private static final String APPLICATION = 'org.apache.grails.core.cli.ApplicationTool'
    private static final String STANDALONE = 'org.apache.grails.core.cli.StandaloneTool'
    private static final String SUPPORT = 'org.apache.grails.core.cli.ToolSupport'
    private static final String CONTEXT = 'org.apache.grails.core.cli.ExecutionContext'
    private static final String CLI = 'src/cli/groovy/example/ImportTool.groovy'

    private static final String NO_DESCRIPTION = "ImportTool has no description: help and agents show only its name. Set description = '...'"
    private static final String NOT_CONSTANT = 'ImportTool has no description that can be read without running it: help and agents show only its name. ' +
            "Set description = '...' to a constant"
    private static final String NOT_ONE_LINE = 'ImportTool has a description that is not one line of at most 512 characters that can be shown: ' +
            "help and agents show only its name. Set description = '...' to one line"
    private static final String UNDECLARED = 'ImportTool reads its arguments without declaring them: declare them with @ToolOption and @ToolParameter, ' +
            'so that help, completion and agents know them'
    private static final String READS_TYPED = 'ImportTool reads what is typed: declare boolean readsInput = true, ' +
            'so that --non-interactive and agents do not run it without input'
    private static final String READS_INPUT_NOT_READ = 'readsInput of ImportTool has to be the constant true or false, where it is declared or in every ' +
            'constructor: the build reads it without running the tool.'

    @TempDir
    File dir

    def "a tool that has a description, declares what it reads and gives its hints lacks nothing, and is not warned of"() {
        when:
        ToolCompilation compiled = compile(IMPORTS + """
@ToolHints(readOnlyHint = true)
class ImportTool implements ${APPLICATION}, ${SUPPORT} {
    String description = 'Imports the data'
    @ToolParameter(index = 0, label = 'FILE') String file
    boolean handle() { println args; true }
}
""")

        then:
        compiled.errors.isEmpty()
        compiled.warnings.isEmpty()
        compiled.err.isEmpty()
        !compiled.descriptor('example.ImportTool').containsKey('incomplete')
    }

    def "a tool written as before declarations compiles, with warnings, and its descriptor says what it lacks"() {
        when:
        ToolCompilation compiled = compile("""
class ImportTool implements ${APPLICATION} {
    boolean handle(${CONTEXT} executionContext) {
        println executionContext.commandLine.remainingArgs
        new Scanner(System.in).nextLine()
        true
    }
}
""")

        then: 'it compiles: a warning never fails a build'
        compiled.errors.isEmpty()
        compiled.warnings == [NO_DESCRIPTION, UNDECLARED, READS_TYPED]

        and: 'a build that does not show the warnings of the compiler, as Gradle, shows the standard error'
        compiled.err.readLines() == compiled.warnings.collect { "${compiled.source.path}:3: warning: ${it}".toString() }

        and: 'its descriptor says what it lacks, and that it does not read what is typed, as it does not say so'
        compiled.descriptor('example.ImportTool').incomplete == ['description', 'arguments', 'hints']
        !compiled.descriptor('example.ImportTool').containsKey('readsInput')
    }

    @Unroll
    def "a description that is #kind is warned of, and is what the tool lacks"() {
        when:
        ToolCompilation compiled = compile("class ImportTool implements ${APPLICATION} { ${member}; boolean handle(${CONTEXT} context) { true } }")

        then:
        compiled.errors.isEmpty()
        compiled.warnings == [warning]
        compiled.descriptor('example.ImportTool').incomplete == ['description', 'hints']

        where:
        kind                  | member                                                   || warning
        'missing'             | 'int count'                                              || NO_DESCRIPTION
        'empty'               | "String description = ''"                                || NO_DESCRIPTION
        'blank'               | "String description = '   '"                             || NO_DESCRIPTION
        'worked out'          | "String description = 'Imports ' + System.getenv('X')"  || NOT_CONSTANT
        'from a method'       | "String getDescription() { 'Imports ' + name }"         || NOT_CONSTANT
        'of two lines'        | "String description = 'Imports\\nthe data'"              || NOT_ONE_LINE
        'of 513 characters'   | "String description = '${'d' * 513}'"                    || NOT_ONE_LINE
    }

    @Unroll
    def "a tool whose own code reads its arguments by #how, and declares none, is warned of"() {
        when:
        ToolCompilation compiled = compile("class ImportTool implements ${APPLICATION}, ${SUPPORT} { String description = 'Imports'; ${code} }")

        then:
        compiled.errors.isEmpty()
        compiled.warnings == [UNDECLARED]
        compiled.descriptor('example.ImportTool').incomplete == ['arguments', 'hints']

        where:
        how                             | code
        'args'                          | 'boolean handle() { println args[0]; true }'
        'getArgs()'                     | 'boolean handle() { println getArgs(); true }'
        'executionContext.commandLine'  | 'boolean handle() { println executionContext.commandLine; true }'
        'commandLine.remainingArgs'     | 'boolean handle() { def line = executionContext.getCommandLine(); println line.remainingArgs; true }'
        'optionValue(...)'              | "boolean handle() { println executionContext.commandLine.optionValue('force'); true }"
        'hasOption(...)'                | "boolean handle() { println executionContext.commandLine.hasOption('force'); true }"
        'a closure'                     | 'boolean handle() { [1].each { println args }; true }'
        'a helper method'               | 'boolean handle() { check(); true }\nprivate void check() { assert !args.isEmpty() }'
    }

    @Unroll
    def "a tool is not warned of reading its arguments when #why"() {
        when:
        ToolCompilation compiled = compile(IMPORTS + "class ImportTool implements ${APPLICATION}, ${SUPPORT} { String description = 'Imports'; ${code} }")

        then:
        compiled.errors.isEmpty()
        compiled.warnings.isEmpty()
        !(compiled.descriptor('example.ImportTool').incomplete as List).contains('arguments')

        where:
        why                                    | code
        'it reads none'                        | 'boolean handle() { println templates; true }'
        'args is a local variable'             | "boolean handle() { def args = ['a']; println args; true }"
        'args is a parameter'                  | 'boolean handle() { greet(["a"]); true }\nprivate void greet(List<String> args) { println args }'
        'it declares a parameter'              | "@ToolParameter(index = 0, label = 'FILE') String file\nboolean handle() { println args; true }"
    }

    def "a tool that declares an option on its class reads its arguments as it declares"() {
        when:
        ToolCompilation compiled = compile(IMPORTS + """
@ToolOption(names = ['--force'], arity = '0')
class ImportTool implements ${APPLICATION}, ${SUPPORT} {
    String description = 'Imports'
    boolean handle() { println executionContext.commandLine.hasOption('force'); true }
}
""")

        then:
        compiled.warnings.isEmpty()
        compiled.descriptor('example.ImportTool').incomplete == ['hints']
    }

    @Unroll
    def "a tool that reads what is typed with #how, and does not say so, is warned of"() {
        when:
        ToolCompilation compiled = compile("class ImportTool implements ${STANDALONE} { String description = 'Imports'; " +
                "boolean handle(${CONTEXT} context) { ${code}; true } }", 'src/standalone/groovy/example/ImportTool.groovy')

        then:
        compiled.errors.isEmpty()
        compiled.warnings == [READS_TYPED]

        where:
        how                | code
        'System.in'        | 'System.in.read()'
        'a reader of it'   | 'new BufferedReader(new InputStreamReader(System.in)).readLine()'
        'System.console()' | 'System.console()?.readLine()'
    }

    @Unroll
    def "readsInput is read from #where, written when it is true, and keeps the tool from being warned of reading what is typed"() {
        when:
        ToolCompilation compiled = compile("class ImportTool implements ${APPLICATION} { String description = 'Imports'\n${members}\n" +
                "boolean handle(${CONTEXT} context) { System.in.read(); true } }")

        then:
        compiled.errors.isEmpty()
        compiled.warnings == (readsInput ? [] : [READS_TYPED])
        compiled.descriptor('example.ImportTool').readsInput == (readsInput ? true : null)

        where:
        where                         | members                                                           || readsInput
        'the property'                | 'boolean readsInput = true'                                       || true
        'a final property'            | 'final boolean readsInput = true'                                 || true
        'a constructor'               | 'ImportTool() { readsInput = true }'                              || true
        'the setter in a constructor' | 'ImportTool() { setReadsInput(true) }'                            || true
        'a method'                    | 'boolean isReadsInput() { true }'                                 || true
        'the property, false'         | 'boolean readsInput = false'                                      || false
        'nothing'                     | ''                                                                || false
    }

    def "a readsInput that cannot be read does not compile"() {
        when:
        ToolCompilation compiled = compile("class ImportTool implements ${APPLICATION} { String description = 'Imports'\n" +
                "boolean readsInput = Boolean.getBoolean('import.asks')\nboolean handle(${CONTEXT} context) { true } }")

        then:
        compiled.errors == [READS_INPUT_NOT_READ]
        !compiled.descriptorFile('example.ImportTool').exists()
    }

    def "a tool that sets its properties by names it works out compiles as it did: it does not read what is typed"() {
        when:
        ToolCompilation compiled = compile("class ImportTool implements ${STANDALONE} { String description = 'Imports'\n" +
                "ImportTool() { setProperty('description', 'Imports the data') }\nboolean handle(${CONTEXT} context) { true } }",
                'src/standalone/groovy/example/ImportTool.groovy')

        then:
        compiled.errors.isEmpty()
        !compiled.descriptor('example.ImportTool').containsKey('readsInput')
    }

    def "a tool that has hints does not lack them"() {
        when:
        ToolCompilation compiled = compile(IMPORTS + "@ToolHints\nclass ImportTool implements ${APPLICATION} { boolean handle(${CONTEXT} context) { true } }")

        then:
        compiled.warnings == [NO_DESCRIPTION]
        compiled.descriptor('example.ImportTool').incomplete == ['description']
        compiled.descriptor('example.ImportTool').hints == [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: true]
    }

    def "the warnings follow the level the compilation is configured with, and are not given for a tool of a folder of tests"() {
        given:
        String source = "class ImportTool implements ${APPLICATION} { boolean handle(${CONTEXT} context) { println context.commandLine; true } }"

        expect:
        ToolCompilation.compile(dir, CLI, source, [], WarningMessage.NONE).warnings.isEmpty()
        ToolCompilation.compile(new File(dir, 'tests'), 'src/test/groovy/example/ImportTool.groovy', source).warnings.isEmpty()
    }

    def "an abstract class is not warned of: the tools that extend it are"() {
        when:
        ToolCompilation compiled = compile("abstract class BaseTool implements ${APPLICATION} { boolean handle(${CONTEXT} context) { println context.commandLine; true } }\n" +
                "class ImportTool extends BaseTool { String description = 'Imports' }")

        then:
        compiled.errors.isEmpty()
        compiled.warnings.isEmpty()
    }

    def "the classes a tool is compiled to run as they did"() {
        when:
        ToolCompilation compiled = ToolCompilation.compile(dir, CLI, "class ImportTool implements ${APPLICATION} { boolean readsInput = true; " +
                "String description = 'Imports'; boolean handle(${CONTEXT} context) { true } }", [], WarningMessage.LIKELY_ERRORS, Phases.ALL)

        then:
        compiled.errors.isEmpty()
        new URLClassLoader([compiled.classes.toURI().toURL()] as URL[], ToolChecksSpec.classLoader).withCloseable { URLClassLoader loader ->
            loader.loadClass('example.ImportTool').getDeclaredConstructor().newInstance().readsInput
        }
    }

    private ToolCompilation compile(String source, String path = CLI) {
        ToolCompilation.compile(dir, path, source)
    }
}
