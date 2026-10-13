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

import spock.lang.Specification

class SqlTailRecordSpec extends Specification {

    void "a record round-trips through its line"() {
        given:
        SqlTailRecord record = new SqlTailRecord(timestamp: 1760290412345L, dataSource: 'DEFAULT', requestId: 'r17',
                page: 'book/show', request: 'GET /book/show/42', sql: 'select b1_0.id from book b1_0 where b1_0.id=?')

        when:
        String line = record.toLine()
        SqlTailRecord parsed = SqlTailRecord.parse(line)

        then:
        line == '1760290412345\tDEFAULT\tr17\tbook/show\tGET /book/show/42\tselect b1_0.id from book b1_0 where b1_0.id=?'
        parsed == record
        parsed.webRequest
    }

    void "whitespace in a statement is collapsed so that it can never break the line"() {
        given:
        SqlTailRecord record = new SqlTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: 'r1', page: 'a/b',
                request: 'GET /a', sql: '\n    select\n\t\tb1_0.id\n    from  book b1_0\n')

        expect:
        record.toLine().endsWith('\tselect b1_0.id from book b1_0')
        SqlTailRecord.parse(record.toLine()).sql == 'select b1_0.id from book b1_0'
    }

    void "fields without a value are written as a dash and read back as null"() {
        given:
        SqlTailRecord record = new SqlTailRecord(timestamp: 5L, dataSource: 'DEFAULT', page: 'thread:main', sql: 'select 1')

        when:
        String line = record.toLine()
        SqlTailRecord parsed = SqlTailRecord.parse(line)

        then:
        line == '5\tDEFAULT\t-\tthread:main\t-\tselect 1'
        parsed.requestId == null
        parsed.request == null
        !parsed.webRequest
        parsed == record
    }

    void "a line that is not a record parses as null"() {
        expect:
        SqlTailRecord.parse(line) == null

        where:
        line << [null, '', 'not a record', '5\tDEFAULT\t-\tthread:main', 'x\tDEFAULT\t-\tthread:main\t-\tselect 1',
                 '5\tDEFAULT\t-\tthread:main\t-\tselect 1\textra']
    }
}
