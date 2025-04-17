package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

class LinkRenderingTagLib2Tests extends Specification implements UrlMappingsUnitTest<LinkRenderingTagLib2TestUrlMappings> {


    def testLinkWithOnlyId() {
        when:
        def template = '<g:link id="competition">Enter</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/competition">Enter</a>'
    }

    def testLinkWithOnlyIdAndAction() {
        when:
        def template = '<g:link id="competition" controller="content" action="view">Enter</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/competition">Enter</a>'
    }

}

@Artefact('UrlMappings')
class LinkRenderingTagLib2TestUrlMappings {
    static mappings = {
        "/$id?"{
            controller = "content"
            action = "view"
        }

        "/$dir/$id"{
            controller = "content"
            action = "view"
        }
    }
}
