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
package org.apache.grails.forge.cli.console

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import groovy.json.JsonOutput
import org.apache.grails.gradle.model.cli.ToolResultFile
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * The result a tool gives besides what it prints ({@code executionContext.result}): the class that runs
 * the tool writes it to {@code .grails/run/tool-result-<n>.json}, beside the file of arguments it was given,
 * and the command line reads it when the tool has ended, removes the file, and keeps it for whoever ran the
 * command line in its process, which takes it with {@link ToolResult#take}. At the terminal nothing is said
 * of it. The wrapper of the project here is no Gradle: it writes what a test gives it where the class that
 * runs a tool writes the result.
 */
@IgnoreIf({ os.windows })
class ToolResultHandOverSpec extends CommandSpec {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File run
    File toGive

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        new File(dir, 'grails-app').mkdirs()
        run = new File(dir, RunDirectory.LOCATION)
        toGive = new File(dir, 'result-to-give')
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '''\
            #!/bin/sh
            here="$(dirname "$0")"
            for argument in "$@"; do
                case "$argument" in
                    --args-file=*) arguments="$here/${argument#--args-file=}" ;;
                esac
            done
            number="$(basename "$arguments" .json | sed 's/^command-args-//')"
            result="$(dirname "$arguments")/tool-result-$number.json"
            if [ -e "$result" ] || [ -L "$result" ]; then echo "a result was there before the tool ran"; fi
            if [ -f "$here/result-to-give" ]; then cp "$here/result-to-give" "$result"; fi
            if [ -f "$here/link-to-give" ]; then ln -s "$here/secret.json" "$result"; fi
            echo "the tool ran"
            '''.stripIndent()
        wrapper.setExecutable(true, false)
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.setText(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: [
                [name: 'count-todos', aliases: [], description: 'Counts the TODOs', origin: 'application', target: 'application', registration: 'descriptor',
                 parameters: [[label: 'FOLDER', arity: '0..1', type: 'string']]]]]), StandardCharsets.US_ASCII.name())
        catalog.setLastModified(System.currentTimeMillis() + 60_000)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void "the result a tool gives is taken by whoever ran the command line, once, and the file of it is gone"() {
        given:
        toGive.text = '{"count":3,"files":["a.groovy","b.groovy"],"ratio":0.5,"done":true,"note":null}'
        CommandStreams streams = CommandStreams.capture()

        when:
        int exit = ForgeCli.execute(ForgeContext.of(beanContext), streams, '--plain-output', '--non-interactive', 'count-todos', 'grails-app')
        ToolResult result = ToolResult.take(streams)

        then: 'what the tool printed is its output, and the result is not in it'
        exit == 0
        streams.standardOutput().readLines() == ['the tool ran']
        !streams.merged().contains('a.groovy')

        and: 'the result as it was written, as JSON and as values'
        result.json == '{"count":3,"files":["a.groovy","b.groovy"],"ratio":0.5,"done":true,"note":null}'
        result.value == [count: 3L, files: ['a.groovy', 'b.groovy'], ratio: 0.5G, done: true, note: null]
        ((Map) result.value).keySet() as List == ['count', 'files', 'ratio', 'done', 'note']

        and: 'it is taken once, and nothing of it is left in the project'
        ToolResult.take(streams) == null
        run.list().findAll { it.startsWith('tool-result-') || it.startsWith('command-args-') } == []
    }

    void "at the terminal nothing is said of the result, and the file of it is gone"() {
        given:
        toGive.text = '[1,2,3]'

        when:
        int exit = ForgeCli.execute(beanContext, 'count-todos')

        then:
        exit == 0
        out.toString().readLines() == ['the tool ran']
        !err.toString().contains('result')
        run.list().findAll { it.startsWith('tool-result-') } == []
    }

    void "a result an earlier tool of the same number left is removed before the tool runs, and is not taken for its result"() {
        given:
        run.mkdirs()
        Files.setPosixFilePermissions(run.toPath(), PosixFilePermissions.fromString('rwx------'))
        new File(run, 'tool-result-0.json').text = '{"stale":true}'
        CommandStreams streams = CommandStreams.capture()

        when:
        int exit = ForgeCli.execute(ForgeContext.of(beanContext), streams, '--plain-output', 'count-todos')

        then:
        exit == 0
        streams.standardOutput().readLines() == ['the tool ran']
        ToolResult.take(streams) == null
        !new File(run, 'tool-result-0.json').exists()
    }

    @Unroll
    void "a result that #what is not given, one line says why, and the file is removed"() {
        given:
        new File(dir, 'secret.json').text = '{"secret":true}'
        prepare.call(dir, toGive)
        CommandStreams streams = CommandStreams.capture()

        when:
        int exit = ForgeCli.execute(ForgeContext.of(beanContext), streams, '--plain-output', 'count-todos')

        then: 'the exit code is the tool\'s'
        exit == 0
        ToolResult.take(streams) == null
        streams.standardError().readLines().contains("| Warning count-todos: the result it set is not given, since .grails/run/tool-result-0.json ${why}".toString())
        !new File(run, 'tool-result-0.json').exists() && !Files.isSymbolicLink(new File(run, 'tool-result-0.json').toPath())
        new File(dir, 'secret.json').text == '{"secret":true}'

        where:
        what                     | why                                          | prepare
        'is a link'              | 'is a link'                                  | { File project, File given -> new File(project, 'link-to-give').text = '' }
        'is not JSON'            | 'is not JSON'                                | { File project, File given -> given.text = "{'single':'quotes'}" }
        'has something after it' | 'has something after the JSON document'      | { File project, File given -> given.text = '{"a":1}{"b":2}' }
        'has a key twice'        | 'has an object with the same key twice'      | { File project, File given -> given.text = '{"a":1,"a":2}' }
        'is not ASCII'           | 'is not the ASCII a result is written in'    | { File project, File given -> given.setText('{"name":"\u00e9"}', 'UTF-8') }
        'is nested too deep'     | 'is nested deeper than 32'                   | { File project, File given -> given.text = '[' * 33 + ']' * 33 }
        'is too long'            | 'has more than the 262144 bytes a result has' | { File project, File given -> given.text = '"' + 'x' * (256 * 1024) + '"' }
    }

    void "the numbers the reader of a result holds to are those of the class that writes it"() {
        expect:
        ToolResult.MAX_BYTES == ToolResultFile.MAX_BYTES
        ToolResult.MAX_DEPTH == ToolResultFile.MAX_DEPTH
        ToolResult.PREFIX == ToolResultFile.PREFIX
        ToolResult.EXTENSION == ToolResultFile.EXTENSION
    }

    void "a result written by the class that writes it is read as it was written"() {
        given:
        File written = new File(dir, 'written.json')
        ToolResultFile.write(written, [count: 2, names: ['a', 'b'], nested: [deep: [1L, 2.5d]], text: 'line\nbreak "quoted" \u00e9'])
        toGive.bytes = written.bytes
        CommandStreams streams = CommandStreams.capture()

        when:
        ForgeCli.execute(ForgeContext.of(beanContext), streams, '--plain-output', 'count-todos')
        ToolResult result = ToolResult.take(streams)

        then:
        result.json == written.getText('US-ASCII')
        result.value == [count: 2L, names: ['a', 'b'], nested: [deep: [1L, 2.5G]], text: 'line\nbreak "quoted" \u00e9']
    }
}
