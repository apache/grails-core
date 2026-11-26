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
package org.grails.gsp

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import org.grails.gsp.compiler.GroovyPageCompiler
import org.grails.gsp.io.GroovyPageCompiledScriptSource

/**
 * Long text is handed from the scanner to the parser in pieces of 64,000 characters. An escaped
 * expression at the end of a piece is still one.
 */
class GspLongTextEscapeSpec extends Specification {

    @TempDir
    File tempDir

    GroovyPagesTemplateEngine engine

    void setup() {
        engine = new GroovyPagesTemplateEngine()
        engine.afterPropertiesSet()
    }

    @Unroll
    void "an escaped expression after #length characters of text is written without its backslash"() {
        given:
        String text = 'x' * length

        expect:
        render(text + '\\${a} and ${1 + 1}') == text + '${a} and 2'

        where:
        length << (63_990..64_005) + (127_990..128_005)
    }

    @Unroll
    void "#lookalike after #length characters of text is not an escaped expression and is written as it is"() {
        given:
        String text = 'x' * length

        expect:
        render(text + lookalike + ' and ${1 + 1}') == text + lookalike + ' and 2'

        where:
        [length, lookalike] << [63_997..64_000, ['\\$ {a}', '\\{a}', '$\\{a}']].combinations()
    }

    @Unroll
    void "a compiled page with an escaped expression after #length characters, #twoByte of them two bytes long, is written without its backslash"() {
        given: 'text whose first 64,000 characters take up to the 65,535 bytes a compiled page can store as one piece'
        String text = ('é' * twoByte) + ('x' * (length - twoByte))

        expect:
        renderCompiled(text + '\\${a} and ${1 + 1}') == text + '${a} and 2'

        where:
        length | twoByte
        63_998 | 0
        63_999 | 0
        63_998 | 1_535
        63_999 | 1_535
        64_000 | 1_535
    }

    private String render(String source) {
        StringWriter out = new StringWriter()
        engine.createTemplate(source, "long${source.hashCode().abs()}").make([:]).writeTo(out)
        out.toString()
    }

    private String renderCompiled(String source) {
        File viewsDir = new File(tempDir, 'views')
        File classesDir = new File(tempDir, 'classes')
        viewsDir.mkdirs()
        classesDir.mkdirs()
        File page = new File(viewsDir, 'page.gsp')
        page.setText(source, 'UTF-8')

        GroovyPageCompiler compiler = new GroovyPageCompiler()
        compiler.viewsDir = viewsDir
        compiler.targetDir = classesDir
        compiler.generatedGroovyPagesDirectory = new File(tempDir, 'generated')
        compiler.srcFiles = [page]
        Map compiled = compiler.compile()

        new URLClassLoader([classesDir.toURI().toURL()] as URL[], getClass().classLoader).withCloseable { URLClassLoader loader ->
            Class<?> pageClass = loader.loadClass(compiled['/page.gsp'] as String)
            StringWriter out = new StringWriter()
            engine.createTemplate(new GroovyPageCompiledScriptSource('/page.gsp', '/page.gsp', pageClass)).make([:]).writeTo(out)
            out.toString()
        }
    }
}
