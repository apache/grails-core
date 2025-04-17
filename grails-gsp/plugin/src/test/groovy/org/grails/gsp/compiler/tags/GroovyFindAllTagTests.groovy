package org.grails.gsp.compiler.tags

import org.grails.gsp.GroovyPage
import org.grails.gsp.compiler.GroovyPageParser
import org.grails.taglib.GrailsTagException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * @author Jeff Brown
 */
class GroovyFindAllTagTests {

    def tag = new GroovyFindAllTag()
    def sw = new StringWriter()

    @BeforeEach
    protected void setUp() {
        Map context = new HashMap();
        context.put(GroovyPage.OUT, new PrintWriter(sw));
        GroovyPageParser parser=new GroovyPageParser("test", "test", "test", new ByteArrayInputStream([] as byte[]), null);
        context.put(GroovyPageParser.class, parser);
        tag.init(context);
    }

    @Test
    void testIsBufferWhiteSpace() {
        assertFalse(tag.isKeepPrecedingWhiteSpace())
    }

    @Test
    void testHasPrecedingContent() {
        assertTrue(tag.isAllowPrecedingContent())
    }

    @Test
    void testDoStartWithNoInAttribute() {
        tag.attributes = ['"expr"': " someExpression "]
        assertThrows(GrailsTagException) {
            tag.doStartTag()
        }
    }

    @Test
    void testDoStartWithNoExprAttribute() {
        tag.attributes = ['"in"': " someExpression "]
        assertThrows(GrailsTagException) {
            tag.doStartTag()
        }
    }

    @Test
    void testDoStartTag() {
        tag.attributes = ['"expr"': " \${it.age > 19}", '"in"': "myObj"]
        tag.doStartTag()

        assertEquals("for( "+tag.getForeachRenamedIt()+" in evaluate('myObj.findAll {it.age > 19}', 1, it) { return myObj.findAll {it.age > 19} } ) {"+System.getProperty("line.separator")+ "changeItVariable(" + tag.getForeachRenamedIt() + ")" + System.getProperty("line.separator"), sw.toString())
    }

    @Test
    void testDoEndTag() {
        tag.doEndTag()
        assertEquals("}${System.properties['line.separator']}".toString(), sw.toString())
    }

    @Test
    void testTagName() {
        assertEquals("findAll", tag.getName())
    }
}
