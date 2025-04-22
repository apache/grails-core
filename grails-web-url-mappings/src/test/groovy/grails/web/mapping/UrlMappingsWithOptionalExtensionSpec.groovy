/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.web.mapping
/**
 */
class UrlMappingsWithOptionalExtensionSpec extends AbstractUrlMappingsSpec {

    void "Test that URL mappings can be specified with an optional extension"() {
        given:"A URL mapping with an optional extension"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/book/list(.$format)"(controller:"book")
            }

        expect:"URLs with and without the format specified match"
            urlMappingsHolder.match('/book/list.xml')
            urlMappingsHolder.match('/book/list')
            urlMappingsHolder.match('/book/list').parameters.format == null
            urlMappingsHolder.match('/book/list.xml').parameters.format == 'xml'

    }

    void "Test that dynamic URL mappings can be specified with an optional parameter and an optional extension"() {
        given:"A URL mapping with an optional extension"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$controller/$action?(.$format)?"()
            }

        expect:"URLs with and without the format specified match"
            urlMappingsHolder.match('/book/list.xml')
            urlMappingsHolder.match('/book/list.xml').parameters.format == 'xml'
            urlMappingsHolder.match('/book')
            urlMappingsHolder.match('/book/list')
            urlMappingsHolder.match('/book/list').parameters.format == null
    }

    void "Test that dynamic URL mappings can be specified with a required parameter and an optional extension"() {
        given:"A URL mapping with an optional extension"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$controller/$action(.$format)?"()
            }
            
        expect:"URLs with and without the format specified match"
            urlMappingsHolder.match('/book/list.xml')
            urlMappingsHolder.match('/book/list.xml').parameters.format == 'xml'
            urlMappingsHolder.match('/book/list')
            urlMappingsHolder.match('/book/list').parameters.format == null
    }

    void "Test deep dynamic URL mappings can be specified with an optional extension"() {
        given:"A URL mapping with an optional extension"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$controller/$action?/$id?(.$format)?"()
            }

        expect:"URLs with and without the format specified match"
            urlMappingsHolder.match('/book/list.xml')
            urlMappingsHolder.match('/book/list.xml').parameters.format == 'xml'
            urlMappingsHolder.match('/book/list/1')
            urlMappingsHolder.match('/book/list/1.xml')
            urlMappingsHolder.match('/book/list/1.xml').parameters.format == 'xml'
            urlMappingsHolder.match('/book')
            urlMappingsHolder.match('/book/list')
            urlMappingsHolder.match('/book/list').parameters.format == null

    }

    void "Test that dynamic URL mappings generated correct links when specified with an optional extension"() {
        given:"A URL mapping with an optional extension"
            def linkGenerator = getLinkGenerator {
                "/$controller/$action?(.$format)?"()
            }

        expect:"URLs with and without the format specified generated the correct links"
            linkGenerator.link(controller:"book") == "http://localhost/book"
            linkGenerator.link(controller:"book", action:"list", params:[format:'xml']) == "http://localhost/book/list.xml"
            linkGenerator.link(controller:"book", action:"list") == "http://localhost/book/list"



    }

    void "Test that URL mappings with optional extensions generate the correct links"() {
        given:"A URL mapping with an optional extension"
            def linkGenerator = getLinkGenerator {
                "/book/list(.$format)?"(controller:"book")
            }

        expect:"URLs with and without the format specified match"
            linkGenerator.link(controller:"book", params:[format:'xml']) == "http://localhost/book/list.xml"
            linkGenerator.link(controller:"book") == "http://localhost/book/list"
    }
}
