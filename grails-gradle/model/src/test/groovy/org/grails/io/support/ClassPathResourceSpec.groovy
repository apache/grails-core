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

}
