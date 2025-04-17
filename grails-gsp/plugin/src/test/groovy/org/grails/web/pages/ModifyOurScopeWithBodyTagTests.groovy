package org.grails.web.pages

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class ModifyOurScopeWithBodyTagTests extends Specification implements TagLibUnitTest<OutScopeTagLib> {

    // test for GRAILS-5847
    void testModifyOuterScopeInTag() {
        expect:
        // test with no body arguments
        applyTemplate('<g:set var="counter" value="${1}"/><g:threeTimes>${counter++}</g:threeTimes>') == '123'
        applyTemplate('<g:set var="counter" value="${1}"/><g:threeTimes var="x">${counter++}</g:threeTimes>') ==  '123'
    }

    // test for GRAILS-2675
    void testRestoreOuterVariableNamesWithBodyArguments() {
        expect:
        applyTemplate('<g:set var="counter" value="${9}"/><g:threeTimes var="counter">${counter++}</g:threeTimes>${counter}') == '0129'
        applyTemplate('<g:set var="counter" value="${1}"/><g:threeTimes var="counter">${counter}</g:threeTimes>${counter}') == '0121'
    }

    // test for GRAILS-7306
    void testRestoreOuterVariableNamesWithBodyArgumentsEvenIfOuterValueIsNull() {
        expect:
        applyTemplate('''<g:set var="foo" value="parentFooVal"/><g:set var="bar" value="${null}"/><g:local vars="[foo:'innerFooVal', bar:'nonNullVal']" someValue="nonNull" var="counter">inner foo: ${foo}, inner bar: ${bar}</g:local> outer foo: ${foo}, outer bar: ${bar}''') == 'inner foo: innerFooVal, inner bar: nonNullVal outer foo: parentFooVal, outer bar: '
    }

    void testBodyIt() {
        expect:
        applyTemplate('''<g:set var="it" value=" world"/><g:ittest>${it}</g:ittest>${it}''') == 'hello world'
    }

    // test for GRAILS-8554
    void testNestedScope() {
        expect:
        applyTemplate('''<g:nestedouter><g:nestedinner>${test1} ${test2}</g:nestedinner></g:nestedouter>''') == '1 2'
    }

    // test for GRAILS-8569
    void testGSetInBody() {
        expect:
        applyTemplate('''<g:bodytag><g:set var="a" value="1"/></g:bodytag><g:bodytag model="[c:3]"><g:set var="b" value="2"/></g:bodytag>${a} ${b} ${c}''') == '1 2 '
    }
}

@Artefact('TagLib')
class OutScopeTagLib {
    Closure threeTimes = { attrs, body ->
        3.times {
            if (attrs.var)
                out << body((attrs.var):it)
            else
                out << body()
        }
    }
    Closure local = { attrs, body ->
        out << body(attrs.vars)
    }

    Closure ittest = { attrs, body ->
        out << body('hello')
    }

    Closure nestedouter = { attrs, body ->
        out << body(test1:1)
    }

    Closure nestedinner = { attrs, body ->
        out << body(test2:2)
    }

    Closure bodytag = { attrs, body ->
        if (attrs.model)
            out << body(attrs.model)
        else
            out << body()
    }
}

