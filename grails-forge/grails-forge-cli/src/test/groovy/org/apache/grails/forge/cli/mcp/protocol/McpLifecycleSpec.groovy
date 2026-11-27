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
package org.apache.grails.forge.cli.mcp.protocol

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.IntConsumer

import io.modelcontextprotocol.modern.client.McpSyncClient
import io.modelcontextprotocol.modern.client.McpSyncSubscriptionListener
import io.modelcontextprotocol.modern.client.SubscriptionEvent
import spock.lang.Specification
import spock.lang.Timeout

/**
 * How {@code grails mcp} ends: at the end of its input, on a signal, and when it asks to, always once and in
 * the same order: the running calls are interrupted, the SDK is closed, what the session holds is closed,
 * and the process exits, unless a signal ends it already.
 */
@Timeout(60)
class McpLifecycleSpec extends Specification {

    StubMcpHandler handler = new StubMcpHandler()
    InProcessMcp mcp = new InProcessMcp(handler)
    ByteArrayOutputStream diagnosed = new ByteArrayOutputStream()
    volatile Runnable onSignal
    McpLifecycle lifecycle = new McpLifecycle(mcp.server, new PrintStream(diagnosed, true, StandardCharsets.UTF_8),
            { int exitCode -> handler.events << "exit ${exitCode}".toString() } as IntConsumer,
            { Runnable signal -> onSignal = signal } as Consumer<Runnable>)

    void setup() {
        lifecycle.onShutdown('the connection to Gradle') { handler.events << "connection closed after the SDK: ${mcp.server.closed}".toString() }
        lifecycle.onShutdown('the project generator') { handler.events << 'generator closed' }
    }

    void cleanup() {
        handler.release.countDown()
        mcp.close()
    }

    void "the end of its input ends the server: the running call is interrupted, the SDK closed, the session closed, and it exits with 0"() {
        given:
        FutureTask<Integer> served = serve()
        CompletableFuture<Object> call = CompletableFuture.supplyAsync { callQuietly('block') }
        handler.started.await(30, TimeUnit.SECONDS)

        when: 'the client closes the input of the server'
        mcp.transport.close()

        then:
        served.get(30, TimeUnit.SECONDS) == 0
        handler.events == ['call interrupted', 'connection closed after the SDK: true', 'generator closed', 'exit 0']
        handler.cancelledWhenInterrupted
        lifecycle.reason == McpLifecycle.Reason.END_OF_INPUT
        !mcp.server.calls.taking
        mcp.server.calls.running().isEmpty()
        call.get(30, TimeUnit.SECONDS) instanceof Throwable
        diagnosed.size() == 0
    }

    void "at the end of its input the server waits up to five seconds for a call that does not stop, and then ends all the same"() {
        given:
        FutureTask<Integer> served = serve()
        CompletableFuture.supplyAsync { callQuietly('stubborn') }
        handler.started.await(30, TimeUnit.SECONDS)

        when:
        long closedAt = System.nanoTime()
        mcp.transport.close()
        int exitCode = served.get(30, TimeUnit.SECONDS)
        Duration took = Duration.ofNanos(System.nanoTime() - closedAt)

        then:
        exitCode == 0
        took >= McpLifecycle.Reason.END_OF_INPUT.grace.minusMillis(100)
        handler.events == ['call interrupted', 'connection closed after the SDK: true', 'generator closed', 'exit 0']
        mcp.server.calls.running()*.tool == ['stubborn']
    }

    void "a signal ends the server without waiting long for a call that does not stop, and without exiting, since the JVM ends"() {
        given:
        FutureTask<Integer> served = serve()
        CountDownLatch listenEnded = new CountDownLatch(1)
        CountDownLatch subscribed = new CountDownLatch(1)
        McpSyncClient.Listening listening = mcp.client.listen().toolsChanged().open(new McpSyncSubscriptionListener() {
            @Override
            void onEvent(SubscriptionEvent event) {
                subscribed.countDown()
            }

            @Override
            void onError(Throwable error) {
                listenEnded.countDown()
            }

            @Override
            void onComplete() {
                listenEnded.countDown()
            }
        })
        subscribed.await(30, TimeUnit.SECONDS)
        CompletableFuture.supplyAsync { callQuietly('stubborn') }
        handler.started.await(30, TimeUnit.SECONDS)

        when: 'the JVM is sent SIGTERM, and runs its hooks'
        Thread hook = new Thread(onSignal, 'spec-shutdown-hook')
        long signalledAt = System.nanoTime()
        hook.start()
        hook.join(30_000)
        Duration took = Duration.ofNanos(System.nanoTime() - signalledAt)

        then: 'it waited a short while for the call, and closed everything, but left the exit to the JVM'
        !hook.alive
        took < McpLifecycle.Reason.END_OF_INPUT.grace
        handler.events == ['call interrupted', 'connection closed after the SDK: true', 'generator closed']
        lifecycle.reason == McpLifecycle.Reason.SIGNAL
        mcp.server.calls.running()*.tool == ['stubborn']
        mcp.server.calls.running()[0].interrupted

        and: 'the main thread returned without exiting, and the listen stream has ended'
        served.get(30, TimeUnit.SECONDS) == 0
        !handler.events.any { String event -> event.startsWith('exit') }
        listenEnded.await(30, TimeUnit.SECONDS)

        cleanup:
        listening?.close()
    }

    void "the server can ask to end with an exit code, and ends as it does at the end of its input"() {
        given:
        FutureTask<Integer> served = serve()

        when:
        lifecycle.requestExit(1)

        then:
        served.get(30, TimeUnit.SECONDS) == 1
        handler.events == ['connection closed after the SDK: true', 'generator closed', 'exit 1']
        lifecycle.reason == McpLifecycle.Reason.EXIT
    }

    void "the server ends once, however often and from however many threads it is asked to"() {
        given:
        CountDownLatch closing = new CountDownLatch(1)
        CountDownLatch closerRuns = new CountDownLatch(1)
        lifecycle.onShutdown('the applications the session started') {
            closerRuns.countDown()
            closing.await(30, TimeUnit.SECONDS)
            handler.events << 'applications stopped'
        }

        when:
        Thread first = Thread.start { lifecycle.shutdown(McpLifecycle.Reason.END_OF_INPUT) }
        closerRuns.await(30, TimeUnit.SECONDS)
        Thread second = Thread.start {
            lifecycle.shutdown(McpLifecycle.Reason.SIGNAL)
            handler.events << 'second returned'
        }
        second.join(500)

        then: 'the second waits until the first has ended the server'
        second.alive

        when:
        closing.countDown()
        first.join(30_000)
        second.join(30_000)
        lifecycle.shutdown(McpLifecycle.Reason.EXIT)

        then:
        handler.events == ['connection closed after the SDK: true', 'generator closed', 'applications stopped', 'second returned']
        lifecycle.reason == McpLifecycle.Reason.END_OF_INPUT
    }

    void "what cannot be closed is said on the standard error, and does not keep the rest open"() {
        given:
        McpLifecycle failing = new McpLifecycle(mcp.server, new PrintStream(diagnosed, true, StandardCharsets.UTF_8), { int exitCode -> } as IntConsumer,
                { Runnable signal -> } as Consumer<Runnable>)
        failing.onShutdown('the connection to Gradle') { throw new IllegalStateException('a build is running') }
        failing.onShutdown('the project generator') { handler.events << 'generator closed' }

        when:
        failing.shutdown(McpLifecycle.Reason.END_OF_INPUT)

        then:
        diagnosed.toString(StandardCharsets.UTF_8) == '| Error grails mcp could not close the connection to Gradle: a build is running' + System.lineSeparator()
        handler.events == ['generator closed']
        mcp.server.closed
    }

    void "a server is served once"() {
        given:
        serve()

        when:
        lifecycle.serve()

        then:
        thrown(IllegalStateException)
    }

    private FutureTask<Integer> serve() {
        FutureTask<Integer> served = new FutureTask<>({ -> lifecycle.serve() } as Callable<Integer>)
        Thread main = new Thread(served, 'spec-main')
        main.daemon = true
        main.start()
        InProcessMcp.await { onSignal != null }
        return served
    }

    private Object callQuietly(String tool) {
        try {
            return mcp.client.tool(tool).call()
        } catch (Throwable e) {
            return e
        }
    }
}
