/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.web.pages

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.GrailsWebMockUtil
import grails.util.GrailsWebUtil
import grails.web.pages.GroovyPagesUriService
import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.IOGroovyMethods
import org.grails.gsp.GroovyPage
import org.grails.gsp.compiler.GroovyPageParser
import org.grails.taglib.GrailsTagException
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.junit.jupiter.api.Assertions
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.PendingFeature
import spock.lang.Specification

/**
 * Tests the GSP parser.  This can detect issues caused by improper
 * GSP->Groovy conversion.  Normally, to compare the code, you can
 * run the page with a showSource parameter specified.
 * <p>
 * The methods parseCode() and trimAndRemoveCR() have been added
 * to simplify test case code.
 */
class ParseSpec extends Specification {
    static final String GSP_FOOTER = """public static final Map JSP_TAGS = new HashMap()
protected void init() {
\tthis.jspTags = JSP_TAGS
}
public static final String CONTENT_TYPE = 'text/html;charset=UTF-8'
public static final long LAST_MODIFIED = 0L
public static final String EXPRESSION_CODEC = 'HTML'
public static final String STATIC_CODEC = 'none'
public static final String OUT_CODEC = 'none'
public static final String TAGLIB_CODEC = 'none'
}
"""

    void 'parse'() {
        given:
        String expected = makeImports() +
                '\n' +
                'class myTest1 extends org.grails.gsp.GroovyPage {\n' +
                'public String getGroovyPageFileName() { "myTest1" }\n' +
                'public Object run() {\n' +
                'Writer out = getOut()\n' +
                'Writer expressionOut = getExpressionOut()\n' +
                'printHtmlPart(0)\n' +
                '}\n' + GSP_FOOTER

        when:
        def result = parseCode('myTest1', '<div>hi</div>')

        then:
        trimAndRemoveCR(expected) == trimAndRemoveCR(result.generatedGsp)
        '<div>hi</div>' == result.htmlParts[0]
    }

    void 'parse with unclosed square brackets'() {
        given:
        String expected = makeImports() +
                '\n' +
                'class myTest2 extends org.grails.gsp.GroovyPage {\n' +
                'public String getGroovyPageFileName() { "myTest2" }\n' +
                'public Object run() {\n' +
                'Writer out = getOut()\n' +
                "Writer expressionOut = getExpressionOut()\n" +

                "invokeTag('message','g',1,['code':evaluate('\"testing [\"', 1, it) { return \"testing [\" }],-1)\n" +
                '}\n' + GSP_FOOTER

        when:
        String output = parseCode('myTest2', '<g:message code="testing ["/>').generatedGsp;

        then:
        trimAndRemoveCR(expected) == trimAndRemoveCR(output)
    }

    void 'parse without unclosed gstring throws exception'() {
        when:
        parseCode('myTest3', '<g:message_value="${boom">')

        then:
        def e = thrown(GrailsTagException)
        e.message == '[myTest3:1] Unclosed GSP expression'
    }

    void 'parse with utf8'() {
        given:
        // This is some unicode Chinese (who knows what it says!)
        String src = 'Chinese text: \u3421\u3437\u343f\u3443\u3410\u3405\u38b3\u389a\u395e\u3947\u3adb\u3b5a\u3b67'

        and:
        String expected = makeImports() +
                '\n' +
                'class myTest4 extends org.grails.gsp.GroovyPage {\n' +
                'public String getGroovyPageFileName() { "myTest4" }\n' +
                'public Object run() {\n' +
                'Writer out = getOut()\n' +
                'Writer expressionOut = getExpressionOut()\n' +
                'printHtmlPart(0)\n' +
                '}\n' + GSP_FOOTER

        // Sanity check the string loaded OK as unicode - it won't look right if you output it, default stdout is not UTF-8
        // on many OSes
        Assertions.assertEquals(src.indexOf('?'), -1)
        def config =new ConfigSlurper().parse('grails.views.gsp.encoding = "UTF-8"')
        buildMockRequest(config)

        when:
        def output = parseCode('myTest4', src)

        then:
        trimAndRemoveCR(expected) == trimAndRemoveCR(output.generatedGsp)
        src == output.htmlParts[0]

        cleanup:
        RequestContextHolder.resetRequestAttributes()
    }

    void 'parse with local encoding'() {
        given:
        String src = 'This is just plain ASCII to make sure test works on all platforms'
        // Sanity check the string loaded OK as unicode - it won't look right if you output it,
        // default stdout is not UTF-8 on many OSes
        Assertions.assertEquals(src.indexOf('?'), -1)

        and:
        String expected = makeImports() +
                '\n' +
                'class myTest5 extends org.grails.gsp.GroovyPage {\n' +
                'public String getGroovyPageFileName() { "myTest5" }\n' +
                'public Object run() {\n' +
                'Writer out = getOut()\n' +
                'Writer expressionOut = getExpressionOut()\n' +
                'printHtmlPart(0)\n' +
                '}\n' + GSP_FOOTER

        when:
        def output = parseCode('myTest5', src)

        then:
        trimAndRemoveCR(expected) == trimAndRemoveCR(output.generatedGsp)
        src == output.htmlParts[0]
    }

    void 'parse gtags with namespace'() {
        given:
        String output = parseCode('myTest6',
                '<tbody>\n' +
                        '  <tt:form />\n' +
                        '</tbody>').generatedGsp;
        System.out.println('output = ' + output);

        expect: "should have call to tag with 'tt' namespace"
        output.indexOf("invokeTag('form','tt',2,[:],-1)") > -1
    }

    void 'parse with whitespace not eaten'() {
        given:
        String expected = makeImports() +
                '\n' +
                'class myTest7 extends org.grails.gsp.GroovyPage {\n' +
                'public String getGroovyPageFileName() { "myTest7" }\n' +
                'public Object run() {\n' +
                'Writer out = getOut()\n' +
                'Writer expressionOut = getExpressionOut()\n' +
                'printHtmlPart(0)\n' +
                GroovyPage.EXPRESSION_OUT_STATEMENT + ".print(evaluate('uri', 3, it) { return uri })\n" +
                'printHtmlPart(1)\n' +
                '}\n' + GSP_FOOTER;

        when:
        def output = parseCode('myTest7',
                'Please click the link below to confirm your email address:\n' +
                        '\n' +
                        '${uri}\n' +
                        '\n' +
                        '\n' +
                        'Thanks')

        then:
        expected.replaceAll('[\r\n]', '') == output.generatedGsp.replaceAll('[\r\n]', '')
        'Please click the link below to confirm your email address:\n\n' == output.htmlParts[0]
        '\n\n\nThanks' == output.htmlParts[1]
    }

    @PendingFeature
    void 'body with gstring attribute'() {
        given:
        String expected = makeImports() +
                '\n' +
                'class GRAILS5598 extends org.grails.gsp.GroovyPage {\n' +
                'public String getGroovyPageFileName() { \'GRAILS5598\' }\n' +
                'public Object run() {\n' +
                'Writer out = getOut()\n' +
                'Writer expressionOut = getExpressionOut()\n' +
                'createClosureForHtmlPart(0, 1)\n' +
                "invokeTag('captureBody','grailsLayout',1,['class':evaluate('\"\${page.name} \${page.group.name.toLowerCase()}\"', 1, it) { return \"\${page.name} \${page.group.name.toLowerCase()}\" }],1)\n" +
                '}\n' + GSP_FOOTER

        when:
        def result = parseCode('GRAILS5598', '<body class="${page.name} ${page.group.name.toLowerCase()}">text</body>')

        then:
        trimAndRemoveCR(expected) == trimAndRemoveCR(result.generatedGsp)
        'text' == result.htmlParts[0]
    }

    void 'bypass grails layout preprocess'() {
        given:
        String expected = makeImports() +
                '\n' +
                'class GRAILS_LAYOUT_PREPROCESS_TEST extends org.grails.gsp.GroovyPage {\n' +
                'public String getGroovyPageFileName() { "GRAILS_LAYOUT_PREPROCESS_TEST" }\n' +
                'public Object run() {\n' +
                'Writer out = getOut()\n' +
                'Writer expressionOut = getExpressionOut()\n' +
                'printHtmlPart(0)\n' +
                '}\n' + GSP_FOOTER

        when:
        def result = parseCode('GRAILS_LAYOUT_PREPROCESS_TEST', '<%@page grailsLayoutPreprocess="false"%>\n<body>text</body>')

        then:
        trimAndRemoveCR(expected) == trimAndRemoveCR(result.generatedGsp)
        '\n<body>text</body>' == result.htmlParts[0]
    }

    @PendingFeature
    void 'meta with gstring attribute'() {
        given:
        String expected = makeImports() +
                '\n' +
                'class GRAILS5605 extends org.grails.gsp.GroovyPage {\n' +
                'public String getGroovyPageFileName() { "GRAILS5605" }\n' +
                'public Object run() {\n' +
                'Writer out = getOut()\n' +
                'Writer expressionOut = getExpressionOut()\n' +
                'printHtmlPart(0)\n' +
                'createTagBody(1, {->\n' +
                "invokeTag('captureMeta','grailsLayout',1,['gsp_sm_xmlClosingForEmptyTag':evaluate('\"/\"', 1, it) { return \"/\" },'name':evaluate('\"SomeName\"', 1, it) { return \"SomeName\" },'content':evaluate('\"\${grailsApplication.config.myFirstConfig}/something/\${someVar}\"', 1, it) { return \"\${grailsApplication.config.myFirstConfig}/something/\${someVar}\" }],-1)\n" +
                '})\n' +
                "invokeTag('captureHead','grailsLayout',1,[:],1)\n" +
                'printHtmlPart(1)\n' +
                '}\n' + GSP_FOOTER

        when:
        def result = parseCode('GRAILS5605', "<html><head><meta name=\"SomeName\" content='\${grailsApplication.config.myFirstConfig}/something/\${someVar}' /></head></html>");

        then:
        trimAndRemoveCR(expected) == trimAndRemoveCR(result.generatedGsp)
    }

    static ParsedResult parseCode(String uri, String gsp) throws IOException {
        // Simulate what the parser does so we get it in the encoding expected
        Object enc = GrailsWebUtil.currentConfiguration().get('grails.views.gsp.encoding')
        if ((enc == null) || (enc.toString().trim().length() == 0)) {
            enc = System.getProperty('file.encoding', 'us-ascii')
        }

        InputStream gspIn = new ByteArrayInputStream(gsp.getBytes(enc.toString()))
        GroovyPageParser parse = new GroovyPageParser(uri, uri, uri, gspIn, enc.toString(), 'HTML', null)

        InputStream inStream = parse.parse()
        def result = new ParsedResult()
        result.parser = parse
        result.generatedGsp = IOGroovyMethods.getText(inStream, enc.toString())
        result.htmlParts = parse.getHtmlPartsArray()
        return result
    }

    static String makeImports() {
        StringBuilder result = new StringBuilder()
        for (int i = 0; i < GroovyPageParser.DEFAULT_IMPORTS.length; i++) {
            result.append('import ').append(GroovyPageParser.DEFAULT_IMPORTS[i]).append('\n')
        }
        return result.toString()
    }

    /**
     * Eliminate potential issues caused by operating system differences
     * and minor output differences that we don't care about.
     * <p>
     * Note: this code is inefficient and could stand to be optimized.
     */
    static String trimAndRemoveCR(String s) {
        int index
        StringBuilder sb = new StringBuilder(s.trim())
        while (((index = sb.toString().indexOf('\r')) != -1) || ((index = sb.toString().indexOf('\n')) != -1)) {
            sb.deleteCharAt(index)
        }
        return sb.toString()
    }

    static GrailsWebRequest buildMockRequest(ConfigObject config) throws Exception {
        GenericWebApplicationContext appCtx = new GenericWebApplicationContext(new MockServletContext())
        appCtx.refresh()
        appCtx.getBeanFactory().registerSingleton(GroovyPagesUriService.BEAN_ID, new DefaultGroovyPagesUriService())

        DefaultGrailsApplication grailsApplication = new DefaultGrailsApplication()
        grailsApplication.setConfig(config)
        appCtx.getBeanFactory().registerSingleton(GrailsApplication.APPLICATION_ID, grailsApplication)
        appCtx.getServletContext().setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, appCtx)
        appCtx.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appCtx)
        return GrailsWebMockUtil.bindMockWebRequest(appCtx)
    }
}

@CompileStatic
class ParsedResult {
    String generatedGsp;
    GroovyPageParser parser;
    String[] htmlParts;

    @Override
    String toString() {
        return generatedGsp;
    }
}
