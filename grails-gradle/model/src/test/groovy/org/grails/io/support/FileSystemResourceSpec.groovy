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

class FileSystemResourceSpec extends Specification {

    @TempDir
    File tempDir

    void 'a resource constructed from a #kind reads writes and exposes file metadata'() {
        given:
        File file = new File(tempDir, 'message.txt')
        file.text = 'initial content'
        def resource = kind == 'File' ? new FileSystemResource(file) : new FileSystemResource(file.path)

        expect:
        resource.exists()
        resource.isReadable()
        resource.isWritable()
        resource.inputStream.withCloseable { it.getText('UTF-8') } == 'initial content'
        resource.path == file.path.replace('\\', '/')
        resource.file == file
        resource.URL == file.toURI().toURL()
        resource.URI == file.toURI()
        resource.filename == 'message.txt'
        resource.description == "file [${file.absolutePath}]"
        resource.toString() == file.toString()
        resource.contentLength() == file.length()
        resource.lastModified() == file.lastModified()

        when:
        resource.outputStream.withCloseable { it.write('replacement'.getBytes('UTF-8')) }

        then:
        file.getText('UTF-8') == 'replacement'
        resource.contentLength() == 11

        where:
        kind << ['File', 'String']
    }

    void 'directories exist but are neither readable nor writable resources'() {
        given:
        def directory = new FileSystemResource(tempDir)

        expect:
        directory.exists()
        !directory.isReadable()
        !directory.isWritable()
    }

    void 'a missing resource fails reads but can be created through its output stream'() {
        given:
        def resource = new FileSystemResource(new File(tempDir, 'missing.txt'))

        expect:
        !resource.exists()
        !resource.isReadable()
        !resource.isWritable()

        when:
        resource.getInputStream()

        then:
        thrown(FileNotFoundException)

        when:
        resource.outputStream.withCloseable { it.write('created'.getBytes('UTF-8')) }

        then:
        resource.exists()
        resource.inputStream.withCloseable { it.getText('UTF-8') } == 'created'
    }

    void 'relative resources distinguish file paths from directory paths ending in a slash'() {
        given:
        def file = new FileSystemResource(new File(tempDir, 'original.txt'))
        def directory = new FileSystemResource(tempDir.path + '/')
        def directoryWithoutSlash = new FileSystemResource(tempDir)

        expect:
        file.createRelative('sibling.txt').file == new File(tempDir, 'sibling.txt')
        directory.createRelative('child.txt').file == new File(tempDir, 'child.txt')
        directoryWithoutSlash.createRelative('sibling.txt').file == new File(tempDir.parentFile, 'sibling.txt')
    }

    void 'equality uses normalized paths'() {
        given:
        def resource = new FileSystemResource(new File(tempDir, 'message.txt'))
        def equivalent = new FileSystemResource(tempDir.path + '/./message.txt')

        expect:
        resource == resource
        resource == equivalent
        resource.hashCode() == equivalent.hashCode()
        resource != new FileSystemResource(new File(tempDir, 'other.txt'))
        !resource.equals(null)
        !resource.equals(resource.file)
    }

    void 'constructors reject null #kind arguments'() {
        when:
        if (kind == 'File') {
            new FileSystemResource((File) null)
        } else {
            new FileSystemResource((String) null)
        }

        then:
        def error = thrown(IllegalArgumentException)
        error.message == message

        where:
        kind     | message
        'File'   | 'File must not be null'
        'String' | 'Path must not be null'
    }
}
