package org.grails.web.taglib

import grails.testing.web.taglib.TagLibUnitTest
import grails.util.GrailsUtil
import grails.util.Holders
import grails.util.Metadata
import grails.util.MockRequestDataValueProcessor
import groovy.test.NotYetImplemented
import org.grails.gsp.GroovyPageBinding
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.plugins.web.taglib.FormTagLib
import org.grails.taglib.GrailsTagException
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import spock.lang.Specification

import jakarta.servlet.http.Cookie

class ApplicationTagLibTests extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    def setup() {
        tagLib.requestDataValueProcessor = new MockRequestDataValueProcessor()
    }

    @Override
    Set<String> getIncludePlugins() {
        ['controllers'] as Set
    }

    void testResourceTagWithPluginAttribute() {
        given:
        unRegisterRequestDataValueProcessor()
        request.contextPath = '/test'
        def template = '${resource(file:"images/foo.jpg", plugin:"controllers")}'
        def result = applyTemplate(template)

        expect:
        result == "/test/plugins/controllers-${GrailsUtil.getGrailsVersion()}/static/images/foo.jpg"
    }

    void testResourceTagWithPluginAttributeAndRequestDataValueProcessor() {
        given:
        request.contextPath = '/test'
        def template = '${resource(file:"images/foo.jpg", plugin:"controllers")}'
        def result = applyTemplate(template)

        expect:
        result == "/test/plugins/controllers-${GrailsUtil.getGrailsVersion()}/static/images/foo.jpg?requestDataValueProcessorParamName=paramValue"
    }


    @NotYetImplemented
    void testResourceTagWithImplicitPlugin() {
        given:
        unRegisterRequestDataValueProcessor()
        def template = '${resource(file:"images/foo.jpg")}'
        request.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, new GroovyPageBinding("/plugin/one"))

        def result = applyTemplate(template)

        expect:
        result ==  "/plugin/one/images/foo.jpg"
    }

    @NotYetImplemented
    void testResourceTagWithImplicitPluginAndRequestDataValueProcessor() {

        def template = '${resource(file:"images/foo.jpg")}'

        request.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, new GroovyPageBinding("/plugin/one"))
        assertOutputEquals "/plugin/one/images/foo.jpg?requestDataValueProcessorParamName=paramValue", template
    }

    @NotYetImplemented
    void testResourceTagWithContextPathAttribute() {
        unRegisterRequestDataValueProcessor()
        request.contextPath = '/test'
        def template = '${resource(file:"images/foo.jpg", contextPath:"/foo")}'
        assertOutputEquals "/foo/images/foo.jpg", template

        request.setAttribute(GrailsApplicationAttributes.PAGE_SCOPE, new GroovyPageBinding("/plugin/one"))
        assertOutputEquals "/foo/images/foo.jpg", template
    }

    void testResourceTagWithContextPathAttributeAndRequestDataValueProcessor() {
        when:
        request.contextPath = '/test'
        def template = '${resource(file:"images/foo.jpg", contextPath:"/foo")}'
        def result = applyTemplate(template)

        then:
        result == "/foo/static/images/foo.jpg?requestDataValueProcessorParamName=paramValue"
    }

    void testResourceTagWithPluginAttributeAndNone() {
        when:
        unRegisterRequestDataValueProcessor()
        request.contextPath = '/test'
        def template = '${resource(file:"images/foo.jpg", plugin:"none")}'

        then:
        applyTemplate(template) == "/test/static/images/foo.jpg"
    }

    void testResourceTagWithPluginAttributeAndNoneAndRequestDataValueProcessor() {
        given:
        request.contextPath = '/test'

        expect:
        applyTemplate('${resource(file:"images/foo.jpg", plugin:"none")}') ==
            "/test/static/images/foo.jpg?requestDataValueProcessorParamName=paramValue"
    }

    void testResourceTag() {
        given:
        unRegisterRequestDataValueProcessor()
        request.contextPath = '/test'
        when:
        def template = '${resource(file:"images/foo.jpg")}'
        def result = applyTemplate(template)

        then:
        result == '/test/static/images/foo.jpg'

        when:
        result = applyTemplate('${resource(dir:"images",file:"foo.jpg")}')

        then:
        result == '/test/static/images/foo.jpg'
    }

    void testResourceTagAndRequestDataValueProcessor() {
        given:
        request.contextPath = '/test'

        expect:
        applyTemplate( '${resource(file:"images/foo.jpg")}')  ==
                '/test/static/images/foo.jpg?requestDataValueProcessorParamName=paramValue'

        applyTemplate('${resource(dir:"images",file:"foo.jpg")}') ==
            '/test/static/images/foo.jpg?requestDataValueProcessorParamName=paramValue'
    }

    void testResourceTagDirOnly() {
        given:
        unRegisterRequestDataValueProcessor()
        request.contextPath = '/test'

        expect:
        applyTemplate('${resource(dir:"jquery")}') ==
            '/test/static/jquery'
    }

    void testResourceTagDirOnlyAndRequestDataValueProcessor() {
        given:
        request.contextPath = '/test'

        expect:
        applyTemplate('${resource(dir:"jquery")}') ==
            '/test/static/jquery?requestDataValueProcessorParamName=paramValue'
    }

    @NotYetImplemented
    void testUseJessionIdWithCreateLink() {
        given:
        unRegisterRequestDataValueProcessor()
        def response = new JsessionIdMockHttpServletResponse()
        ApplicationTagLib.metaClass.getResponse = {-> response}
        ga.config.grails.views.enable.jsessionid=true
        tagLibBean.afterPropertiesSet()
        assertTrue(tagLibBean.@useJsessionId)

        def template = '<g:createLink controller="foo" action="test" />'
        assertOutputEquals "/foo/test;jsessionid=test", template

        ga.config.grails.views.enable.jsessionid=false
        ga.config['grails.views.enable.jsessionid']=false
        tagLibBean.afterPropertiesSet()
        assertFalse(tagLibBean.@useJsessionId)
        assertOutputEquals "/foo/test", template
    }

    @NotYetImplemented
    void testUseJessionIdWithCreateLinkAndRequestDataValueProcessor() {

        def response = new JsessionIdMockHttpServletResponse()
        ApplicationTagLib.metaClass.getResponse = {-> response}
        def tagLibBean = appCtx.getBean(ApplicationTagLib.name)
        ga.config.grails.views.enable.jsessionid=true
        tagLibBean.afterPropertiesSet()
        assertTrue(tagLibBean.@useJsessionId)

        def template = '<g:createLink controller="foo" action="test" />'
        assertOutputEquals "/foo/test?requestDataValueProcessorParamName=paramValue;jsessionid=test", template

        ga.config.grails.views.enable.jsessionid=false
        ga.config['grails.views.enable.jsessionid']=false
        tagLibBean.afterPropertiesSet()
        assertFalse(tagLibBean.@useJsessionId)
        assertOutputEquals "/foo/test?requestDataValueProcessorParamName=paramValue", template
    }

    void testObtainCookieValue() {
        given:
        def cookie = new Cookie("foo", "bar")
        request.cookies = [cookie] as Cookie[]

        expect:
        applyTemplate('<g:cookie name="foo" />') == 'bar'
        applyTemplate('${cookie(name:"foo")}') == 'bar'
    }

    void testObtainHeaderValue() {
        given:
        request.addHeader "FOO", "BAR"

        expect:
        applyTemplate('<g:header name="FOO" />') == 'BAR'
        applyTemplate('${header(name:"FOO")}') == 'BAR'
    }

    void testClonedUrlFromVariable() {
        given:
        unRegisterRequestDataValueProcessor()

        expect:
        applyTemplate( '''<g:set var="urlMap" value="${[controller: 'test', action: 'justdoit']}"/>${urlMap.controller},${urlMap.action}<g:link url="${urlMap}">test</g:link>${urlMap.controller},${urlMap.action}''' ) ==
            'test,justdoit<a href="/test/test/justdoit">test</a>test,justdoit'
    }

    void testLinkWithMultipleParameters() {
        given:
        unRegisterRequestDataValueProcessor()

        def template = '<g:link controller="foo" action="action" params="[test: \'test\', test2: \'test2\']">test</g:link>'
        expect:
        applyTemplate(template) ==
            '<a href="/test/foo/action?test=test&amp;test2=test2">test</a>'
        // test caching too
        applyTemplate(template) ==
                '<a href="/test/foo/action?test=test&amp;test2=test2">test</a>'

    }

    void testLinkWithMultipleParametersAndRequestDataValueProcessor() {
        given:
        def template = '<g:link controller="foo" action="action" params="[test: \'test\', test2: \'test2\']">test</g:link>'

        expect:
        applyTemplate(template) == '<a href="/test/foo/action?test=test&amp;test2=test2&amp;requestDataValueProcessorParamName=paramValue">test</a>'
        // test caching too
        applyTemplate(template) == '<a href="/test/foo/action?test=test&amp;test2=test2&amp;requestDataValueProcessorParamName=paramValue">test</a>'
    }

    void testLinkWithMultipleCollectionParameters() {
        given:
        unRegisterRequestDataValueProcessor()
        def template = '<g:link controller="foo" action="action" params="[test: [\'test-a\',\'test-b\'], test2: [\'test2-a\',\'test2-b\'] as String[]]">test</g:link>'

        expect:
        applyTemplate(template) ==
            '<a href="/test/foo/action?test=test-a&amp;test=test-b&amp;test2=test2-a&amp;test2=test2-b">test</a>'
        // test caching too
        applyTemplate(template) ==
                '<a href="/test/foo/action?test=test-a&amp;test=test-b&amp;test2=test2-a&amp;test2=test2-b">test</a>'
    }

    void testLinkWithMultipleCollectionParametersAndRequestDataValueProcessor() {
        def template = '<g:link controller="foo" action="action" params="[test: [\'test-a\',\'test-b\'], test2: [\'test2-a\',\'test2-b\'] as String[]]">test</g:link>'
        assertOutputEquals('<a href="/foo/action?test=test-a&amp;test=test-b&amp;test2=test2-a&amp;test2=test2-b&amp;requestDataValueProcessorParamName=paramValue">test</a>', template)
        // test caching too
        assertOutputEquals('<a href="/foo/action?test=test-a&amp;test=test-b&amp;test2=test2-a&amp;test2=test2-b&amp;requestDataValueProcessorParamName=paramValue">test</a>', template)
    }

    void testLinkWithCollectionParameter() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:link controller="foo" action="action" params="[test: [\'test-a\']]">test</g:link>'
        assertOutputEquals('<a href="/foo/action?test=test-a">test</a>', template)
        // test caching too
        assertOutputEquals('<a href="/foo/action?test=test-a">test</a>', template)
    }

    void testLinkWithCollectionParameterAndRequestDataValueProcessor() {
        def template = '<g:link controller="foo" action="action" params="[test: [\'test-a\']]">test</g:link>'
        assertOutputEquals('<a href="/foo/action?test=test-a&amp;requestDataValueProcessorParamName=paramValue">test</a>', template)
        // test caching too
        assertOutputEquals('<a href="/foo/action?test=test-a&amp;requestDataValueProcessorParamName=paramValue">test</a>', template)
    }

    void testLinkWithCharCollectionParameter() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:link controller="foo" action="action" params="[letter: [\'a\' as char]]">test</g:link>'
        assertOutputEquals('<a href="/foo/action?letter=a">test</a>', template)
        // test caching too
        assertOutputEquals('<a href="/foo/action?letter=a">test</a>', template)
    }
    void testLinkWithCharCollectionParameterAndRequestDataValueProcessor() {
        def template = '<g:link controller="foo" action="action" params="[letter: [\'a\' as char]]">test</g:link>'
        assertOutputEquals('<a href="/foo/action?letter=a&amp;requestDataValueProcessorParamName=paramValue">test</a>', template)
        // test caching too
        assertOutputEquals('<a href="/foo/action?letter=a&amp;requestDataValueProcessorParamName=paramValue">test</a>', template)
    }

    void testCreateLinkWithCollectionParameter() {
        unRegisterRequestDataValueProcessor()
        def template = '<% l=\'a\' %>${g.createLink(controller:"foo", action:"action", params:[letter:[l]])} ${g.createLink(controller:"foo", action:"action", params:[letter:[l]])}'
        assertOutputEquals('/foo/action?letter=a /foo/action?letter=a', template)
        // test caching too
        assertOutputEquals('/foo/action?letter=a /foo/action?letter=a', template)
    }

    void testCreateLinkWithCollectionParameterAndRequestDataValueProcessor() {
        def template = '<% l=\'a\' %>${g.createLink(controller:"foo", action:"action", params:[letter:[l]])} ${g.createLink(controller:"foo", action:"action", params:[letter:[l]])}'
        assertOutputEquals('/foo/action?letter=a&amp;requestDataValueProcessorParamName=paramValue /foo/action?letter=a&amp;requestDataValueProcessorParamName=paramValue', template)
        // test caching too
        assertOutputEquals('/foo/action?letter=a&amp;requestDataValueProcessorParamName=paramValue /foo/action?letter=a&amp;requestDataValueProcessorParamName=paramValue', template)
    }

    void testLikeWithElementId() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:link elementId="myId" controller="foo" action="list">bar</g:link>'
        assertOutputEquals('<a href="/foo/list" id="myId">bar</a>', template)
    }

    void testLikeWithElementIdAndRequestDataValueProcessor() {
        def template = '<g:link elementId="myId" controller="foo" action="list">bar</g:link>'
        assertOutputEquals('<a href="/foo/list?requestDataValueProcessorParamName=paramValue" id="myId">bar</a>', template)
    }

    void testLinkWithMultipleParametersAndElementId() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:link elementId="myid" controller="foo" action="action" params="[test: \'test\', test2: \'test2\']">test</g:link>'
        assertOutputEquals('<a href="/foo/action?test=test&amp;test2=test2" id="myid">test</a>', template)
    }

    void testLinkWithMultipleParametersAndElementIdAndRequestDataValueProcessor() {
        def template = '<g:link elementId="myid" controller="foo" action="action" params="[test: \'test\', test2: \'test2\']">test</g:link>'
        assertOutputEquals('<a href="/foo/action?test=test&amp;test2=test2&amp;requestDataValueProcessorParamName=paramValue" id="myid">test</a>', template)
    }

    void testLinkWithFragment() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:link controller="foo" action="bar" fragment="test">link</g:link>'
        profile("link rendering") {
            assertOutputEquals('<a href="/foo/bar#test">link</a>', template)
        }
    }

   void testLinkWithFragmentAndRequestDataValueProcessor() {
        def template = '<g:link controller="foo" action="bar" fragment="test">link</g:link>'
        profile("link rendering") {
            assertOutputEquals('<a href="/foo/bar?requestDataValueProcessorParamName=paramValue#test">link</a>', template)
        }
    }

    void testCreateLinkWithFlowExecutionKeyAndEvent() {
        unRegisterRequestDataValueProcessor()
      
        def template = '<g:createLink controller="foo" action="bar" event="boo" />'
        assertOutputEquals('/foo/bar?_eventId=boo', template)
    }

    void testCreateLinkWithFlowExecutionKeyAndEventAndRequestDataValueProcessor() {

        def template = '<g:createLink controller="foo" action="bar" event="boo" />'
        assertOutputEquals('/foo/bar?_eventId=boo&requestDataValueProcessorParamName=paramValue', template)
    }

    void testLinkWithFlowExecutionKeyAndEvent() {
        unRegisterRequestDataValueProcessor()

        def template = '<g:link controller="foo" action="bar" event="boo" >link</g:link>'
        assertOutputEquals('<a href="/foo/bar?_eventId=boo">link</a>', template)
    }

    void testLinkWithFlowExecutionKeyAndEventAndRequestDataValueProcessor() {
        def template = '<g:link controller="foo" action="bar" event="boo" >link</g:link>'
        assertOutputEquals('<a href="/foo/bar?_eventId=boo&amp;requestDataValueProcessorParamName=paramValue">link</a>', template)
    }

    void testSetTag() {
        def template = '<g:set var="one" value="two" />one: ${one}'
        assertOutputEquals('one: two', template)
    }

    void testSetTagWithBody() {
        def template = '<g:set var="one">two</g:set>one: ${one}'
        assertOutputEquals('one: two', template)
    }

    void testSetTagWithMap() {
        def template = '<g:set var="e" value="${c.a}"/>${e?.b}'
        assertOutputEquals('', template, [c:[:]])
        assertOutputEquals('foo', template, [c:[a:[b:'foo']]])
    }

    void testSetTagWithScope() {
        def template = '<g:set var="var1" value="1"/>${var1}<g:set var="var1" value="2"/> ${var1}<g:set var="var2" value="3" scope="request"/> ${var2}<g:set var="var2" value="4" scope="request"/> ${var2}'
        assertOutputEquals('1 2 3 4', template)
    }

    void testSetTagWithBeanName() {
        def template = '<g:set bean="grailsApplication" var="myVar"/>${myVar.initialised}'
        assertOutputEquals('true', template)

        template = '<g:set bean="grailsApplication" var="myRequestVar" scope="request"/>${request.myRequestVar.initialised}'
        assertOutputEquals('true', template)
    }

    void testSetTagWithBeanType() {
        def template = '<%@ page import="grails.core.*" %><g:set bean="${GrailsApplication}" var="myRequestVar" scope="request"/>${request.myRequestVar.initialised}'
        assertOutputEquals('true', template)
    }

    void testIteration() {
        def template = '''<g:set var="counter" value="${1}" />
<g:each in="${[10,11,12]}" var="myVal"><g:set var="counter" value="${myVal+counter}" />${counter}</g:each>'''

        printCompiledSource template
        assertOutputEquals('112234', template, [:], { it.toString().trim() })
    }

    void testMetaTag() {
        Metadata.getInstance(new ByteArrayInputStream("""
app:
    version: 0.9.9.1
""".bytes))
        def template = '<g:meta name="app.version"/>'
        assertOutputEquals('0.9.9.1', template)
    }

    void testCreateLinkToWithDirAndLeadingSlash() {
        unRegisterRequestDataValueProcessor()
        request.contextPath = "/"
        def template = '<g:createLinkTo dir="/images" file="foo.jpg" />'
        assertOutputEquals "/images/foo.jpg", template
    }

    void testCreateLinkToWithDirAndLeadingSlashAndRequestDataValueProcessor() {
        request.contextPath = "/"
        def template = '<g:createLinkTo dir="/images" file="foo.jpg" />'
        assertOutputEquals "/images/foo.jpg?requestDataValueProcessorParamName=paramValue", template
    }

    void testCreateLinkToWithDirAndLeadingNoLeadingSlash() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:createLinkTo dir="images" file="foo.jpg" />'
        assertOutputEquals "/images/foo.jpg", template
    }

    void testCreateLinkToWithDirAndLeadingNoLeadingSlashAndRequestDataValueProcessor() {
        def template = '<g:createLinkTo dir="images" file="foo.jpg" />'
        assertOutputEquals "/images/foo.jpg?requestDataValueProcessorParamName=paramValue", template
    }

    void testCreateLinkToWithFileAndLeadingSlash() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:createLinkTo dir="/images" file="/foo.jpg" />'
        assertOutputEquals "/images/foo.jpg", template
    }

    void testCreateLinkToWithFileAndLeadingSlashAndRequestDataValueProcessor() {
        def template = '<g:createLinkTo dir="/images" file="/foo.jpg" />'
        assertOutputEquals "/images/foo.jpg?requestDataValueProcessorParamName=paramValue", template
    }

    void testCreateLinkTo() {

        unRegisterRequestDataValueProcessor()

        def template = '<g:resource dir="test" />'
        assertOutputEquals '/test', template

        template = '<g:resource dir="test" file="file" />'
        assertOutputEquals '/test/file', template

        template = '<g:resource dir="" />'
        assertOutputEquals '', template
    }

    void testCreateLinkToWithRequestDataValueProcessor() {
        def template = '<g:resource dir="test" />'
        assertOutputEquals '/test?requestDataValueProcessorParamName=paramValue', template

        template = '<g:resource dir="test" file="file" />'
        assertOutputEquals '/test/file?requestDataValueProcessorParamName=paramValue', template

        template = '<g:resource dir="" />'
        assertOutputEquals '', template
    }

    void testCreateLinkToFilesInRoot() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:resource dir="/" file="test.gsp" />'
        assertOutputEquals '/test.gsp', template
    }

    void testCreateLinkToFilesInRootWithRequestDataValueProcessor() {
        def template = '<g:resource dir="/" file="test.gsp" />'
        assertOutputEquals '/test.gsp?requestDataValueProcessorParamName=paramValue', template
    }

    void testResourceFilesInRootWithContext() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:resource dir="/" file="test.gsp" />'
        request.contextPath = "/foo"
        assertOutputEquals '/foo/test.gsp', template
    }

    void testResourceFilesInRootWithContextAndRequestDataValueProcessor() {
        def template = '<g:resource dir="/" file="test.gsp" />'
        request.contextPath = "/foo"
        assertOutputEquals '/foo/test.gsp?requestDataValueProcessorParamName=paramValue', template
    }

    void testCreateLinkWithZeroId() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:createLink action="testAction" controller="testController" id="${id}"  />'
        assertOutputEquals '/testController/testAction/0', template, [id:0]
    }

    void testCreateLinkWithZeroIdAndRequestDataValueProcessor() {
        def template = '<g:createLink action="testAction" controller="testController" id="${id}"  />'
        assertOutputEquals '/testController/testAction/0?requestDataValueProcessorParamName=paramValue', template, [id:0]
    }

    void testCreateLinkURLEncoding() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:createLink action="testAction" controller="testController" params="[name:\'Marc Palmer\']"  />'
        assertOutputEquals '/testController/testAction?name=Marc+Palmer', template
    }

    void testCreateLinkURLEncodingWithRequestDataValueProcessor() {
        def template = '<g:createLink action="testAction" controller="testController" params="[name:\'Marc Palmer\']"  />'
        assertOutputEquals '/testController/testAction?name=Marc+Palmer&requestDataValueProcessorParamName=paramValue', template
    }

    void testCreateLinkURLEncodingWithHTMLChars() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:createLink action="testAction" controller="testController" params="[email:email]" />'
        assertOutputEquals '/testController/testAction?email=%3Cmarc%40anyware.co.uk%3E', template, [email:'<marc@anyware.co.uk>']
    }

    void testCreateLinkURLEncodingWithHTMLCharsAndRequestDataValueProcessor() {
        def template = '<g:createLink action="testAction" controller="testController" params="[email:email]" />'
        assertOutputEquals '/testController/testAction?email=%3Cmarc%40anyware.co.uk%3E&requestDataValueProcessorParamName=paramValue', template, [email:'<marc@anyware.co.uk>']
    }

    void testCreateLinkWithBase() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:createLink base="http://www128.myhost.com:3495" action="testAction" controller="testController" />'
        assertOutputEquals 'http://www128.myhost.com:3495/testController/testAction', template
    }

    void testCreateLinkWithBaseAndRequestDataValueProcessor() {
        def template = '<g:createLink base="http://www128.myhost.com:3495" action="testAction" controller="testController" />'
        assertOutputEquals 'http://www128.myhost.com:3495/testController/testAction?requestDataValueProcessorParamName=paramValue', template
    }

//    void testCreateLinkWithUseJSessionIdAndContextPath() {
//        unRegisterRequestDataValueProcessor()
//        request.contextPath = "/foo"
//        def taglib = appCtx.getBean(ApplicationTagLib.name)
//
//        taglib.useJsessionId = true
//        def template = '<g:createLink action="testAction" controller="testController" />'
//        assertOutputEquals '/foo/testController/testAction', template
//    }

//    void testCreateLinkWithUseJSessionIdAndContextPathAndRequestDataValueProcessor() {
//        request.contextPath = "/foo"
//        def taglib = appCtx.getBean(ApplicationTagLib.name)
//
//        taglib.useJsessionId = true
//        def template = '<g:createLink action="testAction" controller="testController" />'
//        assertOutputEquals '/foo/testController/testAction?requestDataValueProcessorParamName=paramValue', template
//    }

    void testCreateLinkWithContextPath() {
        unRegisterRequestDataValueProcessor()
        request.contextPath = "/foo"
        def template = '<g:createLink action="testAction" controller="testController" />'
        assertOutputEquals '/foo/testController/testAction', template
    }

    void testCreateLinkWithContextPathAndRequestDataValueProcessor() {
        request.contextPath = "/foo"
        def template = '<g:createLink action="testAction" controller="testController" />'
        assertOutputEquals '/foo/testController/testAction?requestDataValueProcessorParamName=paramValue', template
    }

    void testAbsoluteWithContextPath() {
        unRegisterRequestDataValueProcessor()
        request.contextPath = "/foo"
        def template = '<g:createLink action="testAction" controller="testController" absolute="true" />'

        def linkGenerator = appCtx.getBean("grailsLinkGenerator")
        linkGenerator.configuredServerBaseURL="http://www.mysite.com"
        assertOutputEquals 'http://www.mysite.com/testController/testAction', template

        template = '<g:createLink action="testAction" controller="testController" absolute="${true}" />'

        linkGenerator = appCtx.getBean("grailsLinkGenerator")
        linkGenerator.configuredServerBaseURL="http://www.mysite.com"
        assertOutputEquals 'http://www.mysite.com/testController/testAction', template
    }

    void testAbsoluteWithContextPathAndRequestDataValueProcessor() {
        request.contextPath = "/foo"
        def template = '<g:createLink action="testAction" controller="testController" absolute="true" />'

        def linkGenerator = appCtx.getBean("grailsLinkGenerator")
        linkGenerator.configuredServerBaseURL="http://www.mysite.com"
        assertOutputEquals 'http://www.mysite.com/testController/testAction?requestDataValueProcessorParamName=paramValue', template

        template = '<g:createLink action="testAction" controller="testController" absolute="${true}" />'

        linkGenerator = appCtx.getBean("grailsLinkGenerator")
        linkGenerator.configuredServerBaseURL="http://www.mysite.com"
        assertOutputEquals 'http://www.mysite.com/testController/testAction?requestDataValueProcessorParamName=paramValue', template
    }

    void testAbsoluteFalseWithContextPath() {
        unRegisterRequestDataValueProcessor()
        request.contextPath = "/foo"
        def template = '<g:createLink action="testAction" controller="testController" absolute="false" />'

        def linkGenerator = appCtx.getBean("grailsLinkGenerator")
        linkGenerator.configuredServerBaseURL="http://www.mysite.com"
        assertOutputEquals '/foo/testController/testAction', template

        template = '<g:createLink action="testAction" controller="testController" absolute="${false}" />'

        linkGenerator = appCtx.getBean("grailsLinkGenerator")
        linkGenerator.configuredServerBaseURL="http://www.mysite.com"
        assertOutputEquals '/foo/testController/testAction', template
    }

    void testAbsoluteFalseWithContextPathAndRequestDataValueProcessor() {
        request.contextPath = "/foo"
        def template = '<g:createLink action="testAction" controller="testController" absolute="false" />'

        def linkGenerator = appCtx.getBean("grailsLinkGenerator")
        linkGenerator.configuredServerBaseURL="http://www.mysite.com"
        assertOutputEquals '/foo/testController/testAction?requestDataValueProcessorParamName=paramValue', template

        template = '<g:createLink action="testAction" controller="testController" absolute="${false}" />'

        linkGenerator = appCtx.getBean("grailsLinkGenerator")
        linkGenerator.configuredServerBaseURL="http://www.mysite.com"
        assertOutputEquals '/foo/testController/testAction?requestDataValueProcessorParamName=paramValue', template
    }

    void testAbsoluteWithContextPathAndNullConfig() {
        unRegisterRequestDataValueProcessor()
        Holders.config = null
        request.contextPath = "/foo"
        request.serverPort = 8080
        def template = '<g:createLink action="testAction" controller="testController" absolute="true" />'
        assertOutputEquals 'http://localhost:8080/foo/testController/testAction', template

        template = '<g:createLink action="testAction" controller="testController" absolute="${true}" />'
        assertOutputEquals 'http://localhost:8080/foo/testController/testAction', template
    }

    void testAbsoluteWithContextPathAndNullConfigAndRequestDataValueProcessor() {
        Holders.config = null
        request.contextPath = "/foo"
        request.serverPort = 8080
        def template = '<g:createLink action="testAction" controller="testController" absolute="true" />'
        assertOutputEquals 'http://localhost:8080/foo/testController/testAction?requestDataValueProcessorParamName=paramValue', template

        template = '<g:createLink action="testAction" controller="testController" absolute="${true}" />'
        assertOutputEquals 'http://localhost:8080/foo/testController/testAction?requestDataValueProcessorParamName=paramValue', template
    }

    void testAbsoluteFalseWithContextPathAndNullConfig() {
        unRegisterRequestDataValueProcessor()
        Holders.config = null
        request.contextPath = "/foo"
        request.serverPort = 8080
        def template = '<g:createLink action="testAction" controller="testController" absolute="false" />'
        assertOutputEquals '/foo/testController/testAction', template

        template = '<g:createLink action="testAction" controller="testController" absolute="${false}" />'
        assertOutputEquals '/foo/testController/testAction', template
    }

    void testAbsoluteFalseWithContextPathAndNullConfigAndRequestDataValueProcessor() {
        Holders.config = null
        request.contextPath = "/foo"
        request.serverPort = 8080
        def template = '<g:createLink action="testAction" controller="testController" absolute="false" />'
        assertOutputEquals '/foo/testController/testAction?requestDataValueProcessorParamName=paramValue', template

        template = '<g:createLink action="testAction" controller="testController" absolute="${false}" />'
        assertOutputEquals '/foo/testController/testAction?requestDataValueProcessorParamName=paramValue', template
    }

    /**
     * Tests regression of <a href="http://jira.codehaus.org/browse/GRAILS-3368">GRAILS-3368</a>.
     * The context path should not be included in the generated link
     * if "base" is set to an empty string.
     */
    void testCreateLinkWithNoContextPath() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:createLink base="" action="testAction" controller="testController" />'
        assertOutputEquals '/testController/testAction', template
    }

    void testCreateLinkWithNoContextPathAndRequestDataValueProcessor() {
        def template = '<g:createLink base="" action="testAction" controller="testController" />'
        assertOutputEquals '/testController/testAction?requestDataValueProcessorParamName=paramValue', template
    }

    void testCreateLinkWithAbsolute() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:createLink absolute="true" action="testAction" controller="testController" />'
        request.serverPort = 8080
        assertOutputEquals 'http://localhost:8080/testController/testAction', template

        template = '<g:createLink absolute="${true}" action="testAction" controller="testController" />'
        request.serverPort = 8080
        assertOutputEquals 'http://localhost:8080/testController/testAction', template
    }

    void testCreateLinkWithAbsoluteAndRequestDataValueProcessor() {
        def template = '<g:createLink absolute="true" action="testAction" controller="testController" />'
        request.serverPort = 8080
        assertOutputEquals 'http://localhost:8080/testController/testAction?requestDataValueProcessorParamName=paramValue', template

        template = '<g:createLink absolute="${true}" action="testAction" controller="testController" />'
        request.serverPort = 8080
        assertOutputEquals 'http://localhost:8080/testController/testAction?requestDataValueProcessorParamName=paramValue', template
    }

    void testCreateLinkWithAbsoluteFalse() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:createLink absolute="false" action="testAction" controller="testController" />'
        request.serverPort = 8080
        assertOutputEquals '/testController/testAction', template

        template = '<g:createLink absolute="${false}" action="testAction" controller="testController" />'
        request.serverPort = 8080
        assertOutputEquals '/testController/testAction', template
    }

    void testCreateLinkWithAbsoluteFalseAndRequestDataValueProcessor() {
        def template = '<g:createLink absolute="false" action="testAction" controller="testController" />'
        request.serverPort = 8080
        assertOutputEquals '/testController/testAction?requestDataValueProcessorParamName=paramValue', template

        template = '<g:createLink absolute="${false}" action="testAction" controller="testController" />'
        request.serverPort = 8080
        assertOutputEquals '/testController/testAction?requestDataValueProcessorParamName=paramValue', template
    }

    void testJoinStrings() {
        def template = /<g:join in="['Bruce', 'Adrian', 'Dave', 'Nicko', 'Steve']" delimiter="_"\/>/
        assertOutputEquals 'Bruce_Adrian_Dave_Nicko_Steve', template
    }

    void testJoinStringsWithEmptyDelimiter() {
        def template = /<g:join in="['Bruce', 'Adrian', 'Dave', 'Nicko', 'Steve']" delimiter=""\/>/
        assertOutputEquals 'BruceAdrianDaveNickoSteve', template
    }

    void testJoinWithEmptyCollection() {
        def template = /<g:join in="[]" delimiter="_"\/>/
        assertOutputEquals '', template
    }

    void testJoinWithoutSpecifyingIn() {
        def template = '<g:join delimiter="_"/>'
        def msg = shouldFail(GrailsTagException) {
            applyTemplate template
        }
        assertTrue msg?.contains('Tag ["join"] missing required attribute ["in"]')
    }

    void testJoinWithoutSpecifyingDelimiter() {
        def template = /<g:join in="['Bruce', 'Adrian', 'Dave', 'Nicko', 'Steve']"\/>/
        assertOutputEquals 'Bruce, Adrian, Dave, Nicko, Steve', template
    }

    void testImg() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:img dir="images" file="logo.png" width="100" height="200"/>'
        assertOutputEquals '<img src="/images/logo.png" width="100" height="200" />', template

        template = '<g:img file="logo.png" width="100" height="200"/>'
        assertOutputEquals '<img src="/images/logo.png" width="100" height="200" />', template
    }

    void testImgWithRequestDataValueProcessor() {
        def template = '<g:img dir="images" file="logo.png" width="100" height="200"/>'
        assertOutputEquals '<img src="/images/logo.png?requestDataValueProcessorParamName=paramValue" width="100" height="200" />', template

        template = '<g:img file="logo.png" width="100" height="200"/>'
        assertOutputEquals '<img src="/images/logo.png?requestDataValueProcessorParamName=paramValue" width="100" height="200" />', template
    }

    void testExternal() {
        unRegisterRequestDataValueProcessor()
        def template = '<g:external uri="/js/main.js"/>'
        assertOutputEquals '<script src="/js/main.js" type="text/javascript"></script>\r\n', template

        template = '<g:external uri="/css/style.css"/>'
        assertOutputEquals '<link href="/css/style.css" type="text/css" rel="stylesheet" media="screen, projection"/>\r\n', template

        template = '<g:external uri="/css/print.css" media="print"/>'
        assertOutputEquals '<link href="/css/print.css" type="text/css" rel="stylesheet" media="print"/>\r\n', template

        template = '<g:external uri="/images/icons/iphone-icon.png" type="appleicon"/>'
        assertOutputEquals '<link href="/images/icons/iphone-icon.png" rel="apple-touch-icon"/>\r\n', template
    }

    void testExternalWithRequestDataValueProcessor() {
        def template = '<g:external uri="/js/main.js"/>'
        assertOutputEquals '<script src="/js/main.js?requestDataValueProcessorParamName=paramValue" type="text/javascript"></script>\r\n', template

        template = '<g:external uri="/css/style.css"/>'
        assertOutputEquals '<link href="/css/style.css?requestDataValueProcessorParamName=paramValue" type="text/css" rel="stylesheet" media="screen, projection"/>\r\n', template

        template = '<g:external uri="/css/print.css" media="print"/>'
        assertOutputEquals '<link href="/css/print.css?requestDataValueProcessorParamName=paramValue" type="text/css" rel="stylesheet" media="print"/>\r\n', template

        template = '<g:external uri="/images/icons/iphone-icon.png" type="appleicon"/>'
        assertOutputEquals '<link href="/images/icons/iphone-icon.png?requestDataValueProcessorParamName=paramValue" rel="apple-touch-icon"/>\r\n', template
    }

    private void unRegisterRequestDataValueProcessor() {
        tagLib.requestDataValueProcessor = null
    }

    void testWithTagWithNameAndAttrs() {
        def template = '''<g:withTag name="div" attrs="[class: 'foo']">body</g:withTag>'''
        assertOutputEquals '<div class="foo">body</div>', template

        template = '<g:withTag name="div">body</g:withTag>'
        assertOutputEquals '<div>body</div>', template
    }

    void testCreateLinkWithRelativeUri() {
        request.setAttribute WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE, 'http://localhost:8080/test/foo/bar.html'

        def template = '<g:createLink relativeUri="wahoo.html" />'
        assertOutputEquals 'http://localhost:8080/test/foo/wahoo.html?requestDataValueProcessorParamName=paramValue', template

        template = '<g:createLink relativeUri="../wahoo.html" />'
        assertOutputEquals 'http://localhost:8080/test/foo/../wahoo.html?requestDataValueProcessorParamName=paramValue', template

        appCtx.getBean(FormTagLib.name).requestDataValueProcessor = new MockRequestDataValueProcessor()
        try {
            template = '<g:form relativeUri="../bar"></g:form>'
            assertOutputEquals('<form action="http://localhost:8080/test/foo/../bar" method="post" ><input type="hidden" name="requestDataValueProcessorHiddenName" value="hiddenValue" />\n</form>', template)
        }
        finally {
            appCtx.getBean(FormTagLib.name).requestDataValueProcessor = null
        }
    }
    
    void testCreateLinkWithUriAndParams() {
        unRegisterRequestDataValueProcessor()
        def template = '''<g:createLink uri="/some/path" params="[width:21, height:12]"/>'''
        assertOutputEquals '/some/path?width=21&height=12', template
    }
    
    void testCreateLinkWithParamsAndUriContainsRequestParams() {
        unRegisterRequestDataValueProcessor()
        def template = '''<g:createLink uri="/some/path?name=JSB" params="[width:21, height:12]"/>'''
        assertOutputEquals '/some/path?name=JSB&width=21&height=12', template
    }

    void testUrlEncodingParamsCreateLinkWithUri() {
        unRegisterRequestDataValueProcessor()
        def template = '''<g:createLink uri="/some/path" params="['some height':12, name: 'Jeff Brown']"/>'''
        assertOutputEquals '/some/path?some+height=12&name=Jeff+Brown', template
    }
}
