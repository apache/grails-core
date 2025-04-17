package org.grails.web.pages

import org.grails.taglib.GrailsTagException
import org.grails.web.errors.GrailsExceptionResolver
import org.grails.web.taglib.AbstractGrailsTagTests
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TagLibWithGStringTests extends AbstractGrailsTagTests {

    @BeforeEach
    protected void onSetUp() {
        
        gcl.parseClass('''
class GroovyStringTagLib {

   static namespace = 'jeff'

   Closure doit = { attrs ->
       out << "some foo ${fooo}"
   }
}
''')
    }

    @Test
    void testMissingPropertyGString() {
        def template = '<jeff:doit />'

        try {
            applyTemplate(template)
        }
        catch (GrailsTagException e) {
            def cause = GrailsExceptionResolver.getRootCause(e)
            assertTrue cause instanceof MissingPropertyException, "The cause should have been a MPE but was ${cause}"
            assertEquals 1,e.lineNumber
        }
    }
}
