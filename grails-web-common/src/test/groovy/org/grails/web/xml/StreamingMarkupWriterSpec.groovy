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

class StreamingMarkupWriterSpec extends Specification {

    def "escapes double quotes only in attributes while preserving XML escaping"() {
        given:
        def target = new StringWriter()
        def writer = new StreamingMarkupWriter(target)
        writer.setWritingAttribute(attribute)

        when:
        writer.escaped().write('"<&>')

        then:
        target.toString() == expected

        where:
        attribute | expected
        true      | '&quot;&lt;&amp;&gt;'
        false     | '"&lt;&amp;&gt;'
    }

    def "switches from attributes to text and permits unescaped markup"() {
        given:
        def target = new StringWriter()
        def writer = new StreamingMarkupWriter(target, 'UTF-8')

        when:
        writer.unescaped().write('<node title="')
        writer.setWritingAttribute(true)
        writer.write(34)
        writer.setWritingAttribute(false)
        writer.unescaped().write('">')
        writer.escaped().write('"café"')
        writer.unescaped().write('</node>')
        writer.flush()

        then:
        target.toString() == '<node title="&quot;">"café"</node>'
    }

    def "propagates failures when escaping an attribute quote"() {
        given:
        def target = Mock(Writer)
        def writer = new StreamingMarkupWriter(target)
        writer.setWritingAttribute(true)

        when:
        writer.write(34)

        then:
        1 * target.write(*_) >> { throw new IOException('output failed') }
        def error = thrown(IOException)
        error.message == 'output failed'
    }
}
