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
package org.grails.plugins.web.rest.render.hal

import grails.core.DefaultGrailsApplication
import grails.rest.render.hal.HalXmlRenderer
import grails.util.GrailsWebUtil
import org.grails.plugins.web.rest.render.*
import org.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import org.springframework.context.support.StaticMessageSource
import org.springframework.web.util.WebUtils

/**
 * @author Graeme Rocher
 */
class HalDomainClassXmlRendererSpec extends BaseDomainClassRendererSpec {

    void setup() {
        def initializer = new ConvertersConfigurationInitializer(grailsApplication: new DefaultGrailsApplication())
        initializer.initialize()
    }
    
    void cleanup() {
        ConvertersConfigurationHolder.clear()
    }

    void 'Test that the HAL renderer renders domain objects with appropriate links'() {
        given: 'A HAL renderer'
            def renderer = getRenderer()

        and: 'A Book domain object'
            def author = Author.create(2, 'Stephen King')
            def author2 = Author.create(3, 'King Stephen')
            def book = Book.create(1, 'The Stand', author)
            book.link(href: '/publisher', rel: 'The Publisher')
            book.authors.addAll(author, author2)

        when: 'A domain object is rendered'
            def webRequest = setupRequest('application/hal+xml')
            def response = setupResponse(webRequest)
            def renderContext = new ServletRenderContext(webRequest)
            renderer.render(book, renderContext)

        then: 'The resulting HAL is correct'
            def expectedContent = toCompactXml('''
                <?xml version="1.0" encoding="UTF-8"?>
                <resource href="http://localhost/books/1" hreflang="en">
                    <link rel="The Publisher" href="/publisher" hreflang="en" />
                    <link rel="author" href="http://localhost/authors/2" hreflang="en" />
                    <title>The Stand</title>
                    <resource href="http://localhost/authors/2" hreflang="en">
                        <name>Stephen King</name>
                    </resource>
                    <resource href="http://localhost/authors/3" hreflang="en">
                        <name>King Stephen</name>
                    </resource>
                </resource>
            ''')
            response.contentAsString == expectedContent
            response.contentType == GrailsWebUtil.getContentType(HalXmlRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
    }

    void 'Test that the HAL renderer renders regular linkable groovy objects with appropriate links'() {
        given: 'A HAL renderer'
            def renderer = getRenderer()

        and: 'A regular linkable groovy object'
            def product = new Product(name: 'MacBook', category: new Category(name: 'laptop'))
            product.link(rel: 'company', href: 'https://apple.com', title: 'Made by Apple')

        when: 'A domain object is rendered'
            def webRequest = setupRequest('application/hal+xml')
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, '/product/Macbook')
            def response = setupResponse(webRequest)
            def renderContext = new ServletRenderContext(webRequest)
            renderer.render(product, renderContext)

        then: 'The resulting HAL is correct'
            def expectedContent = toCompactXml('''
                <?xml version="1.0" encoding="UTF-8"?>
                <resource href="http://localhost/product/Macbook" hreflang="en">
                    <link rel="company" href="https://apple.com" hreflang="en" title="Made by Apple" />
                    <category>
                        <name>laptop</name>
                    </category>
                    <name>MacBook</name>
                </resource>
            ''')
            response.contentAsString == expectedContent
            response.contentType == GrailsWebUtil.getContentType(HalXmlRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
    }

    void 'Test that the HAL renderer renders a list of domain objects with the appropriate links'() {
        given: 'A HAL renderer'
            def renderer = getRenderer()

        and: 'A Book domain object'
            def author = Author.create(2, 'Stephen King')
            def author2 = Author.create(3, 'King Stephen')
            def book = Book.create(1, 'The Stand', author)
            book.link(href: '/publisher', rel: 'The Publisher')
            book.authors.addAll(author, author2)

        when: 'A domain object is rendered'
            def webRequest = setupRequest('application/hal+xml')
            def response = setupResponse(webRequest)
            def renderContext = new ServletRenderContext(webRequest)
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, '/authors')
            renderer.render(book.authors, renderContext)

        then: 'The resulting HAL is correct'
            response.contentAsString == toCompactXml('''
                <?xml version="1.0" encoding="UTF-8"?>
                <resource href="http://localhost/authors" hreflang="en">
                    <resource href="http://localhost/authors/2" hreflang="en">
                        <name>Stephen King</name>
                    </resource>
                    <resource href="http://localhost/authors/3" hreflang="en">
                        <name>King Stephen</name>
                    </resource>
                </resource>
            ''')
            response.contentType == GrailsWebUtil.getContentType(HalXmlRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
    }

    protected HalXmlRenderer getRenderer() {
        def renderer = new HalXmlRenderer(Book)
        renderer.mappingContext = mappingContext
        renderer.messageSource = new StaticMessageSource()
        renderer.linkGenerator = getLinkGenerator({
            '/books'(resources: 'book')
            '/authors'(resources: 'author')
        })
        return renderer
    }
}
