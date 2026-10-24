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
package org.apache.grails.data.core.querytail

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Writes the statements an application runs to the capture file the {@code query-tail} command
 * tails. Each session factory, Neo4j driver or MongoDB client that captures opens its own capture
 * and closes it when it closes; the captures of one file share it, so every data source of an
 * application writes to the one file. The file is truncated when the first capture opens it, so it
 * holds the statements of the current run only, and its first line names that run. It is closed
 * when the last capture closes, and opened afresh by the next one. Each statement is written whole
 * and at once, so that the command sees it as soon as it is recorded.
 *
 * <p>JVMs that run at the same time with capture enabled, such as the parallel forks of a test task,
 * share the file: a lock file beside it tells the first to start, which truncates the file and names
 * the run, from the others, which append to that run without truncating it and number their
 * requests apart, so that the command reads one run from all of them.</p>
 *
 * <p>A statement is recorded without its values: {@link QueryTailRedactor} replaces its literals and
 * drops its comments before it is written, in the {@link #syntax} of the database it runs on. A
 * statement that is not text, such as a MongoDB command, has its values taken out by whoever records
 * it, through {@link #recordRedacted}.</p>
 *
 * <p>A capture is only opened when {@link QueryTailSettings#SETTING_ENABLED} is set; an application
 * started without it never reaches this class.</p>
 *
 * @since 8.1
 */
@CompileStatic
@Slf4j
class QueryTailCapture implements Closeable {

    private static final Map<String, CaptureFile> OPEN = new HashMap<>()

    /** The file the statements are written to. */
    final File file

    /** The syntax of the database the statements of this capture run on, which their values are found by. */
    volatile QueryTailSyntax syntax = QueryTailSyntax.UNKNOWN

    private final CaptureFile captureFile
    private final AtomicBoolean closed = new AtomicBoolean()

    private QueryTailCapture(File file, CaptureFile captureFile) {
        this.file = file
        this.captureFile = captureFile
    }

    /**
     * Opens a capture of a file. The first capture of a file truncates it; a capture opened while
     * another of the same file is open shares it. Every capture opened must be closed.
     *
     * @param file the capture file
     * @return the capture
     * @throws UncheckedIOException when the file cannot be created
     */
    static QueryTailCapture open(File file) {
        String key
        try {
            key = file.canonicalPath
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cannot resolve the query-tail capture file ${file}: ${e.message}", e)
        }
        synchronized (OPEN) {
            CaptureFile captureFile = OPEN.get(key)
            if (captureFile == null) {
                captureFile = new CaptureFile(file, key)
                OPEN.put(key, captureFile)
                if (captureFile.joined) {
                    log.info('Capturing statements to {} for query-tail, in the run of another JVM writing it', file)
                }
                else {
                    log.info('Capturing statements to {} for query-tail', file)
                }
            }
            captureFile.captures++
            return new QueryTailCapture(file, captureFile)
        }
    }

    /**
     * Opens the capture the settings name.
     *
     * @param settings the query-tail settings
     * @return the capture
     */
    static QueryTailCapture open(QueryTailSettings settings) {
        open(settings.resolveFile())
    }

    /**
     * Records a statement that is about to run on the current thread, without its values.
     *
     * @param dataSource the connection source name the statement runs against
     * @param sql the statement
     */
    void record(String dataSource, String sql) {
        write(dataSource, QueryTailRedactor.redact(sql, syntax))
    }

    /**
     * Records a statement that is about to run on the current thread and that holds no value any
     * more: a MongoDB command, which is a document rather than text, is redacted field by field by
     * the listener that records it. Nothing of the statement is changed here.
     *
     * @param dataSource the connection source name the statement runs against
     * @param statement the statement, with its values already taken out
     */
    void recordRedacted(String dataSource, String statement) {
        write(dataSource, statement)
    }

    private void write(String dataSource, String statement) {
        if (closed.get()) {
            return
        }
        QueryTailRecord record = new QueryTailRecord(timestamp: System.currentTimeMillis(), dataSource: dataSource,
                sql: statement)
        captureFile.write(captureFile.attribution.attribute(record))
    }

    /**
     * @return whether this capture is still writing to its file
     */
    boolean isOpen() {
        !closed.get() && captureFile.open
    }

    /**
     * Closes this capture. The file is closed once every capture of it is.
     */
    @Override
    void close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        boolean last
        synchronized (OPEN) {
            last = --captureFile.captures == 0
            if (last && OPEN.get(captureFile.key).is(captureFile)) {
                OPEN.remove(captureFile.key)
            }
        }
        if (last) {
            captureFile.close()
        }
    }

    /**
     * A capture file and the captures writing to it. The file is written by one channel, a line at a
     * time, and the requests of every data source are numbered by one attribution, so that two
     * requests never share an id.
     *
     * <p>A lock file beside the capture coordinates the JVMs writing it. Every writer holds a shared
     * lock on the {@link #WRITING} byte of the lock file while it writes. A starting writer first
     * takes the {@link #STARTING} byte exclusively, so that starters take turns, then tries the
     * {@link #WRITING} byte exclusively: when it gets it, no other JVM is writing, and it truncates the
     * file and writes the header of a new run before it lets the next starter in, so that the header
     * is the first line of the run whatever a joiner writes after it; when it does not, it joins the
     * run being written. Either way it takes the {@link #WRITING} byte shared before it lets go of
     * {@link #STARTING} and holds it until it closes. A lock file that cannot be created, as beside a
     * device, leaves the writer uncoordinated, as the only one.</p>
     */
    @CompileStatic
    private static class CaptureFile {

        /** The byte of the lock file a starting writer holds, exclusively, while it decides whether to truncate. */
        private static final long STARTING = 0L

        /** The byte of the lock file every writer holds, shared, while it writes. */
        private static final long WRITING = 1L

        final File file
        final String key
        final QueryTailRequestAttribution attribution

        /** Whether this JVM joined the run of another JVM writing the file, rather than starting one. */
        final boolean joined

        /** How many captures are open on the file; guarded by {@link QueryTailCapture#OPEN}. */
        int captures

        private FileChannel channel
        private FileChannel lockChannel
        private FileLock writing

        CaptureFile(File file, String key) {
            this.file = file
            this.key = key
            File parent = file.absoluteFile.parentFile
            boolean first
            try {
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("Cannot create the query-tail capture directory ${parent}")
                }
                lockChannel = openLockFile(file)
                FileLock starting = lockChannel == null ? null : lockChannel.lock(STARTING, 1L, false)
                try {
                    FileLock alone = lockChannel == null ? null : lock(lockChannel, false, true)
                    first = lockChannel == null || alone != null
                    alone?.release()
                    if (first) {
                        Files.newByteChannel(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING).close()
                    }
                    channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                            StandardOpenOption.APPEND)
                    writing = lockChannel == null ? null : lock(lockChannel, true, false)
                    if (first) {
                        writeLine(QueryTailRecord.header(UUID.randomUUID().toString()))
                    }
                }
                finally {
                    starting?.release()
                }
            }
            catch (IOException e) {
                closeChannels()
                throw new UncheckedIOException("Cannot open the query-tail capture file ${file}: ${e.message}", e)
            }
            this.joined = !first
            this.attribution = new QueryTailRequestAttribution(joined ? ProcessHandle.current().pid() + '-' : '')
        }

        /**
         * Appends a record to the file.
         */
        void write(QueryTailRecord record) {
            writeLine(record.toLine())
        }

        synchronized boolean isOpen() {
            channel != null
        }

        synchronized void close() {
            closeChannels()
        }

        /**
         * Appends a line to the file, in one write, so that the lines of two JVMs never mix. A write
         * failure is reported once and ends the capture of the file rather than failing the statement;
         * the file is let go, so that the next capture opened on it starts afresh.
         */
        private void writeLine(String line) {
            boolean failed = false
            synchronized (this) {
                if (channel == null) {
                    return
                }
                try {
                    ByteBuffer bytes = ByteBuffer.wrap((line + '\n').getBytes(StandardCharsets.UTF_8))
                    while (bytes.hasRemaining()) {
                        channel.write(bytes)
                    }
                }
                catch (IOException e) {
                    log.warn('Stopped capturing statements to {}: {}', file, e.message)
                    closeChannels()
                    failed = true
                }
            }
            if (failed) {
                synchronized (OPEN) {
                    if (OPEN.get(key).is(this)) {
                        OPEN.remove(key)
                    }
                }
            }
        }

        private void closeChannels() {
            FileChannel open = channel
            channel = null
            closeQuietly(open)
            FileLock held = writing
            writing = null
            try {
                held?.release()
            }
            catch (IOException ignored) {
                // the lock goes with the channel
            }
            FileChannel locks = lockChannel
            lockChannel = null
            closeQuietly(locks)
        }

        private static void closeQuietly(FileChannel channel) {
            try {
                channel?.close()
            }
            catch (IOException ignored) {
                // the file is no longer written to either way
            }
        }

        /**
         * @return the channel of the lock file beside the capture file, or null when it cannot be created
         */
        private static FileChannel openLockFile(File file) {
            try {
                return FileChannel.open(new File(file.path + '.lock').toPath(), StandardOpenOption.CREATE,
                        StandardOpenOption.READ, StandardOpenOption.WRITE)
            }
            catch (IOException e) {
                log.debug('No lock file for the query-tail capture {}: {}', file, e.message)
                return null
            }
        }

        /**
         * Locks the {@link #WRITING} byte of the lock file.
         *
         * @param shared whether the lock is shared, as a writer's, rather than exclusive, as the probe of a starter
         * @param probe whether to give up at once when another JVM holds the byte, rather than wait for it
         * @return the lock, or null when it is not to be had: another JVM holds the byte and the lock was a probe,
         * or this JVM holds it through another channel already
         */
        private static FileLock lock(FileChannel channel, boolean shared, boolean probe) {
            try {
                probe ? channel.tryLock(WRITING, 1L, shared) : channel.lock(WRITING, 1L, shared)
            }
            catch (OverlappingFileLockException ignored) {
                null
            }
        }
    }
}
