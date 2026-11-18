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
package org.grails.web.util

import java.nio.charset.UnsupportedCharsetException

import spock.lang.Specification

class BoundedCharsAsEncodedBytesCounterSpec extends Specification {

    def "counts encoded bytes through string and character updates"() {
        given:
        def counter = new BoundedCharsAsEncodedBytesCounter(32, encoding)
        counter.getCountingWriter()

        when:
        counter.update('é')
        counter.update('xöx'.toCharArray(), 1, 1)
        counter.update('a'.toCharArray())
        counter.update('')
        counter.update(new char[0])

        then:
        counter.size() == expected

        where:
        encoding     | expected
        'UTF-8'      | 5
        'ISO-8859-1' | 3
    }

    def "writer is initialized lazily reused with configured capacity and encoding and keeps counting after close"() {
        given:
        def counter = new BoundedCharsAsEncodedBytesCounter()
        counter.setCapacity(16)
        counter.setEncoding('UTF-8')

        expect:
        counter.capacity == 16
        counter.encoding == 'UTF-8'
        !counter.writerReferenced

        when:
        def writer = counter.getCountingWriter()
        writer.write('xéx'.toCharArray(), 1, 1)
        writer.write(65)
        writer.write('xbx'.toCharArray(), 1, 1)
        writer.append(new StringBuilder('more'))
        writer.flush()
        writer.close()

        then:
        counter.writerReferenced
        counter.getCountingWriter().is(writer)
        counter.size() == 8

        when: 'close is a no-op so the shared writer keeps counting'
        writer.write(66)

        then:
        counter.size() == 9
    }

    def "appends character sequence slices including sequences larger than the write buffer"() {
        given:
        def counter = new BoundedCharsAsEncodedBytesCounter(20000, 'UTF-8')
        def writer = counter.getCountingWriter()

        expect:
        writer.append(sequence, 1, sequence.length() - 1).is(writer)
        counter.size() == expected

        where:
        sequence                         | expected
        new StringBuilder('xéx')         | 2
        new StringBuffer('xéx')          | 2
        new StringBuilder('x' * 9002)    | 9000
        new StringBuffer('x' * 9002)     | 9000
    }

    def "exact capacity is counted but overflow permanently stops calculation"() {
        given:
        def counter = new BoundedCharsAsEncodedBytesCounter(2, 'UTF-8')
        def writer = counter.getCountingWriter()

        when:
        writer.write('é'.toCharArray())

        then:
        counter.size() == 2

        when:
        writer.write('a'.toCharArray())
        writer.write(65)
        writer.append(new StringBuilder('ignored'))
        writer.append('ignored', 0, 7)
        counter.update('ignored')
        counter.update('ignored'.toCharArray())

        then:
        counter.size() == -1
    }

    def "a multibyte character cannot fit into the remaining capacity"() {
        given:
        def counter = new BoundedCharsAsEncodedBytesCounter(1, 'UTF-8')
        def writer = counter.getCountingWriter()

        when:
        writer.write('é'.toCharArray())

        then:
        counter.size() == -1
    }

    def "unmappable and malformed characters are replaced when counting"() {
        given:
        def counter = new BoundedCharsAsEncodedBytesCounter(8, 'US-ASCII')
        def writer = counter.getCountingWriter()

        when:
        writer.write('é\uD800'.toCharArray())

        then:
        counter.size() == 2
    }

    def "invalid character range stops calculation"() {
        given:
        def counter = new BoundedCharsAsEncodedBytesCounter(8, 'UTF-8')
        counter.getCountingWriter()

        when:
        counter.update('abc'.toCharArray(), 3, 1)

        then:
        counter.size() == -1
    }

    def "unsupported encoding fails when obtaining the writer"() {
        given:
        def counter = new BoundedCharsAsEncodedBytesCounter(8, 'not-a-charset')

        when:
        counter.getCountingWriter()

        then:
        thrown(UnsupportedCharsetException)
        !counter.writerReferenced
    }
}
