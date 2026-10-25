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
package org.grails.io.support

import spock.lang.Specification
import spock.lang.Unroll

class ByteArrayResourceSpec extends Specification {

    @Unroll
    void 'byte array resource exposes its content and metadata for #content'() {
        given:
        byte[] bytes = content as byte[]
        def resource = new ByteArrayResource(bytes)

        expect:
        resource.exists()
        resource.isReadable()
        resource.contentLength() == bytes.length
        resource.lastModified() == 0
        resource.getDescription() == 'resource loaded from byte array'
        resource.getFilename() == resource.getDescription()
        resource.getInputStream().withCloseable { it.readAllBytes() } == bytes

        where:
        content << [[], [0, 1, 127, -128, -1]]
    }

    void 'custom description is also used as filename'() {
        given:
        def resource = new ByteArrayResource([1, 2] as byte[], 'example.bin')

        expect:
        resource.getDescription() == 'example.bin'
        resource.getFilename() == 'example.bin'
    }

    void 'each input stream has independent position and can be reopened after closing'() {
        given:
        def resource = new ByteArrayResource([1, 2, 3] as byte[])
        InputStream first = resource.getInputStream()
        InputStream second = resource.getInputStream()

        expect:
        !first.is(second)
        first.read() == 1
        first.read() == 2
        second.read() == 1

        when:
        first.close()

        then:
        resource.getInputStream().withCloseable { it.readAllBytes() } == [1, 2, 3] as byte[]
        second.read() == 2

        cleanup:
        first.close()
        second.close()
    }

    @Unroll
    void '#operation is unsupported for an in-memory resource'() {
        given:
        def resource = new ByteArrayResource(new byte[0])

        when:
        resource."$operation"(*arguments)

        then:
        UnsupportedOperationException exception = thrown()
        exception.message.contains(operation)

        where:
        operation        | arguments
        'getURL'         | []
        'getURI'         | []
        'getFile'        | []
        'createRelative' | ['other.bin']
    }
}
