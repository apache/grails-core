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
package org.apache.grails.datamapping.core.cli.querytail

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

import org.apache.grails.datamapping.core.querytail.QueryTailRecord

/**
 * Follows a capture file the way {@code tail -f} does: each poll returns the records written
 * since the last one, up to a {@link #CHUNK chunk} of the file at a time, so that a capture of any
 * size is read in pieces; a reader that is {@link #isBehind() behind} has more to read without
 * waiting. A line that has no terminator yet is left for the next poll, so a record the
 * application is still writing is never read in half. A line longer than a chunk is kept from
 * poll to poll until it ends, up to {@link #LONGEST_LINE_CHUNKS} chunks of it: of a longer one,
 * such as a statement too large to be one an application runs, the record is read from what was
 * kept, with its statement cut there and marked {@link #CUT}. An application that starts again
 * truncates the file and names its new run in the first line; when that line names another run,
 * or the file shrinks, reading starts over from the beginning. The run tells a restart apart from
 * a file that grew past where the reader was before it was next polled. A file truncated between
 * being measured and being read, so that the read comes up short, is a restart too: it is read again
 * from the beginning in the same poll. Every poll reads the first line, so a restart is noticed even
 * when the new capture is as long as the one the reader had read; a poll that finds the same run and
 * the file as long as it left it reads nothing more. A file that is there but cannot be read, as a
 * directory or a file the reader may not read cannot, fails the poll rather than read as one removed.
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailFileReader implements Closeable {

    private static final byte NEWLINE = (byte) 10

    /** How much of the file one poll reads at most, in bytes. */
    public static final int CHUNK = 1024 * 1024

    /** How many chunks of a line are kept; the longest command MongoDB runs is as long as sixteen of the default. */
    public static final int LONGEST_LINE_CHUNKS = 16

    /** What the statement of a record cut short ends with. */
    public static final String CUT = ' ... (cut)'

    final File file
    final int chunk
    private long position
    private long measured = -1L
    private String run
    private boolean restarted
    private boolean behind
    private boolean skipping
    /** A line begun in an earlier poll that has not ended yet, as much of it as is kept; null when there is none. */
    private ByteArrayOutputStream pending
    /** Whether the line pending is longer than what is kept of it. */
    private boolean cut

    /**
     * @param file the capture file
     * @param fromStart whether to read what the file already holds, or only what is written from now on
     */
    QueryTailFileReader(File file, boolean fromStart) {
        this(file, fromStart, CHUNK)
    }

    /**
     * @param file the capture file
     * @param fromStart whether to read what the file already holds, or only what is written from now on
     * @param chunk how much of the file one poll reads at most, in bytes
     */
    QueryTailFileReader(File file, boolean fromStart, int chunk) {
        this.file = file
        this.chunk = chunk
        this.position = fromStart ? 0 : file.length()
        this.run = QueryTailRecord.readRun(file)
        // opened in the middle of a line being written: the rest of that line is not a record
        this.skipping = position > 0 && !endsLine(readFrom(position - 1, position))
    }

    /**
     * @return the records written since the last poll, or since the reader was opened, up to a chunk of the file
     * @throws IOException when the file is there but cannot be read, saying why
     */
    List<QueryTailRecord> poll() throws IOException {
        restarted = false
        behind = false
        // a read that comes up short is retried once, from the start; the next poll takes it from there
        for (int attempt = 0; attempt < 2; attempt++) {
            long length = file.length()
            // the run, not the length, tells a restart that wrote as much as was read from an idle file
            String current = length > 0 ? QueryTailRecord.readRun(file) : null
            if (length == position && length == measured && current == run) {
                return []
            }
            measured = length
            if (length < position || (current != null && run != null && current != run)) {
                restart()
            }
            if (current != null || restarted) {
                // a file truncated before its header was written names its run on a later poll
                run = current
            }
            if (length <= position) {
                return []
            }
            long end = Math.min(length, position + chunk)
            byte[] bytes = readFrom(position, end)
            if (bytes.length == end - position) {
                behind = end < length
                return recordsIn(bytes)
            }
            // the application truncated the file after it was measured: it has started again
            restart()
            run = null
        }
        []
    }

    /**
     * @return whether the file holds more than the last poll read, which a poll reads without waiting for
     */
    boolean isBehind() {
        behind
    }

    /**
     * @return whether the last poll found a new run in the file, which an application restart writes
     */
    boolean isRestarted() {
        restarted
    }

    /**
     * @return how far the file has been read
     */
    long getPosition() {
        position
    }

    /**
     * @return the run that wrote the file, as its first line names it; null before the line is complete
     */
    String getRun() {
        run
    }

    @Override
    void close() {
        // the file is opened and closed per poll
    }

    private void restart() {
        position = 0
        restarted = true
        skipping = false
        pending = null
        cut = false
    }

    /**
     * Takes the complete lines out of what was read, and leaves a line still being written for the next poll.
     * A full chunk with no line end is a line longer than a chunk: it is kept, and the next polls add to
     * it until a chunk holds its end.
     */
    private List<QueryTailRecord> recordsIn(byte[] bytes) {
        int start = 0
        if (skipping) {
            int lineEnd = firstLineEnd(bytes, 0)
            if (lineEnd == 0) {
                position += bytes.length
                return []
            }
            skipping = false
            start = lineEnd
        }
        List<QueryTailRecord> records = []
        if (pending != null) {
            int lineEnd = firstLineEnd(bytes, start)
            if (lineEnd == 0) {
                keep(bytes, start, bytes.length)
                position += bytes.length
                return records
            }
            keep(bytes, start, lineEnd - 1)
            add(records, pendingLine())
            start = lineEnd
        }
        int consumed = lastLineEnd(bytes)
        if (consumed <= start) {
            if (start == 0 && behind) {
                pending = new ByteArrayOutputStream()
                keep(bytes, 0, bytes.length)
                position += bytes.length
            }
            else {
                position += start
            }
            return records
        }
        position += consumed
        String text = new String(bytes, start, consumed - start, StandardCharsets.UTF_8)
        for (String line : text.split('\n')) {
            add(records, QueryTailRecord.parse(line))
        }
        records
    }

    private static void add(List<QueryTailRecord> records, QueryTailRecord record) {
        if (record != null) {
            records.add(record)
        }
    }

    /**
     * Adds to the line pending what a poll read of it, as far as a line is kept.
     */
    private void keep(byte[] bytes, int from, int to) {
        int room = Math.max(0, (int) Math.min((long) chunk * LONGEST_LINE_CHUNKS - pending.size(), Integer.MAX_VALUE))
        int count = Math.min(to - from, room)
        pending.write(bytes, from, count)
        cut = cut || count < to - from
    }

    /**
     * @return the record the line pending holds, its statement cut where the line was, or null when it is no record
     */
    private QueryTailRecord pendingLine() {
        QueryTailRecord record = QueryTailRecord.parse(new String(pending.toByteArray(), StandardCharsets.UTF_8))
        if (record != null && cut) {
            record.sql = (record.sql ?: '') + CUT
        }
        pending = null
        cut = false
        record
    }

    /**
     * @param file a capture file
     * @return why the file cannot be read though it is there, or null when it can be, or is not there
     */
    static String unreadable(File file) {
        file.isDirectory() ? 'it is a directory' : file.exists() && !file.canRead() ? 'it cannot be read' : null
    }

    private static boolean endsLine(byte[] bytes) {
        bytes.length > 0 && bytes[bytes.length - 1] == NEWLINE
    }

    private static int firstLineEnd(byte[] bytes, int from) {
        for (int i = from; i < bytes.length; i++) {
            if (bytes[i] == NEWLINE) {
                return i + 1
            }
        }
        0
    }

    private static int lastLineEnd(byte[] bytes) {
        for (int i = bytes.length - 1; i >= 0; i--) {
            if (bytes[i] == NEWLINE) {
                return i + 1
            }
        }
        0
    }

    /**
     * @return the bytes between the offsets, or fewer when the file ends before the second one or is
     * gone: it was truncated, or removed, after it was measured
     * @throws IOException when the file is there but cannot be read, saying why
     */
    private byte[] readFrom(long from, long to) throws IOException {
        byte[] bytes = new byte[(int) Math.max(0L, to - from)]
        RandomAccessFile input
        try {
            input = new RandomAccessFile(file, 'r')
        }
        catch (FileNotFoundException e) {
            // only a file that is gone is read as one that is empty, which the next run writes again
            String unreadable = unreadable(file)
            if (unreadable != null) {
                throw new IOException(unreadable, e)
            }
            return new byte[0]
        }
        try {
            input.seek(from)
            int read = 0
            while (read < bytes.length) {
                int count = input.read(bytes, read, bytes.length - read)
                if (count < 0) {
                    return Arrays.copyOf(bytes, read)
                }
                read += count
            }
            bytes
        }
        finally {
            input.close()
        }
    }
}
