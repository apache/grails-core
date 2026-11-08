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

import org.grails.gsp.GroovyPageMetaInfo

/**
 * Precompiled GSPs must not vary with the modification time of their source.
 *
 * Git records no modification times, so every fresh clone or CI checkout gives each .gsp a new one. Baking
 * that into the generated class made otherwise identical jars differ on every checkout, and because
 * {@code LAST_MODIFIED} was a compile-time constant the difference survived Gradle's compile-classpath
 * normalization, so every downstream task missed the build cache.
 */
class GroovyPageCompilerReproducibilitySpec extends Specification {

    private static final String PAGE_CONTENT = '<html><body><g:if test="${flag}">Hello</g:if></body></html>'

    @TempDir
    File tempDir

    private File viewsDir
    private File page

    void setup() {
        this.viewsDir = new File(this.tempDir, 'views')
        this.page = new File(this.viewsDir, 'index.gsp')
        this.page.parentFile.mkdirs()
        this.page.text = PAGE_CONTENT
    }

    void 'a source compiled at two different modification times produces identical classes'() {
        when: 'the same page is compiled twice, as two checkouts of one commit would'
        assert this.page.setLastModified(1_000_000_000_000L)
        byte[] first = compileToBytes('first')

        and:
        assert this.page.setLastModified(1_700_000_000_000L)
        byte[] second = compileToBytes('second')

        then: 'the jar built from them is byte-identical, so downstream tasks keep their cache hits'
        first == second
    }

    void 'a compiled page records a checksum of its source'() {
        when:
        GroovyPageMetaInfo metaInfo = compileToMetaInfo('recorded')

        then: 'the checksum identifies the content, and no modification time is baked in'
        metaInfo.sourceChecksum ==~ /[0-9a-f]{64}/
    }

    void 'an edited source produces a different checksum'() {
        given:
        GroovyPageMetaInfo before = compileToMetaInfo('before')

        when:
        this.page.text = '<html><body>something else entirely</body></html>'
        GroovyPageMetaInfo after = compileToMetaInfo('after')

        then: 'the runtime can still tell that the page changed'
        before.sourceChecksum != after.sourceChecksum
    }

    private Map compile(File targetDir) {
        targetDir.mkdirs()
        GroovyPageCompiler compiler = new GroovyPageCompiler()
        compiler.viewsDir = this.viewsDir
        compiler.srcFiles = [this.page]
        compiler.targetDir = targetDir
        compiler.generatedGroovyPagesDirectory = new File(this.tempDir, 'generated').tap { mkdirs() }
        compiler.compile()
    }

    private byte[] compileToBytes(String name) {
        File targetDir = new File(this.tempDir, name)
        Map results = compile(targetDir)
        new File(targetDir, "${results.values().first()}.class").bytes
    }

    private GroovyPageMetaInfo compileToMetaInfo(String name) {
        File targetDir = new File(this.tempDir, name)
        Map results = compile(targetDir)
        new URLClassLoader([targetDir.toURI().toURL()] as URL[], getClass().classLoader).withCloseable { URLClassLoader loader ->
            new GroovyPageMetaInfo(loader.loadClass(results.values().first() as String))
        }
    }
}
