package org.grails.web.pages

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.gsp.GroovyPagesException
import org.grails.plugins.web.taglib.ApplicationTagLib
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @author rvanderwerf
 * @since 1.0
 */
class GroovyPageLineNumberTests extends Specification implements TagLibUnitTest<ApplicationTagLib> {

    def testSpanningMultipleLines() {
        when:
        def template = '''
 <a href="${createLink(action:'listActivity', controller:'activity',
        params:[sort:params.sort?params.sort:'',
        order:params.order?params.order:'asc', offset:params.offset?params.offset:0])}"">Click me</a>
'''
        String output = applyTemplate(template).trim()

        then:

        output == '<a href="/activity/listActivity?sort=&amp;order=asc&amp;offset=0"">Click me</a>'
    }

    def testExpressionWithQuotes() {
        when:
        def template = '${foo + \' \' + bar}'
        String output = applyTemplate(template, [foo:"one", bar:"two"])

        then:
        output == "one two"

        when:
        template = '<g:createLinkTo dir="${foo}" file="${foo + \' \' + bar}" />'
        output = applyTemplate(template, [foo:"one", bar:"two"])

        then:
        output == "/static/one/one two"
    }

    def testLineNumberDataInsideTagAttribute() {

        when:
        def template = '''

<p />

<g:set var="foo" value="${foo.bar.path}" />

<p />
'''

        applyTemplate(template)

        then:
        thrown GroovyPagesException


/*
            def cause = e.cause
            while (cause != cause.cause && cause.cause) {
                cause = cause.cause
            }
            assertTrue "The cause should have been a NPE but was ${cause}", cause instanceof NullPointerException
            assertEquals 5, e.lineNumber
        }
*/
    }

    def testLineNumberingDataInsideExpression() {

        when:
        def template = '''

<p />

${foo.bar.path}

<p />
'''

        String output = applyTemplate(template)

        then:
        //thrown GroovyPagesException
        GroovyPagesException e = thrown()


            def cause = e.cause
            while (cause != cause.cause && cause.cause) {
                cause = cause.cause
            }
            cause instanceof NullPointerException
            5 == e.lineNumber

    }

    def testEachWithQuestionMarkAtEnd() {
        when:
        def template = '<g:each in="${list?}">${it}</g:each>'
        String output = applyTemplate(template, [list:[1,2,3]])
        then:
        output == "123"
    }

    def testStringWithQuestionMark() {
        when:
        def template = '${"hello?"}'
        String output = applyTemplate(template)

        then:
        output == "hello?"
    }

    def testComplexPage() {
        when:
        def template = '''
<html>
    <head>
        <title>Welcome to Grails</title>
        <meta name="layout" content="main" />
    </head>
    <body>
        <h1 style="margin-left:20px;">Welcome to Grails</h1>
        ${foo.bar.suck}
        <p style="margin-left:20px;width:80%">Congratulations, you have successfully started your first Grails application! At the moment
        this is the default page, feel free to modify it to either redirect to a controller or display whatever
        content you may choose. Below is a list of controllers that are currently deployed in this application,
        click on each to execute its default action:</p>
        <div class="dialog" style="margin-left:20px;width:60%;">
            <ul>

              <g:each var="c" in="${grailsApplication.controllerClasses}">
                    <li class="controller"><g:link controller="${c.logicalPropertyName}">${c.fullName}</g:link></li>
              </g:each>
            </ul>

        </div>
    </body>
</html>
'''
        String output = applyTemplate(template)

        then:
        //thrown GroovyPagesException
        GroovyPagesException e = thrown()
        9 ==e.lineNumber

    }
}
