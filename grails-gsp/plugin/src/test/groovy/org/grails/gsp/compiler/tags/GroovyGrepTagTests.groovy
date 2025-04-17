package org.grails.gsp.compiler.tags

import org.grails.gsp.GroovyPage
import org.grails.gsp.compiler.GroovyPageParser
import org.grails.taglib.GrailsTagException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

class GroovyGrepTagTests {

    private GroovyGrepTag tag = new GroovyGrepTag();
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
    void testDoStartTag() {

        assertThrows(GrailsTagException, {
            tag.doStartTag()
        }, "Should throw exception for required attributes")

        tag.setAttributes('"in"': 'test', '"filter"':'\${~/regex/}')
        tag.doStartTag()

        assertEquals("for( "+tag.getForeachRenamedIt()+" in evaluate('test.grep(~/regex/)', 1, it) { return test.grep(~/regex/) } ) {"+System.getProperty("line.separator")+ "changeItVariable(" + tag.getForeachRenamedIt() + ")" + System.getProperty("line.separator"), sw.toString())
    }

    @Test
    void testWithStatus() {
        tag.setAttributes('"in"': 'test', '"filter"':'\${~/regex/}','"status"':"i",'"var"':"t")
        tag.doStartTag()

        assertEquals("loop:{int i = 0for( t in evaluate('test.grep(~/regex/)', 1, it) { return test.grep(~/regex/) } ) {", sw.toString().replaceAll('[\r\n]', ''))
    }
}
