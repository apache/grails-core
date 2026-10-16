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
package org.grails.plugins.testing

import org.springframework.mock.web.MockMultipartFile
import spock.lang.Specification
import spock.lang.Unroll

class GrailsMockWebTypesSpec extends Specification {

    @Unroll
    void 'forwarded url #input is recorded as #expected'() {
        given:
        GrailsMockHttpServletResponse response = new GrailsMockHttpServletResponse()

        when:
        response.forwardedUrl = input

        then:
        response.forwardedUrl == expected

        where:
        input                        | expected
        null                         | null
        '/grails/book/list.dispatch' | '/book/list'
        '/grails/book/list'          | '/book/list'
        '/book/list.dispatch'        | '/book/list'
        '/book/list'                 | '/book/list'
        '/grails/'                   | '/'
    }

    void 'the mock multipart file records the transfer target instead of writing it'() {
        given:
        File target = new File('never-written.txt')

        when:
        GrailsMockMultipartFile fromBytes = new GrailsMockMultipartFile('file', 'abc'.bytes)
        GrailsMockMultipartFile fromStream = new GrailsMockMultipartFile('file', new ByteArrayInputStream('def'.bytes))
        GrailsMockMultipartFile named = new GrailsMockMultipartFile('file', 'orig.txt', 'text/plain', 'ghi'.bytes)
        GrailsMockMultipartFile namedStream = new GrailsMockMultipartFile('file', 'orig.txt', 'text/plain', new ByteArrayInputStream('jkl'.bytes))
        fromBytes.transferTo(target)

        then:
        fromBytes instanceof MockMultipartFile
        fromBytes.targetFileLocation.is(target)
        fromStream.targetFileLocation == null
        !target.exists()
        fromBytes.name == 'file'
        fromBytes.bytes == 'abc'.bytes
        fromStream.bytes == 'def'.bytes
        named.originalFilename == 'orig.txt'
        named.contentType == 'text/plain'
        named.bytes == 'ghi'.bytes
        namedStream.bytes == 'jkl'.bytes
        namedStream.contentType == 'text/plain'
    }

}
