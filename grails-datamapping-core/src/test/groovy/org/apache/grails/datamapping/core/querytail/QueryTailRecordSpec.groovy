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

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import spock.lang.Specification
import spock.lang.TempDir

class QueryTailRecordSpec extends Specification {

    static final String RECORD = new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', sql: 'select 1').toLine()

    @TempDir
    File tempDir

    void "a record round-trips through its line"() {
        given:
        QueryTailRecord record = new QueryTailRecord(timestamp: 1760290412345L, dataSource: 'DEFAULT', requestId: 'r17',
                page: 'book/show', request: 'GET /book/show/42', sql: 'select b1_0.id from book b1_0 where b1_0.id=?')

        when:
        String line = record.toLine()
        QueryTailRecord parsed = QueryTailRecord.parse(line)

        then:
        line == '1760290412345\tDEFAULT\tr17\tbook/show\tGET /book/show/42\t-\tselect b1_0.id from book b1_0 where b1_0.id=?'
        parsed == record
        parsed.webRequest
    }

    void "whitespace in a statement is collapsed so that it can never break the line"() {
        given:
        QueryTailRecord record = new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: 'r1', page: 'a/b',
                request: 'GET /a', sql: '\n    select\n\t\tb1_0.id\n    from  book b1_0\n')

        expect:
        record.toLine().endsWith('\tselect b1_0.id from book b1_0')
        QueryTailRecord.parse(record.toLine()).sql == 'select b1_0.id from book b1_0'
    }

    void "whitespace in a quoted name or literal is kept, with a tab or a line break in it escaped so that it never breaks the line"() {
        given:
        QueryTailRecord record = new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', sql: sql)

        when:
        String line = record.toLine()

        then:
        line.readLines().size() == 1
        line.count(QueryTailRecord.FIELD_SEPARATOR) == 6
        line.endsWith(QueryTailRecord.FIELD_SEPARATOR + written)
        QueryTailRecord.parse(line).sql == read

        where:
        sql                                      || written                               | read
        'select  id from "order  lines"'         || 'select id from "order  lines"'       | 'select id from "order  lines"'
        'select\n[a\tb],  `c\r\nd` from t'       || 'select [a\\tb], `c\\r\\nd` from t'       | 'select [a\tb], `c\r\nd` from t'
        "select 'it''s  x' from \"a\"\"  b\""   || "select 'it''s  x' from \"a\"\"  b\""   | "select 'it''s  x' from \"a\"\"  b\""
        'select "a\\nb", "c\\\td"'              || 'select "a\\\\nb", "c\\\\\\td"'          | 'select "a\\nb", "c\\\td"'
        'select x\\y from t'                     || 'select x\\\\y from t'                 | 'select x\\y from t'
        'select "a  b\nc'                        || 'select "a  b\\nc'                     | 'select "a  b\nc'
    }

    void "a document, a MongoDB command, is written as it was recorded and reads back as one: #command"() {
        when:
        String line = new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', sql: command, document: true).toLine()
        QueryTailRecord parsed = QueryTailRecord.parse(line)

        then:
        line.readLines().size() == 1
        line.split(QueryTailRecord.FIELD_SEPARATOR, -1)[5] == 'document'
        parsed.document
        parsed.sql == command

        where:
        command << ['find db.book {"filter": {"a\\"  b": ?}}', 'find db.book {"filter": {"a\\" b": ?}}',
                    'find db.book {"filter": {"a\\\\": ?, "  c": ?}}', 'endSessions {"endSessions": [{"id": ?}]}',
                    'aggregate db.my  books {"pipeline": [{"$match": {"a\\"  b": ?}}]}',
                    'find db.book(archive) {"filter": {"a\\"  b": ?}}', 'find db.wide  collection',
                    'insert db.a\tb {"documents": [{"line\nbreak": ?}]}']
    }

    void "a statement that is not a document is written as text, however much it looks like one"() {
        when:
        QueryTailRecord parsed = QueryTailRecord.parse(new QueryTailRecord(timestamp: 1L, sql: 'find db.wide  collection').toLine())

        then:
        !parsed.document
        parsed.sql == 'find db.wide collection'
    }

    void "fields without a value are written as a dash and read back as null"() {
        given:
        QueryTailRecord record = new QueryTailRecord(timestamp: 5L, dataSource: 'DEFAULT', page: 'thread:main', sql: 'select 1')

        when:
        String line = record.toLine()
        QueryTailRecord parsed = QueryTailRecord.parse(line)

        then:
        line == '5\tDEFAULT\t-\tthread:main\t-\t-\tselect 1'
        parsed.requestId == null
        parsed.request == null
        !parsed.webRequest
        parsed == record
    }

    void "an empty field reads back as null, as a dash does"() {
        expect:
        QueryTailRecord.parse('5\t\t-\tthread:main\t\t\tselect 1') ==
                new QueryTailRecord(timestamp: 5L, page: 'thread:main', sql: 'select 1')
    }

    void "a line that is not a record parses as null"() {
        expect:
        QueryTailRecord.parse(line) == null

        where:
        line << [null, '', 'not a record', '5\tDEFAULT\t-\tthread:main', 'x\tDEFAULT\t-\tthread:main\t-\t-\tselect 1',
                 '5\tDEFAULT\t-\tthread:main\t-\tselect 1', '5\tDEFAULT\t-\tthread:main\t-\t-\tselect 1\textra']
    }

    void "the header of a capture file names its run and is not a record"() {
        when:
        String header = QueryTailRecord.header('3f2a\t9c')

        then:
        header == '# query-tail capture 3f2a 9c'
        QueryTailRecord.parseHeader(header) == '3f2a 9c'
        QueryTailRecord.parse(header) == null
    }

    void "the run of a capture file is read back from its first line, #shape"() {
        given:
        File file = new File(tempDir, 'capture.log')
        if (content != null) {
            file.text = content
        }

        expect:
        QueryTailRecord.readRun(file) == run

        where:
        shape                                       | content                                                    || run
        'a header alone'                            | QueryTailRecord.header('a-run') + '\n'                     || 'a-run'
        'a header before records'                   | QueryTailRecord.header('a-run') + '\n' + RECORD + '\n'     || 'a-run'
        'none while the header is not written whole'| QueryTailRecord.header('a-run')                            || null
        'none when the first line is a record'      | RECORD + '\n'                                              || null
        'none in an empty file'                     | ''                                                         || null
        'none when there is no file'                | null                                                       || null
        'none past the length a header is read to'  | QueryTailRecord.HEADER_PREFIX + 'r' * 300 + '\n'           || null
        'a run too long for a header, as named'     | QueryTailRecord.header('r' * 300) + '\n'                   || QueryTailRecord.runName('r' * 300)
    }

    void "a run is named as a header names it, which reads back whole: #shape"() {
        expect:
        QueryTailRecord.runName(run) == named

        where:
        shape                                           | run                     || named
        'with its whitespace collapsed'                 | ' nightly \t  run '     || 'nightly run'
        'as long as a header can be read back with'     | 'r' * 234               || 'r' * 234
        'too long for that, by its digest'              | 'r' * 235               || 'sha256:' + sha256('r' * 235)
        'of characters a byte each would fit, by bytes' | '\u00e9' * 118           || 'sha256:' + sha256('\u00e9' * 118)
        'of fewer of them, kept'                        | '\u00e9' * 117           || '\u00e9' * 117
        'none for whitespace'                           | ' \t '                  || null
        'none for none'                                 | null                    || null
    }

    void "the run of a capture file is read back on a thread that has been interrupted, which stays interrupted"() {
        given:
        File file = new File(tempDir, 'capture.log')
        file.text = QueryTailRecord.header('a-run') + '\n'

        when:
        Thread.currentThread().interrupt()
        String run = QueryTailRecord.readRun(file)
        boolean interrupted = Thread.interrupted()

        then:
        run == 'a-run'
        interrupted

        cleanup:
        Thread.interrupted()
    }

    void "a directory names no run"() {
        expect:
        QueryTailRecord.readRun(tempDir) == null
    }

    void "a line that is not a header names no run"() {
        expect:
        QueryTailRecord.parseHeader(line) == null

        where:
        line << [null, '', '# query-tail capture ', '# something else', '5\tDEFAULT\t-\tthread:main\t-\tselect 1']
    }

    private static String sha256(String run) {
        MessageDigest.getInstance('SHA-256').digest(run.getBytes(StandardCharsets.UTF_8)).encodeHex().toString()
    }
}
