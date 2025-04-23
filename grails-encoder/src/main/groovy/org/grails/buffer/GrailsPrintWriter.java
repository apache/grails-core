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
package org.grails.buffer;

import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.lang.Writable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.charsequences.CharSequences;
import org.grails.encoder.EncodedAppender;
import org.grails.encoder.EncodedAppenderFactory;
import org.grails.encoder.EncodedAppenderWriter;
import org.grails.encoder.EncodedAppenderWriterFactory;
import org.grails.encoder.Encoder;
import org.grails.encoder.EncodingStateRegistry;
import org.grails.encoder.StreamingEncoder;
import org.grails.encoder.StreamingEncoderWriter;
import org.codehaus.groovy.runtime.GStringImpl;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

/**
 * PrintWriter implementation that doesn't have synchronization. null object
 * references are ignored in print methods (nothing gets printed)
 *
 * @author Lari Hotari, Sagire Software Oy
 */
public class GrailsPrintWriter extends Writer implements GrailsWrappedWriter, EncodedAppenderWriterFactory, GroovyObject {
    protected static final Log LOG = LogFactory.getLog(GrailsPrintWriter.class);
    protected static final char CRLF[] = { '\r', '\n' };
    protected boolean trouble = false;
    protected Writer out;
    protected boolean allowUnwrappingOut = true;
    protected boolean usageFlag = false;
    protected Writer streamCharBufferTarget = null;
    protected Writer previousOut = null;

    public GrailsPrintWriter(Writer out) {
        this.metaClass = InvokerHelper.getMetaClass(this.getClass());
        setOut(out);
    }

    public boolean isAllowUnwrappingOut() {
        return allowUnwrappingOut;
    }

    public Writer unwrap() {
        if (isAllowUnwrappingOut()) {
            return getOut();
        }
        return this;
    }

    public boolean isDestinationActivated() {
        return out != null;
    }

    public Writer getOut() {
        return out;
    }

    public void setOut(Writer newOut) {
        this.out = unwrapWriter(newOut);
        this.lock = this.out != null ? this.out : this;
        this.streamCharBufferTarget = null;
        this.previousOut = null;
    }

    protected Writer unwrapWriter(Writer writer) {
        if (writer instanceof GrailsWrappedWriter ) {
            return ((GrailsWrappedWriter)writer).unwrap();
        }
        return writer;
    }

    /**
     * Provides Groovy &lt;&lt; left shift operator, but intercepts call to make sure
     * nulls are converted to "" strings
     *
     * @param obj The value
     * @return Returns this object
     * @throws IOException
     */
    public GrailsPrintWriter leftShift(Object obj) throws IOException {
        if (trouble || obj == null) {
            usageFlag = true;
            return this;
        }

        Class<?> clazz = obj.getClass();
        if (clazz == String.class) {
            write((String)obj);
        }
        else if (clazz == StreamCharBuffer.class) {
            write((StreamCharBuffer)obj);
        }
        else if (clazz == GStringImpl.class) {
            write((Writable)obj);
        }
        else if (obj instanceof Writable) {
            write((Writable)obj);
        }
        else if (obj instanceof CharSequence) {
            try {
                usageFlag = true;
                CharSequences.writeCharSequence(getOut(), (CharSequence) obj);
            }
            catch (IOException e) {
                handleIOException(e);
            }
        }
        else {        
            InvokerHelper.write(this, obj);
        }
        return this;
    }

    public GrailsPrintWriter plus(Object value) throws IOException {
        usageFlag = true;
        return leftShift(value);
    }

    /**
     * Flush the stream if it's not closed and check its error state. Errors are
     * cumulative; once the stream encounters an error, this routine will return
     * true on all successive calls.
     *
     * @return true if the print stream has encountered an error, either on the
     *         underlying output stream or during a format conversion.
     */
    public boolean checkError() {
        return trouble;
    }

    public void setError() {
        trouble = true;
    }

    /**
     * Flush the stream.
     *
     * @see #checkError()
     */
    @Override
    public synchronized void flush() {
        if (trouble) {
            return;
        }

        if (isDestinationActivated()) {
            try {
                getOut().flush();
            }
            catch (IOException e) {
                handleIOException(e);
            }
        }
    }

    boolean isTrouble() {
        return trouble;
    }

    void handleIOException(IOException e) {
        if (trouble) {
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("I/O exception in GrailsPrintWriter: " + e.getMessage(), e);
        }
        trouble = true;
        setError();
    }

    /**
     * Print an object. The string produced by the <code>{@link
     * java.lang.String#valueOf(Object)}</code> method is translated into bytes
     * according to the platform's default character encoding, and these bytes
     * are written in exactly the manner of the <code>{@link #write(int)}</code>
     * method.
     *
     * @param obj The <code>Object</code> to be printed
     * @see java.lang.Object#toString()
     */
    public void print(final Object obj) {
        if (trouble || obj == null) {
            usageFlag = true;
            return;
        }

        Class<?> clazz = obj.getClass();
        if (clazz == String.class) {
            write((String)obj);
        }
        else if (clazz == StreamCharBuffer.class) {
            write((StreamCharBuffer)obj);
        }
        else if (clazz == GStringImpl.class) {
            write((Writable)obj);
        }
        else if (obj instanceof Writable) {
            write((Writable)obj);
        }
        else if (obj instanceof CharSequence) {
            try {
                usageFlag = true;
                CharSequences.writeCharSequence(getOut(), (CharSequence)obj);
            }
            catch (IOException e) {
                handleIOException(e);
            }
        }
        else {
            write(String.valueOf(obj));
        }
    }

    /**
     * Print a string. If the argument is <code>null</code> then the string
     * <code>""</code> is printed. Otherwise, the string's characters are
     * converted into bytes according to the platform's default character
     * encoding, and these bytes are written in exactly the manner of the
     * <code>{@link #write(int)}</code> method.
     *
     * @param s The <code>String</code> to be printed
     */
    public void print(final String s) {
        if (s == null) {
            usageFlag = true;
            return;
        }
        write(s);
    }

    /**
     * Writes a string. If the argument is <code>null</code> then the string
     * <code>""</code> is printed.
     *
     * @param s The <code>String</code> to be printed
     */
    @Override
    public void write(final String s) {
        usageFlag = true;
        if (trouble || s == null) {
            return;
        }

        try {
            getOut().write(s);
        }
        catch (IOException e) {
            handleIOException(e);
        }
    }

    /**
     * Write a single character.
     *
     * @param c int specifying a character to be written.
     */
    @Override
    public void write(final int c) {
        usageFlag = true;
        if (trouble)
            return;

        try {
            getOut().write(c);
        }
        catch (IOException e) {
            handleIOException(e);
        }
    }

    /**
     * Write a portion of an array of characters.
     *
     * @param buf Array of characters
     * @param off Offset from which to start writing characters
     * @param len Number of characters to write
     */
    @Override
    public void write(final char buf[], final int off, final int len) {
        usageFlag = true;
        if (trouble || buf == null || len == 0)
            return;
        try {
            getOut().write(buf, off, len);
        }
        catch (IOException e) {
            handleIOException(e);
        }
    }

    /**
     * Write a portion of a string.
     *
     * @param s A String
     * @param off Offset from which to start writing characters
     * @param len Number of characters to write
     */
    @Override
    public void write(final String s, final int off, final int len) {
        usageFlag = true;
        if (trouble || s == null || s.length() == 0)
            return;

        try {
            getOut().write(s, off, len);
        }
        catch (IOException e) {
            handleIOException(e);
        }
    }

    @Override
    public void write(final char buf[]) {
        write(buf, 0, buf.length);
    }

    /** delegate methods, not synchronized **/

    public void print(final boolean b) {
        if (b) {
            write("true");
        }
        else {
            write("false");
        }
    }

    public void print(final char c) {
        write(c);
    }

    public void print(final int i) {
        write(String.valueOf(i));
    }

    public void print(final long l) {
        write(String.valueOf(l));
    }

    public void print(final float f) {
        write(String.valueOf(f));
    }

    public void print(final double d) {
        write(String.valueOf(d));
    }

    public void print(final char s[]) {
        write(s);
    }

    public void println() {
        usageFlag = true;
        write(CRLF);
    }

    public void println(final boolean b) {
        print(b);
        println();
    }

    public void println(final char c) {
        print(c);
        println();
    }

    public void println(final int i) {
        print(i);
        println();
    }

    public void println(final long l) {
        print(l);
        println();
    }

    public void println(final float f) {
        print(f);
        println();
    }

    public void println(final double d) {
        print(d);
        println();
    }

    public void println(final char c[]) {
        print(c);
        println();
    }

    public void println(final String s) {
        print(s);
        println();
    }

    public void println(final Object o) {
        print(o);
        println();
    }

    @Override
    public GrailsPrintWriter append(final char c) {
        try {
            usageFlag = true;
            getOut().append(c);
        }
        catch (IOException e) {
            handleIOException(e);
        }
        return this;
    }

    @Override
    public GrailsPrintWriter append(final CharSequence csq, final int start, final int end) {
        try {
            usageFlag = true;
            if (csq == null)
                appendNullCharSequence();
            else
                CharSequences.writeCharSequence(getOut(), csq, start, end);
        }
        catch (IOException e) {
            handleIOException(e);
        }
        return this;
    }

    protected void appendNullCharSequence() throws IOException {
        getOut().append(null);
    }

    @Override
    public GrailsPrintWriter append(final CharSequence csq) {
        try {
            usageFlag = true;
            if (csq == null)
                appendNullCharSequence();
            else
                CharSequences.writeCharSequence(getOut(), csq);
        }
        catch (IOException e) {
            handleIOException(e);
        }
        return this;
    }

    public GrailsPrintWriter append(final Object obj) {
        print(obj);
        return this;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public void write(final StreamCharBuffer otherBuffer) {
        usageFlag = true;
        if (trouble)
            return;

        try {
            otherBuffer.writeTo(findStreamCharBufferTarget(true));
        }
        catch (IOException e) {
            handleIOException(e);
        }
    }

    protected Writer findStreamCharBufferTarget(boolean markUsed) {
        boolean allowCaching = markUsed;

        Writer currentOut = getOut();
        if (allowCaching && streamCharBufferTarget != null && previousOut == currentOut) {
            return streamCharBufferTarget;
        }

        Writer target = currentOut;
        while (target instanceof GrailsWrappedWriter) {
            GrailsWrappedWriter gpr = ((GrailsWrappedWriter)target);
            if (gpr.isAllowUnwrappingOut()) {
                if (markUsed) {
                    gpr.markUsed();
                }
                target = gpr.unwrap();
            }
            else {
                break;
            }
        }

        Writer result;
        if (target instanceof StreamCharBuffer.StreamCharBufferWriter) {
            result = target;
        }
        else {
            result = currentOut;
        }

        if (allowCaching) {
            streamCharBufferTarget = result;
            previousOut = currentOut;
        }

        return result;
    }

    public void print(final StreamCharBuffer otherBuffer) {
        write(otherBuffer);
    }

    public void append(final StreamCharBuffer otherBuffer) {
        write(otherBuffer);
    }

    public void println(final StreamCharBuffer otherBuffer) {
        write(otherBuffer);
        println();
    }

    public GrailsPrintWriter leftShift(final StreamCharBuffer otherBuffer) {
        if (otherBuffer != null) {
            write(otherBuffer);
        }
        return this;
    }

    public void write(final Writable writable) {
        writeWritable(writable);
    }

    protected void writeWritable(final Writable writable) {
        if(writable.getClass() == StreamCharBuffer.class) {
            write((StreamCharBuffer)writable);
            return;
        }
        
        usageFlag = true;
        if (trouble)
            return;

        try {
            writable.writeTo(getOut());
        }
        catch (IOException e) {
            handleIOException(e);
        }
    }

    public void print(final Writable writable) {
        writeWritable(writable);
    }

    public GrailsPrintWriter leftShift(final Writable writable) {
        writeWritable(writable);
        return this;
    }

    public void print(final GStringImpl gstring) {
        writeWritable(gstring);
    }

    public GrailsPrintWriter leftShift(final GStringImpl gstring) {
        writeWritable(gstring);
        return this;
    }

    public GrailsPrintWriter leftShift(final String string) {
        print(string);
        return this;
    }

    public boolean isUsed() {
        if (usageFlag) {
            return true;
        }

        Writer target = findStreamCharBufferTarget(false);
        if (target instanceof StreamCharBuffer.StreamCharBufferWriter) {
            StreamCharBuffer buffer = ((StreamCharBuffer.StreamCharBufferWriter)target).getBuffer();
            if (!buffer.isEmpty()) {
                return true;
            }
        }
        return usageFlag;
    }

    public void setUsed(boolean newUsed) {
        usageFlag = newUsed;
    }

    public boolean resetUsed() {
        boolean prevUsed = usageFlag;
        usageFlag = false;
        return prevUsed;
    }

    @Override
    public void close() {
        if (isDestinationActivated()) {
            try {
                getOut().close();
            }
            catch (IOException e) {
                handleIOException(e);
            }
        }
    }

    public void markUsed() {
        setUsed(true);
    }

    public Object asType(Class<?> clazz) {
        if (clazz == PrintWriter.class) {
            return asPrintWriter();
        }
        if (clazz == Writer.class) {
            return this;
        }
        return DefaultTypeTransformation.castToType(this, clazz);
    }

    public PrintWriter asPrintWriter() {
        return GrailsPrintWriterAdapter.newInstance(this);
    }

    public Writer getWriterForEncoder(Encoder encoder, EncodingStateRegistry encodingStateRegistry) {
        Writer target = null;
        if (getOut() instanceof EncodedAppenderWriterFactory && getOut() != this) {
            target = getOut();
        } else {
            target = findStreamCharBufferTarget(false);
        }
        if (target instanceof EncodedAppenderWriterFactory && target != this) {
            return ((EncodedAppenderWriterFactory)target).getWriterForEncoder(encoder, encodingStateRegistry);
        } else if (target instanceof EncodedAppenderFactory) {
            EncodedAppender encodedAppender=((EncodedAppenderFactory)target).getEncodedAppender();
            if (encodedAppender != null) {
                return new EncodedAppenderWriter(encodedAppender, encoder, encodingStateRegistry);
            }
        }
        if (target != null) {
            if (encoder instanceof StreamingEncoder) {
                return new StreamingEncoderWriter(target, (StreamingEncoder)encoder, encodingStateRegistry);
            } else {
                return new CodecPrintWriter(target, encoder, encodingStateRegistry);
            }
        } else {
            return null;
        }
    }

    // GroovyObject interface implementation to speed up metaclass operations
    private transient MetaClass metaClass;

    public Object getProperty(String property) {
        return getMetaClass().getProperty(this, property);
    }

    public void setProperty(String property, Object newValue) {
        getMetaClass().setProperty(this, property, newValue);
    }

    public Object invokeMethod(String name, Object args) {
        return getMetaClass().invokeMethod(this, name, args);
    }

    public MetaClass getMetaClass() {
        if (metaClass == null) {
            metaClass = InvokerHelper.getMetaClass(getClass());
        }
        return metaClass;
    }

    public void setMetaClass(MetaClass metaClass) {
        this.metaClass = metaClass;
    }
}
