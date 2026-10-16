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

import spock.lang.Specification

class SqlTailOptionsSpec extends Specification {

    File defaultFile = new File('build/sql-tail/capture.log')

    void "no flags follow the default capture as a page summary"() {
        when:
        SqlTailOptions options = SqlTailOptions.from([:], defaultFile)

        then:
        options.file == defaultFile
        options.mode == SqlTailMode.PAGES
        options.top == SqlTailOptions.DEFAULT_TOP
        !options.all
        !options.once
        options.page == null
    }

    void "flags given bare or with a value are read the same way the shell gives them"() {
        when:
        SqlTailOptions options = SqlTailOptions.from(
                [statements: Boolean.TRUE, all: 'true', once: Boolean.TRUE, top: '5', file: '/tmp/x.log'], defaultFile)

        then:
        options.mode == SqlTailMode.STATEMENTS
        options.all
        options.once
        options.top == 5
        options.file == new File('/tmp/x.log')
    }

    void "a flag given as false is not given"() {
        expect:
        !SqlTailOptions.from([all: 'false', once: Boolean.FALSE], defaultFile).all
        !SqlTailOptions.from([all: 'false', once: Boolean.FALSE], defaultFile).once
        !SqlTailOptions.enabled(null)
        !SqlTailOptions.enabled('no')
        SqlTailOptions.enabled('TRUE')
    }

    void "a page implies the statement view and stream wins over statements"() {
        expect:
        SqlTailOptions.from([page: 'book/show'], defaultFile).mode == SqlTailMode.STATEMENTS
        SqlTailOptions.from([page: 'book/show'], defaultFile).page == 'book/show'
        SqlTailOptions.from([stream: Boolean.TRUE, statements: Boolean.TRUE], defaultFile).mode == SqlTailMode.STREAM
        SqlTailOptions.from([page: Boolean.TRUE], defaultFile).page == null
    }
}
