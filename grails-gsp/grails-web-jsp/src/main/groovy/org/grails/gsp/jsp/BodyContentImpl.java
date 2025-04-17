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
import jakarta.servlet.jsp.tagext.BodyContent;
import org.grails.buffer.StreamCharBuffer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Uses an internal CharArrayWriter.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
class BodyContentImpl extends BodyContent {

    static final char[] LINE_BREAK = System.getProperty("line.separator").toCharArray();

    private StreamCharBuffer streamBuffer;
    private Writer streamBufferWriter;

    BodyContentImpl(JspWriter out, boolean buffer) {
        super(out);
        if (buffer) initBuffer();
    }

    void initBuffer() {
        streamBuffer = new StreamCharBuffer();
        streamBufferWriter = streamBuffer.getWriter();
    }

    @Override
    public void flush() throws IOException {
        if (streamBuffer == null) {
            getEnclosingWriter().flush();
        }
    }

    @Override
    public void clear() throws IOException {
        clearBuffer();
    }

    @Override
    public void clearBuffer() throws IOException {
        if (streamBuffer != null) {
            initBuffer();
        }
        else {
            throw new IOException("Can't clear");
        }
    }

    @Override
    public int getRemaining() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void newLine() throws IOException {
        write(LINE_BREAK);
    }

    @Override
    public void close() throws IOException {
        // do nothing
    }

    @Override
    public void print(boolean b) throws IOException {
        write(b ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
    }

    @Override
    public void print(char c) throws IOException {
        write(c);
    }

    @Override
    public void print(char[] chars) throws IOException {
        write(chars);
    }

    @Override
    public void print(double d) throws IOException {
        write(Double.toString(d));
    }

    @Override
    public void print(float f) throws IOException {
        write(Float.toString(f));
    }

    @Override
    public void print(int i) throws IOException {
        write(Integer.toString(i));
    }

    @Override
    public void print(long l) throws IOException {
        write(Long.toString(l));
    }

    @Override
    public void print(Object o) throws IOException {
        write(o == null ? "null" : o.toString());
    }

    @Override
    public void print(String s) throws IOException {
        write(s);
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
        if (streamBufferWriter != null) {
            streamBufferWriter.write(c);
        }
        else {
            getEnclosingWriter().write(c);
        }
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (streamBufferWriter != null) {
            streamBufferWriter.write(cbuf, off, len);
        }
        else {
            getEnclosingWriter().write(cbuf, off, len);
        }
    }

    @Override
    public String getString() {
        return streamBuffer.toString();
    }

    @Override
    public Reader getReader() {
        return streamBuffer.getReader();
    }

    @Override
    public void writeOut(Writer out) throws IOException {
        streamBuffer.writeTo(out);
    }
}
