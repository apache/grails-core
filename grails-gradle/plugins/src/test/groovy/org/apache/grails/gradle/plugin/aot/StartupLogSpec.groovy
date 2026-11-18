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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets

/**
 * Covers telling an application that has started from a container that has started something.
 */
class StartupLogSpec extends Specification {

    @TempDir
    File temporaryFolder

    private File log

    private StartupLog startupLog() {
        log = new File(temporaryFolder, 'run.log')
        new StartupLog(log)
    }

    private void append(String text) {
        log.append(text, 'UTF-8')
    }

    void 'a log that does not exist yet has not started'() {
        expect:
            !startupLog().saysItStarted()
    }

    void 'what a container prints while it is still coming up is not the application starting'() {
        given: 'the lines Jetty prints before the context is built'
            StartupLog started = startupLog()
            append('''\
Started ServerConnector@6d4b1c02{HTTP/1.1, (http/1.1)}{0.0.0.0:8080}
Started Server@1f2a3b4c{STARTING}[12.0.0]
''')

        expect: 'trained or traced here, what is recorded is a half-built application'
            !started.saysItStarted()
    }

    void 'the line Spring Boot prints when it has finished starting is'() {
        given:
            StartupLog started = startupLog()
            append('Started Application in 2.597 seconds (process running for 3.068)\n')

        expect:
            started.saysItStarted()
    }

    void 'a line still being written is left rather than cut in half and missed'() {
        given: 'the log read while the line is half written, as polling it will do'
            StartupLog started = startupLog()
            append('Started Applicat')

        expect: 'no answer is given for a line that is not finished'
            !started.saysItStarted()

        when: 'the rest of it arrives'
            append('ion in 2.597 seconds (process running for 3.068)\n')

        then: 'it is read as the one line it is, rather than as the two reads it arrived in'
            started.saysItStarted()
    }

    void 'once it has started it stays started'() {
        given:
            StartupLog started = startupLog()
            append('Started Application in 1.0 seconds\n')

        expect:
            started.saysItStarted()

        when: 'the file is truncated, as a rerun into the same log would'
            log.text = ''

        then: 'the run that already said it started is not un-started by it'
            started.saysItStarted()
    }

    void 'the log is read as UTF-8 rather than as whatever the machine defaults to'() {
        given: 'a log with a name outside ASCII before the line that matters'
            StartupLog started = startupLog()
            log.newOutputStream().withCloseable { OutputStream out ->
                out.write('Ignorer les caractères accentués — ça arrive\n'.getBytes(StandardCharsets.UTF_8))
                out.write('Started Applicatión in 2.5 seconds\n'.getBytes(StandardCharsets.UTF_8))
            }

        expect:
            started.saysItStarted()
    }

    void 'a word that is only the container announcing a port never satisfies it'() {
        given:
            StartupLog started = startupLog()
            append(line + '\n')

        expect:
            started.saysItStarted() == counts

        where:
            line                                                        || counts
            'Started ServerConnector@1{HTTP/1.1}{0.0.0.0:8080}'         || false
            'Tomcat started on port 8080 (http) with context path \'/\'' || false
            'Started Server@2{STARTING}[12.0.0]'                        || false
            'Started Application in 2.597 seconds'                      || true
            'Started MyApp in 12.5 seconds (process running for 13.1)'  || true
    }
}
