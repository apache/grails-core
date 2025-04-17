package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.taglib.TagLibUnitTest
import org.grails.core.artefact.gsp.TagLibArtefactHandler
import org.grails.gsp.GroovyPageTemplate
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.web.pages.GSPResponseWriter
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 0.4
 */
class NamespacedTagLibMethodTests extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    def setupSpec() {
        mockTagLibs(HasErrorTagLib,SecondOneTagLib,MyeTagLib,AlternateTagLib)
    }

    def testInvokeTagLibNoNamespace() {
        when:
        def templateString = '<%= link(controller:"hello") { "good" } %>'
        GroovyPagesTemplateEngine engine = grailsApplication.getMainContext().getBean("groovyPagesTemplateEngine")
        GroovyPageTemplate template = engine.createTemplate(templateString,"test_" + System.currentTimeMillis())
        template.allowSettingContentType = true
        def w = template.make()
        MockHttpServletResponse mockResponse = new MockHttpServletResponse()
        mockResponse.setCharacterEncoding("UTF-8")
        GSPResponseWriter writer = GSPResponseWriter.getInstance(mockResponse)
        webRequest.out = writer
        w.writeTo(writer)
        writer.flush()
        String output = mockResponse.contentAsString


        then:
        output == '<a href="/hello">good</a>'

    }

    def testStringAsBodyDispatch() {
        when:
        def template = '<g:tag bean="${foo}"/>'
        String output = applyTemplate(template, [foo: new NSTestBean()])

        then:
        output == 'errors'
    }

    def testInvokeTagNoArguments() {
        when:
        def template = '<two:hello/>'
        def taglib = grailsApplication.getArtefact(TagLibArtefactHandler.TYPE,"SecondTagLib")

        String output = applyTemplate(template)

        then:
        output == '<a href="/foo/bar">hello</a>'
        output == '<a href="/foo/bar">hello</a>'
    }

    def testInvokeTagWithNamespace() {
        when:
        def template = '<mye:test1>foo: <mye:test2 foo="bar1" /> one: ${mye.test2(foo:"bar2")} two: ${mye.test2()}</mye:test1>'
        String output = applyTemplate(template)
        then:
        output == 'foo: hello! bar1 one: hello! bar2 two: hello! null'
    }

    void testInvokeTagWithNamespaceFromTagLib() {
        when:
        def template = '<mye:test1>foo: <two:test1 /> </mye:test1>'
        String output = applyTemplate(template)

        then:
        output == 'foo: hello! bar3 '
    }

    void testInvokeTagWithNonExistantNamespace() {
        when:
        def template = '''<foaf:Person a="b" c="d">foo</foaf:Person>'''
        String output = applyTemplate(template)

        then:
        // we don't have a 'foaf' namespace, so the output should be equal to template itself
        output == template

        when:
        // test with nested 'unknown' tags
        template = '''<foaf:Person a="b" c="d"><foaf:Nested e="f" g="h">Something here.</foaf:Nested></foaf:Person>'''
        output = applyTemplate(template)

        then:
        output == template
    }

    void testInvokeDefaultNamespaceFromNamespacedTag() {
        mockTagLib(AlternateTagLib)
        when:
        def template = '''<alt:showme />'''
        String output = applyTemplate(template)

        then:
        output == "/test/foo"

        when:
        template = '''<alt:showmeToo />'''
        output = applyTemplate(template)

        then:
        output == "/test/foo"

        when:
        template = '''<alt:showmeThree />'''
        output = applyTemplate(template)

        then:
        output == "hello! bar"
    }


    class NSTestBean {
        def errors = [hasErrors: { true }, hasFieldErrors: { String name -> true }] // mock errors object
        def hasErrors() { true }
    }

}

@Artefact('TagLib')
class MyeTagLib {
    static namespace = "mye"
    Closure test1 = { attrs, body ->
        out << body(foo:"bar", one:2)
    }

    Closure test2 = { attrs, body ->
        out << "hello! ${attrs.foo}"
    }
}

@Artefact('TagLib')
class HasErrorTagLib {
    Closure tag = { attr, body ->
        out << hasErrors('bean': attr.bean, field: 'bar', 'errors')
    }
    // by declaring a tag called my we can test if the namespace of the tag is available via property access
    Closure mye = { attrs, body ->

    }
}

@Artefact('TagLib')
class SecondOneTagLib {
    static namespace = "two"

    Closure test1 = { attrs, body ->

        out << mye.test2(foo:"bar3")
    }

    Closure hello = { attrs ->
        out << g.link(controller:'foo', action:'bar') { "hello" }
    }

}

@Artefact('TagLib')
class AlternateTagLib {
    static namespace = "alt"

    Closure showme = { attrs, body ->
        out << createLink(controller:'test',action:'foo')
    }

    Closure showmeToo = { attrs, body ->
        out << g.createLink(controller:'test',action:'foo')
    }

    Closure showmeThree = { attrs, body ->
        out << mye.test2(foo:"bar")
    }
}


