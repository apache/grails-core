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

import com.fasterxml.jackson.databind.ObjectMapper
import grails.rest.render.Renderer
import grails.rest.render.hal.HalJsonRenderer
import grails.util.GrailsWebUtil
import org.grails.plugins.web.rest.render.*
import org.springframework.context.support.StaticMessageSource
import org.springframework.web.util.WebUtils
import spock.lang.Shared

/**
 * @author Graeme Rocher
 */
class HalDomainClassJsonRendererSpec extends BaseDomainClassRendererSpec {

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    void 'Test that the HAL renderer renders domain objects with appropriate links'() {
        given: 'A HAL renderer'
            def renderer = getRenderer() as HalJsonRenderer

        and: 'A Book domain object'
            def author = Author.create(2, 'Stephen King')
            def author2 = Author.create(3, 'King Stephen')
            def book = Book.create(1, 'The Stand', author)
            book.link(href: '/publisher', rel: 'The Publisher')
            book.authors.addAll(author, author2)

        when: 'The Book is rendered'
            def request = setupRequest('application/hal+json')
            def response = setupResponse(request)
            def renderContext = new ServletRenderContext(request)
            renderer.render(book, renderContext)

        then: 'The resulting HAL is correct'
            def expectedResponse = '''
                {
                    "_links": {
                        "self": {
                            "href": "http://localhost/books/1",
                            "hreflang": "en",
                            "type": "application/hal+json"
                        },
                        "The Publisher": {
                            "href": "/publisher",
                            "hreflang": "en"
                        },
                        "author": {
                            "href": "http://localhost/authors/2",
                            "hreflang": "en"
                        }
                    },
                    "title": "The Stand",
                    "_embedded": {
                        "authors": [
                            {
                                "_links": {
                                    "self": {
                                        "href": "http://localhost/authors/2",
                                        "hreflang": "en"
                                    }
                                },
                                "name": "Stephen King"
                            },
                            {
                                "_links": {
                                    "self": {
                                        "href": "http://localhost/authors/3",
                                        "hreflang": "en"
                                    }
                                },
                                "name": "King Stephen"
                            }
                        ],
                        "author": {
                            "_links": {
                                "self": {
                                    "href": "http://localhost/authors/2",
                                    "hreflang": "en"
                                }
                            },
                            "name": "Stephen King"
                        }
                    }
                }
            '''
            response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
            objectMapper.readTree(response.contentAsString) == objectMapper.readTree(expectedResponse)
    }

    void 'Test that the HAL renderer renders regular linkable groovy objects with appropriate links'() {
        given: 'A HAL renderer'
            def renderer = getRenderer()
            renderer.prettyPrint = false

        and: 'A regular linkable groovy Product object'
            def product = new Product(name: 'MacBook', category: new Category(name: 'laptop'))
            product.link(rel: 'company',href: 'https://apple.com', title: 'Made by Apple')

        when: 'The Product is rendered'
            def webRequest = setupRequest('application/hal+json')
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, '/product/Macbook')
            def response = setupResponse(webRequest)
            def renderContext = new ServletRenderContext(webRequest)
            renderer.render(product, renderContext)

        then: 'The resulting HAL is correct'
            def expectedResponse = '''
                {
                    "_links": {
                        "self": {
                            "href": "http://localhost/product/Macbook",
                            "hreflang": "en",
                            "type": "application/hal+json"
                        },
                        "company": {
                            "href": "https://apple.com",
                            "hreflang": "en",
                            "title": "Made by Apple"
                        }
                    },
                    "category": {
                        "name": "laptop"
                    },
                    "name": "MacBook"
                }
            '''
            response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
            objectMapper.readTree(response.contentAsString) == objectMapper.readTree(expectedResponse)
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

        when: 'The Author collection of the Book is rendered'
            def webRequest = setupRequest('application/hal+json')
            webRequest.request.setAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, '/authors')
            def response = setupResponse(webRequest)
            def renderContext = new ServletRenderContext(webRequest)
            renderer.render(book.authors, renderContext)

        then: 'The resulting HAL is correct'
            def expectedContent = '''
                {
                    "_links": {
                        "self": {
                            "href": "http://localhost/authors",
                            "hreflang": "en",
                            "type": "application/hal+json"
                        }
                    },
                    "_embedded": [
                        {
                            "_links": {
                                "self": {
                                    "href": "http://localhost/authors/2",
                                    "hreflang": "en",
                                    "type": "application/hal+json"
                                }
                            },
                            "name": "Stephen King"
                        },
                        {
                            "_links": {
                                "self": {
                                    "href": "http://localhost/authors/3",
                                    "hreflang": "en",
                                    "type": "application/hal+json"
                                }
                            },
                            "name": "King Stephen"
                        }
                    ]
                }
            '''
            objectMapper.readTree(response.contentAsString) == objectMapper.readTree(expectedContent)
            response.contentType == GrailsWebUtil.getContentType(HalJsonRenderer.MIME_TYPE.name, GrailsWebUtil.DEFAULT_ENCODING)
    }

    Renderer getRenderer() {
        def renderer = new HalJsonRenderer(Book)
        renderer.mappingContext = mappingContext
        renderer.messageSource = new StaticMessageSource()
        renderer.linkGenerator = getLinkGenerator({
            '/books'(resources: 'book')
            '/authors'(resources: 'author')
        })
        return renderer
    }
}

