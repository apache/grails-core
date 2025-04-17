package org.grails.web.taglib

import grails.testing.web.UrlMappingsUnitTest
import spock.lang.Specification

/**
 * Some more tests for the behaviour of reverse linking from mappings.
 *
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 0.6
 */
class OverlappingReverseMappedLinkTests extends Specification implements UrlMappingsUnitTest<OverLappingReverseMappedLinkUrlMappings> {

    def testSimpleLink() {
        when:
        def template = '<g:link controller="author" action="list">link1</g:link>'
        def expected = '<a href="/authors">link1</a>'
        String output = applyTemplate(template)

        then:
        output == expected
    }

    def testLinkWithPaginationParams() {
        when:
        def template = '<g:link controller="author" action="list" params="[max:10,offset:20]">link1</g:link>'
        def expected = '<a href="/authors?max=10&amp;offset=20">link1</a>'
        String output = applyTemplate(template)

        then:
        output == expected
    }
}


class OverLappingReverseMappedLinkUrlMappings {
    static mappings = {
        "/authors" {
            controller = "author"
            action = "list"
        }

        "/content/$controller/$action?/$id?"{
            constraints {
                // apply constraints here
            }
        }
    }
}
