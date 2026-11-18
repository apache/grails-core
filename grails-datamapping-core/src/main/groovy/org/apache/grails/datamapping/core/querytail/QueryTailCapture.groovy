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
package org.apache.grails.datamapping.core.querytail

import java.nio.channels.ClosedByInterruptException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.FileLockInterruptionException
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.BiFunction
import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import org.springframework.util.ClassUtils

import org.apache.grails.datastore.core.querytail.QueryTailSettings

/**
 * Writes the statements an application runs to the capture file the {@code query-tail} command
 * tails. Each session factory, Neo4j driver, MongoDB client or simple datastore that captures opens its own capture
 * and closes it when it closes; the captures of one file share it, so every data source of an
 * application writes to the one file. The file is truncated when the first capture opens it, so it
 * holds the statements of the current run only, and its first line names that run. It is closed
 * when the last capture closes, and opened afresh by the next one. Each statement is written whole
 * and at once, so that the command sees it as soon as it is recorded.
 *
 * <p>JVMs that run at the same time with capture enabled, such as the parallel forks of a test task,
 * share the file: a lock file beside it tells the first to start, which truncates the file and names
 * the run, from the others, which append to that run without truncating it and number their
 * requests apart, so that the command reads one run from all of them. The lock file is beside the
 * file itself, whatever symbolic link a JVM names it by, so JVMs that name it differently still
 * share its run. A build can name the run with {@link #RUN_PROPERTY}, as the Grails Gradle plugin
 * does for the test tasks of one invocation: a capture that finds the file headed by that run
 * appends to it, so that the datastores a test JVM opens and closes one after another, and the test
 * tasks that run one after another, all add to the one run.</p>
 *
 * <p>Two applications that run in one JVM, each with this class of its own, as two web applications
 * of one servlet container do, cannot share a file: the locks of a file are held by the process, so
 * neither could hold one apart from the other, nor keep the other's when it closes the file. The
 * capture that finds the other application's lock on the file is reported and stopped before it
 * writes anything, and the other goes on; each application is given a file of its own with
 * {@link QueryTailSettings#SETTING_FILE}.</p>
 *
 * <p>Recording never fails the statement it records: a statement that cannot be written, or whose
 * request cannot be told, is left out, and a capture that has stopped does no work at all. A file
 * whose write fails is written no more until the next capture opened on it opens it again, for every
 * capture of it, and appends to the run it was writing while the file still holds that run, so that
 * what was recorded before the failure is kept. Opening never fails what opens it either: a file
 * that cannot be created is reported, and its capture has stopped before it records anything, and
 * one whose lock file another JVM keeps from being had for {@link #STARTING_WAIT}, as a JVM paused
 * while it starts a capture does, is reported and stopped likewise, so a setting meant to diagnose
 * an application never keeps it from starting, nor blocks the statements it runs.</p>
 *
 * <p>A process that captures can be checkpointed with CRaC, which refuses to checkpoint one that
 * holds a file open: when {@code org.crac} is on the classpath, as checkpointing with Spring requires,
 * every capture file is closed before the checkpoint, along with its lock file, and opened again after
 * the restore as a capture opening it first would open it, so that a restored application starts a run
 * of its own unless another JVM is writing one. A capture opened while the process is checkpointed
 * opens its file with the others, and a statement run meanwhile is not recorded.</p>
 *
 * <p>A statement is recorded without its values: {@link QueryTailRedactor} replaces its literals and
 * drops its comments before it is written, in the {@link #syntax} of the database it runs on. A
 * capture keeps what the {@link #REMEMBERED_REDACTIONS} statements it recorded most recently were
 * redacted to, letting go of the one recorded least recently to keep another, so that a statement run again, as the statements an application generates with parameters for its
 * values are, is not read again on the thread that runs it, which a syntax that reads it every way it
 * may be read, as that of a database that is not known does, would read it many times. A statement
 * that is not text, such as a MongoDB command, or that is built without values, as the operations of
 * the simple datastore are, has its values taken out by whoever records it,
 * through {@link #recordRedacted}.</p>
 *
 * <p>A capture is only opened when
 * {@link QueryTailSettings#SETTING_ENABLED} is set; an
 * application started without it never reaches this class.</p>
 *
 * @since 9.0
 */
@CompileStatic
@Slf4j
class QueryTailCapture implements Closeable {

    /**
     * The system property a build names the run of its captures with, which every JVM of the run is
     * given: a capture that finds its file headed by that run appends to it rather than starting
     * another; {@code grails.gorm.queryTail.run}.
     */
    public static final String RUN_PROPERTY = 'grails.gorm.queryTail.run'

    private static final Map<String, CaptureFile> OPEN = new HashMap<>()

    /**
     * What the opening of each file takes turns on, so that one file is opened by one capture at a time while
     * another file, and the closing of any, need not wait for another JVM to let a starting writer in. The turn
     * of a file is kept only while a capture opens it or waits to.
     */
    private static final ConcurrentHashMap<String, Turn> OPENING = new ConcurrentHashMap<>()

    /** How many symbolic links a capture file is followed through, as the operating system would at most. */
    private static final int MAX_LINKS = 40

    /**
     * How long a capture waits for another JVM that is starting a capture of the same file, which takes a
     * moment, before it gives up and stops rather than block what opens it.
     */
    @PackageScope
    static final Duration STARTING_WAIT = Duration.ofSeconds(10)

    /** How often a capture waiting for another JVM to start its capture asks again. */
    private static final long STARTING_POLL_MILLIS = 10L

    /** How many statements a capture keeps the redaction of, those it recorded most recently. */
    @PackageScope
    static final int REMEMBERED_REDACTIONS = 1024

    /** How long a statement may be for a capture to keep its redaction, so that what it keeps stays small. */
    @PackageScope
    static final int REMEMBERED_LENGTH = 8192

    /** Whether {@code org.crac} is on the classpath, through which the capture files are closed for a checkpoint. */
    private static final boolean CHECKPOINTS = ClassUtils.isPresent('org.crac.Resource', QueryTailCapture.classLoader)

    /**
     * Taken shared by every capture that opens a file, and exclusively to close the files for a checkpoint and to open
     * them again after the restore, so that no file is opened while the others are closed.
     */
    private static final ReentrantReadWriteLock CHECKPOINT = new ReentrantReadWriteLock()

    /** Whether the capture files are closed for a checkpoint; changed only under the {@link #CHECKPOINT} lock. */
    private static volatile boolean checkpointed

    /** The file the statements are written to. */
    final File file

    /** The syntax of the database the statements of this capture run on, which their values are found by. */
    volatile QueryTailSyntax syntax = QueryTailSyntax.UNKNOWN

    private final CaptureFile captureFile
    private final AtomicBoolean closed = new AtomicBoolean()

    /**
     * The redaction of each statement recorded most recently, by the statement, with the syntax it was redacted in, in
     * the order the statements were last recorded; read and changed only while holding it.
     */
    private final Map<String, Redaction> redactions = new LinkedHashMap<String, Redaction>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Redaction> eldest) {
            size() > QueryTailCapture.REMEMBERED_REDACTIONS
        }
    }
    private final AtomicBoolean reportedLeftOut = new AtomicBoolean()

    private QueryTailCapture(File file, CaptureFile captureFile) {
        this.file = file
        this.captureFile = captureFile
        if (captureFile == null) {
            closed.set(true)
        }
    }

    /**
     * Opens a capture of a file. The first capture of a file truncates it; a capture opened while
     * another of the same file is open shares it. Every capture opened must be closed.
     *
     * @param file the capture file
     * @return the capture, which has already stopped when the file cannot be created, or could be
     * created but not written; the reason is logged
     */
    static QueryTailCapture open(File file) {
        String key
        try {
            key = target(file).canonicalPath
        }
        catch (IOException | RuntimeException e) {
            return stopped(file, e)
        }
        Turn turn = OPENING.compute(key, { String opened, Turn waiting ->
            (waiting ?: new Turn()).take()
        } as BiFunction<String, Turn, Turn>)
        try {
            synchronized (turn) {
                return openInTurn(file, key)
            }
        }
        finally {
            OPENING.computeIfPresent(key, { String opened, Turn taken ->
                taken.release() ? null : taken
            } as BiFunction<String, Turn, Turn>)
        }
    }

    /**
     * Opens a capture of a file, which no other capture of this JVM is opening: one joining the capture file
     * this JVM has open on it, else one of its own. While the process is checkpointed, a file of its own is
     * opened after the restore, with the others.
     */
    private static QueryTailCapture openInTurn(File file, String key) {
        Lock opening = CHECKPOINT.readLock()
        opening.lock()
        try {
            CaptureFile captureFile = join(key)
            if (captureFile != null) {
                if (!checkpointed) {
                    // a file whose write failed is opened again, for every capture of it
                    captureFile.reopen()
                }
                return new QueryTailCapture(file, captureFile)
            }
            if (CHECKPOINTS) {
                QueryTailCheckpoint.register()
            }
            captureFile = new CaptureFile(file, key)
            if (checkpointed) {
                captureFile.suspend()
            }
            else {
                try {
                    // outside the lock every capture of this JVM opens and closes under: another JVM may hold the file
                    captureFile.open()
                }
                catch (IOException | RuntimeException e) {
                    return stopped(file, e)
                }
            }
            synchronized (OPEN) {
                captureFile.captures++
                OPEN.put(key, captureFile)
            }
            if (captureFile.suspended) {
                log.info('Capturing statements to {} for query-tail once the process is restored', file)
            }
            else if (captureFile.open && captureFile.joined) {
                log.info('Capturing statements to {} for query-tail, in the run already written to it', file)
            }
            else if (captureFile.open) {
                log.info('Capturing statements to {} for query-tail', file)
            }
            new QueryTailCapture(file, captureFile)
        }
        finally {
            opening.unlock()
        }
    }

    /**
     * Closes every capture file before a checkpoint, keeping the captures that write to it, so that the process
     * holds none of them open.
     */
    @PackageScope
    static void suspendAll() {
        Lock exclusive = CHECKPOINT.writeLock()
        exclusive.lock()
        try {
            List<CaptureFile> files
            synchronized (OPEN) {
                checkpointed = true
                files = new ArrayList<>(OPEN.values())
            }
            for (CaptureFile captureFile : files) {
                captureFile.suspend()
            }
        }
        finally {
            exclusive.unlock()
        }
    }

    /**
     * Opens every capture file closed for a checkpoint again after the restore. A file that cannot be opened again
     * is written no more, as one whose write fails is, until the next capture opened on it opens it again.
     */
    @PackageScope
    static void resumeAll() {
        Lock exclusive = CHECKPOINT.writeLock()
        exclusive.lock()
        try {
            List<CaptureFile> files
            synchronized (OPEN) {
                checkpointed = false
                files = new ArrayList<>(OPEN.values())
            }
            for (CaptureFile captureFile : files) {
                captureFile.resume()
            }
        }
        finally {
            exclusive.unlock()
        }
    }

    /**
     * @return the capture file of this JVM on a key, counted as having one more capture, or null when there is none:
     * one that is open, closed for a checkpoint, or written no more since a write failed, which the capture joining
     * it opens again; the last capture of a file lets it go as it closes it
     */
    private static CaptureFile join(String key) {
        synchronized (OPEN) {
            CaptureFile captureFile = OPEN.get(key)
            if (captureFile != null) {
                captureFile.captures++
            }
            captureFile
        }
    }

    /**
     * @return a capture of a file that cannot be created, which has stopped before recording anything
     */
    private static QueryTailCapture stopped(File file, Exception failure) {
        log.warn('Cannot capture statements to {} for query-tail, so none are recorded: {}', file, failure.message)
        new QueryTailCapture(file, null)
    }

    /**
     * @param file a capture file
     * @return the file it names, through every symbolic link it is named by, even one to a file not created yet,
     * which its canonical path alone would not follow
     */
    private static File target(File file) throws IOException {
        Path path = file.toPath().toAbsolutePath()
        for (int links = 0; Files.isSymbolicLink(path) && links < MAX_LINKS; links++) {
            path = path.resolveSibling(Files.readSymbolicLink(path))
        }
        path.toFile()
    }

    /**
     * Records a statement that is about to run on the current thread, without its values.
     *
     * @param dataSource the connection source name the statement runs against
     * @param sql the statement
     */
    void record(String dataSource, String sql) {
        if (!isOpen()) {
            return
        }
        try {
            write(dataSource, redact(sql), false)
        }
        catch (RuntimeException | LinkageError e) {
            leftOut(e)
        }
    }

    /**
     * @return the statement redacted in the syntax of this capture, as it was the last time it was recorded in that
     * syntax, if it was lately
     */
    private String redact(String sql) {
        QueryTailSyntax current = syntax
        boolean remembers = sql != null && sql.length() <= REMEMBERED_LENGTH
        if (remembers) {
            Redaction remembered
            synchronized (redactions) {
                remembered = redactions.get(sql)
            }
            if (remembered != null && remembered.syntax.is(current)) {
                return remembered.redacted
            }
        }
        // read outside the lock, so that a statement read for the first time holds up no other thread
        String redacted = QueryTailRedactor.redact(sql, current)
        if (remembers) {
            synchronized (redactions) {
                redactions.put(sql, new Redaction(current, redacted))
            }
        }
        redacted
    }

    /**
     * Records a statement that is about to run on the current thread and that holds no value any
     * more: a MongoDB command, which is a document rather than text, is redacted field by field by
     * the listener that records it, and the simple datastore builds its statements from the query,
     * without a value. Nothing of the statement is changed here: it is recorded as a
     * {@link QueryTailRecord#document document}, kept as it is. Its values are taken out only when
     * the capture is still writing.
     *
     * @param dataSource the connection source name the statement runs against
     * @param statement takes the values out of the statement
     */
    void recordRedacted(String dataSource, Supplier<String> statement) {
        if (!isOpen()) {
            return
        }
        try {
            write(dataSource, statement.get(), true)
        }
        catch (RuntimeException | LinkageError e) {
            leftOut(e)
        }
    }

    /**
     * Reports a statement that could not be recorded, which is left out rather than failing the
     * statement the application runs: the first one of a capture as a warning, the others quietly.
     */
    private void leftOut(Throwable failure) {
        if (reportedLeftOut.compareAndSet(false, true)) {
            log.warn('A statement could not be recorded to {} for query-tail and is left out; any other is left out quietly', file, failure)
        }
        else {
            log.debug('A statement could not be recorded to {} for query-tail and is left out', file, failure)
        }
    }

    private void write(String dataSource, String statement, boolean document) {
        QueryTailRecord record = new QueryTailRecord(timestamp: System.currentTimeMillis(), dataSource: dataSource,
                sql: statement, document: document)
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
        synchronized (OPEN) {
            if (--captureFile.captures == 0) {
                // A replacement must not join this writer's run before its coordination lock is released.
                captureFile.close()
                if (OPEN.get(captureFile.key).is(captureFile)) {
                    OPEN.remove(captureFile.key)
                }
            }
        }
    }

    /** What a statement was redacted to, and the syntax it was redacted in. */
    @CompileStatic
    private static final class Redaction {

        final QueryTailSyntax syntax
        final String redacted

        Redaction(QueryTailSyntax syntax, String redacted) {
            this.syntax = syntax
            this.redacted = redacted
        }
    }

    /**
     * The turn the captures opening one file take, kept in {@link #OPENING} while any of them opens the
     * file or waits to.
     */
    @CompileStatic
    private static final class Turn {

        /** How many captures open the file or wait to; changed only as the turn is computed in {@link #OPENING}. */
        private int takers

        Turn take() {
            takers++
            this
        }

        /**
         * @return whether no capture is left to open the file, so that the turn is let go
         */
        boolean release() {
            --takers == 0
        }
    }

    /**
     * A capture file and the captures writing to it. The file is written by one stream, each line in
     * one write of its own to the end of the file, which the threads recording statements make at once
     * rather than in turn: only closing the file waits for the writes under way. The requests of every
     * data source are numbered by one attribution, so that two requests never share an id. A JVM that
     * joins the run of another numbers the requests of every run it joins by one counter, so that it
     * repeats none of its ids when it closes its capture and joins the same run again.
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
     *
     * <p>A statement may run on a thread that has been interrupted, as a task cancelled while it runs
     * is, and an interrupt closes a channel the thread is using. The file is written through a stream,
     * which an interrupt leaves alone, so that the capture records on. The lock file can only be locked
     * through a channel: it is locked with the interrupt of the opening thread set aside, and locked
     * afresh when one comes while it waits for the lock, so that an interrupt never makes the capture
     * take another JVM's file for one it may truncate. The interrupt is set again once it is open. A
     * starting writer waits for the {@link #STARTING} byte at most {@link QueryTailCapture#STARTING_WAIT},
     * interrupted or not, and then gives up on the file rather than truncate one another JVM may be
     * starting to write.</p>
     *
     * <p>A write that fails ends the writing of the file, but the captures of the file keep it: the next
     * capture opened on it opens it again for all of them, as a starting writer would, but that a file
     * still headed by the run it was writing is appended to rather than truncated, and its requests are
     * numbered on, so that a disk that filled for a moment costs the statements run while it was full
     * and nothing recorded before.</p>
     */
    @CompileStatic
    private static class CaptureFile {

        /** The byte of the lock file a starting writer holds, exclusively, while it decides whether to truncate. */
        private static final long STARTING = 0L

        /** The byte of the lock file every writer holds, shared, while it writes. */
        private static final long WRITING = 1L

        /**
         * Numbers the requests of every run this JVM joins, so that a JVM that closes its capture and
         * joins the same run again never repeats an id it gave a request of that run before.
         */
        private static final AtomicLong JOINED_REQUESTS = new AtomicLong()

        final File file
        final String key

        /** Numbers the requests of the run the file was opened in last. */
        private volatile QueryTailRequestAttribution attribution

        /**
         * Whether the file was opened last in a run another capture started, one of another JVM writing the file
         * or the one the build named, rather than starting one.
         */
        private volatile boolean joined

        /** Whether the file is closed for a checkpoint, to be opened again after the restore. */
        private volatile boolean suspended

        /**
         * The run the file was opened in last, which a capture opening it again after a write failed appends to while
         * the file is still headed by it; null before the file is opened, and after a checkpoint, whose restore starts
         * a run of its own.
         */
        private volatile String run

        /** Whether the last capture of the file has closed it, which nothing opens again; guarded by {@link #writes}. */
        private boolean closed

        /** How many captures are open on the file; guarded by {@link QueryTailCapture#OPEN}. */
        int captures

        /** Read without the lock every write takes, so that a statement checks whether to record at no cost. */
        private volatile OutputStream output

        /**
         * Held shared by every write, so that the threads recording statements write at once, and exclusively to
         * close the file, which waits for the writes under way and lets no other start.
         */
        private final ReentrantReadWriteLock writes = new ReentrantReadWriteLock()

        private FileChannel lockChannel
        private FileLock writing

        CaptureFile(File file, String key) {
            this.file = file
            this.key = key
        }

        QueryTailRequestAttribution getAttribution() {
            attribution
        }

        boolean isJoined() {
            joined
        }

        boolean isSuspended() {
            suspended
        }

        /**
         * Opens the file: truncated and headed by a new run when this is the first writer, else as it is.
         */
        void open() throws IOException {
            boolean interrupted = Thread.interrupted()
            long deadline = System.nanoTime() + STARTING_WAIT.toNanos()
            try {
                boolean started = false
                while (!started) {
                    try {
                        start(deadline)
                        started = true
                    }
                    catch (ClosedByInterruptException | FileLockInterruptionException ignored) {
                        // an interrupt closed the lock file before the header was written: the file holds
                        // nothing of this capture, which opens it again
                        interrupted = true
                        Thread.interrupted()
                    }
                }
            }
            finally {
                if (interrupted) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        /**
         * Opens the file once, numbering the requests of the run it opens it in, which it joins or starts, before
         * anything can be written to it: on from the requests it numbered in that run before, when it was writing
         * that run before a write failed.
         *
         * @param deadline when to give up waiting for another JVM to start, as {@link System#nanoTime} tells it
         */
        private void start(long deadline) throws IOException {
            // the directory of the file itself, which a link may name from elsewhere
            File parent = new File(key).parentFile
            boolean first
            boolean resumed = false
            try {
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("Cannot create the query-tail capture directory ${parent}")
                }
                lockChannel = openLockFile(file, key)
                FileLock starting = lockChannel == null ? null : lockStarting(deadline)
                try {
                    FileLock alone = lockChannel == null ? null : lock(lockChannel, false, true)
                    first = lockChannel == null || alone != null
                    alone?.release()
                    // as the header spells it, so that the run the header names is read back as the one named
                    String named = QueryTailRecord.runName(System.getProperty(RUN_PROPERTY))
                    // the run this file was written in before a write failed, else the one the build named, which a
                    // capture opened before this one in the build started
                    String continued = run ?: named
                    resumed = first && continued != null && continued == QueryTailRecord.readRun(file)
                    if (first && !resumed) {
                        Files.newByteChannel(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING).close()
                    }
                    joined = !first || resumed
                    // the run being written, which a starter names and a joiner reads from the header the starter
                    // wrote before it let the next starter in
                    String current = first ? (resumed ? continued : (named ?: UUID.randomUUID().toString())) :
                            QueryTailRecord.readRun(file)
                    // the requests of a run this file was written in before are numbered on, so that none repeats
                    if (attribution == null || current == null || current != run) {
                        attribution = joined ?
                                new QueryTailRequestAttribution(ProcessHandle.current().pid() + '-', JOINED_REQUESTS) :
                                new QueryTailRequestAttribution()
                    }
                    run = current
                    output = new FileOutputStream(file, true)
                    writing = lockChannel == null ? null : lock(lockChannel, true, false)
                    if (first && !resumed) {
                        // nothing else writes to the file before it opens, so the header is the first line of the run;
                        // a header that cannot be written leaves the file unopened
                        output.write((QueryTailRecord.header(current) + '\n').getBytes(StandardCharsets.UTF_8))
                    }
                }
                finally {
                    // unless an interrupt closed the lock file, which released the lock
                    if (starting?.valid) {
                        starting.release()
                    }
                }
            }
            catch (IOException | RuntimeException e) {
                closeChannels()
                throw e
            }
        }

        /**
         * Takes the {@link #STARTING} byte of the lock file, asking for it until the deadline, so that a JVM that
         * holds it, as one paused while it starts a capture does, keeps neither this capture nor what opens it
         * waiting for good. A file system that cannot lock it, as some network and shared folders cannot, leaves
         * the writer uncoordinated, as if there were no lock file, rather than keeping the capture from opening.
         *
         * @param deadline when to give up, as {@link System#nanoTime} tells it
         * @return the lock, or null when there is none to be had
         * @throws IOException when another JVM holds the byte past the deadline, which the capture gives up on
         * rather than take the file for one it may truncate
         */
        private FileLock lockStarting(long deadline) throws IOException {
            while (true) {
                FileLock starting
                try {
                    starting = lockChannel.tryLock(STARTING, 1L, false)
                }
                catch (ClosedByInterruptException e) {
                    // the file system can lock it; the opening thread was interrupted as it asked
                    throw e
                }
                catch (OverlappingFileLockException ignored) {
                    throw sharedInProcess()
                }
                catch (IOException e) {
                    log.debug('The lock file of the query-tail capture {} cannot be locked: {}', file, e.message)
                    closeQuietly(lockChannel)
                    lockChannel = null
                    return null
                }
                if (starting != null) {
                    return starting
                }
                if (System.nanoTime() - deadline >= 0) {
                    throw new IOException("another JVM has been starting a capture of it for ${STARTING_WAIT.toSeconds()} seconds")
                }
                try {
                    Thread.sleep(STARTING_POLL_MILLIS)
                }
                catch (InterruptedException ignored) {
                    // as an interrupt while the lock is waited for is taken
                    throw new FileLockInterruptionException()
                }
            }
        }

        /**
         * Appends a record to the file.
         */
        void write(QueryTailRecord record) {
            writeLine(record.toLine())
        }

        boolean isOpen() {
            output != null
        }

        void close() {
            Lock exclusive = writes.writeLock()
            exclusive.lock()
            try {
                closed = true
                suspended = false
                closeChannels()
            }
            finally {
                exclusive.unlock()
            }
        }

        /**
         * Closes the file for a checkpoint, once the writes under way have finished, unless its last capture has
         * closed it. The captures of the file record nothing until it is {@linkplain #resume opened again}.
         */
        void suspend() {
            Lock exclusive = writes.writeLock()
            exclusive.lock()
            try {
                if (closed) {
                    return
                }
                suspended = true
                // the restore starts a run of its own
                run = null
                if (output != null) {
                    closeChannels()
                    log.info('Closed the query-tail capture {} for a checkpoint; it is opened again after the restore', file)
                }
            }
            finally {
                exclusive.unlock()
            }
        }

        /**
         * Opens the file again after a restore, as it is opened first, unless its last capture has closed it
         * meanwhile. No statement is written before the file is opened, so the header of a new run is its first line.
         * A file that cannot be opened is written no more until the next capture opened on it opens it again.
         */
        void resume() {
            Lock exclusive = writes.writeLock()
            exclusive.lock()
            try {
                if (closed || !suspended) {
                    return
                }
                suspended = false
                open()
                log.info(joined ? 'Capturing statements to {} for query-tail again after the restore, in the run already written to it' :
                        'Capturing statements to {} for query-tail again after the restore', file)
            }
            catch (IOException | RuntimeException e) {
                log.warn('Cannot capture statements to {} for query-tail after the restore, so none are recorded: {}', file, e.message)
            }
            finally {
                exclusive.unlock()
            }
        }

        /**
         * Opens the file again after a write failed, for every capture of it, unless it is open, closed for a
         * checkpoint, or closed by its last capture: in the run it was writing when the file is still headed by it,
         * else as a starting writer opens it.
         */
        void reopen() {
            Lock exclusive = writes.writeLock()
            exclusive.lock()
            try {
                if (closed || suspended || output != null) {
                    return
                }
                open()
                log.info(joined ? 'Capturing statements to {} for query-tail again, in the run already written to it' :
                        'Capturing statements to {} for query-tail again', file)
            }
            catch (IOException | RuntimeException e) {
                log.warn('Cannot capture statements to {} for query-tail again, so none are recorded: {}', file, e.message)
            }
            finally {
                exclusive.unlock()
            }
        }

        /**
         * Appends a line to the file, in one write to its end, so that the lines of two threads or two
         * JVMs never mix. A write failure is reported and ends the writing of the file rather than
         * failing the statement, until the next capture opened on the file {@linkplain #reopen opens it again}.
         */
        private void writeLine(String line) {
            byte[] bytes = (line + '\n').getBytes(StandardCharsets.UTF_8)
            IOException failure = null
            OutputStream open = null
            Lock shared = writes.readLock()
            shared.lock()
            try {
                open = output
                if (open == null) {
                    return
                }
                open.write(bytes)
            }
            catch (IOException e) {
                failure = e
            }
            finally {
                shared.unlock()
            }
            if (failure != null) {
                stop(failure, open)
            }
        }

        /**
         * Ends the writing of the file after a write to a stream failed, unless that stream is written no more
         * already: another write that failed, a close, or a capture that opened the file again since has been first.
         */
        private void stop(IOException failure, OutputStream failed) {
            Lock exclusive = writes.writeLock()
            exclusive.lock()
            try {
                if (output == null || !output.is(failed)) {
                    return
                }
                log.warn('Stopped capturing statements to {} until a capture is opened on it again: {}', file, failure.message)
                closeChannels()
            }
            finally {
                exclusive.unlock()
            }
        }

        private void closeChannels() {
            OutputStream open = output
            output = null
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

        private static void closeQuietly(Closeable closeable) {
            try {
                closeable?.close()
            }
            catch (IOException ignored) {
                // the file is no longer written to either way
            }
        }

        /**
         * @param file the capture file, as it was named
         * @param key the canonical path of the capture file, which every JVM writing it shares however it names it
         * @return the channel of the lock file beside the capture file, or null when it cannot be created
         */
        private static FileChannel openLockFile(File file, String key) {
            try {
                return FileChannel.open(new File(key + '.lock').toPath(), StandardOpenOption.CREATE,
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
         * @return the lock, or null when another JVM holds the byte and the lock was a probe
         * @throws IOException when this JVM holds the byte through another channel, as another application
         * that runs in it does once it writes the file
         */
        private static FileLock lock(FileChannel channel, boolean shared, boolean probe) throws IOException {
            try {
                probe ? channel.tryLock(WRITING, 1L, shared) : channel.lock(WRITING, 1L, shared)
            }
            catch (OverlappingFileLockException ignored) {
                throw sharedInProcess()
            }
        }

        /**
         * @return why a capture cannot open a file whose lock file this JVM has locked through another channel than
         * its own: only another application that runs in this JVM, with this class of its own, opens one, since the
         * captures of an application share the one channel of each file
         */
        private static IOException sharedInProcess() {
            new IOException('another application that runs in this JVM captures to it; give each application a file ' +
                    "of its own with ${QueryTailSettings.SETTING_FILE}")
        }
    }
}
