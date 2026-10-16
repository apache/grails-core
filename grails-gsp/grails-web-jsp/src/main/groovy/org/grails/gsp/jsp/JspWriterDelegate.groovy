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

/**
 * Delegates to another java.io.Writer.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class JspWriterDelegate extends JspWriter {

    static final char[] LINE_BREAK = System.getProperty('line.separator').toCharArray()

    private final Writer out

    JspWriterDelegate(Writer out) {
        super(0, true)
        this.out = out
    }

    @Override
    String toString() {
        return out.toString()
    }

    @Override
    void clear() throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    void clearBuffer() throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    void close() throws IOException {
        throw new UnsupportedOperationException()
    }

    @Override
    void flush() throws IOException {
        out.flush()
    }

    @Override
    int getRemaining() {
        return 0
    }

    @Override
    void newLine() throws IOException {
        out.write(LINE_BREAK)
    }

    @Override
    void print(boolean b) throws IOException {
        out.write(b ? Boolean.TRUE.toString() : Boolean.FALSE.toString())
    }

    @Override
    void print(char c) throws IOException {
        out.write((int) c)
    }

    @Override
    void print(char[] cArray) throws IOException {
        out.write(cArray)
    }

    @Override
    void print(double d) throws IOException {
        out.write(Double.toString(d))
    }

    @Override
    void print(float f) throws IOException {
        out.write(Float.toString(f))
    }

    @Override
    void print(int i) throws IOException {
        out.write(Integer.toString(i))
    }

    @Override
    void print(long l) throws IOException {
        out.write(Long.toString(l))
    }

    @Override
    void print(Object o) throws IOException {
        out.write(o == null ? 'null' : o.toString())
    }

    @Override
    void print(String s) throws IOException {
        out.write(s)
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
        out.write(c)
    }

    @Override
    void write(char[] cbuf, int off, int len) throws IOException {
        out.write(cbuf, off, len)
    }
}
