package org.grails.web.mapping

import grails.artefact.Artefact
import grails.testing.spock.OnceBefore
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
class ReverseUrlMappingToDefaultActionTests extends Specification implements UrlMappingsUnitTest<ReverseUrlMappingToDefaultActionUrlMappings> {

    @OnceBefore
    void mockControllers() {
        mockController(ReverseUrlMappingContentController)
        mockController(ReverseUrlMappingTestController)
    }

    def testLinkTagRendering() {
        when:
        def template = '<g:link url="[controller:\'reverseUrlMappingContent\', params:[dir:\'about\'], id:\'index\']">click</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/about/index">click</a>'
    }
}

@Artefact("UrlMappings")
class ReverseUrlMappingToDefaultActionUrlMappings {
    static mappings = {
        "/$id?"{
            controller = "reverseUrlMappingContent"
            action = "view"
        }

        "/$dir/$id?"{
            controller = "reverseUrlMappingContent"
            action = "view"
        }
    }
}

@Artefact("Controller")
class ReverseUrlMappingContentController {
    def view() {}
}
@Artefact("Controller")
class ReverseUrlMappingTestController {
    def foo() {}
    def index() {}
}
