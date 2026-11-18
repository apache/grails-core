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

package org.apache.grails.web.layout

import com.opensymphony.sitemesh.Content
import spock.lang.Specification

class GrailsHTMLPageParserSpec extends Specification {

    void 'HTML parser extracts title head body and layout properties'() {
        given:
        def html = '<html lang="en"><head><title>Title</title><meta name="layout" content="main"></head>' +
                '<body class="content"><p>Hello</p></body></html>'

        when:
        def page = new GrailsHTMLPageParser().parse(html.toCharArray())

        then:
        page.title == 'Title'
        page.head == '<meta name="layout" content="main">'
        page.body == '<p>Hello</p>'
        page.getProperty('lang') == 'en'
        page.getProperty('body.class') == 'content'
        page.getProperty('meta.layout') == 'main'
    }

    void 'titles inside XML do not replace the HTML title'() {
        given:
        def html = '<html><head><title>Page title</title></head>' +
                '<body><xml><book><title>Book title</title></book></xml>Body</body></html>'

        when:
        def page = new GrailsHTMLPageParser().parse(html.toCharArray())

        then:
        page.title == 'Page title'
        page.body.contains('<title>Book title</title>')
        page.body.endsWith('Body')
    }

    void 'parsed content exposes original head and body through the public Content interface'() {
        given:
        def html = '<html><head><title>Title</title><meta name="layout" content="main"></head><body>Body</body></html>'
        Content content = new GrailsHTMLPageParser().parseContent(html.toCharArray())
        def original = new StringWriter()
        def head = new StringWriter()
        def body = new StringWriter()

        when:
        content.writeOriginal(original)
        content.writeHead(head)
        content.writeBody(body)
        content.addProperty('page.extra', 'value')

        then:
        original.toString() == html
        content.originalLength() == html.length()
        head.toString() == '<meta name="layout" content="main">'
        body.toString() == 'Body'
        content.title == 'Title'
        content.getProperty('page.extra') == 'value'
        content.propertyKeys.toList().containsAll(['title', 'meta.layout', 'page.extra'])
        GSPGrailsLayoutPage.content2htmlPage(content).body == 'Body'
    }
}
