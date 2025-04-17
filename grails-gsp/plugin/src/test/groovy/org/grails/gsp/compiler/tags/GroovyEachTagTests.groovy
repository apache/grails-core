package org.grails.gsp.compiler.tags

import org.grails.gsp.GroovyPage
import org.grails.gsp.compiler.GroovyPageParser
import org.grails.taglib.GrailsTagException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

class GroovyEachTagTests {

    private GroovyEachTag tag = new GroovyEachTag();
    private StringWriter sw = new StringWriter();

    @BeforeEach
    protected void setUp() throws Exception {
        Map context = new HashMap();
        context.put(GroovyPage.OUT, new PrintWriter(sw));
        GroovyPageParser parser=new GroovyPageParser("test", "test", "test", new ByteArrayInputStream(new byte[]{}), null);
        context.put(GroovyPageParser.class, parser);
        tag.init(context);
    }

    @Test
    void testEachWithSafeDereference() {
        assertThrows(GrailsTagException) {
            tag.doStartTag()
        }

        tag.setAttributes('"in"': 'test?')

        tag.doStartTag()

       assertEquals("for( "+tag.getForeachRenamedIt()+" in evaluate('test', 1, it) { return test } ) {"+ System.getProperty("line.separator")+ "changeItVariable(" + tag.getForeachRenamedIt() + ")" + System.getProperty("line.separator"), sw.toString())
    }

    @Test
    void testSimpleEach() {
        assertThrows(GrailsTagException) {
            tag.doStartTag()
        }

        tag.setAttributes('"in"': 'test')

        tag.doStartTag()

        assertEquals("for( "+tag.getForeachRenamedIt()+" in evaluate('test', 1, it) { return test } ) {"+ System.getProperty("line.separator")+ "changeItVariable(" + tag.getForeachRenamedIt() + ")" + System.getProperty("line.separator"),sw.toString())
    }

    @Test
    void testEachWithVar() {
        tag.setAttributes('"in"': 'test', '"var"':"i")

        tag.doStartTag()

        assertEquals("for( i in evaluate('test', 1, it) { return test } ) {"+ System.getProperty("line.separator"),sw.toString())
    }

    @Test
    void testEachWithStatusOnly() {
        tag.setAttributes('"in"': 'test', '"status"':"i")
        assertThrows(GrailsTagException) {
            tag.doStartTag()
        }
    }

    @Test
    void testEachWithStatusAndVar() {
        tag.setAttributes('"in"': 'test', '"status"':"i",'"var"':"i")

        assertThrows(GrailsTagException) {
            tag.doStartTag()
        }
        tag.setAttributes('"var"':'j')
        tag.doStartTag()

        assert sw.toString().replaceAll('[\r\n]', '') == "loop:{int i = 0for( j in evaluate('test', 1, it) { return test } ) {"
    }
}
