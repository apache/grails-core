package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 0.4
 */
class NamedTagBodyParamsTests extends Specification implements TagLibUnitTest<BodyParamTagLib> {

    void testNamedBodyParams() {
        expect:
        applyTemplate('<g:test1>foo: ${foo} one: ${one}</g:test1>') == 'foo: bar one: 2'
    }

    void testOverridingParam() {
        expect:
        applyTemplate('''
<g:set var="foo">Test</g:set>
<g:test1>foo: ${foo} one: ${one}</g:test1>
<% assert foo == 'Test' %>
''').trim() == 'foo: bar one: 2'
    }
}

@Artefact('TagLib')
class BodyParamTagLib {
    Closure test1 = { attrs, body ->
        out << body(foo:"bar", one:2)
    }
}

