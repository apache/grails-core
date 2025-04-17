package org.grails.web.taglib

import grails.testing.web.GrailsWebUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 0.4
 */
class PageScopeSpec extends Specification implements GrailsWebUnitTest {

    void 'test referring to non existent page scope property does not throw MissingPropertyException'() {
        expect:
        applyTemplate("<%= pageScope.nonExistent ?: 'No Property Found' %>") == 'No Property Found'
    }
    
    void 'test page scope'() {

        expect:
        applyTemplate ('''\
<g:set var="one" scope="request" value="two" />\
<g:set var="two" scope="page" value="three" />\
<g:set var="three" scope="session" value="four" />\
one: ${request.one} two: ${two} three: ${session.three}\
''') == 'one: two two: three three: four'
    }
}
