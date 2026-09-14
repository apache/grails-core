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
import spock.lang.TempDir

class SpringIOUtilsSpec extends Specification {

    @TempDir
    File tempDir

    void 'byteArrayToHexString converts bytes to lowercase hex'() {
        expect:
        SpringIOUtils.byteArrayToHexString([0x00, 0x0f, (byte) 0xff] as byte[]) == '000fff'
    }

    void 'byteArrayToHexString returns null for a null or empty array'() {
        expect:
        SpringIOUtils.byteArrayToHexString(null) == null
        SpringIOUtils.byteArrayToHexString(new byte[0]) == null
    }

    void 'copy(byte[], File) writes the bytes to the file'() {
        given:
        File out = new File(tempDir, 'out.bin')

        when:
        SpringIOUtils.copy('hello'.bytes, out)

        then:
        out.text == 'hello'
    }

    void 'copy(File, File) copies file contents'() {
        given:
        File source = new File(tempDir, 'source.txt')
        source.text = 'copy me'
        File target = new File(tempDir, 'target.txt')

        when:
        int count = SpringIOUtils.copy(source, target)

        then:
        target.text == 'copy me'
        count == 'copy me'.bytes.length
    }

    void 'copyToByteArray(File) reads the whole file'() {
        given:
        File source = new File(tempDir, 'source.txt')
        source.text = 'bytes here'

        expect:
        new String(SpringIOUtils.copyToByteArray(source)) == 'bytes here'
    }

    void 'copyToString(Reader) reads the whole reader'() {
        expect:
        SpringIOUtils.copyToString(new StringReader('reader contents')) == 'reader contents'
    }

    void 'copy(String, Writer) writes the string and closes the writer'() {
        given:
        StringWriter writer = new StringWriter()

        when:
        SpringIOUtils.copy('written text', writer)

        then:
        writer.toString() == 'written text'
    }

    void 'computeChecksum computes an md5 digest'() {
        given:
        File source = new File(tempDir, 'checksum.txt')
        source.text = 'checksum me'

        expect:
        SpringIOUtils.computeChecksum(source, 'md5').length() == 32
    }

    void 'computeChecksum rejects an unknown algorithm'() {
        given:
        File source = new File(tempDir, 'checksum.txt')
        source.text = 'checksum me'

        when:
        SpringIOUtils.computeChecksum(source, 'not-an-algorithm')

        then:
        thrown(IllegalArgumentException)
    }

    void 'closeQuietly closes without throwing'() {
        given:
        Closeable closeable = { -> } as Closeable

        expect:
        SpringIOUtils.closeQuietly(closeable)
        SpringIOUtils.closeQuietly(null)
    }

    void 'closeQuietly swallows an IOException from close'() {
        given:
        Closeable closeable = { -> throw new IOException('boom') } as Closeable

        when:
        SpringIOUtils.closeQuietly(closeable)

        then:
        noExceptionThrown()
    }

    void 'addAll concatenates two arrays preserving order'() {
        expect:
        SpringIOUtils.addAll(['a', 'b'] as String[], ['c', 'd'] as String[]) == ['a', 'b', 'c', 'd']
    }

    void 'newSAXParser returns a namespace-aware, non-validating parser'() {
        when:
        def parser = SpringIOUtils.newSAXParser()

        then:
        parser.isNamespaceAware()
        !parser.isValidating()
    }

}
