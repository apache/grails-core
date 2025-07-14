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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.objenesis.instantiator.ObjectInstantiator;

import java.io.Reader;

/**
 * Fast in-memory PrintWriter implementation.
 *
 * @author Lari Hotari
 * @since 2.0
 */
public class FastStringPrintWriter extends GrailsPrintWriterAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(FastStringPrintWriter.class);

    private static ObjectInstantiator instantiator;

    static {
        try {
            instantiator = new ObjenesisStd(false).getInstantiatorOf(FastStringPrintWriter.class);
        } catch (Exception e) {
            LOG.debug("Couldn't get direct performance optimized instantiator for FastStringPrintWriter. Using default instantiation.", e);
        }
    }

    private StreamCharBuffer streamBuffer;

    public FastStringPrintWriter() {
        super(new StreamCharBuffer().getWriter());
        streamBuffer = ((StreamCharBuffer.StreamCharBufferWriter) getOut()).getBuffer();
    }

    public FastStringPrintWriter(int initialChunkSize) {
        super(new StreamCharBuffer(initialChunkSize).getWriter());
        streamBuffer = ((StreamCharBuffer.StreamCharBufferWriter) getOut()).getBuffer();
    }

    protected FastStringPrintWriter(Object o) {
        this();
        print(o);
    }

    public static FastStringPrintWriter newInstance() {
        return newInstance(0);
    }

    public static FastStringPrintWriter newInstance(int initialChunkSize) {
        if (instantiator == null) {
            if (initialChunkSize > 0) {
                return new FastStringPrintWriter(initialChunkSize);
            }
            return new FastStringPrintWriter();
        }

        FastStringPrintWriter instance = (FastStringPrintWriter) instantiator.newInstance();
        if (initialChunkSize > 0) {
            instance.streamBuffer = new StreamCharBuffer(initialChunkSize);
        } else {
            instance.streamBuffer = new StreamCharBuffer();
        }
        instance.setTarget(instance.streamBuffer.getWriter());
        return instance;
    }

    public StreamCharBuffer getBuffer() {
        return streamBuffer;
    }

    @Override
    public String toString() {
        return getValue();
    }

    public String getValue() {
        return streamBuffer.toString();
    }

    public Reader getReader() {
        return streamBuffer.getReader();
    }
}
