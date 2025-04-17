package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class InvokeTagLibWithBodyAsMethodTests extends Specification implements TagLibUnitTest<TestTagLib> {

    void testWithResultOfBody() {
        expect:
        applyTemplate('<g:testWithResultOfBody>foo</g:testWithResultOfBody>') == 'onetwofoothreefour'
    }

    void testInvokeWithBodyClosure() {
        expect:
        applyTemplate('<g:testInvokeWithBodyClosure />') == 'body=1body=2body=3'
    }

    void testWithClosureAndGStringReturn() {
        expect:
        applyTemplate('<g:testWithClosureAndGStringReturn />') == 'onetwobarthreefour'
    }

    void testWithClosureAndStringReturn() {
        expect:
        applyTemplate('<g:testWithClosureAndStringReturn />') == 'onetwofoothreefour'
    }

    void testInvokeTagLibAsMethodWithGString() {
        expect:
        applyTemplate('<g:testWithGStringBody />') == 'onetwobarthreefour'
    }

    void testInvokeTagLibAsMethodWithString() {
        expect:
        applyTemplate('<g:testWithStringBody />') == 'onetwofoothreefour'
    }

    void testInvokeTagLibAsMethodWithClosure() {
        expect:
        applyTemplate('<g:testWithClosureBody />') == 'onetwobigbodythreefour'
    }
}

@Artefact('TagLib')
class TestTagLib {
    Closure testInvokeWithBodyClosure = { attrs, body ->
        out << eachItem(items:[1,2,3]) { bodyAttrs ->
             out << "body=${bodyAttrs.var}"
        }
    }
    Closure eachItem = { attrs, body ->
        def items = attrs.items
        items.each { i ->
            out << body(var:i)
        }
    }
    Closure testWithClosureAndGStringReturn = { attrs, body ->
        def foo = "bar"
        out << "one" << test(foo:"bar") { "$foo" } << "four"
    }

    Closure testWithClosureAndStringReturn = { attrs, body ->

        out << "one" << test(foo:"bar") { "foo" } << "four"
    }

    Closure testWithGStringBody = { attrs, body ->
        def foo = "bar"
        out << "one" << test(foo:"bar", "$foo") << "four"
    }

    Closure testWithStringBody = { attrs, body ->
        out << "one" << test(foo:"bar", "foo") << "four"
    }

    Closure testWithResultOfBody= { attrs, body ->
        out << "one" << test(foo:"bar", body()) << "four"
    }

    Closure testWithClosureBody = { attrs, body ->
        out << "one" << test(foo:"bar") {
            out << "big" << "body"
        }
        out << "four"
    }
    Closure test = { attrs, body ->
        def value = body()
        out << "two" << value << "three"
    }
}

