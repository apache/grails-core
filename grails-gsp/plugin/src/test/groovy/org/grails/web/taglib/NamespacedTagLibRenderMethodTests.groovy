package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import org.grails.core.io.MockStringResourceLoader
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
class NamespacedTagLibRenderMethodTests extends Specification implements TagLibUnitTest<WithNamespaceTagLib> {


    def setupSpec() {
        mockTagLibs(NormalTagLib, WithNamespaceTagLib)
    }
    def testInvokeNamespacedTagLib() {
        when:
        def resourceLoader = new MockStringResourceLoader()
        resourceLoader.registerMockResource("/bug1/_t1n.gsp", '''START TAG1|${ns1.tag2()}|STOP TAG1''')
        resourceLoader.registerMockResource("/bug1/_t2n.gsp", 'START TAG2|STOP TAG2')
        grailsApplication.getMainContext().getBean("groovyPagesTemplateEngine").resourceLoader = resourceLoader
        webRequest.controllerName = "foo"

        def template = '''<pre>START|${ns1.tag1()}|STOP</pre>'''
        String output = applyTemplate(template)

        then:
        output == '<pre>START|START TAG1|START TAG2|STOP TAG2|STOP TAG1|STOP</pre>'
    }

    def testInvokeNormalTagLib() {
        when:
        def resourceLoader = new MockStringResourceLoader()
        resourceLoader.registerMockResource("/bug1/_t1.gsp", 'START TAG1|${tag2()}|STOP TAG1')
        resourceLoader.registerMockResource("/bug1/_t2.gsp", 'START TAG2|STOP TAG2')
        grailsApplication.getMainContext().getBean("groovyPagesTemplateEngine").resourceLoader = resourceLoader
        webRequest.controllerName = "foo"

        def template = '''<pre>START|${tag1()}|STOP</pre>'''
        String output = applyTemplate(template)

        then:
        output == '<pre>START|START TAG1|START TAG2|STOP TAG2|STOP TAG1|STOP</pre>'
    }
}


@Artefact('TagLib')
class WithNamespaceTagLib {

    static namespace = "ns1"

    Closure tag1 = { attrs, body ->
        out << render(template: "/bug1/t1n")
    }
    Closure tag2 = { attrs, body ->
        out << render(template: "/bug1/t2n")
    }

}

@Artefact('TagLib')
class NormalTagLib {

    Closure tag1 = { attrs, body ->
        out << render(template: "/bug1/t1")
    }
    Closure tag2 = { attrs, body ->
        out << render(template: "/bug1/t2")
    }

}