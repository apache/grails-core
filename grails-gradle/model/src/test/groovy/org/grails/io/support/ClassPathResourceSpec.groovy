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

import java.net.URLClassLoader

import spock.lang.Specification
import spock.lang.TempDir

class ClassPathResourceSpec extends Specification {

    @TempDir
    File tempDir

    private URLClassLoader loader

    void setup() {
        File directory = new File(tempDir, 'fixtures')
        assert directory.mkdirs()
        new File(directory, 'message.txt').text = 'classpath content'
        new File(directory, 'sibling.txt').text = 'sibling content'
        loader = new URLClassLoader([tempDir.toURI().toURL()] as URL[], (ClassLoader) null)
    }

    void cleanup() {
        loader.close()
    }

    void 'class loader resources normalize paths and expose file metadata and content'() {
        given:
        def resource = new ClassPathResource('/fixtures/unused/../message.txt', loader)
        File file = new File(tempDir, 'fixtures/message.txt')

        expect:
        resource.path == 'fixtures/message.txt'
        resource.classLoader.is(loader)
        resource.exists()
        resource.isReadable()
        resource.inputStream.withCloseable { it.getText('UTF-8') } == 'classpath content'
        resource.URL == file.toURI().toURL()
        resource.URI == file.toURI()
        resource.file == file
        resource.contentLength() == file.length()
        resource.lastModified() == file.lastModified()
        resource.filename == 'message.txt'
        resource.description == 'class path resource [fixtures/message.txt]'
    }

    void 'createRelative retains the class loader and resolves a sibling'() {
        given:
        def resource = new ClassPathResource('fixtures/message.txt', loader)

        when:
        def sibling = resource.createRelative('nested/../sibling.txt')

        then:
        sibling.path == 'fixtures/sibling.txt'
        sibling.classLoader.is(loader)
        sibling.exists()
        sibling.inputStream.withCloseable { it.getText('UTF-8') } == 'sibling content'
    }

    void 'class based resources resolve both package relative and absolute paths'() {
        given:
        def relative = new ClassPathResource('ClassPathResourceSpec.class', ClassPathResourceSpec)
        def absolute = new ClassPathResource('/org/grails/io/support/ClassPathResourceSpec.class', ClassPathResourceSpec)

        expect:
        relative.exists()
        absolute.exists()
        relative.URL == absolute.URL
        relative.classLoader.is(ClassPathResourceSpec.classLoader)
        relative.inputStream.withCloseable { it.read() } == 0xca
        absolute.inputStream.withCloseable { it.read() } == 0xca
        relative.description == 'class path resource [org/grails/io/support/ClassPathResourceSpec.class]'
        relative.createRelative('ClassPathResource.class').exists()
    }

    void 'the default constructor uses the context class loader'() {
        expect:
        new ClassPathResource('org/grails/io/support/ClassPathResourceSpec.class').exists()
    }

    void 'missing #kind resources report absence and fail #operation'() {
        given:
        def resource = kind == 'loader' ? new ClassPathResource('missing.txt', loader) :
                new ClassPathResource('missing.txt', ClassPathResourceSpec)

        expect:
        !resource.exists()
        !resource.isReadable()

        when:
        resource."$operation"()

        then:
        def error = thrown(FileNotFoundException)
        error.message.contains('does not exist')

        where:
        kind     | operation
        'loader' | 'getInputStream'
        'loader' | 'getURL'
        'class'  | 'getInputStream'
        'class'  | 'getURL'
    }

    void 'equality includes the normalized path and the resource loading strategy'() {
        given:
        def resource = new ClassPathResource('fixtures/message.txt', loader)
        def equivalent = new ClassPathResource('/fixtures/./message.txt', loader)

        expect:
        resource == resource
        resource == equivalent
        resource.hashCode() == equivalent.hashCode()
        resource != new ClassPathResource('fixtures/sibling.txt', loader)
        resource != new ClassPathResource('fixtures/message.txt', ClassPathResourceSpec)
        resource != new ClassPathResource('fixtures/message.txt', ClassPathResourceSpec.classLoader)
        !resource.equals(null)
        !resource.equals('fixtures/message.txt')
        new ClassPathResource('ClassPathResourceSpec.class', ClassPathResourceSpec) ==
                new ClassPathResource('./ClassPathResourceSpec.class', ClassPathResourceSpec)
    }
}
