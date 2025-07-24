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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import groovy.lang.Writable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.objenesis.ObjenesisStd;
import org.springframework.objenesis.instantiator.ObjectInstantiator;

/**
 * @author Lari Hotari
 * @since 2.0
 */
public class GrailsPrintWriterAdapter extends PrintWriter implements GrailsWrappedWriter {
    private static final Logger LOG = LoggerFactory.getLogger(GrailsPrintWriterAdapter.class);
    protected GrailsPrintWriter target;

    private static ObjectInstantiator instantiator;
    static {
        try {
            instantiator = new ObjenesisStd(false).getInstantiatorOf(GrailsPrintWriterAdapter.class);
        } catch (Exception e) {
            LOG.debug("Couldn't get direct performance optimized instantiator for GrailsPrintWriterAdapter. Using default instantiation.", e);
        }
    }

    public GrailsPrintWriterAdapter(Writer wrapped) {
        super(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                // no-op
            }

            @Override
            public void flush() throws IOException {
                // no-op
            }

            @Override
            public void close() throws IOException {
                // no-op
            }
        });
        setTarget(wrapped);
    }

    public static GrailsPrintWriterAdapter newInstance(Writer wrapped) {
        if (instantiator != null) {
            GrailsPrintWriterAdapter instance = (GrailsPrintWriterAdapter)instantiator.newInstance();
            instance.setTarget(wrapped);
            return instance;
        }
        return new GrailsPrintWriterAdapter(wrapped);
    }

    public void setTarget(Writer wrapped) {
        if (wrapped instanceof GrailsPrintWriter) {
            this.target = ((GrailsPrintWriter)wrapped);
        }
        else {
            this.target = new GrailsPrintWriter(wrapped);
        }
        this.out = this.target;
        this.lock = this.out != null ? this.out : this;
    }

    public boolean isAllowUnwrappingOut() {
        return true;
    }

    public GrailsPrintWriter getTarget() {
        return target;
    }

    public Writer getOut() {
        return target.getOut();
    }

    public Writer unwrap() {
        return target.unwrap();
    }

    public GrailsPrintWriter leftShift(Object value) throws IOException {
        return target.leftShift(value);
    }

    public GrailsPrintWriter plus(Object value) throws IOException {
        return target.plus(value);
    }

    @Override
    public boolean checkError() {
        return target.checkError();
    }

    @Override
    public void setError() {
        target.setError();
    }

    @Override
    public void flush() {
        target.flush();
    }

    @Override
    public void print(Object obj) {
        target.print(obj);
    }

    @Override
    public void print(String s) {
        target.print(s);
    }

    @Override
    public void write(String s) {
        target.write(s);
    }

    @Override
    public void write(int c) {
        target.write(c);
    }

    @Override
    public void write(char[] buf, int off, int len) {
        target.write(buf, off, len);
    }

    @Override
    public void write(String s, int off, int len) {
        target.write(s, off, len);
    }

    @Override
    public void write(char[] buf) {
        target.write(buf);
    }

    @Override
    public void print(boolean b) {
        target.print(b);
    }

    @Override
    public void print(char c) {
        target.print(c);
    }

    @Override
    public void print(int i) {
        target.print(i);
    }

    @Override
    public void print(long l) {
        target.print(l);
    }

    @Override
    public void print(float f) {
        target.print(f);
    }

    @Override
    public void print(double d) {
        target.print(d);
    }

    @Override
    public void print(char[] s) {
        target.print(s);
    }

    @Override
    public void println() {
        target.println();
    }

    @Override
    public void println(boolean b) {
        target.println(b);
    }

    @Override
    public void println(char c) {
        target.println(c);
    }

    @Override
    public void println(int i) {
        target.println(i);
    }

    @Override
    public void println(long l) {
        target.println(l);
    }

    @Override
    public void println(float f) {
        target.println(f);
    }

    @Override
    public void println(double d) {
        target.println(d);
    }

    @Override
    public void println(char[] c) {
        target.println(c);
    }

    @Override
    public void println(String s) {
        target.println(s);
    }

    @Override
    public void println(Object o) {
        target.println(o);
    }

    @Override
    public PrintWriter append(char c) {
        target.append(c);
        return this;
    }

    @Override
    public PrintWriter append(CharSequence csq, int start, int end) {
        target.append(csq, start, end);
        return this;
    }

    @Override
    public PrintWriter append(CharSequence csq) {
        target.append(csq);
        return this;
    }

    public PrintWriter append(Object obj) {
        target.append(obj);
        return this;
    }

    public void write(StreamCharBuffer otherBuffer) {
        target.write(otherBuffer);
    }

    public void print(StreamCharBuffer otherBuffer) {
        target.print(otherBuffer);
    }

    public void append(StreamCharBuffer otherBuffer) {
        target.append(otherBuffer);
    }

    public void println(StreamCharBuffer otherBuffer) {
        target.println(otherBuffer);
    }

    public GrailsPrintWriter leftShift(StreamCharBuffer otherBuffer) {
        return target.leftShift(otherBuffer);
    }

    public void write(Writable writable) {
        target.write(writable);
    }

    public void print(Writable writable) {
        target.print(writable);
    }

    public GrailsPrintWriter leftShift(Writable writable) {
        return target.leftShift(writable);
    }

    public boolean isUsed() {
        return target.isUsed();
    }

    public void setUsed(boolean newUsed) {
        target.setUsed(newUsed);
    }

    public boolean resetUsed() {
        return target.resetUsed();
    }

    @Override
    public void close() {
        target.close();
    }

    public void markUsed() {
        target.markUsed();
    }

    protected boolean isTrouble() {
        return target.isTrouble();
    }

    protected void handleIOException(IOException e) {
        target.handleIOException(e);
    }
}
