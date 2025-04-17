package org.grails.gsp.jsp

import grails.testing.spock.OnceBefore
import grails.testing.web.taglib.TagLibUnitTest
import grails.web.http.HttpHeaders
import jakarta.servlet.http.HttpServletRequest
import org.grails.config.PropertySourcesConfig
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.web.pages.GroovyPagesServlet
import org.springframework.context.support.StaticMessageSource
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyPageWithJSPTagsTests extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    static final Closure JSP_CONFIG = { PropertySourcesConfig config ->
        def tldScanPattern = [
                'classpath*:/META-INF/spring*.tld',
                'classpath*:/META-INF/fmt.tld',
                'classpath*:/META-INF/c.tld',
                'classpath*:/META-INF/core.tld'
        ].join(',')
        config.merge(
            ['grails':
                 ['gsp':
                      ['tldScanPattern': tldScanPattern]
                ]
            ]
        )
    }

    @Override
    Closure doWithConfig() { JSP_CONFIG }

    @OnceBefore
    @SuppressWarnings('unused')
    void onInit() {
        GroovySystem.metaClassRegistry.removeMetaClass(HttpServletRequest)
        GroovySystem.metaClassRegistry.removeMetaClass(MockHttpServletRequest)
        webRequest.getCurrentRequest().setAttribute(GroovyPagesServlet.SERVLET_INSTANCE, new GroovyPagesServlet())
    }

    def setup() {
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, Locale.ENGLISH)
    }

    def cleanupSpec() {
        GroovySystem.metaClassRegistry.removeMetaClass(TagLibraryResolverImpl)
    }

    @Issue(['GRAILS-4573', 'https://github.com/grails/grails-core/issues/3926'])
    def testIterativeTags() {
        given:
        def template = '''
            |<%@ taglib prefix="c" uri="jakarta.tags.core" %>
            |<html>
            |   <body>
            |       <c:forEach var="i" begin="1" end="3"><c:out value="${i}" /> . <c:out value="${i}" /><br/></c:forEach>
            |   </body>
            |</html>
        '''.stripMargin()

        when:
        def output = applyTemplate(template)

        then:
        output.contains('1 . 1<br/>2 . 2<br/>3 . 3<br/>')
    }

    @Issue(['GRAILS-3797', 'https://github.com/grails/grails-core/issues/1537'])
    @PendingFeature(reason = 'until we upgrade to next version of test support')
    def testGRAILS3797() {
        given:
        def template = '''
            |<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            |<html>
            |   <body>
            |       <g:form controller="search" action="search" method="get">
            |           <g:textField name="q" value="" />
            |           <g:actionSubmit value="search" /><br/>
            |           <img src="<spring:theme code="A_ICON" alt="icon"/>"/>
            |       </g:form>
            |   </body>
            |</html>
        '''.stripMargin()

        when:
        (messageSource as StaticMessageSource).addMessage('A_ICON', request.locale, 'test')
        def output = applyTemplate(template)

        then:
        output.contains('<img src="test"/>')
    }

    void testDynamicAttributes() {
        given:
        def template = '''
            |<%@ taglib prefix="spring" uri="http://www.springframework.org/tags/form" %>
            |<html>
            |   <body>
            |       <spring:form action="action" grails="rocks">
            |       </spring:form>
            |   </body>
            |</html>
        '''.stripMargin()

        when:
        def output = applyTemplate(template)

        then:
        output.contains('grails="rocks"')
    }

    @Issue(['GRAILS-3845', 'https://github.com/grails/grails-core/issues/8830'])
    def testNestedJSPTags() {
        given:
        def template = '''
            |<%@ taglib uri="jakarta.tags.core" prefix="c" %>
            |<html>
            |   <head>
            |       <title>test</title>
            |   </head>
            |   <body>
            |       <c:choose>
            |           <c:when test="${1==1}">
            |               hello
            |           </c:when>
            |           <c:when test="${1==0}">
            |               goodbye
            |           </c:when>
            |       </c:choose>
            |   </body>
            |</html>
        '''.stripMargin()

        when:
        def output = applyTemplate(template)

        then:
        output.contains('hello')
        !output.contains("goodbye")
    }

    def testGSPCantOverrideDefaultNamespaceWithJSP() {
        given:
        def template = '''
            |<%@ taglib prefix="g" uri="jakarta.tags.fmt" %>
            |<g:formatNumber number="10" format=".00"/>
        '''.stripMargin()

        when:
        def output = applyTemplate(template).strip()

        then:
        output == '10.00'
    }

    def testGSPWithIterativeJSPTag() {
        given:
        def template = '''
            |<%@ taglib prefix="c" uri="jakarta.tags.core" %>
            |<g:set var="foo" value="${[1,2,3]}" />
            |<c:forEach items="${foo}" var="num"><p>${num}</p></c:forEach>
        '''.stripMargin()

        when:
        def output = applyTemplate(template).strip()

        then:
        output == '<p>1</p><p>2</p><p>3</p>'
    }

    def testSimpleTagWithValue() {
        given:
        def template = '''
            |<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
            |<fmt:formatNumber value="${10}" pattern=".00"/>
        '''.stripMargin()

        when:
        def output = applyTemplate(template).strip()

        then:
        output == '10.00'
    }

    def testInvokeJspTagAsMethod() {
        given:
        def template = '''
            |<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
            |${fmt.formatNumber(value:10, pattern:".00")}
        '''.stripMargin()

        when:
        def output = applyTemplate(template).strip()

        then:
        output == '10.00'
    }

    def testInvokeJspTagAsMethodWithBody() {
        given:
        def template = '''
            |<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
            |${fmt.formatNumber(pattern:".00",10)}
        '''.stripMargin()

        when:
        def output = applyTemplate(template).strip()

        then:
        output == '10.00'
    }

    def testSimpleTagWithBody() {
        given:
        def template = '''
            |<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
            |<fmt:formatNumber pattern=".00">10</fmt:formatNumber>
        '''.stripMargin()

        when:
        def output = applyTemplate(template).strip()

        then:
        output == '10.00'
    }

    def testSpringJSPTags() {
        given:
        def template = '''
            |<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
            |<form:form commandName="address" action="do">
            |    <b>Zip: </b><form:input path="zip"/>
            |</form:form>
        '''.stripMargin()

        when:
        request.setAttribute('address', new TestJspTagAddress(zip: '342343'))
        request.setAttribute('command', new TestJspTagAddress(zip: '342343'))
        def output = applyTemplate(template).strip()

        then:
        output == '''
            |<form id="command" commandName="address" action="do" method="post">
            |    <b>Zip: </b><input id="zip" name="zip" type="text" value="342343"/>
            |</form>
        '''.stripMargin().strip()
    }
}

class TestJspTagAddress {
    String zip
}
