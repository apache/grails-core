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
package org.grails.web.pages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.buffer.GrailsRoutablePrintWriter;
import org.grails.buffer.StreamCharBuffer;
import org.grails.buffer.StreamCharBuffer.LazyInitializingWriter;
import org.grails.buffer.StreamCharBuffer.StreamCharBufferWriter;
import org.grails.encoder.EncodedAppender;
import org.grails.encoder.EncodedAppenderFactory;
import org.grails.encoder.Encoder;
import org.grails.encoder.EncoderAware;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.BoundedCharsAsEncodedBytesCounter;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.objenesis.instantiator.ObjectInstantiator;

import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.io.Writer;

/**
 * NOTE: Based on work done by on the GSP standalone project (https://gsp.dev.java.net/)
 * A buffered writer that won't commit the response until the buffer has reached the high watermark, or until flush() or close() is called.
 * Performance optimizations by Lari Hotari, 13.03.2009
 * Calculating the Content-Length has been disabled by default since Jetty ignores it (uses Chunked mode anyways).
 * Content-Length mode can be enabled with -DGSPResponseWriter.enableContentLength=true system property.
 *
 * @author Troy Heninger
 * @author Graeme Rocher
 * @author Lari Hotari, Sagire Software Oy
 *
 * Date: Jan 10, 2004
 */
public class GSPResponseWriter extends GrailsRoutablePrintWriter implements EncoderAware, EncodedAppenderFactory {
    protected static final Log LOG = LogFactory.getLog(GSPResponseWriter.class);
    private ServletResponse response;
    private BoundedCharsAsEncodedBytesCounter bytesCounter;
    public static final boolean CONTENT_LENGTH_COUNTING_ENABLED = Boolean.getBoolean("GSPResponseWriter.enableContentLength");
    public static final boolean BUFFERING_ENABLED = Boolean.valueOf(System.getProperty("GSPResponseWriter.enableBuffering", "true"));
    public static final boolean AUTOFLUSH_ENABLED = Boolean.getBoolean("GSPResponseWriter.enableAutoFlush");
    private static final int BUFFER_SIZE = Integer.getInteger("GSPResponseWriter.bufferSize", 8042);
    private Encoder encoder;
    private StreamCharBuffer buffer;
    private static ObjectInstantiator<GSPResponseWriter> instantiator = null;

    static {
        try {
            instantiator = new ObjenesisStd(false).getInstantiatorOf(GSPResponseWriter.class);
        } catch (Exception e) {
            LOG.debug("Couldn't get direct performance optimized instantiator for GSPResponseWriter. Using default instantiation.", e);
        }
    }

    public static GSPResponseWriter getInstance(final ServletResponse response) {
        return getInstance(response, BUFFER_SIZE);
    }

    /**
     * Static factory methdirectWritingod to create the writer.
     *
     * @param response The {@link ServletResponse}
     * @param max The chunkSize
     * @return A {@link GSPResponseWriter} instance
     */
    private static GSPResponseWriter getInstance(final ServletResponse response, final int max) {
        final BoundedCharsAsEncodedBytesCounter bytesCounter = new BoundedCharsAsEncodedBytesCounter();

        final StreamCharBuffer streamBuffer = new StreamCharBuffer(max, 0, max);
        streamBuffer.setChunkMinSize(max / 2);
        streamBuffer.setNotifyParentBuffersEnabled(false);

        final StreamCharBuffer.LazyInitializingWriter lazyResponseWriter = response::getWriter;

        streamBuffer.connectTo(new StreamCharBuffer.LazyInitializingMultipleWriter() {
            public Writer getWriter() {
                return null;
            }

            public LazyInitializingWriter[] initializeMultiple(StreamCharBuffer buffer, boolean autoFlush) {
                final StreamCharBuffer.LazyInitializingWriter[] lazyWriters;
                if (CONTENT_LENGTH_COUNTING_ENABLED) {
                    lazyWriters = new StreamCharBuffer.LazyInitializingWriter[]{() -> {
                        bytesCounter.setCapacity(max * 2);
                        bytesCounter.setEncoding(response.getCharacterEncoding());
                        return bytesCounter.getCountingWriter();
                    }, lazyResponseWriter};
                } else {
                    lazyWriters = new StreamCharBuffer.LazyInitializingWriter[]{lazyResponseWriter};
                }
                return lazyWriters;
            }
        }, AUTOFLUSH_ENABLED);

        if (instantiator != null) {
            GSPResponseWriter instance = instantiator.newInstance();
            instance.initialize(streamBuffer, response, bytesCounter);
            return instance;
        } else {
            return new GSPResponseWriter(streamBuffer, response, bytesCounter);
        }
    }

    /**
     * Static factory method to create the writer.
     * <p>
     * TODO: this can be removed?
     *
     * @param target The target writer to write too
     * @param max The chunkSize
     * @return A {@link GSPResponseWriter} instance
     */
    @SuppressWarnings("unused")
    private static GSPResponseWriter getInstance(Writer target, int max) {
        if (BUFFERING_ENABLED && !(target instanceof GrailsRoutablePrintWriter) && !(target instanceof StreamCharBufferWriter)) {
            StreamCharBuffer streamBuffer = new StreamCharBuffer(max, 0, max);
            streamBuffer.connectTo(target, false);
            target = streamBuffer.getWriter();
        }

        if (instantiator == null) {
            return new GSPResponseWriter(target);
        }

        GSPResponseWriter instance = instantiator.newInstance();
        instance.initialize(target);
        return instance;
    }

    /**
     * Private constructor.  Use getInstance() instead.
     *
     * @param buffer       buffered writer
     * @param response     The servlet response
     * @param bytesCounter Keeps count of encoded bytes count
     */
    private GSPResponseWriter(final StreamCharBuffer buffer, final ServletResponse response, BoundedCharsAsEncodedBytesCounter bytesCounter) {
        super(null);

        initialize(buffer, response, bytesCounter);
    }

    void initialize(final StreamCharBuffer buffer, final ServletResponse response,
                    BoundedCharsAsEncodedBytesCounter bytesCounter) {
        DestinationFactory lazyTargetFactory = () -> {
            final GrailsWebRequest webRequest = GrailsWebRequest.lookup();
            encoder = webRequest != null ? webRequest.lookupFilteringEncoder() : null;
            if (encoder != null) {
                return buffer.getWriterForEncoder(encoder, webRequest.getEncodingStateRegistry());
            }
            return buffer.getWriter();
        };

        updateDestination(lazyTargetFactory);
        this.response = response;
        this.bytesCounter = bytesCounter;
        setBlockClose(true);
        setBlockFlush(false);
    }

    /**
     * Private constructor.  Use getInstance() instead.
     *
     * @param activeWriter buffered writer
     */
    private GSPResponseWriter(final Writer activeWriter) {
        super(null);
        initialize(activeWriter);
    }

    void initialize(final Writer activeWriter) {
        updateDestination(() -> activeWriter);
        setBlockClose(true);
        setBlockFlush(false);
    }

    /**
     * Close the stream.
     *
     * @see #checkError()
     */
    @Override
    public void close() {
        flush();
        if (canFlushContentLengthAwareResponse()) {
            int size = bytesCounter.size();
            if (size > 0) {
                response.setContentLength(size);
            }
            flushResponse();
        }
    }

    private boolean canFlushContentLengthAwareResponse() {
        return CONTENT_LENGTH_COUNTING_ENABLED && isDestinationActivated() && bytesCounter != null && bytesCounter.isWriterReferenced() && response != null && !response.isCommitted() && !isTrouble();
    }

    private void flushResponse() {
        try {
            if (isDestinationActivated()) {
                response.getWriter().flush();
            }
        } catch (IOException e) {
            handleIOException(e);
        }
    }

    @Override
    public boolean isAllowUnwrappingOut() {
        return false;
    }

    @Override
    public Writer unwrap() {
        return this;
    }

    public EncodedAppender getEncodedAppender() {
        if (buffer != null) {
            return ((EncodedAppenderFactory) buffer.getWriter()).getEncodedAppender();
        }

        activateDestination();
        try (Writer target = getTarget().unwrap()) {
            if (target != this && target instanceof EncodedAppenderFactory) {
                return ((EncodedAppenderFactory) target).getEncodedAppender();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public Encoder getEncoder() {
        return encoder;
    }
}
