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

import org.grails.datastore.gorm.jdbc.sqltail.SqlTailRecord
import spock.lang.Specification
import spock.lang.TempDir

class SqlTailFileReaderSpec extends Specification {

    @TempDir
    File tempDir

    File file

    void setup() {
        file = new File(tempDir, 'capture.log')
    }

    void "a reader opened on a live file returns only what is written after it"() {
        given:
        file.text = line('r1', 'select 1') + '\n'
        SqlTailFileReader reader = new SqlTailFileReader(file, false)

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
        SqlTailFileReader reader = new SqlTailFileReader(file, true)

        expect:
        reader.poll()*.sql == ['select 1', 'select 2']

        when:
        file << line('r2', 'select 3') + '\n'

        then:
        reader.poll()*.sql == ['select 3']
    }

    void "a line still being written is left for the next poll"() {
        given:
        SqlTailFileReader reader = new SqlTailFileReader(file, true)
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
        SqlTailFileReader reader = new SqlTailFileReader(file, true)
        reader.poll()

        when: 'the application restarts and truncates the capture'
        file.text = line('r1', 'select 9') + '\n'
        List<SqlTailRecord> records = reader.poll()

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

    void "lines that are not records are skipped"() {
        given:
        file.text = 'not a record\n' + line('r1', 'select 1') + '\n\n'
        SqlTailFileReader reader = new SqlTailFileReader(file, true)

        expect:
        reader.poll()*.sql == ['select 1']
    }

    void "a file that does not exist yet reads as empty"() {
        given:
        SqlTailFileReader reader = new SqlTailFileReader(file, false)

        expect:
        reader.poll().isEmpty()
        reader.position == 0
    }

    private static String line(String requestId, String sql) {
        new SqlTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: requestId, page: 'book/show',
                request: 'GET /book/show/1', sql: sql).toLine()
    }
}
