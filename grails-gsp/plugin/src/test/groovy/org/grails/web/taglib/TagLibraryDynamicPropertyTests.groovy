package org.grails.web.taglib

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TagLibraryDynamicPropertyTests extends AbstractGrailsTagTests {

    @BeforeEach
    protected void onSetUp() {
        
        gcl.parseClass '''
import grails.gsp.*

@TagLib
class FooTagLib {
    Closure showAction = { attrs, body ->
        out << "action: ${actionName}"
    }
    Closure showController = { attrs, body ->
        out << "controller: ${controllerName}"
    }

    Closure showSession = { attrs, body ->
        out << "test: ${session.foo}"
    }
    Closure showParam = { attrs, body ->
        out << "test: ${params.foo}"
    }
}
'''
    }

    @Test
    void testDynamicProperties() {
        webRequest.actionName = "test"
        webRequest.controllerName = "foo"
        request.session.foo = "bar"
        webRequest.params.foo = "bar"
        
        def template = '<g:showAction />, <g:showController />, <g:showSession />, <g:showParam />'
        assertOutputEquals("action: test, controller: foo, test: bar, test: bar", template)
    }
}
