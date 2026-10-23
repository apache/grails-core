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
package org.apache.grails.data.core.cli.querytail

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

import org.apache.grails.data.core.querytail.QueryTailRecord

/**
 * Follows a capture file the way {@code tail -f} does: each poll returns the records written
 * since the last one, up to a {@link #CHUNK chunk} of the file at a time, so that a capture of any
 * size is read in pieces; a reader that is {@link #isBehind() behind} has more to read without
 * waiting. A line that has no terminator yet is left for the next poll, so a record the
 * application is still writing is never read in half, and a line longer than a chunk is dropped
 * up to its end, as a line that is not a record is. An application that starts again
 * truncates the file and names its new run in the first line; when that line names another run,
 * or the file shrinks, reading starts over from the beginning. The run tells a restart apart from
 * a file that grew past where the reader was before it was next polled. A file truncated between
 * being measured and being read, so that the read comes up short, is a restart too: it is read again
 * from the beginning in the same poll. A poll that finds the file as long as it left it reads nothing,
 * not even its first line, so an idle command costs nothing; a restart that has written nothing yet
 * is noticed with its first statement.
 *
 * @since 8.1
 */
@CompileStatic
class QueryTailFileReader implements Closeable {

    private static final byte NEWLINE = (byte) 10

    /** How much of the file the first line is looked for in; a header is far shorter. */
    private static final int HEADER_LIMIT = 256

    /** How much of the file one poll reads at most, in bytes. */
    public static final int CHUNK = 1024 * 1024

    final File file
    final int chunk
    private long position
    private long measured = -1L
    private String run
    private boolean restarted
    private boolean behind
    private boolean skipping

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
        this.run = file.exists() ? readRun() : null
        // opened in the middle of a line being written: the rest of that line is not a record
        this.skipping = position > 0 && !endsLine(readFrom(position - 1, position))
    }

    /**
     * @return the records written since the last poll, or since the reader was opened, up to a chunk of the file
     */
    List<QueryTailRecord> poll() {
        restarted = false
        behind = false
        // a read that comes up short is retried once, from the start; the next poll takes it from there
        for (int attempt = 0; attempt < 2; attempt++) {
            long length = file.length()
            if (length == position && length == measured) {
                return []
            }
            measured = length
            String current = length > 0 ? readRun() : null
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
    }

    /**
     * Takes the complete lines out of what was read, and leaves a line still being written for the next poll.
     * A chunk with no line end is a line longer than a chunk: it is dropped, up to the line end a later
     * chunk holds.
     */
    private List<QueryTailRecord> recordsIn(byte[] bytes) {
        int start = 0
        if (skipping) {
            int lineEnd = firstLineEnd(bytes)
            if (lineEnd == 0) {
                position += bytes.length
                return []
            }
            skipping = false
            start = lineEnd
        }
        int consumed = lastLineEnd(bytes)
        if (consumed == 0) {
            if (behind) {
                skipping = true
                position += bytes.length
            }
            return []
        }
        position += consumed
        String text = new String(bytes, start, consumed - start, StandardCharsets.UTF_8)
        List<QueryTailRecord> records = []
        for (String line : text.split('\n')) {
            QueryTailRecord record = QueryTailRecord.parse(line)
            if (record != null) {
                records.add(record)
            }
        }
        records
    }

    private static boolean endsLine(byte[] bytes) {
        bytes.length > 0 && bytes[bytes.length - 1] == NEWLINE
    }

    private static int firstLineEnd(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
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

    private String readRun() {
        byte[] head = readFrom(0, Math.min(file.length(), (long) HEADER_LIMIT))
        for (int i = 0; i < head.length; i++) {
            if (head[i] == NEWLINE) {
                return QueryTailRecord.parseHeader(new String(head, 0, i, StandardCharsets.UTF_8))
            }
        }
        null
    }

    /**
     * @return the bytes between the offsets, or fewer when the file ends before the second one or is
     * gone: it was truncated, or removed, after it was measured
     */
    private byte[] readFrom(long from, long to) {
        byte[] bytes = new byte[(int) Math.max(0L, to - from)]
        RandomAccessFile input
        try {
            input = new RandomAccessFile(file, 'r')
        }
        catch (FileNotFoundException ignored) {
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
