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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Supplier

import groovy.json.JsonSlurper
import io.modelcontextprotocol.modern.McpSchema.Implementation
import io.modelcontextprotocol.modern.client.McpClient
import io.modelcontextprotocol.modern.client.McpSyncClient
import io.modelcontextprotocol.modern.client.transport.StdioMcpClientTransport
import org.apache.grails.forge.cli.mcp.McpHandler
import org.grails.forge.cli.SpecWaits

/**
 * A server of {@code grails mcp} and the SDK's modern client in one JVM, over pipes: what the client writes
 * is the server's standard input, and what the server writes its standard output, every line of which is
 * also kept, so that a spec can read what the server wrote. A spec can also write a line of its own to the
 * server, as a client that is not the SDK's would.
 *
 * <p>A call of the client and a wait of a spec last at most the bound of every wait of the specs
 * ({@link SpecWaits#BOUND}): a call a spec holds open while it looks at others is not given up on by the client.</p>
 */
class InProcessMcp implements Closeable {

    private static final int PIPE = 1 << 20

    final ModernServer server
    final StdioMcpClientTransport transport
    final McpSyncClient client

    private final PipedOutputStream toServer
    private final List<String> written = new CopyOnWriteArrayList<>()

    InProcessMcp(McpHandler handler) {
        PipedInputStream serverInput = new PipedInputStream(PIPE)
        toServer = new PipedOutputStream(serverInput)
        PipedInputStream clientInput = new PipedInputStream(PIPE)
        OutputStream serverOutput = new LineKeeper(new PipedOutputStream(clientInput), written)
        server = new ModernServer(handler, serverInput, serverOutput)
        transport = StdioMcpClientTransport.builder(clientInput, toServer).start()
        client = McpClient.sync(transport)
                .clientInfo(Implementation.builder('grails-mcp-spec', '1.0').build())
                .roundTimeout(SpecWaits.BOUND)
                .build()
    }

    /** Writes one line to the server, as a client that is not the SDK's would; while the SDK's client is idle. */
    void send(String line) {
        synchronized (toServer) {
            toServer.write((line + '\n').getBytes(StandardCharsets.UTF_8))
            toServer.flush()
        }
    }

    /** @return the lines the server wrote so far, each read as JSON */
    List<Map<String, Object>> lines() {
        return written.collect { String line -> (Map<String, Object>) new JsonSlurper().parseText(line) }
    }

    /** @return the lines the server wrote so far, as it wrote them */
    List<String> rawLines() {
        return new ArrayList<String>(written)
    }

    /** Waits for the server to write a line that the condition holds for, and gives it. */
    Map<String, Object> awaitLine(Closure<Boolean> condition) {
        try {
            return SpecWaits.value('a line of the server that the condition holds for', { -> lines().find(condition) } as Supplier<Map<String, Object>>)
        } catch (AssertionError e) {
            throw new AssertionError("${e.message}; it wrote ${written}".toString())
        }
    }

    /** Waits for the condition to hold. */
    static void await(Closure<Boolean> condition) {
        SpecWaits.until('the condition', { -> condition.call() as boolean } as Supplier<Boolean>)
    }

    @Override
    void close() {
        transport.close()
        server.close()
    }

    // what the server writes, passed on to the client, and kept line by line
    private static final class LineKeeper extends FilterOutputStream {

        private final List<String> lines
        private final ByteArrayOutputStream line = new ByteArrayOutputStream()

        LineKeeper(OutputStream out, List<String> lines) {
            super(out)
            this.lines = lines
        }

        @Override
        synchronized void write(int b) {
            keep(b)
            out.write(b)
        }

        @Override
        synchronized void write(byte[] bytes, int offset, int length) {
            for (int i = offset; i < offset + length; i++) {
                keep(bytes[i])
            }
            out.write(bytes, offset, length)
        }

        private void keep(int b) {
            if (b == ('\n' as char) as int) {
                lines << line.toString(StandardCharsets.UTF_8)
                line.reset()
            } else {
                line.write(b)
            }
        }
    }
}
