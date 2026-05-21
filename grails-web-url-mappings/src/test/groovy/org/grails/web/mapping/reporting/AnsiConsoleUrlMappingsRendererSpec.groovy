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

package org.grails.web.mapping.reporting

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import grails.web.mapping.UrlMappingsHolder
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class AnsiConsoleUrlMappingsRendererSpec extends Specification {

    void "Test render URL mappings for 3 level resource"() {
        given: "A URL mappings renderer"
        def sw = new ByteArrayOutputStream()
        def ps = new PrintStream(sw)
        def renderer = new AnsiConsoleUrlMappingsRenderer(ps)
        renderer.isAnsiEnabled = false
        def urlMappingsHolder = getUrlMappingsHolder {
            "/books"(resources: 'book') {
                '/authors'(resources: 'author') {
                    '/publisher'(resource: 'publisher')
                }
            }
        }
        when: "The URL mappings are rendered"
        renderer.render(urlMappingsHolder.urlMappings.toList())
        println sw.toString()
        then: "The output is correct"
        sw.toString() == '''Controller: author
 |   GET    | /books/${bookId}/authors/create                            | Action: create           |
 |   GET    | /books/${bookId}/authors/${id}/edit                        | Action: edit             |
 |   POST   | /books/${bookId}/authors                                   | Action: save             |
 |   GET    | /books/${bookId}/authors                                   | Action: index            |
 |  DELETE  | /books/${bookId}/authors/${id}                             | Action: delete           |
 |  PATCH   | /books/${bookId}/authors/${id}                             | Action: patch            |
 |   PUT    | /books/${bookId}/authors/${id}                             | Action: update           |
 |   GET    | /books/${bookId}/authors/${id}                             | Action: show             |

Controller: book
 |   GET    | /books/create                                              | Action: create           |
 |   GET    | /books/${id}/edit                                          | Action: edit             |
 |   POST   | /books                                                     | Action: save             |
 |   GET    | /books                                                     | Action: index            |
 |  DELETE  | /books/${id}                                               | Action: delete           |
 |  PATCH   | /books/${id}                                               | Action: patch            |
 |   PUT    | /books/${id}                                               | Action: update           |
 |   GET    | /books/${id}                                               | Action: show             |

Controller: publisher
 |   GET    | /books/${bookId}/authors/${authorId}/publisher/edit        | Action: edit             |
 |   GET    | /books/${bookId}/authors/${authorId}/publisher/create      | Action: create           |
 |  DELETE  | /books/${bookId}/authors/${authorId}/publisher             | Action: delete           |
 |  PATCH   | /books/${bookId}/authors/${authorId}/publisher             | Action: patch            |
 |   PUT    | /books/${bookId}/authors/${authorId}/publisher             | Action: update           |
 |   GET    | /books/${bookId}/authors/${authorId}/publisher             | Action: show             |
 |   POST   | /books/${bookId}/authors/${authorId}/publisher             | Action: save             |

'''.denormalize()
    }

    void "Test render URL mappings to target stream"() {
        given: "A URL mappings renderer"
        def sw = new ByteArrayOutputStream()
        def ps = new PrintStream(sw)
        def renderer = new AnsiConsoleUrlMappingsRenderer(ps)
        renderer.isAnsiEnabled = false
        def urlMappingsHolder = getUrlMappingsHolder {
            "/$controller/$action?/$id?(.$format)?"()
            "/images/$name**.jpg"(controller: "image")
            "/foo"(resources: "foo")
            "500"(controller: "errors")
            "/"(view: "/index")

        }

        when: "The URL mappings are rendered"
        renderer.render(urlMappingsHolder.urlMappings.toList())
        println sw.toString()
        then: "The output is correct"
        sw.toString() == '''Dynamic Mappings
 |    *     | /                                                 | View:   /index           |
 |    *     | /${controller}/${action}?/${id}?(.${format)?      | Action: (default action) |

Controller: errors
 |    *     | ERROR: 500                                        | Action: (default action) |

Controller: foo
 |   GET    | /foo/create                                       | Action: create           |
 |   GET    | /foo/${id}/edit                                   | Action: edit             |
 |   POST   | /foo                                              | Action: save             |
 |   GET    | /foo                                              | Action: index            |
 |  DELETE  | /foo/${id}                                        | Action: delete           |
 |  PATCH   | /foo/${id}                                        | Action: patch            |
 |   PUT    | /foo/${id}                                        | Action: update           |
 |   GET    | /foo/${id}                                        | Action: show             |

Controller: image
 |    *     | /images/${name}**.jpg                             | Action: (default action) |

'''.denormalize()
    }

    UrlMappingsHolder getUrlMappingsHolder(Closure mappings) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        def allMappings = evaluator.evaluateMappings mappings
        return new DefaultUrlMappingsHolder(allMappings)
    }
}
