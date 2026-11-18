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

import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * What a starting application prints, read as it is written.
 *
 * <p>Asked whether the application has finished starting, rather than whether the word "Started"
 * has appeared. A container prints that word well before the application is ready -- Jetty logs
 * {@code Started ServerConnector@...} and {@code Started Server@...} while the context is still
 * being built -- and a run taken for started there is trained or traced half-built. What is
 * recorded is then quietly thinner than it should be, which is the kind of thing that shows up as a
 * slower start or a missing class in an image rather than as anything that failed.</p>
 *
 * <p>Spring Boot's own line has a shape no container's does: a name, {@code in}, and a number of
 * seconds. That is what is matched.</p>
 *
 * <p>Only what has been added since the last look is read, and it is read as UTF-8 rather than as
 * whatever the machine's default happens to be. Matching is on whole lines, so a line still being
 * written is left for the next look rather than being cut in half and missed.</p>
 */
@CompileStatic
final class StartupLog {

    /** Spring Boot's, e.g. {@code Started Application in 2.597 seconds (process running for 3.068)}. */
    private static final Pattern STARTED = Pattern.compile(/Started .+ in [\d.]+ seconds/)

    private static final int MOST_READ_AT_ONCE = 1 << 20

    private final File file

    /** How far into the file has already been read, so a poll costs the new bytes rather than all. */
    private long position

    /** What was read but is not yet a whole line. */
    private final StringBuilder pending = new StringBuilder()

    private boolean started

    StartupLog(File file) {
        this.file = file
    }

    /**
     * Whether the application has said it finished starting.
     *
     * <p>Answered from what has been written so far, so it is asked repeatedly while waiting.</p>
     */
    boolean saysItStarted() {
        if (started) {
            return true
        }
        if (!file.isFile()) {
            return false
        }
        read()
        int newline
        while ((newline = pending.indexOf('\n')) >= 0) {
            String line = pending.substring(0, newline)
            pending.delete(0, newline + 1)
            if (STARTED.matcher(line).find()) {
                started = true
                return true
            }
        }
        false
    }

    private void read() {
        long length = file.length()
        // A file that shrank is one that was replaced, so start again rather than read from the
        // middle of it.
        if (length < position) {
            position = 0
            pending.setLength(0)
        }
        while (length > position) {
            int wanted = (int) Math.min(length - position, MOST_READ_AT_ONCE as long)
            byte[] bytes = new byte[wanted]
            int read = 0
            new RandomAccessFile(file, 'r').withCloseable { RandomAccessFile open ->
                open.seek(position)
                read = open.read(bytes)
            }
            if (read <= 0) {
                return
            }
            pending.append(new String(bytes, 0, read, StandardCharsets.UTF_8))
            position += read
        }
    }
}
