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

class UrlResourceSpec extends Specification {

    void 'equals is true for the same instance'() {
        given:
        UrlResource resource = new UrlResource('file:///tmp/foo.txt')

        expect:
        resource == resource
    }

    void 'equals is true for two resources with the same cleaned URL'() {
        given:
        UrlResource one = new UrlResource('file:///tmp/foo.txt')
        UrlResource two = new UrlResource('file:///tmp/foo.txt')

        expect:
        one == two
        one.hashCode() == two.hashCode()
    }

    void 'equals is false for resources with different URLs'() {
        given:
        UrlResource one = new UrlResource('file:///tmp/foo.txt')
        UrlResource two = new UrlResource('file:///tmp/bar.txt')

        expect:
        one != two
    }

    void 'equals is false when compared against an unrelated object'() {
        given:
        UrlResource resource = new UrlResource('file:///tmp/foo.txt')

        expect:
        !resource.equals('not a resource')
    }

    void 'equals does not infinitely recurse for a distinct non-equal resource'() {
        given:
        UrlResource one = new UrlResource('file:///tmp/foo.txt')
        UrlResource two = new UrlResource('file:///tmp/bar.txt')

        when:
        boolean result = one.equals(two)

        then:
        noExceptionThrown()
        !result
    }

    void 'getURL returns the original URL'() {
        given:
        UrlResource resource = new UrlResource('file:///tmp/foo.txt')

        expect:
        resource.getURL().toString() == 'file:/tmp/foo.txt'
    }

    void 'getFilename returns the last path segment'() {
        given:
        UrlResource resource = new UrlResource('file:///tmp/foo.txt')

        expect:
        resource.getFilename() == 'foo.txt'
    }

    void 'createRelative resolves a relative path against the base URL'() {
        given:
        UrlResource resource = new UrlResource('file:///tmp/dir/foo.txt')

        when:
        Resource relative = resource.createRelative('bar.txt')

        then:
        relative.getURL().toString() == 'file:/tmp/dir/bar.txt'
    }

    void 'getDescription includes the URL'() {
        given:
        UrlResource resource = new UrlResource('file:///tmp/foo.txt')

        expect:
        resource.getDescription() == 'URL [file:/tmp/foo.txt]'
        resource.toString() == resource.getDescription()
    }

    void 'a resource can be created from a URI, a URL string or a URL'() {
        given:
        URI uri = new File('/tmp/x.txt').toURI()

        expect:
        new UrlResource(uri).URL == uri.toURL()
        new UrlResource(uri).URI.is(uri) || new UrlResource(uri).URI == uri
        new UrlResource(uri.toString()).URL == uri.toURL()
        new UrlResource(uri) == new UrlResource(uri.toURL())
    }

    void 'getURI derives the URI from the file when none was given'() {
        given:
        File file = new File('/tmp/derived.txt')

        expect:
        new UrlResource(file.toURI().toURL()).URI == file.toURI()
    }

    void 'getFile resolves through the URI when the resource was created from one'() {
        given:
        File file = new File('/tmp/viauri.txt')

        expect:
        new UrlResource(file.toURI()).file == file
    }

    void 'createRelative drops a leading slash and returns null for an unusable path'() {
        given:
        UrlResource base = new UrlResource('http://example.com/a/b.txt')

        expect:
        base.createRelative('/c.txt').URL.toString() == 'http://example.com/a/c.txt'
        base.createRelative('foo:bar') == null
    }

    void 'hashCode is consistent with equals'() {
        expect:
        new UrlResource('http://example.com/a/../b').hashCode() == new UrlResource('http://example.com/b').hashCode()
    }

    void 'getInputStream reads a file resource'() {
        given:
        File file = File.createTempFile('url-resource', '.txt')
        file.deleteOnExit()
        file.text = 'content'

        expect:
        new UrlResource(file.toURI().toURL()).inputStream.text == 'content'
    }

    void 'getInputStream fails for a resource that does not exist'() {
        when:
        new UrlResource(new File('/no/such/file.txt').toURI().toURL()).inputStream

        then:
        thrown(IOException)
    }

}
