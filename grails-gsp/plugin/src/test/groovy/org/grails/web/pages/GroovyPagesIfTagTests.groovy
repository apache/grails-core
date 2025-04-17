package org.grails.web.pages

import org.grails.web.taglib.AbstractGrailsTagTests
import org.junit.jupiter.api.Test

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyPagesIfTagTests extends AbstractGrailsTagTests {

    @Test
    void testGreaterThan() {
        def template = '<g:if test="${2 > 1}">rechoice</g:if>'
        assertOutputEquals "rechoice", template
    }

    @Test
    void testComplexNestedGreaterThan() {
        def template = '<g:if test="${[1, 2, 3, 4].sum() { it * 2 } - [2, 3, 4, 5].sum() { (0..it).sum() { it * 2 } } > 0}">hello</g:if><g:else>goodbye</g:else>'
        printCompiledSource template

        assertCompiledSourceContains "if([1, 2, 3, 4].sum() { it * 2 } - [2, 3, 4, 5].sum() { (0..it).sum() { it * 2 } } > 0) {", template
        assertOutputEquals "goodbye", template
    }
}
