/*
 * Copyright 2004-2005 Graeme Rocher
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
package org.grails.buffer;

import java.io.Reader;

/**
 * Java's default StringWriter uses a StringBuffer which is synchronized. This
 * implementation doesn't use synchronization
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @since 1.1
 */
public class FastStringWriter extends GrailsPrintWriter {

    protected final StreamCharBuffer streamBuffer;

    public FastStringWriter() {
        super(null);
        streamBuffer = new StreamCharBuffer();
        initOut();
    }

    public FastStringWriter(int initialChunkSize) {
        super(null);
        streamBuffer = new StreamCharBuffer(initialChunkSize);
        initOut();
    }

    public FastStringWriter(Object o) {
        this();
        print(o);
    }

    protected void initOut() {
        setOut(streamBuffer.getWriter());
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
