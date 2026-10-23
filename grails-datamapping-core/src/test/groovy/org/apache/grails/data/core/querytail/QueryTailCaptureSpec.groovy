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
package org.apache.grails.data.core.querytail

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Timeout

class QueryTailCaptureSpec extends Specification {

    @TempDir
    File tempDir

    List<QueryTailCapture> captures = []

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
        QueryTailCapture capture = open(file)

        then: 'the file holds only the header naming this run'
        file.exists()
        file.readLines().size() == 1
        QueryTailRecord.parseHeader(file.readLines().first())
        capture.open
        capture.file == file
    }

    void "records are written as parseable lines and flushed at once"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)

        when:
        capture.record('DEFAULT', 'select\n  b1_0.id\nfrom book b1_0')
        capture.record('reporting', 'select count(*) from book')
        List<QueryTailRecord> records = records(file)

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
        QueryTailCapture capture = open(file)
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/book/show/1')
        request.setAttribute(QueryTailRequestAttribution.CONTROLLER_NAME_ATTRIBUTE, 'book')
        request.setAttribute(QueryTailRequestAttribution.ACTION_NAME_ATTRIBUTE, 'show')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        capture.record('DEFAULT', 'select 1')
        QueryTailRecord record = records(file).first()

        then:
        record.requestId == 'r1'
        record.page == 'book/show'
        record.request == 'GET /book/show/?'
    }

    void "the captures of one file share it until the last of them closes"() {
        given:
        File file = new File(tempDir, 'capture.log')

        when:
        QueryTailCapture first = open(file)
        first.record('DEFAULT', 'select 1 from book')
        QueryTailCapture second = open(new File(tempDir, './capture.log'))
        second.record('reporting', 'select 2 from author')

        then: 'the second capture neither truncates the file nor starts another run'
        !second.is(first)
        records(file)*.dataSource == ['DEFAULT', 'reporting']
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1

        when: 'one of them closes'
        first.close()
        first.record('DEFAULT', 'select 3 from book')
        second.record('reporting', 'select 4 from author')

        then: 'the other keeps writing'
        !first.open
        second.open
        records(file)*.sql == ['select ? from book', 'select ? from author', 'select ? from author']

        when: 'the last one closes'
        second.close()
        second.record('reporting', 'select 5 from author')

        then:
        !second.open
        records(file).size() == 3
    }

    void "a file whose last capture closed is truncated by the next capture, which starts a new run"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        capture.record('DEFAULT', 'select 1 from book')
        String firstRun = QueryTailRecord.parseHeader(file.readLines().first())
        capture.close()

        when:
        QueryTailCapture reopened = open(file)
        reopened.record('DEFAULT', 'select 2 from author')

        then:
        reopened.open
        records(file)*.sql == ['select ? from author']
        QueryTailRecord.parseHeader(file.readLines().first()) != firstRun
    }

    void "requests are numbered once per file, whichever capture records them"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture first = open(file)
        QueryTailCapture second = open(file)

        when:
        inRequest('/book/show/1') { first.record('DEFAULT', 'select 1') }
        inRequest('/author/show/1') { second.record('reporting', 'select 2') }

        then:
        records(file)*.requestId == ['r1', 'r2']
    }

    void "the values of a statement and its comments are not recorded"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)

        when:
        capture.record('DEFAULT', "/* key=abc123 */ select * from account where password='example-secret' and pin=4242")

        then:
        records(file)*.sql == ['select * from account where password=? and pin=?']
        !file.text.contains('example-secret')
        !file.text.contains('abc123')
        !file.text.contains('4242')
    }

    void "a statement is redacted in the syntax of its capture's database, and withheld when its values cannot be found"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)

        expect: 'a capture starts out not knowing the database'
        capture.syntax == QueryTailSyntax.UNKNOWN

        when:
        capture.record('DEFAULT', 'select * from account where password = "example-secret"')
        capture.syntax = QueryTailSyntax.MYSQL
        capture.record('DEFAULT', 'select * from account where password = "example-secret"')
        capture.record('DEFAULT', "select * from account where password = 'example-secret")

        then:
        records(file)*.sql == [QueryTailRedactor.WITHHELD, 'select * from account where password = ?', QueryTailRedactor.WITHHELD]
        !file.text.contains('example-secret')
    }

    void "a double-quoted value is not recorded from a Transact-SQL database, whose double quotes can delimit a string"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        capture.syntax = QueryTailSyntax.TRANSACT_SQL

        when:
        capture.record('DEFAULT', 'SET QUOTED_IDENTIFIER OFF; SELECT "example-secret";')

        then:
        records(file)*.sql == ['SET QUOTED_IDENTIFIER OFF; SELECT ?;']
        !file.text.contains('example-secret')
    }

    void "a value the client percent-encoded in the request path does not reach the file"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        MockHttpServletRequest request = new MockHttpServletRequest('GET', '/search/secret%40example.com')
        request.setParameter('q', 'secret@example.com')
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

        when:
        capture.record('DEFAULT', 'select 1')

        then:
        records(file)*.request == ['GET /search/?']
        !file.text.contains('secret%40example.com')
        !file.text.contains('secret@example.com')
    }

    void "an H2 array literal is not recorded whether whitespace or a comment stands before its bracket"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        capture.syntax = QueryTailSyntax.H2

        when:
        capture.record('DEFAULT', "select ARRAY ['example-secret'] from t")
        capture.record('DEFAULT', "select ARRAY/**/['example-secret'] from t")
        capture.record('DEFAULT', "select ARRAY /* c */['example-secret'] from t")
        capture.record('DEFAULT', 'select [order] from [user]')

        then:
        records(file)*.sql == [QueryTailRedactor.WITHHELD] * 3 + ['select [order] from [user]']
        !file.text.contains('example-secret')
    }

    void "a triple-quoted value is not recorded from a GoogleSQL database, whose strings hold quotes that way"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        capture.syntax = QueryTailSyntax.GOOGLE_SQL

        when:
        capture.record('DEFAULT', 'select """a"example-secret"b""" from t')

        then:
        records(file)*.sql == ['select ? from t']
        !file.text.contains('example-secret')
    }

    void "the capture named by the settings is opened"() {
        given:
        File file = new File(tempDir, 'from-settings.log')

        when:
        QueryTailCapture capture = QueryTailCapture.open(new QueryTailSettings(enabled: true, file: file.path))
        captures << capture

        then:
        capture.file == file
        file.exists()
    }

    void "a closed capture writes nothing more, and closing it again changes nothing"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        QueryTailCapture other = open(file)
        capture.record('DEFAULT', 'select 1')

        when:
        capture.close()
        capture.close()
        capture.record('DEFAULT', 'select 2')

        then: 'a second close does not take the file from the capture still open on it'
        !capture.open
        other.open
        records(file).size() == 1
    }

    void "a file that cannot be created is reported"() {
        given:
        File blocker = new File(tempDir, 'blocker')
        blocker.text = 'a file where the directory should be'

        when:
        QueryTailCapture.open(new File(blocker, 'capture.log'))

        then:
        UncheckedIOException e = thrown()
        e.message.contains('query-tail')
    }

    void "opening a capture creates a directory that does not exist yet"() {
        given:
        File file = new File(tempDir, 'not/yet/there/capture.log')

        expect:
        !file.parentFile.exists()

        when:
        QueryTailCapture capture = open(file)

        then:
        file.parentFile.isDirectory()
        file.exists()
        capture.open
    }

    void "a capture opened while another JVM writes the file joins its run, and the next start after it truncates the file"() {
        given: 'a file another JVM is writing, which holds the lock every writer holds while it writes'
        File file = new File(tempDir, 'capture.log')
        file.text = QueryTailRecord.header('other-jvm') + '\n' +
                new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', sql: 'select a from t').toLine() + '\n'
        FileChannel other = lockFile(file)
        FileLock writing = other.lock(1L, 1L, true)

        when:
        QueryTailCapture capture = open(file)
        inRequest('/book/show/1') { capture.record('DEFAULT', 'select b from t') }
        capture.record('DEFAULT', 'select c from t')

        then: 'the run of the other JVM is kept, with its header, and the requests of this JVM are told apart'
        capture.open
        QueryTailRecord.parseHeader(file.readLines().first()) == 'other-jvm'
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
        records(file)*.sql == ['select a from t', 'select b from t', 'select c from t']
        records(file)[1].requestId == "${ProcessHandle.current().pid()}-r1"

        when: 'the other JVM is done, and the next one starts'
        capture.close()
        writing.release()
        other.close()
        open(file)

        then: 'the file holds a new run'
        QueryTailRecord.parseHeader(file.readLines().first()) != 'other-jvm'
        records(file).isEmpty()
    }

    void "a writer holds the lock file while it writes, and lets it go when its last capture closes"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        FileChannel probe = lockFile(file)

        when: 'the byte every writer holds is asked for exclusively, by this JVM, which holds it through the capture'
        probe.tryLock(1L, 1L, false)

        then:
        thrown(OverlappingFileLockException)

        when:
        capture.close()
        FileLock free = probe.tryLock(1L, 1L, false)

        then:
        free != null

        cleanup:
        free?.release()
        probe.close()
    }

    @Requires({ !os.windows })
    @Timeout(60)
    void "a capture whose writes fail is let go, so that the next capture opened on the file starts afresh"() {
        given: 'a named pipe as the capture file, whose reader goes away'
        File pipe = new File(tempDir, 'capture.pipe')
        new ProcessBuilder('mkfifo', pipe.path).inheritIO().start().waitFor() == 0
        BlockingQueue<InputStream> readers = new ArrayBlockingQueue<>(2)
        Thread.startDaemon('query-tail-pipe-reader') { readers.put(new FileInputStream(pipe)) }
        QueryTailCapture first = open(pipe)
        readers.poll(10, TimeUnit.SECONDS).close()

        when: 'the writes fail'
        first.record('DEFAULT', 'select 1')
        first.record('DEFAULT', 'select 2')

        then: 'the capture ended'
        !first.open

        when: 'a capture is opened on the file again, once it can be written'
        Thread.startDaemon('query-tail-pipe-reader') { readers.put(new FileInputStream(pipe)) }
        QueryTailCapture second = open(pipe)
        InputStream reader = readers.poll(10, TimeUnit.SECONDS)

        then: 'it is a capture of its own, not the one that ended'
        second.open

        cleanup:
        reader?.close()
    }

    @Requires({ new File('/dev/full').canWrite() })
    void "a write that fails ends the capture rather than failing the statement"() {
        given: 'a capture on the device every write to fails with "no space left on device"'
        QueryTailCapture capture = open(new File('/dev/full'))

        when:
        capture.record('DEFAULT', 'select 1')
        capture.record('DEFAULT', 'select 2')

        then: 'neither statement fails, and the first failure closed the capture'
        !capture.open
    }

    private QueryTailCapture open(File file) {
        QueryTailCapture capture = QueryTailCapture.open(file)
        captures << capture
        capture
    }

    private static List<QueryTailRecord> records(File file) {
        file.readLines().collect { QueryTailRecord.parse(it) }.findAll { it != null }
    }

    /** The lock file beside a capture file, as another JVM would open it. */
    private static FileChannel lockFile(File file) {
        FileChannel.open(new File(file.path + '.lock').toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE)
    }

    private static void inRequest(String uri, Closure work) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest('GET', uri)))
        try {
            work.call()
        }
        finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }
}
