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

import java.net.MalformedURLException

import spock.lang.Specification
import spock.lang.TempDir

class UrlResourceSpec extends Specification {

    @TempDir
    File tempDir

    void 'a file resource constructed from a #kind exposes its content and metadata'() {
        given:
        File file = new File(tempDir, 'message.txt')
        file.text = 'URL content'
        URI uri = file.toURI()
        def resource = kind == 'URI' ? new UrlResource(uri) :
                kind == 'URL' ? new UrlResource(uri.toURL()) : new UrlResource(uri.toString())

        expect:
        resource.exists()
        resource.isReadable()
        resource.inputStream.withCloseable { it.getText('UTF-8') } == 'URL content'
        resource.URL == uri.toURL()
        resource.URI == uri
        resource.file == file
        resource.filename == 'message.txt'
        resource.contentLength() == file.length()
        resource.lastModified() == file.lastModified()
        resource.description == "URL [${uri.toURL()}]"
        resource.toString() == resource.description

        where:
        kind << ['URL', 'URI', 'String']
    }

    void 'relative URLs resolve siblings even when the relative path starts with a slash'() {
        given:
        File original = new File(tempDir, 'original.txt')
        File sibling = new File(tempDir, 'sibling.txt')
        sibling.text = 'sibling content'
        def resource = new UrlResource(original.toURI())

        when:
        def relative = resource.createRelative(path)

        then:
        relative.URL == sibling.toURI().toURL()
        relative.inputStream.withCloseable { it.getText('UTF-8') } == 'sibling content'

        where:
        path << ['sibling.txt', '/sibling.txt', 'nested/../sibling.txt']
    }

    void 'missing file URLs report absence and fail stream access'() {
        given:
        def resource = new UrlResource(new File(tempDir, 'missing.txt').toURI())

        expect:
        !resource.exists()
        !resource.isReadable()

        when:
        resource.getInputStream()

        then:
        thrown(FileNotFoundException)
    }

    void 'directories are not readable file resources'() {
        given:
        def resource = new UrlResource(tempDir.toURI())

        expect:
        resource.exists()
        !resource.isReadable()
    }

    void 'a non-file URI is preserved but cannot be resolved to a file'() {
        given:
        URI uri = new URI('https://example.invalid/message.txt')
        def resource = new UrlResource(uri)

        expect:
        resource.URI.is(uri)
        resource.URL == uri.toURL()

        when:
        resource.getFile()

        then:
        thrown(FileNotFoundException)
    }

    void 'equality and hash codes use cleaned URLs rather than the original path'() {
        given:
        def resource = new UrlResource('file:/app/./message.txt')
        def equivalent = new UrlResource('file:/app/message.txt')

        expect:
        resource.URL.toString() == 'file:/app/./message.txt'
        resource == resource
        resource == equivalent
        resource.hashCode() == equivalent.hashCode()
        resource != new UrlResource('file:/app/other.txt')
        !resource.equals(null)
        !resource.equals(resource.URL)
    }

    void 'a malformed URL string is rejected'() {
        when:
        new UrlResource('not a URL')

        then:
        thrown(MalformedURLException)
    }
}
