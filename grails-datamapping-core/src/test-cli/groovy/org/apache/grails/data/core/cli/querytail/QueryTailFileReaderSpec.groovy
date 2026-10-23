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

import org.apache.grails.data.core.querytail.QueryTailRecord
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

class QueryTailFileReaderSpec extends Specification {

    @TempDir
    File tempDir

    File file

    void setup() {
        file = new File(tempDir, 'capture.log')
    }

    void "a reader opened on a live file returns only what is written after it"() {
        given:
        file.text = line('r1', 'select 1') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, false)

        expect:
        reader.poll().isEmpty()

        when:
        file << line('r2', 'select 2') + '\n'

        then:
        reader.poll()*.sql == ['select 2']
        reader.poll().isEmpty()
    }

    void "a reader opened from the start returns what the file holds, then what is appended"() {
        given:
        file.text = line('r1', 'select 1') + '\n' + line('r1', 'select 2') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, true)

        expect:
        reader.poll()*.sql == ['select 1', 'select 2']

        when:
        file << line('r2', 'select 3') + '\n'

        then:
        reader.poll()*.sql == ['select 3']
    }

    void "a line still being written is left for the next poll"() {
        given:
        QueryTailFileReader reader = new QueryTailFileReader(file, true)
        file.text = line('r1', 'select 1') + '\n' + '1\tDEFAULT\tr1\tbook/show\tGET /book\tselect 2 fro'

        expect:
        reader.poll()*.sql == ['select 1']
        reader.poll().isEmpty()

        when:
        file << 'm book\n'

        then:
        reader.poll()*.sql == ['select 2 from book']
    }

    void "a file that shrinks is read again from the start and reported as restarted"() {
        given:
        file.text = line('r1', 'select 1') + '\n' + line('r1', 'select 2') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, true)
        reader.poll()

        when: 'the application restarts and truncates the capture'
        file.text = line('r1', 'select 9') + '\n'
        List<QueryTailRecord> records = reader.poll()

        then:
        reader.restarted
        records*.sql == ['select 9']

        when:
        file << line('r2', 'select 10') + '\n'
        records = reader.poll()

        then:
        !reader.restarted
        records*.sql == ['select 10']
    }

    @Unroll
    void "a file #what after the reader measured it is a restart, not a failure"() {
        given: 'a reader that has read the file'
        file.text = header('run-1') + line('r1', 'select 1') + '\n'
        TruncatedWhenMeasured capture = new TruncatedWhenMeasured(file, replacement)
        QueryTailFileReader reader = new QueryTailFileReader(capture, true)
        reader.poll()

        when: 'the application has appended to the file, and truncates it once the reader has measured that'
        file << line('r1', 'select 2') + '\n'
        capture.truncateWhenMeasured = true
        List<QueryTailRecord> records = reader.poll()

        then:
        records.isEmpty()
        reader.restarted
        reader.position == 0
        reader.run == null

        when: 'the new run is written'
        file.text = header('run-2') + line('r1', 'select 3') + '\n'
        records = reader.poll()

        then:
        !reader.restarted
        reader.run == 'run-2'
        records*.sql == ['select 3']

        where:
        what        | replacement
        'truncated' | '# query-tail cap'
        'removed'   | null
    }

    void "a new run that has grown past where the reader was is read from its start"() {
        given:
        file.text = header('run-1') + line('r1', 'select 1') + '\n' + line('r1', 'select 2') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, true)
        reader.poll()
        long read = reader.position

        when: 'the application restarts and writes more than was read before the next poll'
        file.text = header('run-2') + (3..6).collect { line('r1', "select ${it}".toString()) + '\n' }.join('')
        List<QueryTailRecord> records = reader.poll()

        then:
        file.length() > read
        reader.restarted
        reader.run == 'run-2'
        records*.sql == ['select 3', 'select 4', 'select 5', 'select 6']

        when:
        file << line('r2', 'select 7') + '\n'

        then:
        reader.poll()*.sql == ['select 7']
        !reader.restarted
    }

    void "a run whose header is still being written is taken up without starting over again"() {
        given:
        file.text = header('run-1') + line('r1', 'select 1') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, true)
        reader.poll()

        when: 'the application has truncated the file and not finished its header'
        file.text = '# query-tail cap'

        then:
        reader.poll().isEmpty()
        reader.restarted
        reader.run == null

        when:
        file.text = header('run-2') + line('r1', 'select 2') + '\n'

        then:
        reader.poll()*.sql == ['select 2']
        !reader.restarted
        reader.run == 'run-2'
    }

    void "a reader opened on a live file knows its run, and what is appended to it is not a restart"() {
        given:
        file.text = header('run-1') + line('r1', 'select 1') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, false)

        expect:
        reader.run == 'run-1'

        when:
        file << line('r2', 'select 2') + '\n'

        then:
        reader.poll()*.sql == ['select 2']
        !reader.restarted
    }

    void "lines that are not records are skipped"() {
        given:
        file.text = 'not a record\n' + line('r1', 'select 1') + '\n\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, true)

        expect:
        reader.poll()*.sql == ['select 1']
    }

    void "a capture longer than a chunk is read a chunk at a time, and the reader is behind until it has caught up"() {
        given:
        String record = line('r1', 'select 1') + '\n'
        file.text = (1..5).collect { line('r1', "select ${it}".toString()) + '\n' }.join('')
        QueryTailFileReader reader = new QueryTailFileReader(file, true, record.length() * 2 + 10)

        when:
        List<QueryTailRecord> first = reader.poll()

        then: 'a poll reads at most a chunk of complete lines, and says the file holds more'
        first*.sql == ['select 1', 'select 2']
        reader.behind

        when:
        List<QueryTailRecord> rest = []
        while (reader.behind) {
            rest.addAll(reader.poll())
        }

        then:
        rest*.sql == ['select 3', 'select 4', 'select 5']
        !reader.behind
        reader.poll().isEmpty()
    }

    void "a line longer than a chunk is dropped, and the lines after it are read"() {
        given:
        String record = line('r1', 'select 1') + '\n'
        int chunk = record.length() + 10
        file.text = record + line('r1', 'select ' + ('x' * (chunk * 3))) + '\n' + line('r1', 'select 3') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, true, chunk)

        when:
        List<QueryTailRecord> records = []
        int polls = 0
        while (polls == 0 || reader.behind) {
            records.addAll(reader.poll())
            polls++
        }

        then:
        records*.sql == ['select 1', 'select 3']
        reader.position == file.length()
        polls > 3

        when: 'what is appended after it is read as before'
        file << line('r2', 'select 4') + '\n'

        then:
        reader.poll()*.sql == ['select 4']
        !reader.behind
    }

    void "a reader opened in the middle of a line being written drops the rest of that line"() {
        given:
        String whole = line('r1', 'select 1') + '\n'
        int half = whole.length().intdiv(2)
        file.text = whole.substring(0, half)
        QueryTailFileReader reader = new QueryTailFileReader(file, false)

        when:
        file << whole.substring(half) + line('r2', 'select 2') + '\n'

        then:
        reader.poll()*.sql == ['select 2']
    }

    void "a poll that finds the file as long as it left it does not open it"() {
        given:
        SealableFile sealable = new SealableFile(file)
        sealable.text = line('r1', 'select 1') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(sealable, true)

        expect:
        reader.poll()*.sql == ['select 1']

        when:
        sealable.sealed = true
        List<QueryTailRecord> idle = reader.poll()

        then:
        idle.isEmpty()
        notThrown(IllegalStateException)

        when:
        sealable.sealed = false
        sealable << line('r2', 'select 2') + '\n'

        then:
        reader.poll()*.sql == ['select 2']
    }

    void "a file that does not exist yet reads as empty"() {
        given:
        QueryTailFileReader reader = new QueryTailFileReader(file, false)

        expect:
        reader.poll().isEmpty()
        reader.position == 0
    }

    private static String header(String run) {
        QueryTailRecord.header(run) + '\n'
    }

    private static String line(String requestId, String sql) {
        new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: requestId, page: 'book/show',
                request: 'GET /book/show/1', sql: sql).toLine()
    }
}

/**
 * A capture file the application truncates, or removes, the moment the reader has measured it: the
 * length the reader goes on to read is then longer than the file.
 */
class TruncatedWhenMeasured extends File {

    boolean truncateWhenMeasured
    private final String replacement

    TruncatedWhenMeasured(File file, String replacement) {
        super(file.path)
        this.replacement = replacement
    }

    @Override
    long length() {
        long length = super.length()
        if (truncateWhenMeasured) {
            truncateWhenMeasured = false
            if (replacement == null) {
                delete()
            }
            else {
                setText(replacement)
            }
        }
        length
    }
}

/**
 * A capture file that refuses to be opened for reading while sealed: a reader that opens it then is one
 * that reads when nothing has changed. Only an open is refused, as {@code File.length()} asks for the
 * path too on Windows.
 */
class SealableFile extends File {

    boolean sealed

    SealableFile(File file) {
        super(file.path)
    }

    @Override
    String getPath() {
        if (sealed && Thread.currentThread().stackTrace.any { it.className == RandomAccessFile.name }) {
            throw new IllegalStateException('opened while nothing changed')
        }
        super.getPath()
    }
}
