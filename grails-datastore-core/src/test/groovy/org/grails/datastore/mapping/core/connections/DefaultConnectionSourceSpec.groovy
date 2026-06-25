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
package org.grails.datastore.mapping.core.connections

import spock.lang.Specification

class DefaultConnectionSourceSpec extends Specification {

    void 'close() closes a closeable source by default'() {
        given: 'a connection source over a closeable native source'
        def source = Mock(Closeable)
        def connectionSource = new DefaultConnectionSource('test', source, null)

        expect: 'the source is owned by the connection source'
        connectionSource.closeable

        when: 'the connection source is closed'
        connectionSource.close()

        then: 'the underlying source is closed'
        1 * source.close()
    }

    void 'close() does not close the source when not closeable'() {
        given: 'a connection source over an externally managed source'
        def source = Mock(Closeable)
        def connectionSource = new DefaultConnectionSource('test', source, null, false)

        expect: 'the source is not owned by the connection source'
        !connectionSource.closeable

        when: 'the connection source is closed'
        connectionSource.close()

        then: 'the underlying source is left open for its provider to manage'
        0 * source.close()
    }

    void 'close() closes an autocloseable source when closeable'() {
        given: 'a connection source over an autocloseable native source'
        def source = Mock(AutoCloseable)
        def connectionSource = new DefaultConnectionSource('test', source, null, true)

        when: 'the connection source is closed'
        connectionSource.close()

        then: 'the underlying source is closed'
        1 * source.close()
    }
}
