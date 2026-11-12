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
package org.grails.web.taglib

import java.nio.file.Files
import java.nio.file.Path

import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.taglib.index.TagLibraryIndex
import spock.lang.Specification
import spock.lang.TempDir

/**
 * With the framework tag libraries on the compile classpath, their compile-time descriptors let a GSP
 * be checked against the tags that actually exist, rather than deferring every tag call to runtime
 * dispatch.
 */
class GspStaticTagResolutionSpec extends Specification {

    @TempDir
    Path tempDir

    GroovyPagesTemplateEngine gpte

    def setup() {
        gpte = engineFor(null)
    }

    /**
     * The strictness and dynamic namespaces a build declares reach the compiler as a classpath
     * resource written by the {@code generateTagLibraryIndex} task, so a compilation that is meant to
     * see them is given a class loader that can.
     */
    private GroovyPagesTemplateEngine engineFor(String settings) {
        ClassLoader parent = getClass().classLoader
        if (settings != null) {
            Path settingsDir = Files.createTempDirectory(tempDir, 'settings')
            Path indexDir = Files.createDirectories(settingsDir.resolve(TagLibraryIndex.INDEX_LOCATION))
            indexDir.resolve('compile-settings.properties').toFile().text = settings
            parent = new URLClassLoader([settingsDir.toUri().toURL()] as URL[], parent)
        }
        GroovyPagesTemplateEngine engine = new GroovyPagesTemplateEngine()
        engine.classLoader = parent
        engine.afterPropertiesSet()
        engine
    }

    void 'the framework tag libraries are visible through their compile-time descriptors'() {
        given:
        TagLibraryIndex index = TagLibraryIndex.load(getClass().classLoader)

        expect:
        index.hasNamespace('g')
        index.lookup('g', 'message') != null
        index.isKnown('g', 'link')
    }

    void 'a statically compiled page calling a known tag compiles'() {
        given:
        String template = '''<%@ page compileStatic="true" %>${g.message(code: 'some.code')}'''

        when:
        def t = gpte.createTemplate(template, 'known-tag')

        then:
        t.metaInfo.compilationException == null
    }

    void 'an unrecognised tag does not fail the build by default'() {
        given: 'a namespace can hold tag libraries the index never saw, so absence is not a misspelling'
        String template = '''<%@ page compileStatic="true" %>${g.mesage(code: 'typo')}'''

        when:
        def t = gpte.createTemplate(template, 'unknown-tag-lenient')

        then: 'it resolves at runtime as it did before, with nothing reported'
        t.metaInfo.compilationException == null
    }

    void 'a page that has not declared compileStatic is never judged against the index'() {
        given: 'such a page resolves the receiver against its model, which the build cannot see'
        GroovyPagesTemplateEngine strict = engineFor('strictTags=true\n')
        String template = '''${g.custom(code: 'from the model')}'''

        when:
        def t = strict.createTemplate(template, 'dynamic-page-strict')

        then: 'reporting it would reject a call this release deliberately still allows'
        t.metaInfo.compilationException == null
    }

    void 'an unrecognised tag fails compilation when the build declares its tags complete'() {
        given: 'g is a namespace this project describes, so what it holds is knowable'
        GroovyPagesTemplateEngine strict = engineFor('strictTags=true\nlocalNamespaces=g\n')
        String template = '''<%@ page compileStatic="true" %>${g.mesage(code: 'typo')}'''

        when:
        def t = strict.createTemplate(template, 'unknown-tag-strict')

        then: 'the misspelling is reported when the page is compiled rather than when it renders'
        t.metaInfo.compilationException != null
        t.metaInfo.compilationException.message.contains('No such tag [mesage]')
        t.metaInfo.compilationException.message.contains('namespace [g]')
    }

    void 'an unrecognised tag in a namespace this project does not declare is never reported'() {
        given: 'strict checking on, but g is filled in by tag libraries from elsewhere'
        GroovyPagesTemplateEngine strict = engineFor('strictTags=true\n')
        String template = '''<%@ page compileStatic="true" %>${g.mesage(code: 'typo')}'''

        when:
        def t = strict.createTemplate(template, 'unknown-tag-foreign-namespace')

        then: 'how many of them carry descriptors is not knowable, so a missing tag proves nothing'
        t.metaInfo.compilationException == null
    }

    void 'an unrecognised tag in a declared dynamic namespace is never reported'() {
        given: 'the build said this namespace is filled in while the application runs'
        GroovyPagesTemplateEngine strict = engineFor('strictTags=true\ndynamicTagNamespaces=g\n')
        String template = '''<%@ page compileStatic="true" %>${g.mesage(code: 'typo')}'''

        when:
        def t = strict.createTemplate(template, 'dynamic-namespace-tag')

        then:
        t.metaInfo.compilationException == null
    }

    void 'a tag written as markup is checked against the same descriptions'() {
        given:
        GroovyPagesTemplateEngine strict = engineFor('strictTags=true\nlocalNamespaces=g\n')
        String template = '''<%@ page compileStatic="true" %><g:mesage code="typo"/>'''

        when:
        def t = strict.createTemplate(template, 'unknown-markup-tag')

        then:
        t.metaInfo.compilationException != null
        t.metaInfo.compilationException.message.contains('No such tag [mesage]')
    }

    void 'a tag declared by two tag libraries is never reported as unknown'() {
        given: 'ambiguity means the tag exists but which one runs is decided at runtime'
        GroovyPagesTemplateEngine strict = engineFor('strictTags=true\n')
        String template = '''<%@ page compileStatic="true" %>${g.link(controller: 'book')}'''

        when:
        def t = strict.createTemplate(template, 'ambiguous-not-unknown')

        then: 'a resolvable tag still compiles when the build declares its tags complete'
        t.metaInfo.compilationException == null
    }

    void 'a namespace with no compiled tag library still resolves dynamically'() {
        given: 'a namespace the index knows nothing about, as a runtime-registered tag library would be'
        String template = '''<%@ page compileStatic="true" taglibs="somepluginns" %>${somepluginns.anything(a: 1)}'''

        when:
        def t = gpte.createTemplate(template, 'unindexed-namespace')

        then: 'compilation succeeds and the call is left to runtime dispatch'
        t.metaInfo.compilationException == null
    }
}
