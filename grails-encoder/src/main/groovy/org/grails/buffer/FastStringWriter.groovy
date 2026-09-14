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

/**
 * Java's default StringWriter uses a StringBuffer which is synchronized. This
 * implementation doesn't use synchronization
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @since 1.1
 */
@CompileStatic
class FastStringWriter extends GrailsPrintWriter {

    protected final StreamCharBuffer streamBuffer

    FastStringWriter() {
        super(null)
        streamBuffer = new StreamCharBuffer()
        initOut()
    }

    FastStringWriter(int initialChunkSize) {
        super(null)
        streamBuffer = new StreamCharBuffer(initialChunkSize)
        initOut()
    }

    FastStringWriter(Object o) {
        this()
        print(o)
    }

    protected void initOut() {
        setOut(streamBuffer.getWriter())
    }

    StreamCharBuffer getBuffer() {
        return streamBuffer
    }

    @Override
    String toString() {
        return getValue()
    }

    String getValue() {
        return streamBuffer.toString()
    }

    Reader getReader() {
        return streamBuffer.getReader()
    }
}
