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
package org.grails.web.converters.jackson;

import java.io.IOException;
import java.io.Writer;

/**
 * Escapes JSON text so that it can be embedded in an HTML {@code <script>} element, as {@code grails.converters.JSON}
 * has always escaped it: {@code </} is written as <code>&lt;&#92;u002f</code>, and the line and paragraph separators
 * U+2028 and U+2029, which end a JavaScript string literal, as <code>&#92;u2028</code> and <code>&#92;u2029</code>.
 *
 * <p>JSON text can only contain these outside a string where it is not well formed, so escaping them in the output
 * stream escapes every string, whether a Grails marshaller or the JsonMapper wrote it, and leaves the parsed values
 * unchanged.
 *
 * @since 9.0
 */
final class HtmlSafeJsonWriter extends Writer {

    private static final String ESCAPED_END_TAG = "<\\u002f";

    private final Writer out;

    // a '<' at the end of the last write, written once the next character shows whether it starts "</"
    private boolean pendingLessThan;

    HtmlSafeJsonWriter(Writer out) {
        this.out = out;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        int end = off + len;
        int runStart = off;
        for (int i = off; i < end; i++) {
            char c = cbuf[i];
            if (pendingLessThan) {
                pendingLessThan = false;
                if (c == '/') {
                    out.write(ESCAPED_END_TAG);
                    runStart = i + 1;
                    continue;
                }
                out.write('<');
            }
            if (c == '<') {
                out.write(cbuf, runStart, i - runStart);
                if (i + 1 == end) {
                    pendingLessThan = true;
                }
                else if (cbuf[i + 1] == '/') {
                    out.write(ESCAPED_END_TAG);
                    i++;
                }
                else {
                    out.write('<');
                }
                runStart = i + 1;
            }
            else if (c == '\u2028' || c == '\u2029') {
                out.write(cbuf, runStart, i - runStart);
                out.write(c == '\u2028' ? "\\u2028" : "\\u2029");
                runStart = i + 1;
            }
        }
        if (runStart < end) {
            out.write(cbuf, runStart, end - runStart);
        }
    }

    @Override
    public void flush() throws IOException {
        // a pending '<' is kept, since the next write may start with the '/' of "</"
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (pendingLessThan) {
            pendingLessThan = false;
            out.write('<');
        }
        out.close();
    }
}
