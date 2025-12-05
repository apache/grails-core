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
package grails.web.mapping

/**
 * Tests for greedy extension parameter matching using the + marker.
 * The + marker causes the parameter before an optional extension to match greedily,
 * splitting at the LAST dot instead of the first dot.
 */
class UrlMappingsWithGreedyExtensionSpec extends AbstractUrlMappingsSpec {

    void "Test that required greedy parameter splits at last dot"() {
        given: "A URL mapping with required greedy parameter"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }

        when: "Matching a URL with multiple dots"
            def info = urlMappingsHolder.match('/test.test.json')

        then: "The id captures everything up to the last dot"
            info != null
            info.parameters.id == 'test.test'
            info.parameters.format == 'json'
    }

    void "Test that required greedy parameter works with single dot"() {
        given: "A URL mapping with required greedy parameter"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }

        when: "Matching a URL with single dot"
            def info = urlMappingsHolder.match('/test.json')

        then: "The id captures up to the dot"
            info != null
            info.parameters.id == 'test'
            info.parameters.format == 'json'
    }

    void "Test that required greedy parameter works without any dots"() {
        given: "A URL mapping with required greedy parameter"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }

        when: "Matching a URL without any dots"
            def info = urlMappingsHolder.match('/simpletest')

        then: "The id captures the entire value with no format"
            info != null
            info.parameters.id == 'simpletest'
            info.parameters.format == null
    }

    void "Test that required greedy parameter works without explicit extension marker"() {
        given: "A URL mapping with required greedy parameter"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }

        when: "Matching a URL like /test.test (ambiguous - could be id with extension or id without)"
            def info = urlMappingsHolder.match('/test.test')

        then: "The greedy pattern treats last dot as extension separator"
            info != null
            info.parameters.id == 'test'
            info.parameters.format == 'test'
    }

    void "Test that required greedy parameter works with many dots"() {
        given: "A URL mapping with required greedy parameter"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }

        when: "Matching a URL with many dots"
            def info = urlMappingsHolder.match('/foo.bar.baz.xml')

        then: "The id captures everything up to the last dot"
            info != null
            info.parameters.id == 'foo.bar.baz'
            info.parameters.format == 'xml'
    }

    void "Test that optional greedy parameter splits at last dot"() {
        given: "A URL mapping with optional greedy parameter"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$id+?(.$format)?"(controller: "user", action: "profile")
            }

        when: "Matching a URL with multiple dots"
            def info = urlMappingsHolder.match('/test.test.json')

        then: "The id captures everything up to the last dot"
            info != null
            info.parameters.id == 'test.test'
            info.parameters.format == 'json'
    }

    void "Test that optional greedy parameter allows missing id"() {
        given: "A URL mapping with optional greedy parameter"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$id+?(.$format)?"(controller: "user", action: "profile")
            }

        when: "Matching a URL without id"
            def info = urlMappingsHolder.match('/')

        then: "The mapping matches with null id"
            info != null
            info.parameters.id == null
            info.parameters.format == null
    }

    void "Test that greedy parameter differs from non-greedy behavior"() {
        given: "Two URL mappings - greedy and non-greedy"
            def greedyHolder = getUrlMappingsHolder {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }
            def nonGreedyHolder = getUrlMappingsHolder {
                "/$id(.$format)?"(controller: "user", action: "profile")
            }

        when: "Matching the same URL with both"
            def greedyInfo = greedyHolder.match('/test.test.json')
            def nonGreedyInfo = nonGreedyHolder.match('/test.test.json')

        then: "Greedy splits at last dot, non-greedy at first dot"
            greedyInfo.parameters.id == 'test.test'
            greedyInfo.parameters.format == 'json'

            nonGreedyInfo.parameters.id == 'test'
            nonGreedyInfo.parameters.format == 'test.json'
    }

    void "Test that greedy parameter works in complex mappings"() {
        given: "A URL mapping with controller, action, and greedy id"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$controller/$action/$id+(.$format)?"()
            }

        when: "Matching a complex URL"
            def info = urlMappingsHolder.match('/user/show/test.test.json')

        then: "All parameters are correctly extracted"
            info != null
            info.parameters.controller == 'user'
            info.parameters.action == 'show'
            info.parameters.id == 'test.test'
            info.parameters.format == 'json'
    }

    void "Test that greedy links are generated correctly with format"() {
        given: "A link generator with greedy parameter mapping"
            def linkGenerator = getLinkGenerator {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }

        when: "Generating a link with id containing dots and format"
            def link = linkGenerator.link(controller: "user", action: "profile",
                                         id: "test.test", params: [format: 'json'])

        then: "The link is correctly formatted"
            link == "http://localhost/test.test.json"
    }

    void "Test that greedy links are generated correctly without format"() {
        given: "A link generator with greedy parameter mapping"
            def linkGenerator = getLinkGenerator {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }

        when: "Generating a link with id containing dots but no format"
            def link = linkGenerator.link(controller: "user", action: "profile",
                                         id: "test.test")

        then: "The link is correctly formatted without extension"
            link == "http://localhost/test.test"
    }

    void "Test that greedy parameter works with different file extensions"() {
        given: "A URL mapping with required greedy parameter"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }

        expect: "Various file extensions are correctly parsed"
            urlMappingsHolder.match('/file.name.pdf').parameters.id == 'file.name'
            urlMappingsHolder.match('/file.name.pdf').parameters.format == 'pdf'

            urlMappingsHolder.match('/file.name.html').parameters.id == 'file.name'
            urlMappingsHolder.match('/file.name.html').parameters.format == 'html'

            urlMappingsHolder.match('/file.name.csv').parameters.id == 'file.name'
            urlMappingsHolder.match('/file.name.csv').parameters.format == 'csv'
    }

    void "Test that UrlMappingData reports greedy extension correctly"() {
        given: "A URL mapping with greedy parameter"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$id+(.$format)?"(controller: "user", action: "profile")
            }

        when: "Matching a URL"
            def info = urlMappingsHolder.match('/test.test.json')

        then: "The UrlMappingData indicates greedy extension parameter"
            info != null
            info.urlData.hasGreedyExtensionParam()
            info.urlData.hasOptionalExtension()
    }
}
