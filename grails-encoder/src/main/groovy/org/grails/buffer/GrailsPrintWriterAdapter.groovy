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
package org.grails.buffer

import groovy.transform.CompileStatic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.objenesis.ObjenesisStd
import org.springframework.objenesis.instantiator.ObjectInstantiator

/**
 * @author Lari Hotari
 * @since 2.0
 */
@CompileStatic
class GrailsPrintWriterAdapter extends PrintWriter implements GrailsWrappedWriter {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsPrintWriterAdapter)
    protected GrailsPrintWriter target

    private static ObjectInstantiator instantiator

    static {
        try {
            instantiator = new ObjenesisStd(false).getInstantiatorOf(GrailsPrintWriterAdapter)
        } catch (Exception e) {
            LOG.debug("Couldn't get direct performance optimized instantiator for GrailsPrintWriterAdapter. Using default instantiation.", e)
        }
    }

    GrailsPrintWriterAdapter(Writer wrapped) {
        super(new Writer() {
            @Override
            void write(char[] cbuf, int off, int len) throws IOException {
                // no-op
            }

            @Override
            void flush() throws IOException {
                // no-op
            }

            @Override
            void close() throws IOException {
                // no-op
            }
        })
        setTarget(wrapped)
    }

    static GrailsPrintWriterAdapter newInstance(Writer wrapped) {
        if (instantiator != null) {
            GrailsPrintWriterAdapter instance = (GrailsPrintWriterAdapter) instantiator.newInstance()
            instance.setTarget(wrapped)
            return instance
        }
        return new GrailsPrintWriterAdapter(wrapped)
    }

    void setTarget(Writer wrapped) {
        if (wrapped instanceof GrailsPrintWriter) {
            this.target = ((GrailsPrintWriter) wrapped)
        }
        else {
            this.target = new GrailsPrintWriter(wrapped)
        }
        this.@out = this.target
        this.@lock = this.@out != null ? this.@out : this
    }

    boolean isAllowUnwrappingOut() {
        return true
    }

    GrailsPrintWriter getTarget() {
        return target
    }

    Writer getOut() {
        return target.getOut()
    }

    Writer unwrap() {
        return target.unwrap()
    }

    GrailsPrintWriter leftShift(Object value) throws IOException {
        return target.leftShift(value)
    }

    GrailsPrintWriter plus(Object value) throws IOException {
        return target.plus(value)
    }

    @Override
    boolean checkError() {
        return target.checkError()
    }

    @Override
    void setError() {
        target.setError()
    }

    @Override
    void flush() {
        target.flush()
    }

    @Override
    void print(Object obj) {
        target.print(obj)
    }

    @Override
    void print(String s) {
        target.print(s)
    }

    @Override
    void write(String s) {
        target.write(s)
    }

    @Override
    void write(int c) {
        target.write(c)
    }

    @Override
    void write(char[] buf, int off, int len) {
        target.write(buf, off, len)
    }

    @Override
    void write(String s, int off, int len) {
        target.write(s, off, len)
    }

    @Override
    void write(char[] buf) {
        target.write(buf)
    }

    @Override
    void print(boolean b) {
        target.print(b)
    }

    @Override
    void print(char c) {
        target.print(c)
    }

    @Override
    void print(int i) {
        target.print(i)
    }

    @Override
    void print(long l) {
        target.print(l)
    }

    @Override
    void print(float f) {
        target.print(f)
    }

    @Override
    void print(double d) {
        target.print(d)
    }

    @Override
    void print(char[] s) {
        target.print(s)
    }

    @Override
    void println() {
        target.println()
    }

    @Override
    void println(boolean b) {
        target.println(b)
    }

    @Override
    void println(char c) {
        target.println(c)
    }

    @Override
    void println(int i) {
        target.println(i)
    }

    @Override
    void println(long l) {
        target.println(l)
    }

    @Override
    void println(float f) {
        target.println(f)
    }

    @Override
    void println(double d) {
        target.println(d)
    }

    @Override
    void println(char[] c) {
        target.println(c)
    }

    @Override
    void println(String s) {
        target.println(s)
    }

    @Override
    void println(Object o) {
        target.println(o)
    }

    @Override
    PrintWriter append(char c) {
        target.append(c)
        return this
    }

    @Override
    PrintWriter append(CharSequence csq, int start, int end) {
        target.append(csq, start, end)
        return this
    }

    @Override
    PrintWriter append(CharSequence csq) {
        target.append(csq)
        return this
    }

    PrintWriter append(Object obj) {
        target.append(obj)
        return this
    }

    void write(StreamCharBuffer otherBuffer) {
        target.write(otherBuffer)
    }

    void print(StreamCharBuffer otherBuffer) {
        target.print(otherBuffer)
    }

    void append(StreamCharBuffer otherBuffer) {
        target.append(otherBuffer)
    }

    void println(StreamCharBuffer otherBuffer) {
        target.println(otherBuffer)
    }

    GrailsPrintWriter leftShift(StreamCharBuffer otherBuffer) {
        return target.leftShift(otherBuffer)
    }

    void write(Writable writable) {
        target.write(writable)
    }

    void print(Writable writable) {
        target.print(writable)
    }

    GrailsPrintWriter leftShift(Writable writable) {
        return target.leftShift(writable)
    }

    boolean isUsed() {
        return target.isUsed()
    }

    void setUsed(boolean newUsed) {
        target.setUsed(newUsed)
    }

    boolean resetUsed() {
        return target.resetUsed()
    }

    @Override
    void close() {
        target.close()
    }

    void markUsed() {
        target.markUsed()
    }

    protected boolean isTrouble() {
        return target.isTrouble()
    }

    protected void handleIOException(IOException e) {
        target.handleIOException(e)
    }
}
