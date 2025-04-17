package org.grails.web.pages

import org.grails.web.taglib.AbstractGrailsTagTests
import org.junit.jupiter.api.Test


/**
 * @author Marc Palmer (marc@anyware.co.uk)
 */
class GroovyPagesWhitespaceParsingTagTests extends AbstractGrailsTagTests {

    @Test
    void testTagWithTab() {
        // there is a tab (\t) between "if" and test
        def template = '<g:if\ttest="${2 > 1}">rejoice</g:if>'

        assertOutputEquals "rejoice", template
    }

    @Test
    void testTagWithSpace() {
        // there is a tab (\t) between "if" and test
        def template = '<g:if test="${2 > 1}">rejoice</g:if>'

        assertOutputEquals "rejoice", template
    }

    @Test
    void testTagWithNewline() {
        // there is a tab (\t) between "if" and test
        def template = """<g:if
test="${2 > 1}">rejoice</g:if>"""

        assertOutputEquals "rejoice", template
    }

    @Test
    void testTagWithSurroundingNewlines() {
        def template = """
<g:if test="${2 > 1}">rejoice</g:if>
"""

        assertOutputEquals "\nrejoice\n", template
}

    @Test
    void testTagWithSurroundingContent() {
        def template = """Hello
this is

<g:if test="${2 > 1}">testing</g:if>
whitespace handling


of tags in GSP"""

        assertOutputEquals "Hello\nthis is\n\ntesting\nwhitespace handling\n\n\nof tags in GSP", template
    }

    @Test
    void testTagWithSurroundingContentMultipleNewlines() {
        def template = """Hello
this is

<g:if test="${2 > 1}">testing</g:if>


whitespace handling


of tags in GSP"""

        assertOutputEquals "Hello\nthis is\n\ntesting\n\n\nwhitespace handling\n\n\nof tags in GSP", template
    }

    @Test
    void testConsecutiveTagInvocations() {
        def template = """Hello <g:if test="${2 > 1}">one</g:if> <g:if test="${2 > 1}">two</g:if><g:if test="${2 > 1}">three</g:if>"""

        assertOutputEquals "Hello one twothree", template
    }

    @Test
    void testConsecutiveTagInvocationsWithLineBreaks() {
        def template = """Hello <g:if test="${2 > 1}">one</g:if>
  <g:if test="${2 > 1}">two</g:if>
<g:if test="${2 > 1}">three</g:if>"""

        assertOutputEquals "Hello one\n  two\nthree", template
    }
}
