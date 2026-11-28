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
import org.grails.forge.cli.SpecWaits
import spock.lang.Specification
import spock.lang.Timeout

/**
 * How {@code grails mcp} ends: at the end of its input, on a signal, and when it asks to, always once and in
 * the same order: the running calls are interrupted, the SDK is closed, what the session holds is closed,
 * and the process exits, unless a signal ends it already.
 */
@Timeout(600)
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
        McpLifecycle.setGrace(McpLifecycle.Reason.END_OF_INPUT, null)
    }

    void "the end of its input ends the server: the running call is interrupted, the SDK closed, the session closed, and it exits with 0"() {
        given:
        FutureTask<Integer> served = serve()
        CompletableFuture<Object> call = CompletableFuture.supplyAsync { callQuietly('block') }
        SpecWaits.await(handler.started, 'the start of the call')

        when: 'the client closes the input of the server'
        mcp.transport.close()

        then:
        SpecWaits.get(served, 'the end of serving') == 0
        handler.events == ['call interrupted', 'connection closed after the SDK: true', 'generator closed', 'exit 0']
        handler.cancelledWhenInterrupted
        lifecycle.reason == McpLifecycle.Reason.END_OF_INPUT
        !mcp.server.calls.taking
        mcp.server.calls.running().isEmpty()
        SpecWaits.get(call, 'the end of the call') instanceof Throwable
        diagnosed.size() == 0
    }

    void "at the end of its input the server waits up to five seconds for a call that does not stop, and then ends all the same"() {
        given: 'a server that waits a second, in place of the five seconds it waits for a client'
        assert McpLifecycle.Reason.END_OF_INPUT.grace == Duration.ofSeconds(5)
        McpLifecycle.setGrace(McpLifecycle.Reason.END_OF_INPUT, Duration.ofSeconds(1))
        FutureTask<Integer> served = serve()
        CompletableFuture.supplyAsync { callQuietly('stubborn') }
        SpecWaits.await(handler.started, 'the start of the call')

        when:
        long closedAt = System.nanoTime()
        mcp.transport.close()
        int exitCode = SpecWaits.get(served, 'the end of serving')
        Duration took = Duration.ofNanos(System.nanoTime() - closedAt)

        then: 'it waited the time it waits, which time itself holds: a lower bound, which a slow computer only exceeds'
        exitCode == 0
        took >= McpLifecycle.graceOf(McpLifecycle.Reason.END_OF_INPUT).minusMillis(100)
        handler.events == ['call interrupted', 'connection closed after the SDK: true', 'generator closed', 'exit 0']
        mcp.server.calls.running()*.tool == ['stubborn']
    }

    void "a signal ends the server without waiting long for a call that does not stop, and without exiting, since the JVM ends"() {
        given: 'a server that would wait as long as a wait of the feature may at the end of its input, which a signal is not'
        McpLifecycle.setGrace(McpLifecycle.Reason.END_OF_INPUT, SpecWaits.BOUND)
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
        SpecWaits.await(subscribed, 'the subscription')
        CompletableFuture.supplyAsync { callQuietly('stubborn') }
        SpecWaits.await(handler.started, 'the start of the call')

        when: 'the JVM is sent SIGTERM, and runs its hooks'
        Thread hook = new Thread(onSignal, 'spec-shutdown-hook')
        long signalledAt = System.nanoTime()
        hook.start()
        SpecWaits.join(hook, 'the end of the hook')
        Duration took = Duration.ofNanos(System.nanoTime() - signalledAt)

        then: 'it waited a short while for the call, and closed everything, but left the exit to the JVM'
        took < McpLifecycle.graceOf(McpLifecycle.Reason.END_OF_INPUT)
        handler.events == ['call interrupted', 'connection closed after the SDK: true', 'generator closed']
        lifecycle.reason == McpLifecycle.Reason.SIGNAL
        mcp.server.calls.running()*.tool == ['stubborn']
        mcp.server.calls.running()[0].interrupted

        and: 'the main thread returned without exiting, and the listen stream has ended'
        SpecWaits.get(served, 'the end of serving') == 0
        !handler.events.any { String event -> event.startsWith('exit') }
        SpecWaits.await(listenEnded, 'the end of the listen stream')

        cleanup:
        listening?.close()
    }

    void "the server can ask to end with an exit code, and ends as it does at the end of its input"() {
        given:
        FutureTask<Integer> served = serve()

        when:
        lifecycle.requestExit(1)

        then:
        SpecWaits.get(served, 'the end of serving') == 1
        handler.events == ['connection closed after the SDK: true', 'generator closed', 'exit 1']
        lifecycle.reason == McpLifecycle.Reason.EXIT
    }

    void "the server ends once, however often and from however many threads it is asked to"() {
        given:
        CountDownLatch closing = new CountDownLatch(1)
        CountDownLatch closerRuns = new CountDownLatch(1)
        lifecycle.onShutdown('the applications the session started') {
            closerRuns.countDown()
            closing.await(SpecWaits.BOUND.toMillis(), TimeUnit.MILLISECONDS)
            handler.events << 'applications stopped'
        }

        when:
        Thread first = Thread.start { lifecycle.shutdown(McpLifecycle.Reason.END_OF_INPUT) }
        SpecWaits.await(closerRuns, 'the closer of the first')
        Thread second = Thread.start {
            lifecycle.shutdown(McpLifecycle.Reason.SIGNAL)
            handler.events << 'second returned'
        }
        // the second waits once its thread is parked, which is where it waits for the first
        SpecWaits.until('the wait of the second') { second.state in [Thread.State.WAITING, Thread.State.TIMED_WAITING, Thread.State.BLOCKED] }

        then: 'the second waits until the first has ended the server'
        second.alive
        !handler.events.contains('second returned')

        when:
        closing.countDown()
        SpecWaits.join(first, 'the end of the first')
        SpecWaits.join(second, 'the end of the second')
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
