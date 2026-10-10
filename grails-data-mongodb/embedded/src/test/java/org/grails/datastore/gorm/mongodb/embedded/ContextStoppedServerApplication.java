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
package org.grails.datastore.gorm.mongodb.embedded;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * An application that starts an in-memory server the way any application does, lets a client
 * connect, and stops the server as its context closes. Returning from {@code main} is all that is
 * left, and the JVM then exits through the shutdown hook the initializer registered, which stops the
 * server a second time.
 *
 * <p>{@code EmbeddedMongoLifecycleSpec} runs it in a JVM of its own, since whether that JVM exits is
 * the question. The shutdown hang is a race: this Java application reproduced it reliably on the
 * author's machine, while another machine reproduced it only in the in-process Spock feature.
 * Both tests exercise the race, but neither is guaranteed to reproduce it without the fix.
 */
public final class ContextStoppedServerApplication {

    private ContextStoppedServerApplication() {
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("application", Map.of(
                EmbeddedMongoInitializer.BACKEND, InMemoryMongoBackend.NAME,
                "grails.mongodb.url", "mongodb://embedded:" + port + "/bookstore")));
        new EmbeddedMongoInitializer().initialize(context);
        EmbeddedMongoLifecycle lifecycle = context.getBeanFactory()
                .getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle.class);
        new Socket("localhost", port).close();
        lifecycle.stop();
    }
}
