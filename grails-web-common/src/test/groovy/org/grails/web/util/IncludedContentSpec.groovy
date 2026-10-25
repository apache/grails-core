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

import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class IncludedContentSpec extends Specification {

    def "writes and exposes string object and absent content"() {
        given:
        def included = new IncludedContent('text/plain', content)
        def target = new StringWriter()

        expect:
        included.contentType == 'text/plain'
        included.content == content
        included.redirectURL == null
        included.writeTo(target).is(target)
        target.toString() == expected
        included.contentAsCharArray == expected.toCharArray()

        where:
        content | expected
        'café'  | 'café'
        42      | '42'
        null    | ''
    }

    def "writes buffered include content and supplies the default content type"() {
        given:
        def wrapper = new IncludeResponseWrapper(new MockHttpServletResponse())
        wrapper.getWriter().write('buffered é')
        def included = new IncludedContent(null, wrapper.getContent())
        def target = new StringWriter()

        expect:
        included.contentType == 'text/html;charset=UTF-8'
        included.writeTo(target).is(target)
        target.toString() == 'buffered é'
        included.contentAsCharArray == 'buffered é'.toCharArray()
    }

    def "redirect content has no body"() {
        given:
        def included = new IncludedContent('/login')
        def target = new StringWriter()
        target.write('existing')

        expect:
        included.redirectURL == '/login'
        included.content == null
        included.contentType == 'text/html;charset=UTF-8'
        included.contentAsCharArray.length == 0
        included.writeTo(target).is(target)
        target.toString() == 'existing'
    }

    def "propagates errors from the destination writer"() {
        given:
        def included = new IncludedContent('text/plain', 'body')
        def target = Mock(Writer)

        when:
        included.writeTo(target)

        then:
        1 * target.write('body') >> { throw new IOException('destination failed') }
        def error = thrown(IOException)
        error.message == 'destination failed'
    }
}
