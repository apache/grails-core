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
package org.grails.build.logging;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the ANSI escape sequences {@code GrailsConsole} writes to the terminal.
 *
 * <p>This replaces the former dependency on a separate ANSI library. The console already depends on
 * JLine for its terminal and line reader, and the sequences needed here are a small, fixed set of
 * standard ones, so an extra artifact bought nothing. The escapes are emitted directly rather than
 * looked up through terminfo capabilities so that output does not vary with the terminal database
 * available at runtime - the previous implementation behaved the same way.</p>
 *
 * <p>Style changes are buffered and written as a single SGR sequence when text is appended, so
 * setting bold and a colour together produces {@code ESC[1;31m} rather than two separate escapes.</p>
 *
 * <p>Internal API - not part of the supported public surface.</p>
 *
 * @since 8.0
 */
public final class ConsoleAnsi {

    /** Control Sequence Introducer. */
    private static final String CSI = "[";

    public static final int BOLD = 1;
    public static final int BOLD_OFF = 22;
    public static final int FG_RED = 31;
    public static final int FG_GREEN = 32;
    public static final int FG_YELLOW = 33;
    public static final int FG_CYAN = 36;
    public static final int FG_DEFAULT = 39;

    private final StringBuilder sb = new StringBuilder();
    private final List<Integer> pendingStyles = new ArrayList<>(4);

    private ConsoleAnsi() {
    }

    public static ConsoleAnsi ansi() {
        return new ConsoleAnsi();
    }

    /** Queues an SGR style code; it is written when the next text is appended. */
    public ConsoleAnsi style(int code) {
        this.pendingStyles.add(code);
        return this;
    }

    public ConsoleAnsi bold() {
        return style(BOLD);
    }

    public ConsoleAnsi boldOff() {
        return style(BOLD_OFF);
    }

    public ConsoleAnsi fg(int colorCode) {
        return style(colorCode);
    }

    public ConsoleAnsi a(Object text) {
        flushStyles();
        this.sb.append(text);
        return this;
    }

    /** Full reset (SGR 0), written as the shorthand {@code ESC[m}. */
    public ConsoleAnsi reset() {
        flushStyles();
        this.sb.append(CSI).append('m');
        return this;
    }

    public ConsoleAnsi cursorUp(int lines) {
        return move(lines, 'A');
    }

    public ConsoleAnsi cursorDown(int lines) {
        return move(lines, 'B');
    }

    public ConsoleAnsi cursorLeft(int columns) {
        return move(columns, 'D');
    }

    /** Erases from the cursor to the end of the line. */
    public ConsoleAnsi eraseLineForward() {
        flushStyles();
        this.sb.append(CSI).append("0K");
        return this;
    }

    /** Erases from the start of the line to the cursor. */
    public ConsoleAnsi eraseLineBackward() {
        flushStyles();
        this.sb.append(CSI).append("1K");
        return this;
    }

    private ConsoleAnsi move(int amount, char code) {
        flushStyles();
        this.sb.append(CSI).append(amount).append(code);
        return this;
    }

    private void flushStyles() {
        if (this.pendingStyles.isEmpty()) {
            return;
        }
        this.sb.append(CSI);
        for (int i = 0; i < this.pendingStyles.size(); i++) {
            if (i > 0) {
                this.sb.append(';');
            }
            this.sb.append(this.pendingStyles.get(i));
        }
        this.sb.append('m');
        this.pendingStyles.clear();
    }

    @Override
    public String toString() {
        flushStyles();
        return this.sb.toString();
    }
}
