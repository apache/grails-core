package org.grails.web.mapping

import grails.artefact.Artefact
import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
class RestfulReverseUrlRenderingTests extends Specification implements UrlMappingsUnitTest<RestfulReverseUrlMappings> {


    def testLinkTagRendering() {
        when:
        def template = '<g:link controller="restfulBook">create</g:link>'
        String output = applyTemplate(template)

        then:
        output == '<a href="/book">create</a>'
    }

    def testFormTagRendering() {
        when:
        def template = '<g:form controller="restfulBook" name="myForm" method="POST">save</g:form>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/book" method="post" name="myForm" id="myForm" >save</form>'
    }


    def testFormTagRenderGETRequest() {
        when:
        def template = '<g:form controller="restfulBook" name="myForm" method="GET">create</g:form>'
        String output = applyTemplate(template)

        then:
        output == '<form action="/book" method="get" name="myForm" id="myForm" >create</form>'
    }
}

@Artefact("UrlMappings")
class RestfulReverseUrlMappings {
    static mappings = {
        "/book" (controller: "restfulBook", action: [GET: "create", POST: "save"])
    }
}

@Artefact("Controller")
class RestfulBookController {
    def create = {}
    def save = {}
}
