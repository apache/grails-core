/*
 * Copyright 2009 the original author or authors.
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
package org.grails.web.util;

import org.grails.buffer.StringCharArrayAccessor;

import java.io.IOException;
import java.io.Writer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * Counts chars encoded as bytes up to a certain limit (capacity of byte buffer).
 *
 * size() returns the number of bytes, it will return -1 if the capacity was
 * reached or an error occurred.
 *
 * this class is useful for calculating the content length of a
 * HttpServletResponse before the response has been committed
 *
 * @author Lari Hotari, Sagire Software Oy
 */
public class BoundedCharsAsEncodedBytesCounter {
    private String encoding;
    private int capacity;
    private ByteBuffer bb;
    private CharsetEncoder ce;
    private boolean calculationActive = true;
    private BoundedCharsAsEncodedBytesCounterWriter writer;

    public BoundedCharsAsEncodedBytesCounter() {
    }

    public BoundedCharsAsEncodedBytesCounter(int capacity, String encoding) {
        this.capacity = capacity;
        this.encoding = encoding;
    }

    public void update(String str) {
        if (str.length() == 0) {
            return;
        }
        if (calculationActive) {
            update(str.toCharArray());
        }
    }

    public void update(char[] buf) {
        update(buf, 0, buf.length);
    }

    public void update(char[] buf, int off, int len) {
        if (calculationActive && len > 0) {
            try {
                CharBuffer cb = CharBuffer.wrap(buf, off, len);
                ce.reset();
                CoderResult cr = ce.encode(cb, bb, true);
                if (!cr.isUnderflow()) {
                    terminateCalculation();
                    return;
                }
                cr = ce.flush(bb);
                if (!cr.isUnderflow()) {
                    terminateCalculation();
                    return;
                }
            }
            catch (BufferOverflowException e) {
                terminateCalculation();
            }
            catch (Exception x) {
                terminateCalculation();
            }
        }
    }

    private void terminateCalculation() {
        calculationActive = false;
        if (bb != null) {
            bb.clear();
            bb = null;
        }
    }

    public int size() {
        if (calculationActive) {
            return bb.position();
        }

        return -1;
    }

    public boolean isWriterReferenced() {
        return writer != null;
    }

    public Writer getCountingWriter() {
        if (writer == null) {
            ce = Charset.forName(encoding).newEncoder().onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            bb = ByteBuffer.allocate(capacity);
            writer = new BoundedCharsAsEncodedBytesCounterWriter();
        }
        return writer;
    }

    class BoundedCharsAsEncodedBytesCounterWriter extends Writer {
        char[] writeBuffer = new char[8192];

        @Override
        public void write(char[] b, int off, int len) throws IOException {
            update(b, off, len);
        }

        @Override
        public void close() throws IOException {
            // do nothing
        }

        @Override
        public void write(int b) throws IOException {
            if (!calculationActive) return;
            writeBuffer[0] = (char) b;
            update(writeBuffer, 0, 1);
        }

        @Override
        public Writer append(CharSequence csq, int start, int end) throws IOException {
            if (!calculationActive) return this;

            if (csq instanceof StringBuilder || csq instanceof StringBuffer) {
                int len = end - start;
                char cbuf[];
                if (len <= writeBuffer.length) {
                    cbuf = writeBuffer;
                }
                else {
                    cbuf = new char[len];
                }
                if (csq instanceof StringBuilder) {
                    ((StringBuilder) csq).getChars(start, end, cbuf, 0);
                }
                else {
                    ((StringBuffer) csq).getChars(start, end, cbuf, 0);
                }
                write(cbuf, 0, len);
            }
            else {
                write(csq.subSequence(start, end).toString());
            }
            return this;
        }

        @Override
        public Writer append(CharSequence csq) throws IOException {
            if (!calculationActive) return this;

            if (csq == null) {
                write("null");
            }
            else {
                append(csq, 0, csq.length());
            }
            return this;
        }

        @Override
        public void write(String str, int off, int len) throws IOException {
            if (!calculationActive) return;
            StringCharArrayAccessor.writeStringAsCharArray(this, str, off, len);
        }

        @Override
        public void write(String str) throws IOException {
            if (!calculationActive) return;
            StringCharArrayAccessor.writeStringAsCharArray(this, str);
        }

        @Override
        public void flush() throws IOException {
            // do nothing
        }
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}
