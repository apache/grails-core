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

import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import org.apache.grails.datamapping.core.querytail.QueryTailCapture
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

class QueryTailRunnerSpec extends Specification {

    @TempDir
    File tempDir

    ByteArrayOutputStream bytes = new ByteArrayOutputStream()
    PrintStream out = new PrintStream(bytes, true, 'UTF-8')
    File file

    void setup() {
        file = new File(tempDir, 'capture.log')
    }

    void "once prints the summary of the whole capture and exits"() {
        given:
        file.text = line('r1', 'book/show', 'select b1_0.id from book b1_0 where b1_0.id=?') + line('r2', 'book/index', 'select b1_0.id from book b1_0')
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file, once: true), out, false)

        expect:
        runner.run() == 0
        output().startsWith("query-tail ${file}  2 statements in 2 requests\n")
        output().contains('book/show')
        output().contains('book/index')
        !output().contains('refreshes')
    }

    void "once reads a capture larger than a chunk to its end before it prints the summary"() {
        given:
        String record = line('r1', 'book/show', 'select 1')
        file.text = (1..6).collect { line("r${it}".toString(), 'book/show', "select ${it}".toString()) }.join('')
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file, once: true), out, false)
        runner.chunk = record.length() * 2

        expect:
        runner.run() == 0
        output().startsWith("query-tail ${file}  6 statements in 6 requests\n")
    }

    void "once that is stopped while it is catching up ends without a summary"() {
        given:
        String record = line('r1', 'book/show', 'select 1')
        file.text = (1..6).collect { line("r${it}".toString(), 'book/show', "select ${it}".toString()) }.join('')
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file, once: true), out, false)
        runner.chunk = record.length() * 2
        runner.stop()

        expect:
        runner.run() == 0
        !output().contains('statements in')
    }

    void "once with a missing capture explains how to start capturing and fails"() {
        given:
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file, once: true), out, false)

        expect:
        runner.run() == 1
        output() == "No query-tail capture at ${file}.\n${QueryTailRunner.START_HINT}\n"
    }

    @Timeout(30)
    void "a capture file that is a directory is reported, and the run fails rather than show nothing #run"() {
        given:
        file.mkdirs()
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file, once: once), out, false)

        expect:
        runner.run() == 1
        output() == "Cannot read the query-tail capture ${file}: it is a directory.\n"

        where:
        run          | once
        'once'       | true
        'as it runs' | false
    }

    @Requires({ !os.windows && System.getProperty('user.name') != 'root' })
    void "a capture file that cannot be read is reported, and the run fails rather than show nothing"() {
        given:
        file.text = line('r1', 'book/show', 'select 1')
        file.setReadable(false, false)
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file, once: true), out, false)

        expect:
        runner.run() == 1
        output() == "Cannot read the query-tail capture ${file}: it cannot be read.\n"

        cleanup:
        file.setReadable(true, false)
    }

    @Requires({ !os.windows })
    void "a capture file replaced by a directory as the run follows it is reported, and the run fails"() {
        given:
        file.text = line('r1', 'book/show', 'select 1')
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file), out, false)
        runner.pollMillis = 20
        int[] result = new int[] { -1 }
        Thread thread = Thread.startDaemon('query-tail-runner') { result[0] = runner.run() }

        when:
        new PollingConditions(timeout: 30).eventually { assert output().contains('query-tail ') }
        assert file.delete()
        assert file.mkdirs()
        // an entry, so that the directory measures longer than none
        new File(file, 'entry').text = 'x'
        thread.join(TimeUnit.SECONDS.toMillis(30))

        then:
        !thread.alive
        result[0] == 1
        output().endsWith("Cannot read the query-tail capture ${file}: it is a directory.\n")

        cleanup:
        runner.stop()
    }

    void "once with a page shows the statements of that page"() {
        given:
        file.text = line('r1', 'book/show', 'select 1') + line('r2', 'book/index', 'select 2')
        QueryTailRunner runner = new QueryTailRunner(
                new QueryTailOptions(file: file, once: true, mode: QueryTailMode.STATEMENTS, page: 'book/index'), out, false)

        expect:
        runner.run() == 0
        output().contains('Statements of book/index')
        output().contains('select 2')
        !output().contains('select 1')
    }

    void "a live run waits for the capture, follows it, and stops when told to"() {
        given:
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file), out, false)
        runner.pollMillis = 20
        runner.refreshMillis = 0
        Thread thread = Thread.startDaemon('query-tail-runner') { runner.run() }
        PollingConditions conditions = new PollingConditions(timeout: 10)

        expect: 'it waits for the application to start capturing'
        conditions.eventually {
            assert output().startsWith("Waiting for ${file}. ${QueryTailRunner.START_HINT}\n")
        }

        when: 'the application writes statements'
        file.text = line('r1', 'book/show', 'select b1_0.id from book b1_0 where b1_0.id=?')

        then: 'the summary appears and keeps refreshing'
        conditions.eventually {
            assert output().contains('1 statements in 1 requests  (refreshes as statements run; Ctrl-C to stop)')
            assert output().contains('book/show')
        }

        when:
        file << line('r2', 'book/index', 'select b1_0.id from book b1_0')

        then:
        conditions.eventually {
            assert output().contains('2 statements in 2 requests')
            assert output().contains('book/index')
        }

        when:
        runner.stop()
        thread.join(TimeUnit.SECONDS.toMillis(10))

        then:
        !thread.alive
        runner.stopped
    }

    void "a live run that is stopped while waiting ends without failing"() {
        given:
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file), out, false)
        runner.pollMillis = 20
        int[] result = new int[1]
        Thread thread = Thread.startDaemon('query-tail-runner') { result[0] = runner.run() }

        when:
        new PollingConditions(timeout: 10).eventually { assert output().contains('Waiting for') }
        runner.stop()
        thread.join(TimeUnit.SECONDS.toMillis(10))

        then:
        !thread.alive
        result[0] == 0
    }

    void "the stream prints statements as they are appended"() {
        given:
        file.text = line('r0', 'book/index', 'select 0')
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file, mode: QueryTailMode.STREAM), out, false)
        runner.pollMillis = 20
        Thread thread = Thread.startDaemon('query-tail-runner') { runner.run() }
        PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
        Thread.sleep(100)
        file << line('r1', 'book/show', 'select b1_0.id from book b1_0 where b1_0.id=?')

        then: 'what ran before the command started is not shown'
        conditions.eventually {
            assert output() == '── r1  GET /book/show/1  book/show\n   select ... from book b1_0 where b1_0.id=?\n'
        }

        cleanup:
        runner.stop()
        thread.join(TimeUnit.SECONDS.toMillis(10))
    }

    void "the stream with a page prints only the statements of that page"() {
        given:
        file.text = line('r1', 'book/show', 'select 1 from book') + line('r2', 'author/index', 'select 2 from author') +
                line('r3', 'book/show', 'select 3 from book') + line('r4', 'book/index', 'select 4 from book')

        when:
        new QueryTailRunner(new QueryTailOptions(file: file, once: true, all: true, mode: QueryTailMode.STREAM, page: 'book/show'),
                out, false).run()

        then: 'the other pages are left out, headers and request footers included'
        List<String> lines = output().readLines()
        lines.size() == 5
        lines[0] == '── r1  GET /book/show/1  book/show'
        lines[1].contains('from book')
        lines[2].contains('r1: 1 statement')
        lines[3] == '── r3  GET /book/show/1  book/show'
        lines[4].contains('from book')
        !output().contains('author')

        when: 'a controller name takes every action of the controller'
        bytes.reset()
        new QueryTailRunner(new QueryTailOptions(file: file, once: true, all: true, mode: QueryTailMode.STREAM, page: 'book'),
                out, false).run()

        then:
        output().count('──') == 3
        !output().contains('author')
    }

    void "a value in a captured statement reaches neither the capture nor any view of it"() {
        given: 'an application that ran a statement with a password in it and a key in its comment'
        QueryTailCapture capture = QueryTailCapture.open(file)
        capture.record('DEFAULT', "/* api-key=abc123 */ select * from account where password='example-secret'")
        capture.close()

        expect:
        !file.text.contains('example-secret')
        !file.text.contains('abc123')

        when:
        for (QueryTailMode mode : QueryTailMode.values()) {
            new QueryTailRunner(new QueryTailOptions(file: file, once: true, all: true, mode: mode), out, false).run()
        }

        then: 'the pages, the statements and the stream show the statement without them'
        output().count('select ... from account where password=?') == 3
        !output().contains('example-secret')
        !output().contains('abc123')
    }

    @Unroll
    void "the stream starts counting again when the application restarts and writes #what before the next look"() {
        given:
        file.text = QueryTailRecord.header('run-1') + '\n' + line('r1', 'book/show', 'select 1')
        long before = file.length()
        QueryTailRunner runner = new QueryTailRunner(new QueryTailOptions(file: file, mode: QueryTailMode.STREAM, all: true), out, false)
        runner.pollMillis = 20
        Thread thread = Thread.startDaemon('query-tail-runner') { runner.run() }
        PollingConditions conditions = new PollingConditions(timeout: 10)

        expect:
        conditions.eventually {
            assert output() == '── r1  GET /book/show/1  book/show\n   select 1\n'
        }

        when: 'the application restarts and numbers its requests from r1 again'
        file.text = QueryTailRecord.header('run-2') + '\n' + written.join('')

        then: 'the request of the new run is not taken for the one of the old run'
        (file.length() == before) == sameLength
        conditions.eventually {
            assert output().endsWith(shown)
        }
        !output().contains('(continued)')

        cleanup:
        runner.stop()
        thread.join(TimeUnit.SECONDS.toMillis(10))

        where:
        what                             | written                                                                          | sameLength || shown
        'a longer run'                   | [line('r1', 'author/show', 'select 2'), line('r1', 'author/show', 'select 3')] | false      || '── r1  GET /author/show/1  author/show\n   select 2\n   select 3\n'
        'a run as long as the last one'  | [line('r1', 'book/list', 'select 2')]                                          | true       || '── r1  GET /book/list/1  book/list\n   select 2\n'
    }

    /** The output with the platform's line separator, which println writes, read as a newline. */
    private String output() {
        new String(bytes.toByteArray(), 'UTF-8').replace(System.lineSeparator(), '\n')
    }

    void "a command longer than a chunk of the file is counted with the statements after it"() {
        given: 'a MongoDB find on as many ids as MongoDB takes, with every value taken out, longer than a chunk'
        String large = 'find shop.book {"filter": {"_id": {"$in": [' + Collections.nCopies(350_000, '?').join(', ') + ']}}}'
        QueryTailCapture capture = QueryTailCapture.open(file)
        capture.recordRedacted('DEFAULT', { large } as Supplier<String>)
        capture.recordRedacted('DEFAULT', { 'find shop.book {"filter": {"_id": ?}}' } as Supplier<String>)
        capture.close()
        assert large.length() > QueryTailFileReader.CHUNK
        QueryTailRunner runner = new QueryTailRunner(QueryTailOptions.from([all: true, once: true, statements: true], file), out, false)

        expect:
        runner.run() == 0
        output().contains("${file}  2 statements in 0 requests")
        output().contains('find shop.book {"filter": {"_id": ?}}')
        output().contains('find shop.book {"filter": {"_id": {"$in": [?, ?')
    }

    @Timeout(180)
    void "a summary of #mode reads 350000 statements of one page in #shape within a heap of 96 MB"() {
        given: 'a capture of one statement on one page, which a summary of every request would not fit beside'
        int count = 350_000
        file.withWriter('UTF-8') { Writer writer ->
            writer.write(QueryTailRecord.header('bounded') + '\n')
            for (int i = 1; i <= count; i++) {
                writer.write(line(distinct ? "r${i}".toString() : 'r1', 'book/show', sql(texts ? i : 0)))
            }
        }

        when: 'it is summarized once in a JVM of its own, as with --all --once --top=1'
        Process process = new ProcessBuilder(new File(System.getProperty('java.home'), 'bin/java').path, '-Xmx96m',
                '-XX:-UsePerfData', '-cp', System.getProperty('java.class.path'), QueryTailSummaryOnce.name, file.path, mode.name())
                .redirectErrorStream(true).start()
        String printed = process.inputStream.getText('UTF-8')

        then:
        process.waitFor() == 0
        printed.contains("${count} statements in ${distinct ? count : 1} requests")
        printed.contains('select ... from book b1_0 where b1_0.id=?')

        where:
        mode                     | shape                                   | distinct | texts
        QueryTailMode.STATEMENTS | 'as many requests'                      | true     | false
        QueryTailMode.PAGES      | 'as many requests'                      | true     | false
        QueryTailMode.STATEMENTS | 'one request'                           | false    | false
        QueryTailMode.STATEMENTS | 'one request, recorded as as many texts' | false    | true
    }

    /**
     * @return the select of a book by id, with a column list of its own when given a number, which shortens to the one
     * statement whatever its columns
     */
    private static String sql(int columns) {
        String selected = columns == 0 ? 'b1_0.id,b1_0.title' : (1..20).collect { "b1_0.c${it}_${columns}" }.join(',')
        "select ${selected} from book b1_0 where b1_0.id=?"
    }

    private static String line(String requestId, String page, String sql) {
        new QueryTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: requestId, page: page,
                request: "GET /${page}/1".toString(), sql: sql).toLine() + '\n'
    }
}

/**
 * Prints the summary of a capture once, as {@code grails query-tail --all --once --top=1} does, in a JVM
 * of its own for {@link QueryTailRunnerSpec}; its arguments are the capture file and the {@link QueryTailMode}.
 */
class QueryTailSummaryOnce {

    static void main(String[] args) {
        QueryTailOptions options = new QueryTailOptions(file: new File(args[0]), mode: QueryTailMode.valueOf(args[1]),
                all: true, once: true, top: 1)
        System.exit(new QueryTailRunner(options, System.out, false).run())
    }
}
