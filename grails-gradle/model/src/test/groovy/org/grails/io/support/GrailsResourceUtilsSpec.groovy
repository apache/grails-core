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

class GrailsResourceUtilsSpec extends Specification {

    void 'cleanPath normalizes .. and . segments'() {
        expect:
        GrailsResourceUtils.cleanPath('foo/bar/../baz') == 'foo/baz'
        GrailsResourceUtils.cleanPath('foo/./bar') == 'foo/bar'
        GrailsResourceUtils.cleanPath('foo\\bar') == 'foo/bar'
    }

    void 'cleanPath preserves a scheme prefix'() {
        expect:
        GrailsResourceUtils.cleanPath('file:core/../core/io/Resource.class') == 'file:core/io/Resource.class'
    }

    void 'cleanPath returns null for a null path'() {
        expect:
        GrailsResourceUtils.cleanPath(null) == null
    }

    @Unroll
    void 'getFilename extracts the last path segment for #path'() {
        expect:
        GrailsResourceUtils.getFilename(path) == expected

        where:
        path                  | expected
        'foo/bar/baz.txt'     | 'baz.txt'
        'baz.txt'             | 'baz.txt'
        null                  | null
    }

    void 'classPackageAsResourcePath converts dots to slashes'() {
        expect:
        GrailsResourceUtils.classPackageAsResourcePath(GrailsResourceUtilsSpec) == 'org/grails/io/support'
    }

    void 'classPackageAsResourcePath returns empty string for a null class'() {
        expect:
        GrailsResourceUtils.classPackageAsResourcePath(null) == ''
    }

    void 'applyRelativePath replaces the last path segment'() {
        expect:
        GrailsResourceUtils.applyRelativePath('foo/bar/baz.txt', 'other.txt') == 'foo/bar/other.txt'
    }

    void 'applyRelativePath returns the relative path when there is no separator'() {
        expect:
        GrailsResourceUtils.applyRelativePath('baz.txt', 'other.txt') == 'other.txt'
    }

    void 'isFileURL is true for file: URLs and false for http: URLs'() {
        expect:
        GrailsResourceUtils.isFileURL(new URL('file:///tmp/foo.txt'))
        !GrailsResourceUtils.isFileURL(new URL('https://example.org/foo.txt'))
    }

    private static URL urlWithProtocol(String protocol, String spec) {
        // "zip:"/"wsjar:" are not registered on a plain JVM - build the URL with a
        // no-op handler so only the protocol name is exercised, not real resolution.
        URLStreamHandler noopHandler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return null
            }
        }
        return new URL(null, protocol + ':' + spec, noopHandler)
    }

    void 'isJarURL is true for jar:/zip:/wsjar: protocols and false otherwise'() {
        expect:
        GrailsResourceUtils.isJarURL(new URL('jar:file:/foo.jar!/Bar.class'))
        GrailsResourceUtils.isJarURL(urlWithProtocol('zip', 'file:/foo.jar!/Bar.class'))
        GrailsResourceUtils.isJarURL(urlWithProtocol('wsjar', 'file:/foo.jar!/Bar.class'))
        !GrailsResourceUtils.isJarURL(new URL('file:///tmp/foo.txt'))
    }

    void 'isDomainClass matches a file under grails-app/domain'() {
        given:
        URL url = new URL('file:///project/grails-app/domain/com/example/Book.groovy')

        expect:
        GrailsResourceUtils.isDomainClass(url)
    }

    void 'isDomainClass is false for a file outside grails-app/domain'() {
        given:
        URL url = new URL('file:///project/grails-app/controllers/com/example/BookController.groovy')

        expect:
        !GrailsResourceUtils.isDomainClass(url)
    }

    void 'isDomainClass is false for a null URL'() {
        expect:
        !GrailsResourceUtils.isDomainClass(null)
    }

    void 'isGrailsPath recognises the standard grails-app source roots'() {
        expect:
        GrailsResourceUtils.isGrailsPath('/project/grails-app/domain/com/example/Book.groovy')
        GrailsResourceUtils.isGrailsPath('/project/grails-app/conf/spring/resources.groovy')
        !GrailsResourceUtils.isGrailsPath('/project/build/classes/Helper.class')
    }

    void 'COMPILER_ROOT_PATTERNS contains the spring scripts and resource path patterns'() {
        expect:
        GrailsResourceUtils.COMPILER_ROOT_PATTERNS.length == 2
        GrailsResourceUtils.COMPILER_ROOT_PATTERNS[1].matcher(
                '/project/grails-app/controllers/com/example/BookController.groovy').find()
    }

}
