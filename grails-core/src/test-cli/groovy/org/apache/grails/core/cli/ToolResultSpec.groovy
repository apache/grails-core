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
package org.apache.grails.core.cli

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

import groovy.json.JsonOutput

import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.messages.WarningMessage
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.apache.grails.core.cli.compiler.ToolCompilation

/**
 * What a tool sets as {@code executionContext.result} is given, as JSON, to the program that started it
 * with its arguments in a file {@code command-args-<n>.json}: in the file {@code tool-result-<n>.json}
 * beside it, which only the user can read, once the tool has returned. A result an earlier tool of the
 * number left is removed first; a result that is not JSON, or too large, is not given, and one line says
 * why. Nothing else the tool does changes.
 */
class ToolResultSpec extends Specification {

    private static final String TOOLS = '''
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolParameter

@ToolHints(readOnlyHint = true)
class ResultTool implements StandaloneTool {
    String description = 'Gives a result'
    @ToolParameter(index = 0, label = 'KIND', description = 'The result to give') String kind
    boolean handle(ExecutionContext executionContext) {
        switch (kind) {
            case 'map':
                int count = 3
                executionContext.result = [count: count, files: ['a.txt', 'b.txt'], ratio: 0.5d, exact: 1.25G, big: 12345678901234L,
                                           nested: [ok: true, none: null], text: "made ${count} files", unicode: 'é 日本']
                break
            case 'list': executionContext.result = [1, 'two', false]; break
            case 'text': executionContext.result = 'done'; break
            case 'number': executionContext.result = 42; break
            case 'none': break
            case 'failed': executionContext.result = [error: 'no data']; return false
            case 'throws': executionContext.result = [error: 'thrown']; throw new IllegalStateException('it fails')
            case 'object': executionContext.result = [when: new Date(0)]; break
            case 'key': executionContext.result = [(1): 'one']; break
            case 'nan': executionContext.result = [value: Double.NaN]; break
            case 'large': executionContext.result = 'x' * (256 * 1024); break
            case 'deep': def deep = []; def at = deep; 40.times { def inner = []; at << inner; at = inner }; executionContext.result = deep; break
        }
        println "RAN ${kind}"
        true
    }
}
'''

    @Shared
    File dir = Files.createTempDirectory('tool-result').toFile()

    @Shared
    ToolCompilation compiled

    def setupSpec() {
        compiled = ToolCompilation.compile(dir, 'src/standalone/groovy/example/ResultTool.groovy', TOOLS, [], WarningMessage.LIKELY_ERRORS, Phases.ALL)
    }

    def cleanupSpec() {
        dir.deleteDir()
    }

    def setup() {
        new File(dir, '.grails/run').deleteDir()
        new File(dir, '.grails/run').mkdirs()
    }

    @Unroll
    def "a result of #kind is written as JSON beside the file of arguments, with its number"() {
        when:
        Map<String, Object> result = run(7, kind)

        then:
        compiled.errors.isEmpty()
        result.exit == 0
        result.out.readLines() == ["RAN ${kind}".toString()]
        result.err == ''
        resultFile(7).getText('US-ASCII') == json

        where:
        kind     || json
        'map'    || '{"count":3,"files":["a.txt","b.txt"],"ratio":0.5,"exact":1.25,"big":12345678901234,"nested":{"ok":true,"none":null},' +
                    '"text":"made 3 files","unicode":"\\u00e9 \\u65e5\\u672c"}'
        'list'   || '[1,"two",false]'
        'text'   || '"done"'
        'number' || '42'
    }

    @Requires({ !System.getProperty('os.name').toLowerCase(Locale.ROOT).contains('windows') })
    def "the file of a result is the user's alone"() {
        when:
        run(1, 'map')

        then:
        Files.getPosixFilePermissions(resultFile(1).toPath()) == [PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE] as Set
    }

    def "a tool that sets no result gives none, and what an earlier tool of the number left is removed"() {
        given:
        resultFile(2).text = '{"stale":true}'

        when:
        Map<String, Object> result = run(2, 'none')

        then:
        result.exit == 0
        !resultFile(2).exists()
    }

    @Requires({ !System.getProperty('os.name').toLowerCase(Locale.ROOT).contains('windows') })
    def "a link where the result goes is removed, and what it leads to is neither read nor written"() {
        given:
        File elsewhere = new File(dir, 'elsewhere.json')
        elsewhere.text = 'kept'
        Files.createSymbolicLink(resultFile(3).toPath(), elsewhere.toPath())

        when:
        Map<String, Object> result = run(3, 'text')

        then:
        result.exit == 0
        !Files.isSymbolicLink(resultFile(3).toPath())
        resultFile(3).text == '"done"'
        elsewhere.text == 'kept'
    }

    def "a tool that returns false gives its result, and one that throws gives none"() {
        when:
        Map<String, Object> failed = run(4, 'failed')
        Map<String, Object> thrown = run(5, 'throws')

        then:
        failed.exit == 1
        resultFile(4).text == '{"error":"no data"}'
        thrown.exit == 1
        thrown.err.readLines() == ['Command execution error: it fails']
        !resultFile(5).exists()
    }

    @Unroll
    def "a result that #what is not given, the tool is told why on one line, and it ends as it would have"() {
        when:
        Map<String, Object> result = run(6, kind)

        then:
        result.exit == 0
        result.out.readLines() == ["RAN ${kind}".toString()]
        result.err.readLines() == ["| Warning result: the result it set is not given, since it ${why}".toString()]
        !resultFile(6).exists()

        where:
        what                           | kind     || why
        'has something that is no JSON' | 'object' || 'is not made of maps, lists, texts, numbers, booleans and null'
        'has a key that is not text'   | 'key'    || 'has a map with a key that is not text'
        'has a number JSON lacks'      | 'nan'    || 'has a number that JSON does not have: NaN or an infinity'
        'is too large'                 | 'large'  || 'has more than 262144 bytes as JSON'
        'is nested too deep'           | 'deep'   || 'is nested more than 32 deep'
    }

    def "a tool whose arguments are not in a file named for a result, or not in a file, gives its result to nobody"() {
        given:
        File other = new File(dir, '.grails/run/arguments.json')
        other.text = JsonOutput.toJson(['result', 'map'])

        when:
        Map<String, Object> named = ToolArgumentsReport.run(dir, compiled.classes, ["--grails-args-file=${other.path}".toString()])
        Map<String, Object> given = ToolArgumentsReport.run(dir, compiled.classes, ['result', 'map'])

        then:
        named.exit == 0
        given.exit == 0
        new File(dir, '.grails/run').list().toList().sort() == ['arguments.json']
    }

    private Map<String, Object> run(int number, String kind) {
        File file = new File(dir, ".grails/run/command-args-${number}.json")
        file.setText(JsonOutput.toJson(['result', kind]), 'UTF-8')
        ToolArgumentsReport.run(dir, compiled.classes, ["--grails-args-file=${file.path}".toString()])
    }

    private File resultFile(int number) {
        new File(dir, ".grails/run/tool-result-${number}.json")
    }
}
