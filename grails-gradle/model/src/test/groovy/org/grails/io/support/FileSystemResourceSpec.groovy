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

    void 'exists is true for a real file and false for a missing one'() {
        given:
        File real = new File(tempDir, 'real.txt')
        real.text = 'hello'

        expect:
        new FileSystemResource(real).exists()
        !new FileSystemResource(new File(tempDir, 'missing.txt')).exists()
    }

    void 'getInputStream reads the file contents'() {
        given:
        File real = new File(tempDir, 'real.txt')
        real.text = 'hello world'

        expect:
        new FileSystemResource(real).getInputStream().text == 'hello world'
    }

    void 'equals is true for the same instance'() {
        given:
        FileSystemResource resource = new FileSystemResource(new File(tempDir, 'a.txt'))

        expect:
        resource == resource
    }

    void 'equals is true for two resources with the same cleaned path'() {
        given:
        File file = new File(tempDir, 'a.txt')

        expect:
        new FileSystemResource(file) == new FileSystemResource(file)
        new FileSystemResource(file).hashCode() == new FileSystemResource(file).hashCode()
    }

    void 'equals is false for resources with different paths'() {
        expect:
        new FileSystemResource(new File(tempDir, 'a.txt')) != new FileSystemResource(new File(tempDir, 'b.txt'))
    }

    void 'equals does not infinitely recurse for a distinct non-equal resource'() {
        given:
        FileSystemResource one = new FileSystemResource(new File(tempDir, 'a.txt'))
        FileSystemResource two = new FileSystemResource(new File(tempDir, 'b.txt'))

        when:
        boolean result = one.equals(two)

        then:
        noExceptionThrown()
        !result
    }

    void 'constructor rejects a null File'() {
        when:
        new FileSystemResource((File) null)

        then:
        thrown(IllegalArgumentException)
    }

    void 'constructor rejects a null path'() {
        when:
        new FileSystemResource((String) null)

        then:
        thrown(IllegalArgumentException)
    }

    void 'getFilename returns the file name'() {
        expect:
        new FileSystemResource(new File(tempDir, 'foo.txt')).getFilename() == 'foo.txt'
    }

    void 'createRelative resolves a sibling path'() {
        given:
        File base = new File(tempDir, 'dir/foo.txt')
        FileSystemResource resource = new FileSystemResource(base)

        when:
        Resource relative = resource.createRelative('bar.txt')

        then:
        relative.getFilename() == 'bar.txt'
    }

    void 'readable is true for a file and false for a directory or a missing file'() {
        given:
        File file = File.createTempFile('fsr', '.txt')
        file.deleteOnExit()

        expect:
        new FileSystemResource(file).readable
        !new FileSystemResource(file.parentFile).readable
        !new FileSystemResource(new File(file.parentFile, 'does-not-exist-' + System.nanoTime())).readable
    }

    void 'the resource exposes the underlying file as a URL, URI and File'() {
        given:
        File file = File.createTempFile('fsr', '.txt')
        file.deleteOnExit()
        file.text = 'abcd'
        FileSystemResource resource = new FileSystemResource(file)

        expect:
        resource.URL == file.toURI().toURL()
        resource.URI == file.toURI()
        resource.file == file
        resource.contentLength() == 4
        resource.lastModified() == file.lastModified()
        resource.description == "file [${file.absolutePath}]".toString()
        resource.toString() == file.toString()
        resource.hashCode() == new FileSystemResource(file).hashCode()
        resource.path == GrailsResourceUtils.cleanPath(file.path)
    }

    void 'writable and output stream operate on the underlying file'() {
        given:
        File file = File.createTempFile('fsr', '.txt')
        file.deleteOnExit()
        FileSystemResource resource = new FileSystemResource(file)

        when:
        resource.outputStream.withCloseable { it.write('written'.bytes) }

        then:
        resource.writable
        !new FileSystemResource(file.parentFile).writable
        file.text == 'written'
    }

}
