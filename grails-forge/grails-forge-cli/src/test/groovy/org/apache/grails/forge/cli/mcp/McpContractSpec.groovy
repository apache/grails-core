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
package org.apache.grails.forge.cli.mcp

import java.util.concurrent.Callable

import org.apache.grails.forge.cli.console.CommandStreams
import org.apache.grails.forge.cli.console.ConsoleCommands
import spock.lang.Specification
import spock.lang.Timeout

/**
 * What the parts of {@code grails mcp} are made against: the names the server gives itself and its tools,
 * a tool and the tools of a directory as the protocol shows them without its library, the streams a
 * command line that is run in the server's process writes to, and the kind of session the commands run in.
 */
class McpContractSpec extends Specification {

    void cleanup() {
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
    }

    void "the server and what it adds to the protocol have fixed names"() {
        expect:
        McpNames.SERVER_NAME == 'grails'
        McpNames.SERVER_TITLE == 'Grails'
        McpNames.META_COMMAND == 'org.apache.grails/command'
        McpNames.META_RUN == 'org.apache.grails/run'
        McpNames.META_RESULT == 'org.apache.grails/result'
        McpNames.REFRESH_TOOLS == 'refresh_tools'
        McpNames.LOG_DIRECTORY == 'mcp'
        McpHandler.TOOLS == 'tools'
        McpHandler.RESOURCES == 'resources'
        McpException.INVALID_PARAMS == -32602
        McpException.INTERNAL_ERROR == -32603
        Lane.values()*.name() == ['BUILD', 'GENERATOR', 'DIRECT']
    }

    void "a command is the tool of its words joined by an underscore, and a command of one word, or a tool, keeps its name"() {
        expect:
        McpNames.toolName(path) == name

        where:
        path                    || name
        ['create-controller']   || 'create-controller'
        ['feature', 'add']      || 'feature_add'
        ['project', 'show']     || 'project_show'
        ['dbm-update']          || 'dbm-update'
    }

    void "a command has at least one word"() {
        when:
        McpNames.toolName([])

        then:
        thrown(IllegalArgumentException)
    }

    void "a tool holds copies of its schema, annotations and _meta that cannot be changed, and maps the arguments of a call to a command line"() {
        given:
        Map<String, Object> schema = [type: 'object', properties: [name: [type: 'string']], required: ['name']]
        List<Map<String, Object>> received = []
        McpTool tool = new McpTool('create-controller', 'grails create-controller', 'Creates a controller', schema, null, [(McpNames.META_COMMAND): [name: 'create-controller']],
                Lane.DIRECT, new McpTool.Mapping() {
            @Override
            List<String> commandLine(Map<String, Object> arguments) throws McpException {
                received << arguments
                if (arguments.name == '-x') {
                    throw new McpException(McpException.INVALID_PARAMS, 'name: a value that begins with - is refused')
                }
                return ['create-controller', arguments.name as String]
            }

            @Override
            boolean jsonResult(Map<String, Object> arguments) {
                return false
            }
        })

        when:
        ((Map) schema.get('properties')).name.type = 'integer'

        then: 'what was given to make it is copied'
        ((Map) tool.inputSchema.get('properties')).name.type == 'string'
        tool.annotations == [:]
        tool.meta[McpNames.META_COMMAND] == [name: 'create-controller']
        tool.lane == Lane.DIRECT
        tool.toString() == 'create-controller'

        when:
        ((Map) tool.inputSchema.get('properties')).name.type = 'integer'

        then: 'and nothing of it can be changed, however deep'
        thrown(UnsupportedOperationException)

        when:
        tool.inputSchema.required << 'other'

        then:
        thrown(UnsupportedOperationException)

        when:
        List<String> commandLine = tool.commandLine([name: 'Book'])

        then:
        commandLine == ['create-controller', 'Book']
        !tool.jsonResult(null)

        when:
        commandLine << '--force'

        then:
        thrown(UnsupportedOperationException)

        when: 'a call without arguments is one with none'
        tool.commandLine(null)

        then:
        received.last() == [:]

        when:
        tool.commandLine([name: '-x'])

        then: 'arguments the tool cannot be given are said with what is wrong with them'
        McpException e = thrown()
        e.code == McpException.INVALID_PARAMS
        e.message == 'name: a value that begins with - is refused'
    }

    void "the tools of a directory, and the help document they were made from, cannot be changed"() {
        given:
        McpTool tool = new McpTool('stats', 'grails stats', 'Shows statistics', [type: 'object'], [readOnlyHint: true], null, Lane.DIRECT, new McpTool.Mapping() {
            @Override
            List<String> commandLine(Map<String, Object> arguments) {
                return ['stats']
            }

            @Override
            boolean jsonResult(Map<String, Object> arguments) {
                return false
            }
        })
        List<McpTool> tools = [tool]
        McpToolSet set = new McpToolSet(tools, [format: 1, commands: [[name: 'stats']]], true, 'fingerprint')

        when:
        tools << tool

        then:
        set.tools == [tool]
        set.project
        set.fingerprint == 'fingerprint'
        set.helpDocument.commands*.name == ['stats']

        when:
        set.helpDocument.commands << [name: 'more']

        then:
        thrown(UnsupportedOperationException)

        when:
        set.tools << tool

        then:
        thrown(UnsupportedOperationException)
    }

    void "a command writes where the process does unless streams are bound to its thread"() {
        given:
        PrintStream out = System.out
        PrintStream err = System.err
        PrintStream otherOut = new PrintStream(new ByteArrayOutputStream(), true)

        expect:
        CommandStreams.current().out.is(System.out)
        CommandStreams.current().err.is(System.err)
        CommandStreams.processErr().is(System.err)

        when: 'the process writes elsewhere now'
        System.setOut(otherOut)

        then:
        CommandStreams.current().out.is(otherOut)

        cleanup:
        System.setOut(out)
        System.setErr(err)
    }

    @Timeout(30)
    void "streams bound to a thread are where it, and the threads it starts, write while what it runs runs, and what was bound before is bound again"() {
        given:
        CommandStreams outer = CommandStreams.capture()
        CommandStreams inner = CommandStreams.capture()
        List<Object> seen = []

        when:
        String result = CommandStreams.bind(outer, {
            seen << CommandStreams.current()
            CommandStreams.bind(inner, {
                seen << CommandStreams.current()
                Thread started = new Thread({ CommandStreams.current().out.print('from a thread it started') })
                started.start()
                started.join()
            } as Callable<Object>)
            seen << CommandStreams.current()
            return 'given'
        } as Callable<String>)

        then:
        result == 'given'
        seen[0].is(outer)
        seen[1].is(inner)
        seen[2].is(outer)
        inner.standardOutput() == 'from a thread it started'
        outer.merged() == ''
        CommandStreams.current().out.is(System.out)

        when: 'what runs fails'
        CommandStreams.bind(outer, { throw new IOException('failed') } as Callable<Object>)

        then: 'it fails as it did, and nothing stays bound'
        IOException e = thrown()
        e.message == 'failed'
        CommandStreams.current().out.is(System.out)
    }

    @Timeout(30)
    void "captured streams keep everything in the order it was written, and the output and the errors apart"() {
        given:
        CommandStreams streams = CommandStreams.capture()

        when:
        streams.out.println('one')
        streams.err.println('two, which is an error')
        streams.out.print('three \u00e9')

        then:
        streams.merged().readLines() == ['one', 'two, which is an error', 'three \u00e9']
        streams.standardOutput().readLines() == ['one', 'three \u00e9']
        streams.standardError().readLines() == ['two, which is an error']

        when: 'several threads write at once'
        CommandStreams busy = CommandStreams.capture()
        List<Thread> writers = (1..4).collect { int number ->
            new Thread({ 200.times { int line -> (line % 2 == 0 ? busy.out : busy.err).println("writer ${number} line ${line}") } })
        }
        writers*.start()
        writers*.join()

        then: 'no line is lost or broken'
        busy.merged().readLines().size() == 800
        busy.merged().readLines().every { it ==~ /writer \d line \d+/ }
        busy.standardOutput().readLines().size() == 400
        busy.standardError().readLines().size() == 400
    }

    void "the streams of the process keep nothing"() {
        when:
        CommandStreams.current().merged()

        then:
        thrown(IllegalStateException)
    }

    void "the commands run in one command line, the interactive shell or grails mcp, and only the shell is interactive"() {
        expect:
        ConsoleCommands.session == ConsoleCommands.Session.NONE
        !ConsoleCommands.interactive

        when:
        ConsoleCommands.setSession(ConsoleCommands.Session.MCP)

        then:
        ConsoleCommands.session == ConsoleCommands.Session.MCP
        !ConsoleCommands.interactive

        when:
        ConsoleCommands.setInteractive(true)

        then:
        ConsoleCommands.session == ConsoleCommands.Session.CONSOLE
        ConsoleCommands.interactive

        when:
        ConsoleCommands.setSession(null)

        then:
        ConsoleCommands.session == ConsoleCommands.Session.NONE
        ConsoleCommands.Session.values()*.name() == ['NONE', 'CONSOLE', 'MCP']
    }
}
