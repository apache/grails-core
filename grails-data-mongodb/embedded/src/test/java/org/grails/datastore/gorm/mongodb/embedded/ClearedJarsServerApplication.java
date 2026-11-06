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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBufAllocator;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * An application whose classes stop loading for a moment after the server has started, as they do
 * in an executable jar: Spring Boot closes the jars of its class loader when the context has
 * refreshed, and a class being loaded at that moment fails with {@code IllegalStateException}.
 * A client sends its first bytes during that moment, as the MongoDB driver does on the way to that
 * event, and stays connected.
 *
 * <p>{@code EmbeddedMongoLifecycleSpec} runs it in a JVM of its own and reads what it printed. Reading
 * those bytes allocates the event loop's first buffer, which fails if the class of it is not loaded
 * yet. Netty then logs that, which in an application fails as well, since Logback loads a class of its
 * own to log an exception. That ends the event loop's thread, which leaves the client's connection on
 * it open for good, and stopping the server then waits forever. The logger of this JVM cannot fail
 * that way, so what the spec looks for is the first failure.
 */
public final class ClearedJarsServerApplication {

    private ClearedJarsServerApplication() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        ClearedJarsClassLoader loader = new ClearedJarsClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> server = loader.loadClass(Server.class.getName());
        Runnable stop = (Runnable) server.getMethod("start", int.class).invoke(null, port);
        Method allocate = server.getMethod("allocateAsAnEventLoopDoes");

        try (Socket client = new Socket("localhost", port)) {
            Thread.sleep(1000L);
            loader.cleared = true;
            client.getOutputStream().write(1);
            client.getOutputStream().flush();
            try {
                allocate.invoke(null);
            }
            catch (InvocationTargetException ex) {
                System.out.println(ex.getCause());
            }
            Thread.sleep(1000L);
            loader.cleared = false;

            stop.run();
        }
    }

    /** Created by the class loader above, so that what it loads is loaded through it. */
    public static final class Server {

        private Server() {
        }

        /**
         * As many buffers as it takes for Netty's leak detection to track one, which it does at random
         * and which loads classes of its own. Without this, a server missing those classes would pass
         * all but about once in 128 runs.
         */
        public static void allocateAsAnEventLoopDoes() {
            for (int i = 0; i < 2048; i++) {
                ByteBufAllocator.DEFAULT.ioBuffer(1).release();
            }
        }

        public static Runnable start(int port) {
            GenericApplicationContext context = new GenericApplicationContext();
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("application", Map.of(
                    EmbeddedMongoInitializer.BACKEND, InMemoryMongoBackend.NAME,
                    "grails.mongodb.url", "mongodb://embedded:" + port + "/bookstore")));
            new EmbeddedMongoInitializer().initialize(context);
            EmbeddedMongoLifecycle lifecycle = context.getBeanFactory()
                    .getBean(EmbeddedMongoLifecycle.BEAN_NAME, EmbeddedMongoLifecycle.class);
            return lifecycle::stop;
        }
    }

    private static final class ClearedJarsClassLoader extends URLClassLoader {

        private volatile boolean cleared;

        private ClearedJarsClassLoader() throws MalformedURLException {
            super(classPath(), ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (this.cleared) {
                throw new IllegalStateException("Zip file closed");
            }
            return super.findClass(name);
        }

        private static URL[] classPath() throws MalformedURLException {
            List<URL> urls = new ArrayList<>();
            for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
                urls.add(new File(entry).toURI().toURL());
            }
            return urls.toArray(new URL[0]);
        }
    }
}
