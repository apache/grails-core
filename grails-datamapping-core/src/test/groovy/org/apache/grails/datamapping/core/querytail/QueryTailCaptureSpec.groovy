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

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions
import spock.util.environment.RestoreSystemProperties

class QueryTailCaptureSpec extends Specification {

    @TempDir
    File tempDir

    List<QueryTailCapture> captures = []

    List<QueryTailOtherJvm> otherJvms = []

    void cleanup() {
        captures*.close()
        otherJvms*.close()
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
        QueryTailStubRequestReader.resolve(request, 'book/show', ['/book/show/1': '/book/show/?'])
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

    @Timeout(60)
    void "statements recorded by many threads at once are each written whole, on a line of its own"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        int threads = 16
        int statements = 200
        CountDownLatch start = new CountDownLatch(1)

        when: 'every thread records its statements as soon as all of them can'
        List<Thread> writers = (0..<threads).collect { int thread ->
            Thread.startDaemon("query-tail-writer-${thread}") {
                start.await()
                for (int n = 0; n < statements; n++) {
                    capture.record("thread${thread}", "select ${'c' * 512} from table_${n}".toString())
                }
            }
        }
        start.countDown()
        writers*.join(30000)

        then: 'the file holds its header and every statement, none of them broken into or mixed with another'
        writers.every { !it.alive }
        file.readLines().size() == threads * statements + 1
        records(file).groupBy { it.dataSource }.collectEntries { String dataSource, List<QueryTailRecord> records ->
            [(dataSource): records*.sql]
        } == (0..<threads).collectEntries { int thread ->
            [("thread${thread}".toString()): (0..<statements).collect { int n -> "select ${'c' * 512} from table_${n}".toString() }]
        }
    }

    @Timeout(60)
    void "captures opened on one file by many threads at once share one run of it"() {
        given:
        File file = new File(tempDir, 'capture.log')
        int threads = 16
        CountDownLatch start = new CountDownLatch(1)
        BlockingQueue<QueryTailCapture> opened = new ArrayBlockingQueue<>(threads)

        when: 'every thread opens a capture of the file and records a statement as soon as all of them can'
        List<Thread> openers = (0..<threads).collect { int thread ->
            Thread.startDaemon("query-tail-opener-${thread}") {
                start.await()
                QueryTailCapture capture = QueryTailCapture.open(file)
                opened.put(capture)
                capture.record("thread${thread}", 'select 1')
            }
        }
        start.countDown()
        openers*.join(30000)
        captures.addAll(opened)

        then: 'one of them started the run, and the others joined it'
        captures.size() == threads
        captures.every { it.open }
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
        records(file)*.dataSource.toSet() == (0..<threads).collect { "thread${it}".toString() }.toSet()
    }

    void "the turn of a file to be opened is let go once no capture opens it"() {
        given:
        List<File> files = (1..3).collect { new File(tempDir, "capture-${it}.log") }

        when: 'captures of each file are opened, one of them twice, and closed'
        List<QueryTailCapture> opened = files.collect { open(it) } + [open(files.first())]
        opened*.close()

        then: 'nothing is kept for any of the files'
        files.every { !QueryTailCapture.OPENING.containsKey(it.canonicalPath) }
        files.every { !QueryTailCapture.OPEN.containsKey(it.canonicalPath) }
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

    void "a request seen by two capture files does not reuse the next request's id in either file"() {
        given:
        File firstFile = new File(tempDir, 'first.log')
        File secondFile = new File(tempDir, 'second.log')
        QueryTailCapture first = open(firstFile)
        QueryTailCapture second = open(secondFile)

        when:
        inRequest('/book/index') {
            first.record('DEFAULT', 'select id from book')
            second.record('reporting', 'select id from book')
            second.record('reporting', 'select title from book')
        }
        inRequest('/book/index') { second.record('reporting', 'select id from book') }
        inRequest('/book/index') { first.record('DEFAULT', 'select id from book') }

        then:
        records(firstFile)*.requestId == ['r1', 'r2']
        records(secondFile)*.requestId == ['r1', 'r1', 'r2']
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

    void "a password an Oracle or SAP HANA statement gives as a name, quoted or bare, is not recorded"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)

        when:
        capture.syntax = QueryTailSyntax.ORACLE
        capture.record('DEFAULT', 'ALTER USER app_user IDENTIFIED BY "ExampleSecret123"')
        capture.record('DEFAULT', 'ALTER USER app_user IDENTIFIED BY ExampleSecret123')
        capture.syntax = QueryTailSyntax.HANA
        capture.record('DEFAULT', 'CREATE USER app_user PASSWORD ExampleSecret123')
        capture.record('DEFAULT', 'select u.password from app_user u')

        then:
        records(file)*.sql == [QueryTailRedactor.WITHHELD] * 3 + ['select u.password from app_user u']
        !file.text.contains('ExampleSecret123')
    }

    void "a dollar-quoted value is not recorded from a PostgreSQL database whatever characters its tag is written in"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        capture.syntax = QueryTailSyntax.POSTGRESQL

        when:
        capture.record('DEFAULT', 'select $tag$example-secret$tag$')
        capture.record('DEFAULT', 'select $\u20ac$example-secret$\u20ac$')
        capture.record('DEFAULT', 'select $\u00e9_1$example-secret$\u00e9_1$, $\u00a0$example-secret$\u00a0$')

        then:
        records(file)*.sql == ['select ?', 'select ?', 'select ?, ?']
        !file.text.contains('example-secret')
    }

    void "a quoted name keeps its spaces, so that statements on two names that differ only in them stay apart"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        capture.syntax = QueryTailSyntax.POSTGRESQL

        when:
        capture.record('DEFAULT', 'select  id from "order  lines"')
        capture.record('DEFAULT', 'select id\nfrom "order lines"')
        capture.record('DEFAULT', 'select id from "order\tlines\r\nby day"')

        then: 'the statement outside the names has its whitespace collapsed, and the file has a line per record'
        records(file)*.sql == ['select id from "order  lines"', 'select id from "order lines"',
                               'select id from "order\tlines\r\nby day"']
        file.readLines().size() == 4
    }

    void "a double-quoted value is not recorded from a Transact-SQL database, whose double quotes can delimit a string"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        capture.syntax = QueryTailSyntax.TRANSACT_SQL

        when:
        capture.record('DEFAULT', 'SELECT "example-secret" FROM t;')

        then:
        records(file)*.sql == ['SELECT ? FROM t;']
        !file.text.contains('example-secret')
    }

    void "a statement recorded again in the same syntax is not read again, and one recorded in another syntax is"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        CountingSyntax counting = new CountingSyntax()
        capture.syntax = counting

        when:
        3.times { capture.record('DEFAULT', "select a from t where b = 'example-secret'") }

        then: 'it was read once, and recorded each time'
        counting.read == 1
        records(file)*.sql == ['select a from t where b = ?'] * 3

        when: 'the syntax of the capture changes, as it does once a session factory knows its dialect'
        CountingSyntax other = new CountingSyntax()
        capture.syntax = other
        capture.record('DEFAULT', "select a from t where b = 'example-secret'")

        then:
        other.read == 1
        !file.text.contains('example-secret')
    }

    void "a statement too long for its redaction to be kept is read every time, and statements past those kept are read again"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        CountingSyntax counting = new CountingSyntax()
        capture.syntax = counting
        String wide = 'select a from t where b in (' + (['?'] * QueryTailCapture.REMEMBERED_LENGTH).join(',') + ')'

        when:
        2.times { capture.record('DEFAULT', wide) }

        then:
        counting.read == 2

        when: 'more statements are recorded than a capture keeps, and the first of them again'
        (QueryTailCapture.REMEMBERED_REDACTIONS + 1).times { int i -> capture.record('DEFAULT', "select a from t${i}".toString()) }
        int before = counting.read
        capture.record('DEFAULT', 'select a from t0')

        then: 'it is read again, and recorded as it was'
        counting.read == before + 1
        records(file).last().sql == 'select a from t0'
    }

    void "a statement recorded again and again among more statements than a capture keeps stays kept, and the one recorded least recently is let go"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        CountingSyntax counting = new CountingSyntax()
        capture.syntax = counting

        when: 'one statement runs between each of twice as many others as the capture keeps'
        capture.record('DEFAULT', 'select a from hot')
        (QueryTailCapture.REMEMBERED_REDACTIONS * 2).times { int i ->
            capture.record('DEFAULT', "select a from t${i}".toString())
            capture.record('DEFAULT', 'select a from hot')
        }

        then: 'it was read once, and every other statement once'
        counting.read == 1 + QueryTailCapture.REMEMBERED_REDACTIONS * 2

        when: 'the statement kept longest without being recorded again is recorded again'
        int before = counting.read
        capture.record('DEFAULT', 'select a from t0')

        then:
        counting.read == before + 1
        records(file).last().sql == 'select a from t0'
    }

    void "a setting a statement gives a bare value is not recorded, as PostgreSQL takes a bare word for its value"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        capture.syntax = QueryTailSyntax.POSTGRESQL

        when:
        capture.record('DEFAULT', 'SET app.api_key = s3cr3tToken')
        capture.record('DEFAULT', 'SET myapp.tenant_secret TO abc123')

        then:
        records(file)*.sql == [QueryTailRedactor.WITHHELD, QueryTailRedactor.WITHHELD]
        !file.text.contains('s3cr3tToken')
        !file.text.contains('abc123')
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

    void "a Cypher statement in which Neo4j 5 may decode an escape into the end of a string or a comment is not recorded"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        capture.syntax = QueryTailSyntax.CYPHER

        when:
        capture.record('DEFAULT', "RETURN 'a" + '\\' + "u0027 + 'secret' + " + '\\' + "u0027b'")
        capture.record('DEFAULT', 'RETURN 1 /* ' + '\\' + "u002a/ ' */ secret //'")
        capture.record('DEFAULT', "RETURN 'C:" + '\\\\' + "users'")
        capture.record('DEFAULT', "RETURN 'caf" + '\\' + "u00e9' AS `caf" + '\\' + "u00e9`")

        then: 'an escaped backslash before a u is no escape, and an escape that ends nothing is redacted as usual'
        records(file)*.sql == [QueryTailRedactor.WITHHELD] * 2 + ['RETURN ?', 'RETURN ? AS `caf' + '\\' + 'u00e9`']
        !file.text.contains('secret')
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

    void "a file that cannot be created leaves the capture stopped rather than failing what opens it: #reason"() {
        when:
        QueryTailCapture capture = QueryTailCapture.open(file(tempDir))
        capture.record('DEFAULT', 'select 1')
        capture.recordRedacted('DEFAULT', { 'find db.t' } as Supplier<String>)
        capture.close()

        then:
        notThrown(Exception)
        !capture.open
        capture.file == file(tempDir)

        where:
        reason                                 | file
        'a file stands where its directory is' | { File dir -> new File(blocker(dir), 'capture.log') }
        'its path cannot be a path'            | { File dir -> new File(dir, 'capture\u0000.log') }
    }

    void "a file that could not be created is opened afresh once it can be"() {
        given:
        File blocker = blocker(tempDir)
        File file = new File(blocker, 'capture.log')
        QueryTailCapture failed = QueryTailCapture.open(file)

        when:
        blocker.delete()
        QueryTailCapture capture = open(file)
        capture.record('DEFAULT', 'select 1')

        then:
        !failed.open
        capture.open
        records(file)*.sql == ['select ?']
    }

    private static File blocker(File dir) {
        File blocker = new File(dir, 'blocker')
        blocker.text = 'a file where the directory should be'
        blocker
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
        QueryTailOtherJvm writer = writingJvm(file)

        when:
        QueryTailCapture capture = open(file)
        inRequest('/book/show/1') { capture.record('DEFAULT', 'select b from t') }
        capture.record('DEFAULT', 'select c from t')

        then: 'the run of the other JVM is kept, with its header, and the requests of this JVM are told apart'
        capture.open
        QueryTailRecord.parseHeader(file.readLines().first()) == 'other-jvm'
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
        records(file)*.sql == ['select a from t', 'select b from t', 'select c from t']
        records(file)[1].requestId ==~ /${ProcessHandle.current().pid()}-r\d+/

        when: 'the other JVM is done, and the next one starts'
        capture.close()
        writer.tell('unshare')
        open(file)

        then: 'the file holds a new run'
        QueryTailRecord.parseHeader(file.readLines().first()) != 'other-jvm'
        records(file).isEmpty()
    }

    void "a JVM that joins the run of another again gives its requests ids it has not given in that run"() {
        given: 'a file another JVM is writing, which holds the lock every writer holds while it writes'
        File file = new File(tempDir, 'capture.log')
        file.text = QueryTailRecord.header('other-jvm') + '\n'
        writingJvm(file)

        when: 'this JVM joins the run, serves a request and closes its capture, twice'
        2.times {
            QueryTailCapture capture = open(file)
            inRequest('/book/index') { capture.record('DEFAULT', 'select b from t') }
            capture.close()
        }
        List<String> ids = records(file)*.requestId

        then: 'both requests are its own, and they are two'
        ids.size() == 2
        ids.every { it ==~ /${ProcessHandle.current().pid()}-r\d+/ }
        ids[0] != ids[1]
        QueryTailRecord.parseHeader(file.readLines().first()) == 'other-jvm'
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

    void "a checkpoint closes every capture file and its lock file, and the restore opens each again as a start would"() {
        given: 'two files, one of them captured to twice'
        File file = new File(tempDir, 'capture.log')
        File other = new File(tempDir, 'other/capture.log')
        QueryTailCapture capture = open(file)
        QueryTailCapture sharing = open(file)
        QueryTailCapture another = open(other)
        capture.record('DEFAULT', 'select 1 from book')
        String run = QueryTailRecord.parseHeader(file.readLines().first())
        QueryTailCheckpoint checkpoint = new QueryTailCheckpoint()
        FileChannel probe = lockFile(file)

        when:
        checkpoint.beforeCheckpoint(null)
        FileLock free = probe.tryLock(1L, 1L, false)

        then: 'nothing holds the byte every writer holds while it writes'
        free != null

        when: 'a statement runs while the process is checkpointed'
        free.release()
        capture.record('DEFAULT', 'select 2 from book')

        then: 'no capture writes'
        [capture, sharing, another].every { !it.open }
        records(file)*.sql == ['select ? from book']

        when:
        checkpoint.afterRestore(null)
        sharing.record('DEFAULT', 'select 3 from author')

        then: 'each file is open again, in a run of its own, as no other JVM writes it'
        [capture, sharing, another].every { it.open }
        QueryTailRecord.parseHeader(file.readLines().first()) != run
        records(file)*.sql == ['select ? from author']
        QueryTailRecord.parseHeader(other.readLines().first())

        when: 'the byte every writer holds is asked for again'
        probe.tryLock(1L, 1L, false)

        then: 'this JVM holds it again'
        thrown(OverlappingFileLockException)

        cleanup:
        checkpoint?.afterRestore(null)
        probe?.close()
    }

    void "a capture opened while the process is checkpointed opens its file after the restore, and one closed meanwhile does not"() {
        given:
        File file = new File(tempDir, 'capture.log')
        File closing = new File(tempDir, 'closing.log')
        QueryTailCapture closed = open(closing)
        QueryTailCheckpoint checkpoint = new QueryTailCheckpoint()
        FileChannel probe = lockFile(closing)

        when:
        checkpoint.beforeCheckpoint(null)
        QueryTailCapture opened = open(file)
        QueryTailCapture joining = open(file)
        closed.close()

        then: 'no file is opened until the restore'
        !file.exists()
        !opened.open
        !joining.open

        when:
        checkpoint.afterRestore(null)
        joining.record('DEFAULT', 'select 1 from book')
        FileLock free = probe.tryLock(1L, 1L, false)

        then: 'the captures opened meanwhile share one file, and the one closed meanwhile holds nothing'
        opened.open
        joining.open
        records(file)*.sql == ['select ? from book']
        !closed.open
        free != null

        cleanup:
        free?.release()
        checkpoint?.afterRestore(null)
        probe?.close()
    }

    void "a file that cannot be opened again after the restore is written no more until the next capture of it opens it again, for every capture of it"() {
        given:
        File dir = new File(tempDir, 'dir')
        File file = new File(dir, 'capture.log')
        QueryTailCapture capture = open(file)
        QueryTailCheckpoint checkpoint = new QueryTailCheckpoint()

        when: 'the directory of the file is replaced by a file while the process is checkpointed'
        checkpoint.beforeCheckpoint(null)
        assert dir.deleteDir()
        dir.text = 'a file where the directory was'
        checkpoint.afterRestore(null)
        capture.record('DEFAULT', 'select 1 from book')

        then: 'the capture has stopped, rather than failing the restore or the statement'
        !capture.open

        when: 'the directory can be created again'
        assert dir.delete()
        QueryTailCapture next = open(file)
        capture.record('DEFAULT', 'select 2 from book')

        then: 'the file holds a run of its own, which the capture that could not open it writes to again'
        next.open
        capture.open
        records(file)*.sql == ['select ? from book']

        cleanup:
        checkpoint?.afterRestore(null)
    }

    void "a capture that finds another application of this JVM writing its file is stopped, and leaves the file and that application as they were"() {
        given: 'another application that runs in this JVM, with a capture class of its own, writing the file'
        File file = new File(tempDir, 'capture.log')
        Class<?> otherApplication = new CracHidingClassLoader(QueryTailCapture.classLoader).loadClass(QueryTailCapture.name)
        Closeable other = (Closeable) otherApplication.getMethod('open', File).invoke(null, file)
        other.record('DEFAULT', 'select a from t')
        String run = QueryTailRecord.parseHeader(file.readLines().first())

        when:
        QueryTailCapture capture = open(file)
        capture.record('DEFAULT', 'select b from t')
        other.record('DEFAULT', 'select c from t')

        then: 'this application records nothing, and the other one goes on writing its run'
        !capture.open
        other.open
        QueryTailRecord.parseHeader(file.readLines().first()) == run
        records(file)*.sql == ['select a from t', 'select c from t']

        when: 'the other application is done'
        other.close()
        QueryTailCapture next = open(file)

        then: 'the next capture starts a run of its own'
        next.open
        QueryTailRecord.parseHeader(file.readLines().first()) != run

        cleanup:
        other?.close()
    }

    void "a capture that finds another application of this JVM starting a capture of its file is stopped at once, and leaves the file as it was"() {
        given: 'a run, and another application that runs in this JVM in the middle of starting a capture of it'
        File file = new File(tempDir, 'capture.log')
        file.text = QueryTailRecord.header('other-application') + '\n'
        FileChannel other = lockFile(file)
        FileLock starting = other.lock(0L, 1L, false)

        when:
        long started = System.nanoTime()
        QueryTailCapture capture = open(file)
        long waited = System.nanoTime() - started

        then: 'it did not wait for the other application as it waits for another JVM, nor touch the file'
        !capture.open
        waited < QueryTailCapture.STARTING_WAIT.toNanos()
        file.readLines() == [QueryTailRecord.header('other-application')]

        cleanup:
        starting?.release()
        other?.close()
    }

    void "a capture opens without org.crac on the classpath, which a process never checkpointed need not have"() {
        given:
        File file = new File(tempDir, 'capture.log')
        Class<?> captureClass = new CracHidingClassLoader(QueryTailCapture.classLoader).loadClass(QueryTailCapture.name)

        when:
        Closeable capture = (Closeable) captureClass.getMethod('open', File).invoke(null, file)

        then:
        capture.open
        QueryTailRecord.parseHeader(file.readLines().first())

        cleanup:
        capture?.close()
    }

    @Requires({ !os.windows })
    @Timeout(60)
    void "reopening waits for the last owner to finish its write and release its coordination lock"() {
        given: 'a pending pipe write holds the old capture open while its last owner closes it'
        File pipe = new File(tempDir, 'closing.pipe')
        assert new ProcessBuilder('mkfifo', pipe.path).inheritIO().start().waitFor() == 0
        BlockingQueue<InputStream> readers = new ArrayBlockingQueue<>(1)
        BlockingQueue<QueryTailCapture> reopened = new ArrayBlockingQueue<>(1)
        BlockingQueue<Throwable> failures = new ArrayBlockingQueue<>(3)
        CountDownLatch drain = new CountDownLatch(1)
        Thread readerThread = Thread.startDaemon('query-tail-closing-reader') {
            try {
                new FileInputStream(pipe).withCloseable { InputStream input ->
                    readers.put(input)
                    drain.await()
                    byte[] buffer = new byte[8192]
                    while (input.read(buffer) >= 0) {
                        // drain until both captures and the keeper close their write ends
                    }
                }
            }
            catch (Throwable failure) {
                failures.add(failure)
            }
        }
        QueryTailCapture capture = open(pipe)
        assert readers.poll(10, TimeUnit.SECONDS) != null
        OutputStream keeper = new FileOutputStream(pipe)
        Thread writer = Thread.startDaemon('query-tail-closing-writer') {
            capture.recordRedacted('DEFAULT', { 'x' * (1024 * 1024) } as Supplier<String>)
        }
        PollingConditions conditions = new PollingConditions(timeout: 10)
        conditions.eventually {
            assert writer.stackTrace.any { it.methodName == 'writeBytes' }
        }
        Thread closer = Thread.startDaemon('query-tail-closing-owner') { capture.close() }
        conditions.eventually {
            assert closer.state == Thread.State.WAITING
        }

        when: 'another owner opens the same path before the old write can finish'
        Thread opener = Thread.startDaemon('query-tail-reopening-owner') {
            try {
                reopened.put(QueryTailCapture.open(pipe))
            }
            catch (Throwable failure) {
                failures.add(failure)
            }
        }
        conditions.eventually {
            assert opener.state == Thread.State.BLOCKED || !reopened.isEmpty() || !failures.isEmpty()
        }

        then: 'it cannot join a writer that is already closing'
        reopened.isEmpty()
        failures.isEmpty()

        when: 'the write finishes and the old owner releases its lock'
        drain.countDown()
        QueryTailCapture replacement = reopened.poll(10, TimeUnit.SECONDS)
        captures << replacement
        FileChannel probe = lockFile(pipe)
        probe.tryLock(1L, 1L, false)

        then: 'the replacement owns the coordination lock itself'
        thrown(OverlappingFileLockException)
        replacement.open

        cleanup:
        drain?.countDown()
        writer?.join(10000)
        closer?.join(10000)
        opener?.join(10000)
        (replacement ?: reopened.poll())?.close()
        probe?.close()
        keeper?.close()
        readerThread?.join(10000)
        assert failures.isEmpty()
    }

    @Requires({ !os.windows })
    @Timeout(60)
    void "a capture whose writes fail records again, with every capture of its file, once the next capture opened on the file opens it"() {
        given: 'a named pipe as the capture file, whose reader goes away'
        File pipe = new File(tempDir, 'capture.pipe')
        assert new ProcessBuilder('mkfifo', pipe.path).inheritIO().start().waitFor() == 0
        BlockingQueue<InputStream> readers = new ArrayBlockingQueue<>(2)
        Thread.startDaemon('query-tail-pipe-reader') { readers.put(new FileInputStream(pipe)) }
        QueryTailCapture first = open(pipe)
        readers.poll(10, TimeUnit.SECONDS).close()

        when: 'the writes fail'
        first.record('DEFAULT', 'select a from one')
        first.record('DEFAULT', 'select a from two')

        then: 'the file is written no more'
        !first.open

        when: 'a capture is opened on the file again, once it can be written'
        Thread.startDaemon('query-tail-pipe-reader') { readers.put(new FileInputStream(pipe)) }
        QueryTailCapture second = open(pipe)
        InputStream reader = readers.poll(10, TimeUnit.SECONDS)
        BufferedReader lines = new BufferedReader(new InputStreamReader(reader, StandardCharsets.UTF_8))
        first.record('DEFAULT', 'select a from three')

        then: 'both captures write it, the one whose writes failed too'
        second.open
        first.open
        QueryTailRecord.parseHeader(lines.readLine())
        QueryTailRecord.parse(lines.readLine()).sql == 'select a from three'

        cleanup:
        reader?.close()
    }

    @Requires({ !os.windows })
    @Timeout(60)
    void "a file whose write failed is appended to in the run it was writing once another capture opens it, rather than truncated"() {
        given: 'another JVM that can write a file no longer than a block, and has written a run into one'
        File file = new File(tempDir, 'capture.log')
        QueryTailOtherJvm other = otherJvm(1)
        assert other.tell("open ${file.path}")
        assert other.tell('record select a from one')
        String run = QueryTailRecord.parseHeader(file.readLines().first())

        when: 'a statement too long for the room left fails to be written'
        boolean recorded = other.tell('record select ' + 'a, ' * 2000 + 'a from long')

        then:
        !recorded

        when: 'room is made, as on a disk that filled and was cleared, and another capture is opened on the file'
        String written = file.getText(StandardCharsets.UTF_8.name())
        FileChannel.open(file.toPath(), StandardOpenOption.WRITE).withCloseable { FileChannel channel ->
            // what the failed write left of its line goes, as the room it took is made again
            channel.truncate(written.lastIndexOf('\n') + 1)
        }
        boolean reopened = other.tell("open ${file.path}")
        other.tell('record-first select a from two')
        other.tell('record select a from three')

        then: 'the run and what was recorded before the failure are kept, and the captures of the file both write it'
        reopened
        QueryTailRecord.parseHeader(file.readLines().first()) == run
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
        records(file)*.sql == ['select a from one', 'select a from two', 'select a from three']
    }

    @Timeout(60)
    void "a capture another JVM keeps waiting to start stops after a while, rather than block what opens it, and leaves the file as it was"() {
        given: 'a run another JVM wrote, and another JVM in the middle of starting a capture of it, which it does not finish'
        File file = new File(tempDir, 'capture.log')
        file.text = QueryTailRecord.header('other-jvm') + '\n'
        QueryTailOtherJvm starter = otherJvm()
        starter.tell("hold ${file.path}.lock")

        when:
        long started = System.nanoTime()
        QueryTailCapture capture = open(file)
        long waited = System.nanoTime() - started

        then: 'it waited for the other JVM, then stopped without touching the file'
        !capture.open
        waited >= QueryTailCapture.STARTING_WAIT.toNanos()
        file.readLines() == [QueryTailRecord.header('other-jvm')]

        when: 'the other JVM lets go'
        starter.tell('release')
        QueryTailCapture next = open(file)

        then:
        next.open
    }

    void "a statement run on an interrupted thread is recorded, and so is every one after it"() {
        given: 'a capture, and a thread interrupted as a task cancelled while it runs is'
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)

        when:
        Thread.currentThread().interrupt()
        capture.record('DEFAULT', 'select a from t')
        boolean interrupted = Thread.interrupted()
        capture.record('DEFAULT', 'select b from t')

        then: 'the interrupt is left to the thread, and the capture is still writing'
        interrupted
        capture.open
        records(file)*.sql == ['select a from t', 'select b from t']

        cleanup:
        Thread.interrupted()
    }

    void "a capture opened on an interrupted thread while another JVM writes the file joins its run rather than truncating it"() {
        given: 'a file another JVM is writing, which holds the lock every writer holds while it writes'
        File file = new File(tempDir, 'capture.log')
        file.text = QueryTailRecord.header('other-jvm') + '\n'
        writingJvm(file)

        when:
        Thread.currentThread().interrupt()
        QueryTailCapture capture = open(file)
        boolean interrupted = Thread.interrupted()
        capture.record('DEFAULT', 'select b from t')

        then: 'the run of the other JVM is kept, and the interrupt is left to the thread'
        interrupted
        capture.open
        QueryTailRecord.parseHeader(file.readLines().first()) == 'other-jvm'
        records(file)*.sql == ['select b from t']

        cleanup:
        Thread.interrupted()
    }

    @Requires({ !os.windows })
    @Timeout(60)
    void "an interrupt that comes while a capture waits for another JVM to start it lets the capture open as it would have"() {
        given: 'a file another JVM writes, and another JVM in the middle of starting a capture of it'
        File file = new File(tempDir, 'capture.log')
        file.text = QueryTailRecord.header('other-jvm') + '\n'
        writingJvm(file)
        QueryTailOtherJvm starter = otherJvm()
        starter.tell("hold ${file.path}.lock")
        BlockingQueue<QueryTailCapture> opened = new ArrayBlockingQueue<>(1)
        BlockingQueue<Boolean> interrupts = new ArrayBlockingQueue<>(1)
        Thread opener = Thread.startDaemon('query-tail-interrupted-opener') {
            QueryTailCapture opening = QueryTailCapture.open(file)
            boolean interrupted = Thread.interrupted()
            opened.offer(opening)
            interrupts.offer(interrupted)
        }
        PollingConditions conditions = new PollingConditions(timeout: 10)
        conditions.eventually {
            assert opener.stackTrace.any { it.methodName == 'lockStarting' }
        }

        when: 'the opener is interrupted while it waits, and waits again'
        opener.interrupt()
        conditions.eventually {
            assert !opener.interrupted && opener.stackTrace.any { it.methodName == 'lockStarting' }
        }
        starter.tell('release')
        QueryTailCapture capture = opened.poll(10, TimeUnit.SECONDS)
        captures << capture

        then: 'it joined the run of the JVM writing the file, and the interrupt is left to the thread'
        capture.open
        interrupts.poll(10, TimeUnit.SECONDS)
        QueryTailRecord.parseHeader(file.readLines().first()) == 'other-jvm'

        cleanup:
        starter?.close()
        opener?.join(10000)
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

    @Requires({ !os.windows })
    @Timeout(60)
    void "a capture whose first write fails is opened stopped, even with a lock file beside it, and the next one tries the file afresh"() {
        given: 'another JVM, which can create files but write nothing into them'
        File file = new File(tempDir, 'capture.log')
        File lock = new File(tempDir, 'capture.log.lock')
        QueryTailOtherJvm other = otherJvm(false)

        when: 'the header of the run cannot be written'
        boolean opened = other.tell("open ${file.path}")
        boolean recorded = other.tell('record select 1')

        then: 'opening does not fail, and the capture has stopped'
        lock.exists()
        !opened
        !recorded

        when: 'the file is opened again while the stopped capture is still open'
        assert lock.delete()
        boolean reopened = other.tell("open ${file.path}")

        then: 'the new capture tries the file itself rather than sharing the one that stopped'
        lock.exists()
        !reopened
    }

    @Requires({ !os.windows })
    void "a capture opened through a link before its file exists shares the file with one opened by its own name"() {
        given:
        File file = new File(tempDir, 'capture.log')
        File link = new File(tempDir, 'link.log')
        Files.createSymbolicLink(link.toPath(), file.toPath())

        when:
        QueryTailCapture linked = open(link)
        linked.record('DEFAULT', 'select a from t')
        QueryTailCapture named = open(file)
        named.record('DEFAULT', 'select b from t')

        then: 'the second capture joined the first rather than truncating the file'
        linked.open
        named.open
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
        records(file)*.sql == ['select a from t', 'select b from t']
    }

    @Requires({ !os.windows })
    void "a capture creates the directories of its file and its lock file, whether it is opened by #name"() {
        given: 'a link to a file whose directories do not exist, as after a clean build'
        File file = new File(tempDir, 'build/query-tail/capture.log')
        File link = new File(tempDir, 'queries.log')
        Files.createSymbolicLink(link.toPath(), file.toPath())

        when:
        QueryTailCapture capture = open(linked ? link : file)
        capture.record('DEFAULT', 'select a from t')

        then:
        capture.open
        new File(file.path + '.lock').exists()
        QueryTailRecord.parseHeader(file.readLines().first())
        records(file)*.sql == ['select a from t']

        where:
        name           | linked
        'its own name' | false
        'a link'       | true
    }

    @Requires({ !os.windows })
    @Timeout(60)
    void "a JVM that opens the file through #childName while another writes it through #parentName joins its run rather than truncating it"() {
        given: 'this JVM writes the file'
        File file = new File(tempDir, 'capture.log')
        File link = new File(tempDir, 'link.log')
        Files.createSymbolicLink(link.toPath(), file.toPath())
        Map<String, File> names = ['the file': file, 'a link': link]
        QueryTailCapture capture = open(names[parentName])
        capture.record('DEFAULT', 'select a from parent_before')
        String run = QueryTailRecord.parseHeader(file.readLines().first())

        when: 'another JVM opens it by the other name and records while this one still writes'
        QueryTailOtherJvm other = otherJvm()
        boolean joined = other.tell("open ${names[childName].path}")
        other.tell('record select b from child')
        capture.record('DEFAULT', 'select c from parent_after')

        then: 'the run and every statement of both are kept'
        joined
        capture.open
        QueryTailRecord.parseHeader(file.readLines().first()) == run
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
        records(file)*.sql == ['select a from parent_before', 'select b from child', 'select c from parent_after']

        where:
        parentName | childName
        'the file' | 'a link'
        'a link'   | 'the file'
    }

    void "a statement whose request cannot be read is left out rather than failing, and the capture records on"() {
        given: 'request attributes that fail to answer, as a broken integration might'
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)
        RequestContextHolder.setRequestAttributes(new FailingRequestAttributes())

        when:
        capture.record('DEFAULT', 'select a from t')
        capture.recordRedacted('DEFAULT', { 'find db.t' } as Supplier<String>)
        RequestContextHolder.resetRequestAttributes()
        capture.record('DEFAULT', 'select b from t')

        then: 'neither statement failed, and the next one is recorded'
        notThrown(Exception)
        capture.open
        records(file)*.sql == ['select b from t']
    }

    void "a statement whose values cannot be taken out is left out rather than failing"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture capture = open(file)

        when:
        capture.recordRedacted('DEFAULT', { throw new IllegalStateException('cannot redact') } as Supplier<String>)
        capture.recordRedacted('DEFAULT', { 'find db.t' } as Supplier<String>)

        then:
        notThrown(Exception)
        records(file)*.sql == ['find db.t']
    }

    void "a capture that has stopped takes no values out at all"() {
        given:
        QueryTailCapture capture = open(new File(tempDir, 'capture.log'))
        capture.close()
        boolean redacted = false

        when:
        capture.recordRedacted('DEFAULT', { redacted = true; 'find db.t' } as Supplier<String>)

        then:
        !redacted
    }

    @Requires({ !os.windows })
    void "a lock file the file system cannot lock leaves the capture writing, uncoordinated"() {
        given: 'a lock file that opens but, on macOS, cannot be locked, as on a file system without locks'
        File file = new File(tempDir, 'capture.log')
        assert new ProcessBuilder('mkfifo', new File(tempDir, 'capture.log.lock').path).start().waitFor() == 0

        when:
        QueryTailCapture capture = open(file)
        capture.record('DEFAULT', 'select a from t')

        then:
        capture.open
        QueryTailRecord.parseHeader(file.readLines().first())
        records(file)*.sql == ['select a from t']
    }

    @RestoreSystemProperties
    void "a capture that finds its file headed by the run the build named appends to it"() {
        given: 'a build that names its run, and a datastore that captured to it and closed'
        System.setProperty(QueryTailCapture.RUN_PROPERTY, 'build-run')
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture first = open(file)
        inRequest('/book/index') { first.record('DEFAULT', 'select a from t') }
        first.close()

        when: 'the next datastore of the build opens the capture'
        QueryTailCapture second = open(file)
        inRequest('/book/index') { second.record('DEFAULT', 'select b from t') }

        then: 'the run goes on, and the requests of each are told apart'
        QueryTailRecord.parseHeader(file.readLines().first()) == 'build-run'
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
        records(file)*.sql == ['select a from t', 'select b from t']
        records(file)*.requestId.unique().size() == 2

        when: 'a later build names another run'
        second.close()
        System.setProperty(QueryTailCapture.RUN_PROPERTY, 'next-build-run')
        open(file)

        then: 'it starts its own'
        QueryTailRecord.parseHeader(file.readLines().first()) == 'next-build-run'
        records(file).isEmpty()
    }

    @RestoreSystemProperties
    void "a run the build names #named is appended to as it is to any other"() {
        given: 'a run named as it may be given on the command line or by a CI job, which its header names as it can read back'
        System.setProperty(QueryTailCapture.RUN_PROPERTY, run)
        File file = new File(tempDir, 'capture.log')
        QueryTailCapture first = open(file)
        first.record('DEFAULT', 'select a from t')
        first.close()

        when: 'the next datastore of the build opens the capture'
        QueryTailCapture second = open(file)
        second.record('DEFAULT', 'select b from t')
        String header = QueryTailRecord.parseHeader(file.readLines().first())

        then: 'the run goes on'
        records(file)*.sql == ['select a from t', 'select b from t']
        QueryTailRecord.readRun(file) == header
        digested ? header ==~ /sha256:[0-9a-f]{64}/ : header == run.trim().replaceAll(/\s+/, ' ')

        when: 'a later build names a run that differs from it only at its end'
        second.close()
        System.setProperty(QueryTailCapture.RUN_PROPERTY, run + '2')
        open(file)

        then: 'it starts its own'
        QueryTailRecord.parseHeader(file.readLines().first()) != header
        records(file).isEmpty()

        where:
        named                                         | run                                                || digested
        'with whitespace in it'                       | ' nightly \t  run '                                || false
        'as long as a header can name it'             | 'r' * 234                                          || false
        'too long for a header, by its digest'        | 'r' * 235                                          || true
        'as a CI job URL that is too long, by its digest' | 'https://ci.example.com/job/' + 'grails-core/' * 30 || true
    }

    private QueryTailCapture open(File file) {
        QueryTailCapture capture = QueryTailCapture.open(file)
        captures << capture
        capture
    }

    /**
     * Starts another JVM, which opens and writes captures as it is told.
     *
     * @param writable whether the files it creates can be written to; when not, every write into one fails
     */
    private QueryTailOtherJvm otherJvm(boolean writable = true) {
        QueryTailOtherJvm other = new QueryTailOtherJvm(writable ? null : 0)
        otherJvms << other
        other
    }

    /**
     * Starts another JVM, which opens and writes captures as it is told.
     *
     * @param blocks how many blocks of the shell's {@code ulimit -f} a file it writes may grow to, past which a write
     * into one fails
     */
    private QueryTailOtherJvm otherJvm(int blocks) {
        QueryTailOtherJvm other = new QueryTailOtherJvm(blocks)
        otherJvms << other
        other
    }

    /**
     * Starts another JVM that writes a file, as far as the JVMs that capture to it can tell: it holds the lock every
     * writer holds while it writes, until it is told to {@code unshare} it.
     */
    private QueryTailOtherJvm writingJvm(File file) {
        QueryTailOtherJvm writer = otherJvm()
        writer.tell("share ${file.path}.lock")
        writer
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

/** Another JVM, which opens and writes captures, and holds lock files, as {@link QueryTailCaptureWriter} is told to. */
class QueryTailOtherJvm implements Closeable {

    private final Process process
    private final BufferedReader output
    private final Writer input

    /**
     * @param blocks how many blocks a file the JVM writes may grow to, or null for no limit
     */
    QueryTailOtherJvm(Integer blocks) {
        List<String> java = [new File(System.getProperty('java.home'), 'bin/java').path, '-XX:-UsePerfData',
                '-cp', System.getProperty('java.class.path'), QueryTailCaptureWriter.name]
        // a file size limit lets the JVM create a file but fails every write past it, of nothing every write
        List<String> command = blocks == null ? java : ['sh', '-c', "ulimit -f ${blocks} && exec \"\$@\"".toString(), 'sh'] + java
        process = new ProcessBuilder(command).redirectErrorStream(true).start()
        output = new BufferedReader(new InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
        input = new OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
    }

    /**
     * Tells the JVM to do something, and waits until it has.
     *
     * @param command {@code open <file>}, {@code record <statement>}, {@code record-first <statement>},
     * {@code close}, {@code hold <lock file>}, {@code release}, {@code share <lock file>} or {@code unshare}
     * @return whether the capture it opened last is open
     */
    boolean tell(String command) {
        input.write(command + '\n')
        input.flush()
        List<String> said = []
        for (String line = output.readLine(); line != null; line = output.readLine()) {
            if (line.startsWith(QueryTailCaptureWriter.ANSWER)) {
                return Boolean.parseBoolean(line.substring(QueryTailCaptureWriter.ANSWER.length()))
            }
            said << line
        }
        throw new IllegalStateException("The other JVM ended before it did '${command}':\n${said.join('\n')}")
    }

    @Override
    void close() {
        input.close()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
    }
}

/**
 * Loads the capture as an application without {@code org.crac} on its classpath loads it: it defines the capture,
 * its nested classes and the checkpoint resource itself, so that their lookups of {@code org.crac} come to this
 * loader, which finds none; every other class comes from the parent.
 */
class CracHidingClassLoader extends ClassLoader {

    CracHidingClassLoader(ClassLoader parent) {
        super(parent)
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith('org.crac.')) {
            throw new ClassNotFoundException(name)
        }
        if (name != QueryTailCheckpoint.name && name != QueryTailCapture.name && !name.startsWith(QueryTailCapture.name + '$')) {
            return super.loadClass(name, resolve)
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> defined = findLoadedClass(name)
            if (defined == null) {
                byte[] bytes = parent.getResourceAsStream(name.replace('.', '/') + '.class').bytes
                // the usual class's protection domain, so that coverage tooling instruments the class
                defined = defineClass(name, bytes, 0, bytes.length, QueryTailCapture.protectionDomain)
            }
            if (resolve) {
                resolveClass(defined)
            }
            return defined
        }
    }
}

/**
 * Opens and writes captures in a JVM of its own for {@link QueryTailCaptureSpec}, as it is told on its
 * standard input, one command a line: {@code open <file>}, then {@code record <statement>} and
 * {@code close} on the capture it opened last, and {@code record-first <statement>} on the one it opened
 * first. {@code hold <lock file>} takes the byte of a lock file a
 * starting writer holds, as a JVM in the middle of starting a capture does, until {@code release}, and
 * {@code share <lock file>} the byte every writer holds while it writes, as a JVM that writes the file
 * does, until {@code unshare}. It answers each with whether the capture it opened last is open.
 */
class QueryTailCaptureWriter {

    static final String ANSWER = 'query-tail-writer open='

    static void main(String[] args) {
        List<QueryTailCapture> captures = []
        FileChannel held = null
        FileChannel shared = null
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
        try {
            for (String line = input.readLine(); line != null; line = input.readLine()) {
                int space = line.indexOf(' ')
                String command = space < 0 ? line : line.substring(0, space)
                String argument = space < 0 ? '' : line.substring(space + 1)
                if (command == 'open') {
                    captures << QueryTailCapture.open(new File(argument))
                }
                else if (command == 'record') {
                    captures.last().record('DEFAULT', argument)
                }
                else if (command == 'record-first') {
                    captures.first().record('DEFAULT', argument)
                }
                else if (command == 'close') {
                    captures.last().close()
                }
                else if (command == 'hold') {
                    held = FileChannel.open(new File(argument).toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ,
                            StandardOpenOption.WRITE)
                    held.lock(0L, 1L, false)
                }
                else if (command == 'release') {
                    held.close()
                }
                else if (command == 'share') {
                    shared = FileChannel.open(new File(argument).toPath(), StandardOpenOption.CREATE, StandardOpenOption.READ,
                            StandardOpenOption.WRITE)
                    shared.lock(1L, 1L, true)
                }
                else if (command == 'unshare') {
                    shared.close()
                }
                System.out.println(ANSWER + (captures ? captures.last().open : false))
                System.out.flush()
            }
        }
        finally {
            captures*.close()
            held?.close()
            shared?.close()
        }
    }
}

/**
 * The syntax of H2, counting how many times a statement is read in it.
 */
class CountingSyntax implements QueryTailSyntax {

    int read

    @Override
    List<QueryTailSyntax> readings(String sql) {
        read++
        List.of((QueryTailSyntax) this)
    }

    @Override
    int endOfComment(String sql, int index) {
        QueryTailSyntax.H2.endOfComment(sql, index)
    }

    @Override
    int endOfValue(String sql, int index) {
        QueryTailSyntax.H2.endOfValue(sql, index)
    }

    @Override
    int endOfQuotedName(String sql, int index) {
        QueryTailSyntax.H2.endOfQuotedName(sql, index)
    }
}

/**
 * Request attributes that fail whatever they are asked, as a broken integration's might.
 */
class FailingRequestAttributes implements RequestAttributes {

    @Override
    Object getAttribute(String name, int scope) {
        throw new UnsupportedOperationException('broken')
    }

    @Override
    void setAttribute(String name, Object value, int scope) {
        throw new UnsupportedOperationException('broken')
    }

    @Override
    void removeAttribute(String name, int scope) {
    }

    @Override
    String[] getAttributeNames(int scope) {
        new String[0]
    }

    @Override
    void registerDestructionCallback(String name, Runnable callback, int scope) {
    }

    @Override
    Object resolveReference(String key) {
        null
    }

    @Override
    String getSessionId() {
        'session'
    }

    @Override
    Object getSessionMutex() {
        this
    }
}
