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
package org.grails.forge.cli.mcp

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

import groovy.json.JsonSlurper
import io.modelcontextprotocol.modern.McpSchema.CallToolResult
import io.modelcontextprotocol.modern.McpSchema.DiscoverResult
import io.modelcontextprotocol.modern.McpSchema.Implementation
import io.modelcontextprotocol.modern.McpSchema.ProgressParams
import io.modelcontextprotocol.modern.McpSchema.TextContent
import io.modelcontextprotocol.modern.McpSchema.Tool
import io.modelcontextprotocol.modern.client.McpClient
import io.modelcontextprotocol.modern.client.McpSyncClient
import io.modelcontextprotocol.modern.client.McpSyncSubscriptionListener
import io.modelcontextprotocol.modern.client.SubscriptionEvent
import io.modelcontextprotocol.modern.client.transport.StdioMcpClientTransport
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Timeout

/**
 * {@code grails mcp} in a generated application, end to end: the program the Grails wrapper starts (the jar with
 * everything in it) in a JVM of its own, driven by the SDK's modern client on its standard input and output, and a
 * real Gradle with a Gradle user home of the specification's own, which the builds of {@code grails mcp} run in
 * through the Tooling API on the one connection the server keeps. One application goes through what an agent does:
 * {@code create-tool}, then {@code compile}, after which the client is told that the tools changed and the new tool
 * is offered with the schema of what it declares; the tool runs, with its declared option and its result, and a
 * mistyped option is an error; {@code test-app} tells how it goes and is cancelled, which stops the build;
 * {@code run-app} leaves the application running, and {@code stop-app} stops it. The application has the Database
 * Migration plugin, whose tools are offered with the options they declare.
 *
 * <p>The Gradle user home is new, so the first build downloads Gradle and every dependency of the application: that
 * build is the most of what the specification costs. Every Gradle daemon the builds start writes down its process in
 * the settings of the application, and is ended with the server when the specification ends.</p>
 */
@Stepwise
@IgnoreIf({ os.windows || !System.getProperty('grails.cli.all.jar') || System.getProperty('forge.generatedBuild.initScript') })
@Timeout(1800)
class McpGeneratedApplicationSpec extends Specification {

    /** What the result of {@code create-tool} ends with over MCP. */
    static final String LISTED_AFTER_COMPILE = 'It is listed once the project is compiled: call compile or refresh_tools.'

    /** How long a call may take at most: the first build downloads Gradle and the dependencies of the application. */
    static final Duration LONGEST_CALL = Duration.ofMinutes(25)

    @Shared
    File root

    @Shared
    File project

    @Shared
    File gradleUserHome

    @Shared
    File home

    @Shared
    File stderr

    @Shared
    File daemons

    @Shared
    Process server

    @Shared
    StdioMcpClientTransport transport

    @Shared
    McpSyncClient client

    @Shared
    List<String> written = new CopyOnWriteArrayList<>()

    @Shared
    BlockingQueue<SubscriptionEvent> events = new LinkedBlockingQueue<>()

    @Shared
    McpSyncClient.Listening listening

    @Shared
    Map<String, Tool> tools = [:]

    @Shared
    int port

    void setupSpec() {
        root = Files.createTempDirectory('grails-mcp-app').toFile().canonicalFile
        gradleUserHome = new File(root, 'gradle-user-home')
        gradleUserHome.mkdirs()
        // the specification changes files between two builds: a daemon that watches the file system could take them for unchanged
        new File(gradleUserHome, 'gradle.properties').text = 'org.gradle.vfs.watch=false\norg.gradle.daemon.idletimeout=120000\n'
        home = new File(root, 'home')
        home.mkdirs()
        stderr = new File(root, 'grails-mcp-stderr.txt')
        daemons = new File(root, 'daemons.txt')

        long started = System.nanoTime()
        Map created = cli(root, 'create-app', 'shop', '--features=database-migration')
        assert created.exit == 0: created.output
        project = new File(root, 'shop')
        // every process that reads the settings, which is a Gradle daemon, writes itself down, and is ended at the end
        new File(project, 'settings.gradle') << "\nnew File('${daemons.path}') << (ProcessHandle.current().pid() + '\\n')\n"
        println("[mcp-app] generated the application in ${seconds(started)} s")

        ProcessBuilder builder = new ProcessBuilder(java(), "-Duser.home=${home.path}".toString(), '-jar', System.getProperty('grails.cli.all.jar'), 'mcp')
                .directory(project)
                .redirectError(stderr)
        environment(builder)
        // the builds of grails mcp, through the Tooling API, as the command line runs them in its sessions, and the lines that say so
        builder.environment().remove('GRAILS_GRADLE_RUNNER')
        builder.environment().put('GRAILS_CLI_DIAGNOSTICS', 'true')
        server = builder.start()
        transport = StdioMcpClientTransport.builder(new Kept(server.inputStream, written), server.outputStream).start()
        client = McpClient.sync(transport).clientInfo(Implementation.builder('grails-mcp-application-spec', '1.0').build())
                .roundTimeout(LONGEST_CALL).build()
    }

    void cleanupSpec() {
        listening?.close()
        transport?.close()
        if (server != null) {
            List<ProcessHandle> below = server.descendants().toList()
            server.destroy()
            if (!server.waitFor(30, TimeUnit.SECONDS)) {
                server.destroyForcibly()
            }
            below.each { ProcessHandle each -> each.destroyForcibly() }
        }
        if (project != null) {
            File testPid = new File(project, 'slow-test.pid')
            if (testPid.isFile()) {
                ProcessHandle.of(testPid.text.trim() as long).ifPresent { ProcessHandle each -> each.destroyForcibly() }
            }
        }
        endDaemons()
        if (stderr?.isFile()) {
            println("[mcp-app] what grails mcp wrote on its standard error:\n${stderr.text}")
        }
        root?.deleteDir()
    }

    void "grails mcp serves the generated application: it says what it is, and offers its commands"() {
        when:
        long started = System.nanoTime()
        DiscoverResult discovered = client.discover()
        List<String> names = client.tools().list().tools()*.name()
        listening = client.listen().toolsChanged().open({ SubscriptionEvent event -> events.put(event) } as McpSyncSubscriptionListener)
        println("[mcp-app] discovered and listed in ${seconds(started)} s")

        then:
        discovered.supportedVersions() == ['2026-07-28']
        discovered.meta()['io.modelcontextprotocol/serverInfo'].name == 'grails'
        discovered.instructions().contains(project.path)
        names.containsAll(['compile', 'test-app', 'run-app', 'stop-app', 'create-tool', 'run-tool', 'project_show', 'refresh_tools'])
        !names.contains('data-import')
        events.poll(60, TimeUnit.SECONDS) instanceof SubscriptionEvent.Subscribed
    }

    void "create-tool writes a tool, which is offered once the project is compiled"() {
        when:
        CallToolResult created = client.tool('create-tool').arguments(['tool-name': 'data-import']).call()
        File source = new File(project, 'src/cli/groovy/shop/DataImportTool.groovy')

        then:
        !created.isError()
        texts(created)[0].endsWith('\n' + LISTED_AFTER_COMPILE)
        source.isFile()
        !client.tools().list().tools()*.name().contains('data-import')

        when: 'the tool is given an option and a result, as its author would'
        source.text = source.text
                .replace('import org.apache.grails.core.cli.ToolHints\n', 'import org.apache.grails.core.cli.ToolHints\nimport org.apache.grails.core.cli.ToolOption\n')
                .replace('    String target\n', "    String target\n\n    @ToolOption(names = ['--times'], description = 'How often')\n    int times = 1\n")
                .replace('        println "Ran ${name} for ${target ?: \'nothing\'}"\n',
                        '        times.times { println "Ran ${name} for ${target ?: \'nothing\'}" }\n        executionContext.result = [target: target, times: times]\n')

        then:
        source.text.contains('@ToolOption') && source.text.contains('executionContext.result')
    }

    void "compile runs Gradle through the Tooling API, after which the client is told that the tools changed, and the new tool is offered with its schema"() {
        given:
        List<String> told = new CopyOnWriteArrayList<>()

        when:
        long started = System.nanoTime()
        CallToolResult compiled = client.tool('compile').progress(listener(told)).call()
        println("[mcp-app] the first compile, with a new Gradle user home, took ${seconds(started)} s")

        then: 'it succeeded, and said how it went while it ran'
        assert !compiled.isError(): texts(compiled).join('\n')
        result(compiled).exitCode == 0
        told[0] == 'Running: grails compile'
        told.any { it.contains(' lines; the last: ') || it.contains(' line; the last: ') }

        and: 'the build ran through the Tooling API, as a build of grails mcp'
        stderr.text.contains('| Diagnostic gradle runner: requested auto, GRAILS_GRADLE_RUNNER not set; effective tooling-api: a build of grails mcp')

        and: 'the client was told that the tools changed'
        SubscriptionEvent changed = events.poll(60, TimeUnit.SECONDS)
        changed instanceof SubscriptionEvent.ToolsListChanged

        when:
        client.tools().list().tools().each { Tool tool -> tools[tool.name()] = tool }
        Tool tool = tools['data-import']

        then: 'the new tool is offered with what it declares: its option, its parameter and its hints'
        tool != null
        tool.title() == 'grails data-import'
        tool.description().startsWith('DataImport')
        Map<String, Map> properties = (Map<String, Map>) tool.inputSchema().get('properties')
        properties.keySet() == ['times', 'name'] as Set
        properties.times.type == 'integer'
        properties.name.type == 'string'
        !tool.inputSchema().get('required')
        tool.annotations().readOnlyHint() == false
        tool.annotations().destructiveHint() == true
        ((Map) tool.meta()['org.apache.grails/command']).origin == 'application'
        !((Map) tool.meta()['org.apache.grails/command']).containsKey('incomplete')
    }

    void "the tools of the Database Migration plugin are offered with the options they declare"() {
        when:
        Tool update = tools['dbm-update']
        Tool validate = tools['dbm-validate']
        Tool status = tools['dbm-status']
        Tool rollbackSql = tools['dbm-rollback-sql']

        then:
        update != null && validate != null && status != null && rollbackSql != null
        ((Map) update.inputSchema().get('properties')).keySet().containsAll(['dataSource', 'defaultSchema', 'contexts'])
        update.annotations().readOnlyHint() == false

        and: 'the tools that change neither the schema nor the data of the application, and write only a new file inside the project, are read-only'
        rollbackSql.annotations().readOnlyHint() == true
        rollbackSql.annotations().destructiveHint() == false
        status.annotations().readOnlyHint() == true
        status.annotations().destructiveHint() == false
        validate.annotations().readOnlyHint() == true
        tools.findAll { String name, Tool tool -> name.startsWith('dbm-') && tool.annotations()?.readOnlyHint() }.keySet() ==
                ['dbm-changelog-sync-sql', 'dbm-future-rollback-count-sql', 'dbm-future-rollback-sql', 'dbm-mark-next-changeset-ran-sql',
                 'dbm-rollback-count-sql', 'dbm-rollback-sql', 'dbm-rollback-to-date-sql', 'dbm-validate', 'dbm-status', 'dbm-list-locks'] as Set
        ((Map) update.meta()['org.apache.grails/command']).origin != 'application'
        tools.keySet().count { it.startsWith('dbm-') } > 20
    }

    void "the new tool runs in the application, with its declared option and its result, and a mistyped option is an error"() {
        when:
        long started = System.nanoTime()
        CallToolResult once = client.tool('data-import').arguments([name: 'orders.csv']).call()
        println("[mcp-app] the first run of the tool took ${seconds(started)} s")
        started = System.nanoTime()
        CallToolResult thrice = client.tool('data-import').arguments([name: 'orders.csv', times: 3]).call()
        println("[mcp-app] the second run of the tool took ${seconds(started)} s")

        then: 'it ran, and its result is the structured content of the call'
        assert !once.isError(): texts(once).join('\n')
        texts(once)[0].readLines().count { it == 'Ran data-import for orders.csv' } == 1
        once.structuredContent() == [target: 'orders.csv', times: 1]
        !thrice.isError()
        texts(thrice)[0].readLines().count { it == 'Ran data-import for orders.csv' } == 3
        thrice.structuredContent() == [target: 'orders.csv', times: 3]
        new JsonSlurper().parseText(texts(thrice)[1]) == thrice.structuredContent()

        and: 'nothing of the result or of the arguments is left in the project'
        left() == []

        when: 'an option the tool does not declare is given, as a property the schema does not have'
        CallToolResult mistyped = client.tool('data-import').arguments([name: 'orders.csv', tiems: 3]).call()

        then: 'the call is refused before anything runs'
        mistyped.isError()
        texts(mistyped).join('\n').contains('tiems')

        when: 'it is given through run-tool, which passes the arguments of the tool as they are'
        CallToolResult refused = client.tool('run-tool').arguments([name: 'data-import', arg: ['--tiems', '3']]).call()

        then: 'the tool refuses it, with the line of the command line, and the build fails'
        refused.isError()
        texts(refused)[0].contains('| Error data-import: --tiems is not one of its options.')
        left() == []
    }

    void "test-app tells how it goes, and cancelling it stops the build: the JVM of the tests ends"() {
        given: 'a test that runs until it is stopped'
        File tests = new File(project, 'src/test/groovy/shop')
        tests.mkdirs()
        new File(tests, 'SlowSpec.groovy').text = '''\
            package shop

            import spock.lang.Specification

            class SlowSpec extends Specification {

                void "runs until it is stopped"() {
                    given:
                    new File(System.getProperty('user.dir'), 'slow-test.pid').text = ProcessHandle.current().pid() as String

                    when:
                    Thread.sleep(600_000)

                    then:
                    true
                }
            }
            '''.stripIndent()
        File testPid = new File(project, 'slow-test.pid')
        List<String> told = new CopyOnWriteArrayList<>()
        Object[] ended = new Object[1]

        when: 'the client calls test-app, and gives up on it while the test runs'
        long started = System.nanoTime()
        Thread caller = Thread.start('test-app caller') {
            try {
                ended[0] = client.tool('test-app').progress(listener(told)).call()
            } catch (Throwable e) {
                ended[0] = e
            }
        }
        eventually(Duration.ofMinutes(15)) { testPid.isFile() && testPid.text.trim() ==~ /[0-9]+/ }
        println("[mcp-app] test-app ran the test after ${seconds(started)} s")
        long testJvm = testPid.text.trim() as long
        assert alive(testJvm)
        caller.interrupt()
        caller.join(60_000)

        then: 'the call was cancelled, after the client was told that it ran and how many lines it wrote'
        ended[0] instanceof CancellationException
        told[0] == 'Running: grails test-app'
        told.any { it.contains(' lines; the last: ') }

        and: 'the build stopped: the JVM that ran the test ended'
        eventually(Duration.ofSeconds(90)) { !alive(testJvm) }
        println("[mcp-app] the JVM of the tests ended ${seconds(started)} s after test-app was called")

        cleanup:
        new File(tests, 'SlowSpec.groovy').delete()
    }

    void "run-app leaves the application running after the call, and stop-app stops it"() {
        given:
        port = freePort()

        when:
        long started = System.nanoTime()
        CallToolResult ran = client.tool('run-app').arguments([port: port]).call()
        println("[mcp-app] run-app took ${seconds(started)} s")

        then: 'the call ended, and the application answers'
        assert !ran.isError(): texts(ran).join('\n')
        result(ran).commandLine.containsAll(['run-app', '--detach'])
        answers(port)

        when:
        CallToolResult stopped = client.tool('stop-app').call()

        then:
        assert !stopped.isError(): texts(stopped).join('\n')
        eventually(Duration.ofSeconds(90)) { !answers(port) }
    }

    void "the end of its input ends grails mcp with exit code 0, and it wrote nothing but the protocol"() {
        when:
        listening?.close()
        listening = null
        server.outputStream.close()
        boolean ended = server.waitFor(60, TimeUnit.SECONDS)

        then:
        ended
        server.exitValue() == 0

        and:
        !written.isEmpty()
        written.every { String line -> ((Map) new JsonSlurper().parseText(line)).jsonrpc == '2.0' }

        and: 'every build ran through the Tooling API on the one connection the server kept, and run-app as a process of its own'
        List<String> said = stderr.readLines()
        said.count { it == '| Diagnostic gradle runner: requested auto, GRAILS_GRADLE_RUNNER not set; effective tooling-api: a build of grails mcp' } >= 5
        said.count { it.startsWith('| Diagnostic gradle connection: reused: ') } >= 4
        said.findAll { it.startsWith('| Diagnostic gradle connection: ') }.every { it.endsWith('(1 made in this JVM)') }
        said.any { it.startsWith('| Diagnostic gradle runner: ') && it.contains('effective process: an application that is left running') }
    }

    // the command line, run once in a directory, as a user types it
    private Map cli(File directory, String... args) {
        ProcessBuilder builder = new ProcessBuilder([java(), "-Duser.home=${home.path}".toString(), '-jar', System.getProperty('grails.cli.all.jar')] + args.toList())
                .directory(directory).redirectErrorStream(true)
        environment(builder)
        Process process = builder.start()
        String output = process.inputStream.getText(StandardCharsets.UTF_8.name())
        assert process.waitFor(5, TimeUnit.MINUTES)
        return [exit: process.exitValue(), output: output]
    }

    // the Gradle user home of the specification, the Java of this JVM for the Gradle wrapper, and no catalog or index of the network
    private void environment(ProcessBuilder builder) {
        builder.environment().put('GRADLE_USER_HOME', gradleUserHome.path)
        builder.environment().put('JAVA_HOME', System.getProperty('java.home'))
        builder.environment().put('GRAILS_FORGE_CATALOG', 'none')
        builder.environment().put('GRAILS_PLUGIN_INDEX', 'none')
        ['CLASSPATH', 'JAVA_TOOL_OPTIONS', 'JAVA_OPTS', 'GRADLE_OPTS'].each { String name -> builder.environment().remove(name) }
    }

    private static String java() {
        return new File(System.getProperty('java.home'), 'bin/java').path
    }

    private List<String> left() {
        File run = new File(project, '.grails/run')
        return (run.list() ?: new String[0]).findAll { String name -> name.startsWith('tool-result-') || name.startsWith('command-args-') }
    }

    // the daemons the builds started, which wrote themselves down; the registry is in the Gradle user home of the specification
    private void endDaemons() {
        if (daemons == null || !daemons.isFile()) {
            return
        }
        List<ProcessHandle> left = daemons.readLines().findAll { String line -> line ==~ /[0-9]+/ }.unique()
                .collect { String line -> ProcessHandle.of(line as long).orElse(null) }.findAll { ProcessHandle each -> each != null && each.alive }
        left.each { ProcessHandle each -> each.destroy() }
        left.each { ProcessHandle each ->
            try {
                each.onExit().get(10, TimeUnit.SECONDS)
            } catch (Exception ignored) {
                each.destroyForcibly()
            }
        }
    }

    private static Consumer<ProgressParams> listener(List<String> told) {
        return { ProgressParams params -> told << params.message() } as Consumer<ProgressParams>
    }

    private static List<String> texts(CallToolResult result) {
        return result.content().findAll { it instanceof TextContent }.collect { ((TextContent) it).text() }
    }

    private static Map<String, Object> result(CallToolResult result) {
        return (Map<String, Object>) result.meta()['org.apache.grails/result']
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private static boolean answers(int port) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:${port}/").openConnection()
            connection.connectTimeout = 2000
            connection.readTimeout = 10000
            return connection.responseCode > 0
        } catch (IOException ignored) {
            return false
        }
    }

    private static int freePort() {
        ServerSocket socket = new ServerSocket(0)
        try {
            return socket.localPort
        } finally {
            socket.close()
        }
    }

    private static long seconds(long startedNanos) {
        return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedNanos)
    }

    private static void eventually(Duration within, Closure<Boolean> condition) {
        long deadline = System.nanoTime() + within.toNanos()
        while (!condition.call()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("It did not happen in ${within.seconds} s")
            }
            Thread.sleep(200)
        }
    }

    /** What the server writes, passed on to the client, and kept line by line. */
    static final class Kept extends FilterInputStream {

        private final List<String> lines
        private final ByteArrayOutputStream line = new ByteArrayOutputStream()

        Kept(InputStream from, List<String> lines) {
            super(from)
            this.lines = lines
        }

        @Override
        int read() {
            int b = super.read()
            if (b >= 0) {
                keep(b)
            }
            return b
        }

        @Override
        int read(byte[] bytes, int offset, int length) {
            int count = super.read(bytes, offset, length)
            for (int i = offset; i < offset + count; i++) {
                keep(bytes[i])
            }
            return count
        }

        private synchronized void keep(int b) {
            if (b == ('\n' as char) as int) {
                lines << line.toString(StandardCharsets.UTF_8.name())
                line.reset()
            } else {
                line.write(b)
            }
        }
    }
}
