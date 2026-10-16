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
package startupprogress

import java.util.concurrent.TimeUnit

import spock.lang.Specification

import org.springframework.context.ConfigurableApplicationContext

import grails.boot.GrailsApp

/**
 * Starts the application the way {@code bootRun} does, on a port of its own with the progress page turned
 * on, and follows the start the way the progress page does: polling the status until it says the
 * application is ready.
 */
class StartupProgressPageSpec extends Specification {

    private static final String STATUS_PATH = '/__grails/startup-progress'

    ConfigurableApplicationContext context

    void cleanup() {
        context?.close()
    }

    void 'a browser opening the application while it starts sees progress until BootStrap has run'() {
        given: 'an application whose beans and BootStrap take their time'
        int port = freePort()
        Throwable failure = null
        Thread runner = Thread.start('startup-progress-app') {
            try {
                context = GrailsApp.run(Application,
                        "--server.port=${port}".toString(),
                        '--grails.startup.progress.enabled=true',
                        '--startup.demo.delay.searchIndex=2s',
                        '--startup.demo.bootstrapDelay=3s')
            }
            catch (Throwable ex) {
                failure = ex
            }
        }

        when: 'the status is polled until it says the application is ready, and the application is asked for its page at that moment'
        Set<String> phases = new LinkedHashSet<>()
        String pageWhenReady = null
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2)
        while (pageWhenReady == null && System.nanoTime() < deadline) {
            Map response = get(port, STATUS_PATH)
            if (response?.phase) {
                phases << response.phase
            }
            if (response?.phase == 'READY') {
                pageWhenReady = get(port, '/')?.body
            }
            Thread.sleep(50)
        }
        runner.join(TimeUnit.MINUTES.toMillis(1))

        then: 'the page was told about the beans being created and about BootStrap running'
        failure == null
        phases.contains('CREATING_BEANS')
        phases.contains('INITIALIZING')

        and: 'when the page was first told the application is ready, BootStrap had already finished'
        phases.contains('READY')
        pageWhenReady.contains('BootStrap seeded 3 catalog items')
    }

    private static Map get(int port, String path) {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:${port}${path}").toURL().openConnection()
        connection.connectTimeout = 1000
        connection.readTimeout = 10_000
        connection.setRequestProperty('Accept', 'application/json')
        try {
            int status = connection.responseCode
            InputStream body = status >= 400 ? connection.errorStream : connection.inputStream
            return [status: status, phase: connection.getHeaderField('Grails-Startup-Phase'), body: body?.getText('UTF-8') ?: '']
        }
        catch (IOException ignored) {
            // nothing is listening yet, or the port is changing hands
            return null
        }
        finally {
            connection.disconnect()
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.localPort
        }
    }
}
