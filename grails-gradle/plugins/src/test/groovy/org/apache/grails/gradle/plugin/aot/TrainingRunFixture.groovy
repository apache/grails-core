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
package org.apache.grails.gradle.plugin.aot

import java.util.concurrent.CountDownLatch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer

/**
 * Stands in for the application a training run starts, so the run can be exercised for real.
 *
 * <p>It does the three things {@link TrainAotCacheTask} depends on and nothing else: it says it
 * started the way Spring Boot says it, it answers on the paths it is asked for and writes down
 * which, and it writes its cache while shutting down rather than when told to stop -- which is what
 * makes a killed run leave nothing behind.</p>
 *
 * <p>Run by the script the spec writes, not by {@code java -jar}: a real JVM would have to
 * understand {@code -XX:AOTCacheOutput}, which ties the test to the JDK the build happens to run
 * on.</p>
 */
class TrainingRunFixture {

    static void main(String[] args) {
        File cache = new File(args[0])
        int port = Integer.parseInt(args[1])
        File requested = new File(args[2])

        HttpServer server = HttpServer.create(new InetSocketAddress('localhost', port), 0)
        server.createContext('/') { HttpExchange exchange ->
            requested << "${exchange.requestURI.path}\n"
            byte[] body = 'served'.bytes
            int status = exchange.requestURI.path == '/missing' ? 404 : 200
            exchange.sendResponseHeaders(status, body.length)
            exchange.responseBody.withCloseable { it.write(body) }
        }
        server.start()

        Runtime.runtime.addShutdownHook(new Thread({
            cache.text = 'a cache is what a run leaves behind as it goes'
        }))

        if (args.length < 4 || args[3] != 'quiet') {
            println 'Started Application in 0.05 seconds'
            System.out.flush()
        }

        new CountDownLatch(1).await()
    }
}
