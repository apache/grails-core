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

    @Unroll
    void 'getClassName(#path) is #expected'() {
        expect:
        GrailsResourceUtils.getClassName(path) == expected

        where:
        path                                              | expected
        '/p/grails-app/domain/com/acme/Book.groovy'       | 'com.acme.Book'
        '/p/grails-app/controllers/BookController.groovy' | 'BookController'
        '/p/grails-app/conf/spring/MyBeans.groovy'        | 'MyBeans'
        '/p/src/main/groovy/com/acme/Util.groovy'         | 'com.acme.Util'
        '/p/src/main/java/com/acme/Util.java'             | 'com.acme.Util'
        '/p/src/test/groovy/com/acme/UtilSpec.groovy'     | 'com.acme.UtilSpec'
        '/p/src/test/java/com/acme/UtilTest.java'         | 'com.acme.UtilTest'
        '/p/src/test/functional/com/acme/FuncSpec.groovy' | 'com.acme.FuncSpec'
        'C:\\p\\grails-app\\services\\com\\acme\\Svc.groovy' | 'com.acme.Svc'
        '/p/other/Unknown.groovy'                         | null
        '/p/readme.txt'                                   | null
    }

    void 'getClassName of a resource uses the absolute file path'() {
        expect:
        GrailsResourceUtils.getClassName(new FileSystemResource('/p/grails-app/domain/com/acme/Book.groovy')) == 'com.acme.Book'
    }

    void 'getClassName of a resource that is not a file is null'() {
        expect:
        GrailsResourceUtils.getClassName(new UrlResource('http://example.com/grails-app/domain/Book.groovy')) == null
    }

    void 'getClassNameForClassFile removes the root, separators and extension'() {
        expect:
        GrailsResourceUtils.getClassNameForClassFile('/root/', '/root/com/acme/Foo.class') == 'com.acme.Foo'
        GrailsResourceUtils.getClassNameForClassFile(null, 'com/acme/Foo.class') == 'com.acme.Foo'
        GrailsResourceUtils.getClassNameForClassFile(null, 'com\\acme\\Foo.class') == 'com.acme.Foo'
    }

    void 'getFile resolves a file URL and rejects other protocols'() {
        expect:
        GrailsResourceUtils.getFile(new URL('file:/tmp/a.txt'), 'desc') == new File('/tmp/a.txt')
        GrailsResourceUtils.getFile(new URL('file:/tmp/a b.txt'), 'desc') == new File('/tmp/a b.txt')

        when:
        GrailsResourceUtils.getFile(new URL('http://example.com/a.txt'), 'the resource')

        then:
        FileNotFoundException e = thrown()
        e.message.startsWith('the resource cannot be resolved to absolute file path')
    }

    void 'getFile falls back to the URL file when the URL is not a valid URI'() {
        expect:
        GrailsResourceUtils.getFile(new URL('file:/tmp/a|b.txt'), 'desc') == new File('/tmp/a|b.txt')
    }

    void 'getFile resolves a file URI and rejects other schemes'() {
        expect:
        GrailsResourceUtils.getFile(new URI('file:/tmp/a.txt'), 'desc') == new File('/tmp/a.txt')
        GrailsResourceUtils.getFile(new URI('file:/tmp/b.txt')) == new File('/tmp/b.txt')

        when:
        GrailsResourceUtils.getFile(new URI('http://example.com/a.txt'))

        then:
        FileNotFoundException e = thrown()
        e.message.startsWith('URI cannot be resolved to absolute file path')
    }

    void 'toURI escapes spaces'() {
        expect:
        GrailsResourceUtils.toURI('file:/tmp/a b.txt') == new URI('file:/tmp/a%20b.txt')
        GrailsResourceUtils.toURI(new URL('file:/tmp/a b.txt')) == new URI('file:/tmp/a%20b.txt')
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

    void 'useCachesIfNecessary switches caching off for ordinary connections'() {
        given:
        URLConnection connection = new URL('file:/tmp/x').openConnection()
        connection.useCaches = true

        when:
        GrailsResourceUtils.useCachesIfNecessary(connection)

        then:
        !connection.useCaches
    }

    @Unroll
    void 'cleanPath(#path) is #expected'() {
        expect:
        GrailsResourceUtils.cleanPath(path) == expected

        where:
        path                          | expected
        '/foo/bar'                    | '/foo/bar'
        '../foo'                      | '../foo'
        'foo/../../bar'               | '../bar'
        'file:core/../core/io/R.class' | 'file:core/io/R.class'
        'c:\\dir\\file.txt'          | 'c:/dir/file.txt'
        ''                            | ''
        'a//b'                        | 'a//b'
    }

    @Unroll
    void 'applyRelativePath(#path, #relative) is #expected'() {
        expect:
        GrailsResourceUtils.applyRelativePath(path, relative) == expected

        where:
        path         | relative | expected
        'a/b/c.txt'  | 'd.txt'  | 'a/b/d.txt'
        'a/b/c.txt'  | '/d.txt' | 'a/b/d.txt'
        'c.txt'      | 'd.txt'  | 'd.txt'
    }

    @Unroll
    void 'isGrailsPath(#path) is #expected'() {
        expect:
        GrailsResourceUtils.isGrailsPath(path) == expected
        GrailsResourceUtils.isGrailsPath(path) == expected

        where:
        path                                      | expected
        '/p/grails-app/views/book/Show.groovy'    | true
        '/p/grails-app/conf/spring/Beans.groovy'  | true
        '/p/src/main/groovy/com/acme/Util.groovy' | false
        '/p/readme.txt'                           | false
    }

    @Unroll
    void 'isProjectSource(#path) is #expected'() {
        expect:
        GrailsResourceUtils.isProjectSource(path) == expected

        where:
        path                                      | expected
        '/p/src/main/groovy/com/acme/Util.groovy' | true
        '/p/grails-app/domain/com/acme/Book.groovy' | true
        '/p/readme.txt'                           | false
    }

    void 'isProjectSource and isGrailsResource inspect the resource URL'() {
        expect:
        GrailsResourceUtils.isProjectSource(new UrlResource('file:/p/src/main/groovy/a/B.groovy'))
        GrailsResourceUtils.isProjectSource(new UrlResource('file:/p/FooGrailsPlugin.groovy'))
        !GrailsResourceUtils.isProjectSource(new UrlResource('file:/p/readme.txt'))
        GrailsResourceUtils.isGrailsResource(new UrlResource('file:/p/grails-app/views/a/B.groovy'))
        GrailsResourceUtils.isGrailsResource(new UrlResource('file:/p/FooGrailsPlugin.groovy'))
        !GrailsResourceUtils.isGrailsResource(new UrlResource('file:/p/readme.txt'))
    }

    void 'isProjectSource and isGrailsResource are false for a resource without a URL'() {
        given:
        Resource missing = new ClassPathResource('no/such/thing.groovy')

        expect:
        !GrailsResourceUtils.isProjectSource(missing)
        !GrailsResourceUtils.isGrailsResource(missing)
    }

    void 'getAppDir and getViewsDir find the grails-app directory of a file resource'() {
        given:
        Resource controller = new FileSystemResource('/p/grails-app/controllers/a/BookController.groovy')

        expect:
        GrailsResourceUtils.getAppDir(controller).file.name == 'grails-app'
        GrailsResourceUtils.getViewsDir(controller).path == '/p/grails-app/views'
    }

    void 'getAppDir finds the grails-app directory of a non file resource'() {
        given:
        Resource view = new UrlResource('http://example.com/x/grails-app/views/a/b.gsp')

        expect:
        GrailsResourceUtils.getAppDir(view) == new UrlResource('http://example.com/x/grails-app/')
    }

    void 'getAppDir and getViewsDir are null when there is no grails-app directory or resource'() {
        expect:
        GrailsResourceUtils.getAppDir(null) == null
        GrailsResourceUtils.getViewsDir(null) == null
        GrailsResourceUtils.getAppDir(new FileSystemResource('/p/other/A.groovy')) == null
        GrailsResourceUtils.getViewsDir(new FileSystemResource('/p/other/A.groovy')) == null
        GrailsResourceUtils.getAppDir(new ClassPathResource('no/such/thing.groovy')) == null
    }

    void 'getRelativeInsideWebInf locates a resource below WEB-INF'() {
        expect:
        GrailsResourceUtils.getRelativeInsideWebInf(new UrlResource('http://h/app/WEB-INF/grails-app/views/a.gsp')) == '/WEB-INF/grails-app/views/a.gsp'
        GrailsResourceUtils.getRelativeInsideWebInf(new UrlResource('http://h/plugins/foo-1.0/grails-app/views/a.gsp')) == '/WEB-INF/plugins/foo-1.0/grails-app/views/a.gsp'
        GrailsResourceUtils.getRelativeInsideWebInf(new UrlResource('http://h/x/grails-app/views/a.gsp')) == '/WEB-INF/grails-app/views/a.gsp'
        GrailsResourceUtils.getRelativeInsideWebInf(new UrlResource('http://h/x/readme.txt')) == null
        GrailsResourceUtils.getRelativeInsideWebInf(null) == null
        GrailsResourceUtils.getRelativeInsideWebInf(new ClassPathResource('no/such/thing')) == null
    }

    void 'getStaticResourcePathForResource maps plugin resources below the context path'() {
        given:
        Resource plugin = new UrlResource('http://h/plugins/foo-1.0/grails-app/assets/a.js')

        expect:
        GrailsResourceUtils.getStaticResourcePathForResource(plugin, '/ctx') == '/ctx/plugins/foo-1.0'
        GrailsResourceUtils.getStaticResourcePathForResource(plugin, '') == 'plugins/foo-1.0'
        GrailsResourceUtils.getStaticResourcePathForResource(plugin, null) == 'plugins/foo-1.0'
        GrailsResourceUtils.getStaticResourcePathForResource(new UrlResource('http://h/x/a.js'), '/ctx') == '/ctx'
        GrailsResourceUtils.getStaticResourcePathForResource(null, '/ctx') == '/ctx'
        GrailsResourceUtils.getStaticResourcePathForResource(null, null) == ''
        GrailsResourceUtils.getStaticResourcePathForResource(new ClassPathResource('no/such/thing'), '/ctx') == '/ctx'
    }

    void 'getPathFromRoot returns the file inside the artefact directory'() {
        expect:
        GrailsResourceUtils.getPathFromRoot('/p/grails-app/controllers/a/b/Foo.groovy') == 'a/b/Foo.groovy'
        GrailsResourceUtils.getPathFromRoot('/p/readme.txt') == null
    }

    void 'getPathFromBaseDir strips everything up to grails-app'() {
        expect:
        GrailsResourceUtils.getPathFromBaseDir('/p/grails-app/views/a.gsp') == 'views/a.gsp'
    }

    void 'getPathFromBaseDir makes other paths relative to the base directory when there is one'() {
        given:
        File baseDir = grails.util.BuildSettings.BASE_DIR

        expect:
        baseDir == null ? GrailsResourceUtils.getPathFromBaseDir('x/y.txt') == null :
                GrailsResourceUtils.getPathFromBaseDir(new File(baseDir, 'x/y.txt').path) == 'x/y.txt'
    }

    void 'getArtefactDirectory names the grails-app subdirectory'() {
        expect:
        GrailsResourceUtils.getArtefactDirectory('/p/grails-app/controllers/a/Foo.groovy') == 'controllers'
        GrailsResourceUtils.getArtefactDirectory('/p/readme.txt') == null
        GrailsResourceUtils.getArtefactDirectory(null) == null
    }

    @Unroll
    void 'appendPiecesForUri(#pieces) is #expected'() {
        expect:
        GrailsResourceUtils.appendPiecesForUri(pieces as String[]) == expected

        where:
        pieces             | expected
        ['a', 'b']         | 'a/b'
        ['a/', '/b']       | 'a/b'
        ['a', null, 'b']   | 'a/b'
        ['/a', '', 'b']    | '/a/b'
        []                 | ''
    }

    void 'appendPiecesForUri of null is empty'() {
        expect:
        GrailsResourceUtils.appendPiecesForUri((String[]) null) == ''
    }

    void 'instantiateFromFlatConfig creates the configured class or the default'() {
        expect:
        GrailsResourceUtils.instantiateFromFlatConfig([k: 'java.util.ArrayList'], 'k', 'java.util.HashMap') instanceof ArrayList
        GrailsResourceUtils.instantiateFromFlatConfig([:], 'k', 'java.util.HashMap') instanceof HashMap
        GrailsResourceUtils.instantiateFromFlatConfig([k: 42], 'k', 'java.util.HashMap') instanceof HashMap
    }

    void 'instantiateFromConfig flattens the config object first'() {
        given:
        ConfigObject config = new ConfigObject()
        config.my.impl = 'java.util.ArrayList'

        expect:
        GrailsResourceUtils.instantiateFromConfig(config, 'my.impl', 'java.util.HashMap') instanceof ArrayList
        GrailsResourceUtils.instantiateFromConfig(config, 'missing', 'java.util.HashMap') instanceof HashMap
    }

    void 'instantiateFromFlatConfig fails for an unknown class'() {
        when:
        GrailsResourceUtils.instantiateFromFlatConfig([k: 'no.such.Type'], 'k', 'java.util.HashMap')

        then:
        thrown(ClassNotFoundException)
    }

}
