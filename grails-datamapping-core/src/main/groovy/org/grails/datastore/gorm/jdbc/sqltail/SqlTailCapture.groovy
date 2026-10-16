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
package org.grails.datastore.gorm.jdbc.sqltail

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Writes the statements an application runs to the capture file the {@code sql-tail} command
 * tails. One capture is open per file, shared by every data source of the application; the file is
 * truncated when the capture opens, so it holds the statements of the current run only. Each
 * statement is flushed as it is written so that the command sees it at once.
 *
 * <p>A capture is only opened when {@link SqlTailSettings#SETTING_ENABLED} is set; an application
 * started without it never reaches this class.</p>
 *
 * @since 8.1
 */
@CompileStatic
@Slf4j
class SqlTailCapture implements Closeable {

    private static final Map<String, SqlTailCapture> OPEN = new HashMap<>()

    /** The file the statements are written to. */
    final File file

    private final SqlTailRequestAttribution attribution = new SqlTailRequestAttribution()
    private final String key
    private Writer writer

    private SqlTailCapture(File file, String key) {
        this.file = file
        this.key = key
        File parent = file.absoluteFile.parentFile
        try {
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Cannot create the sql-tail capture directory ${parent}")
            }
            this.writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        }
        catch (IOException e) {
            throw new UncheckedIOException("Cannot open the sql-tail capture file ${file}: ${e.message}", e)
        }
    }

    /**
     * Opens the capture for a file, or returns the one already open for it.
     *
     * @param file the capture file
     * @return the capture
     * @throws UncheckedIOException when the file cannot be created
     */
    static synchronized SqlTailCapture open(File file) {
        String key = file.canonicalPath
        SqlTailCapture capture = OPEN.get(key)
        if (capture == null) {
            capture = new SqlTailCapture(file, key)
            OPEN.put(key, capture)
            log.info('Capturing SQL statements to {} for sql-tail', file)
        }
        capture
    }

    /**
     * Opens the capture the settings name.
     *
     * @param settings the sql-tail settings
     * @return the capture
     */
    static SqlTailCapture open(SqlTailSettings settings) {
        open(settings.resolveFile())
    }

    /**
     * Records a statement that is about to run on the current thread.
     *
     * @param dataSource the connection source name the statement runs against
     * @param sql the statement
     */
    void record(String dataSource, String sql) {
        SqlTailRecord record = new SqlTailRecord(timestamp: System.currentTimeMillis(), dataSource: dataSource, sql: sql)
        write(attribution.attribute(record))
    }

    /**
     * Appends a record to the file. A write failure is reported once and ends the capture rather
     * than failing the statement.
     *
     * @param record the record
     */
    synchronized void write(SqlTailRecord record) {
        if (writer == null) {
            return
        }
        try {
            writer.write(record.toLine())
            writer.write('\n')
            writer.flush()
        }
        catch (IOException e) {
            log.warn('Stopped capturing SQL statements to {}: {}', file, e.message)
            closeWriter()
        }
    }

    /**
     * @return whether the capture is still writing to its file
     */
    synchronized boolean isOpen() {
        writer != null
    }

    @Override
    void close() {
        synchronized (SqlTailCapture) {
            OPEN.remove(key)
        }
        synchronized (this) {
            closeWriter()
        }
    }

    private void closeWriter() {
        Writer open = writer
        writer = null
        try {
            open?.close()
        }
        catch (IOException ignored) {
            // the file is no longer written to either way
        }
    }
}
