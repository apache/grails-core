package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Shared
import spock.lang.Specification

class LayoutWriterStackTests extends Specification implements TagLibUnitTest<TwoColumnTagLib> {

    @Shared def template = """
    <g:twoColumn>
        <g:left>leftContent</g:left>
        <g:right>rightContent</g:right>
        bodyContent
    </g:twoColumn>"""

    void testLayoutTag() {
        when:
        String result = applyTemplate(template)

        then:
        assertEqualsIgnoreWhiteSpace("""
        <div class='twoColumn'>
            left: <div class='left'>leftContent</div>,
            right: <div class='right'>rightContent</div>,
            body: bodyContent
        </div>""",
                result)
    }

    void testNestedLayoutTags() {
        given:
        def nested = template.replaceAll("leftContent", template)
        String result = applyTemplate(nested)

        expect:
        assertEqualsIgnoreWhiteSpace("""
        <div class='twoColumn'>
            left: <div class='left'>
                <div class='twoColumn'>
                    left: <div class='left'>leftContent</div>,
                    right: <div class='right'>rightContent</div>,
                    body: bodyContent
                </div>
            </div>,
            right: <div class='right'>rightContent</div>,
            body: bodyContent</div>""",
                result)
    }

    boolean assertEqualsIgnoreWhiteSpace(String s1, String s2) {
        s1.replaceAll(/\s/, '') == s2.replaceAll(/\s/, '')
    }
}

@Artefact("TagLib")
class TwoColumnTagLib {

    Closure twoColumn = {attrs, body ->
        def parts = LayoutWriterStack.writeParts(body)
        out << "<div class='twoColumn'>left: " << parts.left << ", right: " << parts.right << ", body: " << parts.body << "</div>"
    }
    Closure left = {attrs, body ->
        def w = LayoutWriterStack.currentWriter('left')
        w << "<div class='left'>" << body() << "</div>"
    }

    Closure right = {attrs, body ->
        def w = LayoutWriterStack.currentWriter('right')
        w << "<div class='right'>" << body() << "</div>"
    }
}
