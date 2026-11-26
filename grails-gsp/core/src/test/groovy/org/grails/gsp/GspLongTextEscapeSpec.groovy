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
import spock.lang.Unroll

/**
 * Long text is handed from the scanner to the parser in pieces of 64,000 characters. An escaped
 * expression at the end of a piece is still one.
 */
class GspLongTextEscapeSpec extends Specification {

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
    void "an expression after #length characters of text is evaluated"() {
        expect:
        render('x' * length + '${1 + 1}$ {\\$ \\{') == 'x' * length + '2$ {\\$ \\{'

        where:
        length << (63_996..64_002)
    }

    private String render(String source) {
        StringWriter out = new StringWriter()
        engine.createTemplate(source, "long${source.hashCode().abs()}").make([:]).writeTo(out)
        out.toString()
    }
}
