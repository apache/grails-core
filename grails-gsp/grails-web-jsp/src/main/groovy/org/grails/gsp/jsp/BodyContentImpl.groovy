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
package org.grails.gsp.jsp

import groovy.transform.CompileStatic

import jakarta.servlet.jsp.JspWriter
import jakarta.servlet.jsp.tagext.BodyContent

import org.grails.buffer.StreamCharBuffer

/**
 * Uses an internal CharArrayWriter.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@CompileStatic
class BodyContentImpl extends BodyContent {

    static final char[] LINE_BREAK = System.getProperty('line.separator').toCharArray()

    private StreamCharBuffer streamBuffer
    private Writer streamBufferWriter

    BodyContentImpl(JspWriter out, boolean buffer) {
        super(out)
        if (buffer) initBuffer()
    }

    void initBuffer() {
        streamBuffer = new StreamCharBuffer()
        streamBufferWriter = streamBuffer.getWriter()
    }

    @Override
    void flush() throws IOException {
        if (streamBuffer == null) {
            getEnclosingWriter().flush()
        }
    }

    @Override
    void clear() throws IOException {
        clearBuffer()
    }

    @Override
    void clearBuffer() throws IOException {
        if (streamBuffer != null) {
            initBuffer()
        }
        else {
            throw new IOException("Can't clear")
        }
    }

    @Override
    int getRemaining() {
        return Integer.MAX_VALUE
    }

    @Override
    void newLine() throws IOException {
        write(LINE_BREAK)
    }

    @Override
    void close() throws IOException {
        // do nothing
    }

    @Override
    void print(boolean b) throws IOException {
        write(b ? Boolean.TRUE.toString() : Boolean.FALSE.toString())
    }

    @Override
    void print(char c) throws IOException {
        write((int) c)
    }

    @Override
    void print(char[] chars) throws IOException {
        write(chars)
    }

    @Override
    void print(double d) throws IOException {
        write(Double.toString(d))
    }

    @Override
    void print(float f) throws IOException {
        write(Float.toString(f))
    }

    @Override
    void print(int i) throws IOException {
        write(Integer.toString(i))
    }

    @Override
    void print(long l) throws IOException {
        write(Long.toString(l))
    }

    @Override
    void print(Object o) throws IOException {
        write(o == null ? 'null' : o.toString())
    }

    @Override
    void print(String s) throws IOException {
        write(s)
    }

    @Override
    void println() throws IOException {
        newLine()
    }

    @Override
    void println(boolean b) throws IOException {
        print(b)
        newLine()
    }

    @Override
    void println(char c) throws IOException {
        print(c)
        newLine()
    }

    @Override
    void println(char[] chars) throws IOException {
        print(chars)
        newLine()
    }

    @Override
    void println(double d) throws IOException {
        print(d)
        newLine()
    }

    @Override
    void println(float f) throws IOException {
        print(f)
        newLine()
    }

    @Override
    void println(int i) throws IOException {
        print(i)
        newLine()
    }

    @Override
    void println(long l) throws IOException {
        print(l)
        newLine()
    }

    @Override
    void println(Object o) throws IOException {
        print(o)
        newLine()
    }

    @Override
    void println(String s) throws IOException {
        print(s)
        newLine()
    }

    @Override
    void write(int c) throws IOException {
        if (streamBufferWriter != null) {
            streamBufferWriter.write(c)
        }
        else {
            getEnclosingWriter().write(c)
        }
    }

    @Override
    void write(char[] cbuf, int off, int len) throws IOException {
        if (streamBufferWriter != null) {
            streamBufferWriter.write(cbuf, off, len)
        }
        else {
            getEnclosingWriter().write(cbuf, off, len)
        }
    }

    @Override
    String getString() {
        return streamBuffer.toString()
    }

    @Override
    Reader getReader() {
        return streamBuffer.getReader()
    }

    @Override
    void writeOut(Writer out) throws IOException {
        streamBuffer.writeTo(out)
    }
}
