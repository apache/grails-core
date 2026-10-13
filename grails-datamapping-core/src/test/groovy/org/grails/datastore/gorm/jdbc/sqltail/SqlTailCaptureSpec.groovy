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

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification
import spock.lang.TempDir

class SqlTailCaptureSpec extends Specification {

    @TempDir
    File tempDir

    List<SqlTailCapture> captures = []

    void cleanup() {
        captures*.close()
        RequestContextHolder.resetRequestAttributes()
    }

    void "opening a capture creates the file and its directory and truncates an earlier run"() {
        given:
        File file = new File(tempDir, 'nested/dir/capture.log')
        file.parentFile.mkdirs()
        file.text = 'left over from the previous run\n'

        when:
        SqlTailCapture capture = open(file)

        then:
        file.exists()
        file.text == ''
        capture.open
        capture.file == file
    }

    void "records are written as parseable lines and flushed at once"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SqlTailCapture capture = open(file)

        when:
        capture.record('DEFAULT', 'select\n  b1_0.id\nfrom book b1_0')
        capture.record('reporting', 'select count(*) from book')
        List<SqlTailRecord> records = file.readLines().collect { SqlTailRecord.parse(it) }

        then:
        records.size() == 2
        records*.dataSource == ['DEFAULT', 'reporting']
        records*.sql == ['select b1_0.id from book b1_0', 'select count(*) from book']
        records.every { it.page == "thread:${Thread.currentThread().name}" && !it.webRequest }
        records.every { it.timestamp > 0 }
    }

    void "a record made on a request thread carries the request"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SqlTailCapture capture = open(file)
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/book/show/1')
        request.setAttribute(SqlTailRequestAttribution.CONTROLLER_NAME_ATTRIBUTE, 'book')
        request.setAttribute(SqlTailRequestAttribution.ACTION_NAME_ATTRIBUTE, 'show')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        capture.record('DEFAULT', 'select 1')
        SqlTailRecord record = SqlTailRecord.parse(file.readLines().first())

        then:
        record.requestId == 'r1'
        record.page == 'book/show'
        record.request == 'GET /book/show/1'
    }

    void "one capture is shared by everything that opens the same file"() {
        given:
        File file = new File(tempDir, 'capture.log')

        when:
        SqlTailCapture first = open(file)
        first.record('DEFAULT', 'select 1')
        SqlTailCapture second = SqlTailCapture.open(new File(tempDir, './capture.log'))

        then: 'the second open neither creates a new capture nor truncates the file'
        second.is(first)
        file.readLines().size() == 1
    }

    void "the capture named by the settings is opened"() {
        given:
        File file = new File(tempDir, 'from-settings.log')

        when:
        SqlTailCapture capture = SqlTailCapture.open(new SqlTailSettings(enabled: true, file: file.path))
        captures << capture

        then:
        capture.file == file
        file.exists()
    }

    void "a closed capture writes nothing more and the next open starts a new file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        SqlTailCapture capture = open(file)
        capture.record('DEFAULT', 'select 1')

        when:
        capture.close()
        capture.record('DEFAULT', 'select 2')

        then:
        !capture.open
        file.readLines().size() == 1

        when:
        SqlTailCapture reopened = open(file)

        then:
        !reopened.is(capture)
        file.text == ''
    }

    void "a file that cannot be created is reported"() {
        given:
        File blocker = new File(tempDir, 'blocker')
        blocker.text = 'a file where the directory should be'

        when:
        SqlTailCapture.open(new File(blocker, 'capture.log'))

        then:
        UncheckedIOException e = thrown()
        e.message.contains('sql-tail')
    }

    private SqlTailCapture open(File file) {
        SqlTailCapture capture = SqlTailCapture.open(file)
        captures << capture
        capture
    }
}
