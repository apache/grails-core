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

import groovy.lang.GroovyObjectSupport;
import groovy.lang.Writable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.grails.charsequences.CharArrayAccessible;
import org.grails.charsequences.CharSequences;
import org.grails.encoder.AbstractEncodedAppender;
import org.grails.encoder.ChainedEncoders;
import org.grails.encoder.CodecIdentifier;
import org.grails.encoder.DefaultCodecIdentifier;
import org.grails.encoder.Encodeable;
import org.grails.encoder.EncodedAppender;
import org.grails.encoder.EncodedAppenderFactory;
import org.grails.encoder.EncodedAppenderWriter;
import org.grails.encoder.EncodedAppenderWriterFactory;
import org.grails.encoder.Encoder;
import org.grails.encoder.EncoderAware;
import org.grails.encoder.EncodesToWriter;
import org.grails.encoder.EncodingState;
import org.grails.encoder.EncodingStateImpl;
import org.grails.encoder.EncodingStateRegistry;
import org.grails.encoder.EncodingStateRegistryLookup;
import org.grails.encoder.EncodingStateRegistryLookupHolder;
import org.grails.encoder.StreamEncodeable;
import org.grails.encoder.StreamingEncoder;
import org.grails.encoder.StreamingEncoderWritable;
import org.grails.encoder.WriterEncodedAppender;

import java.io.EOFException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * StreamCharBuffer is a multipurpose in-memory buffer that can replace JDK
 * in-memory buffers (StringBuffer, StringBuilder, StringWriter).
 * </p>
 *
 * <p>
 * Grails GSP rendering uses this class as a buffer that is optimized for performance.
 * </p>
 *
 * <p>
 * StreamCharBuffer keeps the buffer in a linked list of "chunks". The main
 * difference compared to JDK in-memory buffers (StringBuffer, StringBuilder and
 * StringWriter) is that the buffer can be held in several smaller buffers
 * ("chunks" here). In JDK in-memory buffers, the buffer has to be expanded
 * whenever it gets filled up. The old buffer's data is copied to the new one
 * and the old one is discarded. In StreamCharBuffer, there are several ways to
 * prevent unnecessary allocation and copy operations. The StreamCharBuffer
 * contains a linked list of different type of chunks: char arrays,
 * java.lang.String chunks and other StreamCharBuffers as sub chunks. A
 * StringChunk is appended to the linked list whenever a java.lang.String of a
 * length that exceeds the "stringChunkMinSize" value is written to the buffer.
 * </p>
 *
 * <p>
 * Grails tag libraries also use a StreamCharBuffer to "capture" the output of
 * the taglib and return it to the caller. The buffer can be appended to it's
 * parent buffer directly without extra object generation (like converting to
 * java.lang.String in between).
 * <p>
 * for example this line of code in a taglib would just append the buffer
 * returned from the body closure evaluation to the buffer of the taglib:<br>
 * <code>out &lt;&lt; body()</code>
 * <br>
 * other example:<br>
 * <code>out &lt;&lt; g.render(template: '/some/template', model:[somebean: somebean])</code>
 * <br>
 * There's no extra java.lang.String generation overhead.
 *
 * </p>
 *
 * <p>
 * There's a java.io.Writer interface for appending character data to the buffer
 * and a java.io.Reader interface for reading data.
 * </p>
 *
 * <p>
 * Each {@link #getReader()} call will create a new reader instance that keeps
 * it own state.<br>
 * There is a alternative method {@link #getReader(boolean)} for creating the
 * reader. When reader is created by calling getReader(true), the reader will
 * remove already read characters from the buffer. In this mode only a single
 * reader instance is supported.
 * </p>
 *
 * <p>
 * There's also several other options for reading data:<br>
 * readAsCharArray()reads the buffer to a char[] array<br>
 * readAsString() reads the buffer and wraps the char[] data as a
 * String<br>
 * {@link #writeTo(Writer)} writes the buffer to a java.io.Writer<br>
 * {@link #toCharArray()} returns the buffer as a char[] array, caches the
 * return value internally so that this method can be called several times.<br>
 * {@link #toString()} returns the buffer as a String, caches the return value
 * internally<br>
 * </p>
 *
 * <p>
 * By using the "connectTo" method, one can connect the buffer directly to a
 * target java.io.Writer. The internal buffer gets flushed automaticly to the
 * target whenever the buffer gets filled up. See connectTo(Writer).
 * </p>
 *
 * <p>
 * <b>This class is not thread-safe.</b> Object instances of this class are
 * intended to be used by a single Thread. The Reader and Writer interfaces can
 * be open simultaneous and the same Thread can write and read several times.
 * </p>
 *
 * <p>
 * Main operation principle:<br>
 * </p>
 * <p>
 * StreamCharBuffer keeps the buffer in a linked link of "chunks".<br>
 * The main difference compared to JDK in-memory buffers (StringBuffer,
 * StringBuilder and StringWriter) is that the buffer can be held in several
 * smaller buffers ("chunks" here).<br>
 * In JDK in-memory buffers, the buffer has to be expanded whenever it gets
 * filled up. The old buffer's data is copied to the new one and the old one is
 * discarded.<br>
 * In StreamCharBuffer, there are several ways to prevent unnecessary allocation
 * and copy operations.
 * </p>
 * <p>
 * There can be several different type of chunks: char arrays (
 * {@code CharBufferChunk}), String chunks ({@code StringChunk}) and other
 * StreamCharBuffers as sub chunks ({@code StreamCharBufferSubChunk}).
 * </p>
 * <p>
 * Child StreamCharBuffers can be changed after adding to parent buffer. The
 * flush() method must be called on the child buffer's Writer to notify the
 * parent that the child buffer's content has been changed (used for calculating
 * total size).
 * </p>
 * <p>
 * A StringChunk is appended to the linked list whenever a java.lang.String of a
 * length that exceeds the "stringChunkMinSize" value is written to the buffer.
 * </p>
 * <p>
 * If the buffer is in "connectTo" mode, any String or char[] that's length is
 * over writeDirectlyToConnectedMinSize gets written directly to the target. The
 * buffer content will get fully flushed to the target before writing the String
 * or char[].
 * </p>
 * <p>
 * There can be several targets "listening" the buffer in "connectTo" mode. The
 * same content will be written to all targets.
 * <p>
 * <p>
 * Growable chunksize: By default, a newly allocated chunk's size will grow
 * based on the total size of all written chunks.<br>
 * The default growProcent value is 100. If the total size is currently 1024,
 * the newly created chunk will have a internal buffer that's size is 1024.<br>
 * Growable chunksize can be turned off by setting the growProcent to 0.<br>
 * There's a default maximum chunksize of 1MB by default. The minimum size is
 * the initial chunksize size.<br>
 * </p>
 * <table>
 * <caption>System properties to change default configuration parameters:</caption>
 * <tr>
 * <th>System Property name</th>
 * <th>Description</th>
 * <th>Default value</th>
 * </tr>
 * <tr>
 * <td>streamcharbuffer.chunksize</td>
 * <td>default chunk size - the size the first allocated buffer</td>
 * <td>512</td>
 * </tr>
 * <tr>
 * <td>streamcharbuffer.maxchunksize</td>
 * <td>maximum chunk size - the maximum size of the allocated buffer</td>
 * <td>1048576</td>
 * </tr>
 * <tr>
 * <td>streamcharbuffer.growprocent</td>
 * <td>growing buffer percentage - the newly allocated buffer is defined by
 * total_size * (growpercent/100)</td>
 * <td>100</td>
 * </tr>
 * <tr>
 * <td>streamcharbuffer.subbufferchunkminsize</td>
 * <td>minimum size of child StreamCharBuffer chunk - if the size is smaller,
 * the content is copied to the parent buffer</td>
 * <td>512</td>
 * </tr>
 * <tr>
 * <td>streamcharbuffer.substringchunkminsize</td>
 * <td>minimum size of String chunks - if the size is smaller, the content is
 * copied to the buffer</td>
 * <td>512</td>
 * </tr>
 * <tr>
 * <td>streamcharbuffer.chunkminsize</td>
 * <td>minimum size of chunk that gets written directly to the target in
 * connected mode.</td>
 * <td>256</td>
 * </tr>
 * </table>
 * <p>
 * Configuration values can also be changed for each instance of
 * StreamCharBuffer individually. Default values are defined with System
 * Properties.
 *
 * @author Lari Hotari, Sagire Software Oy
 */
public class StreamCharBuffer extends GroovyObjectSupport implements Writable, CharSequence, Externalizable, Encodeable, StreamEncodeable, StreamingEncoderWritable, EncodedAppenderWriterFactory, Cloneable {

    private static final Log LOG = LogFactory.getLog(StreamCharBuffer.class);
    private static final int EXTERNALIZABLE_VERSION = 2;
    private static final long serialVersionUID = EXTERNALIZABLE_VERSION;

    private static final int DEFAULT_CHUNK_SIZE = Integer.getInteger("streamcharbuffer.chunksize", 512);
    private static final int DEFAULT_MAX_CHUNK_SIZE = Integer.getInteger("streamcharbuffer.maxchunksize", 1024 * 1024);
    private static final int DEFAULT_CHUNK_SIZE_GROW_PROCENT = Integer.getInteger("streamcharbuffer.growprocent", 100);
    private static final int SUB_BUFFERCHUNK_MIN_SIZE = Integer.getInteger("streamcharbuffer.subbufferchunkminsize", 512);
    private static final int SUB_STRINGCHUNK_MIN_SIZE = Integer.getInteger("streamcharbuffer.substringchunkminsize", 512);
    private static final int WRITE_DIRECT_MIN_SIZE = Integer.getInteger("streamcharbuffer.writedirectminsize", 1024);
    private static final int CHUNK_MIN_SIZE = Integer.getInteger("streamcharbuffer.chunkminsize", 256);

    boolean preferSubChunkWhenWritingToOtherBuffer = false;
    int allocatedBufferIdSequence = 0;
    int readerCount = 0;
    boolean hasReaders = false;
    int bufferChangesCounter = 0;

    boolean notifyParentBuffersEnabled = true;
    boolean subBuffersEnabled = true;

    private final int firstChunkSize;
    private final int growProcent;
    private final int maxChunkSize;
    private int subStringChunkMinSize = SUB_STRINGCHUNK_MIN_SIZE;
    private int subBufferChunkMinSize = SUB_BUFFERCHUNK_MIN_SIZE;
    private int writeDirectlyToConnectedMinSize = WRITE_DIRECT_MIN_SIZE;
    private int chunkMinSize = CHUNK_MIN_SIZE;

    private int chunkSize;
    private int totalChunkSize;

    private final StreamCharBufferWriter writer;
    private List<ConnectToWriter> connectToWriters;
    private ConnectedWritersWriter connectedWritersWriter;
    private Boolean notConnectedToEncodeAwareWriters = null;

    private AllocatedBuffer allocBuffer;
    private AbstractChunk firstChunk;
    private AbstractChunk lastChunk;
    private int totalCharsInList;
    private int totalCharsInDynamicChunks;
    private int sizeAtLeast;
    private StreamCharBufferKey bufferKey = new StreamCharBufferKey();
    private Map<StreamCharBufferKey, StreamCharBufferSubChunk> dynamicChunkMap;

    private Set<SoftReference<StreamCharBufferKey>> parentBuffers;

    public StreamCharBuffer() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_SIZE_GROW_PROCENT, DEFAULT_MAX_CHUNK_SIZE);
    }

    public StreamCharBuffer(int chunkSize) {
        this(chunkSize, DEFAULT_CHUNK_SIZE_GROW_PROCENT, DEFAULT_MAX_CHUNK_SIZE);
    }

    public StreamCharBuffer(int chunkSize, int growProcent) {
        this(chunkSize, growProcent, DEFAULT_MAX_CHUNK_SIZE);
    }

    public StreamCharBuffer(int chunkSize, int growProcent, int maxChunkSize) {
        this.firstChunkSize = chunkSize;
        this.growProcent = growProcent;
        this.maxChunkSize = maxChunkSize;
        writer = new StreamCharBufferWriter();
        reset(true);
    }

    public boolean isPreferSubChunkWhenWritingToOtherBuffer() {
        return preferSubChunkWhenWritingToOtherBuffer;
    }

    public void setPreferSubChunkWhenWritingToOtherBuffer(boolean prefer) {
        preferSubChunkWhenWritingToOtherBuffer = prefer;
        notifyPreferSubChunkEnabled();
    }

    protected void notifyPreferSubChunkEnabled() {
        if (isPreferSubChunkWhenWritingToOtherBuffer() && parentBuffers != null && isNotifyParentBuffersEnabled()) {
            for (StreamCharBuffer parentBuffer : getCurrentParentBuffers()) {
                if (!parentBuffer.isPreferSubChunkWhenWritingToOtherBuffer()) {
                    parentBuffer.setPreferSubChunkWhenWritingToOtherBuffer(true);
                }
            }
        }
    }

    public final void reset() {
        reset(true);
    }

    /**
     * resets the state of this buffer (empties it)
     *
     * @param resetChunkSize
     */
    public final void reset(boolean resetChunkSize) {
        markBufferChanged();
        firstChunk = null;
        lastChunk = null;
        totalCharsInList = 0;
        totalCharsInDynamicChunks = -1;
        sizeAtLeast = -1;
        if (resetChunkSize) {
            chunkSize = firstChunkSize;
            totalChunkSize = 0;
        }
        if (allocBuffer == null) {
            allocBuffer = new AllocatedBuffer(chunkSize);
        } else {
            allocBuffer.clear();
        }
        if (dynamicChunkMap == null) {
            dynamicChunkMap = new HashMap<StreamCharBufferKey, StreamCharBufferSubChunk>();
        } else {
            dynamicChunkMap.clear();
        }
    }

    /**
     * Clears the buffer and notifies the parents of this buffer of the change.
     */
    public final void clear() {
        reset();
        notifyBufferChange();
    }

    /**
     * Connect this buffer to a target Writer.
     * <p>
     * When the buffer (a chunk) get filled up, it will automaticly write it's content to the Writer
     *
     * @param w
     */
    public final void connectTo(Writer w) {
        connectTo(w, true);
    }

    public final void connectTo(Writer w, boolean autoFlush) {
        initConnected();
        connectToWriters.add(new ConnectToWriter(w, autoFlush));
        initConnectedWritersWriter();
    }

    public final void encodeInStreamingModeTo(final EncoderAware encoderLookup, final EncodingStateRegistryLookup encodingStateRegistryLookup, boolean autoFlush, final Writer w) {
        encodeInStreamingModeTo(encoderLookup, encodingStateRegistryLookup, autoFlush, new LazyInitializingWriter() {
            public Writer getWriter() throws IOException {
                return w;
            }
        });
    }

    public final void encodeInStreamingModeTo(final EncoderAware encoderLookup, final EncodingStateRegistryLookup encodingStateRegistryLookup, final boolean autoFlush, final LazyInitializingWriter... writers) {
        LazyInitializingWriter encodingWriterInitializer = createEncodingInitializer(encoderLookup,
                encodingStateRegistryLookup, writers);
        connectTo(encodingWriterInitializer, autoFlush);
    }

    public LazyInitializingWriter createEncodingInitializer(final EncoderAware encoderLookup,
                                                            final EncodingStateRegistryLookup encodingStateRegistryLookup, final LazyInitializingWriter... writers) {
        LazyInitializingWriter encodingWriterInitializer = new LazyInitializingMultipleWriter() {
            Writer lazyWriter;

            public Writer getWriter() throws IOException {
                return lazyWriter;
            }

            public LazyInitializingWriter[] initializeMultiple(StreamCharBuffer buffer, boolean autoFlushMode) throws IOException {
                Encoder encoder = encoderLookup.getEncoder();
                if (encoder != null) {
                    EncodingStateRegistry encodingStateRegistry = encodingStateRegistryLookup.lookup();
                    StreamCharBuffer encodeBuffer = new StreamCharBuffer(chunkSize, growProcent, maxChunkSize);
                    encodeBuffer.setAllowSubBuffers(false);
                    lazyWriter = encodeBuffer.getWriterForEncoder(encoder, encodingStateRegistry);
                    for (LazyInitializingWriter w : writers) {
                        encodeBuffer.connectTo(w, autoFlushMode);
                    }
                    return new LazyInitializingWriter[]{this};
                } else {
                    return writers;
                }
            }
        };
        return encodingWriterInitializer;
    }

    private void initConnectedWritersWriter() {
        notConnectedToEncodeAwareWriters = null;
        connectedWritersWriter = null;
        setNotifyParentBuffersEnabled(false);
    }

    private void startUsingConnectedWritersWriter() throws IOException {
        if (connectedWritersWriter == null) {
            List<ConnectedWriter> connectedWriters = new ArrayList<ConnectedWriter>();

            for (ConnectToWriter connectToWriter : connectToWriters) {
                for (Writer writer : connectToWriter.getWriters()) {
                    Writer target = writer;
                    if (target instanceof GrailsWrappedWriter) {
                        target = ((GrailsWrappedWriter) target).unwrap();
                    }
                    if (target == null) {
                        throw new NullPointerException("target is null");
                    }
                    connectedWriters.add(new ConnectedWriter(target, connectToWriter.isAutoFlush()));
                }
            }

            if (connectedWriters.size() > 1) {
                connectedWritersWriter = new MultiOutputWriter(connectedWriters);
            } else {
                connectedWritersWriter = new SingleOutputWriter(connectedWriters.get(0));
            }
        }
    }

    public final void connectTo(LazyInitializingWriter w) {
        connectTo(w, true);
    }

    public final void connectTo(LazyInitializingWriter w, boolean autoFlush) {
        initConnected();
        connectToWriters.add(new ConnectToWriter(w, autoFlush));
        initConnectedWritersWriter();
    }

    public final void removeConnections() {
        if (connectToWriters != null) {
            connectToWriters = null;
            connectedWritersWriter = null;
            notConnectedToEncodeAwareWriters = null;
        }
    }

    private void initConnected() {
        if (connectToWriters == null) {
            connectToWriters = new ArrayList<ConnectToWriter>(2);
        }
    }

    public int getSubStringChunkMinSize() {
        return subStringChunkMinSize;
    }

    /**
     * Minimum size for a String to be added as a StringChunk instead of copying content to the char[] buffer of the current StreamCharBufferChunk
     *
     * @param size
     */
    public void setSubStringChunkMinSize(int size) {
        subStringChunkMinSize = size;
    }

    public int getSubBufferChunkMinSize() {
        return subBufferChunkMinSize;
    }

    public void setSubBufferChunkMinSize(int size) {
        subBufferChunkMinSize = size;
    }

    public int getWriteDirectlyToConnectedMinSize() {
        return writeDirectlyToConnectedMinSize;
    }

    /**
     * Minimum size for a String or char[] to get written directly to connected writer (in "connectTo" mode).
     *
     * @param size
     */
    public void setWriteDirectlyToConnectedMinSize(int size) {
        writeDirectlyToConnectedMinSize = size;
    }

    public int getChunkMinSize() {
        return chunkMinSize;
    }

    public void setChunkMinSize(int size) {
        chunkMinSize = size;
    }

    /**
     * Writer interface for adding/writing data to the buffer.
     *
     * @return the Writer
     */
    public Writer getWriter() {
        return writer;
    }

    /**
     * Creates a new Reader instance for reading/consuming data from the buffer.
     * Each call creates a new instance that will keep it's reading state. There can be several readers on the buffer. (single thread only supported)
     *
     * @return the Reader
     */
    public Reader getReader() {
        return getReader(false);
    }

    /**
     * Like getReader(), but when removeAfterReading is true, the read data will be removed from the buffer.
     *
     * @param removeAfterReading
     * @return the Reader
     */
    public Reader getReader(boolean removeAfterReading) {
        readerCount++;
        hasReaders = true;
        return new StreamCharBufferReader(removeAfterReading);
    }

    /**
     * Writes the buffer content to a target java.io.Writer
     *
     * @param target
     * @throws IOException
     */
    public Writer writeTo(Writer target) throws IOException {
        writeTo(target, false, false);
        return target;
    }

    /**
     * Writes the buffer content to a target java.io.Writer
     *
     * @param target      Writer
     * @param flushTarget calls target.flush() before finishing
     * @param emptyAfter  empties the buffer if true
     * @throws IOException
     */
    public void writeTo(Writer target, boolean flushTarget, boolean emptyAfter) throws IOException {
        if (target instanceof GrailsWrappedWriter) {
            GrailsWrappedWriter wrappedWriter = ((GrailsWrappedWriter) target);
            if (wrappedWriter.isAllowUnwrappingOut()) {
                target = wrappedWriter.unwrap();
            }
        }
        if (target == writer) {
            throw new IllegalArgumentException("Cannot write buffer to itself.");
        }
        if (!emptyAfter && target instanceof StreamCharBufferWriter) {
            ((StreamCharBufferWriter) target).write(this, null);
            return;
        } else if (writeToEncodedAppender(this, target, writer.getEncodedAppender(), true)) {
            if (emptyAfter) {
                emptyAfterReading();
            }
            if (flushTarget) {
                target.flush();
            }
            return;
        }
        writeToImpl(target, flushTarget, emptyAfter);
    }

    private static boolean writeToEncodedAppender(StreamCharBuffer source, Writer target, EncodedAppender notAllowedAppender, boolean flush) throws IOException {
        if (target instanceof EncodedAppenderFactory) {
            EncodedAppenderFactory eaw = (EncodedAppenderFactory) target;
            EncodedAppender appender = eaw.getEncodedAppender();
            if (appender != null) {
                if (appender == notAllowedAppender) {
                    throw new IllegalArgumentException("Cannot write buffer to itself.");
                }
                Encoder encoder = null;

                if (target instanceof EncoderAware) {
                    encoder = ((EncoderAware) target).getEncoder();
                }

                if (encoder == null && appender instanceof EncoderAware) {
                    encoder = ((EncoderAware) appender).getEncoder();
                }

                source.encodeTo(appender, encoder);
                if (flush) {
                    appender.flush();
                }
                return true;
            }
        }
        return false;
    }

    private void writeToImpl(Writer target, boolean flushTarget, boolean emptyAfter) throws IOException {
        AbstractChunk current = firstChunk;
        while (current != null) {
            current.writeTo(target);
            current = current.next;
        }
        allocBuffer.writeTo(target);
        if (emptyAfter) {
            emptyAfterReading();
        }
        if (flushTarget) {
            target.flush();
        }
    }

    protected void emptyAfterReading() {
        firstChunk = null;
        lastChunk = null;
        totalCharsInList = 0;
        totalCharsInDynamicChunks = -1;
        sizeAtLeast = -1;
        dynamicChunkMap.clear();
        allocBuffer.clear();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reads (and empties) the buffer to a String, but caches the return value for subsequent calls.
     * If more content has been added between 2 calls, the returned value will be joined from the previously cached value and the data read from the buffer.
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringChunk stringChunk = readToSingleStringChunk(true);
        if (stringChunk != null) {
            return stringChunk.str;
        } else {
            return "";
        }
    }

    public StringChunk readToSingleStringChunk(boolean registerEncodingState) {
        if (firstChunk == lastChunk && firstChunk instanceof StringChunk && allocBuffer.charsUsed() == 0 &&
                ((StringChunk) firstChunk).isSingleBuffer()) {
            StringChunk chunk = ((StringChunk) firstChunk);
            if (registerEncodingState) {
                markEncoded(chunk);
            }
            return chunk;
        }

        int initialReaderCount = readerCount;
        MultipartCharBufferChunk chunk = readToSingleChunk();
        MultipartStringChunk stringChunk = (chunk != null) ? chunk.asStringChunk() : null;
        if (initialReaderCount == 0) {
            // if there are no readers, the result can be cached
            reset();
            if (stringChunk != null) {
                addChunk(stringChunk);
            }
        }

        if (registerEncodingState) {
            markEncoded(stringChunk);
        }

        return stringChunk;
    }

    public void markEncoded(StringChunk strChunk) {
        if (strChunk instanceof MultipartStringChunk) {
            MultipartStringChunk stringChunk = (MultipartStringChunk) strChunk;
            if (stringChunk.isSingleEncoding()) {
                EncodingState encodingState = stringChunk.firstPart.encodingState;
                if (encodingState != null && encodingState.getEncoders() != null && encodingState.getEncoders().size() > 0) {
                    Encoder encoder = encodingState.getEncoders().iterator().next();
                    if (encoder != null) {
                        encoder.markEncoded(stringChunk.str);
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Uses String's hashCode to support compatibility with String instances in maps, sets, etc.
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * equals uses String.equals to check for equality to support compatibility with String instances in maps, sets, etc.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof CharSequence)) {
            return false;
        }

        CharSequence other = (CharSequence) o;

        return toString().equals(other.toString());
    }

    public String plus(String value) {
        return toString() + value;
    }

    public String plus(Object value) {
        return toString() + value;
    }

    /**
     * Reads the buffer to a char[].
     * <p>
     * Caches the result if there aren't any readers.
     *
     * @return the chars
     */
    public char[] toCharArray() {
        // check if there is a cached single charbuffer
        if (firstChunk == lastChunk && firstChunk instanceof CharBufferChunk && allocBuffer.charsUsed() == 0 && ((CharBufferChunk) firstChunk).isSingleBuffer()) {
            return ((CharBufferChunk) firstChunk).buffer;
        }

        int initialReaderCount = readerCount;
        MultipartCharBufferChunk chunk = readToSingleChunk();
        if (initialReaderCount == 0) {
            // if there are no readers, the result can be cached
            reset();
            if (chunk != null) {
                addChunk(chunk);
            }
        }
        if (chunk != null) {
            return chunk.buffer;
        } else {
            return new char[0];
        }
    }

    public List<EncodedPart> dumpEncodedParts() {
        List<EncodedPart> encodedParts = new ArrayList<StreamCharBuffer.EncodedPart>();
        MultipartStringChunk mpStringChunk = readToSingleChunk().asStringChunk();
        if (mpStringChunk.firstPart != null) {
            EncodingStatePart current = mpStringChunk.firstPart;
            int offset = 0;
            char[] buf = StringCharArrayAccessor.getValue(mpStringChunk.str);
            while (current != null) {
                encodedParts.add(new EncodedPart(current.encodingState, new String(buf, offset, current.len)));
                offset += current.len;
                current = current.next;
            }
        }
        return encodedParts;
    }

    private MultipartCharBufferChunk readToSingleChunk() {
        int currentSize = size();
        if (currentSize == 0) {
            return null;
        }

        FixedCharArrayEncodedAppender appender = new FixedCharArrayEncodedAppender(currentSize);
        try {
            encodeTo(appender, null);
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException", e);
        }
        appender.finish();
        return appender.chunk;
    }

    boolean hasQuicklyCalcutableSize() {
        return totalCharsInDynamicChunks != -1 || dynamicChunkMap.size() == 0;
    }

    public int size() {
        int total = totalCharsInList;
        if (totalCharsInDynamicChunks == -1) {
            totalCharsInDynamicChunks = 0;
            for (StreamCharBufferSubChunk chunk : dynamicChunkMap.values()) {
                totalCharsInDynamicChunks += chunk.size();
            }
        }
        total += totalCharsInDynamicChunks;
        total += allocBuffer.charsUsed();
        sizeAtLeast = total;
        return total;
    }

    public boolean isEmpty() {
        return !isNotEmpty();
    }

    boolean isNotEmpty() {
        if (totalCharsInList > 0) {
            return true;
        }
        if (totalCharsInDynamicChunks > 0) {
            return true;
        }
        if (allocBuffer.charsUsed() > 0) {
            return true;
        }
        if (totalCharsInDynamicChunks == -1) {
            for (StreamCharBufferSubChunk chunk : dynamicChunkMap.values()) {
                if (chunk.getSourceBuffer().isNotEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isSizeLarger(int minSize) {
        if (minSize <= sizeAtLeast) {
            return true;
        }

        boolean retval = calculateIsSizeLarger(minSize);
        if (retval && minSize > sizeAtLeast) {
            sizeAtLeast = minSize;
        }
        return retval;
    }

    private boolean calculateIsSizeLarger(int minSize) {
        int total = totalCharsInList;
        total += allocBuffer.charsUsed();
        if (total > minSize) {
            return true;
        }
        if (totalCharsInDynamicChunks != -1) {
            total += totalCharsInDynamicChunks;
            if (total > minSize) {
                return true;
            }
        } else {
            for (StreamCharBufferSubChunk chunk : dynamicChunkMap.values()) {
                int remaining = minSize - total;
                if (!chunk.hasCachedSize() && (chunk.getSourceBuffer().isSizeLarger(remaining) || (chunk.getEncodedBuffer() != chunk.getSourceBuffer() && chunk.getEncodedBuffer().isSizeLarger(remaining)))) {
                    return true;
                }
                total += chunk.size();
                if (total > minSize) {
                    return true;
                }
            }
        }
        return false;
    }

    int allocateSpace(EncodingState encodingState) throws IOException {
        int spaceLeft = allocBuffer.spaceLeft(encodingState);
        if (spaceLeft == 0) {
            spaceLeft = appendCharBufferChunk(encodingState, true, true);
        }
        return spaceLeft;
    }

    private int appendCharBufferChunk(EncodingState encodingState, boolean flushInConnected, boolean allocate) throws IOException {
        int spaceLeft = 0;
        if (flushInConnected && isConnectedMode()) {
            flushToConnected(false);
            if (!isChunkSizeResizeable()) {
                allocBuffer.reuseBuffer(encodingState);
            }
        } else {
            if (allocBuffer.hasChunk()) {
                addChunk(allocBuffer.createChunk());
            }
        }
        spaceLeft = allocBuffer.spaceLeft(encodingState);
        if (allocate && spaceLeft == 0) {
            totalChunkSize += allocBuffer.chunkSize();
            resizeChunkSizeAsProcentageOfTotalSize();
            allocBuffer = new AllocatedBuffer(chunkSize);
            spaceLeft = allocBuffer.spaceLeft(encodingState);
        }
        return spaceLeft;
    }

    void appendStringChunk(EncodingState encodingState, String str, int off, int len) throws IOException {
        appendCharBufferChunk(encodingState, false, false);
        addChunk(new StringChunk(str, off, len)).setEncodingState(encodingState);
    }

    public void appendStreamCharBufferChunk(StreamCharBuffer subBuffer) throws IOException {
        appendStreamCharBufferChunk(subBuffer, null);
    }

    public void appendStreamCharBufferChunk(StreamCharBuffer subBuffer, List<Encoder> encoders) throws IOException {
        appendCharBufferChunk(null, false, false);
        addChunk(new StreamCharBufferSubChunk(subBuffer, encoders));
    }

    AbstractChunk addChunk(AbstractChunk newChunk) {
        if (lastChunk != null) {
            lastChunk.next = newChunk;
            if (hasReaders) {
                // double link only if there are active readers since backwards iterating is only required for simultaneous writer & reader
                newChunk.prev = lastChunk;
            }
        }
        lastChunk = newChunk;
        if (firstChunk == null) {
            firstChunk = newChunk;
        }
        if (newChunk instanceof StreamCharBufferSubChunk) {
            StreamCharBufferSubChunk bufSubChunk = (StreamCharBufferSubChunk) newChunk;
            dynamicChunkMap.put(bufSubChunk.getSourceBuffer().bufferKey, bufSubChunk);
        } else {
            totalCharsInList += newChunk.size();
        }
        return newChunk;
    }

    public boolean isConnectedMode() {
        return connectToWriters != null && !connectToWriters.isEmpty();
    }

    private void flushToConnected(boolean forceFlush) throws IOException {
        startUsingConnectedWritersWriter();
        if (notConnectedToEncodeAwareWriters == null) {
            notConnectedToEncodeAwareWriters = !connectedWritersWriter.isEncoderAware();
        }
        writeTo(connectedWritersWriter, forceFlush, true);
        if (forceFlush) {
            connectedWritersWriter.forceFlush();
        }
    }

    protected boolean isChunkSizeResizeable() {
        return (growProcent > 0 && chunkSize < maxChunkSize);
    }

    protected void resizeChunkSizeAsProcentageOfTotalSize() {
        if (growProcent == 0) {
            return;
        }

        if (growProcent == 100) {
            chunkSize = Math.min(totalChunkSize, maxChunkSize);
        } else if (growProcent == 200) {
            chunkSize = Math.min(totalChunkSize << 1, maxChunkSize);
        } else if (growProcent > 0) {
            chunkSize = Math.max(Math.min((totalChunkSize * growProcent) / 100, maxChunkSize), firstChunkSize);
        }
    }

    protected static final void arrayCopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        if (length == 1) {
            dest[destPos] = src[srcPos];
        } else {
            System.arraycopy(src, srcPos, dest, destPos, length);
        }
    }

    private boolean isNotConnectedToEncoderAwareWriters() {
        return notConnectedToEncodeAwareWriters != null && notConnectedToEncodeAwareWriters;
    }

    /* Compatibility methods so that StreamCharBuffer will behave more like java.lang.String in groovy code */

    public char charAt(int index) {
        return toString().charAt(index);
    }

    public int length() {
        return size();
    }

    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    public boolean asBoolean() {
        return isNotEmpty();
    }

    /* methods for notifying child (sub) StreamCharBuffer changes to the parent StreamCharBuffer */

    void addParentBuffer(StreamCharBuffer parent) {
        if (!notifyParentBuffersEnabled) {
            return;
        }

        if (parentBuffers == null) {
            parentBuffers = new HashSet<SoftReference<StreamCharBufferKey>>();
        }
        parentBuffers.add(new SoftReference<StreamCharBufferKey>(parent.bufferKey));
    }

    protected boolean bufferChanged(StreamCharBuffer buffer) {
        markBufferChanged();

        StreamCharBufferSubChunk subChunk = dynamicChunkMap.get(buffer.bufferKey);
        if (subChunk == null) {
            // buffer isn't a subchunk in this buffer any more
            return false;
        }
        // reset cached size;
        if (subChunk.resetSubBuffer()) {
            totalCharsInDynamicChunks = -1;
            sizeAtLeast = -1;
            // notify parents too
            notifyBufferChange();
        }
        return true;
    }

    protected List<StreamCharBuffer> getCurrentParentBuffers() {
        List<StreamCharBuffer> currentParentBuffers = new ArrayList<StreamCharBuffer>();
        if (parentBuffers != null) {
            for (Iterator<SoftReference<StreamCharBufferKey>> i = parentBuffers.iterator(); i.hasNext(); ) {
                SoftReference<StreamCharBufferKey> ref = i.next();
                final StreamCharBuffer.StreamCharBufferKey parentKey = ref.get();
                if (parentKey != null) {
                    currentParentBuffers.add(parentKey.getBuffer());
                }
            }
        }
        return currentParentBuffers;
    }

    protected void notifyBufferChange() {
        markBufferChanged();

        if (!notifyParentBuffersEnabled) {
            return;
        }

        if (parentBuffers == null || parentBuffers.isEmpty()) {
            return;
        }

        List<SoftReference<StreamCharBufferKey>> parentBuffersList = new ArrayList<>(parentBuffers);
        for (SoftReference<StreamCharBufferKey> ref : parentBuffersList) {
            final StreamCharBuffer.StreamCharBufferKey parentKey = ref.get();
            boolean removeIt = true;
            if (parentKey != null) {
                StreamCharBuffer parent = parentKey.getBuffer();
                removeIt = !parent.bufferChanged(this);
            }
            if (removeIt) {
                parentBuffers.remove(ref);
            }
        }
    }

    public int getBufferChangesCounter() {
        return bufferChangesCounter;
    }

    protected int markBufferChanged() {
        return bufferChangesCounter++;
    }

    @Override
    public StreamCharBuffer clone() {
        StreamCharBuffer cloned = new StreamCharBuffer();
        cloned.setNotifyParentBuffersEnabled(false);
        cloned.setAllowSubBuffers(false);
        if (this.size() > 0) {
            cloned.addChunk(readToSingleChunk());
        }
        cloned.setAllowSubBuffers(true);
        return cloned;
    }

    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        int version = in.readInt();
        if (version != EXTERNALIZABLE_VERSION) {
            throw new IOException("Uncompatible version in serialization stream.");
        }
        reset();
        int len = in.readInt();
        if (len > 0) {
            char[] buf = new char[len];
            Reader reader = new InputStreamReader((InputStream) in, "UTF-8");
            reader.read(buf);
            String str = StringCharArrayAccessor.createString(buf);
            MultipartStringChunk mpStringChunk = new MultipartStringChunk(str);
            int partCount = in.readInt();
            for (int i = 0; i < partCount; i++) {
                EncodingStatePart current = new EncodingStatePart();
                mpStringChunk.appendEncodingStatePart(current);
                current.len = in.readInt();
                int encodersSize = in.readInt();
                Set<Encoder> encoders = null;
                if (encodersSize > 0) {
                    encoders = new LinkedHashSet<Encoder>();
                    for (int j = 0; j < encodersSize; j++) {
                        String codecName = in.readUTF();
                        boolean safe = in.readBoolean();
                        encoders.add(new SavedEncoder(codecName, safe));
                    }
                }
                current.encodingState = new EncodingStateImpl(encoders, null);
            }
            addChunk(mpStringChunk);
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(EXTERNALIZABLE_VERSION);
        StringChunk stringChunk = readToSingleStringChunk(false);
        if (stringChunk != null && stringChunk.str.length() > 0) {
            char[] buf = StringCharArrayAccessor.getValue(stringChunk.str);
            out.writeInt(buf.length);
            Writer writer = new OutputStreamWriter((OutputStream) out, "UTF-8");
            writer.write(buf);
            writer.flush();
            if (stringChunk instanceof MultipartStringChunk) {
                MultipartStringChunk mpStringChunk = (MultipartStringChunk) stringChunk;
                out.writeInt(mpStringChunk.partCount());
                EncodingStatePart current = mpStringChunk.firstPart;
                while (current != null) {
                    out.writeInt(current.len);
                    if (current.encodingState != null && current.encodingState.getEncoders() != null && current.encodingState.getEncoders().size() > 0) {
                        out.writeInt(current.encodingState.getEncoders().size());
                        for (Encoder encoder : current.encodingState.getEncoders()) {
                            out.writeUTF(encoder.getCodecIdentifier().getCodecName());
                            out.writeBoolean(encoder.isSafe());
                        }
                    } else {
                        out.writeInt(0);
                    }
                    current = current.next;
                }
            } else {
                out.writeInt(0);
            }
        } else {
            out.writeInt(0);
        }
    }

    public StreamCharBuffer encodeToBuffer(Encoder encoder) {
        return encodeToBuffer(encoder, isAllowSubBuffers(), isNotifyParentBuffersEnabled());
    }

    public StreamCharBuffer encodeToBuffer(Encoder encoder, boolean allowSubBuffers, boolean notifyParentBuffersEnabled) {
        StreamCharBuffer coded = new StreamCharBuffer(Math.min(Math.max(totalChunkSize, chunkSize) * 12 / 10, maxChunkSize));
        coded.setAllowSubBuffers(allowSubBuffers);
        coded.setNotifyParentBuffersEnabled(notifyParentBuffersEnabled);
        EncodedAppender codedWriter = coded.writer.getEncodedAppender();
        try {
            encodeTo(codedWriter, encoder);
        } catch (IOException e) {
            // Should not ever happen
            LOG.error("IOException in StreamCharBuffer.encodeToBuffer", e);
        }
        return coded;
    }

    public StreamCharBuffer encodeToBuffer(List<Encoder> encoders) {
        return encodeToBuffer(encoders, isAllowSubBuffers(), isNotifyParentBuffersEnabled());
    }

    public StreamCharBuffer encodeToBuffer(List<Encoder> encoders, boolean allowSubBuffers, boolean notifyParentBuffersEnabled) {
        StreamCharBuffer currentBuffer = this;
        for (Encoder encoder : encoders) {
            currentBuffer = currentBuffer.encodeToBuffer(encoder, allowSubBuffers, notifyParentBuffersEnabled);
        }
        return currentBuffer;
    }

    public void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
        if (isPreferSubChunkWhenWritingToOtherBuffer() && appender instanceof StreamCharBufferEncodedAppender) {
            StreamCharBufferWriter writer = ((StreamCharBufferEncodedAppender) appender).getWriter();
            if (writer.appendSubBuffer(this, encoder != null ? Collections.singletonList(encoder) : null)) {
                // subbuffer was appended, so return
                return;
            }
        }
        AbstractChunk current = firstChunk;
        while (current != null) {
            current.encodeTo(appender, encoder);
            current = current.next;
        }
        allocBuffer.encodeTo(appender, encoder);
    }

    public boolean isAllowSubBuffers() {
        return subBuffersEnabled && !isConnectedMode();
    }

    public void setAllowSubBuffers(boolean allowSubBuffers) {
        this.subBuffersEnabled = allowSubBuffers;
    }

    public CharSequence encode(Encoder encoder) {
        return encodeToBuffer(encoder);
    }

    public Writer getWriterForEncoder() {
        return getWriterForEncoder(null);
    }

    public Writer getWriterForEncoder(Encoder encoder) {
        return getWriterForEncoder(encoder, lookupDefaultEncodingStateRegistry());
    }

    protected EncodingStateRegistry lookupDefaultEncodingStateRegistry() {
        EncodingStateRegistryLookup encodingStateRegistryLookup = EncodingStateRegistryLookupHolder.getEncodingStateRegistryLookup();
        return encodingStateRegistryLookup != null ? encodingStateRegistryLookup.lookup() : null;
    }

    public Writer getWriterForEncoder(Encoder encoder, EncodingStateRegistry encodingStateRegistry) {
        return getWriterForEncoder(encoder, encodingStateRegistry, false);
    }

    public Writer getWriterForEncoder(Encoder encoder, EncodingStateRegistry encodingStateRegistry, boolean ignoreEncodingState) {
        EncodedAppender encodedAppender = writer.getEncodedAppender();
        encodedAppender.setIgnoreEncodingState(ignoreEncodingState);
        return new EncodedAppenderWriter(encodedAppender, encoder, encodingStateRegistry);
    }

    public boolean isNotifyParentBuffersEnabled() {
        return notifyParentBuffersEnabled;
    }

    /**
     * By default the parent buffers (a buffer where this buffer has been appended to) get notified of changed to this buffer.
     * <p>
     * You can control the notification behavior with this property.
     * Setting this property to false will also clear the references to parent buffers if there are any.
     *
     * @param notifyParentBuffersEnabled
     */
    public void setNotifyParentBuffersEnabled(boolean notifyParentBuffersEnabled) {
        this.notifyParentBuffersEnabled = notifyParentBuffersEnabled;
        if (!notifyParentBuffersEnabled && parentBuffers != null) {
            parentBuffers.clear();
        }
    }

    @Override
    public void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
        AbstractChunk current = firstChunk;
        while (current != null) {
            current.encodeTo(writer, encoder);
            current = current.next;
        }
        allocBuffer.encodeTo(writer, encoder);
    }

    /**
     * Delegates methodMissing to String object
     *
     * @param name The name of the method
     * @param args The arguments
     * @return The return value
     */
    public Object methodMissing(String name, Object args) {
        String str = this.toString();
        return InvokerHelper.invokeMethod(str, name, args);
    }

    public Object asType(Class clazz) {
        if (clazz == String.class) {
            return toString();
        } else if (clazz == char[].class) {
            return toCharArray();
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            return asBoolean();
        } else {
            return StringGroovyMethods.asType(toString(), clazz);
        }
    }

    private static final class SavedEncoder implements Encoder {

        private CodecIdentifier codecIdentifier;
        private boolean safe;

        public SavedEncoder(String codecName, boolean safe) {
            this.codecIdentifier = new DefaultCodecIdentifier(codecName);
            this.safe = safe;
        }

        public CodecIdentifier getCodecIdentifier() {
            return codecIdentifier;
        }

        public boolean isSafe() {
            return safe;
        }

        public Object encode(Object o) {
            throw new UnsupportedOperationException("encode isn't supported for SavedEncoder");
        }

        public void markEncoded(CharSequence string) {
            throw new UnsupportedOperationException("markEncoded isn't supported for SavedEncoder");
        }

        public boolean isApplyToSafelyEncoded() {
            return false;
        }
    }

    /**
     * This is the java.io.Writer implementation for StreamCharBuffer
     *
     * @author Lari Hotari, Sagire Software Oy
     */
    public final class StreamCharBufferWriter extends Writer implements EncodedAppenderFactory, EncodedAppenderWriterFactory {

        boolean closed = false;
        int writerUsedCounter = 0;
        boolean increaseCounter = true;
        EncodedAppender encodedAppender;

        @Override
        public void write(final char[] b, final int off, final int len) throws IOException {
            write(null, b, off, len);
        }

        private void write(EncodingState encodingState, final char[] b, final int off, final int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            }

            if ((off < 0) || (off > b.length) || (len < 0) ||
                    ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            }

            if (len == 0) {
                return;
            }

            markUsed();
            if (shouldWriteDirectly(len)) {
                appendCharBufferChunk(encodingState, true, true);
                startUsingConnectedWritersWriter();
                connectedWritersWriter.write(b, off, len);
            } else {
                int charsLeft = len;
                int currentOffset = off;
                while (charsLeft > 0) {
                    int spaceLeft = allocateSpace(encodingState);
                    int writeChars = Math.min(spaceLeft, charsLeft);
                    allocBuffer.write(b, currentOffset, writeChars);
                    charsLeft -= writeChars;
                    currentOffset += writeChars;
                }
            }
        }

        private boolean shouldWriteDirectly(final int len) {
            if (!isConnectedMode()) {
                return false;
            }

            if (!(writeDirectlyToConnectedMinSize >= 0 && len >= writeDirectlyToConnectedMinSize)) {
                return false;
            }

            return isNextChunkBigEnough(len);
        }

        private boolean isNextChunkBigEnough(final int len) {
            return (len > getNewChunkMinSize());
        }

        private int getDirectChunkMinSize() {
            if (!isConnectedMode()) {
                return -1;
            }
            if (writeDirectlyToConnectedMinSize >= 0) {
                return writeDirectlyToConnectedMinSize;
            }

            return getNewChunkMinSize();
        }

        private int getNewChunkMinSize() {
            if (chunkMinSize <= 0 || allocBuffer.charsUsed() == 0 || allocBuffer.charsUsed() >= chunkMinSize) {
                return 0;
            }
            return allocBuffer.spaceLeft(null);
        }

        @Override
        public void write(final String str) throws IOException {
            write(null, str, 0, str.length());
        }

        @Override
        public void write(final String str, final int off, final int len) throws IOException {
            write(null, str, off, len);
        }

        private void write(EncodingState encodingState, final String str, final int off, final int len) throws IOException {
            if (len == 0) {
                return;
            }
            markUsed();
            if (shouldWriteDirectly(len)) {
                appendCharBufferChunk(encodingState, true, false);
                startUsingConnectedWritersWriter();
                connectedWritersWriter.write(str, off, len);
            } else if (len >= subStringChunkMinSize && isNextChunkBigEnough(len)) {
                appendStringChunk(encodingState, str, off, len);
            } else {
                int charsLeft = len;
                int currentOffset = off;
                while (charsLeft > 0) {
                    int spaceLeft = allocateSpace(encodingState);
                    int writeChars = Math.min(spaceLeft, charsLeft);
                    allocBuffer.writeString(str, currentOffset, writeChars);
                    charsLeft -= writeChars;
                    currentOffset += writeChars;
                }
            }
        }

        public void write(StreamCharBuffer subBuffer) throws IOException {
            write(subBuffer, null);
        }

        public void write(StreamCharBuffer subBuffer, List<Encoder> encoders) throws IOException {
            markUsed();
            int directChunkMinSize = getDirectChunkMinSize();
            if (encoders == null
                    && (directChunkMinSize == 0 || (directChunkMinSize != -1 && subBuffer
                    .isSizeLarger(directChunkMinSize)))) {
                appendCharBufferChunk(null, true, false);
                startUsingConnectedWritersWriter();
                subBuffer.writeToImpl(connectedWritersWriter, false, false);
            } else if (!appendSubBuffer(subBuffer, encoders)) {
                ChainedEncoders.chainEncode(subBuffer, this.getEncodedAppender(), encoders);
            }
        }

        boolean appendSubBuffer(StreamCharBuffer subBuffer, List<Encoder> encoders) throws IOException {
            if (isAllowSubBuffers() && subBuffer.isPreferSubChunkWhenWritingToOtherBuffer()
                    || subBuffer.isSizeLarger(Math.max(subBufferChunkMinSize, getNewChunkMinSize()))) {
                if (subBuffer.isPreferSubChunkWhenWritingToOtherBuffer()) {
                    StreamCharBuffer.this.setPreferSubChunkWhenWritingToOtherBuffer(true);
                }
                markUsed();
                appendStreamCharBufferChunk(subBuffer, encoders);
                subBuffer.addParentBuffer(StreamCharBuffer.this);
                return true;
            }
            return false;
        }

        @Override
        public Writer append(final CharSequence csq, final int start, final int end)
                throws IOException {
            markUsed();
            if (csq == null) {
                write("null");
            } else {
                appendCharSequence(null, csq, start, end);
            }
            return this;
        }

        protected void appendCharSequence(final EncodingState encodingState, final CharSequence csq, final int start, final int end) throws IOException {
            final Class<?> csqClass = csq.getClass();
            if (csqClass == String.class || csqClass == StringBuffer.class || csqClass == StringBuilder.class || csq instanceof CharArrayAccessible) {
                int len = end - start;
                int charsLeft = len;
                int currentOffset = start;
                while (charsLeft > 0) {
                    int spaceLeft = allocateSpace(encodingState);
                    int writeChars = Math.min(spaceLeft, charsLeft);
                    if (csqClass == String.class) {
                        allocBuffer.writeString((String) csq, currentOffset, writeChars);
                    } else if (csqClass == StringBuffer.class) {
                        allocBuffer.writeStringBuffer((StringBuffer) csq, currentOffset, writeChars);
                    } else if (csqClass == StringBuilder.class) {
                        allocBuffer.writeStringBuilder((StringBuilder) csq, currentOffset, writeChars);
                    } else if (csq instanceof CharArrayAccessible) {
                        allocBuffer.writeCharArrayAccessible((CharArrayAccessible) csq, currentOffset, writeChars);
                    }
                    charsLeft -= writeChars;
                    currentOffset += writeChars;
                }
            } else {
                String str = csq.subSequence(start, end).toString();
                write(encodingState, str, 0, str.length());
            }
        }

        @Override
        public Writer append(final CharSequence csq) throws IOException {
            markUsed();
            if (csq == null) {
                write("null");
            } else {
                append(csq, 0, csq.length());

            }
            return this;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            flushWriter(true);
        }

        public boolean isClosed() {
            return closed;
        }

        public boolean isUsed() {
            return writerUsedCounter > 0;
        }

        public void markUsed() {
            if (increaseCounter) {
                writerUsedCounter++;
                if (!hasReaders) {
                    increaseCounter = false;
                }
            }
        }

        public int resetUsed() {
            int prevUsed = writerUsedCounter;
            writerUsedCounter = 0;
            increaseCounter = true;
            return prevUsed;
        }

        @Override
        public void write(final int b) throws IOException {
            markUsed();
            allocateSpace(null);
            allocBuffer.write((char) b);
        }

        void flushWriter(boolean forceFlush) throws IOException {
            if (isConnectedMode()) {
                flushToConnected(forceFlush);
            }
            notifyBufferChange();
        }

        public StreamCharBuffer getBuffer() {
            return StreamCharBuffer.this;
        }

        public void append(EncodingState encodingState, char character) throws IOException {
            markUsed();
            allocateSpace(isNotConnectedToEncoderAwareWriters() || encodingState == null ? EncodingStateImpl.UNDEFINED_ENCODING_STATE : encodingState);
            allocBuffer.write(character);
        }

        public Writer getWriterForEncoder(Encoder encoder, EncodingStateRegistry encodingStateRegistry) {
            return StreamCharBuffer.this.getWriterForEncoder(encoder, encodingStateRegistry);
        }

        public EncodedAppender getEncodedAppender() {
            if (encodedAppender == null) {
                encodedAppender = new StreamCharBufferEncodedAppender(this);
            }
            return encodedAppender;
        }

        @Override
        public void flush() throws IOException {
            flushWriter(false);
        }
    }

    private static final class StreamCharBufferEncodedAppender extends AbstractEncodedAppender {

        StreamCharBufferWriter writer;

        StreamCharBufferEncodedAppender(StreamCharBufferWriter writer) {
            this.writer = writer;
        }

        public StreamCharBufferWriter getWriter() {
            return writer;
        }

        @Override
        public void flush() throws IOException {
            writer.flush();
        }

        @Override
        protected void write(EncodingState encodingState, char[] b, int off, int len) throws IOException {
            writer.write(encodingState, b, off, len);
        }

        @Override
        protected void write(EncodingState encodingState, String str, int off, int len) throws IOException {
            writer.write(encodingState, str, off, len);

        }

        @Override
        protected void appendCharSequence(EncodingState encodingState, CharSequence str, int start, int end)
                throws IOException {
            writer.appendCharSequence(encodingState, str, start, end);
        }

        public void close() throws IOException {
            writer.close();
        }
    }

    /**
     * This is the java.io.Reader implementation for StreamCharBuffer
     *
     * @author Lari Hotari, Sagire Software Oy
     */
    public final class StreamCharBufferReader extends Reader {

        boolean eofException = false;
        int eofReachedCounter = 0;
        ChunkReader chunkReader;
        ChunkReader lastChunkReader;
        boolean removeAfterReading;

        public StreamCharBufferReader(boolean remove) {
            removeAfterReading = remove;
        }

        private int prepareRead(int len) {
            if (hasReaders && eofReachedCounter != 0) {
                if (eofReachedCounter != writer.writerUsedCounter) {
                    eofReachedCounter = 0;
                    eofException = false;
                    repositionChunkReader();
                }
            }
            if (chunkReader == null && eofReachedCounter == 0) {
                if (firstChunk != null) {
                    chunkReader = firstChunk.getChunkReader(removeAfterReading);
                    if (removeAfterReading) {
                        firstChunk.subtractFromTotalCount();
                    }
                } else {
                    chunkReader = new AllocatedBufferReader(allocBuffer, removeAfterReading);
                }
            }
            int available = 0;
            if (chunkReader != null) {
                available = chunkReader.getReadLenLimit(len);
                while (available == 0 && chunkReader != null) {
                    chunkReader = chunkReader.next();
                    if (chunkReader != null) {
                        available = chunkReader.getReadLenLimit(len);
                    } else {
                        available = 0;
                    }
                }
            }
            if (chunkReader == null) {
                if (hasReaders) {
                    eofReachedCounter = writer.writerUsedCounter;
                } else {
                    eofReachedCounter = 1;
                }
            } else if (hasReaders) {
                lastChunkReader = chunkReader;
            }
            return available;
        }

        /* adds support for reading and writing simultaneously in the same thread */
        private void repositionChunkReader() {
            if (lastChunkReader instanceof AllocatedBufferReader) {
                if (lastChunkReader.isValid()) {
                    chunkReader = lastChunkReader;
                } else {
                    AllocatedBufferReader allocBufferReader = (AllocatedBufferReader) lastChunkReader;
                    // find out what is the CharBufferChunk that was read by the AllocatedBufferReader already
                    int currentPosition = allocBufferReader.position;
                    AbstractChunk chunk = lastChunk;
                    while (chunk != null && chunk.writerUsedCounter >= lastChunkReader.getWriterUsedCounter()) {
                        if (chunk instanceof CharBufferChunk) {
                            CharBufferChunk charBufChunk = (CharBufferChunk) chunk;
                            if (charBufChunk.allocatedBufferId == allocBufferReader.parent.id) {
                                if (currentPosition >= charBufChunk.offset && currentPosition <= charBufChunk.lastposition) {
                                    CharBufferChunkReader charBufChunkReader = (CharBufferChunkReader) charBufChunk.getChunkReader(removeAfterReading);
                                    int oldpointer = charBufChunkReader.pointer;
                                    // skip the already chars
                                    charBufChunkReader.pointer = currentPosition;
                                    if (removeAfterReading) {
                                        int diff = charBufChunkReader.pointer - oldpointer;
                                        totalCharsInList -= diff;
                                        charBufChunk.subtractFromTotalCount();
                                    }
                                    chunkReader = charBufChunkReader;
                                    break;
                                }
                            }
                        }
                        chunk = chunk.prev;
                    }
                }
            }
        }

        @Override
        public boolean ready() throws IOException {
            return true;
        }

        @Override
        public int read(final char[] b, final int off, final int len) throws IOException {
            return readImpl(b, off, len);
        }

        int readImpl(final char[] b, final int off, final int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            }

            if ((off < 0) || (off > b.length) || (len < 0) ||
                    ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            }

            if (len == 0) {
                return 0;
            }

            int charsLeft = len;
            int currentOffset = off;
            int readChars = prepareRead(charsLeft);
            if (eofException) {
                throw new EOFException();
            }

            int totalCharsRead = 0;
            while (charsLeft > 0 && readChars > 0) {
                chunkReader.read(b, currentOffset, readChars);
                charsLeft -= readChars;
                currentOffset += readChars;
                totalCharsRead += readChars;
                if (charsLeft > 0) {
                    readChars = prepareRead(charsLeft);
                }
            }

            if (totalCharsRead > 0) {
                return totalCharsRead;
            }

            eofException = true;
            return -1;
        }

        @Override
        public void close() throws IOException {
            // do nothing
        }

        public StreamCharBuffer getBuffer() {
            return StreamCharBuffer.this;
        }

        public int getReadLenLimit(int askedAmount) {
            return prepareRead(askedAmount);
        }
    }

    abstract class AbstractChunk implements StreamEncodeable, StreamingEncoderWritable {

        AbstractChunk next;
        AbstractChunk prev;
        int writerUsedCounter;
        EncodingState encodingState;

        public AbstractChunk() {
            if (hasReaders) {
                writerUsedCounter = writer.writerUsedCounter;
            } else {
                writerUsedCounter = 1;
            }
        }

        public abstract void writeTo(Writer target) throws IOException;

        public abstract ChunkReader getChunkReader(boolean removeAfterReading);

        public abstract int size();

        public int getWriterUsedCounter() {
            return writerUsedCounter;
        }

        public void subtractFromTotalCount() {
            totalCharsInList -= size();
        }

        public EncodingState getEncodingState() {
            return encodingState;
        }

        public void setEncodingState(EncodingState encodingState) {
            this.encodingState = encodingState;
        }
    }

    // keep read state in this class
    abstract static class ChunkReader {

        public abstract int read(char[] ch, int off, int len) throws IOException;

        public abstract int getReadLenLimit(int askedAmount);

        public abstract ChunkReader next();

        public abstract int getWriterUsedCounter();

        public abstract boolean isValid();
    }

    final class AllocatedBuffer {

        private int id = allocatedBufferIdSequence++;
        private int size;
        private char[] buffer;
        private int used = 0;
        private int chunkStart = 0;
        private EncodingState encodingState;
        private EncodingState nextEncoders;

        public AllocatedBuffer(int size) {
            this.size = size;
            buffer = new char[size];
        }

        public void clear() {
            reuseBuffer(null);
        }

        public int charsUsed() {
            return used - chunkStart;
        }

        public void writeTo(Writer target) throws IOException {
            if (used - chunkStart > 0) {
                target.write(buffer, chunkStart, used - chunkStart);
            }
        }

        public void reuseBuffer(EncodingState encodingState) {
            used = 0;
            chunkStart = 0;
            this.encodingState = null;
            this.nextEncoders = encodingState;
        }

        public int chunkSize() {
            return buffer.length;
        }

        public int spaceLeft(EncodingState encodingState) {
            if (encodingState == null) {
                encodingState = EncodingStateImpl.UNDEFINED_ENCODING_STATE;
            }
            if (this.encodingState != null && (encodingState == null || !this.encodingState.equals(encodingState)) && hasChunk() && !isNotConnectedToEncoderAwareWriters()) {
                addChunk(allocBuffer.createChunk());
                this.encodingState = null;
            }
            this.nextEncoders = encodingState;
            return size - used;
        }

        private void applyEncoders() throws IOException {
            if (encodingState == nextEncoders) {
                return;
            }
            if (encodingState != null && !isNotConnectedToEncoderAwareWriters() && (nextEncoders == null || !encodingState.equals(nextEncoders))) {
                throw new IOException("Illegal operation in AllocatedBuffer");
            }
            encodingState = nextEncoders;
        }

        public boolean write(final char ch) throws IOException {
            if (used < size) {
                applyEncoders();
                buffer[used++] = ch;
                return true;
            }

            return false;
        }

        public void write(final char[] ch, final int off, final int len) throws IOException {
            applyEncoders();
            arrayCopy(ch, off, buffer, used, len);
            used += len;
        }

        public void writeString(final String str, final int off, final int len) throws IOException {
            applyEncoders();
            str.getChars(off, off + len, buffer, used);
            used += len;
        }

        public void writeStringBuilder(final StringBuilder stringBuilder, final int off, final int len) throws IOException {
            applyEncoders();
            stringBuilder.getChars(off, off + len, buffer, used);
            used += len;
        }

        public void writeStringBuffer(final StringBuffer stringBuffer, final int off, final int len) throws IOException {
            applyEncoders();
            stringBuffer.getChars(off, off + len, buffer, used);
            used += len;
        }

        public void writeCharArrayAccessible(final CharArrayAccessible charArrayAccessible, final int off, final int len) throws IOException {
            applyEncoders();
            charArrayAccessible.getChars(off, off + len, buffer, used);
            used += len;
        }

        /**
         * Creates a new chunk from the content written to the buffer (used before adding StringChunk or StreamCharBufferChunk).
         *
         * @return the chunk
         */
        public CharBufferChunk createChunk() {
            CharBufferChunk chunk = new CharBufferChunk(id, buffer, chunkStart, used - chunkStart);
            chunk.setEncodingState(encodingState);
            chunkStart = used;
            return chunk;
        }

        public boolean hasChunk() {
            return (used > chunkStart);
        }

        public void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            if (used - chunkStart > 0) {
                appender.append(encoder, encodingState, buffer, chunkStart, used - chunkStart);
            }
        }

        public EncodingState getEncodingState() {
            return encodingState;
        }

        public void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            if (used - chunkStart > 0) {
                encoder.encodeToWriter(buffer, chunkStart, used - chunkStart, writer, getEncodingState());
            }
        }
    }

    /**
     * The data in the buffer is stored in a linked list of StreamCharBufferChunks.
     * <p>
     * This class contains data & read/write state for the "chunk level".
     * It contains methods for reading & writing to the chunk level.
     * <p>
     * Underneath the chunk is one more level, the StringChunkGroup + StringChunk.
     * StringChunk makes it possible to directly store the java.lang.String objects.
     *
     * @author Lari Hotari
     *
     */
    class CharBufferChunk extends AbstractChunk {

        int allocatedBufferId;
        char[] buffer;
        int offset;
        int lastposition;
        int length;

        public CharBufferChunk(int allocatedBufferId, char[] buffer, int offset, int len) {
            super();
            this.allocatedBufferId = allocatedBufferId;
            this.buffer = buffer;
            this.offset = offset;
            this.lastposition = offset + len;
            this.length = len;
        }

        @Override
        public void writeTo(final Writer target) throws IOException {
            target.write(buffer, offset, length);
        }

        @Override
        public ChunkReader getChunkReader(boolean removeAfterReading) {
            return new CharBufferChunkReader(this, removeAfterReading);
        }

        @Override
        public int size() {
            return length;
        }

        public boolean isSingleBuffer() {
            return offset == 0 && length == buffer.length;
        }

        @Override
        public void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            appender.append(encoder, getEncodingState(), buffer, offset, length);
        }

        @Override
        public void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            encoder.encodeToWriter(buffer, offset, length, writer, getEncodingState());
        }
    }

    class MultipartStringChunk extends StringChunk {

        EncodingStatePart firstPart = null;
        EncodingStatePart lastPart = null;

        public MultipartStringChunk(String str) {
            super(str, 0, str.length());
        }

        @Override
        public void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            if (firstPart != null) {
                EncodingStatePart current = firstPart;
                int offset = 0;
                char[] buf = StringCharArrayAccessor.getValue(str);
                while (current != null) {
                    appender.append(encoder, current.encodingState, buf, offset, current.len);
                    offset += current.len;
                    current = current.next;
                }
            } else {
                super.encodeTo(appender, encoder);
            }
        }

        @Override
        public void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            if (firstPart != null) {
                EncodingStatePart current = firstPart;
                int offset = 0;
                char[] buf = StringCharArrayAccessor.getValue(str);
                while (current != null) {
                    encoder.encodeToWriter(buf, offset, current.len, writer, current.encodingState);
                    offset += current.len;
                    current = current.next;
                }
            } else {
                super.encodeTo(writer, encoder);
            }
        }

        public boolean isSingleEncoding() {
            return (firstPart == lastPart);
        }

        public int partCount() {
            int partCount = 0;
            EncodingStatePart current = firstPart;
            while (current != null) {
                partCount++;
                current = current.next;
            }
            return partCount;
        }

        public void appendEncodingStatePart(EncodingStatePart current) {
            if (firstPart == null) {
                firstPart = current;
                lastPart = current;
            } else {
                lastPart.next = current;
                lastPart = current;
            }
        }
    }

    class MultipartCharBufferChunk extends CharBufferChunk {

        EncodingStatePart firstPart = null;
        EncodingStatePart lastPart = null;

        public MultipartCharBufferChunk(char[] buffer) {
            super(-1, buffer, 0, buffer.length);
        }

        @Override
        public void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            if (firstPart != null) {
                EncodingStatePart current = firstPart;
                int offset = 0;
                while (current != null) {
                    appender.append(encoder, current.encodingState, buffer, offset, current.len);
                    offset += current.len;
                    current = current.next;
                }
            } else {
                super.encodeTo(appender, encoder);
            }
        }

        public MultipartStringChunk asStringChunk() {
            String str = StringCharArrayAccessor.createString(buffer);
            MultipartStringChunk chunk = new MultipartStringChunk(str);
            chunk.firstPart = firstPart;
            chunk.lastPart = lastPart;
            return chunk;
        }
    }

    static final class EncodingStatePart {

        EncodingStatePart next;
        EncodingState encodingState;
        int len = -1;
    }

    abstract class AbstractChunkReader extends ChunkReader {

        private AbstractChunk parentChunk;
        private boolean removeAfterReading;

        public AbstractChunkReader(AbstractChunk parentChunk, boolean removeAfterReading) {
            this.parentChunk = parentChunk;
            this.removeAfterReading = removeAfterReading;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public ChunkReader next() {
            if (removeAfterReading) {
                if (firstChunk == parentChunk) {
                    firstChunk = null;
                }
                if (lastChunk == parentChunk) {
                    lastChunk = null;
                }
            }
            AbstractChunk nextChunk = parentChunk.next;
            if (nextChunk != null) {
                if (removeAfterReading) {
                    if (firstChunk == null) {
                        firstChunk = nextChunk;
                    }
                    if (lastChunk == null) {
                        lastChunk = nextChunk;
                    }
                    nextChunk.prev = null;
                    nextChunk.subtractFromTotalCount();
                }
                return nextChunk.getChunkReader(removeAfterReading);
            }

            return new AllocatedBufferReader(allocBuffer, removeAfterReading);
        }

        @Override
        public int getWriterUsedCounter() {
            return parentChunk.getWriterUsedCounter();
        }
    }

    final class CharBufferChunkReader extends AbstractChunkReader {

        CharBufferChunk parent;
        int pointer;

        public CharBufferChunkReader(CharBufferChunk parent, boolean removeAfterReading) {
            super(parent, removeAfterReading);
            this.parent = parent;
            pointer = parent.offset;
        }

        @Override
        public int read(final char[] ch, final int off, final int len) throws IOException {
            arrayCopy(parent.buffer, pointer, ch, off, len);
            pointer += len;
            return len;
        }

        @Override
        public int getReadLenLimit(int askedAmount) {
            return Math.min(parent.lastposition - pointer, askedAmount);
        }
    }

    /**
     * StringChunk is a wrapper for java.lang.String.
     * <p>
     * It also keeps state of the read offset and the number of unread characters.
     * <p>
     * There's methods that StringChunkGroup uses for reading data.
     *
     * @author Lari Hotari
     *
     */
    class StringChunk extends AbstractChunk {

        String str;
        int offset;
        int lastposition;
        int length;

        public StringChunk(String str, int offset, int length) {
            this.str = str;
            this.offset = offset;
            this.length = length;
            this.lastposition = offset + length;
        }

        @Override
        public ChunkReader getChunkReader(boolean removeAfterReading) {
            return new StringChunkReader(this, removeAfterReading);
        }

        @Override
        public void writeTo(Writer target) throws IOException {
            target.write(str, offset, length);
        }

        @Override
        public int size() {
            return length;
        }

        public boolean isSingleBuffer() {
            return offset == 0 && length == str.length();
        }

        @Override
        public void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            appender.append(encoder, getEncodingState(), str, offset, length);
        }

        @Override
        public void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            encoder.encodeToWriter(toCharSequence(), 0, length, writer, getEncodingState());
        }

        public CharSequence toCharSequence() {
            if (isSingleBuffer()) {
                return str;
            } else {
                return CharSequences.createCharSequence(str, offset, length);
            }
        }
    }

    final class StringChunkReader extends AbstractChunkReader {

        StringChunk parent;
        int position;

        public StringChunkReader(StringChunk parent, boolean removeAfterReading) {
            super(parent, removeAfterReading);
            this.parent = parent;
            this.position = parent.offset;
        }

        @Override
        public int read(final char[] ch, final int off, final int len) {
            parent.str.getChars(position, (position + len), ch, off);
            position += len;
            return len;
        }

        @Override
        public int getReadLenLimit(int askedAmount) {
            return Math.min(parent.lastposition - position, askedAmount);
        }
    }

    final class StreamCharBufferSubChunk extends AbstractChunk {

        int cachedSize;
        int encodedSourceChangesCounter = -1;
        private StreamCharBuffer sourceBuffer;
        private List<Encoder> encoders;
        private StreamCharBuffer encodedBuffer;

        public StreamCharBufferSubChunk(StreamCharBuffer sourceBuffer, List<Encoder> encoders) {
            this.sourceBuffer = sourceBuffer;
            this.encoders = encoders;
            if (encoders == null && hasQuicklyCalcutableSize() && sourceBuffer.hasQuicklyCalcutableSize()) {
                cachedSize = sourceBuffer.size();
                if (totalCharsInDynamicChunks == -1) {
                    totalCharsInDynamicChunks = 0;
                }
                totalCharsInDynamicChunks += cachedSize;
            } else {
                totalCharsInDynamicChunks = -1;
                cachedSize = -1;
            }
            if (encoders == null || sourceBuffer.isEmpty()) {
                encodedBuffer = sourceBuffer;
                encodedSourceChangesCounter = sourceBuffer.getBufferChangesCounter();
            }
        }

        @Override
        public ChunkReader getChunkReader(boolean removeAfterReading) {
            return new StreamCharBufferSubChunkReader(this, removeAfterReading);
        }

        @Override
        public int size() {
            if (cachedSize == -1) {
                cachedSize = getEncodedBuffer().size();
            }
            return cachedSize;
        }

        public boolean hasCachedSize() {
            return (cachedSize != -1);
        }

        public StreamCharBuffer getSourceBuffer() {
            return sourceBuffer;
        }

        @Override
        public void writeTo(Writer target) throws IOException {
            if (encoders == null || hasEncodedBufferAvailable() || !hasOnlyStreamingEncoders()) {
                getEncodedBuffer().writeTo(target);
            } else {
                EncodedAppender appender;
                if (target instanceof EncodedAppender) {
                    appender = ((EncodedAppender) target);
                } else if (target instanceof EncodedAppenderFactory) {
                    appender = ((EncodedAppenderFactory) target).getEncodedAppender();
                } else {
                    appender = new WriterEncodedAppender(target);
                }
                ChainedEncoders.chainEncode(getSourceBuffer(), appender, encoders);
            }
        }

        @Override
        public void encodeTo(EncodedAppender appender, Encoder encodeToEncoder) throws IOException {
            if (appender instanceof StreamCharBufferEncodedAppender
                    && getSourceBuffer().isPreferSubChunkWhenWritingToOtherBuffer()
                    && ((StreamCharBufferEncodedAppender) appender).getWriter().getBuffer().isAllowSubBuffers()) {
                List<Encoder> nextEncoders = ChainedEncoders.appendEncoder(encoders, encodeToEncoder);
                ((StreamCharBufferEncodedAppender) appender).getWriter().write(getSourceBuffer(), nextEncoders);
            } else {
                if (hasEncodedBufferAvailable() || !hasOnlyStreamingEncoders()) {
                    appender.append(encodeToEncoder, getEncodedBuffer());
                } else {
                    ChainedEncoders.chainEncode(getSourceBuffer(), appender, ChainedEncoders.appendEncoder(encoders, encodeToEncoder));
                }
            }
        }

        protected boolean hasOnlyStreamingEncoders() {
            if (encoders == null || encoders.isEmpty()) {
                return false;
            }
            for (Encoder encoder : encoders) {
                if (!(encoder instanceof StreamingEncoder)) {
                    return false;
                }
            }
            return true;
        }

        public StreamCharBuffer getEncodedBuffer() {
            if (!hasEncodedBufferAvailable()) {
                if (encoders == null || sourceBuffer.isEmpty()) {
                    encodedBuffer = sourceBuffer;
                    encodedSourceChangesCounter = sourceBuffer.getBufferChangesCounter();
                } else {
                    encodedBuffer = new StreamCharBuffer(chunkSize, growProcent, maxChunkSize);
                    encodedBuffer.setAllowSubBuffers(isAllowSubBuffers());
                    encodedBuffer.setNotifyParentBuffersEnabled(getSourceBuffer().isNotifyParentBuffersEnabled());
                    encodeToEncodedBuffer();
                }
            }
            return encodedBuffer;
        }

        private void encodeToEncodedBuffer() {
            boolean previousAllowSubBuffer = encodedBuffer.isAllowSubBuffers();
            encodedBuffer.setAllowSubBuffers(false);
            encodedSourceChangesCounter = sourceBuffer.getBufferChangesCounter();
            try {
                ChainedEncoders.chainEncode(getSourceBuffer(), encodedBuffer.writer.getEncodedAppender(), encoders);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            encodedBuffer.setAllowSubBuffers(previousAllowSubBuffer);
            encodedBuffer.setPreferSubChunkWhenWritingToOtherBuffer(getSourceBuffer().isPreferSubChunkWhenWritingToOtherBuffer());
            encodedBuffer.notifyBufferChange();
        }

        protected boolean hasEncodedBufferAvailable() {
            return encodedBuffer != null && encodedSourceChangesCounter == sourceBuffer.getBufferChangesCounter();
        }

        public boolean resetSubBuffer() {
            if (cachedSize != -1 || encodedBuffer != sourceBuffer) {
                cachedSize = -1;
                encodedSourceChangesCounter = -1;
                if (encodedBuffer != sourceBuffer && encodedBuffer != null) {
                    encodedBuffer.clear();
                    encodeToEncodedBuffer();
                }
                return true;
            }
            return false;
        }

        @Override
        public void subtractFromTotalCount() {
            totalCharsInDynamicChunks = -1;
            dynamicChunkMap.remove(sourceBuffer.bufferKey);
        }

        @Override
        public void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            if (hasEncodedBufferAvailable() || !hasOnlyStreamingEncoders() || encoders == null) {
                getEncodedBuffer().encodeTo(writer, encoder);
            } else {
                List<StreamingEncoder> streamingEncoders = new ArrayList<StreamingEncoder>(encoders.size());
                for (Encoder e : encoders) {
                    streamingEncoders.add((StreamingEncoder) e);
                }
                getSourceBuffer().encodeTo(writer, encoder.createChainingEncodesToWriter(streamingEncoders, true));
            }
        }
    }

    final class StreamCharBufferSubChunkReader extends AbstractChunkReader {

        StreamCharBufferSubChunk parent;
        private StreamCharBufferReader reader;

        public StreamCharBufferSubChunkReader(StreamCharBufferSubChunk parent, boolean removeAfterReading) {
            super(parent, removeAfterReading);
            this.parent = parent;
            reader = (StreamCharBufferReader) parent.getEncodedBuffer().getReader();
        }

        @Override
        public int getReadLenLimit(int askedAmount) {
            return reader.getReadLenLimit(askedAmount);
        }

        @Override
        public int read(char[] ch, int off, int len) throws IOException {
            return reader.read(ch, off, len);
        }
    }

    final class AllocatedBufferReader extends ChunkReader {

        AllocatedBuffer parent;
        int position;
        int writerUsedCounter;
        boolean removeAfterReading;

        public AllocatedBufferReader(AllocatedBuffer parent, boolean removeAfterReading) {
            this.parent = parent;
            position = parent.chunkStart;
            if (hasReaders) {
                writerUsedCounter = writer.writerUsedCounter;
            } else {
                writerUsedCounter = 1;
            }
            this.removeAfterReading = removeAfterReading;
        }

        @Override
        public int getReadLenLimit(int askedAmount) {
            return Math.min(parent.used - position, askedAmount);
        }

        @Override
        public int read(char[] ch, int off, int len) throws IOException {
            arrayCopy(parent.buffer, position, ch, off, len);
            position += len;
            if (removeAfterReading) {
                parent.chunkStart = position;
            }
            return len;
        }

        @Override
        public ChunkReader next() {
            return null;
        }

        @Override
        public int getWriterUsedCounter() {
            return writerUsedCounter;
        }

        @Override
        public boolean isValid() {
            return (allocBuffer == parent && (lastChunk == null || lastChunk.writerUsedCounter < writerUsedCounter));
        }
    }

    private final class FixedCharArrayEncodedAppender extends AbstractEncodedAppender {

        char[] buf;
        int count = 0;
        int currentStart = 0;
        EncodingState currentState;
        MultipartCharBufferChunk chunk;

        public FixedCharArrayEncodedAppender(int fixedSize) {
            buf = new char[fixedSize];
            chunk = new MultipartCharBufferChunk(buf);
        }

        private void checkEncodingChange(EncodingState encodingState) {
            if (encodingState == null) {
                encodingState = EncodingStateImpl.UNDEFINED_ENCODING_STATE;
            }
            if (currentState != null && !currentState.equals(encodingState)) {
                addPart();
            }
            if (currentState == null) {
                currentState = encodingState;
            }
        }

        public void finish() {
            addPart();
        }

        private void addPart() {
            if (count - currentStart > 0) {
                EncodingStatePart newPart = new EncodingStatePart();
                newPart.encodingState = currentState;
                newPart.len = count - currentStart;
                if (chunk.lastPart == null) {
                    chunk.firstPart = newPart;
                    chunk.lastPart = newPart;
                } else {
                    chunk.lastPart.next = newPart;
                    chunk.lastPart = newPart;
                }
                currentState = null;
                currentStart = count;
            }
        }

        @Override
        protected void write(EncodingState encodingState, char[] b, int off, int len) throws IOException {
            checkEncodingChange(encodingState);
            arrayCopy(b, off, buf, count, len);
            count += len;
        }

        @Override
        protected void write(EncodingState encodingState, String str, int off, int len) throws IOException {
            checkEncodingChange(encodingState);
            str.getChars(off, off + len, buf, count);
            count += len;
        }

        @Override
        protected void appendCharSequence(EncodingState encodingState, CharSequence csq, int start, int end)
                throws IOException {
            checkEncodingChange(encodingState);
            final Class<?> csqClass = csq.getClass();
            if (csqClass == String.class) {
                write(encodingState, (String) csq, start, end - start);
            } else if (csqClass == StringBuffer.class) {
                ((StringBuffer) csq).getChars(start, end, buf, count);
                count += end - start;
            } else if (csqClass == StringBuilder.class) {
                ((StringBuilder) csq).getChars(start, end, buf, count);
                count += end - start;
            } else if (csq instanceof CharArrayAccessible) {
                ((CharArrayAccessible) csq).getChars(start, end, buf, count);
                count += end - start;
            } else {
                String str = csq.subSequence(start, end).toString();
                write(encodingState, str, 0, str.length());
            }
        }

        public void close() throws IOException {
            finish();
        }
    }

    /**
     * Interface for a Writer that gets initialized if it is used
     * Can be used for passing in to "connectTo" method of StreamCharBuffer
     *
     * @author Lari Hotari
     *
     */
    public interface LazyInitializingWriter {

        Writer getWriter() throws IOException;
    }

    public interface LazyInitializingMultipleWriter extends LazyInitializingWriter {

        /**
         * initialize underlying writer
         *
         * @return false if this writer entry should be removed after calling this callback method
         */
        LazyInitializingWriter[] initializeMultiple(StreamCharBuffer buffer, boolean autoFlush) throws IOException;
    }

    final class ConnectToWriter {

        final Writer writer;
        final LazyInitializingWriter lazyInitializingWriter;
        final boolean autoFlush;
        Boolean encoderAware;

        ConnectToWriter(final Writer writer, final boolean autoFlush) {
            this.writer = writer;
            this.lazyInitializingWriter = null;
            this.autoFlush = autoFlush;
        }

        ConnectToWriter(final LazyInitializingWriter lazyInitializingWriter, final boolean autoFlush) {
            this.lazyInitializingWriter = lazyInitializingWriter;
            this.writer = null;
            this.autoFlush = autoFlush;
        }

        Writer[] getWriters() throws IOException {
            if (writer != null) {
                return new Writer[]{writer};
            } else {
                Set<Writer> writerList = resolveLazyInitializers(new HashSet<Integer>(), lazyInitializingWriter);
                return writerList.toArray(new Writer[writerList.size()]);
            }
        }

        private Set<Writer> resolveLazyInitializers(Set<Integer> resolved, LazyInitializingWriter lazyInitializingWriter) throws IOException {
            Set<Writer> writerList = Collections.emptySet();
            Integer identityHashCode = System.identityHashCode(lazyInitializingWriter);
            if (!resolved.contains(identityHashCode) && lazyInitializingWriter instanceof LazyInitializingMultipleWriter) {
                resolved.add(identityHashCode);
                writerList = new LinkedHashSet<Writer>();
                LazyInitializingWriter[] writers = ((LazyInitializingMultipleWriter) lazyInitializingWriter).initializeMultiple(StreamCharBuffer.this, autoFlush);
                for (LazyInitializingWriter writer : writers) {
                    writerList.addAll(resolveLazyInitializers(resolved, writer));
                }
            } else {
                writerList = Collections.singleton(lazyInitializingWriter.getWriter());
            }
            return writerList;
        }

        public boolean isAutoFlush() {
            return autoFlush;
        }
    }

    /**
     * Simple holder class for the connected writer
     *
     * @author Lari Hotari
     *
     */
    static final class ConnectedWriter {

        final Writer writer;
        final boolean autoFlush;
        final boolean encoderAware;

        ConnectedWriter(final Writer writer, final boolean autoFlush) {
            this.writer = writer;
            this.autoFlush = autoFlush;
            this.encoderAware = (writer instanceof EncodedAppenderFactory || writer instanceof EncodedAppenderWriterFactory);
        }

        Writer getWriter() {
            return writer;
        }

        public void flush() throws IOException {
            if (autoFlush) {
                writer.flush();
            }
        }

        public boolean isEncoderAware() {
            return encoderAware;
        }
    }

    static final class SingleOutputWriter extends ConnectedWritersWriter implements GrailsWrappedWriter {

        private final ConnectedWriter connectedWriter;
        private final Writer writer;
        private final boolean encoderAware;

        public SingleOutputWriter(ConnectedWriter connectedWriter) {
            this.connectedWriter = connectedWriter;
            this.writer = connectedWriter.getWriter();
            this.encoderAware = connectedWriter.isEncoderAware();
        }

        @Override
        public void close() throws IOException {
            // do nothing
        }

        @Override
        public void flush() throws IOException {
            connectedWriter.flush();
        }

        @Override
        public void write(final char[] cbuf, final int off, final int len) throws IOException {
            writer.write(cbuf, off, len);
        }

        @Override
        public Writer append(final CharSequence csq, final int start, final int end)
                throws IOException {
            writer.append(csq, start, end);
            return this;
        }

        @Override
        public void write(String str, int off, int len) throws IOException {
            if (!encoderAware) {
                StringCharArrayAccessor.writeStringAsCharArray(writer, str, off, len);
            } else {
                writer.write(str, off, len);
            }
        }

        @Override
        public boolean isEncoderAware() throws IOException {
            return encoderAware;
        }

        public boolean isAllowUnwrappingOut() {
            return true;
        }

        public Writer unwrap() {
            return writer;
        }

        public void markUsed() {
        }

        @Override
        public void forceFlush() throws IOException {
            writer.flush();
        }
    }

    abstract static class ConnectedWritersWriter extends Writer {

        public abstract boolean isEncoderAware() throws IOException;

        public abstract void forceFlush() throws IOException;
    }

    /**
     * delegates to several writers, used in "connectTo" mode.
     */
    static final class MultiOutputWriter extends ConnectedWritersWriter {

        final List<ConnectedWriter> connectedWriters;
        final List<Writer> writers;

        Boolean encoderAware;

        public MultiOutputWriter(final List<ConnectedWriter> connectedWriters) {
            this.connectedWriters = connectedWriters;
            this.writers = new ArrayList<Writer>(connectedWriters.size());
            for (ConnectedWriter connectedWriter : connectedWriters) {
                writers.add(connectedWriter.getWriter());
            }
        }

        @Override
        public void close() throws IOException {
            // do nothing
        }

        @Override
        public void flush() throws IOException {
            for (ConnectedWriter connectedWriter : connectedWriters) {
                connectedWriter.flush();
            }
        }

        @Override
        public void write(final char[] cbuf, final int off, final int len) throws IOException {
            for (Writer writer : writers) {
                writer.write(cbuf, off, len);
            }
        }

        @Override
        public Writer append(final CharSequence csq, final int start, final int end)
                throws IOException {
            for (Writer writer : writers) {
                writer.append(csq, start, end);
            }
            return this;
        }

        @Override
        public void write(String str, int off, int len) throws IOException {
            if (isEncoderAware()) {
                for (ConnectedWriter connectedWriter : connectedWriters) {
                    if (!connectedWriter.isEncoderAware()) {
                        StringCharArrayAccessor.writeStringAsCharArray(connectedWriter.getWriter(), str, off, len);
                    } else {
                        connectedWriter.getWriter().write(str, off, len);
                    }
                }
            } else {
                for (Writer writer : writers) {
                    writer.write(str, off, len);
                }
            }
        }

        @Override
        public boolean isEncoderAware() throws IOException {
            if (encoderAware == null) {
                encoderAware = false;
                for (ConnectedWriter writer : connectedWriters) {
                    if (writer.isEncoderAware()) {
                        encoderAware = true;
                        break;
                    }
                }
            }
            return encoderAware;
        }

        @Override
        public void forceFlush() throws IOException {
            for (Writer writer : writers) {
                writer.flush();
            }
        }
    }

    public static final class EncodedPart {

        private final EncodingState encodingState;
        private final String part;

        public EncodedPart(EncodingState encodingState, String part) {
            this.encodingState = encodingState;
            this.part = part;
        }

        public EncodingState getEncodingState() {
            return encodingState;
        }

        public String getPart() {
            return part;
        }

        @Override
        public String toString() {
            return "EncodedPart [encodingState='" + encodingState + "', part='" + part + "']";
        }
    }

    private class StreamCharBufferKey {

        StreamCharBuffer getBuffer() {
            return StreamCharBuffer.this;
        }
    }
}
