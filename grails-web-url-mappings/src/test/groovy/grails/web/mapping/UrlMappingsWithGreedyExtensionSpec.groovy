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

    void "Test that greedy id with optional action matches controller-only URL with format"() {
        given: "A complex URL mapping with optional action and optional greedy id"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$controller/$action?/$id+?(.$format)?"()
            }

        when: "Matching a URL with just controller and format (no action, no id)"
            def info = urlMappingsHolder.match('/mobile.json')

        then: "The controller and format are correctly extracted without greedy behavior affecting controller"
            info != null
            info.parameters.controller == 'mobile'
            info.parameters.action == null
            info.parameters.id == null
            info.parameters.format == 'json'
    }

    void "Test that greedy id works correctly when all parameters are present"() {
        given: "A complex URL mapping with optional action and optional greedy id"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$controller/$action?/$id+?(.$format)?"()
            }

        when: "Matching a URL with all parameters including id with dots"
            def info = urlMappingsHolder.match('/user/show/bob.smith.json')

        then: "The greedy id captures everything up to the last dot"
            info != null
            info.parameters.controller == 'user'
            info.parameters.action == 'show'
            info.parameters.id == 'bob.smith'
            info.parameters.format == 'json'
    }

    void "Test that greedy id does not affect controller when only controller and action are present"() {
        given: "A complex URL mapping with optional action and optional greedy id"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$controller/$action?/$id+?(.$format)?"()
            }

        when: "Matching a URL with controller and action only"
            def info = urlMappingsHolder.match('/user/show.json')

        then: "The controller and action are correctly extracted with format"
            info != null
            info.parameters.controller == 'user'
            info.parameters.action == 'show'
            info.parameters.id == null
            info.parameters.format == 'json'
    }

    void "Test that controller with dots is handled correctly without greedy id"() {
        given: "A complex URL mapping with optional action and optional greedy id"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$controller/$action?/$id+?(.$format)?"()
            }

        when: "Matching a URL with controller containing dots and format"
            def info = urlMappingsHolder.match('/test.test.json')

        then: "The URL /test.test.json should NOT apply greedy to controller"
            info != null
            println "DEBUG: controller=${info.parameters.controller}, action=${info.parameters.action}, id=${info.parameters.id}, format=${info.parameters.format}"
            // The greedy + modifier only applies to $id, not $controller
            // So /test.test.json should be parsed using non-greedy behavior:
            // controller=test, format=test.json
            info.parameters.controller == 'test'
            info.parameters.action == null
            info.parameters.id == null
            info.parameters.format == 'test.json'
    }

    void "Test greedy id with no explicit format - what happens with bob.smith"() {
        given: "A complex URL mapping with optional action and optional greedy id"
            def urlMappingsHolder = getUrlMappingsHolder {
                "/$controller/$action?/$id+?(.$format)?"()
            }

        when: "Matching a URL where the id contains a dot but there's no real format"
            def info = urlMappingsHolder.match('/user/show/bob.smith')

        then: "Check what actually happens - does .smith become the format?"
            info != null
            println "DEBUG bob.smith: controller=${info.parameters.controller}, action=${info.parameters.action}, id=${info.parameters.id}, format=${info.parameters.format}"
            // With greedy matching, the last dot is treated as format separator
            // So bob.smith becomes id=bob, format=smith
            // This is the expected (though perhaps surprising) behavior of greedy matching
            info.parameters.controller == 'user'
            info.parameters.action == 'show'
            info.parameters.id == 'bob'
            info.parameters.format == 'smith'
    }

    void "Test non-greedy vs greedy behavior with bob.smith"() {
        given: "Both greedy and non-greedy mappings"
            def greedyHolder = getUrlMappingsHolder {
                "/$controller/$action/$id+(.$format)?"()
            }
            def nonGreedyHolder = getUrlMappingsHolder {
                "/$controller/$action/$id(.$format)?"()
            }

        when: "Matching the same URL"
            def greedyInfo = greedyHolder.match('/user/show/bob.smith')
            def nonGreedyInfo = nonGreedyHolder.match('/user/show/bob.smith')

        then: "Greedy splits at last dot, non-greedy at first dot"
            println "GREEDY:     id=${greedyInfo.parameters.id}, format=${greedyInfo.parameters.format}"
            println "NON-GREEDY: id=${nonGreedyInfo.parameters.id}, format=${nonGreedyInfo.parameters.format}"

            // Greedy: splits at LAST dot
            greedyInfo.parameters.id == 'bob'
            greedyInfo.parameters.format == 'smith'

            // Non-greedy: splits at FIRST dot
            nonGreedyInfo.parameters.id == 'bob'
            nonGreedyInfo.parameters.format == 'smith'
            // Wait - they're the same for single dot! Let's check with multiple dots
    }

    void "Test non-greedy vs greedy with multiple dots in id"() {
        given: "Both greedy and non-greedy mappings"
            def greedyHolder = getUrlMappingsHolder {
                "/$controller/$action/$id+(.$format)?"()
            }
            def nonGreedyHolder = getUrlMappingsHolder {
                "/$controller/$action/$id(.$format)?"()
            }

        when: "Matching URL with multiple dots"
            def greedyInfo = greedyHolder.match('/user/show/bob.smith.jones')
            def nonGreedyInfo = nonGreedyHolder.match('/user/show/bob.smith.jones')

        then: "Greedy splits at last dot, non-greedy at first dot"
            println "GREEDY (bob.smith.jones):     id=${greedyInfo.parameters.id}, format=${greedyInfo.parameters.format}"
            println "NON-GREEDY (bob.smith.jones): id=${nonGreedyInfo.parameters.id}, format=${nonGreedyInfo.parameters.format}"

            // Greedy: splits at LAST dot - id gets bob.smith, format gets jones
            greedyInfo.parameters.id == 'bob.smith'
            greedyInfo.parameters.format == 'jones'

            // Non-greedy: splits at FIRST dot - id gets bob, format gets smith.jones
            nonGreedyInfo.parameters.id == 'bob'
            nonGreedyInfo.parameters.format == 'smith.jones'
    }

    void "Test format constraint to reject invalid formats"() {
        given: "A mapping with format constraint to only allow known formats"
            def holder = getUrlMappingsHolder {
                "/$controller/$action/$id+(.$format)?" {
                    constraints {
                        format(inList: ['json', 'xml', 'html', 'csv'])
                    }
                }
            }

        when: "Matching URL where last segment is not a valid format"
            def infoWithInvalidFormat = holder.match('/user/show/bob.smith')
            def infoWithValidFormat = holder.match('/user/show/bob.smith.json')

        then: "Invalid format should not match, valid format should work"
            println "bob.smith (invalid format): ${infoWithInvalidFormat}"
            println "bob.smith.json (valid format): id=${infoWithValidFormat?.parameters?.id}, format=${infoWithValidFormat?.parameters?.format}"

            // With constraint, 'smith' is not a valid format so it should fail to match
            infoWithInvalidFormat == null

            // With valid format extension, it works
            infoWithValidFormat != null
            infoWithValidFormat.parameters.id == 'bob.smith'
            infoWithValidFormat.parameters.format == 'json'
    }

    void "Test two mappings - greedy with format constraint plus fallback"() {
        given: "Two mappings - greedy with format constraint first, plain fallback second"
            def holder = getUrlMappingsHolder {
                // First: greedy with format constraint for valid formats
                "/$controller/$action/$id+(.$format)?" {
                    constraints {
                        format(inList: ['json', 'xml', 'html', 'csv'])
                    }
                }
                // Fallback: captures id with dots when no valid format
                "/$controller/$action/$id"()
            }

        when: "Testing various URLs"
            def bobSmith = holder.match('/user/show/bob.smith')
            def bobSmithJson = holder.match('/user/show/bob.smith.json')
            def bobSmithJones = holder.match('/user/show/bob.smith.jones')
            def bobSmithJonesXml = holder.match('/user/show/bob.smith.jones.xml')

        then: "URLs with valid formats use greedy, others use fallback"
            println "bob.smith:           id=${bobSmith?.parameters?.id}, format=${bobSmith?.parameters?.format}"
            println "bob.smith.json:      id=${bobSmithJson?.parameters?.id}, format=${bobSmithJson?.parameters?.format}"
            println "bob.smith.jones:     id=${bobSmithJones?.parameters?.id}, format=${bobSmithJones?.parameters?.format}"
            println "bob.smith.jones.xml: id=${bobSmithJonesXml?.parameters?.id}, format=${bobSmithJonesXml?.parameters?.format}"

            // bob.smith - 'smith' is not valid format, fallback captures whole id
            bobSmith != null
            bobSmith.parameters.id == 'bob.smith'
            bobSmith.parameters.format == null

            // bob.smith.json - 'json' is valid, greedy captures bob.smith as id
            bobSmithJson != null
            bobSmithJson.parameters.id == 'bob.smith'
            bobSmithJson.parameters.format == 'json'

            // bob.smith.jones - 'jones' is not valid, fallback captures whole id
            bobSmithJones != null
            bobSmithJones.parameters.id == 'bob.smith.jones'
            bobSmithJones.parameters.format == null

            // bob.smith.jones.xml - 'xml' is valid, greedy captures bob.smith.jones as id
            bobSmithJonesXml != null
            bobSmithJonesXml.parameters.id == 'bob.smith.jones'
            bobSmithJonesXml.parameters.format == 'xml'
    }
}
