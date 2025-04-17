package org.grails.gsp.compiler.tags;

import org.grails.gsp.GroovyPage;
import org.grails.gsp.compiler.GroovyPageParser;
import org.grails.taglib.GrailsTagException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author graemerocher
 */
public class GroovyCollectTagTests {

    private GroovyCollectTag tag = new GroovyCollectTag();
    private StringWriter sw = new StringWriter();

    @BeforeEach
    protected void setUp() throws Exception {
        Map context = new HashMap();
        context.put(GroovyPage.OUT, new PrintWriter(sw));
        GroovyPageParser parser=new GroovyPageParser("test", "test", "test", new ByteArrayInputStream(new byte[]{}), null);
        context.put(GroovyPageParser.class, parser);
        tag.init(context);
    }

    /**
     * Test method for {@link org.grails.gsp.compiler.tags.GroovyCollectTag#isKeepPrecedingWhiteSpace()}.
     */
    @Test
    public void testIsKeepPrecedingWhiteSpace() {
        Assertions.assertTrue(tag.isKeepPrecedingWhiteSpace());
    }

    /**
     * Test method for {@link org.grails.gsp.compiler.tags.GroovyCollectTag#isAllowPrecedingContent()}.
     */
    @Test
    public void testIsAllowPrecedingContent() {
        Assertions.assertTrue(tag.isAllowPrecedingContent());
    }

    /**
     * Test method for {@link org.grails.gsp.compiler.tags.GroovyCollectTag#doStartTag()}.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartTag() {
        Map attrs = new HashMap();
        Assertions.assertThrows(GrailsTagException.class, () -> tag.doStartTag(), "can't create this tag with no [in] and [expr] attributes");

        attrs.put("\"in\"", "myObj");
        attrs.put("\"expr\"", " ${ it.name }");
        tag.setAttributes(attrs);
        Assertions.assertFalse(tag.attributes.isEmpty());
        tag.doStartTag();

        Assertions.assertEquals("for( "+tag.getForeachRenamedIt()+" in evaluate('myObj.collect {it.name}', 1, it) { return myObj.collect {it.name} } ) {"+ System.getProperty("line.separator") + "changeItVariable(" + tag.getForeachRenamedIt() + ")" + System.getProperty("line.separator"), sw.toString());
    }
}
