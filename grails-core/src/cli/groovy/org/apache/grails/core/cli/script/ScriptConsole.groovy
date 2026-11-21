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
package org.apache.grails.core.cli.script

import groovy.transform.CompileStatic

import grails.build.logging.GrailsConsole

/**
 * The console of a script command that the build runs. What a script says goes through Gradle,
 * which is no terminal: every message is one whole line of plain text, with nothing that moves a
 * cursor, colours or rewrites the line before it, and progress is not drawn.
 *
 * @since 9.0
 */
@CompileStatic
class ScriptConsole extends GrailsConsole {

    private static final String BAR = '| '

    private final PrintStream plainOut
    private final PrintStream plainErr

    /**
     * @param out where what a script says is written, or {@code null} for the standard output of the JVM
     * @param err where its errors and warnings are written, or {@code null} for the standard error of the JVM
     */
    ScriptConsole(PrintStream out, PrintStream err) throws IOException {
        super()
        // without one, where the JVM writes: not System.out as it is now, which writes through the console
        this.plainOut = out ?: super.getOut()
        this.plainErr = err ?: super.getErr()
    }

    /**
     * @return where the console writes: a script that writes there itself writes where its
     * messages go
     */
    @Override
    PrintStream getOut() {
        return plainOut ?: super.getOut()
    }

    @Override
    PrintStream getErr() {
        return plainErr ?: super.getErr()
    }

    @Override
    boolean isAnsiEnabled() {
        return false
    }

    @Override
    void indicateProgress() {
    }

    @Override
    void indicateProgress(int number, int total) {
    }

    @Override
    void indicateProgressPercentage(long number, long total) {
    }

    @Override
    void indicateProgress(int number) {
    }

    @Override
    void updateStatus(String msg) {
        line(plainOut, BAR + msg)
    }

    @Override
    void addStatus(String msg) {
        line(plainOut, BAR + msg)
    }

    @Override
    void error(String msg) {
        line(plainErr, "${BAR}Error ${msg}")
    }

    @Override
    void warning(String msg) {
        line(plainErr, "${BAR}Warning ${msg}")
    }

    @Override
    void warn(String msg) {
        warning(msg)
    }

    @Override
    void error(String msg, Throwable error) {
        line(plainErr, "${BAR}Error ${msg}")
        if (error != null && (isStacktrace() || Boolean.getBoolean('grails.full.stacktrace'))) {
            error.printStackTrace(plainErr)
            plainErr.flush()
        }
    }

    @Override
    void error(Throwable error) {
        this.error(String.valueOf(error?.message), error)
    }

    @Override
    void error(String label, String message) {
        line(plainErr, "${BAR}${label} ${message}")
    }

    @Override
    void log(String msg) {
        line(plainOut, msg)
    }

    @Override
    void append(String msg) {
        line(plainOut, msg)
    }

    @Override
    void info(String msg) {
        line(plainOut, msg)
    }

    @Override
    void verbose(String msg) {
        if (isVerbose()) {
            line(plainOut, msg)
        }
    }

    private void line(PrintStream stream, String text) {
        setLastMessage(text)
        stream.println(text)
        stream.flush()
    }
}
