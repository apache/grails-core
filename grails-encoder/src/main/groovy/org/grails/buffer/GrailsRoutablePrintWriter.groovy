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

@CompileStatic
class GrailsRoutablePrintWriter extends GrailsPrintWriterAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsRoutablePrintWriter)
    private DestinationFactory factory
    private boolean blockFlush = true
    private boolean blockClose = true
    private boolean destinationActivated = false
    private static ObjectInstantiator instantiator = null

    static {
        try {
            instantiator = new ObjenesisStd(false).getInstantiatorOf(GrailsRoutablePrintWriter)
        } catch (Exception e) {
            LOG.debug("Couldn't get direct performance optimized instantiator for GrailsRoutablePrintWriter. Using default instantiation.", e)
        }
    }

    /**
     * Factory to lazily instantiate the destination.
     */
    static interface DestinationFactory {
        Writer activateDestination() throws IOException
    }

    GrailsRoutablePrintWriter(DestinationFactory factory) {
        super(new NullWriter())
        this.factory = factory
    }

    static GrailsRoutablePrintWriter newInstance(DestinationFactory factory) {
        if (instantiator != null) {
            GrailsRoutablePrintWriter instance = (GrailsRoutablePrintWriter) instantiator.newInstance()
            instance.@out = new NullWriter()
            instance.@factory = factory
            instance.@blockFlush = true
            instance.@blockClose = true
            return instance
        } else {
            return new GrailsRoutablePrintWriter(factory)
        }
    }

    protected void activateDestination() {
        if (!destinationActivated && factory != null) {
            try {
                super.setTarget(factory.activateDestination())
            }
            catch (IOException e) {
                setError()
            }
            destinationActivated = true
        }
    }

    @Override
    boolean isAllowUnwrappingOut() {
        return destinationActivated ? super.isAllowUnwrappingOut() : false
    }

    @Override
    Writer unwrap() {
        return destinationActivated ? super.unwrap() : this
    }

    void updateDestination(DestinationFactory f) {
        setDestinationActivated(false)
        this.factory = f
    }

    @Override
    void close() {
        if (!isBlockClose() && isDestinationActivated()) {
            super.close()
        }
    }

    @Override
    void println(Object x) {
        activateDestination()
        super.println(x)
    }

    @Override
    void println(String x) {
        activateDestination()
        super.println(x)
    }

    @Override
    void println(char[] x) {
        activateDestination()
        super.println(x)
    }

    @Override
    void println(double x) {
        activateDestination()
        super.println(x)
    }

    @Override
    void println(float x) {
        activateDestination()
        super.println(x)
    }

    @Override
    void println(long x) {
        activateDestination()
        super.println(x)
    }

    @Override
    void println(int x) {
        activateDestination()
        super.println(x)
    }

    @Override
    void println(char x) {
        activateDestination()
        super.println(x)
    }

    @Override
    void println(boolean x) {
        activateDestination()
        super.println(x)
    }

    @Override
    void println() {
        activateDestination()
        super.println()
    }

    @Override
    void print(Object obj) {
        activateDestination()
        super.print(obj)
    }

    @Override
    void print(String s) {
        activateDestination()
        super.print(s)
    }

    @Override
    void print(char[] s) {
        activateDestination()
        super.print(s)
    }

    @Override
    void print(double d) {
        activateDestination()
        super.print(d)
    }

    @Override
    void print(float f) {
        activateDestination()
        super.print(f)
    }

    @Override
    void print(long l) {
        activateDestination()
        super.print(l)
    }

    @Override
    void print(int i) {
        activateDestination()
        super.print(i)
    }

    @Override
    void print(char c) {
        activateDestination()
        super.print(c)
    }

    @Override
    void print(boolean b) {
        activateDestination()
        super.print(b)
    }

    @Override
    void write(String s) {
        activateDestination()
        super.write(s)
    }

    @Override
    void write(String s, int off, int len) {
        activateDestination()
        super.write(s, off, len)
    }

    @Override
    void write(char[] buf) {
        activateDestination()
        super.write(buf)
    }

    @Override
    void write(char[] buf, int off, int len) {
        activateDestination()
        super.write(buf, off, len)
    }

    @Override
    void write(int c) {
        activateDestination()
        super.write(c)
    }

    @Override
    boolean checkError() {
        activateDestination()
        return super.checkError()
    }

    @Override
    void flush() {
        if (!isBlockFlush() && isDestinationActivated()) {
            super.flush()
        }
    }

    @Override
    PrintWriter append(char c) {
        activateDestination()
        return super.append(c)
    }

    @Override
    PrintWriter append(CharSequence csq, int start, int end) {
        activateDestination()
        return super.append(csq, start, end)
    }

    @Override
    PrintWriter append(CharSequence csq) {
        activateDestination()
        return super.append(csq)
    }

    /**
     * Just to keep super constructor for PrintWriter happy - it's never
     * actually used.
     */
    private static class NullWriter extends Writer {
        protected NullWriter() {
            super()
        }

        @Override
        void write(char[] cbuf, int off, int len) throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        void flush() throws IOException {
            throw new UnsupportedOperationException()
        }

        @Override
        void close() throws IOException {
            throw new UnsupportedOperationException()
        }
    }

    boolean isBlockFlush() {
        return blockFlush
    }

    void setBlockFlush(boolean blockFlush) {
        this.blockFlush = blockFlush
    }

    boolean isBlockClose() {
        return blockClose
    }

    void setBlockClose(boolean blockClose) {
        this.blockClose = blockClose
    }

    void unBlockFlushAndClose() {
        this.blockClose = false
        this.blockFlush = false
    }

    void blockFlushAndClose() {
        this.blockClose = true
        this.blockFlush = true
    }

    @Override
    GrailsPrintWriter leftShift(Object value) throws IOException {
        activateDestination()
        return super.leftShift(value)
    }

    @Override
    GrailsPrintWriter leftShift(StreamCharBuffer otherBuffer) {
        activateDestination()
        return super.leftShift(otherBuffer)
    }

    @Override
    GrailsPrintWriter leftShift(Writable writable) {
        activateDestination()
        return super.leftShift(writable)
    }

    boolean isDestinationActivated() {
        return destinationActivated
    }

    void setDestinationActivated(boolean destinationActivated) {
        this.destinationActivated = destinationActivated
        if (!this.destinationActivated) {
            super.setTarget(new NullWriter())
        }
    }
}
