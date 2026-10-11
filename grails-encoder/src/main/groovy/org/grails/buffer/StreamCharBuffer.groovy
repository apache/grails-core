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

import java.lang.ref.SoftReference

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper
import org.codehaus.groovy.runtime.StringGroovyMethods

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import org.grails.charsequences.CharArrayAccessible
import org.grails.charsequences.CharSequences
import org.grails.encoder.AbstractEncodedAppender
import org.grails.encoder.ChainedEncoders
import org.grails.encoder.CodecIdentifier
import org.grails.encoder.DefaultCodecIdentifier
import org.grails.encoder.Encodeable
import org.grails.encoder.EncodedAppender
import org.grails.encoder.EncodedAppenderFactory
import org.grails.encoder.EncodedAppenderWriter
import org.grails.encoder.EncodedAppenderWriterFactory
import org.grails.encoder.Encoder
import org.grails.encoder.EncoderAware
import org.grails.encoder.EncodesToWriter
import org.grails.encoder.EncodingState
import org.grails.encoder.EncodingStateImpl
import org.grails.encoder.EncodingStateRegistry
import org.grails.encoder.EncodingStateRegistryLookup
import org.grails.encoder.EncodingStateRegistryLookupHolder
import org.grails.encoder.StreamEncodeable
import org.grails.encoder.StreamingEncoder
import org.grails.encoder.StreamingEncoderWritable
import org.grails.encoder.WriterEncodedAppender

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
 *
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
 *
 * Configuration values can also be changed for each instance of
 * StreamCharBuffer individually. Default values are defined with System
 * Properties.
 *
 * @author Lari Hotari, Sagire Software Oy
 */
@CompileStatic
class StreamCharBuffer extends GroovyObjectSupport implements Writable, CharSequence, Externalizable, Encodeable, StreamEncodeable, StreamingEncoderWritable, EncodedAppenderWriterFactory, Cloneable {

    private static final int EXTERNALIZABLE_VERSION = 2
    static final long serialVersionUID = EXTERNALIZABLE_VERSION
    private static final Log log = LogFactory.getLog(StreamCharBuffer)

    private static final int DEFAULT_CHUNK_SIZE = Integer.getInteger('streamcharbuffer.chunksize', 512)
    private static final int DEFAULT_MAX_CHUNK_SIZE = Integer.getInteger('streamcharbuffer.maxchunksize', 1024 * 1024)
    private static final int DEFAULT_CHUNK_SIZE_GROW_PROCENT = Integer.getInteger('streamcharbuffer.growprocent', 100)
    private static final int SUB_BUFFERCHUNK_MIN_SIZE = Integer.getInteger('streamcharbuffer.subbufferchunkminsize', 512)
    private static final int SUB_STRINGCHUNK_MIN_SIZE = Integer.getInteger('streamcharbuffer.substringchunkminsize', 512)
    private static final int WRITE_DIRECT_MIN_SIZE = Integer.getInteger('streamcharbuffer.writedirectminsize', 1024)
    private static final int CHUNK_MIN_SIZE = Integer.getInteger('streamcharbuffer.chunkminsize', 256)

    private final int firstChunkSize
    private final int growProcent
    private final int maxChunkSize
    private int subStringChunkMinSize = SUB_STRINGCHUNK_MIN_SIZE
    private int subBufferChunkMinSize = SUB_BUFFERCHUNK_MIN_SIZE
    private int writeDirectlyToConnectedMinSize = WRITE_DIRECT_MIN_SIZE
    private int chunkMinSize = CHUNK_MIN_SIZE

    private int chunkSize
    private int totalChunkSize

    private final StreamCharBufferWriter writer
    private List<ConnectToWriter> connectToWriters
    private ConnectedWritersWriter connectedWritersWriter
    private Boolean notConnectedToEncodeAwareWriters = null

    boolean preferSubChunkWhenWritingToOtherBuffer = false

    private AllocatedBuffer allocBuffer
    private AbstractChunk firstChunk
    private AbstractChunk lastChunk
    private int totalCharsInList
    private int totalCharsInDynamicChunks
    private int sizeAtLeast
    private StreamCharBufferKey bufferKey = new StreamCharBufferKey()
    private Map<StreamCharBufferKey, StreamCharBufferSubChunk> dynamicChunkMap

    private Set<SoftReference<StreamCharBufferKey>> parentBuffers
    int allocatedBufferIdSequence = 0
    int readerCount = 0
    boolean hasReaders = false
    int bufferChangesCounter = 0

    boolean notifyParentBuffersEnabled = true
    boolean subBuffersEnabled = true

    StreamCharBuffer() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_SIZE_GROW_PROCENT, DEFAULT_MAX_CHUNK_SIZE)
    }

    StreamCharBuffer(int chunkSize) {
        this(chunkSize, DEFAULT_CHUNK_SIZE_GROW_PROCENT, DEFAULT_MAX_CHUNK_SIZE)
    }

    StreamCharBuffer(int chunkSize, int growProcent) {
        this(chunkSize, growProcent, DEFAULT_MAX_CHUNK_SIZE)
    }

    StreamCharBuffer(int chunkSize, int growProcent, int maxChunkSize) {
        this.firstChunkSize = chunkSize
        this.growProcent = growProcent
        this.maxChunkSize = maxChunkSize
        writer = new StreamCharBufferWriter()
        reset(true)
    }

    private class StreamCharBufferKey {
        StreamCharBuffer getBuffer() { return StreamCharBuffer.this }
    }

    boolean isPreferSubChunkWhenWritingToOtherBuffer() {
        return preferSubChunkWhenWritingToOtherBuffer
    }

    void setPreferSubChunkWhenWritingToOtherBuffer(boolean prefer) {
        preferSubChunkWhenWritingToOtherBuffer = prefer
        notifyPreferSubChunkEnabled()
    }

    protected void notifyPreferSubChunkEnabled() {
        if (isPreferSubChunkWhenWritingToOtherBuffer() && parentBuffers != null && isNotifyParentBuffersEnabled()) {
            for (StreamCharBuffer parentBuffer : getCurrentParentBuffers()) {
                if (!parentBuffer.isPreferSubChunkWhenWritingToOtherBuffer()) {
                    parentBuffer.setPreferSubChunkWhenWritingToOtherBuffer(true)
                }
            }
        }
    }

    final void reset() {
        reset(true)
    }

    /**
     * resets the state of this buffer (empties it)
     *
     * @param resetChunkSize
     */
    final void reset(boolean resetChunkSize) {
        markBufferChanged()
        firstChunk = null
        lastChunk = null
        totalCharsInList = 0
        totalCharsInDynamicChunks = -1
        sizeAtLeast = -1
        if (resetChunkSize) {
            chunkSize = firstChunkSize
            totalChunkSize = 0
        }
        if (allocBuffer == null) {
            allocBuffer = new AllocatedBuffer(chunkSize)
        } else {
            allocBuffer.clear()
        }
        if (dynamicChunkMap == null) {
            dynamicChunkMap = new HashMap<>()
        } else {
            dynamicChunkMap.clear()
        }
    }

    /**
     * Clears the buffer and notifies the parents of this buffer of the change.
     */
    final void clear() {
        reset()
        notifyBufferChange()
    }

    /**
     * Connect this buffer to a target Writer.
     *
     * When the buffer (a chunk) get filled up, it will automaticly write it's content to the Writer
     *
     * @param w
     */
    final void connectTo(Writer w) {
        connectTo(w, true)
    }

    final void connectTo(Writer w, boolean autoFlush) {
        initConnected()
        connectToWriters.add(new ConnectToWriter(w, autoFlush))
        initConnectedWritersWriter()
    }

    final void encodeInStreamingModeTo(final EncoderAware encoderLookup, final EncodingStateRegistryLookup encodingStateRegistryLookup, boolean autoFlush, final Writer w) {
        encodeInStreamingModeTo(encoderLookup, encodingStateRegistryLookup, autoFlush, new LazyInitializingWriter() {
            Writer getWriter() throws IOException {
                return w
            }
        })
    }

    final void encodeInStreamingModeTo(final EncoderAware encoderLookup, final EncodingStateRegistryLookup encodingStateRegistryLookup, final boolean autoFlush, final LazyInitializingWriter... writers) {
        LazyInitializingWriter encodingWriterInitializer = createEncodingInitializer(encoderLookup,
                encodingStateRegistryLookup, writers)
        connectTo(encodingWriterInitializer, autoFlush)
    }

    LazyInitializingWriter createEncodingInitializer(final EncoderAware encoderLookup,
            final EncodingStateRegistryLookup encodingStateRegistryLookup, final LazyInitializingWriter... writers) {
        LazyInitializingWriter encodingWriterInitializer = new LazyInitializingMultipleWriter() {
            Writer lazyWriter

            Writer getWriter() throws IOException {
                return lazyWriter
            }

            LazyInitializingWriter[] initializeMultiple(StreamCharBuffer buffer, boolean autoFlushMode) throws IOException {
                Encoder encoder = encoderLookup.getEncoder()
                if (encoder != null) {
                    EncodingStateRegistry encodingStateRegistry = encodingStateRegistryLookup.lookup()
                    StreamCharBuffer encodeBuffer = new StreamCharBuffer(StreamCharBuffer.this.chunkSize, StreamCharBuffer.this.growProcent, StreamCharBuffer.this.maxChunkSize)
                    encodeBuffer.setAllowSubBuffers(false)
                    lazyWriter = encodeBuffer.getWriterForEncoder(encoder, encodingStateRegistry)
                    for (LazyInitializingWriter w : writers) {
                        encodeBuffer.connectTo(w, autoFlushMode)
                    }
                    return new LazyInitializingWriter[]{this}
                } else {
                    return writers
                }
            }
        }
        return encodingWriterInitializer
    }

    private void initConnectedWritersWriter() {
        notConnectedToEncodeAwareWriters = null
        connectedWritersWriter = null
        setNotifyParentBuffersEnabled(false)
    }

    private void startUsingConnectedWritersWriter() throws IOException {
        if (connectedWritersWriter == null) {
            List<ConnectedWriter> connectedWriters = new ArrayList<>()

            for (ConnectToWriter connectToWriter : connectToWriters) {
                for (Writer writer : connectToWriter.getWriters()) {
                    Writer target = writer
                    if (target instanceof GrailsWrappedWriter) {
                        target = ((GrailsWrappedWriter) target).unwrap()
                    }
                    if (target == null) {
                        throw new NullPointerException('target is null')
                    }
                    connectedWriters.add(new ConnectedWriter(target, connectToWriter.isAutoFlush()))
                }
            }

            if (connectedWriters.size() > 1) {
                connectedWritersWriter = new MultiOutputWriter(connectedWriters)
            }
            else {
                connectedWritersWriter = new SingleOutputWriter(connectedWriters.get(0))
            }
        }
    }

    final void connectTo(LazyInitializingWriter w) {
        connectTo(w, true)
    }

    final void connectTo(LazyInitializingWriter w, boolean autoFlush) {
        initConnected()
        connectToWriters.add(new ConnectToWriter(w, autoFlush))
        initConnectedWritersWriter()
    }

    final void removeConnections() {
        if (connectToWriters != null) {
            connectToWriters = null
            connectedWritersWriter = null
            notConnectedToEncodeAwareWriters = null
        }
    }

    private void initConnected() {
        if (connectToWriters == null) {
            connectToWriters = new ArrayList<>(2)
        }
    }

    int getSubStringChunkMinSize() {
        return subStringChunkMinSize
    }

    /**
     * Minimum size for a String to be added as a StringChunk instead of copying content to the char[] buffer of the current StreamCharBufferChunk
     *
     * @param size
     */
    void setSubStringChunkMinSize(int size) {
        subStringChunkMinSize = size
    }

    int getSubBufferChunkMinSize() {
        return subBufferChunkMinSize
    }

    void setSubBufferChunkMinSize(int size) {
        subBufferChunkMinSize = size
    }

    int getWriteDirectlyToConnectedMinSize() {
        return writeDirectlyToConnectedMinSize
    }

    /**
     * Minimum size for a String or char[] to get written directly to connected writer (in "connectTo" mode).
     *
     * @param size
     */
    void setWriteDirectlyToConnectedMinSize(int size) {
        writeDirectlyToConnectedMinSize = size
    }

    int getChunkMinSize() {
        return chunkMinSize
    }

    void setChunkMinSize(int size) {
        chunkMinSize = size
    }

    /**
     * Writer interface for adding/writing data to the buffer.
     *
     * @return the Writer
     */
    Writer getWriter() {
        return writer
    }

    /**
     * Creates a new Reader instance for reading/consuming data from the buffer.
     * Each call creates a new instance that will keep it's reading state. There can be several readers on the buffer. (single thread only supported)
     *
     * @return the Reader
     */
    Reader getReader() {
        return getReader(false)
    }

    /**
     * Like getReader(), but when removeAfterReading is true, the read data will be removed from the buffer.
     *
     * @param removeAfterReading
     * @return the Reader
     */
    Reader getReader(boolean removeAfterReading) {
        readerCount++
        hasReaders = true
        return new StreamCharBufferReader(removeAfterReading)
    }

    /**
     * Writes the buffer content to a target java.io.Writer
     *
     * @param target
     * @throws IOException
     */
    Writer writeTo(Writer target) throws IOException {
        writeTo(target, false, false)
        return target
    }

    /**
     * Writes the buffer content to a target java.io.Writer
     *
     * @param target Writer
     * @param flushTarget calls target.flush() before finishing
     * @param emptyAfter empties the buffer if true
     * @throws IOException
     */
    void writeTo(Writer target, boolean flushTarget, boolean emptyAfter) throws IOException {
        if (target instanceof GrailsWrappedWriter) {
            GrailsWrappedWriter wrappedWriter = ((GrailsWrappedWriter) target)
            if (wrappedWriter.isAllowUnwrappingOut()) {
                target = wrappedWriter.unwrap()
            }
        }
        if (target.is(writer)) {
            throw new IllegalArgumentException('Cannot write buffer to itself.')
        }
        if (!emptyAfter && target instanceof StreamCharBufferWriter) {
            ((StreamCharBufferWriter) target).write(this, null)
            return
        } else if (writeToEncodedAppender(this, target, writer.getEncodedAppender(), true)) {
            if (emptyAfter) {
                emptyAfterReading()
            }
            if (flushTarget) {
                target.flush()
            }
            return
        }
        writeToImpl(target, flushTarget, emptyAfter)
    }

    private static boolean writeToEncodedAppender(StreamCharBuffer source, Writer target, EncodedAppender notAllowedAppender, boolean flush) throws IOException {
        if (target instanceof EncodedAppenderFactory) {
            EncodedAppenderFactory eaw = (EncodedAppenderFactory) target
            EncodedAppender appender = eaw.getEncodedAppender()
            if (appender != null) {
                if (appender.is(notAllowedAppender)) {
                    throw new IllegalArgumentException('Cannot write buffer to itself.')
                }
                Encoder encoder = null

                if (target instanceof EncoderAware) {
                    encoder = ((EncoderAware) target).getEncoder()
                }

                if (encoder == null && appender instanceof EncoderAware) {
                    encoder = ((EncoderAware) appender).getEncoder()
                }

                source.encodeTo(appender, encoder)
                if (flush) {
                    appender.flush()
                }
                return true
            }
        }
        return false
    }

    private void writeToImpl(Writer target, boolean flushTarget, boolean emptyAfter) throws IOException {
        AbstractChunk current = firstChunk
        while (current != null) {
            current.writeTo(target)
            current = current.next
        }
        allocBuffer.writeTo(target)
        if (emptyAfter) {
            emptyAfterReading()
        }
        if (flushTarget) {
            target.flush()
        }
    }

    protected void emptyAfterReading() {
        firstChunk = null
        lastChunk = null
        totalCharsInList = 0
        totalCharsInDynamicChunks = -1
        sizeAtLeast = -1
        dynamicChunkMap.clear()
        allocBuffer.clear()
    }

    /**
     * {@inheritDoc}
     *
     * Reads (and empties) the buffer to a String, but caches the return value for subsequent calls.
     * If more content has been added between 2 calls, the returned value will be joined from the previously cached value and the data read from the buffer.
     *
     * @see java.lang.Object#toString()
     */
    @Override
    String toString() {
        StringChunk stringChunk = readToSingleStringChunk(true)
        if (stringChunk != null) {
            return stringChunk.str
        } else {
            return ''
        }
    }

    StringChunk readToSingleStringChunk(boolean registerEncodingState) {
        if (firstChunk.is(lastChunk) && firstChunk instanceof StringChunk && allocBuffer.charsUsed() == 0 &&
                ((StringChunk) firstChunk).isSingleBuffer()) {
            StringChunk chunk = ((StringChunk) firstChunk)
            if (registerEncodingState) {
                markEncoded(chunk)
            }
            return chunk
        }

        int initialReaderCount = readerCount
        MultipartCharBufferChunk chunk = readToSingleChunk()
        MultipartStringChunk stringChunk = (chunk != null) ? chunk.asStringChunk() : null
        if (initialReaderCount == 0) {
            // if there are no readers, the result can be cached
            reset()
            if (stringChunk != null) {
                addChunk(stringChunk)
            }
        }

        if (registerEncodingState) {
            markEncoded(stringChunk)
        }

        return stringChunk
    }

    void markEncoded(StringChunk strChunk) {
        if (strChunk instanceof MultipartStringChunk) {
            MultipartStringChunk stringChunk = (MultipartStringChunk) strChunk
            if (stringChunk.isSingleEncoding()) {
                EncodingState encodingState = stringChunk.firstPart.encodingState
                if (encodingState != null && encodingState.getEncoders() != null && encodingState.getEncoders().size() > 0) {
                    Encoder encoder = encodingState.getEncoders().iterator().next()
                    if (encoder != null)
                        encoder.markEncoded(stringChunk.str)
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Uses String's hashCode to support compatibility with String instances in maps, sets, etc.
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    int hashCode() {
        return toString().hashCode()
    }

    /**
     * equals uses String.equals to check for equality to support compatibility with String instances in maps, sets, etc.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    boolean equals(Object o) {
        if (this.is(o)) return true

        if (!(o instanceof CharSequence)) return false

        CharSequence other = (CharSequence) o

        return toString().equals(other.toString())
    }

    String plus(String value) {
        return toString() + value
    }

    String plus(Object value) {
        return toString() + value
    }

    /**
     * Reads the buffer to a char[].
     *
     * Caches the result if there aren't any readers.
     *
     * @return the chars
     */
    char[] toCharArray() {
        // check if there is a cached single charbuffer
        if (firstChunk.is(lastChunk) && firstChunk instanceof CharBufferChunk && allocBuffer.charsUsed() == 0 && ((CharBufferChunk) firstChunk).isSingleBuffer()) {
            return ((CharBufferChunk) firstChunk).buffer
        }

        int initialReaderCount = readerCount
        MultipartCharBufferChunk chunk = readToSingleChunk()
        if (initialReaderCount == 0) {
            // if there are no readers, the result can be cached
            reset()
            if (chunk != null) {
                addChunk(chunk)
            }
        }
        if (chunk != null) {
            return chunk.buffer
        } else {
            return new char[0]
        }
    }

    static final class EncodedPart {
        private final EncodingState encodingState
        private final String part

        EncodedPart(EncodingState encodingState, String part) {
            this.encodingState = encodingState
            this.part = part
        }

        EncodingState getEncodingState() {
            return encodingState
        }

        String getPart() {
            return part
        }

        @Override
        String toString() {
            return "EncodedPart [encodingState='" + encodingState + "', part='" + part + "']"
        }
    }

    List<EncodedPart> dumpEncodedParts() {
        List<EncodedPart> encodedParts = new ArrayList<>()
        MultipartStringChunk mpStringChunk = readToSingleChunk().asStringChunk()
        if (mpStringChunk.firstPart != null) {
            EncodingStatePart current = mpStringChunk.firstPart
            int offset = 0
            char[] buf = StringCharArrayAccessor.getValue(mpStringChunk.str)
            while (current != null) {
                encodedParts.add(new EncodedPart(current.encodingState, new String(buf, offset, current.len)))
                offset += current.len
                current = current.next
            }
        }
        return encodedParts
    }

    private MultipartCharBufferChunk readToSingleChunk() {
        int currentSize = size()
        if (currentSize == 0) {
            return null
        }

        FixedCharArrayEncodedAppender appender = new FixedCharArrayEncodedAppender(currentSize)
        try {
            encodeTo(appender, null)
        }
        catch (IOException e) {
            throw new RuntimeException('Unexpected IOException', e)
        }
        appender.finish()
        return appender.chunk
    }

    boolean hasQuicklyCalcutableSize() {
        return totalCharsInDynamicChunks != -1 || dynamicChunkMap.size() == 0
    }

    int size() {
        int total = totalCharsInList
        if (totalCharsInDynamicChunks == -1) {
            totalCharsInDynamicChunks = 0
            for (StreamCharBufferSubChunk chunk : dynamicChunkMap.values()) {
                totalCharsInDynamicChunks += chunk.size()
            }
        }
        total += totalCharsInDynamicChunks
        total += allocBuffer.charsUsed()
        sizeAtLeast = total
        return total
    }

    boolean isEmpty() {
        return !isNotEmpty()
    }

    boolean isNotEmpty() {
        if (totalCharsInList > 0) {
            return true
        }
        if (totalCharsInDynamicChunks > 0) {
            return true
        }
        if (allocBuffer.charsUsed() > 0) {
            return true
        }
        if (totalCharsInDynamicChunks == -1) {
            for (StreamCharBufferSubChunk chunk : dynamicChunkMap.values()) {
                if (chunk.getSourceBuffer().isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    boolean isSizeLarger(int minSize) {
        if (minSize <= sizeAtLeast) {
            return true
        }

        boolean retval = calculateIsSizeLarger(minSize)
        if (retval && minSize > sizeAtLeast) {
            sizeAtLeast = minSize
        }
        return retval
    }

    private boolean calculateIsSizeLarger(int minSize) {
        int total = totalCharsInList
        total += allocBuffer.charsUsed()
        if (total > minSize) {
            return true
        }
        if (totalCharsInDynamicChunks != -1) {
            total += totalCharsInDynamicChunks
            if (total > minSize) {
                return true
            }
        } else {
            for (StreamCharBufferSubChunk chunk : dynamicChunkMap.values()) {
                int remaining = minSize - total
                if (!chunk.hasCachedSize() && (chunk.getSourceBuffer().isSizeLarger(remaining) || (!chunk.getEncodedBuffer().is(chunk.getSourceBuffer()) && chunk.getEncodedBuffer().isSizeLarger(remaining)))) {
                    return true
                }
                total += chunk.size()
                if (total > minSize) {
                    return true
                }
            }
        }
        return false
    }

    int allocateSpace(EncodingState encodingState) throws IOException {
        int spaceLeft = allocBuffer.spaceLeft(encodingState)
        if (spaceLeft == 0) {
            spaceLeft = appendCharBufferChunk(encodingState, true, true)
        }
        return spaceLeft
    }

    private int appendCharBufferChunk(EncodingState encodingState, boolean flushInConnected, boolean allocate) throws IOException {
        int spaceLeft = 0
        if (flushInConnected && isConnectedMode()) {
            flushToConnected(false)
            if (!isChunkSizeResizeable()) {
                allocBuffer.reuseBuffer(encodingState)
            }
        }
        else {
            if (allocBuffer.hasChunk()) {
                addChunk(allocBuffer.createChunk())
            }
        }
        spaceLeft = allocBuffer.spaceLeft(encodingState)
        if (allocate && spaceLeft == 0) {
            totalChunkSize += allocBuffer.chunkSize()
            resizeChunkSizeAsProcentageOfTotalSize()
            allocBuffer = new AllocatedBuffer(chunkSize)
            spaceLeft = allocBuffer.spaceLeft(encodingState)
        }
        return spaceLeft
    }

    void appendStringChunk(EncodingState encodingState, String str, int off, int len) throws IOException {
        appendCharBufferChunk(encodingState, false, false)
        addChunk(new StringChunk(str, off, len)).setEncodingState(encodingState)
    }

    void appendStreamCharBufferChunk(StreamCharBuffer subBuffer) throws IOException {
        appendStreamCharBufferChunk(subBuffer, null)
    }

    void appendStreamCharBufferChunk(StreamCharBuffer subBuffer, List<Encoder> encoders) throws IOException {
        appendCharBufferChunk(null, false, false)
        addChunk(new StreamCharBufferSubChunk(subBuffer, encoders))
    }

    AbstractChunk addChunk(AbstractChunk newChunk) {
        if (lastChunk != null) {
            lastChunk.next = newChunk
            if (hasReaders) {
                // double link only if there are active readers since backwards iterating is only required for simultaneous writer & reader
                newChunk.prev = lastChunk
            }
        }
        lastChunk = newChunk
        if (firstChunk == null) {
            firstChunk = newChunk
        }
        if (newChunk instanceof StreamCharBufferSubChunk) {
            StreamCharBufferSubChunk bufSubChunk = (StreamCharBufferSubChunk) newChunk
            dynamicChunkMap.put(bufSubChunk.getSourceBuffer().bufferKey, bufSubChunk)
        }
        else {
            totalCharsInList += newChunk.size()
        }
        return newChunk
    }

    boolean isConnectedMode() {
        return connectToWriters != null && !connectToWriters.isEmpty()
    }

    private void flushToConnected(boolean forceFlush) throws IOException {
        startUsingConnectedWritersWriter()
        if (notConnectedToEncodeAwareWriters == null) {
            notConnectedToEncodeAwareWriters = !connectedWritersWriter.isEncoderAware()
        }
        writeTo(connectedWritersWriter, forceFlush, true)
        if (forceFlush) {
            connectedWritersWriter.forceFlush()
        }
    }

    protected boolean isChunkSizeResizeable() {
        return (growProcent > 0 && chunkSize < maxChunkSize)
    }

    protected void resizeChunkSizeAsProcentageOfTotalSize() {
        if (growProcent == 0) {
            return
        }

        if (growProcent == 100) {
            chunkSize = Math.min(totalChunkSize, maxChunkSize)
        }
        else if (growProcent == 200) {
            chunkSize = Math.min(totalChunkSize << 1, maxChunkSize)
        }
        else if (growProcent > 0) {
            chunkSize = Math.max(Math.min((totalChunkSize * growProcent).intdiv(100), maxChunkSize), firstChunkSize)
        }
    }

    protected static final void arrayCopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        if (length == 1) {
            dest[destPos] = src[srcPos]
        }
        else {
            System.arraycopy(src, srcPos, dest, destPos, length)
        }
    }

    /**
     * This is the java.io.Writer implementation for StreamCharBuffer
     *
     * @author Lari Hotari, Sagire Software Oy
     */
    final class StreamCharBufferWriter extends Writer implements EncodedAppenderFactory, EncodedAppenderWriterFactory {
        boolean closed = false
        int writerUsedCounter = 0
        boolean increaseCounter = true
        EncodedAppender encodedAppender

        @Override
        final void write(final char[] b, final int off, final int len) throws IOException {
            write(null, b, off, len)
        }

        @groovy.transform.PackageScope final void write(EncodingState encodingState, final char[] b, final int off, final int len) throws IOException {
            if (b == null) {
                throw new NullPointerException()
            }

            if ((off < 0) || (off > b.length) || (len < 0) ||
                    ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException()
            }

            if (len == 0) {
                return
            }

            markUsed()
            if (shouldWriteDirectly(len)) {
                appendCharBufferChunk(encodingState, true, true)
                startUsingConnectedWritersWriter()
                StreamCharBuffer.this.connectedWritersWriter.write(b, off, len)
            }
            else {
                int charsLeft = len
                int currentOffset = off
                while (charsLeft > 0) {
                    int spaceLeft = allocateSpace(encodingState)
                    int writeChars = Math.min(spaceLeft, charsLeft)
                    StreamCharBuffer.this.allocBuffer.write(b, currentOffset, writeChars)
                    charsLeft -= writeChars
                    currentOffset += writeChars
                }
            }
        }

        private final boolean shouldWriteDirectly(final int len) {
            if (!isConnectedMode()) {
                return false
            }

            if (!(StreamCharBuffer.this.writeDirectlyToConnectedMinSize >= 0 && len >= StreamCharBuffer.this.writeDirectlyToConnectedMinSize)) {
                return false
            }

            return isNextChunkBigEnough(len)
        }

        private final boolean isNextChunkBigEnough(final int len) {
            return (len > getNewChunkMinSize())
        }

        private final int getDirectChunkMinSize() {
            if (!isConnectedMode()) {
                return -1
            }
            if (StreamCharBuffer.this.writeDirectlyToConnectedMinSize >= 0) {
                return StreamCharBuffer.this.writeDirectlyToConnectedMinSize
            }

            return getNewChunkMinSize()
        }

        private final int getNewChunkMinSize() {
            if (StreamCharBuffer.this.chunkMinSize <= 0 || StreamCharBuffer.this.allocBuffer.charsUsed() == 0 || StreamCharBuffer.this.allocBuffer.charsUsed() >= StreamCharBuffer.this.chunkMinSize) {
                return 0
            }
            return StreamCharBuffer.this.allocBuffer.spaceLeft(null)
        }

        @Override
        final void write(final String str) throws IOException {
            write(null, str, 0, str.length())
        }

        @Override
        final void write(final String str, final int off, final int len) throws IOException {
            write(null, str, off, len)
        }

        @groovy.transform.PackageScope final void write(EncodingState encodingState, final String str, final int off, final int len) throws IOException {
            if (len == 0) return
            markUsed()
            if (shouldWriteDirectly(len)) {
                appendCharBufferChunk(encodingState, true, false)
                startUsingConnectedWritersWriter()
                StreamCharBuffer.this.connectedWritersWriter.write(str, off, len)
            }
            else if (len >= StreamCharBuffer.this.subStringChunkMinSize && isNextChunkBigEnough(len)) {
                appendStringChunk(encodingState, str, off, len)
            }
            else {
                int charsLeft = len
                int currentOffset = off
                while (charsLeft > 0) {
                    int spaceLeft = allocateSpace(encodingState)
                    int writeChars = Math.min(spaceLeft, charsLeft)
                    StreamCharBuffer.this.allocBuffer.writeString(str, currentOffset, writeChars)
                    charsLeft -= writeChars
                    currentOffset += writeChars
                }
            }
        }

        final void write(StreamCharBuffer subBuffer) throws IOException {
            write(subBuffer, null)
        }

        final void write(StreamCharBuffer subBuffer, List<Encoder> encoders) throws IOException {
            markUsed()
            int directChunkMinSize = getDirectChunkMinSize()
            if (encoders == null &&
                    (directChunkMinSize == 0 || (directChunkMinSize != -1 && subBuffer
                            .isSizeLarger(directChunkMinSize)))) {
                appendCharBufferChunk(null, true, false)
                startUsingConnectedWritersWriter()
                subBuffer.writeToImpl(StreamCharBuffer.this.connectedWritersWriter, false, false)
            }
            else if (!appendSubBuffer(subBuffer, encoders)) {
                ChainedEncoders.chainEncode(subBuffer, this.getEncodedAppender(), encoders)
            }
        }

        boolean appendSubBuffer(StreamCharBuffer subBuffer, List<Encoder> encoders) throws IOException {
            if (isAllowSubBuffers() && subBuffer.isPreferSubChunkWhenWritingToOtherBuffer() ||
                    subBuffer.isSizeLarger(Math.max(StreamCharBuffer.this.subBufferChunkMinSize, getNewChunkMinSize()))) {
                if (subBuffer.isPreferSubChunkWhenWritingToOtherBuffer()) {
                    StreamCharBuffer.this.setPreferSubChunkWhenWritingToOtherBuffer(true)
                }
                markUsed()
                appendStreamCharBufferChunk(subBuffer, encoders)
                subBuffer.addParentBuffer(StreamCharBuffer.this)
                return true
            }
            return false
        }

        @Override
        final Writer append(final CharSequence csq, final int start, final int end)
                throws IOException {
            markUsed()
            if (csq == null) {
                write('null')
            }
            else {
                appendCharSequence(null, csq, start, end)
            }
            return this
        }

        protected void appendCharSequence(final EncodingState encodingState, final CharSequence csq, final int start, final int end) throws IOException {
            final Class<?> csqClass = csq.getClass()
            if (csqClass == String || csqClass == StringBuffer || csqClass == StringBuilder || csq instanceof CharArrayAccessible) {
                int len = end - start
                int charsLeft = len
                int currentOffset = start
                while (charsLeft > 0) {
                    int spaceLeft = allocateSpace(encodingState)
                    int writeChars = Math.min(spaceLeft, charsLeft)
                    if (csqClass == String) {
                        StreamCharBuffer.this.allocBuffer.writeString((String) (Object) csq, currentOffset, writeChars)
                    }
                    else if (csqClass == StringBuffer) {
                        StreamCharBuffer.this.allocBuffer.writeStringBuffer((StringBuffer) (Object) csq, currentOffset, writeChars)
                    }
                    else if (csqClass == StringBuilder) {
                        StreamCharBuffer.this.allocBuffer.writeStringBuilder((StringBuilder) (Object) csq, currentOffset, writeChars)
                    }
                    else if (csq instanceof CharArrayAccessible) {
                        StreamCharBuffer.this.allocBuffer.writeCharArrayAccessible((CharArrayAccessible) csq, currentOffset, writeChars)
                    }
                    charsLeft -= writeChars
                    currentOffset += writeChars
                }
            } else {
                String str = csq.subSequence(start, end).toString()
                write(encodingState, str, 0, str.length())
            }
        }

        @Override
        final Writer append(final CharSequence csq) throws IOException {
            markUsed()
            if (csq == null) {
                write('null')
            } else {
                append(csq, 0, csq.length())

            }
            return this
        }

        @Override
        void close() throws IOException {
            closed = true
            flushWriter(true)
        }

        boolean isClosed() {
            return closed
        }

        boolean isUsed() {
            return writerUsedCounter > 0
        }

        final void markUsed() {
            if (increaseCounter) {
                writerUsedCounter++
                if (!StreamCharBuffer.this.hasReaders) {
                    increaseCounter = false
                }
            }
        }

        int resetUsed() {
            int prevUsed = writerUsedCounter
            writerUsedCounter = 0
            increaseCounter = true
            return prevUsed
        }

        @Override
        void write(final int b) throws IOException {
            markUsed()
            allocateSpace(null)
            StreamCharBuffer.this.allocBuffer.write((char) b)
        }

        void flushWriter(boolean forceFlush) throws IOException {
            if (isConnectedMode()) {
                flushToConnected(forceFlush)
            }
            notifyBufferChange()
        }

        final StreamCharBuffer getBuffer() {
            return StreamCharBuffer.this
        }

        void append(EncodingState encodingState, char character) throws IOException {
            markUsed()
            allocateSpace(isNotConnectedToEncoderAwareWriters() || encodingState == null ? EncodingStateImpl.UNDEFINED_ENCODING_STATE : encodingState)
            StreamCharBuffer.this.allocBuffer.write(character)
        }

        Writer getWriterForEncoder(Encoder encoder, EncodingStateRegistry encodingStateRegistry) {
            return StreamCharBuffer.this.getWriterForEncoder(encoder, encodingStateRegistry)
        }

        EncodedAppender getEncodedAppender() {
            if (encodedAppender == null) {
                encodedAppender = new StreamCharBufferEncodedAppender(this)
            }
            return encodedAppender
        }

        @Override
        void flush() throws IOException {
            flushWriter(false)
        }
    }

    private boolean isNotConnectedToEncoderAwareWriters() {
        return notConnectedToEncodeAwareWriters != null && notConnectedToEncodeAwareWriters
    }

    private final static class StreamCharBufferEncodedAppender extends AbstractEncodedAppender {
        StreamCharBufferWriter writer

        StreamCharBufferEncodedAppender(StreamCharBufferWriter writer) {
            this.writer = writer
        }

        StreamCharBufferWriter getWriter() {
            return writer
        }

        @Override
        void flush() throws IOException {
            writer.flush()
        }

        @Override
        protected void write(EncodingState encodingState, char[] b, int off, int len) throws IOException {
            writer.write(encodingState, b, off, len)
        }

        @Override
        protected void write(EncodingState encodingState, String str, int off, int len) throws IOException {
            writer.write(encodingState, str, off, len)
        }

        @Override
        protected void appendCharSequence(EncodingState encodingState, CharSequence str, int start, int end)
                throws IOException {
            writer.appendCharSequence(encodingState, str, start, end)
        }

        void close() throws IOException {
            writer.close()
        }
    }

    /**
     * This is the java.io.Reader implementation for StreamCharBuffer
     *
     * @author Lari Hotari, Sagire Software Oy
     */
    final class StreamCharBufferReader extends Reader {
        boolean eofException = false
        int eofReachedCounter = 0
        ChunkReader chunkReader
        ChunkReader lastChunkReader
        boolean removeAfterReading

        StreamCharBufferReader(boolean remove) {
            removeAfterReading = remove
        }

        private int prepareRead(int len) {
            if (StreamCharBuffer.this.hasReaders && eofReachedCounter != 0) {
                if (eofReachedCounter != StreamCharBuffer.this.writer.writerUsedCounter) {
                    eofReachedCounter = 0
                    eofException = false
                    repositionChunkReader()
                }
            }
            if (chunkReader == null && eofReachedCounter == 0) {
                if (StreamCharBuffer.this.firstChunk != null) {
                    chunkReader = StreamCharBuffer.this.firstChunk.getChunkReader(removeAfterReading)
                    if (removeAfterReading) {
                        StreamCharBuffer.this.firstChunk.subtractFromTotalCount()
                    }
                }
                else {
                    chunkReader = new AllocatedBufferReader(StreamCharBuffer.this.allocBuffer, removeAfterReading)
                }
            }
            int available = 0
            if (chunkReader != null) {
                available = chunkReader.getReadLenLimit(len)
                while (available == 0 && chunkReader != null) {
                    chunkReader = chunkReader.next()
                    if (chunkReader != null) {
                        available = chunkReader.getReadLenLimit(len)
                    } else {
                        available = 0
                    }
                }
            }
            if (chunkReader == null) {
                if (StreamCharBuffer.this.hasReaders) {
                    eofReachedCounter = StreamCharBuffer.this.writer.writerUsedCounter
                } else {
                    eofReachedCounter = 1
                }
            } else if (StreamCharBuffer.this.hasReaders) {
                lastChunkReader = chunkReader
            }
            return available
        }

        /* adds support for reading and writing simultaneously in the same thread */
        private void repositionChunkReader() {
            if (lastChunkReader instanceof AllocatedBufferReader) {
                if (lastChunkReader.isValid()) {
                    chunkReader = lastChunkReader
                } else {
                    AllocatedBufferReader allocBufferReader = (AllocatedBufferReader) lastChunkReader
                    // find out what is the CharBufferChunk that was read by the AllocatedBufferReader already
                    int currentPosition = allocBufferReader.position
                    AbstractChunk chunk = StreamCharBuffer.this.lastChunk
                    while (chunk != null && chunk.writerUsedCounter >= lastChunkReader.getWriterUsedCounter()) {
                        if (chunk instanceof CharBufferChunk) {
                            CharBufferChunk charBufChunk = (CharBufferChunk) chunk
                            if (charBufChunk.allocatedBufferId == allocBufferReader.parent.id) {
                                if (currentPosition >= charBufChunk.offset && currentPosition <= charBufChunk.lastposition) {
                                    CharBufferChunkReader charBufChunkReader = (CharBufferChunkReader) charBufChunk.getChunkReader(removeAfterReading)
                                    int oldpointer = charBufChunkReader.pointer
                                    // skip the already chars
                                    charBufChunkReader.pointer = currentPosition
                                    if (removeAfterReading) {
                                        int diff = charBufChunkReader.pointer - oldpointer
                                        StreamCharBuffer.this.totalCharsInList -= diff
                                        charBufChunk.subtractFromTotalCount()
                                    }
                                    chunkReader = charBufChunkReader
                                    break
                                }
                            }
                        }
                        chunk = chunk.prev
                    }
                }
            }
        }

        @Override
        boolean ready() throws IOException {
            return true
        }

        @Override
        final int read(final char[] b, final int off, final int len) throws IOException {
            return readImpl(b, off, len)
        }

        final int readImpl(final char[] b, final int off, final int len) throws IOException {
            if (b == null) {
                throw new NullPointerException()
            }

            if ((off < 0) || (off > b.length) || (len < 0) ||
                    ((off + len) > b.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException()
            }

            if (len == 0) {
                return 0
            }

            int charsLeft = len
            int currentOffset = off
            int readChars = prepareRead(charsLeft)
            if (eofException) {
                throw new EOFException()
            }

            int totalCharsRead = 0
            while (charsLeft > 0 && readChars > 0) {
                chunkReader.read(b, currentOffset, readChars)
                charsLeft -= readChars
                currentOffset += readChars
                totalCharsRead += readChars
                if (charsLeft > 0) {
                    readChars = prepareRead(charsLeft)
                }
            }

            if (totalCharsRead > 0) {
                return totalCharsRead
            }

            eofException = true
            return -1
        }

        @Override
        void close() throws IOException {
            // do nothing
        }

        final StreamCharBuffer getBuffer() {
            return StreamCharBuffer.this
        }

        int getReadLenLimit(int askedAmount) {
            return prepareRead(askedAmount)
        }
    }

    abstract class AbstractChunk implements StreamEncodeable, StreamingEncoderWritable {
        AbstractChunk next
        AbstractChunk prev
        int writerUsedCounter
        EncodingState encodingState

        AbstractChunk() {
            if (StreamCharBuffer.this.hasReaders) {
                writerUsedCounter = StreamCharBuffer.this.writer.writerUsedCounter
            }
            else {
                writerUsedCounter = 1
            }
        }

        abstract void writeTo(Writer target) throws IOException

        abstract ChunkReader getChunkReader(boolean removeAfterReading)

        abstract int size()

        int getWriterUsedCounter() {
            return writerUsedCounter
        }

        void subtractFromTotalCount() {
            StreamCharBuffer.this.totalCharsInList -= size()
        }

        EncodingState getEncodingState() {
            return encodingState
        }

        void setEncodingState(EncodingState encodingState) {
            this.encodingState = encodingState
        }
    }

    // keep read state in this class
    static abstract class ChunkReader {
        abstract int read(char[] ch, int off, int len) throws IOException

        abstract int getReadLenLimit(int askedAmount)

        abstract ChunkReader next()

        abstract int getWriterUsedCounter()

        abstract boolean isValid()
    }

    final class AllocatedBuffer {
        private int id = StreamCharBuffer.this.@allocatedBufferIdSequence++
        private int size
        private char[] buffer
        private int used = 0
        private int chunkStart = 0
        private EncodingState encodingState
        private EncodingState nextEncoders

        AllocatedBuffer(int size) {
            this.size = size
            buffer = new char[size]
        }

        void clear() {
            reuseBuffer(null)
        }

        int charsUsed() {
            return used - chunkStart
        }

        void writeTo(Writer target) throws IOException {
            if (used - chunkStart > 0) {
                target.write(buffer, chunkStart, used - chunkStart)
            }
        }

        void reuseBuffer(EncodingState encodingState) {
            used = 0
            chunkStart = 0
            this.encodingState = null
            this.nextEncoders = encodingState
        }

        int chunkSize() {
            return buffer.length
        }

        int spaceLeft(EncodingState encodingState) {
            if (encodingState == null) {
                encodingState = EncodingStateImpl.UNDEFINED_ENCODING_STATE
            }
            if (this.encodingState != null && (encodingState == null || !this.encodingState.equals(encodingState)) && hasChunk() && !isNotConnectedToEncoderAwareWriters()) {
                StreamCharBuffer.this.addChunk(StreamCharBuffer.this.allocBuffer.createChunk())
                this.encodingState = null
            }
            this.nextEncoders = encodingState
            return size - used
        }

        private final void applyEncoders() throws IOException {
            if (encodingState == nextEncoders) {
                return
            }
            if (encodingState != null && !isNotConnectedToEncoderAwareWriters() && (nextEncoders == null || !encodingState.equals(nextEncoders))) {
                throw new IOException('Illegal operation in AllocatedBuffer')
            }
            encodingState = nextEncoders
        }

        boolean write(final char ch) throws IOException {
            if (used < size) {
                applyEncoders()
                buffer[used++] = ch
                return true
            }

            return false
        }

        final void write(final char[] ch, final int off, final int len) throws IOException {
            applyEncoders()
            arrayCopy(ch, off, buffer, used, len)
            used += len
        }

        final void writeString(final String str, final int off, final int len) throws IOException {
            applyEncoders()
            str.getChars(off, off + len, buffer, used)
            used += len
        }

        final void writeStringBuilder(final StringBuilder stringBuilder, final int off, final int len) throws IOException {
            applyEncoders()
            stringBuilder.getChars(off, off + len, buffer, used)
            used += len
        }

        final void writeStringBuffer(final StringBuffer stringBuffer, final int off, final int len) throws IOException {
            applyEncoders()
            stringBuffer.getChars(off, off + len, buffer, used)
            used += len
        }

        final void writeCharArrayAccessible(final CharArrayAccessible charArrayAccessible, final int off, final int len) throws IOException {
            applyEncoders()
            charArrayAccessible.getChars(off, off + len, buffer, used)
            used += len
        }

        /**
         * Creates a new chunk from the content written to the buffer (used before adding StringChunk or StreamCharBufferChunk).
         *
         * @return the chunk
         */
        CharBufferChunk createChunk() {
            CharBufferChunk chunk = new CharBufferChunk(id, buffer, chunkStart, used - chunkStart)
            chunk.setEncodingState(encodingState)
            chunkStart = used
            return chunk
        }

        boolean hasChunk() {
            return (used > chunkStart)
        }

        void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            if (used - chunkStart > 0) {
                appender.append(encoder, encodingState, buffer, chunkStart, used - chunkStart)
            }
        }

        EncodingState getEncodingState() {
            return encodingState
        }

        void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            if (used - chunkStart > 0) {
                encoder.encodeToWriter(buffer, chunkStart, used - chunkStart, writer, getEncodingState())
            }
        }
    }

    /**
     * The data in the buffer is stored in a linked list of StreamCharBufferChunks.
     *
     * This class contains data & read/write state for the "chunk level".
     * It contains methods for reading & writing to the chunk level.
     *
     * Underneath the chunk is one more level, the StringChunkGroup + StringChunk.
     * StringChunk makes it possible to directly store the java.lang.String objects.
     *
     * @author Lari Hotari
     *
     */
    class CharBufferChunk extends AbstractChunk {
        int allocatedBufferId
        char[] buffer
        int offset
        int lastposition
        int length

        CharBufferChunk(int allocatedBufferId, char[] buffer, int offset, int len) {
            super()
            this.allocatedBufferId = allocatedBufferId
            this.buffer = buffer
            this.offset = offset
            this.lastposition = offset + len
            this.length = len
        }

        @Override
        void writeTo(final Writer target) throws IOException {
            target.write(buffer, offset, length)
        }

        @Override
        ChunkReader getChunkReader(boolean removeAfterReading) {
            return new CharBufferChunkReader(this, removeAfterReading)
        }

        @Override
        int size() {
            return length
        }

        boolean isSingleBuffer() {
            return offset == 0 && length == buffer.length
        }

        @Override
        void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            appender.append(encoder, getEncodingState(), buffer, offset, length)
        }

        @Override
        void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            encoder.encodeToWriter(buffer, offset, length, writer, getEncodingState())
        }
    }

    class MultipartStringChunk extends StringChunk {
        EncodingStatePart firstPart = null
        EncodingStatePart lastPart = null

        MultipartStringChunk(String str) {
            super(str, 0, str.length())
        }

        @Override
        void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            if (firstPart != null) {
                EncodingStatePart current = firstPart
                int offset = 0
                char[] buf = StringCharArrayAccessor.getValue(str)
                while (current != null) {
                    appender.append(encoder, current.encodingState, buf, offset, current.len)
                    offset += current.len
                    current = current.next
                }
            } else {
                super.encodeTo(appender, encoder)
            }
        }

        @Override
        void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            if (firstPart != null) {
                EncodingStatePart current = firstPart
                int offset = 0
                char[] buf = StringCharArrayAccessor.getValue(str)
                while (current != null) {
                    encoder.encodeToWriter(buf, offset, current.len, writer, current.encodingState)
                    offset += current.len
                    current = current.next
                }
            } else {
                super.encodeTo(writer, encoder)
            }
        }

        boolean isSingleEncoding() {
            return (firstPart.is(lastPart))
        }

        int partCount() {
            int partCount = 0
            EncodingStatePart current = firstPart
            while (current != null) {
                partCount++
                current = current.next
            }
            return partCount
        }

        void appendEncodingStatePart(EncodingStatePart current) {
            if (firstPart == null) {
                firstPart = current
                lastPart = current
            } else {
                lastPart.next = current
                lastPart = current
            }
        }
    }

    class MultipartCharBufferChunk extends CharBufferChunk {
        EncodingStatePart firstPart = null
        EncodingStatePart lastPart = null

        MultipartCharBufferChunk(char[] buffer) {
            super(-1, buffer, 0, buffer.length)
        }

        @Override
        void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            if (firstPart != null) {
                EncodingStatePart current = firstPart
                int offset = 0
                while (current != null) {
                    appender.append(encoder, current.encodingState, buffer, offset, current.len)
                    offset += current.len
                    current = current.next
                }
            } else {
                super.encodeTo(appender, encoder)
            }
        }

        MultipartStringChunk asStringChunk() {
            String str = StringCharArrayAccessor.createString(buffer)
            MultipartStringChunk chunk = new MultipartStringChunk(str)
            chunk.firstPart = firstPart
            chunk.lastPart = lastPart
            return chunk
        }
    }

    static final class EncodingStatePart {
        EncodingStatePart next
        EncodingState encodingState
        int len = -1
    }

    abstract class AbstractChunkReader extends ChunkReader {
        private AbstractChunk parentChunk
        private boolean removeAfterReading

        AbstractChunkReader(AbstractChunk parentChunk, boolean removeAfterReading) {
            this.parentChunk = parentChunk
            this.removeAfterReading = removeAfterReading
        }

        @Override
        boolean isValid() {
            return true
        }

        @Override
        ChunkReader next() {
            if (removeAfterReading) {
                if (StreamCharBuffer.this.firstChunk.is(parentChunk)) {
                    StreamCharBuffer.this.firstChunk = null
                }
                if (StreamCharBuffer.this.lastChunk.is(parentChunk)) {
                    StreamCharBuffer.this.lastChunk = null
                }
            }
            AbstractChunk nextChunk = parentChunk.next
            if (nextChunk != null) {
                if (removeAfterReading) {
                    if (StreamCharBuffer.this.firstChunk == null) {
                        StreamCharBuffer.this.firstChunk = nextChunk
                    }
                    if (StreamCharBuffer.this.lastChunk == null) {
                        StreamCharBuffer.this.lastChunk = nextChunk
                    }
                    nextChunk.prev = null
                    nextChunk.subtractFromTotalCount()
                }
                return nextChunk.getChunkReader(removeAfterReading)
            }

            return new AllocatedBufferReader(StreamCharBuffer.this.allocBuffer, removeAfterReading)
        }

        @Override
        int getWriterUsedCounter() {
            return parentChunk.getWriterUsedCounter()
        }
    }

    final class CharBufferChunkReader extends AbstractChunkReader {
        CharBufferChunk parent
        int pointer

        CharBufferChunkReader(CharBufferChunk parent, boolean removeAfterReading) {
            super(parent, removeAfterReading)
            this.parent = parent
            pointer = parent.offset
        }

        @Override
        int read(final char[] ch, final int off, final int len) throws IOException {
            arrayCopy(parent.buffer, pointer, ch, off, len)
            pointer += len
            return len
        }

        @Override
        int getReadLenLimit(int askedAmount) {
            return Math.min(parent.lastposition - pointer, askedAmount)
        }
    }

    /**
     * StringChunk is a wrapper for java.lang.String.
     *
     * It also keeps state of the read offset and the number of unread characters.
     *
     * There's methods that StringChunkGroup uses for reading data.
     *
     * @author Lari Hotari
     *
     */
    class StringChunk extends AbstractChunk {
        String str
        int offset
        int lastposition
        int length

        StringChunk(String str, int offset, int length) {
            this.str = str
            this.offset = offset
            this.length = length
            this.lastposition = offset + length
        }

        @Override
        ChunkReader getChunkReader(boolean removeAfterReading) {
            return new StringChunkReader(this, removeAfterReading)
        }

        @Override
        void writeTo(Writer target) throws IOException {
            target.write(str, offset, length)
        }

        @Override
        int size() {
            return length
        }

        boolean isSingleBuffer() {
            return offset == 0 && length == str.length()
        }

        @Override
        void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
            appender.append(encoder, getEncodingState(), str, offset, length)
        }

        @Override
        void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            encoder.encodeToWriter(toCharSequence(), 0, length, writer, getEncodingState())
        }

        CharSequence toCharSequence() {
            if (isSingleBuffer()) {
                return str
            } else {
                return CharSequences.createCharSequence(str, offset, length)
            }
        }
    }

    final class StringChunkReader extends AbstractChunkReader {
        StringChunk parent
        int position

        StringChunkReader(StringChunk parent, boolean removeAfterReading) {
            super(parent, removeAfterReading)
            this.parent = parent
            this.position = parent.offset
        }

        @Override
        int read(final char[] ch, final int off, final int len) {
            parent.str.getChars(position, (position + len), ch, off)
            position += len
            return len
        }

        @Override
        int getReadLenLimit(int askedAmount) {
            return Math.min(parent.lastposition - position, askedAmount)
        }
    }

    final class StreamCharBufferSubChunk extends AbstractChunk {
        private StreamCharBuffer sourceBuffer
        private List<Encoder> encoders
        private StreamCharBuffer encodedBuffer
        int cachedSize
        int encodedSourceChangesCounter = -1

        StreamCharBufferSubChunk(StreamCharBuffer sourceBuffer, List<Encoder> encoders) {
            this.sourceBuffer = sourceBuffer
            this.encoders = encoders
            if (encoders == null && hasQuicklyCalcutableSize() && sourceBuffer.hasQuicklyCalcutableSize()) {
                cachedSize = sourceBuffer.size()
                if (StreamCharBuffer.this.totalCharsInDynamicChunks == -1) {
                    StreamCharBuffer.this.totalCharsInDynamicChunks = 0
                }
                StreamCharBuffer.this.totalCharsInDynamicChunks += cachedSize
            } else {
                StreamCharBuffer.this.totalCharsInDynamicChunks = -1
                cachedSize = -1
            }
            if (encoders == null || sourceBuffer.isEmpty()) {
                encodedBuffer = sourceBuffer
                encodedSourceChangesCounter = sourceBuffer.getBufferChangesCounter()
            }
        }

        @Override
        ChunkReader getChunkReader(boolean removeAfterReading) {
            return new StreamCharBufferSubChunkReader(this, removeAfterReading)
        }

        @Override
        int size() {
            if (cachedSize == -1) {
                cachedSize = getEncodedBuffer().size()
            }
            return cachedSize
        }

        boolean hasCachedSize() {
            return (cachedSize != -1)
        }

        StreamCharBuffer getSourceBuffer() {
            return sourceBuffer
        }

        @Override
        void writeTo(Writer target) throws IOException {
            if (encoders == null || hasEncodedBufferAvailable() || !hasOnlyStreamingEncoders()) {
                getEncodedBuffer().writeTo(target)
            }
            else {
                EncodedAppender appender
                if (target instanceof EncodedAppender) {
                    appender = ((EncodedAppender) target)
                } else if (target instanceof EncodedAppenderFactory) {
                    appender = ((EncodedAppenderFactory) target).getEncodedAppender()
                }
                else {
                    appender = new WriterEncodedAppender(target)
                }
                ChainedEncoders.chainEncode(getSourceBuffer(), appender, encoders)
            }
        }

        @Override
        void encodeTo(EncodedAppender appender, Encoder encodeToEncoder) throws IOException {
            if (appender instanceof StreamCharBufferEncodedAppender &&
                    getSourceBuffer().isPreferSubChunkWhenWritingToOtherBuffer() &&
                    ((StreamCharBufferEncodedAppender) appender).getWriter().getBuffer().isAllowSubBuffers()) {
                List<Encoder> nextEncoders = ChainedEncoders.appendEncoder(encoders, encodeToEncoder)
                ((StreamCharBufferEncodedAppender) appender).getWriter().write(getSourceBuffer(), nextEncoders)
            }
            else {
                if (hasEncodedBufferAvailable() || !hasOnlyStreamingEncoders()) {
                    appender.append(encodeToEncoder, getEncodedBuffer())
                }
                else {
                    ChainedEncoders.chainEncode(getSourceBuffer(), appender, ChainedEncoders.appendEncoder(encoders, encodeToEncoder))
                }
            }
        }

        protected boolean hasOnlyStreamingEncoders() {
            if (encoders == null || encoders.isEmpty()) {
                return false
            }
            for (Encoder encoder : encoders) {
                if (!(encoder instanceof StreamingEncoder)) {
                    return false
                }
            }
            return true
        }

        StreamCharBuffer getEncodedBuffer() {
            if (!hasEncodedBufferAvailable()) {
                if (encoders == null || sourceBuffer.isEmpty()) {
                    encodedBuffer = sourceBuffer
                    encodedSourceChangesCounter = sourceBuffer.getBufferChangesCounter()
                }
                else {
                    encodedBuffer = new StreamCharBuffer(StreamCharBuffer.this.chunkSize, StreamCharBuffer.this.growProcent, StreamCharBuffer.this.maxChunkSize)
                    encodedBuffer.setAllowSubBuffers(isAllowSubBuffers())
                    encodedBuffer.setNotifyParentBuffersEnabled(getSourceBuffer().isNotifyParentBuffersEnabled())
                    encodeToEncodedBuffer()
                }
            }
            return encodedBuffer
        }

        private void encodeToEncodedBuffer() {
            boolean previousAllowSubBuffer = encodedBuffer.isAllowSubBuffers()
            encodedBuffer.setAllowSubBuffers(false)
            encodedSourceChangesCounter = sourceBuffer.getBufferChangesCounter()
            try {
                ChainedEncoders.chainEncode(getSourceBuffer(), encodedBuffer.writer.getEncodedAppender(), encoders)
            }
            catch (IOException e) {
                throw new RuntimeException(e)
            }
            encodedBuffer.setAllowSubBuffers(previousAllowSubBuffer)
            encodedBuffer.setPreferSubChunkWhenWritingToOtherBuffer(getSourceBuffer().isPreferSubChunkWhenWritingToOtherBuffer())
            encodedBuffer.notifyBufferChange()
        }

        protected boolean hasEncodedBufferAvailable() {
            return encodedBuffer != null && encodedSourceChangesCounter == sourceBuffer.getBufferChangesCounter()
        }

        boolean resetSubBuffer() {
            if (cachedSize != -1 || !encodedBuffer.is(sourceBuffer)) {
                cachedSize = -1
                encodedSourceChangesCounter = -1
                if (!encodedBuffer.is(sourceBuffer) && encodedBuffer != null) {
                    encodedBuffer.clear()
                    encodeToEncodedBuffer()
                }
                return true
            }
            return false
        }

        @Override
        void subtractFromTotalCount() {
            StreamCharBuffer.this.totalCharsInDynamicChunks = -1
            StreamCharBuffer.this.dynamicChunkMap.remove(sourceBuffer.bufferKey)
        }

        @Override
        void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
            if (hasEncodedBufferAvailable() || !hasOnlyStreamingEncoders() || encoders == null) {
                getEncodedBuffer().encodeTo(writer, encoder)
            } else {
                List<StreamingEncoder> streamingEncoders = new ArrayList<>(encoders.size())
                for (Encoder e : encoders) {
                    streamingEncoders.add((StreamingEncoder) e)
                }
                getSourceBuffer().encodeTo(writer, encoder.createChainingEncodesToWriter(streamingEncoders, true))
            }
        }
    }

    final class StreamCharBufferSubChunkReader extends AbstractChunkReader {
        StreamCharBufferSubChunk parent
        private StreamCharBufferReader reader

        StreamCharBufferSubChunkReader(StreamCharBufferSubChunk parent, boolean removeAfterReading) {
            super(parent, removeAfterReading)
            this.parent = parent
            reader = (StreamCharBufferReader) parent.getEncodedBuffer().getReader()
        }

        @Override
        int getReadLenLimit(int askedAmount) {
            return reader.getReadLenLimit(askedAmount)
        }

        @Override
        int read(char[] ch, int off, int len) throws IOException {
            return reader.read(ch, off, len)
        }
    }

    final class AllocatedBufferReader extends ChunkReader {
        AllocatedBuffer parent
        int position
        int writerUsedCounter
        boolean removeAfterReading

        AllocatedBufferReader(AllocatedBuffer parent, boolean removeAfterReading) {
            this.parent = parent
            position = parent.chunkStart
            if (StreamCharBuffer.this.hasReaders) {
                writerUsedCounter = StreamCharBuffer.this.writer.writerUsedCounter
            } else {
                writerUsedCounter = 1
            }
            this.removeAfterReading = removeAfterReading
        }

        @Override
        int getReadLenLimit(int askedAmount) {
            return Math.min(parent.used - position, askedAmount)
        }

        @Override
        int read(char[] ch, int off, int len) throws IOException {
            arrayCopy(parent.buffer, position, ch, off, len)
            position += len
            if (removeAfterReading) {
                parent.chunkStart = position
            }
            return len
        }

        @Override
        ChunkReader next() {
            return null
        }

        @Override
        int getWriterUsedCounter() {
            return writerUsedCounter
        }

        @Override
        boolean isValid() {
            return (StreamCharBuffer.this.allocBuffer.is(parent) && (StreamCharBuffer.this.lastChunk == null || StreamCharBuffer.this.lastChunk.writerUsedCounter < writerUsedCounter))
        }
    }

    private final class FixedCharArrayEncodedAppender extends AbstractEncodedAppender {
        char[] buf
        int count = 0
        int currentStart = 0
        EncodingState currentState
        MultipartCharBufferChunk chunk

        FixedCharArrayEncodedAppender(int fixedSize) {
            buf = new char[fixedSize]
            chunk = new MultipartCharBufferChunk(buf)
        }

        private void checkEncodingChange(EncodingState encodingState) {
            if (encodingState == null) {
                encodingState = EncodingStateImpl.UNDEFINED_ENCODING_STATE
            }
            if (currentState != null && !currentState.equals(encodingState)) {
                addPart()
            }
            if (currentState == null) {
                currentState = encodingState
            }
        }

        void finish() {
            addPart()
        }

        private void addPart() {
            if (count - currentStart > 0) {
                EncodingStatePart newPart = new EncodingStatePart()
                newPart.encodingState = currentState
                newPart.len = count - currentStart
                if (chunk.lastPart == null) {
                    chunk.firstPart = newPart
                    chunk.lastPart = newPart
                } else {
                    chunk.lastPart.next = newPart
                    chunk.lastPart = newPart
                }
                currentState = null
                currentStart = count
            }
        }

        @Override
        protected void write(EncodingState encodingState, char[] b, int off, int len) throws IOException {
            checkEncodingChange(encodingState)
            arrayCopy(b, off, buf, count, len)
            count += len
        }

        @Override
        protected void write(EncodingState encodingState, String str, int off, int len) throws IOException {
            checkEncodingChange(encodingState)
            str.getChars(off, off + len, buf, count)
            count += len
        }

        @Override
        protected void appendCharSequence(EncodingState encodingState, CharSequence csq, int start, int end)
                throws IOException {
            checkEncodingChange(encodingState)
            final Class<?> csqClass = csq.getClass()
            if (csqClass == String) {
                write(encodingState, (String) csq, start, end - start)
            }
            else if (csqClass == StringBuffer) {
                ((StringBuffer) csq).getChars(start, end, buf, count)
                count += end - start
            }
            else if (csqClass == StringBuilder) {
                ((StringBuilder) csq).getChars(start, end, buf, count)
                count += end - start
            }
            else if (csq instanceof CharArrayAccessible) {
                ((CharArrayAccessible) csq).getChars(start, end, buf, count)
                count += end - start
            }
            else {
                String str = csq.subSequence(start, end).toString()
                write(encodingState, str, 0, str.length())
            }
        }

        void close() throws IOException {
            finish()
        }
    }

    /**
     * Interface for a Writer that gets initialized if it is used
     * Can be used for passing in to "connectTo" method of StreamCharBuffer
     *
     * @author Lari Hotari
     *
     */
    static interface LazyInitializingWriter {
        Writer getWriter() throws IOException
    }

    static interface LazyInitializingMultipleWriter extends LazyInitializingWriter {
        /**
         * initialize underlying writer
         *
         * @return false if this writer entry should be removed after calling this callback method
         */
        LazyInitializingWriter[] initializeMultiple(StreamCharBuffer buffer, boolean autoFlush) throws IOException
    }

    final class ConnectToWriter {
        final Writer writer
        final LazyInitializingWriter lazyInitializingWriter
        final boolean autoFlush
        Boolean encoderAware

        ConnectToWriter(final Writer writer, final boolean autoFlush) {
            this.writer = writer
            this.lazyInitializingWriter = null
            this.autoFlush = autoFlush
        }

        ConnectToWriter(final LazyInitializingWriter lazyInitializingWriter, final boolean autoFlush) {
            this.lazyInitializingWriter = lazyInitializingWriter
            this.writer = null
            this.autoFlush = autoFlush
        }

        Writer[] getWriters() throws IOException {
            if (writer != null) {
                return new Writer[]{writer}
            } else {
                Set<Writer> writerList = resolveLazyInitializers(new HashSet<>(), lazyInitializingWriter)
                return writerList.toArray(new Writer[writerList.size()])
            }
        }

        private Set<Writer> resolveLazyInitializers(Set<Integer> resolved, LazyInitializingWriter lazyInitializingWriter) throws IOException {
            Set<Writer> writerList = Collections.emptySet()
            Integer identityHashCode = System.identityHashCode(lazyInitializingWriter)
            if (!resolved.contains(identityHashCode) && lazyInitializingWriter instanceof LazyInitializingMultipleWriter) {
                resolved.add(identityHashCode)
                writerList = new LinkedHashSet<>()
                LazyInitializingWriter[] writers = ((LazyInitializingMultipleWriter) lazyInitializingWriter).initializeMultiple(StreamCharBuffer.this, autoFlush)
                for (LazyInitializingWriter writer : writers) {
                    writerList.addAll(resolveLazyInitializers(resolved, writer))
                }
            } else {
                writerList = Collections.singleton(lazyInitializingWriter.getWriter())
            }
            return writerList
        }

        boolean isAutoFlush() {
            return autoFlush
        }
    }

    /**
     * Simple holder class for the connected writer
     *
     * @author Lari Hotari
     *
     */
    static final class ConnectedWriter {
        final Writer writer
        final boolean autoFlush
        final boolean encoderAware

        ConnectedWriter(final Writer writer, final boolean autoFlush) {
            this.writer = writer
            this.autoFlush = autoFlush
            this.encoderAware = (writer instanceof EncodedAppenderFactory || writer instanceof EncodedAppenderWriterFactory)
        }

        Writer getWriter() {
            return writer
        }

        void flush() throws IOException {
            if (autoFlush) {
                writer.flush()
            }
        }

        boolean isEncoderAware() {
            return encoderAware
        }
    }

    static final class SingleOutputWriter extends ConnectedWritersWriter implements GrailsWrappedWriter {
        private final ConnectedWriter connectedWriter
        private final Writer writer
        private final boolean encoderAware

        SingleOutputWriter(ConnectedWriter connectedWriter) {
            this.connectedWriter = connectedWriter
            this.writer = connectedWriter.getWriter()
            this.encoderAware = connectedWriter.isEncoderAware()
        }

        @Override
        void close() throws IOException {
            // do nothing
        }

        @Override
        void flush() throws IOException {
            connectedWriter.flush()
        }

        @Override
        void write(final char[] cbuf, final int off, final int len) throws IOException {
            writer.write(cbuf, off, len)
        }

        @Override
        Writer append(final CharSequence csq, final int start, final int end)
                throws IOException {
            writer.append(csq, start, end)
            return this
        }

        @Override
        void write(String str, int off, int len) throws IOException {
            if (!encoderAware) {
                StringCharArrayAccessor.writeStringAsCharArray(writer, str, off, len)
            } else {
                writer.write(str, off, len)
            }
        }

        @Override
        boolean isEncoderAware() throws IOException {
            return encoderAware
        }

        boolean isAllowUnwrappingOut() {
            return true
        }

        Writer unwrap() {
            return writer
        }

        void markUsed() {
        }

        @Override
        void forceFlush() throws IOException {
            writer.flush()
        }
    }

    static abstract class ConnectedWritersWriter extends Writer {
        abstract boolean isEncoderAware() throws IOException

        abstract void forceFlush() throws IOException
    }

    /**
     * delegates to several writers, used in "connectTo" mode.
     */
    static final class MultiOutputWriter extends ConnectedWritersWriter {
        final List<ConnectedWriter> connectedWriters
        final List<Writer> writers

        MultiOutputWriter(final List<ConnectedWriter> connectedWriters) {
            this.connectedWriters = connectedWriters
            this.writers = new ArrayList<>(connectedWriters.size())
            for (ConnectedWriter connectedWriter : connectedWriters) {
                writers.add(connectedWriter.getWriter())
            }
        }

        @Override
        void close() throws IOException {
            // do nothing
        }

        @Override
        void flush() throws IOException {
            for (ConnectedWriter connectedWriter : connectedWriters) {
                connectedWriter.flush()
            }
        }

        @Override
        void write(final char[] cbuf, final int off, final int len) throws IOException {
            for (Writer writer : writers) {
                writer.write(cbuf, off, len)
            }
        }

        @Override
        Writer append(final CharSequence csq, final int start, final int end)
                throws IOException {
            for (Writer writer : writers) {
                writer.append(csq, start, end)
            }
            return this
        }

        @Override
        void write(String str, int off, int len) throws IOException {
            if (isEncoderAware()) {
                for (ConnectedWriter connectedWriter : connectedWriters) {
                    if (!connectedWriter.isEncoderAware()) {
                        StringCharArrayAccessor.writeStringAsCharArray(connectedWriter.getWriter(), str, off, len)
                    } else {
                        connectedWriter.getWriter().write(str, off, len)
                    }
                }
            } else {
                for (Writer writer : writers) {
                    writer.write(str, off, len)
                }
            }
        }

        Boolean encoderAware

        @Override
        boolean isEncoderAware() throws IOException {
            if (encoderAware == null) {
                encoderAware = false
                for (ConnectedWriter writer : connectedWriters) {
                    if (writer.isEncoderAware()) {
                        encoderAware = true
                        break
                    }
                }
            }
            return encoderAware
        }

        @Override
        void forceFlush() throws IOException {
            for (Writer writer : writers) {
                writer.flush()
            }
        }
    }

    /* Compatibility methods so that StreamCharBuffer will behave more like java.lang.String in groovy code */

    char charAt(int index) {
        return toString().charAt(index)
    }

    int length() {
        return size()
    }

    CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end)
    }

    boolean asBoolean() {
        return isNotEmpty()
    }

    /* methods for notifying child (sub) StreamCharBuffer changes to the parent StreamCharBuffer */

    void addParentBuffer(StreamCharBuffer parent) {
        if (!notifyParentBuffersEnabled) return

        if (parentBuffers == null) {
            parentBuffers = new HashSet<>()
        }
        parentBuffers.add(new SoftReference<>(parent.bufferKey))
    }

    protected boolean bufferChanged(StreamCharBuffer buffer) {
        markBufferChanged()

        StreamCharBufferSubChunk subChunk = dynamicChunkMap.get(buffer.bufferKey)
        if (subChunk == null) {
            // buffer isn't a subchunk in this buffer any more
            return false
        }
        // reset cached size;
        if (subChunk.resetSubBuffer()) {
            totalCharsInDynamicChunks = -1
            sizeAtLeast = -1
            // notify parents too
            notifyBufferChange()
        }
        return true
    }

    protected List<StreamCharBuffer> getCurrentParentBuffers() {
        List<StreamCharBuffer> currentParentBuffers = new ArrayList<>()
        if (parentBuffers != null) {
            for (Iterator<SoftReference<StreamCharBufferKey>> i = parentBuffers.iterator(); i.hasNext();) {
                SoftReference<StreamCharBufferKey> ref = i.next()
                final StreamCharBuffer.StreamCharBufferKey parentKey = ref.get()
                if (parentKey != null) {
                    currentParentBuffers.add(parentKey.getBuffer())
                }
            }
        }
        return currentParentBuffers
    }

    protected void notifyBufferChange() {
        markBufferChanged()

        if (!notifyParentBuffersEnabled)
            return

        if (parentBuffers == null || parentBuffers.isEmpty()) {
            return
        }

        List<SoftReference<StreamCharBufferKey>> parentBuffersList = new ArrayList<>(parentBuffers)
        for (SoftReference<StreamCharBufferKey> ref : parentBuffersList) {
            final StreamCharBuffer.StreamCharBufferKey parentKey = ref.get()
            boolean removeIt = true
            if (parentKey != null) {
                StreamCharBuffer parent = parentKey.getBuffer()
                removeIt = !parent.bufferChanged(this)
            }
            if (removeIt) {
                parentBuffers.remove(ref)
            }
        }
    }

    int getBufferChangesCounter() {
        return bufferChangesCounter
    }

    protected int markBufferChanged() {
        return bufferChangesCounter++
    }

    @Override
    StreamCharBuffer clone() {
        StreamCharBuffer cloned = new StreamCharBuffer()
        cloned.setNotifyParentBuffersEnabled(false)
        cloned.setAllowSubBuffers(false)
        if (this.size() > 0) {
            cloned.addChunk(readToSingleChunk())
        }
        cloned.setAllowSubBuffers(true)
        return cloned
    }

    void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        int version = in.readInt()
        if (version != EXTERNALIZABLE_VERSION) {
            throw new IOException('Uncompatible version in serialization stream.')
        }
        reset()
        int len = in.readInt()
        if (len > 0) {
            char[] buf = new char[len]
            Reader reader = new InputStreamReader((InputStream) in, 'UTF-8')
            reader.read(buf)
            String str = StringCharArrayAccessor.createString(buf)
            MultipartStringChunk mpStringChunk = new MultipartStringChunk(str)
            int partCount = in.readInt()
            for (int i = 0; i < partCount; i++) {
                EncodingStatePart current = new EncodingStatePart()
                mpStringChunk.appendEncodingStatePart(current)
                current.len = in.readInt()
                int encodersSize = in.readInt()
                Set<Encoder> encoders = null
                if (encodersSize > 0) {
                    encoders = new LinkedHashSet<>()
                    for (int j = 0; j < encodersSize; j++) {
                        String codecName = in.readUTF()
                        boolean safe = in.readBoolean()
                        encoders.add(new SavedEncoder(codecName, safe))
                    }
                }
                current.encodingState = new EncodingStateImpl(encoders, null)
            }
            addChunk(mpStringChunk)
        }
    }

    private static final class SavedEncoder implements Encoder {
        private CodecIdentifier codecIdentifier
        private boolean safe

        SavedEncoder(String codecName, boolean safe) {
            this.codecIdentifier = new DefaultCodecIdentifier(codecName)
            this.safe = safe
        }

        CodecIdentifier getCodecIdentifier() {
            return codecIdentifier
        }

        boolean isSafe() {
            return safe
        }

        Object encode(Object o) {
            throw new UnsupportedOperationException("encode isn't supported for SavedEncoder")
        }

        void markEncoded(CharSequence string) {
            throw new UnsupportedOperationException("markEncoded isn't supported for SavedEncoder")
        }

        boolean isApplyToSafelyEncoded() {
            return false
        }
    }

    void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(EXTERNALIZABLE_VERSION)
        StringChunk stringChunk = readToSingleStringChunk(false)
        if (stringChunk != null && stringChunk.str.length() > 0) {
            char[] buf = StringCharArrayAccessor.getValue(stringChunk.str)
            out.writeInt(buf.length)
            Writer writer = new OutputStreamWriter((OutputStream) out, 'UTF-8')
            writer.write(buf)
            writer.flush()
            if (stringChunk instanceof MultipartStringChunk) {
                MultipartStringChunk mpStringChunk = (MultipartStringChunk) stringChunk
                out.writeInt(mpStringChunk.partCount())
                EncodingStatePart current = mpStringChunk.firstPart
                while (current != null) {
                    out.writeInt(current.len)
                    if (current.encodingState != null && current.encodingState.getEncoders() != null && current.encodingState.getEncoders().size() > 0) {
                        out.writeInt(current.encodingState.getEncoders().size())
                        for (Encoder encoder : current.encodingState.getEncoders()) {
                            out.writeUTF(encoder.getCodecIdentifier().getCodecName())
                            out.writeBoolean(encoder.isSafe())
                        }
                    } else {
                        out.writeInt(0)
                    }
                    current = current.next
                }
            } else {
                out.writeInt(0)
            }
        } else {
            out.writeInt(0)
        }
    }

    StreamCharBuffer encodeToBuffer(Encoder encoder) {
        return encodeToBuffer(encoder, isAllowSubBuffers(), isNotifyParentBuffersEnabled())
    }

    StreamCharBuffer encodeToBuffer(Encoder encoder, boolean allowSubBuffers, boolean notifyParentBuffersEnabled) {
        StreamCharBuffer coded = new StreamCharBuffer(Math.min((Math.max(totalChunkSize, chunkSize) * 12).intdiv(10), maxChunkSize))
        coded.setAllowSubBuffers(allowSubBuffers)
        coded.setNotifyParentBuffersEnabled(notifyParentBuffersEnabled)
        EncodedAppender codedWriter = coded.writer.getEncodedAppender()
        try {
            encodeTo(codedWriter, encoder)
        } catch (IOException e) {
            // Should not ever happen
            log.error('IOException in StreamCharBuffer.encodeToBuffer', e)
        }
        return coded
    }

    StreamCharBuffer encodeToBuffer(List<Encoder> encoders) {
        return encodeToBuffer(encoders, isAllowSubBuffers(), isNotifyParentBuffersEnabled())
    }

    StreamCharBuffer encodeToBuffer(List<Encoder> encoders, boolean allowSubBuffers, boolean notifyParentBuffersEnabled) {
        StreamCharBuffer currentBuffer = this
        for (Encoder encoder : encoders) {
            currentBuffer = currentBuffer.encodeToBuffer(encoder, allowSubBuffers, notifyParentBuffersEnabled)
        }
        return currentBuffer
    }

    void encodeTo(EncodedAppender appender, Encoder encoder) throws IOException {
        if (isPreferSubChunkWhenWritingToOtherBuffer() && appender instanceof StreamCharBufferEncodedAppender) {
            StreamCharBufferWriter writer = ((StreamCharBufferEncodedAppender) appender).getWriter()
            if (writer.appendSubBuffer(this, encoder != null ? Collections.singletonList(encoder) : null)) {
                // subbuffer was appended, so return
                return
            }
        }
        AbstractChunk current = firstChunk
        while (current != null) {
            current.encodeTo(appender, encoder)
            current = current.next
        }
        allocBuffer.encodeTo(appender, encoder)
    }

    boolean isAllowSubBuffers() {
        return subBuffersEnabled && !isConnectedMode()
    }

    void setAllowSubBuffers(boolean allowSubBuffers) {
        this.subBuffersEnabled = allowSubBuffers
    }

    CharSequence encode(Encoder encoder) {
        return encodeToBuffer(encoder)
    }

    Writer getWriterForEncoder() {
        return getWriterForEncoder(null)
    }

    Writer getWriterForEncoder(Encoder encoder) {
        return getWriterForEncoder(encoder, lookupDefaultEncodingStateRegistry())
    }

    protected EncodingStateRegistry lookupDefaultEncodingStateRegistry() {
        EncodingStateRegistryLookup encodingStateRegistryLookup = EncodingStateRegistryLookupHolder.getEncodingStateRegistryLookup()
        return encodingStateRegistryLookup != null ? encodingStateRegistryLookup.lookup() : null
    }

    Writer getWriterForEncoder(Encoder encoder, EncodingStateRegistry encodingStateRegistry) {
        return getWriterForEncoder(encoder, encodingStateRegistry, false)
    }

    Writer getWriterForEncoder(Encoder encoder, EncodingStateRegistry encodingStateRegistry, boolean ignoreEncodingState) {
        EncodedAppender encodedAppender = writer.getEncodedAppender()
        encodedAppender.setIgnoreEncodingState(ignoreEncodingState)
        return new EncodedAppenderWriter(encodedAppender, encoder, encodingStateRegistry)
    }

    boolean isNotifyParentBuffersEnabled() {
        return notifyParentBuffersEnabled
    }

    /**
     * By default the parent buffers (a buffer where this buffer has been appended to) get notified of changed to this buffer.
     *
     * You can control the notification behavior with this property.
     * Setting this property to false will also clear the references to parent buffers if there are any.
     *
     * @param notifyParentBuffersEnabled
     */
    void setNotifyParentBuffersEnabled(boolean notifyParentBuffersEnabled) {
        this.notifyParentBuffersEnabled = notifyParentBuffersEnabled
        if (!notifyParentBuffersEnabled && parentBuffers != null) {
            parentBuffers.clear()
        }
    }

    @Override
    void encodeTo(Writer writer, EncodesToWriter encoder) throws IOException {
        AbstractChunk current = firstChunk
        while (current != null) {
            current.encodeTo(writer, encoder)
            current = current.next
        }
        allocBuffer.encodeTo(writer, encoder)
    }

    /**
     * Delegates methodMissing to String object
     *
     * @param name The name of the method
     * @param args The arguments
     * @return The return value
     */
    Object methodMissing(String name, Object args) {
        String str = this.toString()
        return InvokerHelper.invokeMethod(str, name, args)
    }

    Object asType(Class clazz) {
        if (clazz == String) {
            return toString()
        } else if (clazz == char[]) {
            return toCharArray()
        } else if (clazz == Boolean || clazz == boolean) {
            return asBoolean()
        } else {
            return StringGroovyMethods.asType(toString(), clazz)
        }
    }
}
