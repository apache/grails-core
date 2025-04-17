package org.grails.web.pages

import org.grails.web.taglib.AbstractGrailsTagTests
import org.junit.jupiter.api.Test

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ReservedWordOverrideTests extends AbstractGrailsTagTests{

    @Test
    void testCannotOverrideReservedWords() {
        assertOutputNotContains "bad", '${pageScope}', [pageScope:"bad"]
    }
}