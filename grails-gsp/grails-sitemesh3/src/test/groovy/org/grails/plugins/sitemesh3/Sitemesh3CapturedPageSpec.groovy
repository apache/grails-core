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
package org.grails.plugins.sitemesh3

import org.grails.buffer.FastStringWriter
import org.grails.buffer.StreamCharBuffer

import spock.lang.Specification

class Sitemesh3CapturedPageSpec extends Specification {

    void "strips the captured title element from the head"() {
        given:
        Sitemesh3CapturedPage page = pageWithHead('<meta charset="UTF-8"><title>My Page</title><link rel="x">')

        expect:
        headOf(page) == '<meta charset="UTF-8"><link rel="x">'
    }

    void "strips a title element that carries attributes"() {
        given:
        Sitemesh3CapturedPage page = pageWithHead('<title data-x="1">My Page</title><meta charset="UTF-8">')

        expect:
        headOf(page) == '<meta charset="UTF-8">'
    }

    void "elements whose names merely start with title are not treated as the title"() {
        given: 'a custom element before the real title'
        Sitemesh3CapturedPage page = pageWithHead('<titlebar>tools</titlebar><title>My Page</title><meta charset="UTF-8">')

        expect: 'only the real title is removed'
        headOf(page) == '<titlebar>tools</titlebar><meta charset="UTF-8">'
    }

    void "a head with only title-prefixed custom elements is left untouched"() {
        given:
        Sitemesh3CapturedPage page = pageWithHead('<title-x>tools</title-x><meta charset="UTF-8">')

        expect:
        headOf(page) == '<title-x>tools</title-x><meta charset="UTF-8">'
    }

    private Sitemesh3CapturedPage pageWithHead(String head) {
        Sitemesh3CapturedPage page = new Sitemesh3CapturedPage()
        page.setHeadBuffer(bufferOf(head))
        page.setTitleBuffer(bufferOf('My Page'))
        page.setTitleCaptured(true)
        page
    }

    private String headOf(Sitemesh3CapturedPage page) {
        page.getExtractedProperties().getChild('head').value
    }

    private StreamCharBuffer bufferOf(String value) {
        FastStringWriter writer = new FastStringWriter()
        writer.print(value)
        writer.buffer
    }
}
