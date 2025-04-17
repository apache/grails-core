package org.grails.web.pages;

import grails.core.DefaultGrailsApplication;
import grails.core.GrailsApplication;
import grails.util.GrailsWebMockUtil;
import grails.util.GrailsWebUtil;
import grails.web.pages.GroovyPagesUriService;
import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.grails.gsp.GroovyPage;
import org.grails.gsp.compiler.GroovyPageParser;
import org.grails.taglib.GrailsTagException;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.GrailsApplicationAttributes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.support.GenericWebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tests the GSP parser.  This can detect issues caused by improper
 * GSP->Groovy conversion.  Normally, to compare the code, you can
 * run the page with a showSource parameter specified.
 *
 * The methods parseCode() and trimAndRemoveCR() have been added
 * to simplify test case code.
 *
 * @author Daiji
 */
public class ParseTests {

    class ParsedResult {
        String generatedGsp;
        GroovyPageParser parser;
        String[] htmlParts;

        @Override
        public String toString() { return generatedGsp; }
    }

    protected static final String GSP_FOOTER = "public static final Map JSP_TAGS = new HashMap()\n"
            + "protected void init() {\n"
            + "\tthis.jspTags = JSP_TAGS\n"
            + "}\n"
            + "public static final String CONTENT_TYPE = 'text/html;charset=UTF-8'\n"
            + "public static final long LAST_MODIFIED = 0L\n"
            + "public static final String EXPRESSION_CODEC = 'HTML'\n"
            + "public static final String STATIC_CODEC = 'none'\n"
            + "public static final String OUT_CODEC = 'none'\n"
            + "public static final String TAGLIB_CODEC = 'none'\n" +
            "}\n";

    protected String makeImports() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < GroovyPageParser.DEFAULT_IMPORTS.length; i++) {
            result.append("import ").append(GroovyPageParser.DEFAULT_IMPORTS[i]).append("\n");
        }
        return result.toString();
    }

    @Test
    public void testParse() throws Exception {
        ParsedResult result = parseCode("myTest1", "<div>hi</div>");
        String expected = makeImports() +
            "\n" +
            "class myTest1 extends org.grails.gsp.GroovyPage {\n" +
            "public String getGroovyPageFileName() { \"myTest1\" }\n" +
            "public Object run() {\n" +
            "Writer out = getOut()\n" +
            "Writer expressionOut = getExpressionOut()\n"+
            "printHtmlPart(0)\n" +
            "}\n" + GSP_FOOTER;
        Assertions.assertEquals(trimAndRemoveCR(expected), trimAndRemoveCR(result.generatedGsp));
        Assertions.assertEquals("<div>hi</div>", result.htmlParts[0]);
    }

    @Test
    public void testParseWithUnclosedSquareBracket() throws Exception {
        String output = parseCode("myTest2", "<g:message code=\"testing [\"/>").generatedGsp;
        String expected = makeImports() +
            "\n" +
            "class myTest2 extends org.grails.gsp.GroovyPage {\n" +
            "public String getGroovyPageFileName() { \"myTest2\" }\n" +
            "public Object run() {\n" +
            "Writer out = getOut()\n" +
            "Writer expressionOut = getExpressionOut()\n"+

            "invokeTag('message','g',1,['code':evaluate('\"testing [\"', 1, it) { return \"testing [\" }],-1)\n" +
            "}\n" + GSP_FOOTER;

        Assertions.assertEquals(trimAndRemoveCR(expected), trimAndRemoveCR(output));
    }

    @Test
    public void testParseWithUnclosedGstringThrowsException() throws IOException {
        Assertions.assertThrows(GrailsTagException.class, () -> parseCode("myTest3", "<g:message value=\"${boom\">"), "[myTest3:1] Unclosed GSP expression");
    }

    @Test
    public void testParseWithUTF8() throws Exception {
        // This is some unicode Chinese (who knows what it says!)
        String src = "Chinese text: \u3421\u3437\u343f\u3443\u3410\u3405\u38b3\u389a\u395e\u3947\u3adb\u3b5a\u3b67";
        // Sanity check the string loaded OK as unicode - it won't look right if you output it, default stdout is not UTF-8
        // on many OSes
        Assertions.assertEquals(src.indexOf('?'), -1);

        ConfigObject config = new ConfigSlurper().parse("grails.views.gsp.encoding = \"UTF-8\"");

        buildMockRequest(config);
        ParsedResult output = null;
        try {
            output = parseCode("myTest4", src);
        }
        finally {
            RequestContextHolder.resetRequestAttributes();
        }
        String expected = makeImports() +
            "\n" +
            "class myTest4 extends org.grails.gsp.GroovyPage {\n" +
            "public String getGroovyPageFileName() { \"myTest4\" }\n" +
            "public Object run() {\n" +
            "Writer out = getOut()\n" +
            "Writer expressionOut = getExpressionOut()\n"+
            "printHtmlPart(0)\n" +
            "}\n" + GSP_FOOTER;
        Assertions.assertEquals(trimAndRemoveCR(expected), trimAndRemoveCR(output.generatedGsp));
        Assertions.assertEquals(src, output.htmlParts[0]);
    }

    private GrailsWebRequest buildMockRequest(ConfigObject config) throws Exception {
        GenericWebApplicationContext appCtx = new GenericWebApplicationContext(new MockServletContext());
        appCtx.refresh();
        appCtx.getBeanFactory().registerSingleton(GroovyPagesUriService.BEAN_ID, new DefaultGroovyPagesUriService());

        DefaultGrailsApplication grailsApplication = new DefaultGrailsApplication();
        grailsApplication.setConfig(config);
        appCtx.getBeanFactory().registerSingleton(GrailsApplication.APPLICATION_ID, grailsApplication);
        appCtx.getServletContext().setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, appCtx);
        appCtx.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appCtx);
        return GrailsWebMockUtil.bindMockWebRequest(appCtx);
    }

    @Test
    public void testParseWithLocalEncoding() throws IOException {
        String src = "This is just plain ASCII to make sure test works on all platforms";
        // Sanity check the string loaded OK as unicode - it won't look right if you output it,
        // default stdout is not UTF-8 on many OSes
        Assertions.assertEquals(src.indexOf('?'), -1);

        ParsedResult output = null;
        output = parseCode("myTest5", src);
        String expected = makeImports() +
            "\n" +
            "class myTest5 extends org.grails.gsp.GroovyPage {\n" +
            "public String getGroovyPageFileName() { \"myTest5\" }\n" +
            "public Object run() {\n" +
            "Writer out = getOut()\n" +
            "Writer expressionOut = getExpressionOut()\n"+
            "printHtmlPart(0)\n" +
            "}\n" + GSP_FOOTER;
        Assertions.assertEquals(trimAndRemoveCR(expected), trimAndRemoveCR(output.generatedGsp));
        Assertions.assertEquals(src, output.htmlParts[0]);
    }

    /**
     * Eliminate potential issues caused by operating system differences
     * and minor output differences that we don't care about.
     *
     * Note: this code is inefficient and could stand to be optimized.
     */
    public String trimAndRemoveCR(String s) {
        int index;
        StringBuilder sb = new StringBuilder(s.trim());
        while (((index = sb.toString().indexOf('\r')) != -1) || ((index = sb.toString().indexOf('\n')) != -1)) {
            sb.deleteCharAt(index);
        }
        return sb.toString();
    }

    public ParsedResult parseCode(String uri, String gsp) throws IOException {
        // Simulate what the parser does so we get it in the encoding expected
        Object enc = GrailsWebUtil.currentConfiguration().get("grails.views.gsp.encoding");
        if ((enc == null) || (enc.toString().trim().length() == 0)) {
            enc = System.getProperty("file.encoding", "us-ascii");
        }

        InputStream gspIn = new ByteArrayInputStream(gsp.getBytes(enc.toString()));
        GroovyPageParser parse = new GroovyPageParser(uri, uri, uri, gspIn, enc.toString(), "HTML", null);

        InputStream in = parse.parse();
        ParsedResult result = new ParsedResult();
        result.parser = parse;
        result.generatedGsp = IOGroovyMethods.getText(in, enc.toString());
        result.htmlParts = parse.getHtmlPartsArray();
        return result;
    }

    @Test
     public void testParseGTagsWithNamespaces() throws Exception {
         String output = parseCode("myTest6",
                 "<tbody>\n" +
                 "  <tt:form />\n" +
                 "</tbody>").generatedGsp;
         System.out.println("output = " + output);
         Assertions.assertTrue(output.indexOf("invokeTag('form','tt',2,[:],-1)") > -1, "should have call to tag with 'tt' namespace");
     }

     @Test
     public void testParseWithWhitespaceNotEaten() throws Exception {
         String expected = makeImports() +
            "\n" +
            "class myTest7 extends org.grails.gsp.GroovyPage {\n" +
            "public String getGroovyPageFileName() { \"myTest7\" }\n" +
            "public Object run() {\n" +
            "Writer out = getOut()\n" +
            "Writer expressionOut = getExpressionOut()\n"+
            "printHtmlPart(0)\n" +
            GroovyPage.EXPRESSION_OUT_STATEMENT + ".print(evaluate('uri', 3, it) { return uri })\n" +
            "printHtmlPart(1)\n" +
            "}\n" + GSP_FOOTER;

         ParsedResult output = parseCode("myTest7",
                 "Please click the link below to confirm your email address:\n" +
                 "\n" +
                 "${uri}\n" +
                 "\n" +
                 "\n" +
                 "Thanks");

         Assertions.assertEquals(expected.replaceAll("[\r\n]", ""), output.generatedGsp.replaceAll("[\r\n]", ""));
         Assertions.assertEquals("Please click the link below to confirm your email address:\n\n", output.htmlParts[0]);
         Assertions.assertEquals("\n\n\nThanks", output.htmlParts[1]);
     }

}
