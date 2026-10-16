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

class ClassPathResourceSpec extends Specification {

    void 'exists is true for a resource on the classpath'() {
        expect:
        new ClassPathResource('org/grails/io/support/Resource.class', ClassPathResourceSpec.classLoader).exists()
    }

    void 'exists is false for a missing resource'() {
        expect:
        !new ClassPathResource('does/not/Exist.class', ClassPathResourceSpec.classLoader).exists()
    }

    void 'getInputStream throws FileNotFoundException for a missing resource'() {
        when:
        new ClassPathResource('does/not/Exist.class', ClassPathResourceSpec.classLoader).getInputStream()

        then:
        thrown(FileNotFoundException)
    }

    void 'equals is true for the same instance'() {
        given:
        ClassPathResource resource = new ClassPathResource('foo.txt')

        expect:
        resource == resource
    }

    void 'equals is true for two resources with the same path and no classloader/class'() {
        expect:
        new ClassPathResource('foo.txt') == new ClassPathResource('foo.txt')
        new ClassPathResource('foo.txt').hashCode() == new ClassPathResource('foo.txt').hashCode()
    }

    void 'equals is true for two resources with the same path and same classloader'() {
        given:
        ClassLoader cl = ClassPathResourceSpec.classLoader

        expect:
        new ClassPathResource('foo.txt', cl) == new ClassPathResource('foo.txt', cl)
    }

    void 'equals is false for two resources with different paths'() {
        expect:
        new ClassPathResource('foo.txt') != new ClassPathResource('bar.txt')
    }

    void 'equals is false for two resources with the same path but different classloaders'() {
        given:
        ClassLoader other = new URLClassLoader(new URL[0], null)

        expect:
        new ClassPathResource('foo.txt', ClassPathResourceSpec.classLoader) != new ClassPathResource('foo.txt', other)
    }

    void 'equals is false when compared against an unrelated object'() {
        expect:
        !new ClassPathResource('foo.txt').equals('not a resource')
    }

    void 'getFilename returns the last path segment'() {
        expect:
        new ClassPathResource('org/grails/foo.txt').getFilename() == 'foo.txt'
    }

    void 'a leading slash is stripped from the path'() {
        expect:
        new ClassPathResource('/foo.txt').getPath() == 'foo.txt'
    }

    void 'createRelative resolves relative to the resource path'() {
        given:
        ClassPathResource resource = new ClassPathResource('org/grails/foo.txt')

        when:
        Resource relative = resource.createRelative('bar.txt')

        then:
        relative instanceof ClassPathResource
        ((ClassPathResource) relative).getPath() == 'org/grails/bar.txt'
    }

    void 'a resource relative to a class resolves against the class package'() {
        given:
        ClassPathResource resource = new ClassPathResource('ClassPathResourceSpec.class', ClassPathResourceSpec)

        expect:
        resource.exists()
        resource.path == 'ClassPathResourceSpec.class'
        resource.classLoader.is(ClassPathResourceSpec.classLoader)
        resource.description == 'class path resource [org/grails/io/support/ClassPathResourceSpec.class]'
        resource.inputStream.withCloseable { it.read() } != -1
        resource.URL.toString().endsWith('org/grails/io/support/ClassPathResourceSpec.class')
        resource.createRelative('UrlResourceSpec.class').exists()
    }

    void 'a missing resource cannot be resolved to a URL'() {
        when:
        new ClassPathResource('missing.txt', ClassPathResourceSpec).URL

        then:
        FileNotFoundException e = thrown()
        e.message.contains('cannot be resolved to URL because it does not exist')
    }

    void 'a class relative missing resource does not exist and cannot be opened'() {
        given:
        ClassPathResource resource = new ClassPathResource('missing.txt', ClassPathResourceSpec)

        expect:
        !resource.exists()

        when:
        resource.inputStream

        then:
        thrown(FileNotFoundException)
    }

    void 'a class path file resolves to a File and URI'() {
        given:
        ClassPathResource resource = new ClassPathResource('org/grails/io/support/ClassPathResourceSpec.class')

        expect:
        resource.file.exists()
        resource.URI == resource.file.toURI()
        resource.contentLength() == resource.file.length()
        resource.lastModified() == resource.file.lastModified()
        resource.readable
    }

    void 'the description of a class loader resource names only the path'() {
        expect:
        new ClassPathResource('a/b.txt').description == 'class path resource [a/b.txt]'
    }

    void 'resources with the same class and path are equal and share a hash code'() {
        given:
        ClassPathResource first = new ClassPathResource('a.txt', ClassPathResourceSpec)
        ClassPathResource second = new ClassPathResource('a.txt', ClassPathResourceSpec)

        expect:
        first == second
        first.hashCode() == second.hashCode()
        first != new ClassPathResource('a.txt', UrlResourceSpec)
        first != new ClassPathResource('a.txt')
    }

}
