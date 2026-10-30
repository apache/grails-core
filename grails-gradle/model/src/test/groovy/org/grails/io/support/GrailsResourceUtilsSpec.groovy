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

import groovy.util.ConfigObject

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

class GrailsResourceUtilsSpec extends Specification {

    @TempDir
    File tempDir

    void 'source helpers classify #path'() {
        expect:
        GrailsResourceUtils.getClassName(path) == className
        GrailsResourceUtils.isProjectSource(path) == projectSource
        GrailsResourceUtils.isGrailsPath(path) == grailsPath
        // the second lookup is answered from the known-path cache and must agree with the first
        GrailsResourceUtils.isGrailsPath(path) == grailsPath

        where:
        path                                                       | className                | projectSource | grailsPath
        '/app/grails-app/domain/example/Book.groovy'                 | 'example.Book'           | true          | true
        '/app/grails-app/controllers/example/BookController.java'   | 'example.BookController' | true          | true
        '/app/grails-app/conf/spring/resources.groovy'              | 'resources'              | true          | true
        'C:\\app\\grails-app\\domain\\example\\Book.java'          | 'example.Book'           | true          | true
        '/app/src/main/groovy/example/Helper.groovy'                 | 'example.Helper'         | true          | false
        '/app/src/main/java/example/Helper.java'                    | 'example.Helper'         | true          | false
        '/app/src/test/groovy/example/HelperSpec.groovy'             | 'example.HelperSpec'     | true          | false
        '/app/src/test/java/example/HelperTest.java'                | 'example.HelperTest'     | true          | false
        '/app/src/test/functional/example/BrowserSpec.groovy'       | 'example.BrowserSpec'    | true          | true
        '/app/grails-tests/example/BookTests.groovy'                 | 'example.BookTests'      | true          | true
        '/app/grails-app/views/book/show.gsp'                       | null                     | false         | false
        '/app/resources/example/Book.groovy'                        | null                     | false         | false
        '/app/not-grails-app/domain/example/Book.groovy'            | null                     | false         | false
    }

    void 'artefact helpers extract directory and compiler relative path from #path'() {
        expect:
        GrailsResourceUtils.getPathFromRoot(path) == relativePath
        GrailsResourceUtils.getArtefactDirectory(path) == directory

        where:
        path                                                  | relativePath                 | directory
        '/app/grails-app/domain/example/Book.groovy'            | 'example/Book.groovy'         | 'domain'
        '/app/grails-app/conf/BootStrap.groovy'                 | 'BootStrap.groovy'            | 'conf'
        '/app/grails-app/conf/spring/resources.groovy'          | 'resources.groovy'            | 'conf'
        '/app/grails-app/conf/spring/nested/beans.groovy'       | 'nested/beans.groovy'         | 'conf'
        'C:\\app\\grails-app\\conf\\spring\\resources.groovy' | 'resources.groovy'            | 'conf'
        'C:\\app\\grails-app\\domain\\example\\Book.java'     | 'example\\Book.java'         | 'domain'
        '/app/src/main/groovy/example/Book.groovy'              | null                         | null
        '/app/grails-app/views/book/show.gsp'                  | null                         | null
    }

    void 'domain detection distinguishes source directories and extensions'() {
        expect:
        GrailsResourceUtils.isDomainClass(new URL(location)) == expected
        GrailsResourceUtils.isDomainClass(null) == false

        where:
        location                                                | expected
        'file:/app/grails-app/domain/example/Book.groovy'          | true
        'file:/app/grails-app/domain/example/Book.java'            | true
        'file:/app/grails-app/controllers/BookController.groovy'  | false
        'file:/app/src/main/groovy/example/Book.groovy'            | false
        'file:/app/grails-app/domain/example/Book.txt'             | false
    }

    void 'resource overloads recognize source files and plugin descriptors'() {
        given:
        def domain = new FileSystemResource(new File(tempDir, 'grails-app/domain/example/Book.groovy'))
        def source = new FileSystemResource(new File(tempDir, 'src/main/java/example/Helper.java'))
        def plugin = new FileSystemResource(new File(tempDir, 'ExampleGrailsPlugin.groovy'))
        def missing = new ClassPathResource('missing-grails-resource.txt', getClass())

        expect:
        GrailsResourceUtils.getClassName(domain) == 'example.Book'
        GrailsResourceUtils.isProjectSource(domain)
        GrailsResourceUtils.isGrailsResource(domain)
        GrailsResourceUtils.isProjectSource(source)
        !GrailsResourceUtils.isGrailsResource(source)
        GrailsResourceUtils.isProjectSource(plugin)
        GrailsResourceUtils.isGrailsResource(plugin)
        GrailsResourceUtils.getClassName(missing) == null
        !GrailsResourceUtils.isProjectSource(missing)
        !GrailsResourceUtils.isGrailsResource(missing)
    }

    void 'cleanPath normalizes #path'() {
        expect:
        GrailsResourceUtils.cleanPath(path) == expected

        where:
        path                                       | expected
        null                                       | null
        ''                                         | ''
        '/app/./grails-app/domain/../views/'         | '/app/grails-app/views/'
        'C:\\app\\grails-app\\..\\src\\Book.java' | 'C:/app/src/Book.java'
        'file:core/../core/io/Resource.class'        | 'file:core/io/Resource.class'
        '../../app/../Book.groovy'                  | '../../Book.groovy'
        'a/b/../../..'                             | '..'
    }

    void 'applyRelativePath replaces the filename without normalizing the result'() {
        expect:
        GrailsResourceUtils.applyRelativePath(path, relative) == expected

        where:
        path              | relative       | expected
        '/app/Book.java'   | 'Other.java'   | '/app/Other.java'
        '/app/Book.java'   | '/Other.java'  | '/app/Other.java'
        '/app/Book.java'   | '../test.java' | '/app/../test.java'
        'Book.java'        | 'Other.java'   | 'Other.java'
    }

    void 'filename class package and compiled class helpers return resource names'() {
        expect:
        GrailsResourceUtils.getFilename(null) == null
        GrailsResourceUtils.getFilename('/app/Book.groovy') == 'Book.groovy'
        GrailsResourceUtils.getFilename('Book.groovy') == 'Book.groovy'
        GrailsResourceUtils.getFilename('/app/') == ''
        GrailsResourceUtils.classPackageAsResourcePath(null) == ''
        GrailsResourceUtils.classPackageAsResourcePath(String) == 'java/lang'
        GrailsResourceUtils.getClassNameForClassFile('/app/classes/', '/app/classes/example/Book.class') == 'example.Book'
        GrailsResourceUtils.getClassNameForClassFile(null, 'example\\Book.class') == 'example.Book'
        GrailsResourceUtils.getPathFromBaseDir('/app/grails-app/domain/example/Book.groovy') == 'domain/example/Book.groovy'
        GrailsResourceUtils.getArtefactDirectory(null) == null
    }

    void 'appendPiecesForUri joins #pieces and removes duplicate slashes'() {
        expect:
        GrailsResourceUtils.appendPiecesForUri(pieces as String[]) == expected

        where:
        pieces                             | expected
        null                               | ''
        []                                 | ''
        ['alpha', 'beta', 'gamma']          | 'alpha/beta/gamma'
        ['/alpha/', '/beta/', '/gamma/']    | '/alpha/beta/gamma/'
        ['', null, '/alpha', 'beta']        | '/alpha/beta'
        ['/alpha//', '///beta']             | '/alpha/beta'
    }

    void 'application and views directories resolve from file and non-file resources'() {
        given:
        def file = new FileSystemResource(new File(tempDir, 'grails-app/domain/example/Book.groovy'))
        def remote = new UrlResource('https://example.invalid/app/grails-app/domain/Book.groovy')
        def outside = new FileSystemResource(new File(tempDir, 'src/main/groovy/Helper.groovy'))

        expect:
        GrailsResourceUtils.getAppDir(file).file == new File(tempDir, 'grails-app')
        GrailsResourceUtils.getViewsDir(file).file == new File(tempDir, 'grails-app/views')
        GrailsResourceUtils.getAppDir(remote).URL.toString() == 'https://example.invalid/app/grails-app/'
        GrailsResourceUtils.getViewsDir(remote).URL.toString() == 'https://example.invalid/app/grails-app/views'
        GrailsResourceUtils.getAppDir(outside) == null
        GrailsResourceUtils.getViewsDir(outside) == null
        GrailsResourceUtils.getAppDir(null) == null
        GrailsResourceUtils.getViewsDir(null) == null
    }

    void 'deployment helpers compute WEB-INF and plugin paths for #location'() {
        given:
        def resource = new UrlResource(location)

        expect:
        GrailsResourceUtils.getRelativeInsideWebInf(resource) == webInfPath
        GrailsResourceUtils.getStaticResourcePathForResource(resource, '/shop') == staticPath

        where:
        location                                                    | webInfPath                                              | staticPath
        'file:/app/grails-app/domain/Book.groovy'                     | '/WEB-INF/grails-app/domain/Book.groovy'                 | '/shop'
        'file:/app/plugins/books/grails-app/domain/Book.groovy'       | '/WEB-INF/plugins/books/grails-app/domain/Book.groovy'   | '/shop/plugins/books'
        'file:/app/WEB-INF/grails-app/domain/Book.groovy'             | '/WEB-INF/grails-app/domain/Book.groovy'                 | '/shop'
        'file:/app/src/main/groovy/Helper.groovy'                     | null                                                    | '/shop'
    }

    void 'deployment helpers handle missing resources and an empty context path'() {
        given:
        def missing = new ClassPathResource('missing-deployment-resource.txt', getClass())
        def plugin = new UrlResource('file:/app/plugins/books/grails-app/domain/Book.groovy')

        expect:
        GrailsResourceUtils.getRelativeInsideWebInf(null) == null
        GrailsResourceUtils.getRelativeInsideWebInf(missing) == null
        GrailsResourceUtils.getStaticResourcePathForResource(null, null) == ''
        GrailsResourceUtils.getStaticResourcePathForResource(missing, '/shop') == '/shop'
        GrailsResourceUtils.getStaticResourcePathForResource(plugin, null) == 'plugins/books'
    }

    void 'file URL and URI helpers decode spaces and extract jar locations'() {
        given:
        File file = new File(tempDir, 'with space.txt')
        URL url = file.toURI().toURL()
        URL jar = new URL('jar:' + url.toString() + '!/example/Book.class')
        URL jarWithoutProtocol = new URL('jar', '', 'C:/mypath/myjar.jar!/example/Book.class')

        expect:
        GrailsResourceUtils.getFile(url, 'fixture') == file
        GrailsResourceUtils.getFile(file.toURI(), 'fixture') == file
        GrailsResourceUtils.getFile(file.toURI()) == file
        GrailsResourceUtils.toURI(url) == file.toURI()
        GrailsResourceUtils.toURI('file:/app/with space.txt') == new URI('file:/app/with%20space.txt')
        GrailsResourceUtils.isFileURL(url)
        !GrailsResourceUtils.isFileURL(jar)
        GrailsResourceUtils.isJarURL(jar)
        !GrailsResourceUtils.isJarURL(url)
        GrailsResourceUtils.extractJarFileURL(jar) == url
        GrailsResourceUtils.extractJarFileURL(url) == url
        GrailsResourceUtils.extractJarFileURL(jarWithoutProtocol) == new URL('file:/C:/mypath/myjar.jar')

        when:
        def connection = url.openConnection()
        GrailsResourceUtils.useCachesIfNecessary(connection)

        then:
        !connection.useCaches
    }

    void 'getFile rejects a non-file #kind location'() {
        when:
        GrailsResourceUtils.getFile(location, 'remote resource')

        then:
        def error = thrown(FileNotFoundException)
        error.message.contains('remote resource cannot be resolved to absolute file path')

        where:
        kind  | location
        'URL' | new URL('https://example.invalid/Book.groovy')
        'URI' | new URI('https://example.invalid/Book.groovy')
    }

    void 'configuration instantiation uses configured names and falls back for non-string values'() {
        given:
        ConfigObject config = new ConfigObject()
        config.helper.type = 'java.lang.StringBuilder'

        expect:
        GrailsResourceUtils.instantiateFromConfig(config, 'helper.type', 'java.util.ArrayList') instanceof StringBuilder
        GrailsResourceUtils.instantiateFromFlatConfig([type: 'java.lang.StringBuilder'], 'type', 'java.util.ArrayList') instanceof StringBuilder
        GrailsResourceUtils.instantiateFromFlatConfig([type: 42], 'type', 'java.util.ArrayList') instanceof ArrayList
        GrailsResourceUtils.instantiateFromFlatConfig([:], 'type', 'java.util.ArrayList') instanceof ArrayList
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

    void 'COMPILER_ROOT_PATTERNS contains the spring scripts and resource path patterns'() {
        expect:
        GrailsResourceUtils.COMPILER_ROOT_PATTERNS.length == 2
        GrailsResourceUtils.COMPILER_ROOT_PATTERNS[1].matcher(
                '/project/grails-app/controllers/com/example/BookController.groovy').find()
    }

    void 'getFile falls back to the URL file when the URL is not a valid URI'() {
        expect:
        GrailsResourceUtils.getFile(new URL('file:/tmp/a|b.txt'), 'desc') == new File('/tmp/a|b.txt')
    }

    void 'isFileURL also accepts the vfs family of protocols'() {
        expect:
        GrailsResourceUtils.isFileURL(urlWithProtocol('vfs', '/tmp/x'))
        GrailsResourceUtils.isFileURL(urlWithProtocol('vfszip', '/tmp/x'))
        !GrailsResourceUtils.isFileURL(urlWithProtocol('other', '/tmp/x'))
    }

    void 'isJarURL accepts a code-source URL only when it points inside a jar'() {
        expect:
        GrailsResourceUtils.isJarURL(urlWithProtocol('code-source', '/x/y.jar!/z'))
        !GrailsResourceUtils.isJarURL(urlWithProtocol('code-source', '/x/y'))
    }

    @Unroll
    void 'extractJarFileURL(#protocol:#spec) is #expected'() {
        expect:
        GrailsResourceUtils.extractJarFileURL(urlWithProtocol(protocol, spec)).toString() == expected

        where:
        protocol | spec                          | expected
        'jar'    | 'file:/tmp/a.jar!/x/y.txt'    | 'file:/tmp/a.jar'
        'jar'    | 'C:/mypath/myjar.jar!/x.txt'  | 'file:/C:/mypath/myjar.jar'
        'jar'    | '/mypath/myjar.jar!/x.txt'    | 'file:/mypath/myjar.jar'
        'http'   | '//example.com/a.jar'         | 'http://example.com/a.jar'
    }

    void 'getPathFromBaseDir makes other paths relative to the base directory when there is one'() {
        given:
        File baseDir = grails.util.BuildSettings.BASE_DIR

        expect:
        baseDir == null ? GrailsResourceUtils.getPathFromBaseDir('x/y.txt') == null :
                GrailsResourceUtils.getPathFromBaseDir(new File(baseDir, 'x/y.txt').path) == 'x/y.txt'
    }

    void 'instantiateFromFlatConfig fails for an unknown class'() {
        when:
        GrailsResourceUtils.instantiateFromFlatConfig([k: 'no.such.Type'], 'k', 'java.util.HashMap')

        then:
        thrown(ClassNotFoundException)
    }
}
