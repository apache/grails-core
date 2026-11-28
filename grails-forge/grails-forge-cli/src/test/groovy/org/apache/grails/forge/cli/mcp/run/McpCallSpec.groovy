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
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
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
import io.modelcontextprotocol.modern.McpSchema.Tool
import io.modelcontextprotocol.modern.client.McpSyncClient
import io.modelcontextprotocol.modern.client.McpSyncSubscriptionListener
import io.modelcontextprotocol.modern.client.SubscriptionEvent
import org.apache.grails.forge.cli.console.ConsoleCommands
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.PlainOutput
import org.apache.grails.forge.cli.mcp.McpLeftovers
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.protocol.InProcessMcp
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeContext
import org.grails.forge.cli.Handshake
import org.grails.forge.cli.SpecWaits
import org.springframework.context.ApplicationContext
import spock.lang.IgnoreIf
import spock.lang.Timeout

/**
 * Calls of {@code grails mcp}, as its server runs them in this process, driven by the SDK's own client over
 * pipes: each is a command line of the command line, run with {@code --plain-output --non-interactive} on
 * streams of its own, and its result is what it wrote and how it ended. The project's Gradle wrapper is a
 * script that writes down what it was given and does what a feature asks of it: it says when a build that is held
 * has started, and holds it until the feature releases it ({@link org.grails.forge.cli.Handshake}). A call the feature
 * cancels runs on a thread of the feature's own, which the feature interrupts once the call is where the feature
 * wants it, as a client does that cancels a call.
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class McpCallSpec extends CommandSpec implements CommandFixture {

    // the Gradle wrapper of the project, after the functions of the handshake: it writes down each build it is given, in
    // the directory of the project; testApp runs until it is ended, and clean until the feature releases it
    static final String WRAPPER = '''\
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
        if [ -f flood ]; then
          if [ /dev/stdout -ef /dev/stderr ]; then echo "one stream"; else echo "two streams"; fi
          yes "a line of the build" | head -n 20000
          echo "the build failed at its end" >&2
          exit 1
        fi
        case $task in
          testApp)
            hold child &
            say testApp $!
            echo "the tests run"
            wait
            ;;
          assemble)
            i=0
            while [ $i -lt 5000 ]; do echo "line $i of the build"; i=$((i+1)); done
            ;;
          clean)
            say clean
            hold clean
            echo "cleaned"
            ;;
          *)
            echo "the build ran $task"
            ;;
        esac
        echo "end $task" >> build-calls.txt
        if [ -f fail ]; then echo "the build failed" >&2; exit 1; fi
        '''.stripIndent()

    // what a call that waits for the build lane is told while clean holds it
    static final String WAITING_FOR_CLEAN = 'Waiting for clean, which is running'

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
        // a build a feature held, which the server waits for when it is closed, ends
        handshake.releaseAll()
        mcp?.close()
        server?.close()
        PlainOutput.leave()
        ConsoleCommands.setSession(ConsoleCommands.Session.NONE)
        GradleBuild.setRunner(null)
        GradleBuild.closeConnections()
        GradleRunner.setStopSeconds(null)
        McpLeftovers.assertNoneLeft()
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

    void "the list of the tools has in _meta only what an entry says of where its command comes from and how it runs, and grails://tools/NAME has the whole entry"() {
        given:
        project()
        serve()

        when:
        Tool listed = mcp.client.tools().list().tools().find { Tool tool -> tool.name() == 'create-controller' }
        Map<String, Object> described = (Map<String, Object>) new JsonSlurper().parseText(
                ((TextResourceContents) mcp.client.resource('grails://tools/create-controller').read().contents()[0]).text())

        then: 'the tool says where its command comes from, and how the server runs it'
        listed.meta()[McpNames.META_COMMAND] == [origin: 'built-in', project: true]
        ((Map) listed.meta()[McpNames.META_RUN]).commandLine == ['--plain-output', '--non-interactive', 'create-controller']

        and: 'its resource has the whole entry of help --json, with the options the input schema was made from'
        described.name == 'create-controller'
        described.command.description == 'Creates a Grails Controller'
        ((List<Map>) described.command.options)*.names.flatten().contains('--force')
        ((List<Map>) described.command.parameters)*.label == ['CONTROLLER-NAME']
        described.run == listed.meta()[McpNames.META_RUN]
    }

    void "a report whose command ends with exit code 1 is a result and not an error, with the exit code in its _meta, and a failure that writes a document is still an error"() {
        given: 'a project that was edited, as every project is: its Gradle wrapper is not the one it was generated with'
        project()
        serve()

        when:
        CallToolResult differs = mcp.client.tool('project_regenerate').call()

        then: 'the files that differ are the answer'
        !differs.isError()
        ((Map) differs.structuredContent()).outcome == 'changes'
        ((Map) differs.structuredContent()).hasChanges == true
        ((List<Map>) ((Map) differs.structuredContent()).files)*.path.contains('gradlew')
        ran(differs).exitCode == 1

        when: 'the health of a project whose Gradle wrapper names no Gradle, which is an error finding'
        new File(dir, 'gradle/wrapper/gradle-wrapper.properties').delete()
        CallToolResult health = mcp.client.tool('project_health').call()
        Map<String, Object> checked = (Map<String, Object>) health.structuredContent()

        then: 'the findings are the answer'
        !health.isError()
        checked.outcome == 'checked'
        checked.passed == false
        ((Map) checked.counts).error > 0
        ran(health).exitCode == 1

        when: 'the record of the project is gone, so that nothing can be compared'
        new File(dir, '.grails').deleteDir()
        CallToolResult failed = mcp.client.tool('project_regenerate').call()

        then: 'the command wrote a document all the same, and its outcome is a failure: the result is an error'
        failed.isError()
        ((Map) failed.structuredContent()).outcome == 'invalid-record'
        ran(failed).exitCode == 1
    }

    void "a dry run of feature_add that finds conflicts is a result and not an error, and one that names no feature is an error with exit code 2"() {
        given: 'a project whose configuration the feature would change where it was edited'
        project()
        File config = new File(dir, 'grails-app/conf/application.yml')
        config.text = config.text.replace('url: jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE', 'url: jdbc:h2:file:./ourDevDb')
        serve()

        when:
        CallToolResult conflicts = mcp.client.tool('feature_add').arguments([feature: ['postgres'], 'dry-run': true]).call()

        then:
        !conflicts.isError()
        ((Map) conflicts.structuredContent()).outcome == 'conflicts'
        ((List<Map>) ((Map) conflicts.structuredContent()).files).find { it.action == 'conflict' }.path == 'grails-app/conf/application.yml'
        ran(conflicts).exitCode == 1

        when:
        CallToolResult unknown = mcp.client.tool('feature_add').arguments([feature: ['no-such-feature'], 'dry-run': true]).call()

        then: 'a mistake in what was asked is an error, also where the command wrote a document'
        unknown.isError()
        ((Map) unknown.structuredContent()).outcome == 'refused'
        ran(unknown).exitCode == 2

        when: 'without dry-run there is no document, and exit code 1 is an error'
        CallToolResult added = mcp.client.tool('feature_add').arguments([feature: ['postgres']]).call()

        then:
        added.isError()
        added.structuredContent() == null
        ran(added).exitCode == 1
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
        assert next(events) instanceof SubscriptionEvent.Subscribed
        assert !mcp.client.tools().list().tools()*.name().contains('compile')

        when: 'an application is created in the directory itself'
        CallToolResult created = mcp.client.tool('create-app').arguments([name: 'shop', inplace: true]).call()

        then: 'the lists changed before the result came, and have the commands and the resources of a project'
        !created.isError()
        [next(events), next(events)]*.class as Set == [SubscriptionEvent.ToolsListChanged, SubscriptionEvent.ResourcesListChanged] as Set
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

    void "what a build that fails wrote last to its errors ends its result, after the output it wrote before, however much of it is still on its way"() {
        given: 'a build that writes much more to its output than a pipe holds, and then, last, why it failed to its errors'
        project()
        serve()
        new File(dir, 'flood').text = ''

        when:
        CallToolResult failed = mcp.client.tool('compile').call()
        List<String> lines = text(failed).readLines()

        then: 'the build was given one stream for its output and its errors, which keeps them in the order it wrote them'
        failed.isError()
        ran(failed).truncated == true
        lines.contains('one stream')

        and: 'so the end of the result is the end of what it wrote, and then the line of the command line'
        lines.takeRight(3) == ['a line of the build', 'the build failed at its end', '| Error Gradle did not succeed: it ended with exit code 1']
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
        given: 'threads of the feature for the calls it holds open while it looks at the others'
        project()
        serve()
        ExecutorService callers = Executors.newCachedThreadPool()
        List<String> toldSecond = new CopyOnWriteArrayList<>()
        List<String> toldThird = new CopyOnWriteArrayList<>()

        when: 'a build that runs until it is released'
        Future<CallToolResult> first = callers.submit({ -> held('clean', null) } as Callable<CallToolResult>)
        handshake.awaitStep('clean')

        and: 'a second build, and a third once the second is told that it waits for its lane'
        Future<CallToolResult> second = callers.submit({ -> held('compile', toldSecond) } as Callable<CallToolResult>)
        eventually { toldSecond.contains(WAITING_FOR_CLEAN) }
        Future<CallToolResult> third = callers.submit({ -> held('dependency-report', toldThird) } as Callable<CallToolResult>)
        eventually { toldThird.contains(WAITING_FOR_CLEAN) }

        and: 'a call of another lane'
        CallToolResult show = mcp.client.tool('project_show').call()

        then: 'the call of another lane ran while the build did, and the builds that wait are told so again while they wait'
        !show.isError()
        !first.done
        eventually { toldSecond.count(WAITING_FOR_CLEAN) >= 2 && toldThird.count(WAITING_FOR_CLEAN) >= 2 }
        calls() == ['start clean']

        when:
        handshake.release('clean')

        then: 'each build ran when the one before it had ended, in the order they came, however long they waited'
        !SpecWaits.get(first, 'the result of the first build').isError()
        !SpecWaits.get(second, 'the result of the second build').isError()
        !SpecWaits.get(third, 'the result of the third build').isError()
        calls() == ['start clean', 'end clean', 'start classes', 'end classes', 'start dependencies', 'end dependencies']

        and: 'a call is told that it runs once it has its lane, and is not told after that that it waits'
        toldSecond.contains('Running: grails compile')
        !toldSecond.subList(toldSecond.indexOf('Running: grails compile'), toldSecond.size()).any { String message -> message.startsWith('Waiting') }
        toldThird.contains('Running: grails dependency-report')

        cleanup:
        callers?.shutdownNow()
    }

    void "a call that is cancelled while it waits for its lane leaves the queue, and never runs"() {
        given:
        project()
        serve()
        ExecutorService callers = Executors.newCachedThreadPool()
        List<String> told = new CopyOnWriteArrayList<>()
        List<Throwable> failed = new CopyOnWriteArrayList<>()

        when: 'a build runs, and a second build is cancelled once it is told that it waits for it'
        Future<CallToolResult> first = callers.submit({ -> held('clean', null) } as Callable<CallToolResult>)
        handshake.awaitStep('clean')
        Thread second = calling('compile', told, failed)
        eventually { told.contains(WAITING_FOR_CLEAN) }
        second.interrupt()
        SpecWaits.join(second, 'the end of the cancelled call')

        then: 'the client gave up on it'
        failed*.class == [CancellationException]

        when: 'the first build ends, and another build is called'
        handshake.release('clean')
        SpecWaits.get(first, 'the result of the first build')
        CallToolResult third = mcp.client.tool('dependency-report').call()

        then: 'the cancelled build never ran'
        !third.isError()
        calls() == ['start clean', 'end clean', 'start dependencies', 'end dependencies']

        cleanup:
        callers?.shutdownNow()
    }

    void "a cancelled build is interrupted: Gradle and what it started are stopped"() {
        given:
        project()
        serve()

        List<String> told = new CopyOnWriteArrayList<>()
        List<Throwable> failed = new CopyOnWriteArrayList<>()

        when: 'the call is cancelled once the client was told of the line the build wrote'
        Thread call = calling('test-app', told, failed)
        eventually { told.contains('1 line; the last: the tests run') }
        call.interrupt()
        SpecWaits.join(call, 'the end of the cancelled call')

        then: 'the client gave up on it'
        failed*.class == [CancellationException]

        and: 'while it ran, the client was told that it ran, and how many lines it wrote and the last of them'
        told[0] == 'Running: grails test-app'

        and: 'the wrapper had started, and it and its child are ended, though nothing released them'
        Handshake.Step build = handshake.awaitStep('testApp')
        Handshake.awaitExit(build.pid)
        Handshake.awaitExit(build.number(0))

        and: 'the lane takes the next build'
        !mcp.client.tool('compile').call().isError()
    }

    void "a cancelled call of the generator lane is not interrupted: it writes all of its files, its result is dropped, and its lane is free once it has ended"() {
        given: 'a directory in no project, and a project generator that starts, when the first call needs it, once the feature lets it, which an interrupt would stop'
        CountDownLatch starting = new CountDownLatch(1)
        CountDownLatch go = new CountDownLatch(1)
        List<String> interrupted = new CopyOnWriteArrayList<>()
        serve(new ForgeContext({ ->
            starting.countDown()
            try {
                go.await()
            } catch (InterruptedException e) {
                interrupted << 'the generator was interrupted while it started'
                throw e
            }
            beanContext
        } as Supplier<ApplicationContext>))
        List<Throwable> failed = new CopyOnWriteArrayList<>()

        when: 'create-app is cancelled while the generator starts, and the server has taken it for cancelled'
        Thread call = calling('create-app', [name: 'shop'], null, failed)
        SpecWaits.await(starting, 'the start of the generator')
        call.interrupt()
        SpecWaits.join(call, 'the end of the cancelled call')
        // the server no longer runs the call once it took it for cancelled, while its command goes on in its lane
        eventually { mcp.server.calls.running().isEmpty() }

        then: 'the client gave up on it'
        failed*.class == [CancellationException]

        when: 'a call of the same lane comes next, and the generator starts once that call is told that it waits'
        List<String> told = new CopyOnWriteArrayList<>()
        CallToolResult next = mcp.client.tool('create-app').arguments([name: 'other']).progress({ ProgressParams params ->
            told << params.message()
            if (params.message() == 'Waiting for create-app, which is running') {
                go.countDown()
            }
        } as Consumer<ProgressParams>).call()

        then: 'it waited for the cancelled call, which went on to its end'
        !next.isError()
        told.contains('Waiting for create-app, which is running')
        interrupted == []

        and: 'the cancelled call wrote the whole application, as the call that was not cancelled did'
        files(new File(dir, 'shop')).size() > 20
        files(new File(dir, 'shop')) == files(new File(dir, 'other'))
        new File(dir, 'shop/.grails/project.yml').isFile()
        !processErr.toString(StandardCharsets.UTF_8.name()).contains('ClosedByInterrupt')
    }

    void "a cancelled build that does not stop is given up on: its lane takes other builds, and after three the server asks to end with exit code 1"() {
        given: 'a build that does not end when it is asked to, and is not ended by force while the feature runs, and a server that gives up on a call one second after it was cancelled'
        project()
        new File(dir, 'stubborn').text = ''
        GradleRunner.setStopSeconds(SpecWaits.boundSeconds as int)
        GrailsMcpServer.Settings settings = settings()
        settings.abandonAfter = Duration.ofSeconds(1)
        // closing the server waits for the builds it gave up on, which end when the feature releases them
        settings.commandsAtShutdown = SpecWaits.BOUND
        serve(ForgeContext.of(beanContext), settings)

        when: 'three times, a build is cancelled once it has started'
        (1..3).each { int time ->
            Thread call = calling('test-app', null, new CopyOnWriteArrayList<Throwable>())
            handshake.awaitStep('testApp', time)
            // the client gave up on it, and cancelled it
            call.interrupt()
            SpecWaits.join(call, 'the end of the cancelled call')
            eventually { mcp.server.calls.running().isEmpty() }
        }

        then: 'each was given up on, one after the other, and the server asked to end'
        eventually { exits == [1] }
        String said = processErr.toString(StandardCharsets.UTF_8.name())
        said.readLines().count { it.startsWith('| Warning grails mcp gave up on the call of test-app: it had not ended 1 second after it was cancelled. ' +
                'Its lane takes other calls again, and the next build gets a new connection to Gradle.') } == 3
        said.contains('| Error grails mcp ends: 3 calls did not end after they were cancelled. The client starts it again.')
        calls().count { it == 'start testApp' } == 3
        handshake.said('testApp').every { Handshake.Step build -> SpecWaits.alive(build.pid) }

        cleanup:
        new File(dir, 'stubborn').delete()
    }

    void "closing the server waits a while for a command that still runs in the process, and says which it no longer waits for"() {
        given: 'a build that does not end when it is cancelled until the feature releases it, and a server that waits one second for it when it is closed'
        project()
        new File(dir, 'stubborn').text = ''
        GradleRunner.setStopSeconds(SpecWaits.boundSeconds as int)
        GrailsMcpServer.Settings settings = settings()
        settings.commandsAtShutdown = Duration.ofSeconds(1)
        serve(ForgeContext.of(beanContext), settings)
        Thread call = calling('test-app', null, new CopyOnWriteArrayList<Throwable>())
        long build = handshake.awaitStep('testApp').pid
        // the client gave up on it, and cancelled it
        call.interrupt()
        SpecWaits.join(call, 'the end of the cancelled call')

        when:
        long closing = System.nanoTime()
        server.close()
        long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closing)

        then: 'it waited, and then said that the build still runs'
        // time itself is what is held here: a lower bound, which a slow computer only exceeds
        tookMillis >= 900
        processErr.toString(StandardCharsets.UTF_8.name()).readLines().contains('| Warning grails mcp ends while a command still runs in its process (test-app): ' +
                'what it wrote last may not be whole, and the next command of the project says so where it can')
        SpecWaits.alive(build)

        cleanup: 'the build ends once it is released, before the next feature'
        handshake.releaseAll()
        eventually { mcp.server.calls.running().isEmpty() }
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
        ['refresh_tools', 'run-tool', 'development', 'run-app', 'stop-app', 'shortened', 'grails://commands', 'grails://tools/NAME is the whole entry of one tool',
         'https://grails.apache.org/docs/'].every { inProject.contains(it) }
        outside.contains('in no Grails project')
        outside.contains('(--read-only)')
        outside.endsWith('/guide/commandLine.html#mcp')
    }

    // a web application, whose Gradle wrapper is the script above
    private void project() {
        generateProject(ApplicationType.WEB)
        stubWrapper(dir, WRAPPER)
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

    // A call a feature holds open while it looks at what else runs: its round lasts as long as a wait of the feature may,
    // as that of every call does (InProcessMcp). What the call is told while it runs is added to the list, where there is one.
    private CallToolResult held(String tool, List<String> told) {
        McpSyncClient.ToolSpec call = mcp.client.tool(tool)
        if (told != null) {
            call = call.progress({ ProgressParams params -> told << params.message() } as Consumer<ProgressParams>)
        }
        return call.call()
    }

    private Thread calling(String tool, List<String> told, List<Throwable> failed) {
        return calling(tool, null, told, failed)
    }

    // A call on a thread of the feature's own, which the feature cancels by interrupting the thread, as a client does
    // that cancels a call: the SDK's client then tells the server that it was cancelled. How the call ended for the
    // client is added to the list.
    private Thread calling(String tool, Map<String, Object> arguments, List<String> told, List<Throwable> failed) {
        Thread thread = new Thread({
            try {
                McpSyncClient.ToolSpec call = mcp.client.tool(tool).arguments(arguments)
                if (told != null) {
                    call = call.progress({ ProgressParams params -> told << params.message() } as Consumer<ProgressParams>)
                }
                call.call()
            } catch (Throwable e) {
                failed << e
            }
        }, "call of ${tool}")
        thread.start()
        return thread
    }

    private static SubscriptionEvent next(BlockingQueue<SubscriptionEvent> events) {
        SubscriptionEvent event = events.poll(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
        assert event != null: "no event of the subscription came within ${SpecWaits.boundSeconds} s"
        return event
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
        SpecWaits.until('the condition of the feature', { -> condition.call() as boolean } as Supplier<Boolean>)
    }
}
