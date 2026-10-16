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
package org.grails.datastore.gorm.jdbc.sqltail.cli

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.jdbc.sqltail.SqlTailRecord

/**
 * Follows a capture file the way {@code tail -f} does: each poll returns the records written
 * since the last one. A line that has no terminator yet is left for the next poll, so a record
 * the application is still writing is never read in half. When the file shrinks, the application
 * has restarted and truncated it, and reading starts over from the beginning.
 *
 * @since 8.1
 */
@CompileStatic
class SqlTailFileReader implements Closeable {

    private static final byte NEWLINE = (byte) 10

    final File file
    private long position
    private boolean restarted

    /**
     * @param file the capture file
     * @param fromStart whether to read what the file already holds, or only what is written from now on
     */
    SqlTailFileReader(File file, boolean fromStart) {
        this.file = file
        this.position = fromStart ? 0 : file.length()
    }

    /**
     * @return the records written since the last poll, or since the reader was opened
     */
    List<SqlTailRecord> poll() {
        restarted = false
        long length = file.length()
        if (length < position) {
            position = 0
            restarted = true
        }
        if (length == position) {
            return []
        }
        byte[] bytes = readFrom(position, length)
        int consumed = lastLineEnd(bytes)
        if (consumed == 0) {
            return []
        }
        position += consumed
        String text = new String(bytes, 0, consumed, StandardCharsets.UTF_8)
        List<SqlTailRecord> records = []
        for (String line : text.split('\n')) {
            SqlTailRecord record = SqlTailRecord.parse(line)
            if (record != null) {
                records.add(record)
            }
        }
        records
    }

    /**
     * @return whether the last poll found the file truncated, which an application restart does
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

    @Override
    void close() {
        // the file is opened and closed per poll
    }

    private static int lastLineEnd(byte[] bytes) {
        for (int i = bytes.length - 1; i >= 0; i--) {
            if (bytes[i] == NEWLINE) {
                return i + 1
            }
        }
        0
    }

    private byte[] readFrom(long from, long to) {
        RandomAccessFile input = new RandomAccessFile(file, 'r')
        try {
            input.seek(from)
            byte[] bytes = new byte[(int) (to - from)]
            input.readFully(bytes)
            bytes
        }
        finally {
            input.close()
        }
    }
}
