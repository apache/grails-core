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
package org.apache.grails.startup;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Holds the application's port until the embedded web server is about to take it over, answering every
 * request on it with the {@link StartupProgressResponder}: the progress page for a browser, the status
 * the page polls for, and a plain {@code 503} for anything else.
 */
final class StartupProgressServer {

    private final InetSocketAddress address;

    private final StartupProgressResponder responder;

    private HttpServer server;

    private ExecutorService executor;

    private volatile boolean running;

    StartupProgressServer(InetAddress address, int port, StartupProgressResponder responder) {
        this.address = new InetSocketAddress(address, port);
        this.responder = responder;
    }

    /**
     * Binds the port and starts answering. A stopped server can be started again.
     *
     * @throws IOException when the port cannot be bound, typically because something else holds it
     */
    void start() throws IOException {
        if (running) {
            return;
        }
        HttpServer httpServer = HttpServer.create(address, 0);
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "grails-startup-progress");
            thread.setDaemon(true);
            return thread;
        });
        httpServer.createContext("/", this::handle);
        httpServer.setExecutor(pool);
        httpServer.start();
        server = httpServer;
        executor = pool;
        running = true;
    }

    boolean isRunning() {
        return running;
    }

    /**
     * Closes the listening socket and returns once it is released, so the web server can bind the port
     * straight after.
     */
    void stop() {
        if (!running) {
            return;
        }
        running = false;
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            responder.respond(new JdkExchange(exchange), StartupProgressResponder.Takes.EVERY_REQUEST);
        }
    }

    /** A request to the JDK's HTTP server, as the responder answers it. */
    private record JdkExchange(HttpExchange exchange) implements StartupProgressResponder.Exchange {

        @Override
        public String method() {
            return exchange.getRequestMethod();
        }

        @Override
        public String path() {
            return exchange.getRequestURI().getRawPath();
        }

        @Override
        public String query() {
            return exchange.getRequestURI().getRawQuery();
        }

        @Override
        public String header(String name) {
            return exchange.getRequestHeaders().getFirst(name);
        }

        @Override
        public List<String> headers(String name) {
            List<String> values = exchange.getRequestHeaders().get(name);
            return values != null ? values : List.of();
        }

        @Override
        public void setHeader(String name, String value) {
            exchange.getResponseHeaders().set(name, value);
        }

        @Override
        public void send(int status, String contentType, byte[] body) throws IOException {
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            if (body.length == 0 || "HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
