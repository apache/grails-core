package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 0.4
 */
class PageScopeTagTests extends Specification implements TagLibUnitTest<PageScopeTagLib> {

    void testScopes() {
        expect:
        applyTemplate('''
<g:set var="one" scope="request" value="two" />
<g:set var="two" scope="page" value="three" />
<g:set var="three" scope="session" value="four" />one: ${request.one} two: ${two} three: ${session.three}''').trim() == 'one: two two: three three: four'
    }
}

@Artefact('TagLib')
class PageScopeTagLib {
    Closure test1 = { attrs, body ->
        pageScope.bar = "foo"
        out << pageScope.foo
    }
}
