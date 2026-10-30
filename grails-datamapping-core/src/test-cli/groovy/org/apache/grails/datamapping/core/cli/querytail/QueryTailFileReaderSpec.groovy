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

import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import spock.lang.Requires
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
        file.text = line('r1', 'select 1') + '\n' + '1\tDEFAULT\tr1\tbook/show\tGET /book\t-\tselect 2 fro'

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

    void "a line longer than a chunk is read whole over the polls it takes, and the lines after it are read"() {
        given:
        String record = line('r1', 'select 1') + '\n'
        int chunk = record.length() + 10
        String longSql = 'select ' + ('x' * (chunk * 3))
        file.text = record + line('r1', longSql) + '\n' + line('r1', 'select 3') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, true, chunk)

        when:
        List<QueryTailRecord> records = []
        int polls = 0
        while (polls == 0 || reader.behind) {
            records.addAll(reader.poll())
            polls++
        }

        then:
        records*.sql == ['select 1', longSql, 'select 3']
        records[1].requestId == 'r1'
        reader.position == file.length()
        polls > 3

        when: 'what is appended after it is read as before'
        file << line('r2', 'select 4') + '\n'

        then:
        reader.poll()*.sql == ['select 4']
        !reader.behind
    }

    void "a line longer than a line is kept is counted with what was kept, its statement cut and marked"() {
        given:
        String record = line('r1', 'select 1') + '\n'
        int chunk = record.length() + 10
        int kept = chunk * QueryTailFileReader.LONGEST_LINE_CHUNKS
        file.text = line('r2', 'select ' + ('x' * (kept * 2))) + '\n' + record
        QueryTailFileReader reader = new QueryTailFileReader(file, true, chunk)

        when:
        List<QueryTailRecord> records = []
        int polls = 0
        while (polls == 0 || reader.behind) {
            records.addAll(reader.poll())
            polls++
        }

        then: 'the long record keeps its request and page, and its statement is cut at what a line keeps'
        records*.requestId == ['r2', 'r1']
        records[0].page == 'book/show'
        records[0].sql.startsWith('select xxx')
        records[0].sql.endsWith(QueryTailFileReader.CUT)
        records[0].sql.length() < kept
        records[1].sql == 'select 1'
        reader.position == file.length()
    }

    void "a line longer than a chunk that is still being written is read once it ends"() {
        given:
        String record = line('r1', 'select 1') + '\n'
        int chunk = record.length() + 10
        String whole = line('r2', 'select ' + ('x' * (chunk * 3)))
        file.text = whole.substring(0, chunk * 2)
        QueryTailFileReader reader = new QueryTailFileReader(file, true, chunk)

        when: 'the poll reads what there is of it'
        List<QueryTailRecord> early = []
        int polls = 0
        while (polls == 0 || reader.behind) {
            early.addAll(reader.poll())
            polls++
        }

        then:
        early.isEmpty()

        when: 'the rest of it is written'
        file << whole.substring(chunk * 2) + '\n' + record
        List<QueryTailRecord> records = []
        polls = 0
        while (polls == 0 || reader.behind) {
            records.addAll(reader.poll())
            polls++
        }

        then:
        records*.requestId == ['r2', 'r1']
        records[0].sql.length() == chunk * 3 + 'select '.length()
    }

    void "a line longer than a chunk that a restart cut off is not read into the new run"() {
        given:
        String record = line('r1', 'select 1') + '\n'
        int chunk = record.length() + 10
        file.text = header('run-1') + line('r2', 'select ' + ('x' * (chunk * 3))).substring(0, chunk * 2)
        QueryTailFileReader reader = new QueryTailFileReader(file, true, chunk)
        int polls = 0
        while (polls == 0 || reader.behind) {
            reader.poll()
            polls++
        }

        when: 'the application starts again'
        file.text = header('run-2') + record
        List<QueryTailRecord> records = reader.poll()
        boolean restarted = reader.restarted
        while (reader.behind) {
            records.addAll(reader.poll())
        }

        then:
        restarted
        records*.sql == ['select 1']
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

    void "a new run as long as the one that was read is read from its start, as its header names another run"() {
        given:
        file.text = header('run-1') + line('r1', 'select 1') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, true)
        reader.poll()
        long read = reader.position

        when: 'the application restarts and writes as much as was read before the next poll'
        file.text = header('run-2') + line('r1', 'select 2') + '\n'
        List<QueryTailRecord> records = reader.poll()

        then:
        file.length() == read
        reader.restarted
        reader.run == 'run-2'
        records*.sql == ['select 2']

        when: 'nothing is written before the next poll'
        List<QueryTailRecord> idle = reader.poll()

        then: 'the same run as long as the reader left it is idle'
        idle.isEmpty()
        !reader.restarted
        reader.run == 'run-2'
        reader.position == read

        when:
        file << line('r2', 'select 3') + '\n'

        then:
        reader.poll()*.sql == ['select 3']
        !reader.restarted
    }

    void "a file that does not exist yet reads as empty"() {
        given:
        QueryTailFileReader reader = new QueryTailFileReader(file, false)

        expect:
        reader.poll().isEmpty()
        reader.position == 0
    }

    @Requires({ !os.windows })
    void "a file that is a directory when it is read fails the poll, saying so, rather than read as empty"() {
        given: 'a reader that has read the file'
        file.text = header('run-1') + line('r1', 'select 1') + '\n'
        QueryTailFileReader reader = new QueryTailFileReader(file, true)
        reader.poll()

        when: 'the file is replaced by a directory, which holds an entry so that it measures longer than none'
        assert file.delete()
        assert file.mkdirs()
        new File(file, 'entry').text = 'x'
        reader.poll()

        then:
        IOException e = thrown()
        e.message == 'it is a directory'
    }

    void "why a file cannot be read is told for a directory, and nothing for a file or none"() {
        expect:
        QueryTailFileReader.unreadable(tempDir) == 'it is a directory'
        QueryTailFileReader.unreadable(file) == null

        when:
        file.text = header('run-1')

        then:
        QueryTailFileReader.unreadable(file) == null
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
