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
package org.grails.web.xml

import spock.lang.Specification

class PrettyPrintXMLStreamWriterSpec extends Specification {

    def "pretty prints a document with nested elements attributes escaped text and empty elements"() {
        given:
        def target = new StringWriter()
        def writer = new PrettyPrintXMLStreamWriter(new StreamingMarkupWriter(target, 'UTF-8'))
        def newline = PrettyPrintXMLStreamWriter.NEWLINE

        when:
        writer.startDocument('UTF-8', '1.0')
            .startNode('root').attribute('id', 'a"&b')
            .startNode('child').characters('a < b & c > d').end()
            .startNode('empty').end()
            .end()

        then:
        target.toString() == [
            '<?xml version="1.0" encoding="UTF-8"?>',
            '<root id="a&quot;&amp;b">',
            '  <child>',
            '    a &lt; b &amp; c &gt; d',
            '  </child>',
            '  <empty />',
            '</root>',
            ''
        ].join(newline)
    }

    def "uses custom indentation at each nested level"() {
        given:
        def target = new StringWriter()
        def writer = new PrettyPrintXMLStreamWriter(new StreamingMarkupWriter(target), '....')

        when:
        writer.startNode('outer').startNode('middle').startNode('inner').end().end().end()

        then:
        target.toString() == [
            '<outer>',
            '....<middle>',
            '........<inner />',
            '....</middle>',
            '</outer>',
            ''
        ].join(PrettyPrintXMLStreamWriter.NEWLINE)
    }

    def "rejects attributes outside an open start tag"() {
        given:
        def writer = new PrettyPrintXMLStreamWriter(new StreamingMarkupWriter(new StringWriter()))
        if (afterText) {
            writer.startNode('root').characters('body')
        }

        when:
        writer.attribute('id', 'invalid')

        then:
        thrown(IllegalStateException)

        where:
        afterText << [false, true]
    }

    def "rejects a document declaration after an element has started"() {
        given:
        def writer = new PrettyPrintXMLStreamWriter(new StreamingMarkupWriter(new StringWriter()))
        writer.startNode('root')

        when:
        writer.startDocument('UTF-8', '1.0')

        then:
        thrown(IllegalStateException)
    }

    def "propagates output failures"() {
        given:
        def target = Mock(Writer)
        def writer = new PrettyPrintXMLStreamWriter(new StreamingMarkupWriter(target))

        when:
        writer.startNode('root')

        then:
        1 * target.write(*_) >> { throw new IOException('output failed') }
        def error = thrown(IOException)
        error.message == 'output failed'
    }
}
