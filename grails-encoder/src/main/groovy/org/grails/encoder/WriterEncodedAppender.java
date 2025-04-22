/*
 * Copyright 2013 the original author or authors.
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
package org.grails.encoder;

import org.grails.charsequences.CharSequences;

import java.io.IOException;
import java.io.Writer;

/**
 * An EncodedAppender implementation that writes to a java.io.Writer.
 *
 * @author Lari Hotari
 * @since 2.3
 */
public class WriterEncodedAppender extends AbstractEncodedAppender {
    private Writer target;

    /**
     * Default constructor.
     *
     * @param target the target writer
     */
    public WriterEncodedAppender(Writer target) {
        this.target = target;
    }

    /* (non-Javadoc)
     * @see AbstractEncodedAppender#flush()
     */
    @Override
    public void flush() throws IOException {
        target.flush();
    }

    /* (non-Javadoc)
     * @see AbstractEncodedAppender#write(EncodingState, char[], int, int)
     */
    @Override
    protected void write(EncodingState encodingState, char[] b, int off, int len) throws IOException {
        target.write(b, off, len);
    }

    /* (non-Javadoc)
     * @see AbstractEncodedAppender#write(EncodingState, java.lang.String, int, int)
     */
    @Override
    protected void write(EncodingState encodingState, String str, int off, int len) throws IOException {
        target.write(str, off, len);
    }

    /* (non-Javadoc)
     * @see AbstractEncodedAppender#appendCharSequence(EncodingState, java.lang.CharSequence, int, int)
     */
    @Override
    protected void appendCharSequence(EncodingState encodingState, CharSequence csq, int start, int end) throws IOException {
        CharSequences.writeCharSequence(target, csq, start, end);
    }

    /* (non-Javadoc)
     * @see EncodedAppender#close()
     */
    public void close() throws IOException {
        target.close();
    }
    
    @Override
    protected EncodingState createNewEncodingState(Encoder encoder, EncodingState encodingState) {
        return null;
    }
}
