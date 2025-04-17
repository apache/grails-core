package org.grails.web.pages

import org.grails.web.taglib.AbstractGrailsTagTests
import org.junit.jupiter.api.Test

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class NewLineRenderingTests extends AbstractGrailsTagTests {

    @Test
    void testNewLinesBetweenExpressions() {
        def template = '''username: ${username}
password: ${password}'''

         assertOutputEquals('''username: bob
password: foo''', template, [username:'bob', password:'foo'])
    }
}
