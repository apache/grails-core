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
package org.apache.grails.forge.cli.mcp.run

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.Supplier

import groovy.json.JsonSlurper
import io.modelcontextprotocol.modern.McpException as ProtocolError
import io.modelcontextprotocol.modern.McpSchema.CallToolResult
import io.modelcontextprotocol.modern.McpSchema.ProgressParams
import io.modelcontextprotocol.modern.McpSchema.ResourceLink
import io.modelcontextprotocol.modern.McpSchema.TextContent
import io.modelcontextprotocol.modern.McpSchema.TextResourceContents
import io.modelcontextprotocol.modern.client.McpSyncClient
import io.modelcontextprotocol.modern.client.McpSyncSubscriptionListener
import io.modelcontextprotocol.modern.client.McpTimeoutException
import io.modelcontextprotocol.modern.client.SubscriptionEvent
import org.apache.grails.forge.cli.console.ConsoleCommands
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.PlainOutput
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.protocol.InProcessMcp
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeContext
import org.springframework.context.ApplicationContext
import spock.lang.IgnoreIf
import spock.lang.Timeout

/**
 * Calls of {@code grails mcp}, as its server runs them in this process, driven by the SDK's own client over
 * pipes: each is a command line of the command line, run with {@code --plain-output --non-interactive} on
 * streams of its own, and its result is what it wrote and how it ended. The project's Gradle wrapper is a
 * script that writes down what it was given and does what a feature asks of it.
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class McpCallSpec extends CommandSpec implements CommandFixture {

    // the Gradle wrapper of the project: it writes down each build it is given, in the directory of the project
    static final String WRAPPER = '''\
        #!/bin/sh
        if [ -f stubborn ]; then trap '' TERM; fi
        case "$*" in
          *testApp*) task=testApp ;;
          *assemble*) task=assemble ;;
          *clean*) task=clean ;;
          *classes*) task=classes ;;
          *dependencies*) task=dependencies ;;
          *) task=other ;;
        esac
        echo "start $task" >> build-calls.txt
        case $task in
          testApp)
            echo $$ > build.pid
            sleep 300 &
            echo $! > child.pid
            echo "the tests run"
            wait
            ;;
          assemble)
            i=0
            while [ $i -lt 5000 ]; do echo "line $i of the build"; i=$((i+1)); done
            ;;
          clean)
            while [ ! -f release ]; do sleep 0.05; done
            echo "cleaned"
            ;;
          *)
            echo "the build ran $task"
            ;;
        esac
        echo "end $task" >> build-calls.txt
        if [ -f fail ]; then echo "the build failed" >&2; exit 1; fi
        '''.stripIndent()

    ByteArrayOutputStream processErr = new ByteArrayOutputStream()
    List<Integer> exits = new CopyOnWriteArrayList<>()
    GrailsMcpServer server
    InProcessMcp mcp

    void setup() {
        dir = dir.canonicalFile
        System.setProperty('user.dir', dir.path)
        GradleBuild.setRunner(GradleBuild.PROCESS)
        ConsoleCommands.setSession(ConsoleCommands.Session.MCP)
        PlainOutput.enter()
    }

    void cleanup() {
        // what the server said of itself, for a feature that failed
        System.err.print(processErr.toString(StandardCharsets.UTF_8.name()))
        mcp?.close()
        server?.close()
        PlainOutput.leave()
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
        GradleBuild.setRunner(null)
        GradleBuild.closeConnections()
    }

    void "a command of the command line runs in the process, and its result is what it wrote, with the whole output kept in the project and linked"() {
        given:
        project()
        serve()

        when:
        CallToolResult result = mcp.client.tool('create-controller').arguments(['controller-name': 'Book']).call()
        Map<String, Object> ran = ran(result)

        then: 'it ran in the directory of the server, as the command line runs it'
        !result.isError()
        text(result).contains('BookController.groovy')
        new File(dir, 'grails-app/controllers/example/grails/BookController.groovy').isFile()

        and: 'the result says what was run, and how it ended'
        ran.exitCode == 0
        ran.commandLine == ['--plain-output', '--non-interactive', 'create-controller', 'Book']
        ran.durationMs instanceof Number
        ran.truncated == false

        and: 'the whole output is a file of the project, owner only, which the result links and the client can read'
        ResourceLink link = (ResourceLink) result.content().find { it instanceof ResourceLink }
        link.uri() == ran.log
        link.mimeType() == 'text/plain'
        File log = new File(URI.create((String) ran.log))
        log.parentFile == new File(dir, '.grails/run/mcp')
        log.name ==~ /[0-9]{8}-[0-9]{6}-[0-9]{3}-create-controller\.log/
        permissions(log.parentFile) == 'rwx------'
        permissions(log) == 'rw-------'
        ((TextResourceContents) mcp.client.resource(link.uri()).read().contents()[0]).text() == text(result)

        when: 'the result of create-tool ends with what lists the new tool'
        CallToolResult tool = mcp.client.tool('create-tool').arguments(['tool-name': 'DataImport']).call()

        then:
        !tool.isError()
        ((TextContent) tool.content()[0]).text().endsWith('\nIt is listed once the project is compiled: call compile or refresh_tools.')
    }

    void "a command whose output is a JSON document gives it as the structured content and as text"() {
        given:
        project()
        serve()

        when:
        CallToolResult result = mcp.client.tool('project_show').call()

        then:
        !result.isError()
        result.structuredContent() instanceof Map
        new JsonSlurper().parseText(((TextContent) result.content()[0]).text()) == result.structuredContent()
        ran(result).commandLine == ['--plain-output', '--non-interactive', 'project', 'show', '--json']

        and: 'the resource of the project is the same document, which the server writes with the same command when it is read'
        mcp.client.resources().list().resources()*.uri() == ['grails://commands', 'grails://project', 'grails://project/health']
        new JsonSlurper().parseText(((TextResourceContents) mcp.client.resource('grails://project').read().contents()[0]).text()) == result.structuredContent()
        new JsonSlurper().parseText(((TextResourceContents) mcp.client.resource('grails://project/health').read().contents()[0]).text()) instanceof Map
    }

    void "the whole outputs of the newest calls are kept, and only files of the directory of the project are read as resources"() {
        given:
        project()
        serve()

        when:
        (OutputLog.KEPT + 5).times { mcp.client.tool('project_show').call() }
        File logs = new File(dir, '.grails/run/mcp')

        then:
        logs.listFiles().count { it.name.endsWith('.log') } == OutputLog.KEPT

        when: 'a file elsewhere is asked for, by a URI of the form of the links'
        File elsewhere = new File(dir, '20261127-000000-000-compile.log')
        elsewhere.text = 'not an output of grails mcp'
        mcp.client.resource(elsewhere.toURI().toString()).read()

        then:
        ProtocolError refused = thrown()
        refused.error().code() == -32602
    }

    void "a call that makes the directory a project changes the tools and the resources, and the client is told of both"() {
        given: 'a server in a directory that is in no project'
        serve()
        BlockingQueue<SubscriptionEvent> events = new LinkedBlockingQueue<>()
        McpSyncClient.Listening listening = mcp.client.listen().toolsChanged().resourcesChanged()
                .open({ SubscriptionEvent event -> events.put(event) } as McpSyncSubscriptionListener)
        assert events.poll(60, TimeUnit.SECONDS) instanceof SubscriptionEvent.Subscribed
        assert !mcp.client.tools().list().tools()*.name().contains('compile')

        when: 'an application is created in the directory itself'
        CallToolResult created = mcp.client.tool('create-app').arguments([name: 'shop', inplace: true]).call()

        then: 'the lists changed before the result came, and have the commands and the resources of a project'
        !created.isError()
        [events.poll(60, TimeUnit.SECONDS), events.poll(60, TimeUnit.SECONDS)]*.class as Set == [SubscriptionEvent.ToolsListChanged, SubscriptionEvent.ResourcesListChanged] as Set
        mcp.client.tools().list().tools()*.name().containsAll(['compile', 'create-controller', 'refresh_tools'])
        mcp.client.resources().list().resources()*.uri().contains('grails://project')

        cleanup:
        listening?.close()
    }

    void "arguments a tool cannot be given are a result that is an error and names the argument, and nothing runs"() {
        given:
        project()
        serve()

        when:
        CallToolResult result = mcp.client.tool('create-controller').arguments(['controller-name': '-rf']).call()

        then:
        result.isError()
        text(result) == 'controller-name: a value that begins with - is refused, so that it is never read as an option'
        !new File(dir, 'grails-app/controllers').listFiles().any { it.name.contains('rf') }
        !new File(dir, '.grails/run/mcp').exists()
    }

    void "an argument that begins with @ reaches the command as it is, and the file it names is not read"() {
        given: 'a file whose line would be the name of a controller, were it read as a file of arguments'
        project()
        serve()
        File names = new File(dir, 'names.txt')
        names.text = 'Leaked\n'

        when:
        CallToolResult result = mcp.client.tool('create-controller').arguments(['controller-name': "@${names.path}".toString()]).call()

        then: 'the command line was given the escaped argument, and the command the name as it was written'
        ran(result).commandLine == ['--plain-output', '--non-interactive', 'create-controller', "@@${names.path}".toString()]
        text(result).startsWith('"@/')
        text(result).contains('is not a name for a class')

        and: 'nothing of the file reached the command, or the result'
        !text(result).contains('Leaked')
        !new File(dir, 'grails-app/controllers/example/grails/LeakedController.groovy').exists()
        dir.listFiles().every { File each -> !each.name.contains('Leaked') }
    }

    void "a command that fails is a result that is an error, with what it said"() {
        given:
        project()
        serve()
        new File(dir, 'fail').text = ''

        when:
        CallToolResult result = mcp.client.tool('compile').call()

        then:
        result.isError()
        text(result).contains('the build failed')
        text(result).contains('Gradle did not succeed: it ended with exit code 1')
        ran(result).exitCode == 1
    }

    void "a long output is cut in its middle, and the result says where the whole of it is"() {
        given:
        project()
        serve()

        when:
        CallToolResult result = mcp.client.tool('assemble').call()
        Map<String, Object> ran = ran(result)
        String said = ((TextContent) result.content()[0]).text()
        File log = new File(URI.create((String) ran.log))

        then: 'the first and the last characters are kept, around a line that says what was left out and where it is'
        !result.isError()
        ran.truncated == true
        said.length() < CommandOutput.SUCCESS_HEAD + CommandOutput.SUCCESS_TAIL + 400
        said.contains('line 0 of the build\n')
        said.contains('line 4999 of the build\n')
        !said.contains('line 1000 of the build\n')
        String cut = said.readLines().find { it.startsWith('[… ') }
        cut ==~ /\[… [0-9,]+ lines \([0-9,]+ characters\) left out: the whole output is .*\.log\]/
        cut.endsWith("the whole output is ${log.path}]".toString())

        and: 'the file has all of it'
        (0..<5000).every { int line -> log.text.contains("line ${line} of the build\n".toString()) }
        result.content().any { it instanceof ResourceLink && ((ResourceLink) it).uri() == ran.log }

        when: 'the same output of a build that fails keeps less, and more of its end'
        new File(dir, 'fail').text = ''
        CallToolResult failed = mcp.client.tool('assemble').call()
        String failedText = ((TextContent) failed.content()[0]).text()

        then:
        failed.isError()
        failedText.length() < CommandOutput.FAILURE_HEAD + CommandOutput.FAILURE_TAIL + 400
        failedText.contains('the build failed')
    }

    void "the builds run one at a time in the order they came, beside the other calls, and a call that waits for its lane is told which tool holds it"() {
        given:
        project()
        serve()
        List<String> told = new CopyOnWriteArrayList<>()

        when: 'a build that runs until it is released, a second build, and a call of another lane'
        CompletableFuture<CallToolResult> first = CompletableFuture.supplyAsync { mcp.client.tool('clean').call() }
        eventually { calls().contains('start clean') }
        CompletableFuture<CallToolResult> second = CompletableFuture.supplyAsync {
            mcp.client.tool('compile').progress({ ProgressParams params -> told << params.message() } as Consumer<ProgressParams>).call()
        }
        CallToolResult show = mcp.client.tool('project_show').call()

        then: 'the call of another lane ran while the build did, and the second build waits'
        !show.isError()
        !first.done
        eventually { told.contains('Waiting for clean, which is running') }
        !calls().any { it.contains('classes') }

        when:
        new File(dir, 'release').text = ''

        then: 'the second build ran when the first had ended'
        !first.get(60, TimeUnit.SECONDS).isError()
        !second.get(60, TimeUnit.SECONDS).isError()
        calls() == ['start clean', 'end clean', 'start classes', 'end classes']
        told.contains('Running: grails compile')
    }

    void "a call that is cancelled while it waits for its lane leaves the queue, and never runs"() {
        given:
        project()
        serve()

        when: 'a build runs, and a second build is cancelled while it waits for it'
        CompletableFuture<CallToolResult> first = CompletableFuture.supplyAsync { mcp.client.tool('clean').call() }
        eventually { calls().contains('start clean') }
        mcp.client.tool('compile').callTimeout(Duration.ofSeconds(2)).call()

        then:
        thrown(McpTimeoutException)

        when: 'the first build ends, and another build is called'
        new File(dir, 'release').text = ''
        first.get(60, TimeUnit.SECONDS)
        CallToolResult third = mcp.client.tool('dependency-report').call()

        then: 'the cancelled build never ran'
        !third.isError()
        calls() == ['start clean', 'end clean', 'start dependencies', 'end dependencies']
    }

    void "a cancelled build is interrupted: Gradle and what it started are stopped"() {
        given:
        project()
        serve()

        List<String> told = new CopyOnWriteArrayList<>()

        when:
        mcp.client.tool('test-app').progress({ ProgressParams params -> told << params.message() } as Consumer<ProgressParams>)
                .callTimeout(Duration.ofSeconds(3)).call()

        then:
        thrown(McpTimeoutException)

        and: 'while it ran, the client was told that it ran, and how many lines it wrote and the last of them'
        told[0] == 'Running: grails test-app'
        told.contains('1 line; the last: the tests run')

        and: 'the wrapper had started, and it and its child are ended'
        long build = new File(dir, 'build.pid').text.trim() as long
        long child = new File(dir, 'child.pid').text.trim() as long
        eventually { !alive(build) && !alive(child) }

        and: 'the lane takes the next build'
        !mcp.client.tool('compile').call().isError()
    }

    void "a cancelled call of the generator lane is not interrupted: it writes all of its files, its result is dropped, and its lane is free once it has ended"() {
        given: 'a directory in no project, and a project generator that takes a while to start when the first call needs it, which an interrupt would stop'
        serve(new ForgeContext({ ->
            Thread.sleep(3000)
            beanContext
        } as Supplier<ApplicationContext>))

        when: 'create-app is cancelled while the generator starts'
        mcp.client.tool('create-app').arguments([name: 'shop']).callTimeout(Duration.ofSeconds(1)).call()

        then:
        thrown(McpTimeoutException)

        when: 'a call of the same lane comes next'
        List<String> told = new CopyOnWriteArrayList<>()
        CallToolResult next = mcp.client.tool('create-app').arguments([name: 'other'])
                .progress({ ProgressParams params -> told << params.message() } as Consumer<ProgressParams>).call()

        then: 'it waited for the cancelled call, which went on to its end'
        !next.isError()
        told.contains('Waiting for create-app, which is running')

        and: 'the cancelled call wrote the whole application, as the call that was not cancelled did'
        files(new File(dir, 'shop')).size() > 20
        files(new File(dir, 'shop')) == files(new File(dir, 'other'))
        new File(dir, 'shop/.grails/project.yml').isFile()
        !processErr.toString(StandardCharsets.UTF_8.name()).contains('ClosedByInterrupt')
    }

    void "a cancelled build that does not stop is given up on: its lane takes other builds, and after three the server asks to end with exit code 1"() {
        given: 'a build that does not end when it is asked to, and a server that gives up on a call one second after it was cancelled'
        project()
        new File(dir, 'stubborn').text = ''
        GrailsMcpServer.Settings settings = settings()
        settings.abandonAfter = Duration.ofSeconds(1)
        serve(ForgeContext.of(beanContext), settings)

        when:
        3.times {
            try {
                mcp.client.tool('test-app').callTimeout(Duration.ofSeconds(1)).call()
            } catch (McpTimeoutException ignored) {
                // the client gave up on it, and cancelled it
            }
            eventually { mcp.server.calls.running().isEmpty() }
        }

        then: 'each was given up on, one after the other, and the server asked to end'
        eventually { exits == [1] }
        String said = processErr.toString(StandardCharsets.UTF_8.name())
        said.readLines().count { it.startsWith('| Warning grails mcp gave up on the call of test-app: it had not ended 1 second after it was cancelled. ' +
                'Its lane takes other calls again, and the next build gets a new connection to Gradle.') } == 3
        said.contains('| Error grails mcp ends: 3 calls did not end after they were cancelled. The client starts it again.')
        calls().count { it == 'start testApp' } == 3

        cleanup:
        new File(dir, 'stubborn').delete()
    }

    void "the instructions say what the server is and how its tools run, in at most 1500 characters"() {
        given:
        String deep = '/' + (1..60).collect { "directory${it}" }.join('/')

        when:
        String inProject = GrailsMcpServer.instructionsFor(dir.toPath(), true, false)
        String outside = GrailsMcpServer.instructionsFor(java.nio.file.Paths.get(deep), false, true)

        then:
        [inProject, outside].every { it.length() <= GrailsMcpServer.MAX_INSTRUCTIONS }
        inProject.startsWith("grails mcp is the Grails command line of one directory, ${dir.path}, which is in a Grails project. ".toString())
        ['refresh_tools', 'run-tool', 'development', 'run-app', 'stop-app', 'shortened', 'grails://commands', 'https://grails.apache.org/docs/'].every { inProject.contains(it) }
        outside.contains('in no Grails project')
        outside.contains('(--read-only)')
        outside.endsWith('/guide/commandLine.html#mcp')
    }

    // a web application, whose Gradle wrapper is the script above
    private void project() {
        generateProject(ApplicationType.WEB)
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = WRAPPER
        wrapper.setExecutable(true, false)
    }

    private void serve(ForgeContext context = ForgeContext.of(beanContext), GrailsMcpServer.Settings settings = settings()) {
        server = new GrailsMcpServer(context, dir.toPath(), false, settings, new PrintStream(processErr, true, StandardCharsets.UTF_8.name()))
        mcp = new InProcessMcp(server)
        server.start(mcp.server.calls, { int exitCode -> exits << exitCode } as IntConsumer)
        mcp.server.start()
    }

    private static GrailsMcpServer.Settings settings() {
        GrailsMcpServer.Settings settings = new GrailsMcpServer.Settings()
        settings.waitingEvery = Duration.ofMillis(500)
        settings.progressEvery = Duration.ofMillis(200)
        return settings
    }

    private List<String> calls() {
        File calls = new File(dir, 'build-calls.txt')
        return calls.exists() ? calls.readLines() : []
    }

    private static Map<String, Object> ran(CallToolResult result) {
        return (Map<String, Object>) result.meta()[McpNames.META_RESULT]
    }

    private static String text(CallToolResult result) {
        return result.content().findAll { it instanceof TextContent }.collect { ((TextContent) it).text() }.join('\n')
    }

    private static String permissions(File file) {
        return java.nio.file.attribute.PosixFilePermissions.toString(java.nio.file.Files.getPosixFilePermissions(file.toPath()))
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private static List<String> files(File root) {
        List<String> found = []
        root.eachFileRecurse { File file ->
            if (file.isFile()) {
                // by the name of the application, which some files have in theirs
                found << root.toPath().relativize(file.toPath()).toString().replaceAll(/(?i)other|shop/, 'NAME')
            }
        }
        return found.sort()
    }

    private static void eventually(Closure<Boolean> condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (!condition.call()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError('It did not happen in 60 s')
            }
            Thread.sleep(20)
        }
    }
}
