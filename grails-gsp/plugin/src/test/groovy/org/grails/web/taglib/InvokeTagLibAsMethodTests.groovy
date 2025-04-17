package org.grails.web.taglib

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InvokeTagLibAsMethodTests extends AbstractGrailsTagTests {

    @BeforeEach
    void onSetUp() {
        
        gcl.parseClass('''
import grails.gsp.*

@TagLib
class TestTagLib {
    Closure testTypeConversion = { attrs ->
        out << "Number Is: ${attrs.int('number')}"
    }
}
''')
    }

    @Test
    void testTypeConvertersWhenTagIsInvokedAsMethod() {
        // test for GRAILS-5484
        def template = '${g.testTypeConversion(number: "42")}'
        assertOutputEquals 'Number Is: 42', template
    }
}