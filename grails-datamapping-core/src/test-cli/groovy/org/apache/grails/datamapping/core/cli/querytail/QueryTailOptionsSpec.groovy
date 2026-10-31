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

import spock.lang.Specification
import spock.lang.TempDir

class QueryTailOptionsSpec extends Specification {

    File defaultFile = new File('build/query-tail/capture.log')

    @TempDir
    File appDir

    void "no flags follow the default capture as a page summary"() {
        when:
        QueryTailOptions options = QueryTailOptions.from([:], defaultFile)

        then:
        options.file == defaultFile
        options.mode == QueryTailMode.PAGES
        options.top == QueryTailOptions.DEFAULT_TOP
        !options.all
        !options.once
        options.page == null
    }

    void "flags given bare or with a value are read the same way the shell gives them"() {
        when:
        QueryTailOptions options = QueryTailOptions.from(
                [statements: Boolean.TRUE, all: 'true', once: Boolean.TRUE, top: '5', file: '/tmp/x.log'], defaultFile)

        then:
        options.mode == QueryTailMode.STATEMENTS
        options.all
        options.once
        options.top == 5
        options.file == new File('/tmp/x.log')
    }

    void "a relative file is read against the application directory, as the application reads the file it is configured with"() {
        given:
        File absolute = new File(appDir, 'elsewhere/capture.log')

        expect:
        QueryTailOptions.from([file: 'out/capture.log'], defaultFile, appDir).file == new File(appDir, 'out/capture.log')
        QueryTailOptions.from([file: absolute.path], defaultFile, appDir).file == absolute
        QueryTailOptions.from([file: Boolean.TRUE], defaultFile, appDir).file == defaultFile
        QueryTailOptions.from([file: 'out/capture.log'], defaultFile).file == new File('out/capture.log')
    }

    void "a file flag given bare names no file"() {
        expect:
        QueryTailOptions.from([file: Boolean.TRUE], defaultFile).file == defaultFile
    }

    void "a flag given as false is not given"() {
        expect:
        !QueryTailOptions.from([all: 'false', once: Boolean.FALSE], defaultFile).all
        !QueryTailOptions.from([all: 'false', once: Boolean.FALSE], defaultFile).once
        !QueryTailOptions.enabled(null)
        !QueryTailOptions.enabled('no')
        QueryTailOptions.enabled('TRUE')
    }

    void "a number of rows that is not one is refused with what to give instead: #top"() {
        when:
        QueryTailOptions.from([top: top], new File('capture.log'))

        then:
        IllegalArgumentException e = thrown()
        e.message == "--top takes a number of rows, such as --top=10, not '${top}'"

        where:
        top << ['ten', '1e3', '-1', '2.5']
    }

    void "a number of rows is read whatever spaces surround it"() {
        expect:
        QueryTailOptions.from([top: ' 12 '], new File('capture.log')).top == 12
        QueryTailOptions.from([top: '0'], new File('capture.log')).top == 0
    }

    void "a page implies the statement view and stream wins over statements"() {
        expect:
        QueryTailOptions.from([page: 'book/show'], defaultFile).mode == QueryTailMode.STATEMENTS
        QueryTailOptions.from([page: 'book/show'], defaultFile).page == 'book/show'
        QueryTailOptions.from([stream: Boolean.TRUE, statements: Boolean.TRUE], defaultFile).mode == QueryTailMode.STREAM
        QueryTailOptions.from([page: Boolean.TRUE], defaultFile).page == null
    }
}
