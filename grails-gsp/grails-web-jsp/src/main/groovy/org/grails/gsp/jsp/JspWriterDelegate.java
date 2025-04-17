/*
 * Copyright 2004-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gsp.jsp;

import jakarta.servlet.jsp.JspWriter;

import java.io.IOException;
import java.io.Writer;

/**
 * Delegates to another java.io.Writer.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JspWriterDelegate extends JspWriter {

    static final char[] LINE_BREAK = System.getProperty("line.separator").toCharArray();

    private final Writer out;

    JspWriterDelegate(Writer out) {
        super(0, true);
        this.out = out;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    @Override
    public void clear() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearBuffer() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public int getRemaining() {
        return 0;
    }

    @Override
    public void newLine() throws IOException {
        out.write(LINE_BREAK);
    }

    @Override
    public void print(boolean b) throws IOException {
        out.write(b ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
    }

    @Override
    public void print(char c) throws IOException {
        out.write(c);
    }

    @Override
    public void print(char[] cArray) throws IOException {
        out.write(cArray);
    }

    @Override
    public void print(double d) throws IOException {
        out.write(Double.toString(d));
    }

    @Override
    public void print(float f) throws IOException {
        out.write(Float.toString(f));
    }

    @Override
    public void print(int i) throws IOException {
        out.write(Integer.toString(i));
    }

    @Override
    public void print(long l) throws IOException {
        out.write(Long.toString(l));
    }

    @Override
    public void print(Object o) throws IOException {
        out.write(o == null ? "null" : o.toString());
    }

    @Override
    public void print(String s) throws IOException {
        out.write(s);
    }

    @Override
    public void println() throws IOException {
        newLine();
    }

    @Override
    public void println(boolean b) throws IOException {
        print(b);
        newLine();
    }

    @Override
    public void println(char c) throws IOException {
        print(c);
        newLine();
    }

    @Override
    public void println(char[] chars) throws IOException {
        print(chars);
        newLine();
    }

    @Override
    public void println(double d) throws IOException {
        print(d);
        newLine();
    }

    @Override
    public void println(float f) throws IOException {
        print(f);
        newLine();
    }

    @Override
    public void println(int i) throws IOException {
        print(i);
        newLine();
    }

    @Override
    public void println(long l) throws IOException {
        print(l);
        newLine();
    }

    @Override
    public void println(Object o) throws IOException {
        print(o);
        newLine();
    }

    @Override
    public void println(String s) throws IOException {
        print(s);
        newLine();
    }

    @Override
    public void write(int c) throws IOException {
        out.write(c);
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        out.write(cbuf, off, len);
    }
}
