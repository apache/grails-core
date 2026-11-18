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
package org.grails.gsp.compiler

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Covers the view registry {@link GroovyPageCompiler} writes beside the classes it compiles. The
 * registry is what resolves a view to its compiled class at runtime, so an entry that outlives the
 * page it names sends the locator after a class that is no longer there.
 */
class GroovyPageCompilerSpec extends Specification {

    @TempDir
    File tempDir

    private File viewsDir
    private File targetDir
    private File generatedDir

    void setup() {
        viewsDir = new File(tempDir, 'views')
        viewsDir.mkdirs()
        targetDir = new File(tempDir, 'classes')
        targetDir.mkdirs()
        // the compiler writes the Groovy it generates for each page under java.io.tmpdir when it is
        // given nowhere else, which every parallel fork would share and none would clean up
        generatedDir = new File(tempDir, 'generated')
        generatedDir.mkdirs()
    }

    void 'the registry names every compiled page'() {
        given:
        writeView('index.gsp')
        writeView('layouts/main.gsp')

        when:
        compile()

        then:
        registry().keySet() == ['/index.gsp', '/layouts/main.gsp'] as Set
    }

    void 'a page removed since the last compile leaves the registry'() {
        given: 'a registry written for two pages'
        writeView('index.gsp')
        File removed = writeView('obsolete.gsp')
        compile()

        when: 'one of them is gone and the pages are compiled again'
        removed.delete()
        compile()

        then: 'the registry names only the page that is still there'
        registry().keySet() == ['/index.gsp'] as Set
    }

    void 'what was compiled for a page removed since the last compile is removed with it'() {
        given: 'two pages compiled, one of them with a closure, which compiles to an inner class'
        writeView('index.gsp')
        File removed = writeView('obsolete.gsp', '<% [1, 2].each { int i -> out << i } %>')
        compile()
        List<String> left = targetDir.list().findAll { it.contains('obsolete') }
        assert left.any { it.endsWith('_html.data') } && left.any { it.contains('$') }

        and: 'a file the compiler did not write'
        File unrelated = new File(targetDir, 'gsp_probeobsolete_gsp.txt')
        unrelated.text = 'not a page'

        when:
        removed.delete()
        compile()

        then: 'nothing is left of the removed page, and what else is there is kept'
        targetDir.list().findAll { it.contains('obsolete') } == [unrelated.name]
        targetDir.list().any { it.startsWith('gsp_probeindex_gsp') }
    }

    void 'a page whose name extends a removed one is not taken for part of it'() {
        given:
        File removed = writeView('a.gsp')
        writeView('a_gsp$b.gsp')
        compile()

        when:
        removed.delete()
        compile()

        then:
        !targetDir.list().any { it.startsWith('gsp_probea_gsp.') || it.startsWith('gsp_probea_gsp_') }
        registry().values().every { String pageClass -> new File(targetDir, "${pageClass}.class").isFile() }
    }

    void 'a generated page is compiled with the views, named by its path under its own directory'() {
        given:
        File generated = new File(tempDir, 'generated-views')
        writeView('index.gsp')
        writeView('grails-scaffolded/com.example.Book/show-0123.gsp', null, generated)

        when:
        compile('/', [], [generated])

        then: 'one compilation and one registry, which is what the application reads'
        registry().keySet() == ['/index.gsp', '/grails-scaffolded/com.example.Book/show-0123.gsp'] as Set
        registry().values().every { String pageClass -> new File(targetDir, "${pageClass}.class").isFile() }
    }

    void 'a page of the views takes precedence over a generated page at its path'() {
        given:
        File generated = new File(tempDir, 'generated-views')
        writeView('shared/page.gsp', '<p>written</p>')
        writeView('shared/page.gsp', '<p>generated</p>', generated)

        when:
        compile('/', [], [generated])

        then:
        registry().keySet() == ['/shared/page.gsp'] as Set
        targetDir.listFiles().find { it.name.endsWith('_html.data') }.bytes.with { new String(it, 'UTF-8') }.contains('written')
    }

    void 'a generated page is optional by its path under its own directory'() {
        given:
        File generated = new File(tempDir, 'generated-views')
        writeView('index.gsp')
        writeView('grails-scaffolded/broken.gsp', '<% def x = ; %>', generated)

        when:
        compile('/', ['grails-scaffolded/broken.gsp'], [generated])

        then:
        registry().keySet() == ['/index.gsp'] as Set
        lastCompiler.leftOut.keySet() == ['grails-scaffolded/broken.gsp'] as Set
    }

    void 'a page recompiled under a different prefix is registered only under the new one'() {
        given: 'a registry written under the prefix a Grails application looks views up by'
        writeView('index.gsp')
        compile('/WEB-INF/grails-app/views/')

        when: 'the pages are compiled again under the prefix a standalone application looks them up by'
        compile('/')

        then:
        registry().keySet() == ['/index.gsp'] as Set
    }

    void 'an optional page that does not compile is left out, and the others compiled'() {
        given:
        writeView('index.gsp')
        writeView('generated/good.gsp')
        writeView('generated/broken.gsp', '<% def x = ; %>')

        when:
        compile('/', ['generated/good.gsp', 'generated/broken.gsp'])

        then: 'nothing names the page that did not compile, so it is produced when it is rendered'
        registry().keySet() == ['/index.gsp', '/generated/good.gsp'] as Set
        !targetDir.listFiles().any { it.name.contains('broken') }

        and: 'the page is reported'
        lastCompiler.leftOut.keySet() == ['generated/broken.gsp'] as Set
    }

    void 'a page that is not optional and does not compile fails the compilation, beside optional ones or not'() {
        given:
        writeView(page, '<% def x = ; %>')
        writeView('generated/optional.gsp')

        when:
        compile('/', ['generated/optional.gsp'])

        then:
        thrown(Exception)

        where:
        page << ['index.gsp', 'generated/own.gsp']
    }

    private File writeView(String path, String content = null, File root = viewsDir) {
        File view = new File(root, path)
        view.parentFile.mkdirs()
        view.text = content ?: "<html><body>${path}</body></html>"
        view
    }

    private GroovyPageCompiler lastCompiler

    private void compile(String viewPrefix = '/', List<String> optional = [], List<File> generated = []) {
        GroovyPageCompiler compiler = new GroovyPageCompiler()
        lastCompiler = compiler
        compiler.optionalPages = optional as Set<String>
        compiler.generatedViewsDirs = generated
        compiler.viewsDir = viewsDir
        compiler.targetDir = targetDir
        compiler.generatedGroovyPagesDirectory = generatedDir
        compiler.viewPrefix = viewPrefix
        compiler.packagePrefix = 'probe'
        compiler.srcFiles = []
        ([viewsDir] + generated).each { File root ->
            root.eachFileRecurse { File file ->
                if (file.name.endsWith('.gsp')) {
                    compiler.srcFiles << file
                }
            }
        }
        compiler.compile()
    }

    private Properties registry() {
        Properties views = new Properties()
        new File(targetDir, 'gsp/views.properties').withInputStream { views.load(it) }
        views
    }

}
