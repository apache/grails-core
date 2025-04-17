package org.grails.gsp.compiler;

import org.grails.gsp.GroovyPage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the GSP lexer (Scan class).
 *
 * @author a.shneyderman
 */
public class ScanTests {

    @Test
    public void testTagsCustomNamespace() {
        String gsp =
            "<tbody>\n" +
            "  <tt:form />\n" +
            "</tbody>";

        GroovyPageScanner s = new GroovyPageScanner(gsp);
        int next;
        while ((next = s.nextToken()) != Tokens.EOF) {
            if (next == Tokens.GSTART_TAG ||
                next == Tokens.GEND_TAG) {
                Assertions.assertEquals("tt", s.getNamespace());
            }
        }
    }

    @Test
    public void testTagsDefaultNamespace() {
        String gsp =
            "<tbody>\n" +
            "  <g:form />\n" +
            "</tbody>";

        GroovyPageScanner s = new GroovyPageScanner(gsp);
        int next;
        while ((next = s.nextToken()) != Tokens.EOF) {
            if (next == Tokens.GSTART_TAG ||
                next == Tokens.GEND_TAG) {
                Assertions.assertEquals(GroovyPage.DEFAULT_NAMESPACE, s.getNamespace());
            }
        }
    }

    @Test
    public void testMaxHtmlLength() {
        String gsp = "0123456789ABCDEFGHIJK";
        GroovyPageScanner scanner = new GroovyPageScanner(gsp);
        scanner.setMaxHtmlLength(10);
        Assertions.assertEquals(GroovyPageScanner.HTML, scanner.nextToken());
        Assertions.assertEquals("0123456789", scanner.getToken());
        Assertions.assertEquals(GroovyPageScanner.HTML, scanner.nextToken());
        Assertions.assertEquals("ABCDEFGHIJ", scanner.getToken());
        Assertions.assertEquals(GroovyPageScanner.HTML, scanner.nextToken());
        Assertions.assertEquals("K", scanner.getToken());
        Assertions.assertEquals(GroovyPageScanner.EOF, scanner.nextToken());
    }
}
