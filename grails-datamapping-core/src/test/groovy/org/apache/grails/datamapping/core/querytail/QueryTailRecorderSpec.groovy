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

import java.lang.ref.WeakReference
import java.util.function.Supplier

import spock.lang.Specification
import spock.lang.TempDir

class QueryTailRecorderSpec extends Specification {

    @TempDir
    File tempDir

    List<QueryTailRecorder> recorders = []

    void cleanup() {
        recorders*.close()
    }

    void "the first statement opens the capture and is recorded under the connection source"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder recorder = recorder('reporting', file)

        expect:
        recorder.capture == null
        !file.exists()

        when:
        recorder.record("select title from book where title = 'example-secret'")

        then:
        recorder.capture.open
        recorder.dataSourceName == 'reporting'
        recorder.file == file
        records(file)*.dataSource == ['reporting']
        records(file)*.sql == ['select title from book where title = ?']
    }

    void "the first statement that holds no value any more opens the capture and is recorded as it is, as a document"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder recorder = recorder('reporting', file)

        when:
        recorder.recordRedacted({ 'find shop.books {"filter": {"title": ?}}' } as Supplier<String>)

        then:
        recorder.capture.open
        records(file)*.dataSource == ['reporting']
        records(file)*.sql == ['find shop.books {"filter": {"title": ?}}']
        records(file)*.document == [true]
    }

    void "a recorder closed before anything opened its capture takes no values out of a statement"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder recorder = recorder('DEFAULT', file)
        boolean asked = false

        when:
        recorder.close()
        recorder.recordRedacted({ asked = true; 'find shop.books' } as Supplier<String>)

        then:
        !asked
        recorder.capture == null
        !file.exists()
    }

    void "opening the capture before any statement is recorded opens it once"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder recorder = recorder('DEFAULT', file)

        when:
        QueryTailCapture opened = recorder.open()
        recorder.record('select 1')

        then:
        opened.open
        recorder.open().is(opened)
        recorder.capture.is(opened)
        records(file)*.sql == ['select ?']
    }

    void "a recorder given the syntax of its database redacts the statement that opens its capture in that syntax"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder recorder = new QueryTailRecorder('DEFAULT', file, QueryTailSyntax.CYPHER)
        recorders << recorder

        when: 'the first statement opens the capture'
        recorder.record("RETURN 'example-secret' // api-key=abc123")

        then:
        recorder.capture.syntax.is(QueryTailSyntax.CYPHER)
        records(file)*.sql == ['RETURN ?']
        !file.text.contains('example-secret')
    }

    void "opening the capture in the syntax of its database redacts the statements after it in that syntax"() {
        given: 'a capture a statement opened before the syntax of its database was known'
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder recorder = recorder('DEFAULT', file)
        recorder.record('select "title" from book')

        when:
        QueryTailCapture opened = recorder.open(QueryTailSyntax.H2)
        recorder.record('select "title" from book')

        then: 'it is the one capture, which reads a double-quoted name as H2 does, where another database may read a string'
        opened.is(recorder.capture)
        opened.syntax == QueryTailSyntax.H2
        records(file)*.sql == [QueryTailRedactor.WITHHELD, 'select "title" from book']
    }

    void "a recorder closed before anything opened its capture opens none in the syntax of its database either"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder recorder = recorder('DEFAULT', file)

        when:
        recorder.close()

        then:
        recorder.open(QueryTailSyntax.H2) == null
        !file.exists()
    }

    void "closing the recorder closes its capture, and records nothing after"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder recorder = recorder('DEFAULT', file)
        recorder.record('select 1')
        QueryTailCapture capture = recorder.capture

        when:
        recorder.close()
        recorder.record('select 2')

        then:
        !capture.open
        recorder.open().is(capture)
        records(file)*.sql == ['select ?']
    }

    void "a recorder closed before anything opened its capture never opens one"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder recorder = recorder('DEFAULT', file)

        when:
        recorder.close()
        recorder.record('select 1')

        then:
        recorder.open() == null
        recorder.capture == null
        !file.exists()
    }

    void "a recorder read back in the JVM that wrote it records to the capture of the one written, which its owner closes"() {
        given: 'a recorder written before anything opened its capture'
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder original = recorder('DEFAULT', file)
        QueryTailRecorder copy = readBack(original)

        when: 'statements are recorded through both'
        copy.record('select 1')
        original.record('select 2')

        then: 'they share one capture'
        copy.is(original)
        records(file)*.sql == ['select ?', 'select ?']

        when: 'the owner closes the recorder it holds'
        original.close()
        QueryTailCapture next = QueryTailCapture.open(file)

        then: 'no capture is left open on the file, so the next one starts a new run'
        !copy.capture.open
        records(file).isEmpty()

        cleanup:
        next?.close()
    }

    void "a recorder read back once the one written has closed records nothing, since nothing would close what it opened"() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailRecorder original = recorder('DEFAULT', file)
        original.record('select 1')
        original.close()

        when:
        QueryTailRecorder copy = readBack(original)
        recorders << copy
        copy.record('select 2')

        then:
        copy.dataSourceName == 'DEFAULT'
        copy.file == file
        copy.capture == null
        copy.open() == null
        records(file)*.sql == ['select ?']
    }

    void "a recorder let go without closing is forgotten once collected, and a copy of it read back records nothing"() {
        given: 'a recorder written and let go, which nothing closes'
        File file = new File(tempDir, 'capture.log')
        WeakReference<QueryTailRecorder> letGo = new WeakReference<>(new QueryTailRecorder('DEFAULT', file))
        byte[] written = write(letGo.get())

        when: 'it is collected, and another recorder is made'
        for (int i = 0; i < 100 && letGo.get() != null; i++) {
            System.gc()
            Thread.sleep(50)
        }
        recorder('DEFAULT', new File(tempDir, 'other.log'))
        QueryTailRecorder copy = read(written)
        recorders << copy
        copy.record('select 1')

        then:
        letGo.get() == null
        copy.open() == null
        !file.exists()
    }

    private QueryTailRecorder recorder(String dataSourceName, File file) {
        QueryTailRecorder recorder = new QueryTailRecorder(dataSourceName, file)
        recorders << recorder
        recorder
    }

    private static QueryTailRecorder readBack(QueryTailRecorder recorder) {
        read(write(recorder))
    }

    private static byte[] write(QueryTailRecorder recorder) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(recorder) }
        bytes.toByteArray()
    }

    private static QueryTailRecorder read(byte[] written) {
        new ObjectInputStream(new ByteArrayInputStream(written)).withCloseable { it.readObject() as QueryTailRecorder }
    }

    private static List<QueryTailRecord> records(File file) {
        file.readLines().collect { QueryTailRecord.parse(it) }.findAll { it != null }
    }
}
