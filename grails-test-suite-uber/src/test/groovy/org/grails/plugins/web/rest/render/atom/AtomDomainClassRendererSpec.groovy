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

package org.grails.plugins.web.rest.render.atom

import grails.rest.render.Renderer
import grails.rest.render.atom.AtomRenderer
import grails.util.GrailsWebUtil
import org.grails.plugins.web.rest.render.Author
import org.grails.plugins.web.rest.render.BaseDomainClassRendererSpec
import org.grails.plugins.web.rest.render.Book
import org.grails.plugins.web.rest.render.ServletRenderContext
import org.springframework.context.support.StaticMessageSource
import org.springframework.web.util.WebUtils

/**
 * @author Graeme Rocher
 */
class AtomDomainClassRendererSpec extends BaseDomainClassRendererSpec {

    void 'Test that the Atom renderer renders domain objects with appropriate links'() {

        given: 'An Atom renderer'
            def renderer = getRenderer()

        and: 'A Book domain object'
            def author = Author.create(2, 'Stephen King')
            def author2 = Author.create(3, 'King Stephen')
            def book = Book.create(1, 'The Stand', author)
            book.link(href: '/publisher', rel: 'The Publisher')
            book.authors.addAll(author, author2)

        when: 'The Book is rendered'
            def request = setupRequest('application/atom+xml')
            def response = setupResponse(request)
            def renderContext = new ServletRenderContext(request)
            renderer.render(book, renderContext)

        then: 'The resulting Atom is correct'
            def expectedContent = toCompactXml('''
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                    <id>tag:localhost:1</id>
                    <link rel="self" href="http://localhost/books/1" hreflang="en" type="application/atom+xml" />
                    <link rel="alternate" href="http://localhost/books/1" hreflang="en" />
                    <link rel="The Publisher" href="/publisher" hreflang="en" />
                    <link rel="author" href="http://localhost/authors/2" hreflang="en" />
                    <title>The Stand</title>
                    <entry>
                        <title>org.grails.plugins.web.rest.render.Author : 2</title>
                        <id>tag:localhost:2</id>
                        <link rel="self" href="http://localhost/authors/2" hreflang="en" type="application/atom+xml" />
                        <link rel="alternate" href="http://localhost/authors/2" hreflang="en" />
                        <name>Stephen King</name>
                    </entry>
                    <authors>
                        <entry>
                            <title>org.grails.plugins.web.rest.render.Author : 2</title>
                            <id>tag:localhost:2</id>
                            <link rel="self" href="http://localhost/authors/2" hreflang="en" type="application/atom+xml" />
                            <link rel="alternate" href="http://localhost/authors/2" hreflang="en" />
                            <name>Stephen King</name>
                        </entry>
                        <entry>
                            <title>org.grails.plugins.web.rest.render.Author : 3</title>
                            <id>tag:localhost:3</id>
                            <link rel="self" href="http://localhost/authors/3" hreflang="en" type="application/atom+xml" />
                            <link rel="alternate" href="http://localhost/authors/3" hreflang="en" />
                            <name>King Stephen</name>
                        </entry>
                    </authors>
                </feed>            
            ''')
            response.contentAsString == expectedContent
            response.contentType == GrailsWebUtil.getContentType(AtomRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
    }

    void 'Test that the Atom renderer renders a list of domain objects with the appropriate links'() {

        given: 'An Atom renderer'
            def renderer = getRenderer()

        and: 'A Book domain object'
            def author = Author.create(2, 'Stephen King')
            def author2 = Author.create(3, 'King Stephen')
            def book = Book.create(1, 'The Stand', author)
            book.link(href: '/publisher', rel: 'The Publisher')
            book.authors.addAll(author, author2)

        when: 'The Author collection of the Book is rendered'
            def webRequest = setupRequest('application/atom+xml')
            def response = setupResponse(webRequest)
            def renderContext = new ServletRenderContext(webRequest)
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, '/authors')
            renderer.render(book.authors, renderContext)

        then: 'The resulting Atom is correct'
            def expectedContent = toCompactXml('''
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                    <title></title>
                    <id>tag:localhost:/authors</id>
                    <link rel="self" href="http://localhost/authors" hreflang="en" type="application/atom+xml" />
                    <link rel="alternate" href="http://localhost/authors" hreflang="en" />
                    <entry>
                        <title>org.grails.plugins.web.rest.render.Author : 2</title>
                        <id>tag:localhost:2</id>
                        <link rel="self" href="http://localhost/authors/2" hreflang="en" type="application/atom+xml" />
                        <link rel="alternate" href="http://localhost/authors/2" hreflang="en" />
                        <name>Stephen King</name>
                    </entry>
                    <entry>
                        <title>org.grails.plugins.web.rest.render.Author : 3</title>
                        <id>tag:localhost:3</id>
                        <link rel="self" href="http://localhost/authors/3" hreflang="en" type="application/atom+xml" />
                        <link rel="alternate" href="http://localhost/authors/3" hreflang="en" />
                        <name>King Stephen</name>
                    </entry>
                </feed>
            ''')
            response.contentAsString == expectedContent
            response.contentType == GrailsWebUtil.getContentType(AtomRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
    }

    protected Renderer getRenderer() {
        def renderer = new AtomRenderer(Book)
        renderer.mappingContext = mappingContext
        renderer.messageSource = new StaticMessageSource()
        renderer.linkGenerator = getLinkGenerator {
            '/books'(resources: 'book')
            '/authors'(resources: 'author')
        }
        return renderer
    }
}
