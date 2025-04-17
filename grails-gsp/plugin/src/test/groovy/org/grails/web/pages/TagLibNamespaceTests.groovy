package org.grails.web.pages

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Ignore
import spock.lang.Specification

@Ignore // ignored until new release of testing framework
class TagLibNamespaceTests extends Specification implements TagLibUnitTest<TestTagLib> {

    void testInvokeNamespacedTag() {
        expect:
        applyTemplate('<t1:foo />') == 'bar'
    }

    void testInvokeNestedNamespacedTag() {
        expect:
        applyTemplate('<t1:nested name="hello"><t1:foo /></t1:nested>') == "<hello>barbar</hello>"
    }

    void testDynamicDispatch() {
        expect:
        applyTemplate('<t1:condition><%println t1."${\'nested\'}"(name:\'hello\')%></t1:condition>') == ''
    }
}

@Artefact('TagLib')
class TestTagLib {
    static namespace = "t1"

    Closure condition = { attrs, body -> }

    Closure foo = { attrs, body ->
        out << "bar"
        out << body?.call()
    }

    Closure nested = { attrs, body ->
       out << "<${attrs.name}>"
        out << foo()
        out << body()
       out << "</${attrs.name}>"
    }
}
